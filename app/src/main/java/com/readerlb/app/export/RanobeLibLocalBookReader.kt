package com.readerlb.app.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.readerlb.app.storage.LocalLibraryItem
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

data class OpenedLocalExportBook(
    val book: LocalExportBook,
    val coverUri: Uri?,
    internal val fileUris: Map<String, Uri>
)

class RanobeLibLocalBookReader(
    private val context: Context
) {

    fun open(
        treeUri: Uri,
        item: LocalLibraryItem
    ): OpenedLocalExportBook {
        val rootDocumentId =
            DocumentsContract
                .getTreeDocumentId(
                    treeUri
                )

        val titleDirectory =
            listChildren(
                treeUri =
                    treeUri,
                parentDocumentId =
                    rootDocumentId
            )
                .firstOrNull {
                    it.mimeType ==
                        DocumentsContract
                            .Document
                            .MIME_TYPE_DIR &&
                        it.name ==
                            item.folderName
                }
                ?: error(
                    "Локальная папка тайтла больше не найдена. Обновите библиотеку ReaderLB."
                )

        val files =
            listChildren(
                treeUri =
                    treeUri,
                parentDocumentId =
                    titleDirectory
                        .documentId
            )
                .filter {
                    it.mimeType !=
                        DocumentsContract
                            .Document
                            .MIME_TYPE_DIR
                }
                .associateBy {
                    it.name
                }

        val info =
            files["info.json"]
                ?: error(
                    "У тайтла отсутствует info.json"
                )
        val chapters =
            files["chapters.json"]
                ?: error(
                    "У тайтла отсутствует chapters.json"
                )

        val infoText =
            readBoundedText(
                uri = info.uri,
                limitBytes =
                    MAX_LOCAL_INFO_BYTES,
                label = "info.json"
            )
        val chaptersText =
            readBoundedText(
                uri = chapters.uri,
                limitBytes =
                    MAX_LOCAL_CHAPTERS_BYTES,
                label = "chapters.json"
            )

        val book =
            parseLocalExportBook(
                infoText = infoText,
                chaptersText =
                    chaptersText,
                folderName =
                    titleDirectory.name,
                availableFileNames =
                    files.keys
            )

        return OpenedLocalExportBook(
            book = book,
            coverUri =
                book.coverName
                    ?.let(files::get)
                    ?.uri,
            fileUris =
                files.mapValues {
                    it.value.uri
                }
        )
    }

    fun readChapter(
        opened: OpenedLocalExportBook,
        reference: LocalExportChapterRef
    ): LocalExportChapter {
        val archiveName =
            reference.archiveName
                ?: error(
                    "Не найден локальный файл главы ${reference.number}"
                )
        val archiveUri =
            opened.fileUris[
                archiveName
            ] ?: error(
                "Файл главы ${reference.number} больше не найден"
            )

        val archive =
            inspectChapterArchive(
                archiveUri
            )

        return parseLocalChapterDocument(
            number = reference.number,
            title = reference.title,
            dataText = archive.dataText,
            archiveEntryNames =
                archive.entryNames
        )
    }

    fun copyChapterImage(
        opened: OpenedLocalExportBook,
        reference: LocalExportChapterRef,
        entryName: String,
        output: OutputStream
    ) {
        requireSafeZipEntryName(
            entryName
        )

        val archiveName =
            reference.archiveName
                ?: error(
                    "Не найден локальный файл главы ${reference.number}"
                )
        val archiveUri =
            opened.fileUris[
                archiveName
            ] ?: error(
                "Файл главы ${reference.number} больше не найден"
            )

        val input =
            context.contentResolver
                .openInputStream(
                    archiveUri
                )
                ?: error(
                    "Android не дал прочитать главу ${reference.number}"
                )

        ZipInputStream(
            input.buffered()
        ).use { zip ->
            var found = false

            while (true) {
                val entry =
                    zip.nextEntry
                        ?: break
                requireSafeZipEntryName(
                    entry.name
                )

                if (
                    !entry.isDirectory &&
                    entry.name ==
                        entryName
                ) {
                    copyBounded(
                        input = zip,
                        output = output,
                        limitBytes =
                            MAX_LOCAL_IMAGE_BYTES,
                        label =
                            "Иллюстрация"
                    )
                    found = true
                    break
                }

                zip.closeEntry()
            }

            require(found) {
                "Иллюстрация больше не найдена в локальной главе"
            }
        }
    }

    private fun inspectChapterArchive(
        uri: Uri
    ): InspectedChapterArchive {
        val input =
            context.contentResolver
                .openInputStream(uri)
                ?: error(
                    "Android не дал прочитать локальную главу"
                )

        val names =
            LinkedHashSet<String>()
        var dataText: String? = null

        ZipInputStream(
            input.buffered()
        ).use { zip ->
            while (true) {
                val entry =
                    zip.nextEntry
                        ?: break

                requireSafeZipEntryName(
                    entry.name
                )

                require(
                    names.size <
                        MAX_CHAPTER_ARCHIVE_ENTRIES
                ) {
                    "В архиве главы слишком много файлов"
                }

                require(
                    names.add(
                        entry.name
                    )
                ) {
                    "В архиве главы повторяется файл ${entry.name}"
                }

                if (
                    !entry.isDirectory &&
                    entry.name ==
                        "data.txt"
                ) {
                    dataText =
                        readBoundedZipText(
                            zip
                        )
                }

                zip.closeEntry()
            }
        }

        return InspectedChapterArchive(
            dataText =
                dataText
                    ?: error(
                        "Локальная глава не содержит data.txt"
                    ),
            entryNames = names
        )
    }

    private fun readBoundedZipText(
        zip: ZipInputStream
    ): String {
        val output =
            ByteArrayOutputStream()

        copyBounded(
            input = zip,
            output = output,
            limitBytes =
                MAX_CHAPTER_DATA_BYTES,
            label = "data.txt"
        )

        return output
            .toByteArray()
            .toString(
                Charsets.UTF_8
            )
    }

    private fun readBoundedText(
        uri: Uri,
        limitBytes: Long,
        label: String
    ): String {
        val input =
            context.contentResolver
                .openInputStream(uri)
                ?: error(
                    "Android не дал прочитать $label"
                )
        val output =
            ByteArrayOutputStream()

        input.buffered().use {
            copyBounded(
                input = it,
                output = output,
                limitBytes =
                    limitBytes,
                label = label
            )
        }

        return output
            .toByteArray()
            .toString(
                Charsets.UTF_8
            )
    }

    private fun listChildren(
        treeUri: Uri,
        parentDocumentId: String
    ): List<SafDocument> {
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

        val cursor =
            context.contentResolver.query(
                childrenUri,
                projection,
                null,
                null,
                null
            ) ?: error(
                "Android не дал прочитать локальную библиотеку"
            )

        return cursor.use {
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

            buildList {
                while (
                    it.moveToNext()
                ) {
                    val id =
                        it.getString(
                            idIndex
                        )
                    val name =
                        it.getString(
                            nameIndex
                        )
                            .orEmpty()
                    val mime =
                        it.getString(
                            mimeIndex
                        )
                            .orEmpty()

                    if (
                        id.isBlank()
                    ) {
                        continue
                    }

                    add(
                        SafDocument(
                            documentId = id,
                            name = name,
                            mimeType = mime,
                            uri =
                                DocumentsContract
                                    .buildDocumentUriUsingTree(
                                        treeUri,
                                        id
                                    )
                        )
                    )
                }
            }
        }
    }

    private data class SafDocument(
        val documentId: String,
        val name: String,
        val mimeType: String,
        val uri: Uri
    )

    private data class InspectedChapterArchive(
        val dataText: String,
        val entryNames: Set<String>
    )
}

