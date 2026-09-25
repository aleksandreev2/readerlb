package com.readerlb.app.export

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalExportParserTest {

    @Test
    fun parsesLocalBookMetadataAndResolvesChapterArchives() {
        val book = parseLocalExportBook(
            infoText = """
                {
                  "media": {
                    "name": "Fallback",
                    "rusName": "Локальная книга",
                    "slugUrl": "42--local-book",
                    "imageUrl": "file:///book/42--local-book/cover.jpg",
                    "summary": "<p>Описание книги</p>",
                    "authors": [
                      {"name":"Автор Один"},
                      {"name":"Автор Два"}
                    ],
                    "type": {"title":"Корея"}
                  }
                }
            """.trimIndent(),
            chaptersText = """
                [
                  {
                    "id":100,
                    "volume":"1",
                    "number":"0",
                    "name":"Пролог",
                    "branches":[]
                  },
                  {
                    "id":999,
                    "volume":"2",
                    "number":"1.5",
                    "name":"Продолжение",
                    "branches":[{"id":200}]
                  }
                ]
            """.trimIndent(),
            folderName = "42--local-book",
            availableFileNames = setOf(
                "info.json",
                "chapters.json",
                "cover.jpg",
                "v1-n0-100.zip",
                "v2-n1.5-200.zip"
            )
        )

        assertEquals(
            "Локальная книга",
            book.title
        )
        assertEquals(
            "Автор Один, Автор Два",
            book.author
        )
        assertEquals(
            "Описание книги",
            book.description
        )
        assertEquals(
            "Корея",
            book.languageLabel
        )
        assertEquals(
            "cover.jpg",
            book.coverName
        )
        assertEquals(
            listOf(
                "0",
                "1.5"
            ),
            book.chapters.map {
                it.number
            }
        )
        assertEquals(
            "v1-n0-100.zip",
            book.chapters[0]
                .archiveName
        )
        assertEquals(
            "v2-n1.5-200.zip",
            book.chapters[1]
                .archiveName
        )
    }

    @Test
    fun chapterParserPreservesTextQuotesRulesImagesAndUnknownFallback() {
        val chapter =
            parseLocalChapterDocument(
                number = "12",
                title = "Проверка",
                dataText = """
                    {
                      "type":"doc",
                      "content":[
                        {
                          "type":"paragraph",
                          "attrs":{"textAlign":"center"},
                          "content":[
                            {"type":"text","text":"Строка"},
                            {"type":"hardBreak"},
                            {"type":"text","text":"вторая"}
                          ]
                        },
                        {"type":"horizontalRule"},
                        {
                          "type":"blockquote",
                          "content":[
                            {
                              "type":"paragraph",
                              "content":[
                                {"type":"text","text":"Цитата"}
                              ]
                            }
                          ]
                        },
                        {
                          "type":"image",
                          "attrs":{
                            "description":"Арт",
                            "images":[
                              {"image":"image-id"}
                            ]
                          }
                        },
                        {
                          "type":"mysteryBlock",
                          "content":[
                            {"type":"text","text":"Не потерять"}
                          ]
                        }
                      ]
                    }
                """.trimIndent(),
                archiveEntryNames =
                    setOf(
                        "data.txt",
                        "image-id.png"
                    )
            )

        assertEquals(
            5,
            chapter.blocks.size
        )

        val paragraph =
            chapter.blocks[0]
                as LocalExportBlock
                    .Paragraph
        assertEquals(
            "Строка\nвторая",
            paragraph.text
        )
        assertTrue(
            paragraph.centered
        )

        assertTrue(
            chapter.blocks[1] ===
                LocalExportBlock
                    .HorizontalRule
        )

        assertEquals(
            listOf("Цитата"),
            (
                chapter.blocks[2]
                    as LocalExportBlock
                        .Quote
                ).lines
        )

        val image =
            chapter.blocks[3]
                as LocalExportBlock.Image
        assertEquals(
            "image-id.png",
            image.entryName
        )
        assertEquals(
            "Арт",
            image.description
        )

        assertEquals(
            "Не потерять",
            (
                chapter.blocks[4]
                    as LocalExportBlock
                        .Paragraph
                ).text
        )
        assertTrue(
            chapter.warnings.any {
                it.contains(
                    "mysteryBlock"
                )
            }
        )
    }

    @Test
    fun txtWriterStreamsChaptersAndReportsProgress() {
        val book =
            LocalExportBook(
                title = "Большая книга",
                author = "Автор",
                description = "",
                languageLabel = "",
                slugUrl = "book",
                coverName = null,
                chapters = listOf(
                    LocalExportChapterRef(
                        number = "0",
                        title = "Пролог",
                        volume = "1",
                        chapterId = 1L,
                        archiveName =
                            "v1-n0-1.zip"
                    ),
                    LocalExportChapterRef(
                        number = "1",
                        title = "",
                        volume = "1",
                        chapterId = 2L,
                        archiveName =
                            "v1-n1-2.zip"
                    )
                )
            )

        val output =
            ByteArrayOutputStream()
        val progress =
            mutableListOf<
                Pair<Int, Int>
            >()

        LocalBookTxtWriter().write(
            book = book,
            output = output,
            readChapter = {
                    reference ->
                LocalExportChapter(
                    number =
                        reference.number,
                    title =
                        reference.title,
                    blocks = listOf(
                        LocalExportBlock
                            .Paragraph(
                                "Текст"
                            ),
                        LocalExportBlock
                            .Image(
                                entryName =
                                    "art.jpg",
                                extension =
                                    "jpg",
                                description =
                                    "Иллюстрация главы"
                            )
                    )
                )
            },
            onProgress = {
                progress +=
                    it.completedChapters to
                        it.totalChapters
            }
        )

        val text =
            output.toString(
                Charsets.UTF_8.name()
            )

        assertTrue(
            text.contains(
                "Большая книга"
            )
        )
        assertTrue(
            text.contains(
                "Глава 0 — Пролог"
            )
        )
        assertTrue(
            text.contains(
                "Глава 1"
            )
        )
        assertTrue(
            text.contains(
                "[Иллюстрация: Иллюстрация главы]"
            )
        )
        assertEquals(
            listOf(
                0 to 2,
                1 to 2,
                2 to 2
            ),
            progress
        )
    }

    @Test
    fun ambiguousMissingArchiveIsNotSilentlyGuessed() {
        val resolved =
            resolveChapterArchiveName(
                volume = "1",
                number = "7",
                chapterId = null,
                branchIds =
                    emptyList(),
                availableFileNames =
                    setOf(
                        "v1-n7-10.zip",
                        "v1-n7-11.zip"
                    )
            )

        assertEquals(
            null,
            resolved
        )
    }
}
