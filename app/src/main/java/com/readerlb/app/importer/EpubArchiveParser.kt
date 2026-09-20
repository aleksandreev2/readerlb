package com.readerlb.app.importer

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.math.BigDecimal
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.ZipFile

/**
 * Pure EPUB parser.
 *
 * No Android dependencies are allowed here: exactly the same parser used by
 * the app is exercised by JVM regression and corpus tests.
 */
class EpubArchiveParser {

    fun parse(
        file: File,
        sourceName: String? = null
    ): ParsedBook {
        ZipFile(file).use { zip ->
            val issues = mutableListOf<ImportIssue>()

            val container = readText(
                zip,
                "META-INF/container.xml"
            )
            val containerDoc = Jsoup.parse(
                container,
                "",
                Parser.xmlParser()
            )
            val opfPath = elementsByLocalName(
                containerDoc,
                "rootfile"
            )
                .firstOrNull()
                ?.attr("full-path")
                ?.takeIf(String::isNotBlank)
                ?: error("В EPUB не найден OPF")

            val opfDoc = Jsoup.parse(
                readText(zip, opfPath),
                "",
                Parser.xmlParser()
            )
            val opfBase = opfPath.substringBeforeLast('/', "")

            val title = metaText(opfDoc, "title")
                .ifBlank { "Локальная новелла" }
            val author = metaText(opfDoc, "creator")
            val description = metaText(opfDoc, "description")
            val language = metaText(opfDoc, "language")

            val manifest = elementsByLocalName(opfDoc, "item")
                .mapNotNull { item ->
                    val id = item.attr("id")
                    val href = item.attr("href")
                    if (id.isBlank() || href.isBlank()) {
                        null
                    } else {
                        id to ManifestItem(
                            id = id,
                            href = href,
                            mediaType = item.attr("media-type"),
                            properties = item.attr("properties")
                        )
                    }
                }
                .toMap()

            val spineIds = elementsByLocalName(
                opfDoc,
                "itemref"
            )
                .map { it.attr("idref") }
                .filter(String::isNotBlank)

            val missingSpineRefs = spineIds
                .filterNot(manifest::containsKey)
            if (missingSpineRefs.isNotEmpty()) {
                issues += ImportIssue(
                    code = "MISSING_SPINE_REFS",
                    message = "В EPUB есть ссылки на отсутствующие элементы: " +
                        missingSpineRefs.take(8).joinToString() +
                        if (missingSpineRefs.size > 8) "…" else ""
                )
            }

            val coverItem = findCover(
                opfDoc,
                manifest
            )
            val coverBytes = coverItem?.let { item ->
                runCatching {
                    readBytes(
                        zip,
                        resolve(opfBase, item.href)
                    )
                }.getOrNull()
            }
            val coverExtension = coverItem
                ?.href
                ?.substringAfterLast('.', "jpg")
                ?.lowercase()
                ?.takeIf {
                    it in setOf(
                        "jpg",
                        "jpeg",
                        "png",
                        "webp"
                    )
                }
                ?: "jpg"

            val tocByPath = readNavigation(
                zip = zip,
                opfBase = opfBase,
                manifest = manifest,
                issues = issues
            )

            val docs = spineIds
                .mapNotNull(manifest::get)
                .filter {
                    it.mediaType.contains(
                        "html",
                        ignoreCase = true
                    )
                }
                .mapIndexedNotNull { spineIndex, item ->
                    val contentPath = resolve(
                        opfBase,
                        item.href
                    )
                    parseHtmlDocument(
                        zip = zip,
                        contentPath = contentPath,
                        item = item,
                        spineIndex = spineIndex,
                        tocLabel = tocByPath[contentPath],
                        issues = issues
                    )
                }

            val emptyDocuments = docs.filter {
                !it.serviceDocument &&
                    it.plainText.isBlank() &&
                    it.inlineImageCount == 0
            }
            if (emptyDocuments.isNotEmpty()) {
                issues += ImportIssue(
                    code = "EMPTY_CONTENT_DOCUMENTS",
                    message = "В EPUB найдено пустых файлов содержимого: " +
                        emptyDocuments.size + "."
                )
            }

            val tocOverrides = docs.count { doc ->
                val toc = doc.tocChapterNumber
                val href = doc.hrefChapterNumber
                toc != null &&
                    href != null &&
                    !chapterNumbersEquivalent(toc, href)
            }
            if (tocOverrides > 0) {
                issues += ImportIssue(
                    code = "TOC_NUMBERING_USED",
                    message = "Для $tocOverrides разделов номера из оглавления " +
                        "отличаются от технических имён файлов. " +
                        "ReaderLB использовал оглавление.",
                    severity = ImportIssueSeverity.INFO
                )
            }

            val numberDisagreements = docs.filter { doc ->
                val preferred = doc.tocChapterNumber
                    ?: doc.textChapterNumber
                    ?: doc.hrefChapterNumber
                    ?: return@filter false

                listOfNotNull(
                    doc.tocChapterNumber,
                    doc.textChapterNumber,
                    doc.hrefChapterNumber
                ).any {
                    !chapterNumbersEquivalent(
                        preferred,
                        it
                    )
                }
            }
            if (numberDisagreements.isNotEmpty()) {
                issues += ImportIssue(
                    code = "NUMBER_MISMATCH",
                    message = "В ${numberDisagreements.size} главах источники нумерации " +
                        "не совпадают. ReaderLB использовал приоритет: " +
                        "оглавление → номер в тексте → техническое имя файла."
                )
            }

            val numbered = docs
                .filterNot { it.serviceDocument }
                .mapNotNull { doc ->
                    val number = doc.tocChapterNumber
                        ?: doc.textChapterNumber
                        ?: doc.hrefChapterNumber
                    number?.let(doc::toCandidate)
                }

            val candidates: List<ChapterCandidate>
            if (numbered.isNotEmpty()) {
                val omittedUnnumbered = docs.filter {
                    !it.serviceDocument &&
                        it.tocChapterNumber == null &&
                        it.hrefChapterNumber == null &&
                        it.textChapterNumber == null &&
                        it.plainText.length >= MIN_CHAPTER_TEXT
                }

                if (omittedUnnumbered.isNotEmpty()) {
                    issues += ImportIssue(
                        code = "UNNUMBERED_CONTENT_OMITTED",
                        message = "В EPUB есть содержательных разделов " +
                            "без номера главы: ${omittedUnnumbered.size}. " +
                            "ReaderLB не присвоил им номера автоматически."
                    )
                }
                candidates = numbered
            } else {
                val fallback = docs.filter {
                    !it.serviceDocument &&
                        it.plainText.length >= MIN_CHAPTER_TEXT
                }
                require(fallback.isNotEmpty()) {
                    "В EPUB не удалось найти главы"
                }

                val declaredRange = sourceRangeFromName(sourceName)
                if (
                    declaredRange != null &&
                    declaredRange.count() == fallback.size
                ) {
                    issues += ImportIssue(
                        code = "NUMBERING_FROM_FILENAME",
                        message = "Номера глав не найдены внутри EPUB. " +
                            "ReaderLB восстановил диапазон " +
                            "${declaredRange.first}–${declaredRange.last} " +
                            "по имени файла.",
                        severity = ImportIssueSeverity.INFO
                    )
                    candidates = fallback.mapIndexed { index, doc ->
                        doc.toCandidate(
                            (declaredRange.first + index)
                                .toString()
                        )
                    }
                } else {
                    issues += ImportIssue(
                        code = "NUMBERING_INFERRED",
                        message = "Надёжные номера глав не найдены. " +
                            "ReaderLB временно использовал порядок файлов; " +
                            "проверьте диапазон перед импортом."
                    )
                    candidates = fallback.mapIndexed { index, doc ->
                        doc.toCandidate((index + 1).toString())
                    }
                }
            }

            val sorted = candidates.sortedWith { left, right ->
                val numberCompare = compareChapterNumbers(
                    left.number,
                    right.number
                )
                if (numberCompare != 0) {
                    numberCompare
                } else {
                    left.spineIndex.compareTo(right.spineIndex)
                }
            }

            val deduplicated = mutableListOf<ChapterCandidate>()
            val duplicates = mutableListOf<String>()

            sorted
                .groupBy { it.number }
                .forEach { (number, sameNumber) ->
                    deduplicated += sameNumber.first()
                    if (sameNumber.size > 1) {
                        duplicates += number
                    }
                }

            if (duplicates.isNotEmpty()) {
                issues += ImportIssue(
                    code = "DUPLICATE_CHAPTER_NUMBERS",
                    message = "В EPUB повторяются номера глав: " +
                        formatNumbers(duplicates)
                )
            }

            val numbers = deduplicated
                .map { it.number }

            findIntegerGaps(numbers)
                .takeIf(List<Int>::isNotEmpty)
                ?.let { gaps ->
                    issues += ImportIssue(
                        code = "CHAPTER_GAPS",
                        message = "В исходном EPUB отсутствуют главы: " +
                            formatIntegerRanges(gaps)
                    )
                }

            validateExpectedRange(
                sourceName = sourceName,
                actualNumbers = numbers,
                issues = issues
            )

            val chapters = deduplicated.map { candidate ->
                val source = candidate.source
                val titleSource = source.tocLabel
                    ?.takeIf(String::isNotBlank)
                    ?: source.heading

                ParsedChapter(
                    number = candidate.number,
                    title = cleanChapterTitle(
                        title = titleSource,
                        number = candidate.number
                    ),
                    blocks = source.blocks.ifEmpty {
                        listOf(
                            ReaderBlock.Paragraph(
                                source.plainText
                            )
                        )
                    }
                )
            }

            require(chapters.isNotEmpty()) {
                "В EPUB не удалось найти главы"
            }

            return ParsedBook(
                title = title,
                author = author,
                description = description,
                language = language,
                chapters = chapters,
                coverBytes = coverBytes,
                coverExtension = coverExtension,
                issues = issues.distinctBy {
                    it.code to it.message
                }
            )
        }
    }

