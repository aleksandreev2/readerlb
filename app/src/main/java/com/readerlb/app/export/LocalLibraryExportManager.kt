package com.readerlb.app.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.readerlb.app.importer.chapterNumberDecimal
import com.readerlb.app.importer.chapterNumberInRange
import com.readerlb.app.storage.LocalLibraryItem
import com.readerlb.app.shizuku.RanobeLibPrivilegedFiles
import java.io.OutputStream

data class ExportedLocalBookFile(
    val uri: Uri,
    val displayName: String,
    val chapterCount: Int,
    val format: LocalBookExportFormat,
    val warningCount: Int = 0,
    val warnings: List<String> = emptyList()
)

class LocalLibraryExportManager(
    private val context: Context
) {
    private val safReader =
        RanobeLibLocalBookReader(
            context
        )
    private val txtWriter =
        LocalBookTxtWriter()
    private val epubWriter =
        LocalBookEpubWriter()
    private val fb2Writer =
        LocalBookFb2Writer()
    private val pdfWriter =
        LocalBookPdfWriter(
            context
        )

    fun export(
        format: LocalBookExportFormat,
        treeUri: Uri,
        item: LocalLibraryItem,
        options: LocalExportOptions =
            LocalExportOptions(),
        onProgress: (
            LocalExportProgress
        ) -> Unit = {}
    ): ExportedLocalBookFile {
        val source =
            safReader.open(
                treeUri = treeUri,
                item = item
            )

        return exportOpened(
            format = format,
            source = source,
            options = options,
            readChapter = {
                    opened,
                    reference ->
                safReader.readChapter(
                    opened,
                    reference
                )
            },
            copyTitleFile = {
                    opened,
                    fileName,
                    output ->
                safReader.copyTitleFile(
                    opened,
                    fileName,
                    output
                )
            },
            copyChapterImage = {
                    opened,
                    reference,
                    entryName,
                    output ->
                safReader.copyChapterImage(
                    opened,
                    reference,
                    entryName,
                    output
                )
            },
            onProgress = onProgress
        )
    }

    fun export(
        format: LocalBookExportFormat,
        privilegedFiles:
            RanobeLibPrivilegedFiles,
        item: LocalLibraryItem,
        options: LocalExportOptions =
            LocalExportOptions(),
        onProgress: (
            LocalExportProgress
        ) -> Unit = {}
    ): ExportedLocalBookFile {
        val reader =
            ShizukuRanobeLibLocalBookReader(
                privilegedFiles
            )
        val source =
            reader.open(item)

        return exportOpened(
            format = format,
            source = source,
            options = options,
            readChapter = {
                    opened,
                    reference ->
                reader.readChapter(
                    opened,
                    reference
                )
            },
            copyTitleFile = {
                    opened,
                    fileName,
                    output ->
                reader.copyTitleFile(
                    opened,
                    fileName,
                    output
                )
            },
            copyChapterImage = {
                    opened,
                    reference,
                    entryName,
                    output ->
                reader.copyChapterImage(
                    opened,
                    reference,
                    entryName,
                    output
                )
            },
            onProgress = onProgress
        )
    }

    private fun exportOpened(
        format:
            LocalBookExportFormat,
        source:
            OpenedLocalExportBook,
        options:
            LocalExportOptions,
        readChapter: (
            OpenedLocalExportBook,
            LocalExportChapterRef
        ) -> LocalExportChapter,
        copyTitleFile: (
            OpenedLocalExportBook,
            String,
            OutputStream
        ) -> Unit,
        copyChapterImage: (
            OpenedLocalExportBook,
            LocalExportChapterRef,
            String,
            OutputStream
        ) -> Unit,
        onProgress: (
            LocalExportProgress
        ) -> Unit
    ): ExportedLocalBookFile {
        val selectedBook =
            selectLocalExportBook(
                book = source.book,
                options = options
            )
        val opened =
            source.copy(
                book = selectedBook
            )
        val displayName =
            safeExportFileName(
                selectedBook.title
            ) +
                "." +
                format.extension
        var warningCount = 0
        val warnings =
            mutableListOf<String>()

        fun readForExport(
            reference:
                LocalExportChapterRef
        ): LocalExportChapter {
            val raw =
                readChapter(
                    opened,
                    reference
                )
            val chapter =
                if (
                    options.includeImages
                ) {
                    raw
                } else {
                    raw.copy(
                        blocks =
                            raw.blocks
                                .filterNot {
                                    it is
                                        LocalExportBlock
                                            .Image
                                }
                    )
                }

            chapter.warnings.forEach {
                    warning ->
                warningCount += 1

                if (
                    warnings.size <
                    MAX_EXPORTED_WARNING_MESSAGES
                ) {
                    warnings +=
                        "Глава " +
                            reference.number +
                            ": " +
                            warning
                }
            }

            return chapter
        }

        fun coverCopy():
            ((OutputStream) -> Unit)? =
            opened.book.coverName
                ?.let {
                        coverName ->
                    {
                            output ->
                        copyTitleFile(
                            opened,
                            coverName,
                            output
                        )
                    }
                }

        return writePendingDownload(
            resolver =
                context.contentResolver,
            displayName =
                displayName,
            mimeType =
                format.mimeType
        ) {
                output ->
            when (format) {
                LocalBookExportFormat
                    .TXT -> {
                    txtWriter.write(
                        book =
                            selectedBook,
                        output = output,
                        readChapter =
                            ::readForExport,
                        onProgress =
                            onProgress
                    )
                }

                LocalBookExportFormat
                    .EPUB -> {
                    epubWriter.write(
                        book =
                            selectedBook,
                        output = output,
                        readChapter =
                            ::readForExport,
                        copyCover =
                            coverCopy(),
                        copyChapterImage = {
                                reference,
                                image,
                                imageOutput ->
                            copyChapterImage(
                                opened,
                                reference,
                                image.entryName,
                                imageOutput
                            )
                        },
                        onProgress =
                            onProgress
                    )
                }

                LocalBookExportFormat
                    .FB2 -> {
                    fb2Writer.write(
                        book =
                            selectedBook,
                        output = output,
                        readChapter =
                            ::readForExport,
                        copyCover =
                            coverCopy(),
                        copyChapterImage = {
                                reference,
                                image,
                                imageOutput ->
                            copyChapterImage(
                                opened,
                                reference,
                                image.entryName,
                                imageOutput
                            )
                        },
                        onProgress =
                            onProgress
                    )
                }

                LocalBookExportFormat
                    .PDF -> {
                    pdfWriter.write(
                        book =
                            selectedBook,
                        output = output,
                        readChapter =
                            ::readForExport,
                        copyCover =
                            coverCopy(),
                        copyChapterImage = {
                                reference,
                                image,
                                imageOutput ->
                            copyChapterImage(
                                opened,
                                reference,
                                image.entryName,
                                imageOutput
                            )
                        },
                        onProgress =
                            onProgress
                    )
                }
            }

            ExportedLocalBookFile(
                uri = Uri.EMPTY,
                displayName =
                    displayName,
                chapterCount =
                    selectedBook
                        .chapters.size,
                format = format,
                warningCount =
                    warningCount,
                warnings =
                    warnings.toList()
            )
        }
    }
}


