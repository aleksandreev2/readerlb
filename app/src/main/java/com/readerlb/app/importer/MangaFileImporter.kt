package com.readerlb.app.importer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import com.readerlb.app.storage.ShizukuAccess
import com.readerlb.app.storage.TesterDiagnostics
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject

internal data class MangaSourcePreview(
    val format: String,
    val pageCount: Int,
    val volume: String,
    val number: String,
    val chapterTitle: String
)

internal data class MangaImportResult(val folderName: String, val pageCount: Int, val chapterCount: Int)

/** Converts one CBZ or PDF into one chapter of a ReaderLB-owned local MangaLib title. */
internal class MangaFileImporter(private val context: Context) {
    fun inspect(uri: Uri, name: String): MangaSourcePreview {
        val hint = parseComicFileHint(name)
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val count = when (ext) {
            "cbz" -> context.contentResolver.openInputStream(uri)?.use(::inspectComicZip)?.pageCount
                ?: error("Не удалось открыть CBZ")
            "pdf" -> withTemporarySource(uri, ".pdf") { file ->
                val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(descriptor)
                try {
                    require(renderer.pageCount in 1..2_000) { "PDF должен содержать от 1 до 2000 страниц" }
                    renderer.pageCount
                } finally { renderer.close(); descriptor.close() }
            }
            else -> error("Для манги выберите CBZ или PDF")
        }
        return MangaSourcePreview(ext, count, hint.volume, hint.number, hint.chapterTitle)
    }

    fun importFile(uri: Uri, name: String, title: String, preview: MangaSourcePreview): MangaImportResult {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty() && cleanTitle.length <= 150) { "Укажите название манги (до 150 символов)" }
        require(Regex("\\d+(?:\\.\\d+)?").matches(preview.volume) &&
            Regex("\\d+(?:\\.\\d+)?").matches(preview.number)) { "Том и глава должны быть числами" }
        require(preview.chapterTitle.length <= 150) { "Название главы слишком длинное" }
        require(preview.format == name.substringAfterLast('.', "").lowercase(Locale.ROOT)) { "Формат файла изменился" }

