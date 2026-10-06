package com.readerlb.app.storage

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.zip.ZipInputStream
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MangaFixtureTest {
    private fun fixture(name: String): ByteArray {
        val stream = checkNotNull(javaClass.getResourceAsStream("/mangalib/ReaderLB_MangaLib_Exact_Fixture.zip"))
        return ZipInputStream(stream).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: error("Missing $name")
                if (entry.name.endsWith(name)) return@use zip.readBytes()
            }
            error("Missing $name")
        }
    }

    @Test fun metadataAndChapterDetection() {
        val info = JSONObject(fixture("/info.json").toString(Charsets.UTF_8))
        assertEquals("manga", info.getJSONObject("media").getString("model"))
        assertEquals("990001--readerlb-manga-fixture", info.getJSONObject("media").getString("slugUrl"))
        val chapters = parseMangaChapters(fixture("/chapters.json").toString(Charsets.UTF_8))
        assertEquals(1, chapters.size)
        assertEquals("v1-n1-990101.zip", chapters.single().archiveName)
    }

    @Test fun lightweightPageIndexAndDiskStagingKeepNaturalOrder() {
        val archive =
            fixture(
                "/v1-n1-990101.zip"
            )
        val infos =
            inspectMangaChapterPageInfos(
                ByteArrayInputStream(
                    archive
                )
            )
        assertEquals(
            listOf(
                "p1",
                "p2",
                "p10",
                "p18"
            ),
            infos.map {
                it.name
                    .substringBefore(
                        '-'
                    )
            }
        )
        assertTrue(
            infos.all {
                it.format ==
                    "avif"
            }
        )

        val directory =
            Files.createTempDirectory(
                "readerlb-manga-stage"
            ).toFile()
        try {
            val staged =
                stageMangaChapterArchive(
                    ByteArrayInputStream(
                        archive
                    ),
                    directory
                )
            assertEquals(
                infos.map {
                    it.name
                },
                staged.map {
                    it.name
                }
            )
            assertTrue(
                staged.all {
                    it.file.isFile &&
                        it.file.length() > 0L
                }
            )
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun extensionlessAvifNaturalSortAndOrientationWithoutDataTxt() {
        val archive = fixture("/v1-n1-990101.zip")
        val pages = inspectMangaChapterArchive(ByteArrayInputStream(archive))
        assertEquals(4, pages.size)
        assertEquals(listOf("p1", "p2", "p10", "p18"), pages.map { it.name.substringBefore('-') })
        assertTrue(pages.all { !it.name.contains('.') && it.format == "avif" })
        assertTrue(pages.take(3).all { !it.isLandscape && it.width < it.height })
        assertTrue(pages.last().isLandscape)
        assertEquals(1003, pages.last().width)
        assertEquals(800, pages.last().height)
    }
}
