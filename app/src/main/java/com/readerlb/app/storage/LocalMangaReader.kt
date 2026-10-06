package com.readerlb.app.storage

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.ZipInputStream

internal data class MangaChapter(
    val id: Long,
    val volume: String,
    val number: String,
    val name: String,
    val archiveName: String
)

internal data class MangaPageInfo(
    val name: String,
    val format: String
)

internal data class MangaPage(
    val name: String,
    val format: String,
    val width: Int,
    val height: Int,
    val bytes: ByteArray
) {
    val isLandscape: Boolean
        get() = width > height
}

internal data class StagedMangaPage(
    val name: String,
    val format: String,
    val file: File
)

private const val MAX_MANGA_ARCHIVE_ENTRIES = 10_000
private const val MAX_MANGA_PAGE_BYTES = 32 * 1024 * 1024
private const val MAX_STAGED_CHAPTER_BYTES = 768L * 1024 * 1024
private const val IMAGE_SIGNATURE_BYTES = 16

internal fun parseMangaChapters(
    json: String
): List<MangaChapter> {
    val array = JSONArray(json)
    return (0 until array.length()).map {
            index ->
        val chapter =
            array.getJSONObject(
                index
            )
        val id =
            chapter.getLong(
                "id"
            )
        val volume =
            chapter.getString(
                "volume"
            )
        val number =
            chapter.getString(
                "number"
            )
        MangaChapter(
            id,
            volume,
            number,
            chapter.optString(
                "name"
            ),
            "v$volume-n$number-$id.zip"
        )
    }
}

internal fun inspectMangaChapterArchive(
    input: InputStream
): List<MangaPage> {
    val pages =
        ArrayList<MangaPage>()
    val names =
        HashSet<String>()

    ZipInputStream(
        input.buffered()
    ).use {
            zip ->
        while (true) {
            val entry =
                zip.nextEntry
                    ?: break
            validateMangaZipEntry(
                entry.name,
                names
            )
            if (!entry.isDirectory) {
                val bytes =
                    readBoundedMangaEntry(
                        zip
                    )
                val format =
                    detectMangaImageFormat(
                        bytes
                    )
                if (format != null) {
                    val dimensions =
                        if (format == "avif") {
                            avifDimensions(
                                bytes
                            )
                        } else {
                            0 to 0
                        }
                    pages +=
                        MangaPage(
                            entry.name,
                            format,
                            dimensions.first,
                            dimensions.second,
                            bytes
                        )
                }
            }
            zip.closeEntry()
        }
    }

    require(pages.isNotEmpty()) {
        "Manga chapter has no images"
    }
    return pages.sortedWith {
            a,
            b ->
        naturalPageCompare(
            a.name,
            b.name
        )
    }
}

internal fun inspectMangaChapterPageInfos(
    input: InputStream
): List<MangaPageInfo> {
    val pages =
        ArrayList<MangaPageInfo>()
    val names =
        HashSet<String>()

    ZipInputStream(
        input.buffered()
    ).use {
            zip ->
        while (true) {
            val entry =
                zip.nextEntry
                    ?: break
            validateMangaZipEntry(
                entry.name,
                names
            )
            if (!entry.isDirectory) {
                if (
                    entry.size >
                    MAX_MANGA_PAGE_BYTES
                ) {
                    error(
                        "Manga page too large"
                    )
                }
                val prefix =
                    readPrefix(
                        zip,
                        IMAGE_SIGNATURE_BYTES
                    )
                val format =
                    detectMangaImageFormat(
                        prefix
                    )
                if (format != null) {
                    pages +=
                        MangaPageInfo(
                            entry.name,
                            format
                        )
                }
            }
            zip.closeEntry()
        }
    }

    require(pages.isNotEmpty()) {
        "Manga chapter has no images"
    }
    return pages.sortedWith {
            a,
            b ->
        naturalPageCompare(
            a.name,
            b.name
        )
    }
}

