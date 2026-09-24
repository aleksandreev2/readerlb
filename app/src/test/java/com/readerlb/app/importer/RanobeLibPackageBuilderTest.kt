package com.readerlb.app.importer

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.file.Files
import java.util.zip.ZipFile

class RanobeLibPackageBuilderTest {

    private val builder = RanobeLibPackageBuilder(
        nowMillis = { 1_700_000_000_000L }
    )

    @Test
    fun preservesRealNumbersForPartialRanges() {
        val book = bookWithChapters("431", "432", "433")
        val root = Files.createTempDirectory("readerlb_package_").toFile()

        val built = builder.build(
            book = book,
            rootDir = root,
            firstChapter = "432",
            lastChapter = "433"
        )

        assertEquals(2, built.chapterCount)
        assertEquals("432", built.firstChapter)
        assertEquals("433", built.lastChapter)

        val chapters = JSONArray(
            built.titleDir
                .resolve("chapters.json")
                .readText()
        )

        assertEquals(
            listOf("432", "433"),
            (0 until chapters.length()).map {
                chapters
                    .getJSONObject(it)
                    .getString("number")
            }
        )

        val zipNames = built.titleDir
            .listFiles()
            .orEmpty()
            .filter { it.extension == "zip" }
            .map { it.name }
            .sorted()

        assertEquals(2, zipNames.size)
        assertTrue(zipNames.any { it.startsWith("v1-n432-") })
        assertTrue(zipNames.any { it.startsWith("v1-n433-") })
        assertFalse(zipNames.any { it.startsWith("v1-n1-") })
    }

    @Test
    fun everyGeneratedChapterContainsValidReaderDocument() {
        val book = ParsedBook(
            title = "Тест",
            chapters = listOf(
                ParsedChapter(
                    number = "1",
                    title = "Начало",
                    blocks = listOf(
                        ReaderBlock.Paragraph("Первый абзац."),
                        ReaderBlock.HorizontalRule,
                        ReaderBlock.Quote(
                            listOf("Системное сообщение")
                        )
                    )
                )
            )
        )
        val root = Files.createTempDirectory("readerlb_package_").toFile()

        val built = builder.build(
            book = book,
            rootDir = root
        )

        val zip = built.titleDir
            .listFiles()
            .orEmpty()
            .single { it.extension == "zip" }

        ZipFile(zip).use { archive ->
            val entry = archive.getEntry("data.txt")
            assertTrue(entry != null)

            val data = archive
                .getInputStream(entry)
                .use {
                    it.readBytes().toString(Charsets.UTF_8)
                }

            val document = JSONObject(data)
            assertEquals("doc", document.getString("type"))

            val nodes = document.getJSONArray("content")
            assertEquals(3, nodes.length())
            assertEquals(
                "paragraph",
                nodes.getJSONObject(0).getString("type")
            )
            assertEquals(
                "horizontalRule",
                nodes.getJSONObject(1).getString("type")
            )
            assertEquals(
                "blockquote",
                nodes.getJSONObject(2).getString("type")
            )
        }
    }

