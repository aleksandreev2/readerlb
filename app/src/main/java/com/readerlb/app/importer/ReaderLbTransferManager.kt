package com.readerlb.app.importer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import com.readerlb.app.storage.LocalLibraryItem
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class PortableTitleInfo(
    val title: String,
    val slugUrl: String,
    val chapterCount: Int,
    val firstChapter: String,
    val lastChapter: String
)

class ReaderLbTransferManager(
    private val context: Context
) {
    fun inspect(uri: Uri): PortableTitleInfo? =
        context.contentResolver
            .openInputStream(uri)
            ?.buffered()
            ?.use(::inspectReaderLbTransfer)

    fun createShareUri(
        treeUri: Uri,
        item: LocalLibraryItem
    ): Uri {
        cleanupOldShareFiles()

        val directory = findChild(
            treeUri = treeUri,
            parentDocumentId =
                DocumentsContract
                    .getTreeDocumentId(
                        treeUri
                    ),
            expectedName = item.slugUrl,
            requireDirectory = true
        ) ?: error(
            "Тайтл больше не найден в локальной библиотеке"
        )

        val files = listChildren(
            treeUri = treeUri,
            parentDocumentId =
                directory.documentId
        ).filterNot {
            it.mimeType ==
                DocumentsContract.Document
                    .MIME_TYPE_DIR
        }

        require(
            files.any {
                it.name == "info.json"
            } &&
                files.any {
                    it.name == "chapters.json"
                }
        ) {
            "Локальный тайтл повреждён: отсутствуют служебные файлы"
        }

        val shareDir = File(
            context.cacheDir,
            "shares"
        ).apply {
            require(
                isDirectory || mkdirs()
            ) {
                "Не удалось подготовить папку для отправки"
            }
        }

        val safeName = item.title
            .replace(
                Regex(
                    """[\\/:*?"<>|\p{Cntrl}]+"""
                ),
                "_"
            )
            .trim()
            .take(80)
            .ifBlank {
                "ReaderLB_title"
            }

        val target = File(
            shareDir,
            safeName +
                "_" +
                System.currentTimeMillis() +
                ".readerlb.zip"
        )

        try {
            ZipOutputStream(
                target.outputStream()
                    .buffered()
            ).use { zip ->
                val manifest = JSONObject()
                    .put(
                        "format",
                        TRANSFER_FORMAT
                    )
                    .put(
                        "version",
                        TRANSFER_VERSION
                    )
                    .put(
                        "title",
                        item.title
                    )
                    .put(
                        "slugUrl",
                        item.slugUrl
                    )
                    .put(
                        "chapterCount",
                        item.chapterCount
                    )
                    .put(
                        "firstChapter",
                        item.firstChapter
                    )
                    .put(
                        "lastChapter",
                        item.lastChapter
                    )
                    .toString()
                    .toByteArray(
                        Charsets.UTF_8
                    )

                zip.putNextEntry(
                    ZipEntry(
                        TRANSFER_MANIFEST
                    )
                )
                zip.write(manifest)
                zip.closeEntry()

                files
                    .sortedBy {
                        it.name
                    }
                    .forEach { file ->
                        val name = file.name
                        require(
                            isSafeLeafName(name)
                        ) {
                            "Недопустимое имя файла внутри тайтла"
                        }

                        zip.putNextEntry(
                            ZipEntry(
                                "book/" +
                                    item.slugUrl +
                                    "/" +
                                    name
                            )
                        )

                        context.contentResolver
                            .openInputStream(
                                file.uri
                            )
                            .use { input ->
                                requireNotNull(
                                    input
                                ) {
                                    "Не удалось прочитать $name"
                                }
                                input.copyTo(zip)
                            }
                        zip.closeEntry()
                    }
            }
        } catch (throwable: Throwable) {
            target.delete()
            throw throwable
        }

        require(
            target.isFile &&
                target.length() > 0L
        ) {
            "Пакет ReaderLB не был создан"
        }

        return FileProvider.getUriForFile(
            context,
            context.packageName +
                ".files",
            target
        )
    }

    fun shareIntent(
        uri: Uri,
        title: String
    ): Intent =
        Intent(
            Intent.ACTION_SEND
        ).apply {
            type = "application/zip"
            putExtra(
                Intent.EXTRA_STREAM,
                uri
            )
            putExtra(
                Intent.EXTRA_SUBJECT,
                title
            )
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

    fun install(
        uri: Uri,
        treeUri: Uri
    ): ExportResult {
        val info = inspect(uri)
            ?: error(
                "Это не пакет ReaderLB"
            )

        val tempRoot = File(
            context.cacheDir,
            "readerlb_transfer_" +
                System.nanoTime()
        ).apply {
            require(mkdirs()) {
                "Не удалось подготовить временную папку"
            }
        }

        try {
            context.contentResolver
                .openInputStream(uri)
                ?.buffered()
                ?.use { input ->
                    extractReaderLbTransfer(
                        input = input,
                        info = info,
                        rootDir = tempRoot
                    )
                }
                ?: error(
                    "Не удалось открыть пакет ReaderLB"
                )

            val titleDir = File(
                tempRoot,
                "book/" + info.slugUrl
            )
            val built =
                BuiltRanobeLibPackage(
                    rootDir = tempRoot,
                    titleDir = titleDir,
                    title = info.title,
                    chapterCount =
                        info.chapterCount,
                    firstChapter =
                        info.firstChapter,
                    lastChapter =
                        info.lastChapter,
                    slugUrl =
                        info.slugUrl
                )

            val report =
                RanobeLibPackageBuilder()
                    .verify(built)
            if (!report.isValid) {
                throw PackageVerificationException(
                    report
                )
            }

            val direct =
                RanobeLibExporter(context)
                    .copyToRanobeLibTree(
                        treeUri = treeUri,
                        source = titleDir,
                        slugUrl =
                            info.slugUrl
                    )

            return ExportResult(
                title = info.title,
                chapterCount =
                    direct.chapterCount,
                firstChapter =
                    direct.firstChapter,
                lastChapter =
                    direct.lastChapter,
                slugUrl =
                    info.slugUrl,
                installedDirectly = true,
                updatedExisting =
                    direct.updatedExisting,
                addedChapterCount =
                    direct.addedChapterCount
            )
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    fun deleteTitle(
        treeUri: Uri,
        slugUrl: String
    ) {
        val rootId =
            DocumentsContract
                .getTreeDocumentId(
                    treeUri
                )

        val title = findChild(
            treeUri = treeUri,
            parentDocumentId =
                rootId,
            expectedName = slugUrl,
            requireDirectory = true
        ) ?: return

        val deleted =
            DocumentsContract
                .deleteDocument(
                    context.contentResolver,
                    title.uri
                )

        require(deleted) {
            "Android не разрешил удалить локальный тайтл"
        }
    }

    private fun cleanupOldShareFiles() {
        val cutoff =
            System.currentTimeMillis() -
                SHARE_FILE_MAX_AGE

        File(
            context.cacheDir,
            "shares"
        )
            .listFiles()
            .orEmpty()
            .filter {
                it.isFile &&
                    it.lastModified() <
                        cutoff
            }
            .forEach {
                runCatching {
                    it.delete()
                }
            }
    }

    private fun findChild(
        treeUri: Uri,
        parentDocumentId: String,
        expectedName: String,
        requireDirectory: Boolean
    ): SafEntry? =
        listChildren(
            treeUri = treeUri,
            parentDocumentId =
                parentDocumentId
        ).firstOrNull { child ->
            child.name ==
                expectedName &&
                (
                    !requireDirectory ||
                        child.mimeType ==
                        DocumentsContract
                            .Document
                            .MIME_TYPE_DIR
                )
        }

    private fun listChildren(
        treeUri: Uri,
        parentDocumentId: String
    ): List<SafEntry> {
        val childrenUri =
            DocumentsContract
                .buildChildDocumentsUriUsingTree(
                    treeUri,
                    parentDocumentId
                )

        val projection = arrayOf(
            DocumentsContract.Document
                .COLUMN_DOCUMENT_ID,
            DocumentsContract.Document
                .COLUMN_DISPLAY_NAME,
            DocumentsContract.Document
                .COLUMN_MIME_TYPE
        )

        val result =
            ArrayList<SafEntry>()

        val cursor =
            context.contentResolver
                .query(
                    childrenUri,
                    projection,
                    null,
                    null,
                    null
                )
                ?: error(
                    "Android не дал прочитать локальный тайтл"
                )

        cursor.use {
            val idIndex =
                it.getColumnIndexOrThrow(
                    DocumentsContract.Document
                        .COLUMN_DOCUMENT_ID
                )
            val nameIndex =
                it.getColumnIndexOrThrow(
                    DocumentsContract.Document
                        .COLUMN_DISPLAY_NAME
                )
            val mimeIndex =
                it.getColumnIndexOrThrow(
                    DocumentsContract.Document
                        .COLUMN_MIME_TYPE
                )

            while (it.moveToNext()) {
                val documentId =
                    it.getString(idIndex)
                        .orEmpty()
                val name =
                    it.getString(nameIndex)
                        .orEmpty()
                val mime =
                    it.getString(mimeIndex)
                        .orEmpty()

                if (
                    documentId.isBlank() ||
                    name.isBlank()
                ) {
                    continue
                }

                result += SafEntry(
                    documentId =
                        documentId,
                    name = name,
                    mimeType = mime,
                    uri =
                        DocumentsContract
                            .buildDocumentUriUsingTree(
                                treeUri,
                                documentId
                            )
                )
            }
        }

        return result
    }

    private data class SafEntry(
        val documentId: String,
        val name: String,
        val mimeType: String,
        val uri: Uri
    )

    private companion object {
        const val SHARE_FILE_MAX_AGE =
            24L * 60L * 60L * 1000L
    }
}

internal fun inspectReaderLbTransfer(
    input: InputStream
): PortableTitleInfo? {
    var manifest: PortableTitleInfo? = null
    var hasInfo = false
    var hasChapters = false

    ZipInputStream(
        input.buffered()
    ).use { zip ->
        while (true) {
            val entry =
                zip.nextEntry
                    ?: break

            val name = entry.name
            require(
                isSafeTransferEntry(
                    name
                )
            ) {
                "Пакет ReaderLB содержит небезопасный путь"
            }

            if (
                !entry.isDirectory &&
                name ==
                TRANSFER_MANIFEST
            ) {
                val json =
                    JSONObject(
                        zip.readBytes()
                            .toString(
                                Charsets.UTF_8
                            )
                    )
                manifest =
                    parseTransferManifest(
                        json
                    )
            }

            val current = manifest
            if (current != null) {
                val prefix =
                    "book/" +
                        current.slugUrl +
                        "/"
                if (
                    name ==
                    prefix + "info.json"
                ) {
                    hasInfo = true
                }
                if (
                    name ==
                    prefix +
                        "chapters.json"
                ) {
                    hasChapters = true
                }
            }

            zip.closeEntry()
        }
    }

    val result =
        manifest ?: return null

    require(
        hasInfo &&
            hasChapters
    ) {
        "Пакет ReaderLB неполный"
    }

    return result
}

internal fun extractReaderLbTransfer(
    input: InputStream,
    info: PortableTitleInfo,
    rootDir: File
) {
    val titleDir = File(
        rootDir,
        "book/" + info.slugUrl
    )
    require(
        titleDir.mkdirs()
    ) {
        "Не удалось подготовить пакет ReaderLB"
    }

    val prefix =
        "book/" +
            info.slugUrl +
            "/"
    var totalBytes = 0L
    var extracted = 0

    ZipInputStream(
        input.buffered()
    ).use { zip ->
        while (true) {
            val entry =
                zip.nextEntry
                    ?: break
            val name = entry.name

            require(
                isSafeTransferEntry(
                    name
                )
            ) {
                "Пакет ReaderLB содержит небезопасный путь"
            }

            if (
                entry.isDirectory ||
                name ==
                TRANSFER_MANIFEST
            ) {
                zip.closeEntry()
                continue
            }

            require(
                name.startsWith(prefix)
            ) {
                "В пакете ReaderLB найден посторонний файл"
            }

            val leaf =
                name.removePrefix(
                    prefix
                )
            require(
                isSafeLeafName(
                    leaf
                )
            ) {
                "Пакет ReaderLB содержит вложенные или небезопасные файлы"
            }

            val target =
                File(
                    titleDir,
                    leaf
                )
            target.outputStream()
                .buffered()
                .use { output ->
                    val buffer =
                        ByteArray(
                            64 * 1024
                        )
                    while (true) {
                        val count =
                            zip.read(
                                buffer
                            )
                        if (count < 0) {
                            break
                        }

                        totalBytes +=
                            count
                        require(
                            totalBytes <=
                                MAX_TRANSFER_BYTES
                        ) {
                            "Пакет ReaderLB слишком большой"
                        }

                        output.write(
                            buffer,
                            0,
                            count
                        )
                    }
                }

            require(
                target.length() > 0L
            ) {
                "В пакете ReaderLB найден пустой файл"
            }

            extracted += 1
            zip.closeEntry()
        }
    }

    require(
        extracted > 0 &&
            File(
                titleDir,
                "info.json"
            ).isFile &&
            File(
                titleDir,
                "chapters.json"
            ).isFile
    ) {
        "Пакет ReaderLB не содержит локальный тайтл"
    }
}

private fun parseTransferManifest(
    json: JSONObject
): PortableTitleInfo {
    require(
        json.optString("format") ==
            TRANSFER_FORMAT
    ) {
        "Неизвестный формат пакета ReaderLB"
    }
    require(
        json.optInt("version") ==
            TRANSFER_VERSION
    ) {
        "Версия пакета ReaderLB пока не поддерживается"
    }

    val title =
        json.optString("title")
            .trim()
    val slugUrl =
        json.optString("slugUrl")
            .trim()
    val chapterCount =
        json.optInt(
            "chapterCount",
            0
        )
    val firstChapter =
        json.optString(
            "firstChapter"
        ).trim()
    val lastChapter =
        json.optString(
            "lastChapter"
        ).trim()

    require(
        title.isNotBlank() &&
            isSafeLeafName(
                slugUrl
            ) &&
            chapterCount > 0 &&
            firstChapter.isNotBlank() &&
            lastChapter.isNotBlank()
    ) {
        "Манифест пакета ReaderLB повреждён"
    }

    return PortableTitleInfo(
        title = title,
        slugUrl = slugUrl,
        chapterCount = chapterCount,
        firstChapter =
            firstChapter,
        lastChapter =
            lastChapter
    )
}

private fun isSafeTransferEntry(
    name: String
): Boolean =
    name.isNotBlank() &&
        !name.startsWith("/") &&
        !name.startsWith("\\") &&
        !name.contains("../") &&
        !name.contains("..\\") &&
        !name.contains(':') &&
        name.length <= 240

private fun isSafeLeafName(
    name: String
): Boolean =
    name.isNotBlank() &&
        name != "." &&
        name != ".." &&
        '/' !in name &&
        '\\' !in name &&
        ':' !in name &&
        name.length <= 180

private const val TRANSFER_FORMAT =
    "readerlb-local-title"
private const val TRANSFER_VERSION = 1
private const val TRANSFER_MANIFEST =
    "readerlb-transfer.json"
private const val MAX_TRANSFER_BYTES =
    2L * 1024L * 1024L * 1024L