internal fun readMangaPageEntry(
    input: InputStream,
    page: MangaPageInfo
): MangaPage {
    val names =
        HashSet<String>()

    ZipInputStream(
        input.buffered()
    ).use {
            zip ->
        while (true) {
            val entry =
                zip.nextEntry
                    ?: break
            validateMangaZipEntry(
                entry.name,
                names
            )
            if (
                !entry.isDirectory &&
                entry.name == page.name
            ) {
                val bytes =
                    readBoundedMangaEntry(
                        zip
                    )
                val format =
                    requireNotNull(
                        detectMangaImageFormat(
                            bytes
                        )
                    ) {
                        "Manga page is not an image: ${page.name}"
                    }
                require(
                    format == page.format
                ) {
                    "Manga page format changed: ${page.name}"
                }
                val dimensions =
                    if (format == "avif") {
                        avifDimensions(
                            bytes
                        )
                    } else {
                        0 to 0
                    }
                return MangaPage(
                    entry.name,
                    format,
                    dimensions.first,
                    dimensions.second,
                    bytes
                )
            }
            zip.closeEntry()
        }
    }

    error(
        "Manga page missing: ${page.name}"
    )
}

internal fun stageMangaChapterArchive(
    input: InputStream,
    directory: File
): List<StagedMangaPage> {
    require(
        directory.isDirectory ||
            directory.mkdirs()
    ) {
        "Cannot create manga staging directory"
    }

    val pages =
        ArrayList<StagedMangaPage>()
    val names =
        HashSet<String>()
    var chapterBytes = 0L

    try {
        ZipInputStream(
            input.buffered()
        ).use {
                zip ->
            while (true) {
                val entry =
                    zip.nextEntry
                        ?: break
                validateMangaZipEntry(
                    entry.name,
                    names
                )
                if (!entry.isDirectory) {
                    val staged =
                        File.createTempFile(
                            "page-",
                            ".bin",
                            directory
                        )
                    var entryBytes = 0L
                    try {
                        staged.outputStream()
                            .buffered()
                            .use {
                                    output ->
                                val buffer =
                                    ByteArray(
                                        64 * 1024
                                    )
                                while (true) {
                                    val read =
                                        zip.read(
                                            buffer
                                        )
                                    if (read < 0) {
                                        break
                                    }
                                    entryBytes += read
                                    chapterBytes += read
                                    require(
                                        entryBytes <=
                                            MAX_MANGA_PAGE_BYTES
                                    ) {
                                        "Manga page too large"
                                    }
                                    require(
                                        chapterBytes <=
                                            MAX_STAGED_CHAPTER_BYTES
                                    ) {
                                        "Manga chapter too large to stage"
                                    }
                                    output.write(
                                        buffer,
                                        0,
                                        read
                                    )
                                }
                            }

                        val prefix =
                            staged.inputStream()
                                .use {
                                    readPrefix(
                                        it,
                                        IMAGE_SIGNATURE_BYTES
                                    )
                                }
                        val format =
                            detectMangaImageFormat(
                                prefix
                            )
                        if (format != null) {
                            pages +=
                                StagedMangaPage(
                                    entry.name,
                                    format,
                                    staged
                                )
                        } else {
                            staged.delete()
                        }
                    } catch (
                        throwable: Throwable
                    ) {
                        staged.delete()
                        throw throwable
                    }
                }
                zip.closeEntry()
            }
        }

        require(pages.isNotEmpty()) {
            "Manga chapter has no images"
        }
        return pages.sortedWith {
                a,
                b ->
            naturalPageCompare(
                a.name,
                b.name
            )
        }
    } catch (throwable: Throwable) {
        directory.deleteRecursively()
        throw throwable
    }
}

private fun validateMangaZipEntry(
    name: String,
    names: MutableSet<String>
) {
    require(
        !name.startsWith('/') &&
            name.split('/')
                .none {
                    it == ".." ||
                        it == "."
                }
    ) {
        "Unsafe manga ZIP entry"
    }
    require(
        names.add(
            name
        )
    ) {
        "Duplicate manga ZIP entry"
    }
    require(
        names.size <=
            MAX_MANGA_ARCHIVE_ENTRIES
    ) {
        "Too many manga ZIP entries"
    }
}

