package com.readerlb.app.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class RanobeLibLibraryScannerTest {

    @Test
    fun parsesRealisticLocalTitleMetadata() {
        val info = """
            {
              "media": {
                "name": "Fallback",
                "rusName": "Тестовая новелла",
                "slugUrl": "900000001--test",
                "imageUrl": "file:///storage/emulated/0/Android/data/ru.libappc/files/book/900000001--test/cover.webp"
              },
              "writeTime": 123456789
            }
        """.trimIndent()

        val chapters = """
            [
              {"number":"50"},
              {"number":"0"},
              {"number":"51"},
              {"number":"50"}
            ]
        """.trimIndent()

        val parsed = parseLocalLibraryMetadata(
            infoText = info,
            chaptersText = chapters,
            folderName = "900000001--test"
        )

        assertEquals("Тестовая новелла", parsed.title)
        assertEquals("900000001--test", parsed.slugUrl)
        assertEquals(3, parsed.chapterCount)
        assertEquals("0", parsed.firstChapter)
        assertEquals("51", parsed.lastChapter)
        assertEquals("cover.webp", parsed.coverName)
        assertEquals(123456789L, parsed.writeTime)
    }
}
