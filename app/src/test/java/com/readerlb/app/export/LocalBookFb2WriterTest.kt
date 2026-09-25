package com.readerlb.app.export

import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBookFb2WriterTest {

    @Test
    fun writesFb2WithCoverChapterAndEmbeddedImage() {
        val output =
            ByteArrayOutputStream()
        val book =
            LocalExportBook(
                title = "Тестовая книга",
                author = "Автор",
                description = "Описание",
                languageLabel = "",
                slugUrl = "book-id",
                coverName = "cover.jpg",
                chapters = listOf(
                    LocalExportChapterRef(
                        number = "1",
                        title = "Начало",
                        volume = "1",
                        chapterId = 1L,
                        archiveName =
                            "v1-n1-1.zip"
                    )
                )
            )

        LocalBookFb2Writer().write(
            book = book,
            output = output,
            readChapter = {
                LocalExportChapter(
                    number = "1",
                    title = "Начало",
                    blocks = listOf(
                        LocalExportBlock
                            .Paragraph(
                                "Абзац"
                            ),
                        LocalExportBlock
                            .Quote(
                                listOf(
                                    "Цитата"
                                )
                            ),
                        LocalExportBlock
                            .Image(
                                entryName =
                                    "art.png",
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
            }
        )

        val text =
            output.toString(
                Charsets.UTF_8.name()
            )

        assertTrue(
            text.contains(
                "<book-title>Тестовая книга</book-title>"
            )
        )
        assertTrue(
            text.contains(
                "Глава 1 — Начало"
            )
        )
        assertTrue(
            text.contains(
                "<cite>"
            )
        )
        assertTrue(
            text.contains(
                "l:href=\"#cover-image\""
            )
        )
        assertTrue(
            text.contains(
                "content-type=\"image/png\""
            )
        )
        assertTrue(
            text.contains(
                Base64.getEncoder()
                    .encodeToString(
                        byteArrayOf(
                            4,
                            5,
                            6
                        )
                    )
            )
        )
        val parsed =
            DocumentBuilderFactory
                .newInstance()
                .apply {
                    isNamespaceAware =
                        true
                }
                .newDocumentBuilder()
                .parse(
                    output
                        .toByteArray()
                        .inputStream()
                )
        assertEquals(
            "FictionBook",
            parsed.documentElement
                .localName
        )
    }
}