private fun copyBounded(
    input: java.io.InputStream,
    output: OutputStream,
    limitBytes: Long,
    label: String
) {
    var total = 0L
    val buffer =
        ByteArray(
            64 * 1024
        )

    while (true) {
        if (
            Thread.currentThread()
                .isInterrupted
        ) {
            throw InterruptedException(
                "Операция отменена"
            )
        }

        val count =
            input.read(buffer)
        if (count < 0) {
            break
        }
        if (count == 0) {
            continue
        }

        total += count
        require(
            total <= limitBytes
        ) {
            "$label слишком большой"
        }

        output.write(
            buffer,
            0,
            count
        )
    }
}

private fun requireSafeZipEntryName(
    name: String
) {
    require(
        name.isNotBlank() &&
            !name.startsWith("/") &&
            !name.startsWith("\\") &&
            !name.contains("../") &&
            !name.contains("..\\") &&
            !name.contains('/') &&
            !name.contains('\\')
    ) {
        "Некорректное имя файла внутри главы"
    }
}

private const val MAX_LOCAL_INFO_BYTES =
    4L * 1024L * 1024L
private const val MAX_LOCAL_CHAPTERS_BYTES =
    32L * 1024L * 1024L
private const val MAX_CHAPTER_DATA_BYTES =
    8L * 1024L * 1024L
private const val MAX_LOCAL_IMAGE_BYTES =
    64L * 1024L * 1024L
private const val MAX_CHAPTER_ARCHIVE_ENTRIES =
    4096
