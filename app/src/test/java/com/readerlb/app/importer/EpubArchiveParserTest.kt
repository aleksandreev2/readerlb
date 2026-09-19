package com.readerlb.app.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EpubArchiveParserTest {

    private val parser = EpubArchiveParser()

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

        assertEquals(listOf(1, 2), book.chapters.map { it.number })
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

        assertEquals(listOf(431, 432, 433), book.chapters.map { it.number })
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

        assertEquals(listOf(1, 2), book.chapters.map { it.number })
        assertTrue(book.chapters.none { it.title.contains("Справочник") })
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

        assertEquals(listOf(1, 3), book.chapters.map { it.number })
        assertTrue(book.issues.any { it.code == "CHAPTER_GAPS" && it.message.contains("2") })
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

        assertEquals(listOf(1, 2), book.chapters.map { it.number })
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

        assertEquals(listOf(1, 2), book.chapters.map { it.number })
        assertTrue(book.issues.any { it.code == "NUMBERING_INFERRED" })
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
    fun reportsFilenameAndTextNumberMismatch() {
        val epub = buildEpub(
            docs = listOf(
                Doc("ch0010", "text/ch0010.xhtml", "<h1>Глава 11 — Ошибка</h1><p>Достаточно длинный текст для проверки несовпадения номера.</p>")
            )
        )

        val book = parser.parse(epub)

        assertEquals(10, book.chapters.single().number)
        assertTrue(book.issues.any { it.code == "NUMBER_MISMATCH" })
    }

    private fun buildEpub(
        docs: List<Doc>,
        namespacedOpf: Boolean = false,
        namespacedContainer: Boolean = false,
        opfVersion: String = "3.0"
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
