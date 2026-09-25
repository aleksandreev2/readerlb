package com.readerlb.app.export

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBookEpubWriterTest {

    @Test
    fun writesValidEpubSkeletonWithCoverChapterAndImage() {
        val book =
            LocalExportBook(
                title = "Книга & тест",
                author = "Автор",
                description =
                    "Описание <книги>",
                languageLabel = "Корея",
                slugUrl = "42--book",
                coverName = "cover.jpg",
                chapters = listOf(
                    LocalExportChapterRef(
                        number = "0",
                        title = "Пролог",
                        volume = "1",
                        chapterId = 10L,
                        archiveName =
                            "v1-n0-10.zip"
                    )
                )
            )
        val output =
            ByteArrayOutputStream()
        val progress =
            mutableListOf<
                Pair<Int, Int>
            >()

        LocalBookEpubWriter().write(
            book = book,
            output = output,
            readChapter = {
                LocalExportChapter(
                    number = "0",
                    title = "Пролог",
                    blocks = listOf(
                        LocalExportBlock
                            .Paragraph(
                                "Первая строка"
                            ),
                        LocalExportBlock
                            .Image(
                                entryName =
                                    "image-id.png",
                                extension =
                                    "png",
                                description =
                                    "Арт"
                            )
                    )
                )
            },
            copyCover = {
                it.write(
                    byteArrayOf(
                        1,
                        2,
                        3
                    )
                )
            },
            copyChapterImage = {
                    _,
                    _,
                    imageOutput ->
                imageOutput.write(
                    byteArrayOf(
                        4,
                        5,
                        6
                    )
                )
            },
            onProgress = {
                progress +=
                    it.completedChapters to
                        it.totalChapters
            }
        )

        val entries =
            linkedMapOf<
                String,
                ByteArray
            >()
        var firstName: String? =
            null
        var firstMethod: Int? =
            null

        ZipInputStream(
            ByteArrayInputStream(
                output.toByteArray()
            )
        ).use { zip ->
            while (true) {
                val entry =
                    zip.nextEntry
                        ?: break
                if (
                    firstName == null
                ) {
                    firstName =
                        entry.name
                    firstMethod =
                        entry.method
                }

                entries[
                    entry.name
                ] =
                    zip.readBytes()
                zip.closeEntry()
            }
        }

        assertEquals(
            "mimetype",
            firstName
        )
        assertEquals(
            ZipEntry.STORED,
            firstMethod
        )
        assertEquals(
            "application/epub+zip",
            entries
                .getValue(
                    "mimetype"
                )
                .toString(
                    Charsets.UTF_8
                )
        )

        assertTrue(
            entries.containsKey(
                "META-INF/container.xml"
            )
        )
        assertTrue(
            entries.containsKey(
                "OEBPS/content.opf"
            )
        )
        assertTrue(
            entries.containsKey(
                "OEBPS/nav.xhtml"
            )
        )
        assertTrue(
            entries.containsKey(
                "OEBPS/cover.xhtml"
            )
        )
        assertTrue(
            entries.containsKey(
                "OEBPS/images/cover.jpg"
            )
        )
        assertTrue(
            entries.containsKey(
                "OEBPS/images/chapter-1-image-1.png"
            )
        )

        val opf =
            entries
                .getValue(
                    "OEBPS/content.opf"
                )
                .toString(
                    Charsets.UTF_8
                )
        assertTrue(
            opf.contains(
                "cover-image"
            )
        )
        assertTrue(
            opf.contains(
                "chapter-1"
            )
        )
        assertTrue(
            opf.contains(
                "Книга &amp; тест"
            )
        )

        val chapter =
            entries
                .getValue(
                    "OEBPS/chapters/chapter-1.xhtml"
                )
                .toString(
                    Charsets.UTF_8
                )
        assertTrue(
            chapter.contains(
                "Глава 0 — Пролог"
            )
        )
        assertTrue(
            chapter.contains(
                "../images/chapter-1-image-1.png"
            )
        )

        assertEquals(
            listOf(
                0 to 1,
                1 to 1
            ),
            progress
        )
    }

    @Test
    fun omitsCoverWhenNoCopySourceExists() {
        val output =
            ByteArrayOutputStream()
        val book =
            LocalExportBook(
                title = "Без обложки",
                author = "",
                description = "",
                languageLabel = "",
                slugUrl = "no-cover",
                coverName = "cover.jpg",
                chapters = listOf(
                    LocalExportChapterRef(
                        number = "1",
                        title = "",
                        volume = "1",
                        chapterId = 1L,
                        archiveName =
                            "v1-n1-1.zip"
                    )
                )
            )

        LocalBookEpubWriter().write(
            book = book,
            output = output,
            readChapter = {
                LocalExportChapter(
                    number = "1",
                    title = "",
                    blocks =
                        emptyList()
                )
            },
            copyCover = null,
            copyChapterImage = {
                    _,
                    _,
                    _ ->
            }
        )

        val names =
            mutableSetOf<String>()
        ZipInputStream(
            ByteArrayInputStream(
                output.toByteArray()
            )
        ).use { zip ->
            while (true) {
                val entry =
                    zip.nextEntry
                        ?: break
                names += entry.name
                zip.closeEntry()
            }
        }

        assertTrue(
            "OEBPS/cover.xhtml" !in
                names
        )
        assertTrue(
            "OEBPS/images/cover.jpg" !in
                names
        )
    }
}
