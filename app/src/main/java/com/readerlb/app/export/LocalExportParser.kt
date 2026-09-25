package com.readerlb.app.export

import org.json.JSONArray
import org.json.JSONObject

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
    val root = JSONObject(dataText)
    val content = root
        .optJSONArray("content")
        ?: JSONArray()

    val blocks =
        mutableListOf<LocalExportBlock>()
    val warnings =
        mutableListOf<String>()

    for (index in 0 until content.length()) {
        val node =
            content.optJSONObject(index)
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
