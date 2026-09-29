package com.readerlb.app.storage

import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.readerlb.app.export.LocalBookExportFormat
import com.readerlb.app.export.LocalMangaExportManager
import java.util.zip.ZipInputStream
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import rikka.shizuku.Shizuku

@RunWith(AndroidJUnit4::class)
class MangaFixtureExportAndroidTest {
    @Test fun pdfAndEpubExportKeepAllFixturePagesInNaturalOrder() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("readerlb_manga_fixture") == "true")
        assertTrue(Shizuku.pingBinder())
        assertEquals(PackageManager.PERMISSION_GRANTED, Shizuku.checkSelfPermission())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.runOnMainSync { ShizukuAccess.refresh(context) }
        for (attempt in 0 until 100) {
            if (ShizukuAccess.state == ShizukuAccessState.READY) break
            Thread.sleep(100)
        }
        assertEquals(ShizukuAccessState.READY, ShizukuAccess.state)
        val item = RanobeLibLibraryScanner(context).scan(ShizukuAccess.mangaTreeUri)
            .items.single { it.slugUrl == "990001--readerlb-manga-fixture" }
        val created = mutableListOf<Uri>()
        try {
            val manager = LocalMangaExportManager(context)
            val pdf = manager.export(item, LocalBookExportFormat.PDF).also { created += it.uri }
            assertEquals(1, pdf.chapterCount)
            context.contentResolver.openFileDescriptor(pdf.uri, "r")!!.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    assertEquals(4, renderer.pageCount)
                    renderer.openPage(0).use { page ->
                        assertEquals(800, page.width)
                        assertEquals(1131, page.height)
                    }
                    renderer.openPage(3).use { page ->
                        assertEquals(1003, page.width)
                        assertEquals(800, page.height)
                    }
                }
            }

            val epub = manager.export(item, LocalBookExportFormat.EPUB).also { created += it.uri }
            val names = mutableListOf<String>()
            val pageSizes = mutableListOf<Pair<Int, Int>>()
            ZipInputStream(context.contentResolver.openInputStream(epub.uri)!!).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    names += entry.name
                    if (entry.name.matches(Regex("OEBPS/images/page\\d+\\.jpg"))) {
                        val bytes = zip.readBytes()
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        assertNotNull(bitmap)
                        pageSizes += bitmap.width to bitmap.height
                        bitmap.recycle()
                    }
                    zip.closeEntry()
                }
            }
            assertEquals("mimetype", names.first())
            assertTrue(names.contains("OEBPS/content.opf"))
            assertTrue(names.contains("OEBPS/nav.xhtml"))
            assertEquals(4, names.count { it.matches(Regex("OEBPS/page\\d+\\.xhtml")) })
            assertEquals(listOf(800 to 1131, 800 to 1132, 800 to 1132, 1003 to 800), pageSizes)
        } finally {
            created.forEach { context.contentResolver.delete(it, null, null) }
        }
    }
}