private fun readBoundedMangaEntry(
    input: InputStream
): ByteArray {
    val out =
        ByteArrayOutputStream()
    val buffer =
        ByteArray(
            64 * 1024
        )
    while (true) {
        val read =
            input.read(
                buffer
            )
        if (read < 0) {
            break
        }
        require(
            out.size() + read <=
                MAX_MANGA_PAGE_BYTES
        ) {
            "Manga page too large"
        }
        out.write(
            buffer,
            0,
            read
        )
    }
    return out.toByteArray()
}

private fun readPrefix(
    input: InputStream,
    limit: Int
): ByteArray {
    val bytes =
        ByteArray(
            limit
        )
    var size = 0
    while (size < limit) {
        val read =
            input.read(
                bytes,
                size,
                limit - size
            )
        if (read < 0) {
            break
        }
        size += read
    }
    return bytes.copyOf(
        size
    )
}

internal fun detectMangaImageFormat(
    bytes: ByteArray
): String? {
    if (
        bytes.size >= 12 &&
        bytes.copyOfRange(
            4,
            8
        ).toString(
            Charsets.US_ASCII
        ) == "ftyp" &&
        bytes.copyOfRange(
            8,
            12
        ).toString(
            Charsets.US_ASCII
        ) in setOf(
            "avif",
            "avis"
        )
    ) {
        return "avif"
    }
    if (
        bytes.size >= 8 &&
        bytes.copyOfRange(
            0,
            8
        ).contentEquals(
            byteArrayOf(
                0x89.toByte(),
                0x50,
                0x4e,
                0x47,
                0x0d,
                0x0a,
                0x1a,
                0x0a
            )
        )
    ) {
        return "png"
    }
    if (
        bytes.size >= 3 &&
        bytes[0] ==
            0xff.toByte() &&
        bytes[1] ==
            0xd8.toByte() &&
        bytes[2] ==
            0xff.toByte()
    ) {
        return "jpeg"
    }
    if (
        bytes.size >= 12 &&
        bytes.copyOfRange(
            0,
            4
        ).toString(
            Charsets.US_ASCII
        ) == "RIFF" &&
        bytes.copyOfRange(
            8,
            12
        ).toString(
            Charsets.US_ASCII
        ) == "WEBP"
    ) {
        return "webp"
    }
    return null
}

internal fun avifDimensions(
    bytes: ByteArray
): Pair<Int, Int> {
    for (
        i in 4 until
            bytes.size - 16
    ) {
        if (
            bytes[i] ==
                'i'.code.toByte() &&
            bytes[i + 1] ==
                's'.code.toByte() &&
            bytes[i + 2] ==
                'p'.code.toByte() &&
            bytes[i + 3] ==
                'e'.code.toByte()
        ) {
            fun u32(
                offset: Int
            ): Int =
                (
                    bytes[offset]
                        .toInt() and
                        255 shl 24
                    ) or
                    (
                        bytes[offset + 1]
                            .toInt() and
                            255 shl 16
                        ) or
                    (
                        bytes[offset + 2]
                            .toInt() and
                            255 shl 8
                        ) or
                    (
                        bytes[offset + 3]
                            .toInt() and
                            255
                        )
            val width =
                u32(
                    i + 8
                )
            val height =
                u32(
                    i + 12
                )
            if (
                width in 1..100000 &&
                height in 1..100000
            ) {
                return width to height
            }
        }
    }
    return 0 to 0
}

