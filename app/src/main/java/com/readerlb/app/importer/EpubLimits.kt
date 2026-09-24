package com.readerlb.app.importer

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.InterruptedIOException

internal const val MAX_EPUB_SOURCE_BYTES = 256L * 1024 * 1024
internal const val MAX_EPUB_IMAGE_BYTES = 24L * 1024 * 1024
internal const val MAX_EPUB_EXTRACTED_IMAGES_BYTES = 256L * 1024 * 1024

internal class EpubLimitException(message: String) : IllegalArgumentException(message)

internal fun checkImageBudget(
    alreadyExtracted: Long,
    currentImage: Long,
    nextChunk: Int,
    singleLimit: Long = MAX_EPUB_IMAGE_BYTES,
    totalLimit: Long = MAX_EPUB_EXTRACTED_IMAGES_BYTES
) {
    if (currentImage + nextChunk > singleLimit) {
        throw EpubLimitException("Иллюстрация EPUB слишком большая")
    }
    if (alreadyExtracted + currentImage + nextChunk > totalLimit) {
        throw EpubLimitException("Иллюстрации EPUB превышают общий лимит")
    }
}

internal fun checkEpubInterrupted() {
    if (Thread.currentThread().isInterrupted) {
        throw InterruptedIOException("Анализ EPUB отменён")
    }
}

internal fun copyEpubSource(
    input: InputStream,
    target: File,
    maxBytes: Long = MAX_EPUB_SOURCE_BYTES
) {
    var total = 0L
    try {
        target.outputStream().buffered().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                checkEpubInterrupted()
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > maxBytes) {
                    throw EpubLimitException(
                        "Исходный EPUB слишком большой " +
                            "(лимит ${maxBytes / 1024 / 1024} МБ)"
                    )
                }
                output.write(buffer, 0, count)
            }
        }
    } catch (error: Throwable) {
        target.delete()
        throw error
    }
}

internal fun readBoundedEpubImage(input: InputStream): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(64 * 1024)
    while (true) {
        checkEpubInterrupted()
        val count = input.read(buffer)
        if (count < 0) break
        checkImageBudget(0, output.size().toLong(), count)
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
