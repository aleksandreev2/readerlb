package com.readerlb.app.importer

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.readerlb.app.storage.MAX_INFO_JSON_BYTES

/**
 * SAF-backed storage for one already installed RanobeLib title directory.
 *
 * The transaction itself stays Android-independent; this adapter is the only
 * layer that touches DocumentFile.
 */
class RanobeLibDocumentStorage(
    private val context: Context,
    private val directory: DocumentFile
) : RanobeLibMutableStorage {

    init {
        require(directory.isDirectory) {
            "Выбранный тайтл RanobeLib не является папкой"
        }
    }

    override fun exists(name: String): Boolean =
        find(name)?.isFile == true

    override fun readBytes(name: String): ByteArray {
        val file = find(name)
            ?.takeIf(DocumentFile::isFile)
            ?: error("Файл $name отсутствует")

        return context.contentResolver
            .openInputStream(file.uri)
            .use { input ->
                requireNotNull(input) {
                    "Не удалось открыть $name"
                }
                if (name == "info.json") {
                    val bytes = input.readBytesLimited(MAX_INFO_JSON_BYTES)
                    bytes
                } else {
                    input.readBytes()
                }
            }
    }

    override fun writeBytes(
        name: String,
        bytes: ByteArray
    ) {
        find(name)?.let { existing ->
            require(existing.delete()) {
                "Не удалось очистить временный файл $name"
            }
        }

        val file = directory.createFile(
            mimeType(name),
            name
        ) ?: error("Не удалось создать $name")

        context.contentResolver
            .openOutputStream(file.uri, "w")
            .use { output ->
                requireNotNull(output) {
                    "Не удалось открыть $name для записи"
                }
                output.write(bytes)
                output.flush()
            }

        require(file.length() == bytes.size.toLong()) {
            "Размер $name после записи не совпадает"
        }
    }

    override fun delete(name: String): Boolean {
        val file = find(name) ?: return true
        return file.delete()
    }

    override fun rename(
        from: String,
        to: String
    ): Boolean {
        if (find(to) != null) return false

        val source = find(from) ?: return false
        if (!source.renameTo(to)) return false

        return find(to)?.isFile == true
    }

    override fun length(name: String): Long =
        find(name)
            ?.takeIf(DocumentFile::isFile)
            ?.length()
            ?: -1L

    override fun names(): Set<String> =
        directory.listFiles()
            .filter(DocumentFile::isFile)
            .mapNotNullTo(linkedSetOf()) {
                it.name
            }

    private fun find(name: String): DocumentFile? =
        directory.findFile(name)

    private fun mimeType(name: String): String =
        when (name.substringAfterLast('.', "").lowercase()) {
            "json" -> "application/json"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
}

private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= limit) { "info.json слишком большой" }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
