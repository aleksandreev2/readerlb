package com.readerlb.app.export

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal fun parseLocalExportBook(
    infoText: String,
    chaptersText: String,
    folderName: String,
    availableFileNames: Set<String>
): LocalExportBook {
    val info = JSONObject(infoText)
    val media = info.getJSONObject("media")
    val chapters = JSONArray(chaptersText)

    val title = sequenceOf(
        media.optString("rusName"),
        media.optString("name"),
        media.optString("engName"),
        folderName
    )
        .map(String::trim)
        .firstOrNull(String::isNotBlank)
        ?: error("У тайтла нет названия")

    val slugUrl = media
        .optString("slugUrl")
        .trim()
        .ifBlank { folderName }

    val author = media
        .optJSONArray("authors")
        ?.let { authors ->
            buildList {
                for (index in 0 until authors.length()) {
                    val name = authors
                        .optJSONObject(index)
                        ?.optString("name")
                        ?.trim()
                        .orEmpty()
                    if (name.isNotBlank()) {
                        add(name)
                    }
                }
            }
                .distinct()
                .joinToString(", ")
        }
        .orEmpty()

    val description = media
        .optString("summary")
        .replace(
            Regex("<[^>]+>"),
            " "
        )
        .replace(
            Regex("\\s+"),
            " "
        )
        .trim()

    val languageLabel = media
        .optJSONObject("type")
        ?.optString("title")
        ?.trim()
        .orEmpty()

    val coverName = media
        .optString("imageUrl")
        .trim()
        .takeIf(String::isNotBlank)
        ?.substringAfterLast('/')
        ?.takeIf(availableFileNames::contains)

    val seenNumbers =
        HashSet<String>()

    val chapterRefs = buildList {
        for (index in 0 until chapters.length()) {
            val chapter =
                chapters.optJSONObject(index)
                    ?: continue
            val number = chapter
                .optString("number")
                .trim()
            if (
                number.isBlank() ||
                !seenNumbers.add(number)
            ) {
                continue
            }

            val titleValue = chapter
                .optString("name")
                .trim()
            val volume = chapter
                .optString("volume")
                .trim()
                .ifBlank { "1" }
            val chapterId =
                chapter
                    .optLong(
                        "id",
                        Long.MIN_VALUE
                    )
                    .takeIf {
                        it != Long.MIN_VALUE
                    }

            val branchIds =
                chapter
                    .optJSONArray(
                        "branches"
                    )
                    ?.let { branches ->
                        buildList {
                            for (
                                branchIndex
                                in 0 until branches.length()
                            ) {
                                val id = branches
                                    .optJSONObject(
                                        branchIndex
                                    )
                                    ?.optLong(
                                        "id",
                                        Long.MIN_VALUE
                                    )
                                    ?: Long.MIN_VALUE
                                if (
                                    id !=
                                    Long.MIN_VALUE
                                ) {
                                    add(id)
                                }
                            }
                        }
                    }
                    .orEmpty()

            val archiveName =
                resolveChapterArchiveName(
                    volume = volume,
                    number = number,
                    chapterId = chapterId,
                    branchIds = branchIds,
                    availableFileNames =
                        availableFileNames
                )

            add(
                LocalExportChapterRef(
                    number = number,
                    title = titleValue,
                    volume = volume,
                    chapterId = chapterId,
                    archiveName =
                        archiveName
                )
            )
        }
    }

    require(
        chapterRefs.isNotEmpty()
    ) {
        "Тайтл не содержит глав"
    }

    return LocalExportBook(
        title = title,
        author = author,
        description = description,
        languageLabel = languageLabel,
        slugUrl = slugUrl,
        coverName = coverName,
        chapters = chapterRefs
    )
}

internal fun resolveChapterArchiveName(
    volume: String,
    number: String,
    chapterId: Long?,
    branchIds: List<Long>,
    availableFileNames: Set<String>
): String? {
    val candidateIds = buildList {
        chapterId?.let(::add)
        branchIds.forEach {
            if (it !in this) {
                add(it)
            }
        }
    }

    candidateIds.forEach { id ->
        val exact =
            "v$volume-n$number-$id.zip"
        if (
            availableFileNames.contains(
                exact
            )
        ) {
            return exact
        }
    }

    val prefix =
        "v$volume-n$number-"
    val volumeMatches =
        availableFileNames
            .asSequence()
            .filter {
                it.startsWith(prefix) &&
                    it.endsWith(
                        ".zip",
                        ignoreCase = true
                    )
            }
            .toList()

    if (volumeMatches.size == 1) {
        return volumeMatches.single()
    }

    val numberMarker =
        "-n$number-"
    val numberMatches =
        availableFileNames
            .asSequence()
            .filter {
                it.contains(
                    numberMarker
                ) &&
                    it.endsWith(
                        ".zip",
                        ignoreCase = true
                    )
            }
            .toList()

    return numberMatches
        .singleOrNull()
}

