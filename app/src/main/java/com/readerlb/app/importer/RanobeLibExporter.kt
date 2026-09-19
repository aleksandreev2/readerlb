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
        ranobeLibBookTree: Uri? = null
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
            val built = packageBuilder.build(
                book = book,
                rootDir = tempRoot,
                titleOverride = titleOverride,
                firstChapter = firstChapter,
                lastChapter = lastChapter
            )

            val installed = ranobeLibBookTree != null
            if (ranobeLibBookTree != null) {
                copyToRanobeLibTree(
                    treeUri = ranobeLibBookTree,
                    source = built.titleDir,
                    slugUrl = built.slugUrl
                )
            }

            val download = if (installed) {
                null
            } else {
                saveZipToDownloads(built)
            }

            return ExportResult(
                title = built.title,
                chapterCount = built.chapterCount,
                firstChapter = built.firstChapter,
                lastChapter = built.lastChapter,
                slugUrl = built.slugUrl,
                installedDirectly = installed,
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
    ) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Нет доступа к папке RanobeLib")

        require(root.findFile(slugUrl) == null) {
            "Тайтл уже существует в RanobeLib. " +
                "Безопасное обновление существующих тайтлов появится в ReaderLB 0.3."
        }

        val titleDir = root.createDirectory(slugUrl)
            ?: error("Не удалось создать папку тайтла")

        val sourceFiles = source
            .listFiles()
            .orEmpty()
            .filter(File::isFile)
            .sortedBy { it.name }

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
}
