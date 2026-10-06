package com.readerlb.app.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.readerlb.app.storage.LocalContentType
import com.readerlb.app.storage.LocalLibraryItem
import com.readerlb.app.storage.LocalMangaReader
import com.readerlb.app.storage.MangaChapter
import com.readerlb.app.storage.MangaPage
import com.readerlb.app.storage.TesterDiagnostics
import com.readerlb.app.storage.decodeMangaBitmap
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Exports downloaded image chapters without going through the text-book parser. */
class LocalMangaExportManager(private val context: Context) {
    private val reader = LocalMangaReader(context)

    fun export(
        item: LocalLibraryItem,
        format: LocalBookExportFormat,
        onProgress: (LocalExportProgress) -> Unit = {},
        destinationUri: Uri? = null
    ): ExportedLocalBookFile {
        require(item.contentType == LocalContentType.MANGA) { "Это не локальная манга" }
        require(format == LocalBookExportFormat.EPUB || format == LocalBookExportFormat.PDF) {
            "Для манги доступны EPUB и PDF"
        }
        val chapters = reader.chapters(item)
        require(chapters.isNotEmpty()) { "Манга не содержит глав" }
        val name = "${safeExportFileName(item.title)}.${format.extension}"
        val writerBlock: (OutputStream) -> ExportedLocalBookFile = { output ->
            when (format) {
                LocalBookExportFormat.EPUB -> writeEpub(item, chapters, output, onProgress)
                LocalBookExportFormat.PDF -> writePdf(item, chapters, output, onProgress)
                else -> error("Unsupported manga export format")
            }
            ExportedLocalBookFile(Uri.EMPTY, name, chapters.size, format)
        }

        return try {
            if (destinationUri == null) {
                writePendingDownload(
                    resolver = context.contentResolver,
                    displayName = name,
                    mimeType = format.mimeType,
                    block = writerBlock
                )
            } else {
                writeDocumentUri(
                    resolver = context.contentResolver,
                    uri = destinationUri,
                    displayName = name,
                    mimeType = format.mimeType,
                    block = writerBlock
                )
            }
        } catch (failure: ExportDestinationUnavailableException) {
            TesterDiagnostics.record(
                context,
                "manga.export.destination." + failure.stage,
                "name=" + failure.displayName + "; mime=" + failure.mimeType,
                failure
            )
            throw failure
        }
    }

    private fun writePdf(
        item: LocalLibraryItem,
        chapters: List<MangaChapter>,
        output: OutputStream,
        onProgress: (LocalExportProgress) -> Unit
    ) {
        val pdf = PdfDocument()
        var pageNumber = 0
        try {
            chapters.forEachIndexed { chapterIndex, chapter ->
                checkInterrupted()
                reader.forEachPage(item, chapter) { image ->
                    checkInterrupted()
                    val bitmap = decode(image)
                    try {
                        val scale = minOf(1.0f, 1440f / maxOf(bitmap.width, bitmap.height))
                        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
                        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
                        pageNumber++
                        val page = pdf.startPage(
                            PdfDocument.PageInfo.Builder(width, height, pageNumber).create()
                        )
                        page.canvas.drawColor(Color.WHITE)
                        page.canvas.drawBitmap(
                            bitmap, null, Rect(0, 0, width, height),
                            Paint(Paint.FILTER_BITMAP_FLAG)
                        )
                        pdf.finishPage(page)
                    } finally {
                        bitmap.recycle()
                    }
                }
                onProgress(LocalExportProgress(chapterIndex + 1, chapters.size))
            }
            require(pageNumber > 0) { "Манга не содержит страниц" }
            pdf.writeTo(output)
        } finally {
            pdf.close()
        }
    }

    private fun writeEpub(
        item: LocalLibraryItem,
        chapters: List<MangaChapter>,
        output: OutputStream,
        onProgress: (LocalExportProgress) -> Unit
    ) {
        val modified = java.time.Instant.now()
            .truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString()
        ZipOutputStream(output).use { zip ->
            val mimetype = "application/epub+zip".toByteArray(StandardCharsets.US_ASCII)
            val crc = CRC32().apply { update(mimetype) }
            zip.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED
                size = mimetype.size.toLong()
                compressedSize = size
                this.crc = crc.value
            })
            zip.write(mimetype)
            zip.closeEntry()
            zip.textEntry(
                "META-INF/container.xml",
                """<?xml version="1.0" encoding="UTF-8"?>
                <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                </container>""".trimIndent()
            )