internal fun parseLocalChapterDocument(
    number: String,
    title: String,
    dataText: String,
    archiveEntryNames: Set<String>
): LocalExportChapter {
    val trimmed =
        dataText.trim()

    require(
        trimmed.isNotEmpty()
    ) {
        "Локальная глава пуста"
    }

    val parsed =
        runCatching {
            JSONTokener(
                trimmed
            ).nextValue()
        }.getOrNull()

    return when (parsed) {
        is JSONObject -> {
            val contentValue =
                parsed.opt("content")

            if (
                contentValue is String
            ) {
                parseLegacyHtmlChapter(
                    number = number,
                    title = title,
                    html = contentValue,
                    archiveEntryNames =
                        archiveEntryNames
                )
            } else {
                parseJsonChapterDocument(
                    number = number,
                    title = title,
                    root = parsed,
                    archiveEntryNames =
                        archiveEntryNames
                )
            }
        }

        is String -> {
            parseLegacyHtmlChapter(
                number = number,
                title = title,
                html = parsed,
                archiveEntryNames =
                    archiveEntryNames
            )
        }

        else -> {
            if (
                looksLikeHtml(
                    trimmed
                )
            ) {
                parseLegacyHtmlChapter(
                    number = number,
                    title = title,
                    html = trimmed,
                    archiveEntryNames =
                        archiveEntryNames
                )
            } else {
                LocalExportChapter(
                    number = number,
                    title = title,
                    blocks = listOf(
                        LocalExportBlock
                            .Paragraph(
                                trimmed
                            )
                    ),
                    warnings = listOf(
                        "Глава использует неизвестный текстовый формат; содержимое сохранено как обычный текст"
                    )
                )
            }
        }
    }
}

private fun parseJsonChapterDocument(
    number: String,
    title: String,
    root: JSONObject,
    archiveEntryNames: Set<String>
): LocalExportChapter {
    val content = root
        .optJSONArray("content")
        ?: JSONArray()

    val blocks =
        mutableListOf<LocalExportBlock>()
    val warnings =
        mutableListOf<String>()

    for (
        index
        in 0 until content.length()
    ) {
        val node =
            content.optJSONObject(
                index
            )
                ?: continue
        parseBlock(
            node = node,
            archiveEntryNames =
                archiveEntryNames,
            output = blocks,
            warnings = warnings
        )
    }

    return LocalExportChapter(
        number = number,
        title = title,
        blocks = blocks,
        warnings = warnings
    )
}

private fun parseLegacyHtmlChapter(
    number: String,
    title: String,
    html: String,
    archiveEntryNames: Set<String>
): LocalExportChapter {
    val document =
        Jsoup.parseBodyFragment(
            html
        )
    val blocks =
        mutableListOf<LocalExportBlock>()
    val warnings =
        mutableListOf<String>()

    document.body()
        .childNodes()
        .forEach {
                node ->
            parseHtmlNode(
                node = node,
                archiveEntryNames =
                    archiveEntryNames,
                output = blocks,
                warnings = warnings
            )
        }

    if (
        blocks.isEmpty()
    ) {
        val fallback =
            document.body()
                .text()
                .trim()
        if (
            fallback.isNotBlank()
        ) {
            blocks +=
                LocalExportBlock
                    .Paragraph(
                        fallback
                    )
        }
    }

    return LocalExportChapter(
        number = number,
        title = title,
        blocks = blocks,
        warnings = warnings
    )
}