    private fun readNavigation(
        zip: ZipFile,
        opfBase: String,
        manifest: Map<String, ManifestItem>,
        issues: MutableList<ImportIssue>
    ): Map<String, String> {
        val result = linkedMapOf<String, String>()
        val ambiguousPaths = linkedSetOf<String>()

        fun rememberLabel(path: String, label: String) {
            val existing = result[path]
            if (existing == null) {
                result[path] = label
            } else if (existing != label) {
                ambiguousPaths += path
            }
        }

        val navigationItems = manifest.values.filter { item ->
            item.mediaType.equals(
                "application/x-dtbncx+xml",
                ignoreCase = true
            ) ||
                item.properties
                    .split(' ')
                    .any {
                        it.equals(
                            "nav",
                            ignoreCase = true
                        )
                    }
        }

        navigationItems.forEach { item ->
            val navigationPath = resolve(
                opfBase,
                item.href
            )

            val raw = runCatching {
                readText(zip, navigationPath)
            }.getOrElse {
                issues += ImportIssue(
                    code = "BROKEN_NAVIGATION",
                    message = "Не удалось прочитать оглавление EPUB: " +
                        item.href
                )
                return@forEach
            }

            val doc = Jsoup.parse(
                raw,
                "",
                Parser.xmlParser()
            )
            val navigationBase = navigationPath
                .substringBeforeLast('/', "")

            if (
                item.mediaType.equals(
                    "application/x-dtbncx+xml",
                    ignoreCase = true
                )
            ) {
                elementsByLocalName(doc, "navPoint")
                    .forEach { navPoint ->
                        val content = descendantsByLocalName(
                            navPoint,
                            "content"
                        ).firstOrNull()
                        val src = content
                            ?.attr("src")
                            ?.takeIf(String::isNotBlank)
                            ?: return@forEach

                        val label = descendantsByLocalName(
                            navPoint,
                            "navLabel"
                        )
                            .firstOrNull()
                            ?.let {
                                descendantsByLocalName(
                                    it,
                                    "text"
                                ).firstOrNull()
                            }
                            ?.text()
                            ?.trim()
                            .orEmpty()

                        if (label.isNotBlank()) {
                            rememberLabel(
                                path = resolve(
                                    navigationBase,
                                    src
                                ),
                                label = label
                            )
                        }
                    }
            } else {
                val navElements = elementsByLocalName(
                    doc,
                    "nav"
                )
                val tocRoot = navElements.firstOrNull { nav ->
                    val type = sequenceOf(
                        nav.attr("epub:type"),
                        nav.attr("type"),
                        nav.id()
                    )
                        .joinToString(" ")
                        .lowercase()

                    type
                        .split(
                            Regex("""\s+""")
                        )
                        .any {
                            it == "toc" ||
                                it.endsWith(":toc")
                        }
                } ?: doc

                descendantsByLocalName(
                    tocRoot,
                    "a"
                ).forEach { anchor ->
                    val href = anchor
                        .attr("href")
                        .takeIf(String::isNotBlank)
                        ?: return@forEach

                    val label = anchor
                        .text()
                        .trim()

                    if (label.isNotBlank()) {
                        rememberLabel(
                            path = resolve(
                                navigationBase,
                                href
                            ),
                            label = label
                        )
                    }
                }
            }
        }

        if (ambiguousPaths.isNotEmpty()) {
            issues += ImportIssue(
                code = "MULTIPLE_TOC_ENTRIES_ONE_FILE",
                message = "В оглавлении несколько разделов указывают на один XHTML-файл " +
                    "для ${ambiguousPaths.size} файлов. ReaderLB 0.2 не делит один XHTML " +
                    "на несколько глав, поэтому такой EPUB требует проверки."
            )
        }

        return result
    }

