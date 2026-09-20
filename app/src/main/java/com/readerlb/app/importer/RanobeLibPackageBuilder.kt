package com.readerlb.app.importer

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

data class BuiltRanobeLibPackage(
    val rootDir: File,
    val titleDir: File,
    val title: String,
    val chapterCount: Int,
    val firstChapter: String,
    val lastChapter: String,
    val slugUrl: String
)

data class PackageVerificationReport(
    val errors: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()
}

class PackageVerificationException(
    val report: PackageVerificationReport
) : IllegalStateException(
    buildString {
        append("Проверка пакета RanobeLib не пройдена")
        if (report.errors.isNotEmpty()) {
            append(": ")
            append(report.errors.joinToString("; "))
        }
    }
)

/**
 * Pure filesystem builder for the local RanobeLib book format.
 *
 * Android storage APIs intentionally do not live here. This allows the exact
 * package generation and verification path to run in ordinary JVM tests.
 */
class RanobeLibPackageBuilder(
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    private val chapterImageExtensions = setOf(
        "jpg",
        "png",
        "webp",
        "gif"
    )


    fun build(
        book: ParsedBook,
        rootDir: File,
        titleOverride: String = "",
        firstChapter: String? = null,
        lastChapter: String? = null,
        onChapterPrepared: (
            completed: Int,
            total: Int
        ) -> Unit = { _, _ -> },
        onVerifying: () -> Unit = {}
    ): BuiltRanobeLibPackage {
        val firstValue = firstChapter
            ?.takeIf(String::isNotBlank)
            ?.let(::chapterNumberDecimal)
        val lastValue = lastChapter
            ?.takeIf(String::isNotBlank)
            ?.let(::chapterNumberDecimal)

        if (firstChapter?.isNotBlank() == true) {
            require(firstValue != null) {
                "Некорректный номер начальной главы"
            }
        }
        if (lastChapter?.isNotBlank() == true) {
            require(lastValue != null) {
                "Некорректный номер конечной главы"
            }
        }
        if (firstValue != null && lastValue != null) {
            require(firstValue <= lastValue) {
                "Начальная глава не может быть больше конечной"
            }
        }

        val title = titleOverride.trim().ifBlank { book.title.trim() }
        require(title.isNotBlank()) { "Название новеллы не может быть пустым" }

        val invalidNumbers = book.chapters
            .map { it.number }
            .filter {
                chapterNumberDecimal(it) == null
            }
            .distinct()
        require(invalidNumbers.isEmpty()) {
            "Неподдерживаемые номера глав: " +
                invalidNumbers.take(20).joinToString()
        }

        val duplicateSourceNumbers = book.chapters
            .groupingBy { it.number }
            .eachCount()
            .filterValues { it > 1 }
            .keys
            .sortedWith(::compareChapterNumbers)
        require(duplicateSourceNumbers.isEmpty()) {
            "В исходных данных повторяются номера глав: " +
                duplicateSourceNumbers.take(20).joinToString()
        }

        val selected = book.chapters
            .filter { chapter ->
                chapterNumberInRange(
                    value = chapter.number,
                    first = firstChapter,
                    last = lastChapter
                )
            }
            .sortedWith { left, right ->
                compareChapterNumbers(
                    left.number,
                    right.number
                )
            }

        require(selected.isNotEmpty()) {
            "В выбранный диапазон не попало ни одной главы"
        }

        onChapterPrepared(
            0,
            selected.size
        )

        val mediaId = stableMediaId(title)
        val slug = slugify(title).ifBlank { "local-book-$mediaId" }
        val slugUrl = "$mediaId--$slug"

        val titleDir = File(rootDir, "book/$slugUrl")
        if (titleDir.exists()) {
            require(titleDir.deleteRecursively()) {
                "Не удалось очистить временную папку пакета"
            }
        }
        require(titleDir.mkdirs()) {
            "Не удалось создать временную папку пакета"
        }

        val coverName = writeCover(book, titleDir)
        val coverUri = coverName?.let {
            "file:///storage/emulated/0/Android/data/ru.libappc/files/book/$slugUrl/$it"
        }.orEmpty()

        val generatedAt = nowMillis()
        val chaptersJson = JSONArray()

        selected.forEachIndexed { index, chapter ->
            val chapterId = chapterId(mediaId, chapter.number)
            val zipName = chapterZipName(chapter.number, chapterId)

            val imageFiles =
                linkedMapOf<String, ChapterImageFile>()
            val document = readerDocument(
                chapter = chapter,
                imageFiles = imageFiles
            )

            ZipOutputStream(
                File(titleDir, zipName).outputStream().buffered()
            ).use { zip ->
                zip.putNextEntry(ZipEntry("data.txt"))
                zip.write(
                    document
                        .toString()
                        .toByteArray(Charsets.UTF_8)
                )
                zip.closeEntry()

                imageFiles.forEach { (id, image) ->
                    zip.putNextEntry(
                        ZipEntry(
                            "$id.${image.extension}"
                        )
                    )
                    val sourcePath = image.filePath
                    if (sourcePath != null) {
                        val source = File(sourcePath)
                        require(
                            source.isFile &&
                                source.length() > 0L
                        ) {
                            "Временный файл иллюстрации отсутствует"
                        }
                        source.inputStream()
                            .buffered()
                            .use {
                                it.copyTo(zip)
                            }
                    } else {
                        val bytes = requireNotNull(
                            image.bytes
                        ) {
                            "У иллюстрации нет данных"
                        }
                        require(bytes.isNotEmpty()) {
                            "Иллюстрация не содержит данных"
                        }
                        zip.write(bytes)
                    }
                    zip.closeEntry()
                }
            }

            val branch = JSONObject()
                .put("id", chapterId)
                .put("branchId", -1)
                .put("dateMillis", generatedAt + index)
                .put("teams", JSONArray())
                .put(
                    "user",
                    JSONObject()
                        .put("id", 0)
                        .put("username", "ReaderLB")
                )
                .put("notify", false)

            chaptersJson.put(
                JSONObject()
                    .put("id", chapterId)
                    .put("volume", "1")
                    .put("number", chapter.number)
                    .put("name", chapter.title)
                    .put("itemNumber", index + 1)
                    .put("branches", JSONArray().put(branch))
                    .put("withBranches", false)
                    .put("totalBranchesSize", 1)
            )

            onChapterPrepared(
                index + 1,
                selected.size
            )
        }

        File(titleDir, "chapters.json")
            .writeText(chaptersJson.toString(), Charsets.UTF_8)

        File(titleDir, "info.json").writeText(
            infoJson(
                mediaId = mediaId,
                slug = slug,
                slugUrl = slugUrl,
                title = title,
                book = book,
                chapterCount = selected.size,
                coverUri = coverUri,
                generatedAt = generatedAt
            ).toString(),
            Charsets.UTF_8
        )

        val built = BuiltRanobeLibPackage(
            rootDir = rootDir,
            titleDir = titleDir,
            title = title,
            chapterCount = selected.size,
            firstChapter = selected.first().number,
            lastChapter = selected.last().number,
            slugUrl = slugUrl
        )

        onVerifying()

        val report = verify(
            built = built,
            expectedChapterNumbers = selected.map { it.number }
        )
        if (!report.isValid) throw PackageVerificationException(report)

        return built
    }

    fun verify(
        built: BuiltRanobeLibPackage,
        expectedChapterNumbers: List<String>? = null
    ): PackageVerificationReport {
        val errors = mutableListOf<String>()
        val titleDir = built.titleDir

        if (!titleDir.isDirectory) {
            return PackageVerificationReport(
                listOf("Папка тайтла не существует")
            )
        }

        val infoFile = File(titleDir, "info.json")
        val chaptersFile = File(titleDir, "chapters.json")

        if (!infoFile.isFile || infoFile.length() == 0L) {
            errors += "info.json отсутствует или пуст"
        }
        if (!chaptersFile.isFile || chaptersFile.length() == 0L) {
            errors += "chapters.json отсутствует или пуст"
        }

        val info = runCatching {
            JSONObject(infoFile.readText(Charsets.UTF_8))
        }.getOrElse {
            errors += "info.json не является валидным JSON"
            null
        }

        val chapters = runCatching {
            JSONArray(chaptersFile.readText(Charsets.UTF_8))
        }.getOrElse {
            errors += "chapters.json не является валидным JSON"
            null
        }

        if (info != null) {
            val media = info.optJSONObject("media")
            if (media == null) {
                errors += "info.json не содержит media"
            } else {
                if (media.optString("slugUrl") != built.slugUrl) {
                    errors += "slugUrl в info.json не совпадает с папкой"
                }
                if (media.optInt("uploadedCount", -1) != built.chapterCount) {
                    errors += "uploadedCount не совпадает с числом глав"
                }

                val cover = media.optString("imageUrl")
                if (cover.isNotBlank()) {
                    val coverName = cover.substringAfterLast('/')
                    val coverFile = File(titleDir, coverName)
                    if (!coverFile.isFile || coverFile.length() == 0L) {
                        errors += "Обложка указана в info.json, но файл отсутствует"
                    }
                }
            }
        }

        val actualNumbers = mutableListOf<String>()

        if (chapters != null) {
            if (chapters.length() != built.chapterCount) {
                errors += "chapters.json содержит ${chapters.length()} глав вместо ${built.chapterCount}"
            }

            val seenNumbers = mutableSetOf<String>()
            val seenIds = mutableSetOf<Long>()

            for (index in 0 until chapters.length()) {
                val chapter = chapters.optJSONObject(index)
                if (chapter == null) {
                    errors += "Элемент chapters.json #${index + 1} не является объектом"
                    continue
                }

                val number = chapter
                    .optString("number")
                    .trim()
                if (number.isBlank()) {
                    errors += "У главы #${index + 1} отсутствует номер"
                    continue
                }
                if (chapterNumberDecimal(number) == null) {
                    errors += "У главы #${index + 1} некорректный номер: $number"
                    continue
                }
                actualNumbers += number

                if (!seenNumbers.add(number)) {
                    errors += "В chapters.json повторяется глава $number"
                }

                val id = chapter.optLong("id", Long.MIN_VALUE)
                if (id == Long.MIN_VALUE) {
                    errors += "У главы $number отсутствует id"
                    continue
                }
                if (!seenIds.add(id)) {
                    errors += "В chapters.json повторяется id $id"
                }

                val zipFile = File(
                    titleDir,
                    chapterZipName(number, id)
                )

                verifyChapterZip(
                    zipFile = zipFile,
                    chapterNumber = number,
                    errors = errors
                )
            }
        }

        if (expectedChapterNumbers != null) {
            if (actualNumbers != expectedChapterNumbers) {
                errors += "Список номеров глав после сборки не совпадает с исходным"
            }
        }

        val zipFiles = titleDir.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("zip", true) }

        if (zipFiles.size != built.chapterCount) {
            errors += "В папке ${zipFiles.size} ZIP глав вместо ${built.chapterCount}"
        }

        return PackageVerificationReport(errors.distinct())
    }

    private fun verifyChapterZip(
        zipFile: File,
        chapterNumber: String,
        errors: MutableList<String>
    ) {
        if (!zipFile.isFile || zipFile.length() == 0L) {
            errors += "ZIP главы $chapterNumber отсутствует или пуст"
            return
        }

        runCatching {
            ZipFile(zipFile).use { zip ->
                val entries = zip.entries().toList()
                val dataEntry = entries.firstOrNull {
                    it.name == "data.txt"
                }
                if (dataEntry == null) {
                    errors += "ZIP главы $chapterNumber не содержит data.txt"
                    return@use
                }

                val imageEntries = entries
                    .filterNot {
                        it.isDirectory ||
                            it.name == "data.txt"
                    }
                    .filter {
                        val extension = it.name
                            .substringAfterLast(
                                '.',
                                ""
                            )
                            .lowercase()

                        !it.name.contains('/') &&
                            extension in chapterImageExtensions
                    }

                val unexpected = entries.filterNot {
                    it.isDirectory ||
                        it.name == "data.txt" ||
                        it in imageEntries
                }
                if (unexpected.isNotEmpty()) {
                    errors +=
                        "ZIP главы $chapterNumber содержит лишние файлы"
                }

                imageEntries.forEach { entry ->
                    if (entry.size == 0L) {
                        errors +=
                            "Иллюстрация " + entry.name +
                            " в главе $chapterNumber пуста"
                    }
                }

                val data = zip.getInputStream(dataEntry).use {
                    it.readBytes().toString(Charsets.UTF_8)
                }
                val document = JSONObject(data)

                if (document.optString("type") != "doc") {
                    errors +=
                        "data.txt главы $chapterNumber имеет неверный корневой type"
                }
                if (document.optJSONArray("content") == null) {
                    errors +=
                        "data.txt главы $chapterNumber не содержит content"
                }

                val referencedImages =
                    collectReferencedImageIds(document)
                val fileIds = imageEntries
                    .map {
                        it.name.substringBeforeLast('.')
                    }
                    .toSet()

                val missingImages =
                    referencedImages - fileIds
                if (missingImages.isNotEmpty()) {
                    errors +=
                        "В главе $chapterNumber отсутствуют файлы " +
                        "иллюстраций: " +
                        missingImages
                            .take(5)
                            .joinToString()
                }

                val orphanImages =
                    fileIds - referencedImages
                if (orphanImages.isNotEmpty()) {
                    errors +=
                        "В ZIP главы $chapterNumber есть " +
                        "неиспользуемые иллюстрации"
                }
            }
        }.onFailure {
            errors +=
                "ZIP главы $chapterNumber повреждён: " +
                (it.message ?: it.javaClass.simpleName)
        }
    }

    private fun collectReferencedImageIds(
        document: JSONObject
    ): Set<String> {
        val result = linkedSetOf<String>()

        fun walkArray(array: JSONArray) {
            for (index in 0 until array.length()) {
                val node = array.optJSONObject(index)
                    ?: continue

                if (node.optString("type") == "image") {
                    val images = node
                        .optJSONObject("attrs")
                        ?.optJSONArray("images")

                    if (images != null) {
                        for (
                            imageIndex in 0 until
                                images.length()
                        ) {
                            images
                                .optJSONObject(imageIndex)
                                ?.optString("image")
                                ?.trim()
                                ?.takeIf(
                                    String::isNotBlank
                                )
                                ?.let(result::add)
                        }
                    }
                }

                node.optJSONArray("content")
                    ?.let(::walkArray)
            }
        }

        document.optJSONArray("content")
            ?.let(::walkArray)

        return result
    }

    private fun writeCover(
        book: ParsedBook,
        titleDir: File
    ): String? {
        val bytes = book.coverBytes ?: return null
        if (bytes.isEmpty()) return null

        val extension = when (book.coverExtension.lowercase()) {
            "jpeg" -> "jpg"
            "jpg", "png", "webp" -> book.coverExtension.lowercase()
            else -> "jpg"
        }

        val name = "cover.$extension"
        File(titleDir, name).writeBytes(bytes)
        return name
    }

    private fun readerDocument(
        chapter: ParsedChapter,
        imageFiles: MutableMap<String, ChapterImageFile>
    ): JSONObject {
        val content = JSONArray()

        chapter.blocks.forEach { block ->
            when (block) {
                is ReaderBlock.Paragraph -> {
                    val node = JSONObject()
                        .put("type", "paragraph")

                    if (block.centered) {
                        node.put(
                            "attrs",
                            JSONObject().put("textAlign", "center")
                        )
                    }

                    if (block.text.isNotBlank()) {
                        node.put(
                            "content",
                            JSONArray().put(
                                JSONObject()
                                    .put("type", "text")
                                    .put("text", block.text)
                            )
                        )
                    }

                    content.put(node)
                }

                ReaderBlock.HorizontalRule -> {
                    content.put(
                        JSONObject().put("type", "horizontalRule")
                    )
                }

                is ReaderBlock.Quote -> {
                    val quoteContent = JSONArray()
                    block.lines
                        .filter(String::isNotBlank)
                        .forEach { line ->
                            quoteContent.put(
                                JSONObject()
                                    .put("type", "paragraph")
                                    .put(
                                        "attrs",
                                        JSONObject().put(
                                            "textAlign",
                                            "center"
                                        )
                                    )
                                    .put(
                                        "content",
                                        JSONArray().put(
                                            JSONObject()
                                                .put("type", "text")
                                                .put("text", line)
                                        )
                                    )
                            )
                        }

                    if (quoteContent.length() > 0) {
                        content.put(
                            JSONObject()
                                .put("type", "blockquote")
                                .put("content", quoteContent)
                        )
                    }
                }

                is ReaderBlock.Image -> {
                    val extension =
                        normalizeChapterImageExtension(
                            block.extension
                        )
                    val filePath = block.filePath
                        ?.takeIf(String::isNotBlank)
                    val id = imageId(
                        bytes = block.bytes,
                        filePath = filePath
                    )

                    imageFiles.putIfAbsent(
                        id,
                        ChapterImageFile(
                            extension = extension,
                            bytes = block.bytes
                                .takeIf {
                                    filePath == null
                                },
                            filePath = filePath
                        )
                    )

                    content.put(
                        JSONObject()
                            .put("type", "image")
                            .put(
                                "attrs",
                                JSONObject()
                                    .put(
                                        "description",
                                        block.description
                                            ?.takeIf(
                                                String::isNotBlank
                                            )
                                            ?: JSONObject.NULL
                                    )
                                    .put(
                                        "images",
                                        JSONArray().put(
                                            JSONObject()
                                                .put(
                                                    "image",
                                                    id
                                                )
                                        )
                                    )
                            )
                    )
                }
            }
        }

        if (content.length() == 0) {
            content.put(
                JSONObject().put("type", "paragraph")
            )
        }

        return JSONObject()
            .put("type", "doc")
            .put("content", content)
    }

    private fun normalizeChapterImageExtension(
        raw: String
    ): String {
        val extension = when (raw.lowercase()) {
            "jpeg" -> "jpg"
            else -> raw.lowercase()
        }

        require(
            extension in chapterImageExtensions
        ) {
            "Неподдерживаемый формат иллюстрации: $raw"
        }

        return extension
    }

    private data class ChapterImageFile(
        val extension: String,
        val bytes: ByteArray? = null,
        val filePath: String? = null
    )

    private fun imageId(
        bytes: ByteArray,
        filePath: String?
    ): String {
        if (filePath == null) {
            require(bytes.isNotEmpty()) {
                "Иллюстрация не содержит данных"
            }
            return UUID
                .nameUUIDFromBytes(bytes)
                .toString()
        }

        val file = File(filePath)
        require(
            file.isFile &&
                file.length() > 0L
        ) {
            "Временный файл иллюстрации отсутствует"
        }

        val digest = MessageDigest
            .getInstance("SHA-256")
        file.inputStream()
            .buffered()
            .use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(
                        buffer,
                        0,
                        count
                    )
                }
            }

        return UUID
            .nameUUIDFromBytes(
                digest.digest()
            )
            .toString()
    }

    private fun infoJson(
        mediaId: Int,
        slug: String,
        slugUrl: String,
        title: String,
        book: ParsedBook,
        chapterCount: Int,
        coverUri: String,
        generatedAt: Long
    ): JSONObject {
        val typeTitle = when (book.language.lowercase()) {
            "zh", "zh-cn", "zh-hans", "cn" -> "Китай"
            "ko", "kr" -> "Корея"
            "ja", "jp" -> "Япония"
            else -> "Другое"
        }

        val summary = book.description
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(1800)
            .ifBlank {
                "Локальный тайтл, импортированный через ReaderLB."
            }

        val media = JSONObject()
            .put("id", mediaId)
            .put("name", title)
            .put("rusName", title)
            .put("engName", title)
            .put("otherNames", JSONArray())
            .put("slug", slug)
            .put("slugUrl", slugUrl)
            .put("imageUrl", coverUri)
            .put("model", "manga")
            .put("sourceId", "3")
            .put("backgroundUrl", coverUri)
            .put("ageRestriction", tag("6+", "1"))
            .put("type", tag(typeTitle, "0"))
            .put("summary", summary)
            .put("closeView", 0)
            .put("closeComments", 0)
            .put("releaseDate", "")
            .put("views", "0")
            .put(
                "rating",
                JSONObject()
                    .put("first", "0.00")
                    .put("second", "0")
                    .put("third", 0)
            )
            .put("genres", JSONArray())
            .put("tags", JSONArray())
            .put(
                "authors",
                if (book.author.isBlank()) {
                    JSONArray()
                } else {
                    JSONArray().put(
                        JSONObject().put("name", book.author)
                    )
                }
            )
            .put("artists", JSONArray())
            .put("uploadedCount", chapterCount)
            .put("status", tag("Онгоинг", "1"))
            .put("scanlateStatus", tag("Онгоинг", "1"))
            .put("format", JSONArray().put(tag("Веб", "6")))

        return JSONObject()
            .put("media", media)
            .put("writeTime", generatedAt)
            .put("version", 1)
    }

    private fun tag(title: String, id: String): JSONObject =
        JSONObject()
            .put("title", title)
            .put("id", id)
            .put("tag", JSONObject.NULL)

    internal fun stableMediaId(title: String): Int {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(
                title
                    .lowercase()
                    .trim()
                    .toByteArray(Charsets.UTF_8)
            )

        val raw = (
            ((digest[0].toInt() and 0xff) shl 24) or
                ((digest[1].toInt() and 0xff) shl 16) or
                ((digest[2].toInt() and 0xff) shl 8) or
                (digest[3].toInt() and 0xff)
            ) and 0x7fffffff

        return 900_000_000 + (raw % 90_000_000)
    }

    internal fun slugify(value: String): String {
        val table = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d",
            'е' to "e", 'ё' to "e", 'ж' to "zh", 'з' to "z", 'и' to "i",
            'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
            'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t",
            'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "c", 'ч' to "ch",
            'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "",
            'э' to "e", 'ю' to "yu", 'я' to "ya"
        )

        val ascii = buildString {
            value.lowercase().forEach { char ->
                when {
                    char in 'a'..'z' || char in '0'..'9' -> append(char)
                    table.containsKey(char) -> append(table.getValue(char))
                    else -> append('-')
                }
            }
        }

        return ascii
            .replace(Regex("-+"), "-")
            .trim('-')
            .take(90)
    }

    private fun chapterId(
        mediaId: Int,
        chapterNumber: String
    ): Long {
        val digest = MessageDigest
            .getInstance("SHA-256")
            .digest(
                "$mediaId:$chapterNumber"
                    .toByteArray(Charsets.UTF_8)
            )

        val raw = (
            ((digest[0].toInt() and 0xff) shl 24) or
                ((digest[1].toInt() and 0xff) shl 16) or
                ((digest[2].toInt() and 0xff) shl 8) or
                (digest[3].toInt() and 0xff)
            ) and 0x7fffffff

        // Keep IDs inside positive signed Int range because the original
        // RanobeLib data uses integer chapter IDs.
        return (if (raw == 0) 1 else raw).toLong()
    }

    private fun chapterZipName(
        chapterNumber: String,
        chapterId: Long
    ): String = "v1-n$chapterNumber-$chapterId.zip"
}

private fun <T> java.util.Enumeration<T>.toList(): List<T> =
    buildList {
        while (hasMoreElements()) {
            add(nextElement())
        }
    }
