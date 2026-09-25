package com.readerlb.app.export

import com.readerlb.app.importer.ParsedBook
import com.readerlb.app.importer.ParsedChapter
import com.readerlb.app.importer.RanobeLibPackageBuilder
import com.readerlb.app.importer.ReaderBlock
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderLbLocalExportRoundTripTest {

    @Test
    fun readerLbPackageRoundTripsIntoReadableFormats() {
        val root =
            Files.createTempDirectory(
                "readerlb-export-roundtrip"
            )
                .toFile()

        try {
            val source =
                ParsedBook(
                    title = "Круговой тест",
                    author = "Автор",
                    description =
                        "Описание",
                    language = "ko",
                    chapters = listOf(
                        ParsedChapter(
                            number = "0",
                            title = "Пролог",
                            blocks = listOf(
                                ReaderBlock
                                    .Paragraph(
                                        text =
                                            "Первый абзац",
                                        centered =
                                            true
                                    ),
                                ReaderBlock
                                    .Quote(
                                        listOf(
                                            "Цитата"
                                        )
                                    )
                            )
                        ),
                        ParsedChapter(
                            number = "0.5",
                            title =
                                "Интерлюдия",
                            blocks = listOf(
                                ReaderBlock
                                    .Paragraph(
                                        "Второй текст"
                                    ),
                                ReaderBlock
                                    .Image(
                                        bytes =
                                            byteArrayOf(
                                                1,
                                                2,
                                                3,
                                                4
                                            ),
                                        extension =
                                            "png",
                                        description =
                                            "Иллюстрация"
                                    )
                            )
                        ),
                        ParsedChapter(
                            number = "001",
                            title =
                                "С нулями",
                            blocks = listOf(
                                ReaderBlock
                                    .HorizontalRule,
                                ReaderBlock
                                    .Paragraph(
                                        "Финал"
                                    )
                            )
                        )
                    ),
                    coverBytes =
                        byteArrayOf(
                            9,
                            8,
                            7
                        ),
                    coverExtension =
                        "jpg"
                )

            val built =
                RanobeLibPackageBuilder(
                    nowMillis = {
                        123456L
                    }
                )
                    .build(
                        book = source,
                        rootDir = root
                    )

            val available =
                built.titleDir
                    .listFiles()
                    .orEmpty()
                    .map(File::getName)
                    .toSet()
            val exportBook =
                parseLocalExportBook(
                    infoText =
                        File(
                            built.titleDir,
                            "info.json"
                        ).readText(),
                    chaptersText =
                        File(
                            built.titleDir,
                            "chapters.json"
                        ).readText(),
                    folderName =
                        built.titleDir.name,
                    availableFileNames =
                        available
                )

            assertEquals(
                listOf(
                    "0",
                    "0.5",
                    "001"
                ),
                exportBook.chapters
                    .map {
                        it.number
                    }
            )
            assertEquals(
                "Автор",
                exportBook.author
            )

            fun readChapter(
                reference:
                    LocalExportChapterRef
            ): LocalExportChapter {
                val file =
                    File(
                        built.titleDir,
                        requireNotNull(
                            reference
                                .archiveName
                        )
                    )
                val archive =
                    file.inputStream()
                        .use(
                            ::inspectLocalChapterArchive
                        )

                return parseLocalChapterDocument(
                    number =
                        reference.number,
                    title =
                        reference.title,
                    dataText =
                        archive.dataText,
                    archiveEntryNames =
                        archive.entryNames
                )
            }

            fun copyImage(
                reference:
                    LocalExportChapterRef,
                image:
                    LocalExportBlock.Image,
                output: OutputStream
            ) {
                val file =
                    File(
                        built.titleDir,
                        requireNotNull(
                            reference
                                .archiveName
                        )
                    )
                ZipFile(file).use {
                        zip ->
                    val entry =
                        requireNotNull(
                            zip.getEntry(
                                image.entryName
                            )
                        )
                    zip.getInputStream(
                        entry
                    ).use {
                        input ->
                        input.copyTo(
                            output
                        )
                    }
                }
            }

            val txt =
                ByteArrayOutputStream()
            LocalBookTxtWriter()
                .write(
                    book = exportBook,
                    output = txt,
                    readChapter =
                        ::readChapter
                )
            val txtText =
                txt.toString(
                    Charsets.UTF_8.name()
                )
            assertTrue(
                txtText.contains(
                    "Глава 0 — Пролог"
                )
            )
            assertTrue(
                txtText.contains(
                    "[Иллюстрация: Иллюстрация]"
                )
            )

            val epub =
                ByteArrayOutputStream()
            LocalBookEpubWriter()
                .write(
                    book = exportBook,
                    output = epub,
                    readChapter =
                        ::readChapter,
                    copyCover =
                        exportBook.coverName
                            ?.let {
                                    cover ->
                                {
                                        output:
                                            OutputStream ->
                                    File(
                                        built.titleDir,
                                        cover
                                    )
                                        .inputStream()
                                        .use {
                                            it.copyTo(
                                                output
                                            )
                                        }
                                }
                            },
                    copyChapterImage =
                        ::copyImage
                )

            val epubEntries =
                mutableSetOf<String>()
            ZipInputStream(
                epub.toByteArray()
                    .inputStream()
            ).use {
                    zip ->
                while (true) {
                    val entry =
                        zip.nextEntry
                            ?: break
                    epubEntries +=
                        entry.name
                    zip.closeEntry()
                }
            }

            assertTrue(
                "OEBPS/nav.xhtml" in
                    epubEntries
            )
            assertTrue(
                "OEBPS/chapters/chapter-3.xhtml" in
                    epubEntries
            )
            assertTrue(
                epubEntries.any {
                    it.startsWith(
                        "OEBPS/images/chapter-2-image-"
                    )
                }
            )

            val fb2 =
                ByteArrayOutputStream()
            LocalBookFb2Writer()
                .write(
                    book = exportBook,
                    output = fb2,
                    readChapter =
                        ::readChapter,
                    copyCover =
                        exportBook.coverName
                            ?.let {
                                    cover ->
                                {
                                        output:
                                            OutputStream ->
                                    File(
                                        built.titleDir,
                                        cover
                                    )
                                        .inputStream()
                                        .use {
                                            it.copyTo(
                                                output
                                            )
                                        }
                                }
                            },
                    copyChapterImage =
                        ::copyImage
                )

            val fb2Text =
                fb2.toString(
                    Charsets.UTF_8.name()
                )
            assertTrue(
                fb2Text.contains(
                    "<book-title>Круговой тест</book-title>"
                )
            )
            assertTrue(
                fb2Text.contains(
                    "Глава 001 — С нулями"
                )
            )
            assertTrue(
                fb2Text.contains(
                    "<binary id=\"image-2-2\""
                )
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