            val manifest = StringBuilder()
            val spine = StringBuilder()
            val navigation = StringBuilder()
            var pageNumber = 0
            chapters.forEachIndexed { chapterIndex, chapter ->
                checkInterrupted()
                var chapterPageIndex = 0
                reader.forEachPage(item, chapter) { image ->
                    checkInterrupted()
                    val index = chapterPageIndex
                    chapterPageIndex += 1
                    pageNumber++
                    val fileNumber = pageNumber.toString().padStart(5, '0')
                    val imageName = "images/page$fileNumber.jpg"
                    val pageName = "page$fileNumber.xhtml"
                    val bitmap = decode(image)
                    try {
                        zip.putNextEntry(ZipEntry("OEBPS/$imageName"))
                        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, zip)) {
                            "Не удалось закодировать страницу $pageNumber"
                        }
                        zip.closeEntry()
                    } finally {
                        bitmap.recycle()
                    }
                    zip.textEntry(
                        "OEBPS/$pageName",
                        """<?xml version="1.0" encoding="UTF-8"?>
                        <html xmlns="http://www.w3.org/1999/xhtml"><head><title>Страница $pageNumber</title>
                        <style>html,body{margin:0;padding:0;background:#fff}img{display:block;width:100%;height:auto;max-height:100vh;object-fit:contain;margin:auto}</style>
                        </head><body><img src="$imageName" alt="Страница $pageNumber"/></body></html>""".trimIndent()
                    )
                    manifest.append("<item id=\"img$pageNumber\" href=\"$imageName\" media-type=\"image/jpeg\"/>")
                    manifest.append("<item id=\"page$pageNumber\" href=\"$pageName\" media-type=\"application/xhtml+xml\"/>")
                    spine.append("<itemref idref=\"page$pageNumber\"/>")
                    if (index == 0) {
                        val label = xmlEscape("Том ${chapter.volume}, глава ${chapter.number}: ${chapter.name}".trimEnd(':', ' '))
                        navigation.append("<li><a href=\"$pageName\">$label</a></li>")
                    }
                }
                onProgress(LocalExportProgress(chapterIndex + 1, chapters.size))
            }
            require(pageNumber > 0) { "Манга не содержит страниц" }
            zip.textEntry(
                "OEBPS/nav.xhtml",
                """<?xml version="1.0" encoding="UTF-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
                <head><title>Содержание</title></head><body><nav epub:type="toc" id="toc"><h1>Главы</h1><ol>$navigation</ol></nav></body></html>""".trimIndent()
            )
            zip.textEntry(
                "OEBPS/content.opf",
                """<?xml version="1.0" encoding="UTF-8"?>
                <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bookid">
                <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="bookid">readerlb:manga:${xmlEscape(item.slugUrl)}</dc:identifier>
                <dc:title>${xmlEscape(item.title)}</dc:title><dc:language>ru</dc:language><meta property="dcterms:modified">$modified</meta></metadata>
                <manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>$manifest</manifest>
                <spine>$spine</spine></package>""".trimIndent()
            )
        }
    }
}

private fun decode(
    page: MangaPage
): Bitmap =
    decodeMangaBitmap(
        page
    )

private fun ZipOutputStream.textEntry(name: String, value: String) {
    putNextEntry(ZipEntry(name))
    write(value.toByteArray(StandardCharsets.UTF_8))
    closeEntry()
}

private fun xmlEscape(value: String): String = value.replace("&", "&amp;")
    .replace("<", "&lt;").replace(">", "&gt;")
    .replace("\"", "&quot;").replace("'", "&apos;")

private fun checkInterrupted() {
    if (Thread.currentThread().isInterrupted) throw InterruptedException("Экспорт отменён")
}
