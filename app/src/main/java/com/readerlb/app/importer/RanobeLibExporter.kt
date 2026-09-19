package com.readerlb.app.importer

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.absoluteValue

class RanobeLibExporter(private val context: Context) {

    fun export(
        book: ParsedBook,
        titleOverride: String = "",
        firstChapter: Int? = null,
        lastChapter: Int? = null,
        ranobeLibBookTree: Uri? = null
    ): ExportResult {
        val title = titleOverride.trim().ifBlank { book.title }
        val selected = book.chapters.filter { ch ->
            (firstChapter == null || ch.number >= firstChapter) &&
                (lastChapter == null || ch.number <= lastChapter)
        }
        require(selected.isNotEmpty()) { "В выбранный диапазон не попало ни одной главы" }

        val mediaId = stableMediaId(title)
        val slug = slugify(title).ifBlank { "local-book-$mediaId" }
        val slugUrl = "$mediaId--$slug"
        val tempRoot = File(context.cacheDir, "readerlb_export_${System.nanoTime()}")
        val titleDir = File(tempRoot, "book/$slugUrl").apply { mkdirs() }

        val coverName = book.coverBytes?.let {
            val ext = book.coverExtension.lowercase().let { value ->
                when (value) {
                    "jpeg" -> "jpg"
                    "jpg", "png", "webp" -> value
                    else -> "jpg"
                }
            }
            "cover.$ext".also { name -> File(titleDir, name).writeBytes(it) }
        }
        val coverUri = coverName?.let {
            "file:///storage/emulated/0/Android/data/ru.libappc/files/book/$slugUrl/$it"
        }.orEmpty()

        val chapterJson = JSONArray()
        val now = System.currentTimeMillis()

        selected.forEachIndexed { index, chapter ->
            val displayNumber = chapter.number
            val chapterId = mediaId.toLong() + displayNumber
            val zipName = "v1-n$displayNumber-$chapterId.zip"
            ZipOutputStream(File(titleDir, zipName).outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("data.txt"))
                zip.write(readerDocument(chapter).toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            val branch = JSONObject()
                .put("id", chapterId)
                .put("branchId", -1)
                .put("dateMillis", now + index)
                .put("teams", JSONArray())
                .put("user", JSONObject().put("id", 0).put("username", "ReaderLB"))
                .put("notify", false)

            chapterJson.put(
                JSONObject()
                    .put("id", chapterId)
                    .put("volume", "1")
                    .put("number", displayNumber.toString())
                    .put("name", chapter.title)
                    .put("itemNumber", index + 1)
                    .put("branches", JSONArray().put(branch))
                    .put("withBranches", false)
                    .put("totalBranchesSize", 1)
            )
        }

        File(titleDir, "chapters.json").writeText(chapterJson.toString(), Charsets.UTF_8)
        File(titleDir, "info.json").writeText(
            infoJson(
                mediaId = mediaId,
                slug = slug,
                slugUrl = slugUrl,
                title = title,
                book = book,
                chapterCount = selected.size,
                coverUri = coverUri
            ).toString(),
            Charsets.UTF_8
        )

        var installed = false
        if (ranobeLibBookTree != null) {
            installed = runCatching {
                copyToRanobeLibTree(ranobeLibBookTree, titleDir, slugUrl)
                true
            }.getOrDefault(false)
        }

        val download = if (!installed) saveZipToDownloads(tempRoot, title, slugUrl) else null
        tempRoot.deleteRecursively()

        return ExportResult(
            title = title,
            chapterCount = selected.size,
            slugUrl = slugUrl,
            installedDirectly = installed,
            downloadUri = download?.toString()
        )
    }

    private fun readerDocument(chapter: ParsedChapter): JSONObject {
        val content = JSONArray()
        chapter.blocks.forEach { block ->
            when (block) {
                is ReaderBlock.Paragraph -> {
                    val node = JSONObject().put("type", "paragraph")
                    if (block.centered) {
                        node.put("attrs", JSONObject().put("textAlign", "center"))
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
                ReaderBlock.HorizontalRule -> content.put(JSONObject().put("type", "horizontalRule"))
                is ReaderBlock.Quote -> {
                    val quoteContent = JSONArray()
                    block.lines.forEach { line ->
                        quoteContent.put(
                            JSONObject()
                                .put("type", "paragraph")
                                .put("attrs", JSONObject().put("textAlign", "center"))
                                .put(
                                    "content",
                                    JSONArray().put(
                                        JSONObject().put("type", "text").put("text", line)
                                    )
                                )
                        )
                    }
                    content.put(
                        JSONObject()
                            .put("type", "blockquote")
                            .put("content", quoteContent)
                    )
                }
            }
        }
        if (content.length() == 0) content.put(JSONObject().put("type", "paragraph"))
        return JSONObject().put("type", "doc").put("content", content)
    }

    private fun infoJson(
        mediaId: Int,
        slug: String,
        slugUrl: String,
        title: String,
        book: ParsedBook,
        chapterCount: Int,
        coverUri: String
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
            .ifBlank { "Локальный тайтл, импортированный через ReaderLB." }

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
            .put("rating", JSONObject().put("first", "0.00").put("second", "0").put("third", 0))
            .put("genres", JSONArray())
            .put("tags", JSONArray())
            .put(
                "authors",
                if (book.author.isBlank()) JSONArray()
                else JSONArray().put(JSONObject().put("name", book.author))
            )
            .put("artists", JSONArray())
            .put("uploadedCount", chapterCount)
            .put("status", tag("Онгоинг", "1"))
            .put("scanlateStatus", tag("Онгоинг", "1"))
            .put("format", JSONArray().put(tag("Веб", "6")))

        return JSONObject()
            .put("media", media)
            .put("writeTime", System.currentTimeMillis())
            .put("version", 1)
    }

    private fun tag(title: String, id: String) =
        JSONObject().put("title", title).put("id", id).put("tag", JSONObject.NULL)

    private fun copyToRanobeLibTree(treeUri: Uri, source: File, slugUrl: String) {
        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("Нет доступа к папке RanobeLib")
        val titleDir = root.findFile(slugUrl) ?: root.createDirectory(slugUrl)
            ?: error("Не удалось создать папку тайтла")

        source.listFiles().orEmpty().forEach { file ->
            titleDir.findFile(file.name)?.delete()
            val mime = when (file.extension.lowercase()) {
                "json", "txt" -> "application/json"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "zip" -> "application/zip"
                else -> "application/octet-stream"
            }
            val target = titleDir.createFile(mime, file.name)
                ?: error("Не удалось создать ${file.name}")
            context.contentResolver.openOutputStream(target.uri, "w").use { output ->
                requireNotNull(output)
                file.inputStream().use { input -> input.copyTo(output) }
            }
        }
    }

    private fun saveZipToDownloads(tempRoot: File, title: String, slugUrl: String): Uri {
        val safeTitle = slugify(title).ifBlank { slugUrl }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "${safeTitle}_for_RanobeLib.zip")
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ReaderLB")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Не удалось создать файл в Downloads")

        try {
            context.contentResolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output)
                ZipOutputStream(output.buffered()).use { zip ->
                    File(tempRoot, "book").walkTopDown()
                        .filter(File::isFile)
                        .forEach { file ->
                            val rel = file.relativeTo(tempRoot).invariantSeparatorsPath
                            zip.putNextEntry(ZipEntry(rel))
                            file.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                }
            }
            val done = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            context.contentResolver.update(uri, done, null, null)
            return uri
        } catch (t: Throwable) {
            context.contentResolver.delete(uri, null, null)
            throw t
        }
    }

    private fun stableMediaId(title: String): Int {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(title.lowercase().trim().toByteArray(Charsets.UTF_8))
        val raw = ((digest[0].toInt() shl 24) or
            ((digest[1].toInt() and 0xff) shl 16) or
            ((digest[2].toInt() and 0xff) shl 8) or
            (digest[3].toInt() and 0xff)).absoluteValue
        return 900_000_000 + (raw % 90_000_000)
    }

    private fun slugify(value: String): String {
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
            value.lowercase().forEach { ch ->
                when {
                    ch in 'a'..'z' || ch in '0'..'9' -> append(ch)
                    table.containsKey(ch) -> append(table.getValue(ch))
                    else -> append('-')
                }
            }
        }
        return ascii.replace(Regex("-+"), "-").trim('-').take(90)
    }
}
