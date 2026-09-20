package com.readerlb.app.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ReaderLbTransferManagerTest {

    @Test
    fun detectsPortableReaderLbPackage() {
        val bytes = packageBytes()

        val info =
            inspectReaderLbTransfer(
                ByteArrayInputStream(
                    bytes
                )
            )

        assertNotNull(info)
        assertEquals(
            "Тестовый тайтл",
            info?.title
        )
        assertEquals(
            41,
            info?.chapterCount
        )
        assertEquals(
            "0",
            info?.firstChapter
        )
        assertEquals(
            "89",
            info?.lastChapter
        )
    }

    @Test
    fun ordinaryZipIsNotDetectedAsTransfer() {
        val bytes =
            ByteArrayOutputStream()
                .also { output ->
                    ZipOutputStream(
                        output
                    ).use { zip ->
                        zip.putNextEntry(
                            ZipEntry(
                                "random.txt"
                            )
                        )
                        zip.write(
                            "hello"
                                .toByteArray()
                        )
                        zip.closeEntry()
                    }
                }
                .toByteArray()

        assertNull(
            inspectReaderLbTransfer(
                ByteArrayInputStream(
                    bytes
                )
            )
        )
    }

    @Test
    fun extractionKeepsTitleFilesInsideExpectedDirectory() {
        val bytes = packageBytes()
        val info = requireNotNull(
            inspectReaderLbTransfer(
                ByteArrayInputStream(
                    bytes
                )
            )
        )
        val root =
            Files.createTempDirectory(
                "readerlb_transfer_"
            ).toFile()

        try {
            extractReaderLbTransfer(
                input =
                    ByteArrayInputStream(
                        bytes
                    ),
                info = info,
                rootDir = root
            )

            val titleDir = root.resolve(
                "book/" +
                    info.slugUrl
            )

            assertEquals(
                true,
                titleDir.resolve(
                    "info.json"
                ).isFile
            )
            assertEquals(
                true,
                titleDir.resolve(
                    "chapters.json"
                ).isFile
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun packageBytes():
        ByteArray =
        ByteArrayOutputStream()
            .also { output ->
                ZipOutputStream(
                    output
                ).use { zip ->
                    fun entry(
                        name: String,
                        text: String
                    ) {
                        zip.putNextEntry(
                            ZipEntry(name)
                        )
                        zip.write(
                            text.toByteArray()
                        )
                        zip.closeEntry()
                    }

                    entry(
                        "readerlb-transfer.json",
                        """
                        {
                          "format":"readerlb-local-title",
                          "version":1,
                          "title":"Тестовый тайтл",
                          "slugUrl":"900000001--test",
                          "chapterCount":41,
                          "firstChapter":"0",
                          "lastChapter":"89"
                        }
                        """.trimIndent()
                    )
                    entry(
                        "book/900000001--test/info.json",
                        "{}"
                    )
                    entry(
                        "book/900000001--test/chapters.json",
                        "[]"
                    )
                }
            }
            .toByteArray()