    @Test
    fun embedsIllustrationFileAndRanobeLibImageNode() {
        val imageBytes = byteArrayOf(
            0x89.toByte(),
            'P'.code.toByte(),
            'N'.code.toByte(),
            'G'.code.toByte(),
            13,
            10,
            26,
            10,
            10,
            20,
            30
        )

        val book = ParsedBook(
            title = "Книга с иллюстрацией",
            chapters = listOf(
                ParsedChapter(
                    number = "1",
                    title = "Начало",
                    blocks = listOf(
                        ReaderBlock.Paragraph(
                            "До картинки"
                        ),
                        ReaderBlock.Image(
                            bytes = imageBytes,
                            extension = "png",
                            description = "Иллюстрация"
                        ),
                        ReaderBlock.Paragraph(
                            "После картинки"
                        )
                    )
                )
            )
        )

        val root = Files
            .createTempDirectory(
                "readerlb_image_package_"
            )
            .toFile()

        val built = builder.build(
            book = book,
            rootDir = root
        )

        val chapterZip = built.titleDir
            .listFiles()
            .orEmpty()
            .single { it.extension == "zip" }

        ZipFile(chapterZip).use { archive ->
            val data = archive
                .getInputStream(
                    archive.getEntry("data.txt")
                )
                .use {
                    it.readBytes()
                        .toString(Charsets.UTF_8)
                }

            val nodes = JSONObject(data)
                .getJSONArray("content")
            assertEquals(3, nodes.length())
            assertEquals(
                "image",
                nodes.getJSONObject(1)
                    .getString("type")
            )

            val attrs = nodes
                .getJSONObject(1)
                .getJSONObject("attrs")
            assertEquals(
                "Иллюстрация",
                attrs.getString("description")
            )

            val imageId = attrs
                .getJSONArray("images")
                .getJSONObject(0)
                .getString("image")
            val entry = archive.getEntry(
                "$imageId.png"
            )

            assertTrue(entry != null)
            val storedBytes = archive
                .getInputStream(entry)
                .use { it.readBytes() }
            assertTrue(
                storedBytes.contentEquals(imageBytes)
            )
        }

        assertTrue(
            builder.verify(built).isValid
        )
    }

    @Test
    fun streamsFileBackedIllustrationIntoChapterZip() {
        val imageBytes = byteArrayOf(
            0x89.toByte(),
            'P'.code.toByte(),
            'N'.code.toByte(),
            'G'.code.toByte(),
            13,
            10,
            26,
            10,
            42,
            43,
            44
        )
        val imageFile = Files
            .createTempFile(
                "readerlb_spilled_image_",
                ".png"
            )
            .toFile()
            .apply {
                writeBytes(imageBytes)
            }

        val book = ParsedBook(
            title = "Файловая иллюстрация",
            chapters = listOf(
                ParsedChapter(
                    number = "1",
                    title = "",
                    blocks = listOf(
                        ReaderBlock.Image(
                            extension = "png",
                            description = "С диска",
                            filePath =
                                imageFile.absolutePath
                        )
                    )
                )
            )
        )
        val root = Files
            .createTempDirectory(
                "readerlb_file_image_"
            )
            .toFile()

        val built = builder.build(
            book = book,
            rootDir = root
        )
        val chapterZip = built.titleDir
            .listFiles()
            .orEmpty()
            .single {
                it.extension == "zip"
            }

        ZipFile(chapterZip).use { archive ->
            val imageEntry = archive.entries()
                .toList()
                .single {
                    it.name.endsWith(".png")
                }
            val stored = archive
                .getInputStream(imageEntry)
                .use { it.readBytes() }

            assertTrue(
                stored.contentEquals(imageBytes)
            )
        }

        assertTrue(builder.verify(built).isValid)
    }

    @Test
    fun infoJsonMatchesGeneratedPackage() {
        val book = ParsedBook(
            title = "Первоклассная удача",
            author = "Автор",
            language = "zh",
            description = "<p>Описание книги</p>",
            chapters = listOf(
                chapter("1"),
                chapter("2")
            ),
            coverBytes = byteArrayOf(1, 2, 3, 4),
            coverExtension = "png"
        )
        val root = Files.createTempDirectory("readerlb_package_").toFile()

        val built = builder.build(
            book = book,
            rootDir = root
        )

        val info = JSONObject(
            built.titleDir
                .resolve("info.json")
                .readText()
        )
        val media = info.getJSONObject("media")

        assertEquals(built.slugUrl, media.getString("slugUrl"))
        assertEquals(2, media.getInt("uploadedCount"))
        assertEquals("Первоклассная удача", media.getString("name"))
        assertTrue(media.getString("imageUrl").endsWith("/cover.png"))
        assertTrue(built.titleDir.resolve("cover.png").isFile)
    }