internal fun writePendingDownload(
    resolver: android.content.ContentResolver,
    displayName: String,
    mimeType: String,
    block: (
        java.io.OutputStream
    ) -> ExportedLocalBookFile
): ExportedLocalBookFile {
    val values =
        ContentValues()
            .apply {
                put(
                    MediaStore
                        .MediaColumns
                        .DISPLAY_NAME,
                    displayName
                )
                put(
                    MediaStore
                        .MediaColumns
                        .MIME_TYPE,
                    mimeType
                )
                put(
                    MediaStore
                        .MediaColumns
                        .RELATIVE_PATH,
                    Environment
                        .DIRECTORY_DOWNLOADS +
                        "/ReaderLB"
                )
                put(
                    MediaStore
                        .MediaColumns
                        .IS_PENDING,
                    1
                )
            }

    val uri =
        resolver.insert(
            MediaStore
                .Downloads
                .EXTERNAL_CONTENT_URI,
            values
        ) ?: error(
            "Android не создал файл экспорта"
        )

    try {
        val output =
            resolver
                .openOutputStream(
                    uri,
                    "w"
                ) ?: error(
                "Android не дал записать файл экспорта"
            )

        val result =
            output.buffered().use(
                block
            )

        val published =
            ContentValues()
                .apply {
                    put(
                        MediaStore
                            .MediaColumns
                            .IS_PENDING,
                        0
                    )
                }

        resolver.update(
            uri,
            published,
            null,
            null
        )

        return result.copy(
            uri = uri
        )
    } catch (throwable: Throwable) {
        resolver.delete(
            uri,
            null,
            null
        )
        throw throwable
    }
}

internal fun selectLocalExportBook(
    book: LocalExportBook,
    options: LocalExportOptions
): LocalExportBook {
    val first =
        options.firstChapter
            ?.trim()
            ?.takeIf(
                String::isNotBlank
            )
    val last =
        options.lastChapter
            ?.trim()
            ?.takeIf(
                String::isNotBlank
            )
    val firstValue =
        first?.let(
            ::chapterNumberDecimal
        )
    val lastValue =
        last?.let(
            ::chapterNumberDecimal
        )

    if (first != null) {
        require(
            firstValue != null
        ) {
            "Некорректный номер начальной главы"
        }
    }
    if (last != null) {
        require(
            lastValue != null
        ) {
            "Некорректный номер конечной главы"
        }
    }
    if (
        firstValue != null &&
        lastValue != null
    ) {
        require(
            firstValue <=
                lastValue
        ) {
            "Начальная глава не может быть больше конечной"
        }
    }

    val selected =
        book.chapters
            .filter {
                chapterNumberInRange(
                    value = it.number,
                    first = first,
                    last = last
                )
            }

    require(
        selected.isNotEmpty()
    ) {
        "В выбранном диапазоне нет глав"
    }

    return book.copy(
        coverName =
            if (
                options.includeCover
            ) {
                book.coverName
            } else {
                null
            },
        chapters = selected
    )
}

internal fun safeExportFileName(
    title: String
): String {
    val cleaned =
        title
            .replace(
                Regex(
                    """[\\/:*?"<>|]"""
                ),
                "_"
            )
            .replace(
                Regex(
                    """\s+"""
                ),
                " "
            )
            .trim()
            .trimEnd('.', ' ')
            .take(120)

    return cleaned
        .ifBlank {
            "ReaderLB-book"
        }
}


private const val MAX_EXPORTED_WARNING_MESSAGES =
    12
