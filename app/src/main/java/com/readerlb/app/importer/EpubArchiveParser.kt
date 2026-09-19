package com.readerlb.app.importer

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

/**
 * Pure EPUB parser. It intentionally has no Android dependencies so the exact
 * parsing path can be covered by JVM regression tests.
 */
class EpubArchiveParser {

    fun parse(file: File, sourceName: String? = null): ParsedBook {
        ZipFile(file).use { zip ->
            val issues = mutableListOf<ImportIssue>()

            val container = readText(zip, "META-INF/container.xml")
            val containerDoc = Jsoup.parse(container, "", Parser.xmlParser())
            val opfPath = elementsByLocalName(containerDoc, "rootfile")
                .firstOrNull()
                ?.attr("full-path")
                ?.takeIf { it.isNotBlank() }
                ?: error("В EPUB не найден OPF")

            val opfDoc = Jsoup.parse(readText(zip, opfPath), "", Parser.xmlParser())
            val opfBase = opfPath.substringBeforeLast('/', "")

            val title = metaText(opfDoc, "title").ifBlank { "Локальная новелла" }
            val author = metaText(opfDoc, "creator")
            val description = metaText(opfDoc, "description")
            val language = metaText(opfDoc, "language")

            val manifest = elementsByLocalName(opfDoc, "item")
                .mapNotNull { item ->
                    val id = item.attr("id")
                    val href = item.attr("href")
                    if (id.isBlank() || href.isBlank()) null
                    else id to ManifestItem(
                        id = id,
                        href = href,
                        mediaType = item.attr("media-type"),
                        properties = item.attr("properties")
                    )
                }
                .toMap()

            val spineIds = elementsByLocalName(opfDoc, "itemref")
                .map { it.attr("idref") }
                .filter { it.isNotBlank() }

            val missingSpineRefs = spineIds.filterNot(manifest::containsKey)
            if (missingSpineRefs.isNotEmpty()) {
                issues += ImportIssue(
                    code = "MISSING_SPINE_REFS",
                    message = "В EPUB есть ссылки оглавления на отсутствующие элементы: " +
                        missingSpineRefs.take(8).joinToString() +
                        if (missingSpineRefs.size > 8) "…" else ""
                )
            }

            val coverItem = findCover(opfDoc, manifest)
            val coverBytes = coverItem?.let { item ->
                runCatching { readBytes(zip, resolve(opfBase, item.href)) }.getOrNull()
            }
            val coverExt = coverItem?.href
                ?.substringAfterLast('.', "jpg")
                ?.lowercase()
                ?.takeIf { it in setOf("jpg", "jpeg", "png", "webp") }
                ?: "jpg"

            val docs = spineIds
                .mapNotNull(manifest::get)
                .filter { it.mediaType.contains("html", ignoreCase = true) }
                .mapIndexedNotNull { spineIndex, item ->
                    parseHtmlDocument(
                        zip = zip,
                        opfBase = opfBase,
                        item = item,
                        spineIndex = spineIndex,
                        issues = issues
                    )
                }

            val explicitHref = docs
                .filter { it.hrefChapterNumber != null && !it.serviceDocument }
                .map { it.toCandidate(it.hrefChapterNumber!!) }

            val textNumbered = docs
                .filter { it.textChapterNumber != null && !it.serviceDocument }
                .map { it.toCandidate(it.textChapterNumber!!) }

            val candidates: List<ChapterCandidate>
            if (explicitHref.isNotEmpty()) {
                candidates = explicitHref
            } else if (textNumbered.isNotEmpty()) {
                candidates = textNumbered
            } else {
                val fallback = docs.filter {
                    !it.serviceDocument && it.plainText.length >= MIN_CHAPTER_TEXT
                }
                require(fallback.isNotEmpty()) { "В EPUB не удалось найти главы" }

                issues += ImportIssue(
                    code = "NUMBERING_INFERRED",
                    message = "Номера глав не найдены в EPUB. ReaderLB использовал порядок файлов.",
                    severity = ImportIssueSeverity.INFO
                )
                candidates = fallback.mapIndexed { index, doc ->
                    doc.toCandidate(index + 1)
                }
            }

            val sorted = candidates.sortedWith(
                compareBy<ChapterCandidate> { it.number }.thenBy { it.spineIndex }
            )
            val deduplicated = mutableListOf<ChapterCandidate>()
            val duplicates = mutableListOf<Int>()
            sorted.groupBy { it.number }.forEach { (number, sameNumber) ->
                deduplicated += sameNumber.first()
                if (sameNumber.size > 1) duplicates += number
            }

            if (duplicates.isNotEmpty()) {
                issues += ImportIssue(
                    code = "DUPLICATE_CHAPTER_NUMBERS",
                    message = "В EPUB повторяются номера глав: ${formatNumbers(duplicates)}"
                )
            }

            val numbers = deduplicated.map { it.number }.sorted()
            if (numbers.size >= 2) {
                val present = numbers.toHashSet()
                val gaps = (numbers.first()..numbers.last()).filterNot(present::contains)
                if (gaps.isNotEmpty()) {
                    issues += ImportIssue(
                        code = "CHAPTER_GAPS",
                        message = "В исходном EPUB отсутствуют главы: ${formatRanges(gaps)}"
                    )
                }
            }

            deduplicated.forEach { candidate ->
                val textNumber = candidate.source.textChapterNumber
                val hrefNumber = candidate.source.hrefChapterNumber
                if (textNumber != null && hrefNumber != null && textNumber != hrefNumber) {
                    issues += ImportIssue(
                        code = "NUMBER_MISMATCH",
                        message = "У файла ${candidate.source.item.href} номер $hrefNumber, " +
                            "но в тексте указана глава $textNumber."
                    )
                }
            }

            validateExpectedRange(sourceName, numbers, issues)

            val chapters = deduplicated.map { candidate ->
                ParsedChapter(
                    number = candidate.number,
                    title = cleanChapterTitle(candidate.source.heading, candidate.number),
                    blocks = candidate.source.blocks.ifEmpty {
                        listOf(ReaderBlock.Paragraph(candidate.source.plainText))
                    }
                )
            }

            require(chapters.isNotEmpty()) { "В EPUB не удалось найти главы" }

            return ParsedBook(
                title = title,
                author = author,
                description = description,
                language = language,
                chapters = chapters,
                coverBytes = coverBytes,
                coverExtension = coverExt,
                issues = issues.distinctBy { it.code to it.message }
            )
        }
    }

