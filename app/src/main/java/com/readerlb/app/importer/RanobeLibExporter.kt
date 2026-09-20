package com.readerlb.app.importer

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RanobeLibExporter(private val context: Context) {

    private val packageBuilder = RanobeLibPackageBuilder()

    fun export(
        book: ParsedBook,
        titleOverride: String = "",
        firstChapter: String? = null,
        lastChapter: String? = null,
        ranobeLibBookTree: Uri? = null,
        onStage: (ExportStage) -> Unit = {}
    ): ExportResult {
        val tempRoot = File(
            context.cacheDir,
            "readerlb_export_${System.nanoTime()}"
        ).apply {
            require(mkdirs()) {
                "Не удалось создать временную папку экспорта"
            }
        }

        try {
            onStage(ExportStage.PREPARING)

            val built = packageBuilder.build(
                book = book,
                rootDir = tempRoot,
                titleOverride = titleOverride,
                firstChapter = firstChapter,
                lastChapter = lastChapter
            )

            onStage(ExportStage.VERIFYING)
            val verification =
                packageBuilder.verify(built)

            if (!verification.isValid) {
                throw PackageVerificationException(
                    verification
                )
            }

            onStage(ExportStage.WRITING)

            val directResult = ranobeLibBookTree?.let {
                copyToRanobeLibTree(
                    treeUri = it,
                    source = built.titleDir,
                    slugUrl = built.slugUrl
                )
            }
            val installed = directResult != null

            val download = if (installed) {
                null
            } else {
                saveZipToDownloads(built)
            }

            onStage(ExportStage.FINALIZING)

            return ExportResult(
                title = built.title,
                chapterCount = directResult?.chapterCount
                    ?: built.chapterCount,
                firstChapter = directResult?.firstChapter
                    ?: built.firstChapter,
                lastChapter = directResult?.lastChapter
                    ?: built.lastChapter,
                slugUrl = built.slugUrl,
                installedDirectly = installed,
                updatedExisting = directResult?.updatedExisting ?: false,
                addedChapterCount = directResult?.addedChapterCount
                    ?: built.chapterCount,
                downloadUri = download?.toString()
            )
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    private fun copyToRanobeLibTree(
        treeUri: Uri,
        source: File,
        slugUrl: String
    ): DirectWriteResult {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Нет доступа к папке RanobeLib")

        val existingTitleDir = root.findFile(slugUrl)
        if (existingTitleDir != null) {
            require(existingTitleDir.isDirectory) {
                "Путь существующего тайтла RanobeLib не является папкой"
            }

            val result = RanobeLibUpdateTransaction().apply(
                existing = RanobeLibDocumentStorage(
                    context = context,
                    directory = existingTitleDir
                ),
                incomingTitleDir = source
            )

            val merged = result.mergedNumbers
            require(merged.isNotEmpty()) {
                "После обновления тайтл не содержит глав"
            }

            return DirectWriteResult(
                chapterCount = result.totalChapterCount,
                firstChapter = merged.first(),
                lastChapter = merged.last(),
                updatedExisting = true,
                addedChapterCount = result.addedNumbers.size
            )
        }

        val titleDir = root.createDirectory(slugUrl)
            ?: error("Не удалось создать папку тайтла")

        val sourceFiles = source
            .listFiles()
            .orEmpty()
            .filter(File::isFile)
            .sortedWith(
                compareBy<File> { file ->
                    // info.json is RanobeLib's entry point for a local title.
                    // Write it last so the app cannot discover a half-copied book.
                    when (file.name) {
                        "info.json" -> 2
                        "chapters.json" -> 1
                        else -> 0
                    }
                }.thenBy { it.name }
            )

        require(sourceFiles.isNotEmpty()) {
            "Подготовленный пакет пуст"
        }

        try {
            sourceFiles.forEach { file ->
                val target = titleDir.createFile(
                    mimeType(file),
                    file.name
                ) ?: error("Не удалось создать ${file.name}")

                context.contentResolver
                    .openOutputStream(target.uri, "w")
                    .use { output ->
                        requireNotNull(output) {
                            "Не удалось открыть ${file.name} для записи"
                        }
                        file.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }

                require(target.length() == file.length()) {
                    "Размер ${file.name} после копирования не совпадает"
                }
            }

            val installed = titleDir
                .listFiles()
                .associateBy { it.name }

            sourceFiles.forEach { sourceFile ->
                val target = installed[sourceFile.name]
                require(target != null && target.isFile) {
                    "После копирования отсутствует ${sourceFile.name}"
                }
                require(target.length() == sourceFile.length()) {
                    "Проверка ${sourceFile.name} после копирования не пройдена"
                }
            }
        } catch (throwable: Throwable) {
            // This directory was created by this attempt. Removing it prevents
            // RanobeLib from indexing a half-written book after a failed copy.
            runCatching { titleDir.delete() }
            throw throwable
        }

        val incomingChapters = org.json.JSONArray(
            File(source, "chapters.json")
                .readText(Charsets.UTF_8)
        )
        val numbers = (0 until incomingChapters.length())
            .map {
                incomingChapters
                    .getJSONObject(it)
                    .getString("number")
            }
            .sortedWith(::compareChapterNumbers)

        require(numbers.isNotEmpty()) {
            "Подготовленный тайтл не содержит глав"
        }

        return DirectWriteResult(
            chapterCount = numbers.size,
            firstChapter = numbers.first(),
            lastChapter = numbers.last(),
            updatedExisting = false,
            addedChapterCount = numbers.size
        )
    }

    private fun mimeType(file: File): String =
        when (file.extension.lowercase()) {
            "json" -> "application/json"
            "txt" -> "text/plain"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }

    private fun saveZipToDownloads(
        built: BuiltRanobeLibPackage
    ): Uri {
        val safeTitle = built.slugUrl
            .substringAfter("--", built.slugUrl)
            .ifBlank { built.slugUrl }

        val values = ContentValues().apply {
            put(
                MediaStore.Downloads.DISPLAY_NAME,
                "${safeTitle}_for_RanobeLib.zip"
            )
            put(
                MediaStore.Downloads.MIME_TYPE,
                "application/zip"
            )
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/ReaderLB"
            )
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val uri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values
        ) ?: error("Не удалось создать файл в Downloads")

        try {
            context.contentResolver
                .openOutputStream(uri, "w")
                .use { output ->
                    requireNotNull(output) {
                        "Не удалось открыть ZIP для записи"
                    }

                    ZipOutputStream(output.buffered()).use { zip ->
                        File(built.rootDir, "book")
                            .walkTopDown()
                            .filter(File::isFile)
                            .sortedBy {
                                it.relativeTo(built.rootDir)
                                    .invariantSeparatorsPath
                            }
                            .forEach { file ->
                                val relative = file
                                    .relativeTo(built.rootDir)
                                    .invariantSeparatorsPath

                                zip.putNextEntry(
                                    ZipEntry(relative)
                                )
                                file.inputStream().use {
                                    it.copyTo(zip)
                                }
                                zip.closeEntry()
                            }
                    }
                }

            val done = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            context.contentResolver.update(
                uri,
                done,
                null,
                null
            )
            return uri
        } catch (throwable: Throwable) {
            context.contentResolver.delete(
                uri,
                null,
                null
            )
            throw throwable
        }
    }
    private data class DirectWriteResult(
        val chapterCount: Int,
        val firstChapter: String,
        val lastChapter: String,
        val updatedExisting: Boolean,
        val addedChapterCount: Int
    )
}