        val chapterArchive = File.createTempFile("readerlb-manga-", ".zip", context.cacheDir)
        try {
            val pageCount = when (preview.format) {
                "cbz" -> {
                    copySource(uri, chapterArchive)
                    chapterArchive.inputStream().use(::inspectComicZip).pageCount
                }
                "pdf" -> withTemporarySource(uri, ".pdf") { renderPdfToZip(it, chapterArchive) }
                else -> error("Формат манги не поддерживается")
            }
            require(pageCount == preview.pageCount) { "Файл изменился после анализа. Выберите его снова." }
            val result = installChapter(cleanTitle, preview, chapterArchive, pageCount)
            TesterDiagnostics.record(context, "manga.import", "format=${preview.format} titleFolder=${result.folderName} chapter=${preview.volume}/${preview.number} pages=$pageCount")
            return result
        } catch (failure: Exception) {
            TesterDiagnostics.record(context, "manga.import.failed", "source=$name format=${preview.format}", failure)
            throw failure
        } finally { chapterArchive.delete() }
    }

    private fun installChapter(title: String, preview: MangaSourcePreview, archive: File, pageCount: Int): MangaImportResult {
        val mangaFolder = DocumentFile.fromTreeUri(context, ShizukuAccess.ensureContentRoot(context, "manga"))
            ?: error("Не удалось открыть files/manga")

        val normalized = title.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        val titleHash = sha256(normalized).take(12)
        val folderName = "readerlb-import-$titleHash"
        val existing = mangaFolder.findFile(folderName)
        val titleFolder = existing ?: mangaFolder.createDirectory(folderName)
            ?: error("Не удалось создать папку манги")
        val newTitle = existing == null
        var archiveDoc: DocumentFile? = null
        try {
            val chaptersDoc = titleFolder.findFile("chapters.json")
            val chapters = if (chaptersDoc == null) JSONArray() else JSONArray(readText(chaptersDoc))
            if (!newTitle) {
                val info = JSONObject(readText(requireNotNull(titleFolder.findFile("info.json")) { "Нет info.json у локальной манги" }))
                require(info.getJSONObject("media").optString("rusName") == title) { "Папка манги принадлежит другому тайтлу" }
            }
            for (i in 0 until chapters.length()) {
                val chapter = chapters.getJSONObject(i)
                require(chapter.optString("volume") != preview.volume || chapter.optString("number") != preview.number) {
                    "Том ${preview.volume}, глава ${preview.number} уже добавлены"
                }
            }
            val id = 1_000_000_000_000L + sha256("$normalized/${preview.volume}/${preview.number}").take(12).toLong(16)
            val archiveName = "v${preview.volume}-n${preview.number}-$id.zip"
            require(titleFolder.findFile(archiveName) == null) { "Архив главы уже существует" }
            archiveDoc = titleFolder.createFile("application/zip", archiveName)
                ?: error("Не удалось создать архив главы")
            context.contentResolver.openOutputStream(archiveDoc.uri, "w")!!.use { out -> archive.inputStream().use { it.copyTo(out) } }
            if (newTitle) {
                writeText(requireNotNull(titleFolder.createFile("application/json", "info.json")), buildInfo(title, folderName, titleHash))
            }
            chapters.put(buildChapter(id, preview))
            if (newTitle) {
                writeText(requireNotNull(titleFolder.createFile("application/json", "chapters.json")), chapters.toString())
            } else {
                replaceChapters(titleFolder, requireNotNull(chaptersDoc), chapters.toString())
            }
            return MangaImportResult(folderName, pageCount, chapters.length())
        } catch (failure: Exception) {
            if (newTitle) titleFolder.delete() else archiveDoc?.delete()
            throw failure
        }
    }

    private fun replaceChapters(folder: DocumentFile, old: DocumentFile, text: String) {
        val staged = folder.createFile("application/json", "chapters.json.readerlb-new")
            ?: error("Не удалось подготовить обновление chapters.json")
        try {
            writeText(staged, text)
            check(old.renameTo("chapters.json.readerlb-backup")) { "Не удалось сохранить резервную копию chapters.json" }
            if (!staged.renameTo("chapters.json")) {
                folder.findFile("chapters.json.readerlb-backup")?.renameTo("chapters.json")
                error("Не удалось обновить chapters.json")
            }
            folder.findFile("chapters.json.readerlb-backup")?.delete()
        } finally { folder.findFile("chapters.json.readerlb-new")?.delete() }
    }

    private fun buildInfo(title: String, folder: String, hash: String): String {
        val media = JSONObject()
            .put("id", 1_000_000_000_000L + hash.toLong(16))
            .put("name", title).put("rusName", title).put("engName", title)
            .put("otherNames", JSONArray()).put("slug", folder).put("slugUrl", folder)
            .put("imageUrl", "").put("model", "manga").put("sourceId", "readerlb")
            .put("backgroundUrl", "").put("summary", "Локальная манга, импортированная ReaderLB")
            .put("authors", JSONArray()).put("artists", JSONArray())
            .put("genres", JSONArray()).put("tags", JSONArray())
        return JSONObject().put("media", media).put("writeTime", System.currentTimeMillis()).put("version", 1).toString()
    }

    private fun buildChapter(id: Long, preview: MangaSourcePreview): JSONObject {
        val branch = JSONObject().put("id", id).put("branchId", -1)
            .put("dateMillis", System.currentTimeMillis()).put("teams", JSONArray())
            .put("user", JSONObject().put("id", 0).put("username", "ReaderLB"))
            .put("notify", false)
        return JSONObject().put("id", id).put("volume", preview.volume).put("number", preview.number)
            .put("name", preview.chapterTitle).put("itemNumber", 2)
            .put("branches", JSONArray().put(branch)).put("withBranches", false).put("totalBranchesSize", 1)
    }

    private fun renderPdfToZip(source: File, archive: File): Int {
        val descriptor = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(descriptor)
        try {
            require(renderer.pageCount in 1..2_000) { "PDF должен содержать от 1 до 2000 страниц" }
            ZipOutputStream(archive.outputStream().buffered()).use { zip ->
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    try {
                        val scale = minOf(2.5f, 5400f / maxOf(page.width, page.height))
                        val width = maxOf(1, (page.width * scale).toInt())
                        val height = maxOf(1, (page.height * scale).toInt())
                        require(width.toLong() * height <= 12_000_000L) { "Страница PDF слишком большая" }
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        try {
                            bitmap.eraseColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            zip.putNextEntry(ZipEntry("p${(i + 1).toString().padStart(4, '0')}.jpg"))
                            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, zip)) { "Не удалось сохранить страницу PDF" }
                            zip.closeEntry()
                        } finally { bitmap.recycle() }
                    } finally { page.close() }
                    require(archive.length() <= 400L * 1024 * 1024) { "Глава PDF слишком большая" }
                }
            }
            return renderer.pageCount
        } finally { renderer.close(); descriptor.close() }
    }

    private fun readText(file: DocumentFile): String = context.contentResolver.openInputStream(file.uri)!!
        .bufferedReader().use { it.readText() }

    private fun writeText(file: DocumentFile, text: String) {
        context.contentResolver.openOutputStream(file.uri, "w")!!.bufferedWriter().use { it.write(text) }
    }

    private fun <T> withTemporarySource(uri: Uri, suffix: String, block: (File) -> T): T {
        val source = File.createTempFile("readerlb-source-", suffix, context.cacheDir)
        try { copySource(uri, source); return block(source) } finally { source.delete() }
    }

    private fun copySource(uri: Uri, target: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    require(total <= 500L * 1024 * 1024) { "Файл манги больше 500 МБ" }
                    output.write(buffer, 0, n)
                }
            }
        } ?: error("Не удалось открыть файл манги")
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
