package com.readerlb.app.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.readerlb.app.importer.chapterNumberDecimal
import com.readerlb.app.importer.chapterNumberInRange
import com.readerlb.app.storage.LocalLibraryItem
import com.readerlb.app.storage.TesterDiagnostics

class ExportDestinationUnavailableException(
    val stage: String,
    val displayName: String,
    val mimeType: String,
    cause: Throwable? = null
) : IllegalStateException(
    "Android не смог подготовить файл экспорта ($stage). Выберите место сохранения вручную.",
    cause
)

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
    private val reader =
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
        ) -> Unit = {},
        destinationUri: Uri? = null
    ): ExportedLocalBookFile {
        val source =
            reader.open(
                treeUri = treeUri,
                item = item
            )
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
            reference: LocalExportChapterRef
        ): LocalExportChapter {
            val chapter =
                readChapter(
                    opened = opened,
                    reference =
                        reference,
                    includeImages =
                        options
                            .includeImages
                )

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

        val writerBlock:
            (java.io.OutputStream) ->
                ExportedLocalBookFile = { output ->
            when (format) {
                LocalBookExportFormat
                    .TXT -> {
                    txtWriter.write(
                        book = selectedBook,
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
                        book = selectedBook,
                        output = output,
                        readChapter =
                            ::readForExport,
                        copyCover =
                            coverCopy(
                                opened
                            ),
                        copyChapterImage = {
                                reference,
                                image,
                                imageOutput ->
                            reader
                                .copyChapterImage(
                                    opened =
                                        opened,
                                    reference =
                                        reference,
                                    entryName =
                                        image
                                            .entryName,
                                    output =
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
                        book = selectedBook,
                        output = output,
                        readChapter =
                            ::readForExport,
                        copyCover =
                            coverCopy(
                                opened
                            ),
                        copyChapterImage = {
                                reference,
                                image,
                                imageOutput ->
                            reader
                                .copyChapterImage(
                                    opened =
                                        opened,
                                    reference =
                                        reference,
                                    entryName =
                                        image
                                            .entryName,
                                    output =
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
                        book = selectedBook,
                        output = output,
                        readChapter =
                            ::readForExport,
                        copyCover =
                            coverCopy(
                                opened
                            ),
                        copyChapterImage = {
                                reference,
                                image,
                                imageOutput ->
                            reader
                                .copyChapterImage(
                                    opened =
                                        opened,
                                    reference =
                                        reference,
                                    entryName =
                                        image
                                            .entryName,
                                    output =
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

        return try {
            if (destinationUri == null) {
                writePendingDownload(
                    resolver =
                        context.contentResolver,
                    displayName =
                        displayName,
                    mimeType =
                        format.mimeType,
                    block =
                        writerBlock
                )
            } else {
                writeDocumentUri(
                    resolver =
                        context.contentResolver,
                    uri =
                        destinationUri,
                    displayName =
                        displayName,
                    mimeType =
                        format.mimeType,
                    block =
                        writerBlock
                )
            }
        } catch (
            failure:
                ExportDestinationUnavailableException
        ) {
            TesterDiagnostics.record(
                context,
                "export.destination." +
                    failure.stage,
                "name=" +
                    failure.displayName +
                    "; mime=" +
                    failure.mimeType,
                failure
            )
            throw failure
        }
    }

    private fun readChapter(
        opened: OpenedLocalExportBook,
        reference: LocalExportChapterRef,
        includeImages: Boolean
    ): LocalExportChapter {
        val chapter =
            reader.readChapter(
                opened = opened,
                reference = reference
            )

        return if (
            includeImages
        ) {
            chapter
        } else {
            chapter.copy(
                blocks =
                    chapter.blocks
                        .filterNot {
                            it is
                                LocalExportBlock
                                    .Image
                        }
            )
        }
    }

    private fun coverCopy(
        opened: OpenedLocalExportBook
    ): ((
        java.io.OutputStream
    ) -> Unit)? =
        opened.book.coverName
            ?.let {
                    coverName ->
                {
                        output:
                            java.io.OutputStream ->
                    reader.copyTitleFile(
                        opened = opened,
                        fileName =
                            coverName,
                        output = output
                    )
                }
            }


}


internal fun writePendingDownload(
    resolver: android.content.ContentResolver,
    displayName: String,
    mimeType: String,
    insertDownload: (
        ContentValues
    ) -> Uri? = { values ->
        resolver.insert(
            MediaStore
                .Downloads
                .EXTERNAL_CONTENT_URI,
            values
        )
    },
    publishDownload: (
        Uri,
        ContentValues
    ) -> Int = { uri, values ->
        resolver.update(
            uri,
            values,
            null,
            null
        )
    },
    deleteDownload: (
        Uri
    ) -> Unit = { uri ->
        resolver.delete(
            uri,
            null,
            null
        )
        Unit
    },
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
        try {
            insertDownload(
                values
            )
        } catch (
            throwable: Throwable
        ) {
            throw ExportDestinationUnavailableException(
                stage = "media-store-insert",
                displayName = displayName,
                mimeType = mimeType,
                cause = throwable
            )
        } ?: throw ExportDestinationUnavailableException(
            stage = "media-store-insert",
            displayName = displayName,
            mimeType = mimeType
        )

    try {
        val output =
            try {
                resolver
                    .openOutputStream(
                        uri,
                        "w"
                    )
            } catch (
                throwable: Throwable
            ) {
                throw ExportDestinationUnavailableException(
                    stage = "media-store-open",
                    displayName = displayName,
                    mimeType = mimeType,
                    cause = throwable
                )
            } ?: throw ExportDestinationUnavailableException(
                stage = "media-store-open",
                displayName = displayName,
                mimeType = mimeType
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

        val updated =
            try {
                publishDownload(
                    uri,
                    published
                )
            } catch (
                throwable: Throwable
            ) {
                throw ExportDestinationUnavailableException(
                    stage = "media-store-publish",
                    displayName = displayName,
                    mimeType = mimeType,
                    cause = throwable
                )
            }

        if (updated <= 0) {
            throw ExportDestinationUnavailableException(
                stage = "media-store-publish",
                displayName = displayName,
                mimeType = mimeType
            )
        }

        return result.copy(
            uri = uri
        )
    } catch (throwable: Throwable) {
        runCatching {
            deleteDownload(
                uri
            )
        }
        throw throwable
    }
}

internal fun writeDocumentUri(
    resolver: android.content.ContentResolver,
    uri: Uri,
    displayName: String,
    mimeType: String,
    block: (
        java.io.OutputStream
    ) -> ExportedLocalBookFile
): ExportedLocalBookFile {
    val output =
        try {
            resolver
                .openOutputStream(
                    uri,
                    "w"
                )
        } catch (
            throwable: Throwable
        ) {
            throw ExportDestinationUnavailableException(
                stage = "document-open",
                displayName = displayName,
                mimeType = mimeType,
                cause = throwable
            )
        } ?: throw ExportDestinationUnavailableException(
            stage = "document-open",
            displayName = displayName,
            mimeType = mimeType
        )

    return try {
        output.buffered().use(
            block
        ).copy(
            uri = uri
        )
    } catch (throwable: Throwable) {
        runCatching {
            resolver.delete(
                uri,
                null,
                null
            )
        }
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
