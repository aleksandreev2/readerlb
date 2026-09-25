package com.readerlb.app.export

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LocalChapterArchiveTest {

    @Test
    fun readsDataAndImageEntriesFromValidChapterZip() {
        val bytes =
            chapterZip(
                linkedMapOf(
                    "data.txt" to
                        """{"type":"doc","content":[]}"""
                            .toByteArray(
                                Charsets.UTF_8
                            ),
                    "image-id.webp" to
                        byteArrayOf(
                            1,
                            2,
                            3
                        )
                )
            )

        val archive =
            inspectLocalChapterArchive(
                ByteArrayInputStream(
                    bytes
                )
            )

        assertTrue(
            archive.dataText
                .contains(
                    "\"type\":\"doc\""
                )
        )
        assertEquals(
            linkedSetOf(
                "data.txt",
                "image-id.webp"
            ),
            archive.entryNames
        )
    }

    @Test
    fun rejectsUnsafeChapterZipEntry() {
        val bytes =
            chapterZip(
                linkedMapOf(
                    "data.txt" to
                        """{"type":"doc","content":[]}"""
                            .toByteArray(
                                Charsets.UTF_8
                            ),
                    "../evil.txt" to
                        byteArrayOf(1)
                )
            )

        assertFailsWithMessage(
            "Некорректное имя файла"
        ) {
            inspectLocalChapterArchive(
                ByteArrayInputStream(
                    bytes
                )
            )
        }
    }

    @Test
    fun rejectsChapterZipWithoutDataDocument() {
        val bytes =
            chapterZip(
                linkedMapOf(
                    "image.jpg" to
                        byteArrayOf(
                            1,
                            2,
                            3
                        )
                )
            )

        assertFailsWithMessage(
            "не содержит data.txt"
        ) {
            inspectLocalChapterArchive(
                ByteArrayInputStream(
                    bytes
                )
            )
        }
    }

    @Test
    fun rejectsCorruptChapterArchiveWithoutDocument() {
        assertFailsWithMessage(
            "не содержит data.txt"
        ) {
            inspectLocalChapterArchive(
                ByteArrayInputStream(
                    byteArrayOf(
                        1,
                        2,
                        3,
                        4,
                        5
                    )
                )
            )
        }
    }

    @Test
    fun missingImageBecomesVisibleWarningInsteadOfSilentLoss() {
        val chapter =
            parseLocalChapterDocument(
                number = "12",
                title = "",
                dataText = """
                    {
                      "type":"doc",
                      "content":[
                        {
                          "type":"image",
                          "attrs":{
                            "images":[
                              {"image":"missing"}
                            ]
                          }
                        }
                      ]
                    }
                """.trimIndent(),
                archiveEntryNames =
                    setOf("data.txt")
            )

        assertTrue(
            chapter.blocks.isEmpty()
        )
        assertTrue(
            chapter.warnings.any {
                it.contains(
                    "missing"
                )
            }
        )
    }

    @Test
    fun archiveInspectionHonorsThreadCancellation() {
        val bytes =
            chapterZip(
                linkedMapOf(
                    "data.txt" to
                        """{"type":"doc","content":[]}"""
                            .toByteArray(
                                Charsets.UTF_8
                            )
                )
            )

        Thread.currentThread()
            .interrupt()

        try {
            try {
                inspectLocalChapterArchive(
                    ByteArrayInputStream(
                        bytes
                    )
                )
                fail(
                    "Ожидалось прерывание экспорта"
                )
            } catch (
                expected:
                    InterruptedException
            ) {
                assertTrue(true)
            }
        } finally {
            Thread.interrupted()
        }
    }

    private fun chapterZip(
        entries:
            LinkedHashMap<
                String,
                ByteArray
            >
    ): ByteArray {
        val output =
            ByteArrayOutputStream()

        ZipOutputStream(
            output
        ).use { zip ->
            entries.forEach {
                    (name, bytes) ->
                zip.putNextEntry(
                    ZipEntry(name)
                )
                zip.write(bytes)
                zip.closeEntry()
            }
        }

        return output
            .toByteArray()
    }

    private fun assertFailsWithMessage(
        expected: String,
        block: () -> Unit
    ) {
        try {
            block()
            fail(
                "Ожидалась ошибка: $expected"
            )
        } catch (
            throwable: Throwable
        ) {
            assertTrue(
                "Фактическая ошибка: " +
                    throwable.message,
                throwable.message
                    .orEmpty()
                    .contains(
                        expected,
                        ignoreCase = true
                    )
            )
        }
    }
}
