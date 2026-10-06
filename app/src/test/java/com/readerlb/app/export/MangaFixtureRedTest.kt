package com.readerlb.app.export

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Test

class MangaFixtureRedTest {
    @Test fun mangaChapterDoesNotNeedDataTxt() {
        val outer = checkNotNull(javaClass.getResourceAsStream(
            "/mangalib/ReaderLB_MangaLib_Exact_Fixture.zip"))
        val chapter = ZipInputStream(outer).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: error("Chapter fixture missing")
                if (entry.name.endsWith("/v1-n1-990101.zip")) return@use zip.readBytes()
            }
            error("Chapter fixture missing")
        }
        val archive = com.readerlb.app.storage.inspectMangaChapterArchive(ByteArrayInputStream(chapter))
        assertEquals(4, archive.size)
    }
}
