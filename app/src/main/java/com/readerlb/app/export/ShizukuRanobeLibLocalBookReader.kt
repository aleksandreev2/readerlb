package com.readerlb.app.export

import com.readerlb.app.shizuku.RanobeLibPrivilegedFiles
import com.readerlb.app.storage.LocalLibraryItem
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream

class ShizukuRanobeLibLocalBookReader(
    private val files:
        RanobeLibPrivilegedFiles
) {

    fun open(
        item: LocalLibraryItem
    ): OpenedLocalExportBook {
        val folder =
            item.folderName

        require(
            files.isDirectory(
                folder
            )
        ) {
            "Локальная папка тайтла больше не найдена. Обновите библиотеку ReaderLB."
        }

        val names =
            files.listNames(
                folder
            )
                .filter {
                    !files.isDirectory(
                        "$folder/$it"
                    )
                }
                .toSet()

        require(
            "info.json" in names
        ) {
            "У тайтла отсутствует info.json"
        }
        require(
            "chapters.json" in names
        ) {
            "У тайтла отсутствует chapters.json"
        }

        val infoText =
            readBoundedText(
                "$folder/info.json",
                MAX_LOCAL_INFO_BYTES,
                "info.json"
            )
        val chaptersText =
            readBoundedText(
                "$folder/chapters.json",
                MAX_LOCAL_CHAPTERS_BYTES,
                "chapters.json"
            )

        val book =
            parseLocalExportBook(
                infoText = infoText,
                chaptersText =
                    chaptersText,
                folderName =
                    folder,
                availableFileNames =
                    names
            )

        return OpenedLocalExportBook(
            book = book,
            coverUri = null,
            fileUris =
                emptyMap(),
            folderName =
                folder
        )
    }

    fun readChapter(
        opened:
            OpenedLocalExportBook,
        reference:
            LocalExportChapterRef
    ): LocalExportChapter {
        val archiveName =
            reference.archiveName
                ?: error(
                    "Не найден локальный файл главы ${reference.number}"
                )
        val path =
            opened.folderName +
                "/" +
                archiveName

        require(
            files.exists(path)
        ) {
            "Файл главы ${reference.number} больше не найден"
        }

        val archive =
            files.openInput(path)
                .use(
                    ::inspectLocalChapterArchive
                )

        return parseLocalChapterDocument(
            number =
                reference.number,
            title =
                reference.title,
            dataText =
                archive.dataText,
            archiveEntryNames =
                archive.entryNames
        )
    }

    fun copyTitleFile(
        opened:
            OpenedLocalExportBook,
        fileName: String,
        output: OutputStream
    ) {
        val path =
            opened.folderName +
                "/" +
                fileName

        require(
            files.exists(path)
        ) {
            "Локальный файл больше не найден"
        }

        files.openInput(path)
            .buffered()
            .use {
                input ->
                copyBounded(
                    input = input,
                    output = output,
                    limitBytes =
                        MAX_LOCAL_IMAGE_BYTES,
                    label = "Файл"
                )
            }
    }

    fun copyChapterImage(
        opened:
            OpenedLocalExportBook,
        reference:
            LocalExportChapterRef,
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
        val path =
            opened.folderName +
                "/" +
                archiveName

        require(
            files.exists(path)
        ) {
            "Файл главы ${reference.number} больше не найден"
        }

        ZipInputStream(
            files.openInput(path)
                .buffered()
        ).use {
                zip ->
            var found = false

            while (true) {
                checkLocalExportInterrupted()

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

    private fun readBoundedText(
        relativePath: String,
        limitBytes: Long,
        label: String
    ): String {
        val output =
            ByteArrayOutputStream()

        files.openInput(
            relativePath
        )
            .buffered()
            .use {
                input ->
                copyBounded(
                    input = input,
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
}