internal fun naturalPageCompare(
    a: String,
    b: String
): Int {
    val x =
        Regex(
            "\\d+|\\D+"
        ).findAll(
            a
        ).map {
            it.value
        }.toList()
    val y =
        Regex(
            "\\d+|\\D+"
        ).findAll(
            b
        ).map {
            it.value
        }.toList()

    for (
        i in 0 until
            minOf(
                x.size,
                y.size
            )
    ) {
        val left = x[i]
        val right = y[i]
        val comparison =
            if (
                left.first().isDigit() &&
                right.first().isDigit()
            ) {
                val l =
                    left.trimStart(
                        '0'
                    ).ifEmpty {
                        "0"
                    }
                val r =
                    right.trimStart(
                        '0'
                    ).ifEmpty {
                        "0"
                    }
                compareValues(
                    l.length,
                    r.length
                ).takeIf {
                    it != 0
                } ?: l.compareTo(
                    r
                )
            } else {
                left.compareTo(
                    right,
                    ignoreCase = true
                )
            }
        if (comparison != 0) {
            return comparison
        }
    }

    return compareValues(
        x.size,
        y.size
    ).takeIf {
        it != 0
    } ?: a.compareTo(
        b
    )
}

internal class LocalMangaReader(
    private val context: Context
) {
    private fun title(
        item: LocalLibraryItem
    ): DocumentFile =
        requireNotNull(
            DocumentFile.fromTreeUri(
                context,
                ShizukuAccess.mangaTreeUri
            )?.findFile(
                item.folderName
            )
        ) {
            "Manga title folder missing"
        }

    private fun chapterFile(
        item: LocalLibraryItem,
        chapter: MangaChapter
    ): DocumentFile =
        requireNotNull(
            title(item).findFile(
                chapter.archiveName
            )
        ) {
            "Manga chapter ZIP missing"
        }

    fun chapters(
        item: LocalLibraryItem
    ): List<MangaChapter> {
        val file =
            requireNotNull(
                title(item).findFile(
                    "chapters.json"
                )
            ) {
                "chapters.json missing"
            }
        val text =
            context.contentResolver
                .openInputStream(
                    file.uri
                )!!
                .bufferedReader()
                .use {
                    it.readText()
                }
        return parseMangaChapters(
            text
        )
    }

    fun pageInfos(
        item: LocalLibraryItem,
        chapter: MangaChapter
    ): List<MangaPageInfo> {
        val file =
            chapterFile(
                item,
                chapter
            )
        return context.contentResolver
            .openInputStream(
                file.uri
            )!!
            .use(
                ::inspectMangaChapterPageInfos
            )
    }

    fun readPage(
        item: LocalLibraryItem,
        chapter: MangaChapter,
        page: MangaPageInfo
    ): MangaPage {
        val file =
            chapterFile(
                item,
                chapter
            )
        return context.contentResolver
            .openInputStream(
                file.uri
            )!!
            .use {
                input ->
                readMangaPageEntry(
                    input,
                    page
                )
            }
    }

    fun forEachPage(
        item: LocalLibraryItem,
        chapter: MangaChapter,
        block: (
            MangaPage
        ) -> Unit
    ) {
        val file =
            chapterFile(
                item,
                chapter
            )
        val stagingRoot =
            File(
                context.cacheDir,
                "manga-export"
            ).apply {
                mkdirs()
            }
        val stagingDirectory =
            Files.createTempDirectory(
                stagingRoot.toPath(),
                "chapter-"
            ).toFile()

        try {
            val staged =
                context.contentResolver
                    .openInputStream(
                        file.uri
                    )!!
                    .use {
                        input ->
                        stageMangaChapterArchive(
                            input,
                            stagingDirectory
                        )
                    }

            staged.forEach {
                    page ->
                val bytes =
                    page.file.readBytes()
                val dimensions =
                    if (
                        page.format ==
                        "avif"
                    ) {
                        avifDimensions(
                            bytes
                        )
                    } else {
                        0 to 0
                    }
                block(
                    MangaPage(
                        page.name,
                        page.format,
                        dimensions.first,
                        dimensions.second,
                        bytes
                    )
                )
            }
        } finally {
            stagingDirectory
                .deleteRecursively()
        }
    }
}
