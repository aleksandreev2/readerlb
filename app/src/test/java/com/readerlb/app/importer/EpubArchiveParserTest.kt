package com.readerlb.app.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class EpubArchiveParserTest {

    private val parser = EpubArchiveParser()

    @Test
    fun rejectsTruncatedEpubInsteadOfGuessing() {
        val epub = Files.createTempFile(
            "readerlb_truncated_",
            ".epub"
        ).toFile()
        epub.writeBytes(
            byteArrayOf(
                'P'.code.toByte(),
                'K'.code.toByte(),
                3,
                4,
                1,
                2,
                3
            )
        )

        try {
            parser.parse(epub)
            fail(
                "Expected truncated EPUB to be rejected"
            )
        } catch (_: Exception) {
            // Any parser/ZIP exception is acceptable here:
            // the important contract is no guessed book.
        } finally {
            epub.delete()
        }
    }

    @Test
    fun rejectsEpubWithoutContainerMetadata() {
        val epub = Files.createTempFile(
            "readerlb_missing_container_",
            ".epub"
        ).toFile()

        ZipOutputStream(
            epub.outputStream()
        ).use { zip ->
            put(
                zip,
                "mimetype",
                "application/epub+zip"
            )
        }

        try {
            parser.parse(epub)
            fail(
                "Expected EPUB without container.xml " +
                    "to be rejected"
            )
        } catch (error: Exception) {
            assertTrue(
                error.message
                    .orEmpty()
                    .contains(
                        "META-INF/container.xml"
                    )
            )
        } finally {
            epub.delete()
        }
    }

    @Test
    fun parsesNamespacedOpfAndContainer() {
        val epub = buildEpub(
            namespacedOpf = true,
            namespacedContainer = true,
            docs = listOf(
                Doc("ch0001", "text/ch0001.xhtml", "<h1>Глава 1 — Начало</h1><p>Первый достаточно длинный абзац главы.</p>"),
                Doc("ch0002", "text/ch0002.xhtml", "<h1>Глава 2 — Дальше</h1><p>Второй достаточно длинный абзац главы.</p>")
            )
        )

        val book = parser.parse(epub)

        assertEquals(listOf("1", "2"), book.chapters.map { it.number })
        assertEquals("Тестовая книга", book.title)
    }

    @Test
    fun preservesOriginalPartialRangeInsteadOfRenumberingFromOne() {
        val epub = buildEpub(
            docs = listOf(
                Doc("ch0431", "text/ch0431.xhtml", "<h1>Глава 431 — A</h1><p>Содержимое четыреста тридцать первой главы.</p>"),
                Doc("ch0432", "text/ch0432.xhtml", "<h1>Глава 432 — B</h1><p>Содержимое четыреста тридцать второй главы.</p>"),
                Doc("ch0433", "text/ch0433.xhtml", "<h1>Глава 433 — C</h1><p>Содержимое четыреста тридцать третьей главы.</p>")
            )
        )

        val book = parser.parse(epub)

        assertEquals(listOf("431", "432", "433"), book.chapters.map { it.number })
    }

    @Test
    fun excludesServicePagesAndFindsChapterNumbersInsideSecFiles() {
        val epub = buildEpub(
            docs = listOf(
                Doc("title", "text/title.xhtml", "<h1>Тестовая книга</h1><p>Полное издание и служебная информация.</p>"),
                Doc("translation", "text/translation.xhtml", "<h1>Перевод</h1><p>Служебная информация о переводе книги.</p>"),
                Doc("sec0000", "text/sec0000.xhtml", "<h1>Справочник</h1><p>Справочные материалы без номера главы.</p>"),
                Doc("sec0001", "text/sec0001.xhtml", "<h1>Первая</h1><p>Глава 1 Первый достаточно длинный текст главы.</p>"),
                Doc("sec0002", "text/sec0002.xhtml", "<h1>Вторая</h1><p>Глава 2 Второй достаточно длинный текст главы.</p>")
            )
        )

        val book = parser.parse(epub)

        assertEquals(listOf("1", "2"), book.chapters.map { it.number })
        assertTrue(book.chapters.none { it.title.contains("Справочник") })
    }

    @Test
    fun detectsCyrillicChapterWordInsideText() {
        val epub = buildEpub(
            docs = listOf(
                Doc(
                    "sec0002",
                    "text/sec0002.xhtml",
                    "<h1>Вторая</h1><p>Глава 2. Текст главы.</p>"
                )
            )
        )

        val book = parser.parse(epub)

        assertEquals(listOf("2"), book.chapters.map { it.number })
    }

    @Test
    fun supportsMixedFilenameAndTextNumbering() {
        val epub = buildEpub(
            docs = listOf(
                Doc("ch0001", "text/ch0001.xhtml", "<h1>Глава 1</h1><p>Первая глава с номером в имени файла.</p>"),
                Doc("sec0002", "text/sec0002.xhtml", "<h1>Вторая</h1><p>Глава 2 Вторая глава с номером только внутри текста.</p>")
            )
        )

        val book = parser.parse(epub)

        assertEquals(
            "Mixed filename/text numbering should preserve both chapters: " +
                book.chapters.map { it.number },
            listOf("1", "2"),
            book.chapters.map { it.number }
        )
    }

    @Test
    fun reportsGapsInsteadOfSilentlyCollapsingChapterNumbers() {
        val epub = buildEpub(
            docs = listOf(
                Doc("ch0001", "text/ch0001.xhtml", "<h1>Глава 1</h1><p>Содержимое первой главы достаточно длинное.</p>"),
                Doc("ch0003", "text/ch0003.xhtml", "<h1>Глава 3</h1><p>Содержимое третьей главы достаточно длинное.</p>")
            )
        )

        val book = parser.parse(epub)

        assertEquals(listOf("1", "3"), book.chapters.map { it.number })
        assertTrue(book.issues.any { it.code == "CHAPTER_GAPS" && it.message.contains("2") })
    }

    @Test
    fun epub3TocNavigationIsUsedWithoutPageListOverrides() {
        val epub = buildEpubWithNav(
            docs = listOf(
                Doc(
                    "sec0000",
                    "text/sec0000.xhtml",
                    "<h1>Справочник</h1><p>Служебный справочный раздел.</p>"
                ),
                Doc(
                    "sec0001",
                    "text/sec0001.xhtml",
                    "<h1>Первое название</h1><p>Содержимое первой главы без номера в тексте.</p>"
                ),
                Doc(
                    "sec0002",
                    "text/sec0002.xhtml",
                    "<h1>Второе название</h1><p>Содержимое второй главы без номера в тексте.</p>"
                )
            ),
            tocLabels = listOf(
                "Справочник. Термины",
                "Глава 1. Первое название",
                "Глава 2. Второе название"
            )
        )

        val book = parser.parse(epub)

        assertEquals(
            listOf("1", "2"),
            book.chapters.map { it.number }
        )
        assertEquals(
            listOf(
                "Первое название",
                "Второе название"
            ),
            book.chapters.map { it.title }
        )
        assertTrue(
            book.issues.none {
                it.code == "UNNUMBERED_CONTENT_OMITTED"
            }
        )
    }

    @Test
    fun titlePageMentioningChapterZeroDoesNotShadowRealIllustratedChapterZero() {
        val firstImage = byteArrayOf(
            0x89.toByte(),
            'P'.code.toByte(),
            'N'.code.toByte(),
            'G'.code.toByte(),
            13,
            10,
            26,
            10,
            1
        )
        val secondImage = byteArrayOf(
            0x89.toByte(),
            'P'.code.toByte(),
            'N'.code.toByte(),
            'G'.code.toByte(),
            13,
            10,
            26,
            10,
            2
        )

        val epub = buildEpub(
            docs = listOf(
                Doc(
                    "titlepage",
                    "text/title.xhtml",
                    "<section class=\"titlepage\">" +
                        "<h1>Тестовая книга</h1>" +
                        "<p>В издание включено глав: 41.</p>" +
                        "<p>Глава 0 и главы 50–89</p>" +
                        "</section>"
                ),
                Doc(
                    "ch0",
                    "text/chapter_0000.xhtml",
                    "<h1>Глава 0</h1>" +
                        "<div class=\"illustration\">" +
                        "<img src=\"../images/ch0000_01.png\" " +
                        "alt=\"Глава 0, страница 1\"/>" +
                        "</div>" +
                        "<div class=\"illustration\">" +
                        "<img src=\"../images/ch0000_02.png\" " +
                        "alt=\"Глава 0, страница 2\"/>" +
                        "</div>"
                ),
                Doc(
                    "ch50",
                    "text/chapter_0050.xhtml",
                    "<h1>Глава 50</h1><p>Текст главы 50.</p>"
                )
            ),
            extraEntries = mapOf(
                "OEBPS/images/ch0000_01.png" to firstImage,
                "OEBPS/images/ch0000_02.png" to secondImage
            )
        )

        val book = parser.parse(
            epub,
            "Тестовая_книга_главы_0_50-50.epub"
        )

        assertEquals(
            listOf("0", "50"),
            book.chapters.map { it.number }
        )

        val chapterZero = book.chapters
            .first { it.number == "0" }
        val images = chapterZero.blocks
            .filterIsInstance<ReaderBlock.Image>()

        assertEquals(2, images.size)
        assertTrue(
            images[0].bytes.contentEquals(firstImage)
        )
        assertTrue(
            images[1].bytes.contentEquals(secondImage)
        )
        assertTrue(
            book.issues.none {
                it.code == "DUPLICATE_CHAPTER_NUMBERS"
            }
        )
        assertTrue(
            book.issues.none {
                it.code == "CHAPTER_GAPS"
            }
        )
        assertTrue(
            book.issues.none {
                it.code == "SOURCE_RANGE_MISMATCH"
            }
        )
    }

    @Test
    fun underscoreSeparatedSplitFilenameDoesNotCreateFalseRangeWarnings() {
        val docs = buildList {
            add(
                Doc(
                    "ch0",
                    "text/chapter_0000.xhtml",
                    "<h1>Глава 0</h1><p>Нулевая глава.</p>"
                )
            )
            (50..89).forEach { chapter ->
                add(
                    Doc(
                        "ch$chapter",
                        "text/chapter_" +
                            chapter.toString().padStart(4, '0') +
                            ".xhtml",
                        "<h1>Глава $chapter</h1>" +
                            "<p>Текст главы $chapter.</p>"
                    )
                )
            }
        }

        val epub = buildEpub(docs = docs)
        val book = parser.parse(
            epub,
            "Я_стал_мастером_бессознательного_флирта_" +
                "главы_0_50_89.epub"
        )

        assertEquals(41, book.chapters.size)
        assertEquals(
            listOf("0") +
                (50..89).map(Int::toString),
            book.chapters.map { it.number }
        )
        assertTrue(
            book.issues.none {
                it.code == "CHAPTER_GAPS"
            }
        )
        assertTrue(
            book.issues.none {
                it.code == "SOURCE_RANGE_MISMATCH"
            }
        )
    }

    @Test
    fun detectsDomNekromantaEditionWithoutTurningCreditPageIntoChapter() {
        val epub = buildEpub(
            docs = listOf(
                Doc(
                    "translation",
                    "text/translation.xhtml",
                    "<h1>Сведения о переводе</h1>" +
                        "<p>Перевод выполнен командой «Дом Некроманта».</p>" +
                        "<p><a href=\"https://ranobelib.me/ru/team/" +
                        "11969--dom-nekromanta\">Дом Некроманта</a></p>"
                ),
                Doc(
                    "ch1",
                    "text/chapter_0001.xhtml",
                    "<h1>Глава 1</h1><p>Обычный текст главы.</p>"
                )
            )
        )

        val book = parser.parse(epub)

        assertTrue(book.domNekromantaEdition)
        assertEquals(
            listOf("1"),
            book.chapters.map { it.number }
        )
    }

    @Test
    fun epub3PrologueMappedToCh0001DoesNotBecomeChapterOne() {
        val epub = buildEpubWithNav(
            docs = listOf(
                Doc(
                    "ch0001",
                    "text/ch0001.xhtml",
                    "<h1>Пролог</h1><p>Содержательный пролог.</p>"
                ),
                Doc(
                    "ch0002",
                    "text/ch0002.xhtml",
                    "<h1>Глава 2</h1><p>Вторая глава после пролога.</p>"
                )
            ),
            tocLabels = listOf(
                "Пролог",
                "Глава 2. Мидбосс, будущий союзник"
            )
        )

        val book = parser.parse(epub)

        assertEquals(
            listOf("0", "2"),
            book.chapters.map { it.number }
        )
        assertTrue(
            book.chapters.none { it.number == "1" }
        )
    }

    @Test
    fun translatorAfterwordInSecFileIsOmittedAfterLastNumberedChapter() {
        val epub = buildEpubWithNav(
            docs = listOf(
                Doc(
                    "sec1192",
                    "text/sec1192.xhtml",
                    "<h1>Глава 1192</h1><p>Последняя основная глава.</p>"
                ),
                Doc(
                    "sec1193",
                    "text/sec1193.xhtml",
                    "<h1>Послесловие переводчика</h1><p>Служебное послесловие команды перевода.</p>"
                )
            ),
            tocLabels = listOf(
                "Глава 1192. Верховенство Конечного Истока",
                "Послесловие переводчика"
            )
        )

        val book = parser.parse(epub)

        assertEquals(
            listOf("1192"),
            book.chapters.map { it.number }
        )
        assertTrue(
            book.chapters.none {
                it.title.contains(
                    "Послесловие переводчика",
                    ignoreCase = true
                )
            }
        )
    }

    @Test
    fun ncxNavigationOverridesTechnicalFilenameSequence() {
        val epub = buildEpubWithNcx(
            docs = listOf(
                Doc(
                    "chapter0001",
                    "Text/chapter0001.xhtml",
                    "<p>Пролог без номера внутри текста.</p>"
                ),
                Doc(
                    "chapter0002",
                    "Text/chapter0002.xhtml",
                    "<p>Первая корейская глава без слова Chapter.</p>"
                ),
                Doc(
                    "chapter0003",
                    "Text/chapter0003.xhtml",
                    "<p>Вторая корейская глава без слова Chapter.</p>"
                )
            ),
            labels = listOf(
                "프롤로그",
                "1. 야호",
                "2. 적합도 1.1"
            )
        )

        val book = parser.parse(epub)

        assertEquals(
            listOf("0", "1", "2"),
            book.chapters.map { it.number }
        )
        assertEquals(
            listOf("프롤로그", "야호", "적합도 1.1"),
            book.chapters.map { it.title }
        )
        assertTrue(
            book.issues.any {
                it.code == "TOC_NUMBERING_USED"
            }
        )
    }

    @Test
    fun chapterFilenameWorksWhenHeadingIsNotRussianOrEnglish() {
        val epub = buildEpub(
            opfVersion = "2.0",
            docs = listOf(
                Doc("chapter0001", "Text/chapter0001.xhtml", "<h1>첫 번째 이야기</h1><p>한국어 본문이 충분히 길게 들어 있는 첫 번째 장입니다.</p>"),
                Doc("chapter0002", "Text/chapter0002.xhtml", "<h1>두 번째 이야기</h1><p>한국어 본문이 충분히 길게 들어 있는 두 번째 장입니다.</p>")
            )
        )

        val book = parser.parse(epub)

        assertEquals(listOf("1", "2"), book.chapters.map { it.number })
    }

    @Test
    fun fallsBackToSpineOrderOnlyWhenNoNumberingExists() {
        val epub = buildEpub(
            docs = listOf(
                Doc("partA", "text/part-a.xhtml", "<h1>Начало</h1><p>Первый длинный фрагмент без номера главы вообще.</p>"),
                Doc("partB", "text/part-b.xhtml", "<h1>Продолжение</h1><p>Второй длинный фрагмент без номера главы вообще.</p>")
            )
        )

        val book = parser.parse(epub)

        assertEquals(listOf("1", "2"), book.chapters.map { it.number })
        assertTrue(book.issues.any { it.code == "NUMBERING_INFERRED" })
    }

    @Test
    fun fallbackNumberingUsesExactRangeFromFilename() {
        val epub = buildEpub(
            docs = listOf(
                Doc("partA", "text/part-a.xhtml", "<h1>Часть A</h1><p>Первый длинный фрагмент без номера.</p>"),
                Doc("partB", "text/part-b.xhtml", "<h1>Часть B</h1><p>Второй длинный фрагмент без номера.</p>"),
                Doc("partC", "text/part-c.xhtml", "<h1>Часть C</h1><p>Третий длинный фрагмент без номера.</p>")
            )
        )

        val book = parser.parse(
            epub,
            "Тестовая_книга_главы_431-433.epub"
        )

        assertEquals(
            listOf("431", "432", "433"),
            book.chapters.map { it.number }
        )
        assertTrue(
            book.issues.any {
                it.code == "NUMBERING_FROM_FILENAME" &&
                    it.severity == ImportIssueSeverity.INFO
            }
        )
        assertTrue(
            book.issues.none {
                it.code == "SOURCE_RANGE_MISMATCH"
            }
        )
    }

    @Test
    fun fallbackWithoutReliableRangeRequiresAcknowledgement() {
        val epub = buildEpub(
            docs = listOf(
                Doc("partA", "text/part-a.xhtml", "<h1>Начало</h1><p>Первый длинный фрагмент без номера.</p>"),
                Doc("partB", "text/part-b.xhtml", "<h1>Продолжение</h1><p>Второй длинный фрагмент без номера.</p>")
            )
        )

        val book = parser.parse(epub, "Тестовая_книга.epub")

        assertEquals(listOf("1", "2"), book.chapters.map { it.number })
        assertTrue(
            book.issues.any {
                it.code == "NUMBERING_INFERRED" &&
                    it.severity == ImportIssueSeverity.WARNING
            }
        )
    }

    @Test
    fun mismatchedFilenameRangeDoesNotInventMissingChapterNumbers() {
        val epub = buildEpub(
            docs = listOf(
                Doc("partA", "text/part-a.xhtml", "<h1>Начало</h1><p>Первый длинный фрагмент без номера.</p>"),
                Doc("partB", "text/part-b.xhtml", "<h1>Продолжение</h1><p>Второй длинный фрагмент без номера.</p>")
            )
        )

        val book = parser.parse(
            epub,
            "Тестовая_книга_главы_431-433.epub"
        )

        assertEquals(listOf("1", "2"), book.chapters.map { it.number })
        assertTrue(book.issues.any { it.code == "NUMBERING_INFERRED" })
        assertTrue(book.issues.any { it.code == "SOURCE_RANGE_MISMATCH" })
    }

    @Test
    fun validatesDeclaredRangeFromSourceFilename() {
        val epub = buildEpub(
            docs = listOf(
                Doc("ch0001", "text/ch0001.xhtml", "<h1>Глава 1</h1><p>Содержимое первой главы достаточно длинное.</p>"),
                Doc("ch0002", "text/ch0002.xhtml", "<h1>Глава 2</h1><p>Содержимое второй главы достаточно длинное.</p>")
            )
        )

        val book = parser.parse(epub, "Книга_главы_1-3.epub")

        assertTrue(book.issues.any {
            it.code == "SOURCE_RANGE_MISMATCH" && it.message.contains("3")
        })
    }

    @Test
    fun numberedChapterWithServiceWordInTitleIsNotDropped() {
        val epub = buildEpub(
            docs = listOf(
                Doc(
                    "ch0010",
                    "text/ch0010.xhtml",
                    "<h1>Глава 10 — Справочник мага</h1><p>Обычный текст десятой главы.</p>"
                )
            )
        )

        val book = parser.parse(epub)

        assertEquals(listOf("10"), book.chapters.map { it.number })
    }

    @Test
    fun preservesListAndPreformattedTextBlocks() {
        val epub = buildEpub(
            docs = listOf(
                Doc(
                    "ch0001",
                    "text/ch0001.xhtml",
                    "<h1>Глава 1</h1><ul><li>Первый пункт</li><li>Второй пункт</li></ul><pre>СТАТУС: 10/10</pre>"
                )
            )
        )

        val book = parser.parse(epub)
        val paragraphs = book.chapters.single().blocks
            .filterIsInstance<ReaderBlock.Paragraph>()
            .map { it.text }

        assertTrue(paragraphs.contains("Первый пункт"))
        assertTrue(paragraphs.contains("Второй пункт"))
        assertTrue(paragraphs.contains("СТАТУС: 10/10"))
    }

    @Test
    fun commonServicePagesDoNotTriggerUnnumberedWarning() {
        val epub = buildEpub(
            docs = listOf(
                Doc("title", "title.xhtml", "<h1>Название</h1><p>Полное издание и статистика книги.</p>"),
                Doc("info", "text/info.xhtml", "<h1>Сведения о переводе</h1><p>Информация о команде перевода.</p>"),
                Doc("fullversion", "text/fullversion.xhtml", "<h1>Полная версия</h1><p>Служебное сообщение о полной версии.</p>"),
                Doc("ch0001", "text/ch0001.xhtml", "<h1>Глава 1</h1><p>Первая глава с достаточным количеством текста.</p>")
            )
        )

        val book = parser.parse(epub)

        assertEquals(listOf("1"), book.chapters.map { it.number })
        assertTrue(book.issues.none { it.code == "UNNUMBERED_CONTENT_OMITTED" })
    }

    @Test
    fun preservesPrologueAsChapterZero() {
        val epub = buildEpub(
            docs = listOf(
                Doc("prologue", "text/prologue.xhtml", "<h1>Пролог</h1><p>Длинный содержательный пролог без явного номера главы.</p>"),
                Doc("ch0001", "text/ch0001.xhtml", "<h1>Глава 1</h1><p>Первая глава с нормальным номером.</p>")
            )
        )

        val book = parser.parse(epub)

        assertEquals(listOf("0", "1"), book.chapters.map { it.number })
        assertTrue(book.issues.none { it.code == "UNNUMBERED_CONTENT_OMITTED" })
    }

    @Test
    fun serviceSecFilesAroundRealChapterRangeStayOutOfBook() {
        val epub = buildEpub(
            docs = listOf(
                Doc(
                    "title",
                    "text/title.xhtml",
                    "<h1>Полное издание</h1>" +
                        "<p>1192 главы · справочник · " +
                        "послесловие переводчика</p>"
                ),
                Doc(
                    "sec0000",
                    "text/sec0000.xhtml",
                    "<h1>Справочник</h1>" +
                        "<p>Служебный материал без номера главы.</p>"
                ),
                Doc(
                    "sec0001",
                    "text/sec0001.xhtml",
                    "<h1>Глава 1</h1>" +
                        "<p>Содержимое первой главы достаточно длинное.</p>"
                ),
                Doc(
                    "sec0002",
                    "text/sec0002.xhtml",
                    "<h1>Глава 2</h1>" +
                        "<p>Содержимое второй главы достаточно длинное.</p>"
                ),
                Doc(
                    "sec0003",
                    "text/sec0003.xhtml",
                    "<h1>Послесловие переводчика</h1>" +
                        "<p>Служебный финальный раздел без номера главы.</p>"
                )
            )
        )

        val book = parser.parse(epub)

        assertEquals(
            listOf("1", "2"),
            book.chapters.map { it.number }
        )
        assertTrue(
            book.chapters.none {
                it.title.contains(
                    "Справочник",
                    ignoreCase = true
                ) ||
                    it.title.contains(
                        "Послесловие",
                        ignoreCase = true
                    )
            }
        )
    }

    @Test
    fun realStylePartialRangeReportsOnlyActualMissingChapters() {
        val docs = buildList {
            for (number in 100..118) {
                add(
                    Doc(
                        "ch$number",
                        "text/ch$number.xhtml",
                        "<h1>Глава $number</h1>" +
                            "<p>Содержимое главы $number достаточно длинное.</p>"
                    )
                )
            }
            for (number in 123..191) {
                add(
                    Doc(
                        "ch$number",
                        "text/ch$number.xhtml",
                        "<h1>Глава $number</h1>" +
                            "<p>Содержимое главы $number достаточно длинное.</p>"
                    )
                )
            }
        }
        val epub = buildEpub(docs = docs)

        val book = parser.parse(
            epub,
            "Тайтл_главы_100-191.epub"
        )

        assertEquals(88, book.chapters.size)
        assertEquals(
            "100",
            book.chapters.first().number
        )
        assertEquals(
            "191",
            book.chapters.last().number
        )

        val gap = book.issues.single {
            it.code == "CHAPTER_GAPS"
        }
        assertTrue(gap.message.contains("119"))
        assertTrue(gap.message.contains("122"))
        assertTrue(
            !gap.message.contains("99")
        )
        assertTrue(
            !gap.message.contains("192")
        )
    }

    @Test
    fun filenameRangeSupportsUnderscoreSeparator() {
        val epub = buildEpub(
            docs = listOf(
                Doc("ch0001", "text/ch0001.xhtml", "<h1>Глава 1</h1><p>Содержимое первой главы достаточно длинное.</p>"),
                Doc("ch0002", "text/ch0002.xhtml", "<h1>Глава 2</h1><p>Содержимое второй главы достаточно длинное.</p>")
            )
        )

        val book = parser.parse(epub, "Книга_главы_1_3.epub")

        assertTrue(book.issues.any { it.code == "SOURCE_RANGE_MISMATCH" })
    }

    @Test
    fun importsFigureIllustrationAndKeepsItsPosition() {
        val imageBytes = byteArrayOf(
            0x89.toByte(),
            'P'.code.toByte(),
            'N'.code.toByte(),
            'G'.code.toByte(),
            13,
            10,
            26,
            10,
            1,
            2,
            3
        )

        val epub = buildEpub(
            docs = listOf(
                Doc(
                    "ch0001",
                    "text/ch0001.xhtml",
                    "<h1>Глава 1</h1>" +
                        "<p>До картинки.</p>" +
                        "<figure class=\"illustration\">" +
                        "<img src=\"../images/ch0001_01.png\" " +
                        "alt=\"Иллюстрация к главе 1\"/>" +
                        "</figure>" +
                        "<p>После картинки.</p>"
                )
            ),
            extraEntries = mapOf(
                "OEBPS/images/ch0001_01.png" to imageBytes
            )
        )

        val book = parser.parse(epub)
        val blocks = book.chapters.single().blocks

        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is ReaderBlock.Paragraph)
        assertTrue(blocks[1] is ReaderBlock.Image)
        assertTrue(blocks[2] is ReaderBlock.Paragraph)

        val image = blocks[1] as ReaderBlock.Image
        assertEquals("png", image.extension)
        assertEquals(
            "Иллюстрация к главе 1",
            image.description
        )
        assertTrue(image.bytes.contentEquals(imageBytes))
        assertTrue(
            book.issues.none {
                it.code == "INLINE_IMAGES_OMITTED"
            }
        )
    }

    @Test
    fun fileBackedModeSpillsIllustrationWithoutKeepingBytes() {
        val imageBytes = byteArrayOf(
            0x89.toByte(),
            'P'.code.toByte(),
            'N'.code.toByte(),
            'G'.code.toByte(),
            13,
            10,
            26,
            10,
            1,
            2,
            3,
            4
        )
        val epub = buildEpub(
            docs = listOf(
                Doc(
                    "chapter0001",
                    "text/chapter_0001.xhtml",
                    "<h1>Глава 1</h1>" +
                        "<p>До картинки.</p>" +
                        "<img src=\"../images/one.png\"/>" +
                        "<p>После картинки.</p>"
                )
            ),
            extraEntries = mapOf(
                "OEBPS/images/one.png" to
                    imageBytes
            )
        )
        val assetDirectory = Files
            .createTempDirectory(
                "readerlb_assets_"
            )
            .toFile()
        val packageRoot = Files
            .createTempDirectory(
                "readerlb_streamed_package_"
            )
            .toFile()

        try {
            val book = parser.parse(
                file = epub,
                assetDirectory =
                    assetDirectory
            )
            val image = book.chapters
                .single()
                .blocks
                .filterIsInstance<
                    ReaderBlock.Image
                >()
                .single()

            assertTrue(image.bytes.isEmpty())
            assertTrue(
                !image.filePath.isNullOrBlank()
            )
            val spilled = File(
                requireNotNull(
                    image.filePath
                )
            )
            assertTrue(spilled.isFile)
            assertTrue(
                spilled
                    .readBytes()
                    .contentEquals(imageBytes)
            )
            assertEquals(
                assetDirectory.absolutePath,
                book.temporaryAssetDirectory
            )

            val built =
                RanobeLibPackageBuilder()
                    .build(
                        book = book,
                        rootDir = packageRoot
                    )
            val chapterZip = built.titleDir
                .listFiles()
                .orEmpty()
                .single {
                    it.extension == "zip"
                }

            ZipFile(chapterZip).use {
                    archive ->
                val imageEntry = archive
                    .entries()
                    .asSequence()
                    .single {
                        !it.isDirectory &&
                            it.name != "data.txt"
                    }
                val packagedBytes = archive
                    .getInputStream(imageEntry)
                    .use { it.readBytes() }

                assertTrue(
                    packagedBytes
                        .contentEquals(
                            imageBytes
                        )
                )
            }
        } finally {
            packageRoot.deleteRecursively()
            assetDirectory.deleteRecursively()
            epub.delete()
        }
    }

    @Test
    fun imageHeavyChapterZeroDoesNotRetainArchiveImageBytes() {
        val imageCount = 48
        val imageSize = 128 * 1024
        val imageEntries = linkedMapOf<String, ByteArray>()
        val imageTags = buildString {
            repeat(imageCount) { index ->
                val bytes = ByteArray(imageSize) {
                    position ->
                    when (position) {
                        0 -> 0x89.toByte()
                        1 -> 'P'.code.toByte()
                        2 -> 'N'.code.toByte()
                        3 -> 'G'.code.toByte()
                        4 -> 13
                        5 -> 10
                        6 -> 26
                        7 -> 10
                        else ->
                            ((index + position) and 0xff)
                                .toByte()
                    }
                }
                val name =
                    "OEBPS/images/ch0_" +
                        index.toString()
                            .padStart(2, '0') +
                        ".png"
                imageEntries[name] = bytes
                append(
                    "<img src=\"../images/" +
                        name.substringAfterLast('/') +
                        "\"/>"
                )
            }
        }

        val epub = buildEpub(
            docs = listOf(
                Doc(
                    "ch0",
                    "text/chapter_0000.xhtml",
                    "<h1>Глава 0</h1>" +
                        imageTags
                )
            ),
            extraEntries = imageEntries
        )
        val assetDirectory = Files
            .createTempDirectory(
                "readerlb_many_images_"
            )
            .toFile()

        try {
            val book = parser.parse(
                file = epub,
                assetDirectory =
                    assetDirectory
            )
            val images = book.chapters
                .single()
                .blocks
                .filterIsInstance<
                    ReaderBlock.Image
                >()

            assertEquals(
                imageCount,
                images.size
            )
            assertEquals(
                0,
                images.sumOf {
                    it.bytes.size
                }
            )
            assertTrue(
                images.all {
                    it.filePath
                        ?.let(::File)
                        ?.isFile == true
                }
            )
            assertEquals(
                imageCount.toLong() *
                    imageSize.toLong(),
                images.sumOf {
                    File(
                        requireNotNull(
                            it.filePath
                        )
                    ).length()
                }
            )
        } finally {
            assetDirectory.deleteRecursively()
            epub.delete()
        }
    }

    @Test
    fun realCorpusStyleChapterKeepsTwoJpegIllustrationsAndSceneBreak() {
        val firstImage = byteArrayOf(
            0xFF.toByte(),
            0xD8.toByte(),
            0xFF.toByte(),
            1,
            2,
            3
        )
        val secondImage = byteArrayOf(
            0xFF.toByte(),
            0xD8.toByte(),
            0xFF.toByte(),
            4,
            5,
            6
        )

        val epub = buildEpub(
            docs = listOf(
                Doc(
                    "chapter0062",
                    "text/chapter_0062.xhtml",
                    "<h1>Глава 62</h1>" +
                        "<p>До первой картинки.</p>" +
                        "<figure class=\"illustration\">" +
                        "<img src=\"../images/ch0062_01.jpg\" " +
                        "alt=\"Иллюстрация к главе 62\" />" +
                        "</figure>" +
                        "<p class=\"scene\">***</p>" +
                        "<p>Между картинками.</p>" +
                        "<figure class=\"illustration\">" +
                        "<img src=\"../images/ch0062_02.jpg\" " +
                        "alt=\"Иллюстрация к главе 62\" />" +
                        "</figure>" +
                        "<p>После второй картинки.</p>"
                )
            ),
            extraEntries = mapOf(
                "OEBPS/images/ch0062_01.jpg" to firstImage,
                "OEBPS/images/ch0062_02.jpg" to secondImage
            )
        )

        val book = parser.parse(epub)
        val blocks = book.chapters.single().blocks

        assertEquals(
            listOf(
                ReaderBlock.Paragraph::class,
                ReaderBlock.Image::class,
                ReaderBlock.HorizontalRule::class,
                ReaderBlock.Paragraph::class,
                ReaderBlock.Image::class,
                ReaderBlock.Paragraph::class
            ),
            blocks.map { it::class }
        )

        val images = blocks
            .filterIsInstance<ReaderBlock.Image>()
        assertEquals(2, images.size)
        assertEquals("jpg", images[0].extension)
        assertEquals("jpg", images[1].extension)
        assertTrue(
            images[0].bytes.contentEquals(firstImage)
        )
        assertTrue(
            images[1].bytes.contentEquals(secondImage)
        )
    }

    @Test
    fun coverImageDoesNotTriggerChapterImageWarning() {
        val epub = buildEpub(
            docs = listOf(
                Doc("cover", "text/cover.xhtml", "<h1>Обложка</h1><img src=\"../images/cover.jpg\"/>"),
                Doc("ch0001", "text/ch0001.xhtml", "<h1>Глава 1</h1><p>Текст главы без встроенных изображений.</p>")
            )
        )

        val book = parser.parse(epub)

        assertTrue(
            book.issues.none {
                it.code.startsWith("INLINE_IMAGE_")
            }
        )
    }

    @Test
    fun warnsWhenReferencedChapterImageFileIsMissing() {
        val epub = buildEpub(
            docs = listOf(
                Doc(
                    "chapter0001",
                    "text/chapter0001.xhtml",
                    "<h1>Глава 1</h1><p>Текст главы достаточно длинный.</p><img src=\"../images/ill_001.jpg\"/>"
                )
            )
        )

        val book = parser.parse(epub)

        assertTrue(
            book.issues.any {
                it.code == "INLINE_IMAGE_FILE_MISSING"
            }
        )
    }

    @Test
    fun preservesDecimalAndZeroPaddedNumbersFromNavigation() {
        val epub = buildEpubWithNcx(
            docs = listOf(
                Doc(
                    "chapter0001",
                    "Text/chapter0001.xhtml",
                    "<p>Спецглава.</p>"
                ),
                Doc(
                    "chapter0002",
                    "Text/chapter0002.xhtml",
                    "<p>Глава с ведущими нулями.</p>"
                ),
                Doc(
                    "chapter0003",
                    "Text/chapter0003.xhtml",
                    "<p>Обычная глава.</p>"
                )
            ),
            labels = listOf(
                "0.5 — Special",
                "001. Zero padded",
                "1. Normal"
            )
        )

        val book = parser.parse(epub)

        assertEquals(
            listOf("0.5", "001", "1"),
            book.chapters.map { it.number }
        )
    }

    @Test
    fun warnsWhenMultipleTocEntriesShareOneXhtmlFile() {
        val epub = buildEpubWithSharedNavigationFile()

        val book = parser.parse(epub)

        assertTrue(
            book.issues.any {
                it.code == "MULTIPLE_TOC_ENTRIES_ONE_FILE"
            }
        )
    }

    @Test
    fun reportsFilenameAndTextNumberMismatch() {
        val epub = buildEpub(
            docs = listOf(
                Doc("ch0010", "text/ch0010.xhtml", "<h1>Глава 11 — Ошибка</h1><p>Достаточно длинный текст для проверки несовпадения номера.</p>")
            )
        )

        val book = parser.parse(epub)

        assertEquals("11", book.chapters.single().number)
        assertTrue(book.issues.any { it.code == "NUMBER_MISMATCH" })
    }

    private fun buildEpubWithSharedNavigationFile(): File {
        val file = Files.createTempFile(
            "readerlb_shared_nav_",
            ".epub"
        ).toFile()
        file.deleteOnExit()

        val container = """<?xml version="1.0"?>
<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""

        val opf = """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf"
         xmlns:dc="http://purl.org/dc/elements/1.1/"
         version="3.0">
  <metadata><dc:title>Shared XHTML</dc:title></metadata>
  <manifest>
    <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
    <item id="chapter" href="text/chapter.xhtml" media-type="application/xhtml+xml"/>
  </manifest>
  <spine><itemref idref="chapter"/></spine>
</package>"""

        val nav = """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"
      xmlns:epub="http://www.idpf.org/2007/ops">
<body>
<nav epub:type="toc">
  <ol>
    <li><a href="text/chapter.xhtml#one">Глава 1</a></li>
    <li><a href="text/chapter.xhtml#two">Глава 2</a></li>
  </ol>
</nav>
</body>
</html>"""

        val chapter = """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml">
<body>
<h1 id="one">Глава 1</h1><p>Первая часть текста достаточно длинная.</p>
<h1 id="two">Глава 2</h1><p>Вторая часть текста достаточно длинная.</p>
</body>
</html>"""

        ZipOutputStream(file.outputStream()).use { zip ->
            put(zip, "mimetype", "application/epub+zip")
            put(zip, "META-INF/container.xml", container)
            put(zip, "OEBPS/content.opf", opf)
            put(zip, "OEBPS/nav.xhtml", nav)
            put(zip, "OEBPS/text/chapter.xhtml", chapter)
        }

        return file
    }

    private fun buildEpubWithNav(
        docs: List<Doc>,
        tocLabels: List<String>
    ): File {
        require(docs.size == tocLabels.size)

        val file = Files.createTempFile(
            "readerlb_nav_test_",
            ".epub"
        ).toFile()
        file.deleteOnExit()

        val container = """<?xml version="1.0"?>
<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""

        val manifest = buildString {
            append(
                """<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>"""
            )
            docs.forEach { doc ->
                append(
                    """<item id="${doc.id}" href="${doc.href}" media-type="application/xhtml+xml"/>"""
                )
            }
        }

        val spine = docs.joinToString("\n") { doc ->
            """<itemref idref="${doc.id}"/>"""
        }

        val opf = """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf"
         xmlns:dc="http://purl.org/dc/elements/1.1/"
         version="3.0">
  <metadata>
    <dc:title>EPUB3 nav book</dc:title>
    <dc:language>ru</dc:language>
  </metadata>
  <manifest>$manifest</manifest>
  <spine>$spine</spine>
</package>"""

        val tocItems = docs.indices.joinToString("\n") { index ->
            """<li><a href="${docs[index].href}">${tocLabels[index]}</a></li>"""
        }
        val pageListItems = docs.indices.joinToString("\n") { index ->
            """<li><a href="${docs[index].href}">PAGE-${index + 100}</a></li>"""
        }

        val nav = """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"
      xmlns:epub="http://www.idpf.org/2007/ops">
<body>
<nav epub:type="toc" id="toc">
  <ol>$tocItems</ol>
</nav>
<nav epub:type="page-list" id="pages">
  <ol>$pageListItems</ol>
</nav>
</body>
</html>"""

        ZipOutputStream(file.outputStream()).use { zip ->
            put(zip, "mimetype", "application/epub+zip")
            put(zip, "META-INF/container.xml", container)
            put(zip, "OEBPS/content.opf", opf)
            put(zip, "OEBPS/nav.xhtml", nav)

            docs.forEach { doc ->
                put(
                    zip,
                    "OEBPS/${doc.href}",
                    """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>${doc.id}</title></head>
<body>${doc.body}</body>
</html>"""
                )
            }
        }

        return file
    }

    private fun buildEpubWithNcx(
        docs: List<Doc>,
        labels: List<String>
    ): File {
        require(docs.size == labels.size)

        val file = Files.createTempFile(
            "readerlb_ncx_test_",
            ".epub"
        ).toFile()
        file.deleteOnExit()

        val container = """<?xml version="1.0"?>
<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>"""

        val manifest = buildString {
            append(
                """<item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>"""
            )
            docs.forEach { doc ->
                append(
                    """<item id="${doc.id}" href="${doc.href}" media-type="application/xhtml+xml"/>"""
                )
            }
        }

        val spine = docs.joinToString("\n") { doc ->
            """<itemref idref="${doc.id}"/>"""
        }

        val opf = """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf"
         xmlns:dc="http://purl.org/dc/elements/1.1/"
         version="2.0">
  <metadata>
    <dc:title>NCX book</dc:title>
    <dc:language>ko</dc:language>
  </metadata>
  <manifest>$manifest</manifest>
  <spine toc="ncx">$spine</spine>
</package>"""

        val navPoints = docs.indices.joinToString("\n") { index ->
            """<navPoint id="n$index" playOrder="${index + 1}">
<navLabel><text>${labels[index]}</text></navLabel>
<content src="${docs[index].href}"/>
</navPoint>"""
        }

        val ncx = """<?xml version="1.0" encoding="utf-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
<navMap>$navPoints</navMap>
</ncx>"""

        ZipOutputStream(file.outputStream()).use { zip ->
            put(zip, "mimetype", "application/epub+zip")
            put(zip, "META-INF/container.xml", container)
            put(zip, "OEBPS/content.opf", opf)
            put(zip, "OEBPS/toc.ncx", ncx)

            docs.forEach { doc ->
                put(
                    zip,
                    "OEBPS/${doc.href}",
                    """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml">
<head><title>${doc.id}</title></head>
<body>${doc.body}</body>
</html>"""
                )
            }
        }

        return file
    }

    private fun buildEpub(
        docs: List<Doc>,
        namespacedOpf: Boolean = false,
        namespacedContainer: Boolean = false,
        opfVersion: String = "3.0",
        extraEntries: Map<String, ByteArray> = emptyMap()
    ): File {
        val file = Files.createTempFile("readerlb_test_", ".epub").toFile()
        file.deleteOnExit()

        val container = if (namespacedContainer) {
            """<?xml version="1.0"?>
<c:container xmlns:c="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
  <c:rootfiles><c:rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></c:rootfiles>
</c:container>"""
        } else {
            """<?xml version="1.0"?>
<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
  <rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
</container>"""
        }

        val manifest = docs.joinToString("\n") { doc ->
            val tag = if (namespacedOpf) "ns0:item" else "item"
            """<$tag id="${doc.id}" href="${doc.href}" media-type="application/xhtml+xml"/>"""
        }
        val spine = docs.joinToString("\n") { doc ->
            val tag = if (namespacedOpf) "ns0:itemref" else "itemref"
            """<$tag idref="${doc.id}"/>"""
        }

        val opf = if (namespacedOpf) {
            """<?xml version="1.0" encoding="utf-8"?>
<ns0:package xmlns:ns0="http://www.idpf.org/2007/opf" xmlns:dc="http://purl.org/dc/elements/1.1/" version="$opfVersion">
  <ns0:metadata>
    <dc:title>Тестовая книга</dc:title>
    <dc:creator>Автор</dc:creator>
    <dc:language>ru</dc:language>
  </ns0:metadata>
  <ns0:manifest>$manifest</ns0:manifest>
  <ns0:spine>$spine</ns0:spine>
</ns0:package>"""
        } else {
            """<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" xmlns:dc="http://purl.org/dc/elements/1.1/" version="$opfVersion">
  <metadata>
    <dc:title>Тестовая книга</dc:title>
    <dc:creator>Автор</dc:creator>
    <dc:language>ru</dc:language>
  </metadata>
  <manifest>$manifest</manifest>
  <spine>$spine</spine>
</package>"""
        }

        ZipOutputStream(file.outputStream()).use { zip ->
            put(zip, "mimetype", "application/epub+zip")
            put(zip, "META-INF/container.xml", container)
            put(zip, "OEBPS/content.opf", opf)
            docs.forEach { doc ->
                put(
                    zip,
                    "OEBPS/${doc.href}",
                    """<?xml version="1.0" encoding="utf-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>${doc.id}</title></head><body>${doc.body}</body></html>"""
                )
            }

            extraEntries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }

        return file
    }

    private fun put(zip: ZipOutputStream, name: String, text: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private data class Doc(
        val id: String,
        val href: String,
        val body: String
    )
}
