package com.readerlb.app.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

internal data class MangaChapter(val id: Long, val volume: String, val number: String, val name: String, val archiveName: String)
internal data class MangaPage(val name: String, val format: String, val width: Int, val height: Int, val bytes: ByteArray) {
    val isLandscape: Boolean get() = width > height
}

internal fun parseMangaChapters(json: String): List<MangaChapter> {
    val array = JSONArray(json)
    return (0 until array.length()).map { index ->
        val chapter = array.getJSONObject(index)
        val id = chapter.getLong("id")
        val volume = chapter.getString("volume")
        val number = chapter.getString("number")
        MangaChapter(id, volume, number, chapter.optString("name"), "v$volume-n$number-$id.zip")
    }
}

internal fun inspectMangaChapterArchive(input: InputStream): List<MangaPage> {
    val pages = ArrayList<MangaPage>()
    val names = HashSet<String>()
    ZipInputStream(input.buffered()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            require(!entry.name.startsWith('/') && entry.name.split('/').none { it == ".." || it == "." }) {
                "Unsafe manga ZIP entry"
            }
            require(names.add(entry.name)) { "Duplicate manga ZIP entry" }
            require(names.size <= 10000) { "Too many manga ZIP entries" }
            if (!entry.isDirectory) {
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(65536)
                while (true) {
                    val read = zip.read(buffer)
                    if (read < 0) break
                    require(out.size() + read <= 32 * 1024 * 1024) { "Manga page too large" }
                    out.write(buffer, 0, read)
                }
                val bytes = out.toByteArray()
                val format = detectMangaImageFormat(bytes)
                if (format != null) {
                    val (width, height) = if (format == "avif") avifDimensions(bytes) else 0 to 0
                    pages += MangaPage(entry.name, format, width, height, bytes)
                }
            }
            zip.closeEntry()
        }
    }
    require(pages.isNotEmpty()) { "Manga chapter has no images" }
    return pages.sortedWith { a, b -> naturalPageCompare(a.name, b.name) }
}

internal fun detectMangaImageFormat(bytes: ByteArray): String? {
    if (bytes.size >= 12 && bytes.copyOfRange(4, 8).toString(Charsets.US_ASCII) == "ftyp" &&
        bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) in setOf("avif", "avis")) return "avif"
    if (bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a))) return "png"
    if (bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()) return "jpeg"
    if (bytes.size >= 12 && bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII) == "RIFF" && bytes.copyOfRange(8, 12).toString(Charsets.US_ASCII) == "WEBP") return "webp"
    return null
}

internal fun avifDimensions(bytes: ByteArray): Pair<Int, Int> {
    // ISO BMFF image spatial extents property: ispe + version/flags + width + height.
    for (i in 4 until bytes.size - 16) {
        if (bytes[i] == 'i'.code.toByte() && bytes[i+1] == 's'.code.toByte() &&
            bytes[i+2] == 'p'.code.toByte() && bytes[i+3] == 'e'.code.toByte()) {
            fun u32(offset: Int): Int = (bytes[offset].toInt() and 255 shl 24) or
                (bytes[offset+1].toInt() and 255 shl 16) or
                (bytes[offset+2].toInt() and 255 shl 8) or (bytes[offset+3].toInt() and 255)
            val width = u32(i + 8)
            val height = u32(i + 12)
            if (width in 1..100000 && height in 1..100000) return width to height
        }
    }
    return 0 to 0
}

internal fun naturalPageCompare(a: String, b: String): Int {
    val x = Regex("\\d+|\\D+").findAll(a).map { it.value }.toList()
    val y = Regex("\\d+|\\D+").findAll(b).map { it.value }.toList()
    for (i in 0 until minOf(x.size, y.size)) {
        val left = x[i]; val right = y[i]
        val comparison = if (left.first().isDigit() && right.first().isDigit()) {
            val l = left.trimStart('0').ifEmpty { "0" }; val r = right.trimStart('0').ifEmpty { "0" }
            compareValues(l.length, r.length).takeIf { it != 0 } ?: l.compareTo(r)
        } else left.compareTo(right, ignoreCase = true)
        if (comparison != 0) return comparison
    }
    return compareValues(x.size, y.size).takeIf { it != 0 } ?: a.compareTo(b)
}

internal class LocalMangaReader(private val context: Context) {
    private fun title(item: LocalLibraryItem): DocumentFile =
        requireNotNull(DocumentFile.fromTreeUri(context, ShizukuAccess.mangaTreeUri)?.findFile(item.folderName)) {
            "Manga title folder missing"
        }

    fun chapters(item: LocalLibraryItem): List<MangaChapter> {
        val file = requireNotNull(title(item).findFile("chapters.json")) { "chapters.json missing" }
        val text = context.contentResolver.openInputStream(file.uri)!!.bufferedReader().use { it.readText() }
        return parseMangaChapters(text)
    }

    fun pages(item: LocalLibraryItem, chapter: MangaChapter): List<MangaPage> {
        val file = requireNotNull(title(item).findFile(chapter.archiveName)) { "Manga chapter ZIP missing" }
        return context.contentResolver.openInputStream(file.uri)!!.use(::inspectMangaChapterArchive)
    }
}