    private fun parseHtmlDocument(
        zip: ZipFile,
        opfBase: String,
        item: ManifestItem,
        spineIndex: Int,
        issues: MutableList<ImportIssue>
    ): HtmlDoc? {
        val path = resolve(opfBase, item.href)
        val raw = runCatching { readText(zip, path) }.getOrElse {
            issues += ImportIssue(
                code = "MISSING_CONTENT_FILE",
                message = "В EPUB отсутствует файл главы: ${item.href}"
            )
            return null
        }

        val doc = Jsoup.parse(raw, "", Parser.xmlParser())
        val body = doc.body()
        val plain = body.text().replace('\u00A0', ' ').trim()
        val heading = body.selectFirst("h1, h2, h3")?.text()?.trim().orEmpty()
        val hint = (item.id + " " + item.href + " " + item.properties).lowercase()

        return HtmlDoc(
            item = item,
            spineIndex = spineIndex,
            heading = heading,
            plainText = plain,
            blocks = extractBlocks(body, heading),
            hrefChapterNumber = chapterNumberFromHref(item.href),
            textChapterNumber = chapterNumberFromText(plain),
            serviceDocument = SERVICE_HINTS.any(hint::contains)
        )
    }

    private fun chapterNumberFromHref(href: String): Int? {
        val decoded = decodeHref(href).substringBefore('#')
        return CHAPTER_FILE_REGEX
            .find(decoded)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun chapterNumberFromText(text: String): Int? {
        return CHAPTER_TEXT_REGEX
            .find(text.take(500))
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun extractBlocks(root: Element, firstHeading: String): List<ReaderBlock> {
        val out = mutableListOf<ReaderBlock>()

        fun walk(element: Element) {
            element.children().forEach { child ->
                when (child.tagName().substringAfterLast(':').lowercase()) {
                    "h1", "h2", "h3" -> {
                        val text = child.text().trim()
                        if (text.isNotBlank() && text != firstHeading) {
                            out += ReaderBlock.Paragraph(text)
                        }
                    }
                    "p" -> {
                        val text = child.wholeText().replace('\u00A0', ' ').trim()
                        if (text.isNotBlank()) {
                            val cls = child.classNames().map(String::lowercase)
                            if (cls.any { it.contains("scene") || it.contains("separator") }) {
                                out += ReaderBlock.HorizontalRule
                            } else {
                                out += ReaderBlock.Paragraph(text)
                            }
                        }
                    }
                    "hr" -> out += ReaderBlock.HorizontalRule
                    "blockquote" -> {
                        val lines = child.select("p")
                            .map { it.text().trim() }
                            .filter(String::isNotBlank)
                        if (lines.isNotEmpty()) out += ReaderBlock.Quote(lines)
                    }
                    "div" -> {
                        val cls = child.classNames().map(String::lowercase)
                        if (cls.any {
                                it.contains("system") ||
                                    it.contains("quote") ||
                                    it.contains("notice")
                            }
                        ) {
                            val lines = child.select("p")
                                .map { it.text().trim() }
                                .filter(String::isNotBlank)
                            if (lines.isNotEmpty()) out += ReaderBlock.Quote(lines)
                            else walk(child)
                        } else {
                            walk(child)
                        }
                    }
                    "section", "article", "main" -> walk(child)
                    else -> if (child.children().isNotEmpty()) walk(child)
                }
            }
        }

        walk(root)
        return out
    }

    private fun cleanChapterTitle(title: String, number: Int): String {
        if (title.isBlank()) return ""
        return title
            .replace(
                Regex(
                    """^(?:глава|chapter)\s*$number\s*[-—.:]?\s*""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .trim()
            .takeIf { it.isNotBlank() }
            ?: "Глава $number"
    }

    private fun findCover(
        doc: Document,
        manifest: Map<String, ManifestItem>
    ): ManifestItem? {
        manifest.values.firstOrNull {
            it.properties.split(' ').any { p -> p == "cover-image" }
        }?.let { return it }

        val coverId = elementsByLocalName(doc, "meta")
            .firstOrNull { it.attr("name").equals("cover", true) }
            ?.attr("content")
        coverId?.let(manifest::get)?.let { return it }

        return manifest.values.firstOrNull {
            it.mediaType.startsWith("image/") &&
                (it.id.contains("cover", true) || it.href.contains("cover", true))
        }
    }

    private fun elementsByLocalName(doc: Document, localName: String): List<Element> {
        val wanted = localName.lowercase()
        return doc.getAllElements().filter { element ->
            val tag = element.tagName().lowercase()
            tag == wanted || tag.substringAfterLast(':') == wanted
        }
    }

    private fun metaText(doc: Document, localName: String): String {
        val wanted = localName.lowercase()
        return doc.getAllElements()
            .firstOrNull {
                val tag = it.tagName().lowercase()
                tag == wanted || tag.substringAfterLast(':') == wanted
            }
            ?.text()
            ?.trim()
            .orEmpty()
    }

    private fun resolve(base: String, href: String): String {
        val decoded = decodeHref(href).substringBefore('#')
        val raw = if (base.isBlank()) decoded else "$base/$decoded"
        val parts = ArrayDeque<String>()
        raw.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeLast()
                else -> parts.addLast(part)
            }
        }
        return parts.joinToString("/")
    }

    private fun decodeHref(value: String): String {
        // URLDecoder treats '+' as a space. EPUB hrefs may legitimately contain '+'.
        return URLDecoder.decode(
            value.replace("+", "%2B"),
            StandardCharsets.UTF_8.name()
        )
    }

    private fun readText(zip: ZipFile, path: String): String =
        readBytes(zip, path).toString(Charsets.UTF_8)

    private fun readBytes(zip: ZipFile, path: String): ByteArray {
        val entry = zip.getEntry(path) ?: error("В EPUB отсутствует $path")
        return zip.getInputStream(entry).use { it.readBytes() }
    }

    private fun validateExpectedRange(
        sourceName: String?,
        actualNumbers: List<Int>,
        issues: MutableList<ImportIssue>
    ) {
        if (sourceName.isNullOrBlank() || actualNumbers.isEmpty()) return
        val match = SOURCE_RANGE_REGEX.find(sourceName) ?: return
        val start = match.groupValues[1].toIntOrNull() ?: return
        val end = match.groupValues[2].toIntOrNull() ?: return
        if (end < start) return

        val actual = actualNumbers.toHashSet()
        val missing = (start..end).filterNot(actual::contains)
        val outside = actualNumbers.filter { it < start || it > end }

        if (missing.isNotEmpty() || outside.isNotEmpty()) {
            val details = buildList {
                if (missing.isNotEmpty()) add("не найдены: ${formatRanges(missing)}")
                if (outside.isNotEmpty()) add("вне заявленного диапазона: ${formatRanges(outside)}")
            }.joinToString("; ")
            issues += ImportIssue(
                code = "SOURCE_RANGE_MISMATCH",
                message = "Имя файла заявляет главы $start–$end, но структура EPUB не совпадает ($details)."
            )
        }
    }

    private fun formatNumbers(numbers: List<Int>): String =
        numbers.distinct().sorted().take(20).joinToString() +
            if (numbers.distinct().size > 20) "…" else ""

    private fun formatRanges(numbers: List<Int>): String {
        if (numbers.isEmpty()) return ""
        val sorted = numbers.distinct().sorted()
        val ranges = mutableListOf<IntRange>()
        var start = sorted.first()
        var previous = start

        sorted.drop(1).forEach { value ->
            if (value == previous + 1) {
                previous = value
            } else {
                ranges += start..previous
                start = value
                previous = value
            }
        }
        ranges += start..previous

        val rendered = ranges.take(8).joinToString { range ->
            if (range.first == range.last) range.first.toString()
            else "${range.first}–${range.last}"
        }
        return rendered + if (ranges.size > 8) "…" else ""
    }

    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: String
    )

    private data class HtmlDoc(
        val item: ManifestItem,
        val spineIndex: Int,
        val heading: String,
        val plainText: String,
        val blocks: List<ReaderBlock>,
        val hrefChapterNumber: Int?,
        val textChapterNumber: Int?,
        val serviceDocument: Boolean
    ) {
        fun toCandidate(number: Int) = ChapterCandidate(
            number = number,
            spineIndex = spineIndex,
            source = this
        )
    }

    private data class ChapterCandidate(
        val number: Int,
        val spineIndex: Int,
        val source: HtmlDoc
    )

    private companion object {
        const val MIN_CHAPTER_TEXT = 20

        val CHAPTER_FILE_REGEX = Regex(
            """(?:^|/)(?:ch|chapter)[-_ ]?0*(\d{1,6})\.(?:xhtml|html?)$""",
            RegexOption.IGNORE_CASE
        )

        val CHAPTER_TEXT_REGEX = Regex(
            """\b(?:глава|chapter)\s+(\d{1,6})(?:\b|[.:—-])""",
            RegexOption.IGNORE_CASE
        )

        val SERVICE_HINTS = listOf(
            "cover",
            "titlepage",
            "/title.",
            "translator",
            "translation",
            "copyright",
            "colophon",
            "toc",
            "nav",
            "about"
        )
    }
}
