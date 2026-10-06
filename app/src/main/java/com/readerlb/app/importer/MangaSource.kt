package com.readerlb.app.importer

import com.readerlb.app.storage.detectMangaImageFormat
import com.readerlb.app.storage.naturalPageCompare
import java.io.InputStream
import java.util.zip.ZipInputStream

internal data class ComicFileHint(val volume: String, val number: String, val chapterTitle: String)
internal data class ComicZipInspection(val pageNames: List<String>) {
    val pageCount: Int get() = pageNames.size
}

internal fun parseComicFileHint(fileName: String): ComicFileHint {
    val base = fileName.substringBeforeLast('.', fileName).trim()
    val exact = Regex("^(\\d+)_-_([0-9]+(?:[.,][0-9]+)?)(?:_(.+))?$").matchEntire(base)
    if (exact != null) return ComicFileHint(
        exact.groupValues[1], exact.groupValues[2].replace(',', '.'), exact.groupValues[3].replace('_', ' ').trim()
    )
    val common = Regex("(?i)^v?(\\d+)[-_ ]+(?:ch|chapter|глава)?[-_ ]*(\\d+(?:[.,]\\d+)?)(?:[-_ ]+(.+))?$").matchEntire(base)
    if (common != null) return ComicFileHint(
        common.groupValues[1], common.groupValues[2].replace(',', '.'), common.groupValues[3].replace('_', ' ').trim()
    )
    return ComicFileHint("1", "1", base.replace('_', ' '))
}

internal fun inspectComicZip(input: InputStream): ComicZipInspection {
    val names = HashSet<String>()
    val pages = ArrayList<String>()
    var totalBytes = 0L
    ZipInputStream(input.buffered()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            require(!entry.name.startsWith('/') && entry.name.split('/').none { it == "." || it == ".." }) {
                "Небезопасный путь внутри CBZ"
            }
            require(names.add(entry.name)) { "Повторяющееся имя файла внутри CBZ" }
            require(names.size <= 10_000) { "Слишком много файлов внутри CBZ" }
            if (!entry.isDirectory) {
                val magic = ByteArray(16)
                var copied = 0
                var entryBytes = 0L
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = zip.read(buffer)
                    if (n < 0) break
                    if (copied < magic.size) {
                        val take = minOf(n, magic.size - copied)
                        buffer.copyInto(magic, copied, 0, take)
                        copied += take
                    }
                    entryBytes += n
                    totalBytes += n
                    require(entryBytes <= 32L * 1024 * 1024 && totalBytes <= 400L * 1024 * 1024) {
                        "CBZ слишком большой"
                    }
                }
                if (detectMangaImageFormat(magic.copyOf(copied)) != null) {
                    pages += entry.name
                    require(pages.size <= 2_000) { "Слишком много страниц в CBZ" }
                }
            }
            zip.closeEntry()
        }
    }
    require(pages.isNotEmpty()) { "В CBZ нет поддерживаемых изображений" }
    pages.sortWith(::naturalPageCompare)
    return ComicZipInspection(pages)
}