private fun parseHtmlNode(
    node: Node,
    archiveEntryNames: Set<String>,
    output: MutableList<LocalExportBlock>,
    warnings: MutableList<String>
) {
    when (node) {
        is TextNode -> {
            val text =
                node.text()
                    .trim()
            if (
                text.isNotBlank()
            ) {
                output +=
                    LocalExportBlock
                        .Paragraph(
                            text
                        )
            }
        }

        is Element -> {
            when (
                node.normalName()
            ) {
                "p",
                "h1",
                "h2",
                "h3",
                "h4",
                "h5",
                "h6",
                "center" -> {
                    addHtmlParagraph(
                        element = node,
                        output = output
                    )
                }

                "blockquote" -> {
                    val lines =
                        node.children()
                            .filter {
                                it.normalName() in
                                    HTML_TEXT_BLOCK_TAGS
                            }
                            .map {
                                collectHtmlText(
                                    it
                                )
                                    .trim()
                            }
                            .filter(
                                String::isNotBlank
                            )
                            .ifEmpty {
                                listOf(
                                    collectHtmlText(
                                        node
                                    )
                                        .trim()
                                )
                                    .filter(
                                        String::isNotBlank
                                    )
                            }

                    if (
                        lines.isNotEmpty()
                    ) {
                        output +=
                            LocalExportBlock
                                .Quote(
                                    lines
                                )
                    }
                }

                "hr" -> {
                    output +=
                        LocalExportBlock
                            .HorizontalRule
                }

                "img" -> {
                    parseHtmlImage(
                        element = node,
                        archiveEntryNames =
                            archiveEntryNames,
                        output = output,
                        warnings = warnings
                    )
                }

                "figure" -> {
                    val images =
                        node.select(
                            "img"
                        )
                    if (
                        images.isNotEmpty()
                    ) {
                        images.forEach {
                                image ->
                            parseHtmlImage(
                                element =
                                    image,
                                archiveEntryNames =
                                    archiveEntryNames,
                                output = output,
                                warnings =
                                    warnings
                            )
                        }
                    } else {
                        addHtmlParagraph(
                            element = node,
                            output = output
                        )
                    }
                }

                "div",
                "section",
                "article",
                "main",
                "body" -> {
                    val blockChildren =
                        node.children()
                            .any {
                                it.normalName() in
                                    HTML_BLOCK_TAGS
                            }

                    if (
                        blockChildren
                    ) {
                        node.childNodes()
                            .forEach {
                                    child ->
                                parseHtmlNode(
                                    node = child,
                                    archiveEntryNames =
                                        archiveEntryNames,
                                    output = output,
                                    warnings =
                                        warnings
                                )
                            }
                    } else {
                        addHtmlParagraph(
                            element = node,
                            output = output
                        )
                    }
                }

                "br" -> Unit

                else -> {
                    val blockChildren =
                        node.children()
                            .any {
                                it.normalName() in
                                    HTML_BLOCK_TAGS
                            }

                    if (
                        blockChildren
                    ) {
                        node.childNodes()
                            .forEach {
                                    child ->
                                parseHtmlNode(
                                    node = child,
                                    archiveEntryNames =
                                        archiveEntryNames,
                                    output = output,
                                    warnings =
                                        warnings
                                )
                            }
                    } else {
                        addHtmlParagraph(
                            element = node,
                            output = output
                        )
                    }
                }
            }
        }
    }
}

private fun addHtmlParagraph(
    element: Element,
    output: MutableList<LocalExportBlock>
) {
    val text =
        collectHtmlText(
            element
        )
            .trim()

    if (
        text.isBlank()
    ) {
        return
    }

    val centered =
        element.normalName() ==
            "center" ||
            element.attr(
                "align"
            )
                .equals(
                    "center",
                    ignoreCase = true
                ) ||
            element.attr(
                "style"
            )
                .contains(
                    "text-align",
                    ignoreCase = true
                ) &&
            element.attr(
                "style"
            )
                .contains(
                    "center",
                    ignoreCase = true
                )

    output +=
        LocalExportBlock
            .Paragraph(
                text = text,
                centered = centered
            )
}

private fun collectHtmlText(
    node: Node
): String =
    buildString {
        node.childNodes()
            .forEach {
                    child ->
                when (child) {
                    is TextNode -> {
                        append(
                            child.text()
                        )
                    }

                    is Element -> {
                        if (
                            child.normalName() ==
                            "br"
                        ) {
                            append('\n')
                        } else {
                            append(
                                collectHtmlText(
                                    child
                                )
                            )
                        }
                    }
                }
            }
    }

private fun parseHtmlImage(
    element: Element,
    archiveEntryNames: Set<String>,
    output: MutableList<LocalExportBlock>,
    warnings: MutableList<String>
) {
    val source =
        sequenceOf(
            element.attr("src"),
            element.attr("data-src"),
            element.attr("data-original"),
            element.attr("data-image")
        )
            .map(String::trim)
            .firstOrNull(
                String::isNotBlank
            )
            .orEmpty()

    if (
        source.isBlank()
    ) {
        warnings +=
            "У HTML-иллюстрации нет ссылки на файл"
        return
    }

    val sourceName =
        source
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
    val sourceStem =
        sourceName.substringBeforeLast(
            '.',
            missingDelimiterValue =
                sourceName
        )

    val entryName =
        archiveEntryNames
            .firstOrNull {
                it == sourceName
            }
            ?: archiveEntryNames
                .firstOrNull {
                    it.substringBeforeLast(
                        '.',
                        missingDelimiterValue =
                            it
                    ) == sourceStem
                }

    if (
        entryName == null
    ) {
        warnings +=
            "Не найден файл HTML-иллюстрации $sourceName"
        return
    }

    val extension =
        entryName
            .substringAfterLast(
                '.',
                ""
            )
            .lowercase()
    val description =
        sequenceOf(
            element.attr("alt"),
            element.attr("title")
        )
            .map(String::trim)
            .firstOrNull(
                String::isNotBlank
            )

    output +=
        LocalExportBlock
            .Image(
                entryName = entryName,
                extension = extension,
                description =
                    description
            )
}

