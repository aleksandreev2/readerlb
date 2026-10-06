package com.readerlb.app.storage

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import rikka.shizuku.Shizuku

@RunWith(AndroidJUnit4::class)
class MangaFixtureAndroidTest {
    @Test fun seededMangaRootScansAndReadsAvifPages() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("readerlb_manga_fixture") == "true")
        assertTrue("Shizuku server is not running", Shizuku.pingBinder())
        assertEquals(PackageManager.PERMISSION_GRANTED, Shizuku.checkSelfPermission())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { ShizukuAccess.refresh(instrumentation.targetContext) }
        for (attempt in 0 until 100) {
            if (ShizukuAccess.state == ShizukuAccessState.READY) break
            Thread.sleep(100)
        }
        assertEquals(ShizukuAccessState.READY, ShizukuAccess.state)
        val item = RanobeLibLibraryScanner(instrumentation.targetContext)
            .scan(ShizukuAccess.mangaTreeUri).items.single { it.slugUrl == "990001--readerlb-manga-fixture" }
        assertEquals(LocalContentType.MANGA, item.contentType)
        assertEquals(1, item.chapterCount)
        val reader = LocalMangaReader(instrumentation.targetContext)
        val chapter = reader.chapters(item).single()
        val pages = reader.pageInfos(item, chapter)
        assertEquals(listOf("p1", "p2", "p10", "p18"), pages.map { it.name.substringBefore('-') })
        assertTrue(pages.all { it.format == "avif" })

        val first = reader.readPage(item, chapter, pages.first())
        val last = reader.readPage(item, chapter, pages.last())
        assertFalse(first.isLandscape)
        assertTrue(last.isLandscape)

        val bitmap = com.radzivon.bartoshyk.avif.coder.HeifCoder().decode(first.bytes)
        try {
            assertEquals(800, bitmap.width)
            assertEquals(1131, bitmap.height)
        } finally {
            bitmap.recycle()
        }

        val landscape = com.radzivon.bartoshyk.avif.coder.HeifCoder().decode(last.bytes)
        try {
            assertEquals(1003, landscape.width)
            assertEquals(800, landscape.height)
        } finally {
            landscape.recycle()
        }
    }
}
