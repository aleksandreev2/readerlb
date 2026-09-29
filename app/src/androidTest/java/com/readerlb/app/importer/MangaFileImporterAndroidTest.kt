package com.readerlb.app.importer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.readerlb.app.storage.LocalMangaReader
import com.readerlb.app.storage.RanobeLibLibraryScanner
import com.readerlb.app.storage.ShizukuAccess
import com.readerlb.app.storage.ShizukuAccessState
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import rikka.shizuku.Shizuku

@RunWith(AndroidJUnit4::class)
class MangaFileImporterAndroidTest {
    @Test fun sharedFilesAccessCanPrepareBookRootForNovels() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("readerlb_manga_import_fixture") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync { ShizukuAccess.refresh(context) }
        for (attempt in 0 until 100) {
            if (ShizukuAccess.state == ShizukuAccessState.READY) break
            Thread.sleep(100)
        }
        assertEquals(ShizukuAccessState.READY, ShizukuAccess.state)
        val files = DocumentFile.fromTreeUri(context, ShizukuAccess.filesTreeUri)!!
        val existed = files.findFile("book") != null
        try {
            val book = DocumentFile.fromTreeUri(context, ShizukuAccess.ensureContentRoot(context, "book"))!!
            assertTrue(book.isDirectory)
        } finally {
            if (!existed) {
                files.findFile("book")?.delete()
                ShizukuAccess.service().probe()
            }
        }
    }

    @Test fun suppliedElicedCbzAndPdfImportAsTwoChapters() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("readerlb_real_manga_files") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = context.cacheDir
        val cbz = File(source, "1_-_3_Контратака.cbz")
        val pdf = File(source, "1_-_4.pdf")
        assertTrue(cbz.isFile && pdf.isFile)
        instrumentation.runOnMainSync { ShizukuAccess.refresh(context) }
        for (attempt in 0 until 100) {
            if (ShizukuAccess.state == ShizukuAccessState.READY) break
            Thread.sleep(100)
        }
        assertEquals(ShizukuAccessState.READY, ShizukuAccess.state)
        val importer = MangaFileImporter(context)
        val title = "Элисед — тест ReaderLB"
        var folder: String? = null
        try {
            val cbzPreview = importer.inspect(Uri.fromFile(cbz), cbz.name)
            assertEquals(18, cbzPreview.pageCount)
            assertEquals("Контратака", cbzPreview.chapterTitle)
            folder = importer.importFile(Uri.fromFile(cbz), cbz.name, title, cbzPreview).folderName
            val pdfPreview = importer.inspect(Uri.fromFile(pdf), pdf.name)
            assertTrue(pdfPreview.pageCount > 0)
            importer.importFile(Uri.fromFile(pdf), pdf.name, title, pdfPreview)
            val item = RanobeLibLibraryScanner(context).scan(ShizukuAccess.mangaTreeUri)
                .items.single { it.folderName == folder }
            assertEquals(2, item.chapterCount)
            val reader = LocalMangaReader(context)
            val chapters = reader.chapters(item)
            assertEquals(listOf("3", "4"), chapters.map { it.number })
            assertEquals(18, reader.pages(item, chapters[0]).size)
            assertEquals(pdfPreview.pageCount, reader.pages(item, chapters[1]).size)
        } finally {
            folder?.let { DocumentFile.fromTreeUri(context, ShizukuAccess.mangaTreeUri)?.findFile(it)?.delete() }
            cbz.delete()
            pdf.delete()
        }
    }

    @Test fun cbzAndPdfBecomeReadableChaptersOfOneLocalTitle() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("readerlb_manga_import_fixture") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assertTrue(Shizuku.pingBinder())
        instrumentation.runOnMainSync { ShizukuAccess.refresh(context) }
        for (attempt in 0 until 100) {
            if (ShizukuAccess.state == ShizukuAccessState.READY) break
            Thread.sleep(100)
        }
        assertEquals(ShizukuAccessState.READY, ShizukuAccess.state)

        val cbz = File(context.cacheDir, "1_-_3_Контратака.cbz")
        val pdf = File(context.cacheDir, "1_-_4.pdf")
        ZipOutputStream(cbz.outputStream()).use { zip ->
            for (name in listOf("p10.png", "p2.png", "p1.png")) {
                zip.putNextEntry(ZipEntry(name))
                val bitmap = Bitmap.createBitmap(8, 12, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.RED)
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)
                } finally { bitmap.recycle() }
                zip.closeEntry()
            }
        }
        val document = PdfDocument()
        try {
            for ((index, size) in listOf(300 to 500, 500 to 300).withIndex()) {
                val page = document.startPage(PdfDocument.PageInfo.Builder(size.first, size.second, index + 1).create())
                page.canvas.drawColor(Color.BLUE)
                document.finishPage(page)
            }
            pdf.outputStream().use(document::writeTo)
        } finally { document.close() }

        val importer = MangaFileImporter(context)
        val title = "ReaderLB import instrumentation"
        var folder: String? = null
        try {
            val cbzPreview = importer.inspect(Uri.fromFile(cbz), cbz.name)
            assertEquals(3, cbzPreview.pageCount)
            assertEquals("3", cbzPreview.number)
            folder = importer.importFile(Uri.fromFile(cbz), cbz.name, title, cbzPreview).folderName
            val pdfPreview = importer.inspect(Uri.fromFile(pdf), pdf.name)
            assertEquals(2, pdfPreview.pageCount)
            assertEquals("4", pdfPreview.number)
            importer.importFile(Uri.fromFile(pdf), pdf.name, title, pdfPreview)

            val item = RanobeLibLibraryScanner(context).scan(ShizukuAccess.mangaTreeUri)
                .items.single { it.folderName == folder }
            assertEquals(2, item.chapterCount)
            val reader = LocalMangaReader(context)
            val chapters = reader.chapters(item)
            assertEquals(listOf("3", "4"), chapters.map { it.number })
            assertEquals(listOf("p1.png", "p2.png", "p10.png"), reader.pages(item, chapters[0]).map { it.name })
            assertEquals(2, reader.pages(item, chapters[1]).size)
        } finally {
            folder?.let { DocumentFile.fromTreeUri(context, ShizukuAccess.mangaTreeUri)?.findFile(it)?.delete() }
            cbz.delete()
            pdf.delete()
        }
    }
}