    private fun parseHtmlDocument(
        zip: ZipFile,
        contentPath: String,
        item: ManifestItem,
        spineIndex: Int,
        tocLabel: String?,
        issues: MutableList<ImportIssue>
    ): HtmlDoc? {
        val raw = runCatching {
            readText(
                zip,
                contentPath
            )
        }.getOrElse {
            issues += ImportIssue(
                code = "MISSING_CONTENT_FILE",
                message = "В EPUB отсутствует файл главы: " +
                    item.href
            )
            return null
        }

        val doc = Jsoup.parse(
            raw,
            "",
            Parser.xmlParser()
        )

        // XmlTreeBuilder does not always populate Document.body()
        // for namespace-heavy XHTML.
        val body = elementsByLocalName(
            doc,
            "body"
        ).firstOrNull() ?: doc

        val plainText = body
            .text()
            .replace('\u00A0', ' ')
            .trim()

        val heading = body
            .getAllElements()
            .firstOrNull {
                it.tagName()
                    .substringAfterLast(':')
                    .lowercase() in setOf(
                    "h1",
                    "h2",
                    "h3"
                )
            }
            ?.text()
            ?.trim()
            .orEmpty()

        val hrefChapterNumber = chapterNumberFromHref(
            item.href
        )
        val textChapterNumber = chapterNumberFromText(
            plainText = plainText,
            heading = heading
        )
        val tocChapterNumber = chapterNumberFromLabel(
            tocLabel
        )
        // A number explicitly stated by navigation or chapter text
        // is stronger than service-page heuristics. A technical filename
        // number alone is not: real EPUBs can have e.g. sec1193.xhtml whose
        // TOC label is "Послесловие переводчика".
        val hasExplicitChapterNumber =
            tocChapterNumber != null ||
                textChapterNumber != null

        // Strong service-page identity from the EPUB manifest/path
        // always wins. A title page can legitimately mention text such as
        // "Глава 0 и главы 50–89"; treating that sentence as chapter 0 would
        // otherwise shadow the real chapter_0000.xhtml and drop its images.
        //
        // We only let explicit chapter numbering override weak label-based
        // service heuristics such as "справочник".
        val serviceDocument =
            isServiceDocument(item) ||
                (
                    !hasExplicitChapterNumber &&
                        isServiceLabel(
                            tocLabel,
                            heading
                        )
                    )

        val blocks = if (serviceDocument) {
            emptyList()
        } else {
            extractBlocks(
                zip = zip,
                contentPath = contentPath,
                root = body,
                firstHeading = heading,
                issues = issues
            )
        }

        return HtmlDoc(
            item = item,
            spineIndex = spineIndex,
            heading = heading,
            tocLabel = tocLabel,
            plainText = plainText,
            blocks = blocks,
            hrefChapterNumber = hrefChapterNumber,
            textChapterNumber = textChapterNumber,
            tocChapterNumber = tocChapterNumber,
            serviceDocument = serviceDocument,
            inlineImageCount = body
                .getAllElements()
                .count {
                    it.tagName()
                        .substringAfterLast(':')
                        .equals(
                            "img",
                            ignoreCase = true
                        )
                }
        )
    }