private fun looksLikeHtml(
    value: String
): Boolean {
    val trimmed =
        value.trimStart()

    return trimmed.startsWith(
        "<"
    ) &&
        trimmed.contains(
            ">"
        )
}

private val HTML_TEXT_BLOCK_TAGS =
    setOf(
        "p",
        "div",
        "h1",
        "h2",
        "h3",
        "h4",
        "h5",
        "h6"
    )

private val HTML_BLOCK_TAGS =
    HTML_TEXT_BLOCK_TAGS +
        setOf(
            "blockquote",
            "hr",
            "figure",
            "img",
            "section",
            "article",
            "main",
            "center"
        )

private fun parseBlock(
    node: JSONObject,
    archiveEntryNames: Set<String>,
    output: MutableList<LocalExportBlock>,
    warnings: MutableList<String>
) {
    val type = node
        .optString("type")
        .trim()

    when (type) {
        "paragraph",
        "heading" -> {
            val text =
                collectNodeText(node)
                    .trimEnd()
            if (
                text.isNotBlank()
            ) {
                val centered =
                    node
                        .optJSONObject(
                            "attrs"
                        )
                        ?.optString(
                            "textAlign"
                        )
                        ?.equals(
                            "center",
                            ignoreCase =
                                true
                        ) == true
                output +=
                    LocalExportBlock
                        .Paragraph(
                            text = text,
                            centered =
                                centered
                        )
            }
        }

        "horizontalRule" -> {
            output +=
                LocalExportBlock
                    .HorizontalRule
        }

        "blockquote" -> {
            val quoteContent =
                node.optJSONArray(
                    "content"
                )
            val lines =
                buildList {
                    if (
                        quoteContent !=
                        null
                    ) {
                        for (
                            index
                            in 0 until quoteContent.length()
                        ) {
                            val line =
                                quoteContent
                                    .optJSONObject(
                                        index
                                    )
                                    ?.let(
                                        ::collectNodeText
                                    )
                                    ?.trim()
                                    .orEmpty()
                            if (
                                line.isNotBlank()
                            ) {
                                add(line)
                            }
                        }
                    }
                }

            if (
                lines.isNotEmpty()
            ) {
                output +=
                    LocalExportBlock
                        .Quote(lines)
            }
        }

        "image" -> {
            parseImageBlocks(
                node = node,
                archiveEntryNames =
                    archiveEntryNames,
                output = output,
                warnings = warnings
            )
        }

        else -> {
            val fallback =
                collectNodeText(node)
                    .trim()
            if (
                fallback.isNotBlank()
            ) {
                output +=
                    LocalExportBlock
                        .Paragraph(
                            fallback
                        )
            }

            if (
                type.isNotBlank()
            ) {
                warnings +=
                    "Неизвестный тип блока: $type"
            }
        }
    }
}

private fun parseImageBlocks(
    node: JSONObject,
    archiveEntryNames: Set<String>,
    output: MutableList<LocalExportBlock>,
    warnings: MutableList<String>
) {
    val attrs =
        node.optJSONObject("attrs")
    val description = attrs
        ?.optString("description")
        ?.trim()
        ?.takeIf(String::isNotBlank)
    val images =
        attrs?.optJSONArray("images")

    if (images == null) {
        warnings +=
            "У блока изображения нет списка файлов"
        return
    }

    for (index in 0 until images.length()) {
        val imageId = images
            .optJSONObject(index)
            ?.optString("image")
            ?.trim()
            .orEmpty()
        if (
            imageId.isBlank()
        ) {
            continue
        }

        val entryName =
            archiveEntryNames
                .firstOrNull { name ->
                    name.substringBeforeLast(
                        '.',
                        missingDelimiterValue =
                            name
                    ) == imageId
                }

        if (entryName == null) {
            warnings +=
                "Не найден файл иллюстрации $imageId"
            continue
        }

        val extension =
            entryName
                .substringAfterLast(
                    '.',
                    ""
                )
                .lowercase()

        output +=
            LocalExportBlock.Image(
                entryName = entryName,
                extension = extension,
                description =
                    description
            )
    }
}

private fun collectNodeText(
    node: JSONObject
): String {
    if (
        node.optString("type") ==
        "hardBreak"
    ) {
        return "\n"
    }

    val direct = node
        .optString("text")
    val content =
        node.optJSONArray("content")

    if (content == null) {
        return direct
    }

    return buildString {
        if (
            direct.isNotBlank()
        ) {
            append(direct)
        }

        for (index in 0 until content.length()) {
            val child =
                content.optJSONObject(index)
                    ?: continue
            append(
                collectNodeText(
                    child
                )
            )
        }
    }
}
