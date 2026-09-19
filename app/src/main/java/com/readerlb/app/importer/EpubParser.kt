package com.readerlb.app.importer

import android.content.Context
import android.net.Uri
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.util.zip.ZipFile

class EpubParser(private val context: Context) {

    fun parse(uri: Uri): ParsedBook {
        val temp = File.createTempFile("readerlb_", ".epub", context.cacheDir)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Не удалось открыть файл" }
            temp.outputStream().use(input::copyTo)
        }

        try {
            ZipFile(temp).use { zip ->
                val container = readText(zip, "META-INF/container.xml")
                val containerDoc = Jsoup.parse(container, "", Parser.xmlParser())
                val opfPath = elementsByLocalName(containerDoc, "rootfile")
                    .firstOrNull()?.attr("full-path")
                    ?.takeIf { it.isNotBlank() }
                    ?: error("В EPUB не найден OPF")

                val opfDoc = Jsoup.parse(readText(zip, opfPath), "", Parser.xmlParser())
                val opfBase = opfPath.substringBeforeLast('/', "")

                val title = metaText(opfDoc, "title").ifBlank { "Локальная новелла" }
                val author = metaText(opfDoc, "creator")
                val description = metaText(opfDoc, "description")
                val language = metaText(opfDoc, "language")

                val manifest = elementsByLocalName(opfDoc, "item").associate { item ->
                    item.attr("id") to ManifestItem(
                        id = item.attr("id"),
                        href = item.attr("href"),
                        mediaType = item.attr("media-type"),
                        properties = item.attr("properties")
                    )
                }

                val spine = elementsByLocalName(opfDoc, "itemref")
                    .map { it.attr("idref") }
                    .filter { it.isNotBlank() }

                val coverItem = findCover(opfDoc, manifest)
                val coverBytes = coverItem?.let { item ->
                    runCatching { readBytes(zip, resolve(opfBase, item.href)) }.getOrNull()
                }
                val coverExt = coverItem?.href?.substringAfterLast('.', "jpg")
                    ?.lowercase()
                    ?.takeIf { it in setOf("jpg", "jpeg", "png", "webp") }
                    ?: "jpg"

                val spineItems = spine.mapNotNull(manifest::get)
                    .filter { it.mediaType.contains("html", ignoreCase = true) }

                val explicitlyNumbered = spineItems.mapNotNull { item ->
                    Regex("""(?:^|/)ch(?:apter)?[-_ ]?(\d{1,6})\.(?:xhtml|html?)$""", RegexOption.IGNORE_CASE)
                        .find(item.href)?.groupValues?.get(1)?.toIntOrNull()?.let { it to item }
                }

                val candidates = if (explicitlyNumbered.size >= 2) {
                    explicitlyNumbered.sortedBy { it.first }.map { it.second }
                } else {
                    spineItems.filterNot { item ->
                        val hint = (item.id + " " + item.href + " " + item.properties).lowercase()
                        hint.contains("nav") || hint.contains("cover") || hint.contains("titlepage")
                    }
                }

                val chapters = buildList {
                    candidates.forEach { item ->
                        val raw = runCatching { readText(zip, resolve(opfBase, item.href)) }.getOrNull()
                            ?: return@forEach
                        val doc = Jsoup.parse(raw, "", Parser.xmlParser())
                        val body = doc.body()
                        val plain = body.text().trim()
                        if (plain.length < 20) return@forEach

                        val provisionalNumber = size + 1
                        val heading = body.selectFirst("h1, h2, h3")?.text()?.trim().orEmpty()
                        val cleanedTitle = cleanChapterTitle(heading, provisionalNumber)
                        val blocks = extractBlocks(body, heading)
                            .ifEmpty { listOf(ReaderBlock.Paragraph(plain)) }

                        add(
                            ParsedChapter(
                                number = provisionalNumber,
                                title = cleanedTitle,
                                blocks = blocks
                            )
                        )
                    }
                }

                require(chapters.isNotEmpty()) { "В EPUB не удалось найти главы" }

                return ParsedBook(
                    title = title,
                    author = author,
                    description = description,
                    language = language,
                    chapters = chapters,
                    coverBytes = coverBytes,
                    coverExtension = coverExt
                )
            }
        } finally {
            temp.delete()
        }
    }

    private fun extractBlocks(root: Element, firstHeading: String): List<ReaderBlock> {
        val out = mutableListOf<ReaderBlock>()

        fun walk(element: Element) {
            element.children().forEach { child ->
                when (child.tagName().lowercase()) {
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
                        val lines = child.select("p").map { it.text().trim() }.filter(String::isNotBlank)
                        if (lines.isNotEmpty()) out += ReaderBlock.Quote(lines)
                    }
                    "div" -> {
                        val cls = child.classNames().map(String::lowercase)
                        if (cls.any { it.contains("system") || it.contains("quote") }) {
                            val lines = child.select("p").map { it.text().trim() }.filter(String::isNotBlank)
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
                Regex("""^(?:глава|chapter)\s*\d+\s*[-—.:]?\s*""", RegexOption.IGNORE_CASE),
                ""
            )
            .trim()
            .takeIf { it.isNotBlank() }
            ?: "Глава $number"
    }

    private fun findCover(doc: Document, manifest: Map<String, ManifestItem>): ManifestItem? {
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
            tag == wanted || tag.endsWith(":$wanted")
        }
    }

    private fun metaText(doc: Document, localName: String): String {
        return doc.getAllElements()
            .firstOrNull {
                val tag = it.tagName().lowercase()
                tag == localName || tag.endsWith(":$localName")
            }
            ?.text()
            ?.trim()
            .orEmpty()
    }

    private fun resolve(base: String, href: String): String {
        val decoded = Uri.decode(href).substringBefore('#')
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

    private fun readText(zip: ZipFile, path: String): String =
        readBytes(zip, path).toString(Charsets.UTF_8)

    private fun readBytes(zip: ZipFile, path: String): ByteArray {
        val entry = zip.getEntry(path) ?: error("В EPUB отсутствует $path")
        return zip.getInputStream(entry).use { it.readBytes() }
    }

    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: String
    )
}