    private fun isServiceLabel(
        tocLabel: String?,
        heading: String
    ): Boolean {
        val label = sequenceOf(
            tocLabel.orEmpty(),
            heading
        )
            .joinToString(" ")
            .lowercase()

        return SERVICE_LABEL_MARKERS.any {
            marker -> label.contains(marker)
        }
    }

    private fun isServiceDocument(
        item: ManifestItem
    ): Boolean {
        val id = item.id.lowercase()
        val href = decodeHref(item.href)
            .substringBefore('#')
            .lowercase()
        val base = href
            .substringAfterLast('/')
            .substringBeforeLast('.')
        val properties = item.properties
            .split(' ')
            .map(String::trim)
            .filter(String::isNotBlank)

        return id in SERVICE_IDS ||
            base in SERVICE_IDS ||
            properties.any {
                it.equals(
                    "nav",
                    ignoreCase = true
                )
            } ||
            SERVICE_HINTS.any { hint ->
                (" " + id + " " + href + " ")
                    .contains(hint)
            }
    }

    private fun chapterNumberFromHref(
        href: String
    ): String? {
        val decoded = decodeHref(href)
            .substringBefore('#')

        return CHAPTER_FILE_REGEX
            .find(decoded)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::normalizeTechnicalNumber)
    }

    private fun chapterNumberFromText(
        plainText: String,
        heading: String
    ): String? {
        chapterNumberFromLabel(heading)
            ?.let { return it }

        return CHAPTER_WORD_REGEX
            .find(plainText.take(700))
            ?.groupValues
            ?.drop(1)
            ?.firstOrNull(String::isNotBlank)
            ?.let(::normalizeChapterNumber)
    }

    private fun chapterNumberFromLabel(
        label: String?
    ): String? {
        if (label.isNullOrBlank()) {
            return null
        }

        val cleaned = label
            .replace('\u00A0', ' ')
            .trim()

        if (
            PROLOGUE_MARKERS.any {
                cleaned.contains(
                    it,
                    ignoreCase = true
                )
            }
        ) {
            return "0"
        }

        CHAPTER_WORD_REGEX
            .find(cleaned)
            ?.groupValues
            ?.drop(1)
            ?.firstOrNull(String::isNotBlank)
            ?.let {
                return normalizeChapterNumber(it)
            }

        LEADING_NUMBER_REGEX
            .find(cleaned)
            ?.groupValues
            ?.getOrNull(1)
            ?.let {
                return normalizeChapterNumber(it)
            }

        return null
    }

    private fun normalizeTechnicalNumber(
        value: String
    ): String {
        val normalized = normalizeChapterNumber(value)
        val decimal = chapterNumberDecimal(normalized)
            ?: return normalized

        return if (
            !normalized.contains('.') &&
            !normalized.contains(',')
        ) {
            decimal
                .stripTrailingZeros()
                .toPlainString()
        } else {
            normalized
        }
    }

    private fun normalizeChapterNumber(
        value: String
    ): String =
        value
            .trim()
            .replace(',', '.')

    private fun chapterNumbersEquivalent(
        first: String,
        second: String
    ): Boolean {
        val firstDecimal = chapterNumberDecimal(first)
        val secondDecimal = chapterNumberDecimal(second)

        return if (
            firstDecimal != null &&
            secondDecimal != null
        ) {
            firstDecimal.compareTo(secondDecimal) == 0
        } else {
            first == second
        }
    }

    private fun extractBlocks(
        zip: ZipFile,
        contentPath: String,
        root: Element,
        firstHeading: String,
        issues: MutableList<ImportIssue>
    ): List<ReaderBlock> {
        val out = mutableListOf<ReaderBlock>()

        fun appendImage(element: Element) {
            readImageBlock(
                zip = zip,
                contentPath = contentPath,
                element = element,
                issues = issues
            )?.let(out::add)
        }

        fun walk(element: Element) {
            element.children().forEach { child ->
                when (
                    child.tagName()
                        .substringAfterLast(':')
                        .lowercase()
                ) {
                    "h1", "h2", "h3" -> {
                        val text = child
                            .text()
                            .trim()
                        if (
                            text.isNotBlank() &&
                            text != firstHeading
                        ) {
                            out += ReaderBlock.Paragraph(text)
                        }
                    }

                    "p" -> {
                        val images = child
                            .getAllElements()
                            .filter {
                                it !== child &&
                                    it.tagName()
                                        .substringAfterLast(':')
                                        .equals(
                                            "img",
                                            ignoreCase = true
                                        )
                            }

                        if (images.isNotEmpty()) {
                            val ownText = child
                                .ownText()
                                .replace('\u00A0', ' ')
                                .trim()
                            if (ownText.isNotBlank()) {
                                out += ReaderBlock.Paragraph(
                                    ownText
                                )
                            }
                            images.forEach(::appendImage)
                        } else {
                            val text = child
                                .wholeText()
                                .replace('\u00A0', ' ')
                                .trim()

                            if (text.isNotBlank()) {
                                val classes = child
                                    .classNames()
                                    .map(String::lowercase)

                                if (
                                    classes.any {
                                        it.contains("scene") ||
                                            it.contains("separator")
                                    }
                                ) {
                                    out += ReaderBlock.HorizontalRule
                                } else {
                                    out += ReaderBlock.Paragraph(text)
                                }
                            }
                        }
                    }

                    "img" -> {
                        appendImage(child)
                    }

                    "hr" -> {
                        out += ReaderBlock.HorizontalRule
                    }

                    "blockquote" -> {
                        val images = child
                            .select("img")
                        if (images.isNotEmpty()) {
                            walk(child)
                        } else {
                            val lines = child
                                .select("p")
                                .map {
                                    it.text().trim()
                                }
                                .filter(String::isNotBlank)

                            if (lines.isNotEmpty()) {
                                out += ReaderBlock.Quote(lines)
                            }
                        }
                    }

                    "div" -> {
                        val classes = child
                            .classNames()
                            .map(String::lowercase)

                        if (child.select("img").isNotEmpty()) {
                            walk(child)
                        } else if (
                            classes.any {
                                it.contains("system") ||
                                    it.contains("quote") ||
                                    it.contains("notice")
                            }
                        ) {
                            val lines = child
                                .select("p")
                                .map {
                                    it.text().trim()
                                }
                                .filter(String::isNotBlank)

                            if (lines.isNotEmpty()) {
                                out += ReaderBlock.Quote(lines)
                            } else {
                                walk(child)
                            }
                        } else {
                            walk(child)
                        }
                    }

                    "section", "article", "main", "figure" -> {
                        walk(child)
                    }

                    "li", "pre", "td", "th", "figcaption" -> {
                        val text = child
                            .wholeText()
                            .replace('\u00A0', ' ')
                            .trim()
                        if (text.isNotBlank()) {
                            out += ReaderBlock.Paragraph(text)
                        }
                    }

                    else -> {
                        if (child.children().isNotEmpty()) {
                            walk(child)
                        } else {
                            val tag = child
                                .tagName()
                                .substringAfterLast(':')
                                .lowercase()
                            if (tag !in NON_CONTENT_TAGS) {
                                val text = child
                                    .wholeText()
                                    .replace('\u00A0', ' ')
                                    .trim()
                                if (text.isNotBlank()) {
                                    out += ReaderBlock.Paragraph(text)
                                }
                            }
                        }
                    }
                }
            }
        }

        walk(root)
        return out
    }

    private fun readImageBlock(
        zip: ZipFile,
        contentPath: String,
        element: Element,
        issues: MutableList<ImportIssue>
    ): ReaderBlock.Image? {
        val src = sequenceOf(
            element.attr("src"),
            element.attr("xlink:href"),
            element.attr("href")
        )
            .firstOrNull(String::isNotBlank)
            ?.trim()
            .orEmpty()

        if (src.isBlank()) {
            issues += ImportIssue(
                code = "INLINE_IMAGE_MISSING_SOURCE",
                message = "В EPUB найдено изображение без src."
            )
            return null
        }

        val description = sequenceOf(
            element.attr("alt"),
            element.attr("title")
        )
            .firstOrNull(String::isNotBlank)
            ?.trim()

        if (
            src.startsWith("http://", true) ||
            src.startsWith("https://", true)
        ) {
            issues += ImportIssue(
                code = "INLINE_IMAGE_REMOTE_UNSUPPORTED",
                message = "В EPUB есть внешнее изображение $src. " +
                    "ReaderLB не скачивает сетевые иллюстрации."
            )
            return null
        }

        val bytes: ByteArray
        val extensionHint: String

        if (src.startsWith("data:image/", true)) {
            val metadata = src.substringBefore(',', "")
            val payload = src.substringAfter(',', "")
            if (
                metadata.isBlank() ||
                payload.isBlank() ||
                !metadata.contains(
                    ";base64",
                    ignoreCase = true
                )
            ) {
                issues += ImportIssue(
                    code = "INLINE_IMAGE_DATA_INVALID",
                    message = "В EPUB найдено неподдерживаемое встроенное изображение data:."
                )
                return null
            }

            extensionHint = metadata
                .substringAfter(
                    "data:image/",
                    ""
                )
                .substringBefore(';')
                .trim()

            bytes = runCatching {
                Base64.getDecoder().decode(
                    payload.filterNot(Char::isWhitespace)
                )
            }.getOrElse {
                issues += ImportIssue(
                    code = "INLINE_IMAGE_DATA_INVALID",
                    message = "Не удалось декодировать встроенную иллюстрацию EPUB."
                )
                return null
            }
        } else {
            val cleanSrc = src
                .substringBefore('?')
                .substringBefore('#')
            val base = contentPath
                .substringBeforeLast('/', "")
            val imagePath = resolve(
                base,
                cleanSrc
            )

            bytes = runCatching {
                readBytes(
                    zip,
                    imagePath
                )
            }.getOrElse {
                issues += ImportIssue(
                    code = "INLINE_IMAGE_FILE_MISSING",
                    message = "В EPUB не найден файл иллюстрации: $cleanSrc"
                )
                return null
            }

            extensionHint = imagePath
                .substringAfterLast('.', "")
        }

        if (bytes.isEmpty()) {
            issues += ImportIssue(
                code = "INLINE_IMAGE_EMPTY",
                message = "В EPUB найдена пустая иллюстрация."
            )
            return null
        }

        val extension = imageExtension(
            hint = extensionHint,
            bytes = bytes
        )

        if (extension == null) {
            issues += ImportIssue(
                code = "INLINE_IMAGE_FORMAT_UNSUPPORTED",
                message = "Формат одной из иллюстраций EPUB не поддерживается."
            )
            return null
        }

        return ReaderBlock.Image(
            bytes = bytes,
            extension = extension,
            description = description
        )
    }

    private fun imageExtension(
        hint: String,
        bytes: ByteArray
    ): String? {
        when (hint.lowercase()) {
            "jpg", "jpeg" -> return "jpg"
            "png" -> return "png"
            "webp" -> return "webp"
            "gif" -> return "gif"
        }

        if (
            bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() &&
            bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte()
        ) {
            return "jpg"
        }

        if (
            bytes.size >= 8 &&
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 'P'.code.toByte() &&
            bytes[2] == 'N'.code.toByte() &&
            bytes[3] == 'G'.code.toByte()
        ) {
            return "png"
        }

        if (
            bytes.size >= 6 &&
            bytes
                .copyOfRange(0, 3)
                .toString(Charsets.US_ASCII) == "GIF"
        ) {
            return "gif"
        }

        if (
            bytes.size >= 12 &&
            bytes
                .copyOfRange(0, 4)
                .toString(Charsets.US_ASCII) == "RIFF" &&
            bytes
                .copyOfRange(8, 12)
                .toString(Charsets.US_ASCII) == "WEBP"
        ) {
            return "webp"
        }

        return null
    }

    private fun cleanChapterTitle(
        title: String,
        number: String
    ): String {
        if (title.isBlank()) {
            return ""
        }

        val escapedNumber = Regex.escape(number)

        return title
            .replace(
                Regex(
                    """^(?:глава|chapter)\s*$escapedNumber\s*[-—.:：]?\s*""",
                    RegexOption.IGNORE_CASE
                ),
                ""
            )
            .replace(
                Regex(
                    """^$escapedNumber\s*[.、:：\-–—]\s*"""
                ),
                ""
            )
            .trim()
            .takeIf(String::isNotBlank)
            ?: title.trim()
    }

    private fun findCover(
        doc: Document,
        manifest: Map<String, ManifestItem>
    ): ManifestItem? {
        manifest.values
            .firstOrNull {
                it.properties
                    .split(' ')
                    .any { property ->
                        property == "cover-image"
                    }
            }
            ?.let { return it }

        val coverId = elementsByLocalName(
            doc,
            "meta"
        )
            .firstOrNull {
                it.attr("name")
                    .equals(
                        "cover",
                        true
                    )
            }
            ?.attr("content")

        coverId
            ?.let(manifest::get)
            ?.let { return it }

        return manifest.values
            .firstOrNull {
                it.mediaType.startsWith("image/") &&
                    (
                        it.id.contains(
                            "cover",
                            true
                        ) ||
                            it.href.contains(
                                "cover",
                                true
                            )
                        )
            }
    }

    private fun elementsByLocalName(
        doc: Document,
        localName: String
    ): List<Element> =
        doc.getAllElements()
            .filter {
                localName(it) == localName.lowercase()
            }

    private fun descendantsByLocalName(
        element: Element,
        localName: String
    ): List<Element> =
        element.getAllElements()
            .filter {
                localName(it) == localName.lowercase()
            }

    private fun localName(
        element: Element
    ): String =
        element
            .tagName()
            .lowercase()
            .substringAfterLast(':')

    private fun metaText(
        doc: Document,
        localName: String
    ): String =
        elementsByLocalName(
            doc,
            localName
        )
            .firstOrNull()
            ?.text()
            ?.trim()
            .orEmpty()

    private fun resolve(
        base: String,
        href: String
    ): String {
        val decoded = decodeHref(href)
            .substringBefore('#')

        val raw = if (base.isBlank()) {
            decoded
        } else {
            "$base/$decoded"
        }

        val parts = ArrayDeque<String>()
        raw.split('/')
            .forEach { part ->
                when (part) {
                    "", "." -> Unit
                    ".." -> {
                        if (parts.isNotEmpty()) {
                            parts.removeLast()
                        }
                    }
                    else -> {
                        parts.addLast(part)
                    }
                }
            }

        return parts.joinToString("/")
    }

    private fun decodeHref(
        value: String
    ): String =
        URLDecoder.decode(
            value.replace("+", "%2B"),
            StandardCharsets.UTF_8.name()
        )

    private fun readText(
        zip: ZipFile,
        path: String
    ): String =
        readBytes(
            zip,
            path
        ).toString(Charsets.UTF_8)

    private fun readBytes(
        zip: ZipFile,
        path: String
    ): ByteArray {
        val entry = zip.getEntry(path)
            ?: error(
                "В EPUB отсутствует $path"
            )

        return zip
            .getInputStream(entry)
            .use {
                it.readBytes()
            }
    }

    private fun sourceRangeFromName(
        sourceName: String?
    ): IntRange? {
        if (sourceName.isNullOrBlank()) {
            return null
        }

        val match = SOURCE_RANGE_REGEX
            .find(sourceName)
            ?: return null

        val start = match
            .groupValues[1]
            .toIntOrNull()
            ?: return null

        val end = match
            .groupValues[2]
            .toIntOrNull()
            ?: return null

        return if (end >= start) {
            start..end
        } else {
            null
        }
    }

    private fun validateExpectedRange(
        sourceName: String?,
        actualNumbers: List<String>,
        issues: MutableList<ImportIssue>
    ) {
        val expected = sourceRangeFromName(sourceName)
            ?: return
        if (actualNumbers.isEmpty()) {
            return
        }

        val actualDecimals = actualNumbers
            .mapNotNull(::chapterNumberDecimal)

        val actualIntegerValues = actualDecimals
            .filter {
                it.stripTrailingZeros().scale() <= 0
            }
            .map {
                it.toInt()
            }
            .toSet()

        val missing = expected
            .filterNot(actualIntegerValues::contains)

        val start = BigDecimal(expected.first)
        val end = BigDecimal(expected.last)
        val outside = actualNumbers.filter { raw ->
            val value = chapterNumberDecimal(raw)
                ?: return@filter true
            value < start || value > end
        }

        if (
            missing.isNotEmpty() ||
            outside.isNotEmpty()
        ) {
            val details = buildList {
                if (missing.isNotEmpty()) {
                    add(
                        "не найдены: " +
                            formatIntegerRanges(missing)
                    )
                }
                if (outside.isNotEmpty()) {
                    add(
                        "вне заявленного диапазона: " +
                            formatNumbers(outside)
                    )
                }
            }.joinToString("; ")

            issues += ImportIssue(
                code = "SOURCE_RANGE_MISMATCH",
                message = "Имя файла заявляет главы " +
                    "${expected.first}–${expected.last}, " +
                    "но структура EPUB не совпадает ($details)."
            )
        }
    }

    private fun findIntegerGaps(
        values: List<String>
    ): List<Int> {
        val canonicalIntegers = values
            .filter {
                CANONICAL_INTEGER_REGEX
                    .matches(it)
            }
            .mapNotNull(String::toIntOrNull)
            .distinct()
            .sorted()

        if (canonicalIntegers.size < 2) {
            return emptyList()
        }

        val first = canonicalIntegers.first()
        val last = canonicalIntegers.last()

        if (last - first > MAX_GAP_SCAN) {
            return emptyList()
        }

        val present = canonicalIntegers.toHashSet()
        return (first..last)
            .filterNot(present::contains)
    }

    private fun formatNumbers(
        numbers: List<String>
    ): String {
        val unique = numbers
            .distinct()
            .sortedWith(::compareChapterNumbers)

        return unique
            .take(20)
            .joinToString() +
            if (unique.size > 20) "…" else ""
    }

    private fun formatIntegerRanges(
        numbers: List<Int>
    ): String {
        if (numbers.isEmpty()) {
            return ""
        }

        val sorted = numbers
            .distinct()
            .sorted()
        val ranges = mutableListOf<IntRange>()

        var start = sorted.first()
        var previous = start

        sorted
            .drop(1)
            .forEach { value ->
                if (value == previous + 1) {
                    previous = value
                } else {
                    ranges += start..previous
                    start = value
                    previous = value
                }
            }

        ranges += start..previous

        val rendered = ranges
            .take(8)
            .joinToString { range ->
                if (
                    range.first == range.last
                ) {
                    range.first.toString()
                } else {
                    "${range.first}–${range.last}"
                }
            }

        return rendered +
            if (ranges.size > 8) "…" else ""
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
        val tocLabel: String?,
        val plainText: String,
        val blocks: List<ReaderBlock>,
        val hrefChapterNumber: String?,
        val textChapterNumber: String?,
        val tocChapterNumber: String?,
        val serviceDocument: Boolean,
        val inlineImageCount: Int
    ) {
        fun toCandidate(
            number: String
        ): ChapterCandidate =
            ChapterCandidate(
                number = number,
                spineIndex = spineIndex,
                source = this
            )
    }

    private data class ChapterCandidate(
        val number: String,
        val spineIndex: Int,
        val source: HtmlDoc
    )

    private companion object {
        const val MIN_CHAPTER_TEXT = 20
        const val MAX_GAP_SCAN = 10_000

        val CHAPTER_FILE_REGEX = Regex(
            """(?:^|/)(?:ch|chapter)[-_ ]?0*(\d+(?:[.,]\d+)?)\.(?:xhtml|html?)$""",
            RegexOption.IGNORE_CASE
        )

        // Jsoup's XML tree can concatenate adjacent XHTML block text
        // (for example </h1><p> -> "...Глава 2"). Russian "глава" can
        // therefore be preceded by a letter in the flattened text. Matching
        // the keyword itself is safe because whitespace after it is required.
        // English keeps a Latin-word boundary to avoid matching "subchapter".
        val CHAPTER_WORD_REGEX = Regex(
            """(?:глава\s+(\d+(?:[.,]\d+)?)(?!\d))|""" +
                """(?:(?<![A-Za-z0-9_])chapter\s+(\d+(?:[.,]\d+)?)(?!\d))|""" +
                """(?:제\s*(\d+(?:[.,]\d+)?)\s*화)|""" +
                """(?:第\s*(\d+(?:[.,]\d+)?)\s*[章話话])""",
            RegexOption.IGNORE_CASE
        )

        val LEADING_NUMBER_REGEX = Regex(
            """^\s*(\d+(?:[.,]\d+)?)\s*[.、:：\-–—]"""
        )

        val SOURCE_RANGE_REGEX = Regex(
            """(?:глав\p{L}*|chapters?)[^\d]{0,24}(\d{1,6})\s*[_–—-]\s*(\d{1,6})""",
            RegexOption.IGNORE_CASE
        )

        val CANONICAL_INTEGER_REGEX = Regex(
            """0|[1-9]\d*"""
        )

        val PROLOGUE_MARKERS = setOf(
            "пролог",
            "prologue",
            "프롤로그",
            "序章"
        )

        val SERVICE_LABEL_MARKERS = setOf(
            "сведения о переводе",
            "информация о переводе",
            "справочник",
            "глоссарий",
            "translation info",
            "translation information",
            "glossary"
        )

        val NON_CONTENT_TAGS = setOf(
            "script",
            "style",
            "link",
            "meta",
            "img",
            "svg",
            "source",
            "br"
        )

        val SERVICE_IDS = setOf(
            "cover",
            "title",
            "titlepage",
            "translator",
            "translation",
            "info",
            "fullversion",
            "copyright",
            "colophon",
            "toc",
            "nav",
            "about"
        )

        val SERVICE_HINTS = listOf(
            "/cover.",
            "/title.",
            "/titlepage.",
            "/translator.",
            "/translation.",
            "/info.",
            "/fullversion.",
            "/copyright.",
            "/colophon.",
            "/toc.",
            "/nav.",
            "/about."
        )
    }
}
