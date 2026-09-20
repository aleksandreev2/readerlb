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
              {
                "number":"50",
                "branches":[
                  {
                    "user":{
                      "username":"ReaderLB"
                    }
                  }
                ]
              },
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
        assertEquals(
            true,
            parsed.createdByReaderLB
        )
    }



    @Test
    fun distinguishesForeignLocalTitle() {
        val parsed = parseLocalLibraryMetadata(
            infoText = """
                {
                  "media": {
                    "name": "Обычный тайтл",
                    "slugUrl": "123--ordinary",
                    "imageUrl": ""
                  },
                  "writeTime": 7
                }
            """.trimIndent(),
            chaptersText = """
                [
                  {
                    "number":"1",
                    "branches":[
                      {
                        "user":{
                          "username":"translator"
                        }
                      }
                    ]
                  }
                ]
            """.trimIndent(),
            folderName = "123--ordinary"
        )

        assertEquals(
            false,
            parsed.createdByReaderLB
        )
    }

    @Test
    fun localLibraryCacheRoundTripsWithoutLosingCounts() {
        val original = listOf(
            LocalLibraryItem(
                title = "Тест",
                slugUrl = "123--test",
                chapterCount = 41,
                firstChapter = "0",
                lastChapter = "89",
                coverUri = android.net.Uri.parse(
                    "content://example/cover"
                ),
                writeTime = 42L,
                createdByReaderLB = true
            )
        )

        val restored =
            decodeLocalLibraryCache(
                encodeLocalLibraryCache(
                    original
                )
            )

        assertEquals(
            original,
            restored
        )
    }

}
