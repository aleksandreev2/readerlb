package com.readerlb.app.importer

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.InterruptedIOException

internal const val DEFAULT_EPUB_SOURCE_LIMIT_MB = 256L
internal const val DEFAULT_EPUB_IMAGE_LIMIT_MB = 24L
internal const val DEFAULT_EPUB_TOTAL_IMAGE_LIMIT_MB = 256L

private const val MEBIBYTE_BYTES = 1024L * 1024L

internal data class EpubImportLimits(
    val sourceBytes: Long?,
    val singleImageBytes: Long?,
    val totalImageBytes: Long?
) {
    companion object {
        val DEFAULT =
            fromMegabytes(
                sourceMb = DEFAULT_EPUB_SOURCE_LIMIT_MB,
                singleImageMb = DEFAULT_EPUB_IMAGE_LIMIT_MB,
                totalImageMb = DEFAULT_EPUB_TOTAL_IMAGE_LIMIT_MB
            )

        fun fromMegabytes(
            sourceMb: Long,
            singleImageMb: Long,
            totalImageMb: Long
        ): EpubImportLimits =
            EpubImportLimits(
                sourceBytes =
                    epubLimitBytesFromMegabytes(
                        sourceMb
                    ),
                singleImageBytes =
                    epubLimitBytesFromMegabytes(
                        singleImageMb
                    ),
                totalImageBytes =
                    epubLimitBytesFromMegabytes(
                        totalImageMb
                    )
            )
    }
}

internal fun epubLimitBytesFromMegabytes(
    megabytes: Long
): Long? {
    if (megabytes <= 0L) return null

    if (
        megabytes >
        Long.MAX_VALUE / MEBIBYTE_BYTES
    ) {
        return Long.MAX_VALUE
    }

    return megabytes * MEBIBYTE_BYTES
}

internal class EpubLimitException(
    message: String
) : IllegalArgumentException(message)

internal fun checkImageBudget(
    alreadyExtracted: Long,
    currentImage: Long,
    nextChunk: Int,
    singleLimit: Long? =
        EpubImportLimits.DEFAULT.singleImageBytes,
    totalLimit: Long? =
        EpubImportLimits.DEFAULT.totalImageBytes
) {
    if (
        singleLimit != null &&
        currentImage + nextChunk >
            singleLimit
    ) {
        throw EpubLimitException(
            "Иллюстрация EPUB слишком большая"
        )
    }

    if (
        totalLimit != null &&
        alreadyExtracted +
            currentImage +
            nextChunk >
            totalLimit
    ) {
        throw EpubLimitException(
            "Иллюстрации EPUB превышают общий лимит"
        )
    }
}

internal fun checkEpubInterrupted() {
    if (Thread.currentThread().isInterrupted) {
        throw InterruptedIOException(
            "Анализ EPUB отменён"
        )
    }
}

internal fun copyEpubSource(
    input: InputStream,
    target: File,
    maxBytes: Long? =
        EpubImportLimits.DEFAULT.sourceBytes
) {
    var total = 0L

    try {
        target.outputStream()
            .buffered()
            .use { output ->
                val buffer =
                    ByteArray(64 * 1024)

                while (true) {
                    checkEpubInterrupted()
                    val count =
                        input.read(buffer)

                    if (count < 0) break

                    total += count

                    if (
                        maxBytes != null &&
                        total > maxBytes
                    ) {
                        throw EpubLimitException(
                            "Исходный EPUB слишком большой " +
                                "(лимит " +
                                (maxBytes /
                                    MEBIBYTE_BYTES) +
                                " МБ)"
                        )
                    }

                    output.write(
                        buffer,
                        0,
                        count
                    )
                }
            }
    } catch (error: Throwable) {
        target.delete()
        throw error
    }
}

internal fun readBoundedEpubImage(
    input: InputStream,
    maxBytes: Long? =
        EpubImportLimits.DEFAULT.singleImageBytes
): ByteArray {
    val output =
        ByteArrayOutputStream()
    val buffer =
        ByteArray(64 * 1024)

    while (true) {
        checkEpubInterrupted()
        val count =
            input.read(buffer)

        if (count < 0) break

        checkImageBudget(
            alreadyExtracted = 0,
            currentImage =
                output.size().toLong(),
            nextChunk = count,
            singleLimit = maxBytes,
            totalLimit = null
        )

        output.write(
            buffer,
            0,
            count
        )
    }

    return output.toByteArray()
}
