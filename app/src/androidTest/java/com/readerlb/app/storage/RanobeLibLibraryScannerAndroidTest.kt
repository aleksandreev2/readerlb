package com.readerlb.app.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RanobeLibLibraryScannerAndroidTest {

    @Test
    fun streamsLargeChapterListWithoutChangingRange() {
        val chapters = buildString {
            append('[')
            for (number in 1..2600) {
                if (number > 1) {
                    append(',')
                }
                append(
                    "{\"number\":\"" +
                        number +
                        "\",\"branches\":[]}"
                )
            }
            append(']')
        }

        val parsed =
            parseLocalChapterSummary(
                chapters
            )

        assertEquals(
            2600,
            parsed.chapterCount
        )
        assertEquals(
            "1",
            parsed.firstChapter
        )
        assertEquals(
            "2600",
            parsed.lastChapter
        )
        assertEquals(
            false,
            parsed.createdByReaderLB
        )
    }

    @Test
    fun streamedSummaryDetectsReaderLbOriginAndDistinctNumbers() {
        val parsed =
            parseLocalChapterSummary(
                """
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
                  {"number":"0","branches":[]},
                  {"number":"50","branches":[]},
                  {"number":"51","branches":[]}
                ]
                """.trimIndent()
            )

        assertEquals(
            3,
            parsed.chapterCount
        )
        assertEquals(
            "0",
            parsed.firstChapter
        )
        assertEquals(
            "51",
            parsed.lastChapter
        )
        assertEquals(
            true,
            parsed.createdByReaderLB
        )
    }
}