    @Test
    fun verifierDetectsTamperedChapterZip() {
        val root = Files.createTempDirectory("readerlb_package_").toFile()
        val built = builder.build(
            book = bookWithChapters("1", "2"),
            rootDir = root
        )

        val zip = built.titleDir
            .listFiles()
            .orEmpty()
            .first { it.name.startsWith("v1-n1-") }

        zip.writeText("not-a-zip")

        val report = builder.verify(
            built = built,
            expectedChapterNumbers = listOf("1", "2")
        )

        assertFalse(report.isValid)
        assertTrue(
            report.errors.any {
                it.contains("повреждён") ||
                    it.contains("data.txt")
            }
        )
    }

    @Test
    fun verifyRejectsChapterRangeMetadataThatDoesNotMatchFiles() {
        val root =
            Files.createTempDirectory(
                "readerlb_range_verify_"
            ).toFile()

        try {
            val built =
                builder.build(
                    book =
                        bookWithChapters(
                            "10",
                            "11",
                            "12"
                        ),
                    rootDir = root
                )

            val report =
                builder.verify(
                    built.copy(
                        firstChapter = "9",
                        lastChapter = "99"
                    )
                )

            assertFalse(report.isValid)
            assertTrue(
                report.errors.any {
                    it.contains(
                        "Первая глава"
                    )
                }
            )
            assertTrue(
                report.errors.any {
                    it.contains(
                        "Последняя глава"
                    )
                }
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun duplicateChapterNumbersAreRejectedBeforeWriting() {
        val book = ParsedBook(
            title = "Дубликаты",
            chapters = listOf(
                chapter("10"),
                chapter("10")
            )
        )
        val root = Files.createTempDirectory("readerlb_package_").toFile()

        try {
            builder.build(book = book, rootDir = root)
            fail("Expected duplicate chapter validation to fail")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("повторяются"))
        }
    }

    @Test
    fun reversedRangeIsRejectedBeforeWriting() {
        val root = Files.createTempDirectory("readerlb_package_").toFile()

        try {
            builder.build(
                book = bookWithChapters("1", "2", "3"),
                rootDir = root,
                firstChapter = "3",
                lastChapter = "1"
            )
            fail("Expected reversed range validation to fail")
        } catch (error: IllegalArgumentException) {
            assertTrue(
                error.message
                    .orEmpty()
                    .contains("Начальная глава")
            )
        }
    }

    @Test
    fun decimalAndZeroPaddedNumbersSurvivePackageRoundTrip() {
        val root = Files.createTempDirectory(
            "readerlb_package_"
        ).toFile()

        val built = builder.build(
            book = bookWithChapters(
                "0",
                "0.5",
                "001",
                "1",
                "2.91"
            ),
            rootDir = root
        )

        val chapters = JSONArray(
            built.titleDir
                .resolve("chapters.json")
                .readText()
        )

        val numbers = (0 until chapters.length())
            .map {
                chapters
                    .getJSONObject(it)
                    .getString("number")
            }

        assertEquals(
            listOf("0", "0.5", "001", "1", "2.91"),
            numbers
        )

        numbers.forEach { number ->
            assertTrue(
                built.titleDir
                    .listFiles()
                    .orEmpty()
                    .any {
                        it.name.startsWith("v1-n$number-")
                    }
            )
        }
    }

    @Test
    fun buildsAndVerifiesLargeBook() {
        val root = Files.createTempDirectory(
            "readerlb_large_package_"
        ).toFile()

        val chapters = (1..1200).map { number ->
            chapter(number.toString())
        }

        val built = builder.build(
            book = ParsedBook(
                title = "Большая тестовая новелла",
                chapters = chapters
            ),
            rootDir = root
        )

        assertEquals(1200, built.chapterCount)
        assertEquals("1", built.firstChapter)
        assertEquals("1200", built.lastChapter)

        val report = builder.verify(
            built = built,
            expectedChapterNumbers = chapters.map { it.number }
        )
        assertTrue(
            report.errors.joinToString(),
            report.isValid
        )

        assertEquals(
            1200,
            built.titleDir
                .listFiles()
                .orEmpty()
                .count {
                    it.extension == "zip"
                }
        )
    }

    @Test
    fun reportsRealChapterPreparationProgress() {
        val root = Files.createTempDirectory(
            "readerlb_progress_package_"
        ).toFile()
        val progress =
            mutableListOf<Pair<Int, Int>>()
        var verificationStarted = false

        builder.build(
            book = bookWithChapters(
                "1",
                "2",
                "3"
            ),
            rootDir = root,
            onChapterPrepared = {
                    completed,
                    total ->
                progress += completed to total
            },
            onVerifying = {
                verificationStarted = true
            }
        )

        assertEquals(
            listOf(
                0 to 3,
                1 to 3,
                2 to 3,
                3 to 3
            ),
            progress
        )
        assertTrue(verificationStarted)
    }

    @Test
    fun stableIdentityDoesNotChangeBetweenBuilds() {
        val first = builder.stableMediaId("  Культивация Онлайн ")
        val second = builder.stableMediaId("культивация онлайн")

        assertEquals(first, second)
        assertEquals(
            builder.slugify("Культивация Онлайн"),
            builder.slugify("культивация онлайн")
        )
    }

    private fun bookWithChapters(
        vararg numbers: String
    ): ParsedBook =
        ParsedBook(
            title = "Тестовая новелла",
            chapters = numbers.map(::chapter)
        )

    private fun chapter(number: String): ParsedChapter =
        ParsedChapter(
            number = number,
            title = "Глава $number",
            blocks = listOf(
                ReaderBlock.Paragraph(
                    "Содержимое главы $number"
                )
            )
        )
    @Test
    fun chapterZeroWithIllustrationBuildsAndVerifies() {
        val imageBytes = byteArrayOf(
            0x89.toByte(),
            'P'.code.toByte(),
            'N'.code.toByte(),
            'G'.code.toByte(),
            13,
            10,
            26,
            10,
            7,
            8,
            9
        )
        val book = ParsedBook(
            title = "Нулевая глава",
            chapters = listOf(
                ParsedChapter(
                    number = "0",
                    title = "Пролог",
                    blocks = listOf(
                        ReaderBlock.Paragraph(
                            "Текст нулевой главы"
                        ),
                        ReaderBlock.Image(
                            bytes = imageBytes,
                            extension = "png",
                            description = "Иллюстрация нулевой главы"
                        )
                    )
                )
            )
        )
        val root = Files
            .createTempDirectory(
                "readerlb_chapter_zero_"
            )
            .toFile()

        try {
            val built = builder.build(
                book = book,
                rootDir = root
            )

            assertEquals("0", built.firstChapter)
            assertEquals("0", built.lastChapter)
            assertEquals(1, built.chapterCount)

            val chapterZip = built.titleDir
                .listFiles()
                .orEmpty()
                .single {
                    it.name.startsWith("v1-n0-") &&
                        it.extension == "zip"
                }

            ZipFile(chapterZip).use { archive ->
                val dataEntry = archive.getEntry("data.txt")
                assertTrue(dataEntry != null)

                val document = JSONObject(
                    archive
                        .getInputStream(dataEntry)
                        .use {
                            it.readBytes()
                                .toString(Charsets.UTF_8)
                        }
                )
                val nodes = document
                    .getJSONArray("content")
                val imageNode = (0 until nodes.length())
                    .map { nodes.getJSONObject(it) }
                    .single {
                        it.getString("type") == "image"
                    }
                val imageId = imageNode
                    .getJSONObject("attrs")
                    .getJSONArray("images")
                    .getJSONObject(0)
                    .getString("image")
                val storedImage = archive.getEntry(
                    "$imageId.png"
                )

                assertTrue(storedImage != null)
                assertTrue(
                    archive
                        .getInputStream(storedImage)
                        .use { it.readBytes() }
                        .contentEquals(imageBytes)
                )
            }

            assertTrue(
                builder.verify(
                    built = built,
                    expectedChapterNumbers =
                        listOf("0")
                ).isValid
            )
        } finally {
            root.deleteRecursively()
        }
    }


}
