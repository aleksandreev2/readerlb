package com.readerlb.app.storage

import android.content.Context
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import androidx.documentfile.provider.DocumentFile
import com.readerlb.app.importer.compareChapterNumbers
import org.json.JSONArray
import org.json.JSONObject
import java.io.Reader
import java.io.StringReader

data class LocalLibraryItem(
    val title: String,
    val slugUrl: String,
    val chapterCount: Int,
    val firstChapter: String,
    val lastChapter: String,
    val coverUri: Uri?,
    val writeTime: Long,
    val createdByReaderLB: Boolean
)

data class LocalLibrarySnapshot(
    val items: List<LocalLibraryItem>,
    val skippedTitles: Int
)

class RanobeLibLibraryScanner(
    private val context: Context
) {
    fun scan(
        treeUri: Uri,
        onProgress: (
            completed: Int,
            total: Int
        ) -> Unit = { _, _ -> }
    ): LocalLibrarySnapshot {
        val root = DocumentFile.fromTreeUri(
            context,
            treeUri
        ) ?: error("Нет доступа к папке RanobeLib")

        val directories = root
            .listFiles()
            .filter(DocumentFile::isDirectory)

        var skipped = 0
        val items = ArrayList<
            LocalLibraryItem
        >(directories.size)

        directories.forEachIndexed {
                index,
                directory ->
            val item = runCatching {
                readTitle(directory)
            }.getOrElse {
                skipped++
                null
            }

            if (item != null) {
                items += item
            }

            onProgress(
                index + 1,
                directories.size
            )
        }

        items.sortWith(
            compareByDescending<
                LocalLibraryItem
            > {
                it.writeTime
            }.thenBy {
                it.title.lowercase()
            }
        )

        return LocalLibrarySnapshot(
            items = items,
            skippedTitles = skipped
        )
    }

    private fun readTitle(
        directory: DocumentFile
    ): LocalLibraryItem? {
        val infoFile = directory.findFile(
            "info.json"
        ) ?: return null
        val chaptersFile = directory.findFile(
            "chapters.json"
        ) ?: return null

        val info = JSONObject(
            readText(infoFile)
        )
        val media = info.getJSONObject(
            "media"
        )
        val chapters = readChapterSummary(
            chaptersFile
        )

        val title = sequenceOf(
            media.optString("rusName"),
            media.optString("name"),
            media.optString("engName"),
            directory.name.orEmpty()
        )
            .map(String::trim)
            .firstOrNull(String::isNotBlank)
            ?: error("У тайтла нет названия")

        val slugUrl = media
            .optString("slugUrl")
            .trim()
            .ifBlank {
                directory.name.orEmpty()
            }

        val coverName = media
            .optString("imageUrl")
            .trim()
            .takeIf(String::isNotBlank)
            ?.substringAfterLast('/')

        val coverUri = coverName
            ?.let(directory::findFile)
            ?.takeIf(DocumentFile::isFile)
            ?.uri

        return LocalLibraryItem(
            title = title,
            slugUrl = slugUrl,
            chapterCount =
                chapters.chapterCount,
            firstChapter =
                chapters.firstChapter,
            lastChapter =
                chapters.lastChapter,
            coverUri = coverUri,
            writeTime =
                info.optLong(
                    "writeTime",
                    0L
                ),
            createdByReaderLB =
                chapters.createdByReaderLB
        )
    }

    private fun readChapterSummary(
        file: DocumentFile
    ): LocalChapterSummary =
        context.contentResolver
            .openInputStream(file.uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { reader ->
                readLocalChapterSummary(
                    JsonReader(reader)
                )
            }
            ?: error(
                "Не удалось прочитать " +
                    file.name
            )

    private fun readText(
        file: DocumentFile
    ): String =
        context.contentResolver
            .openInputStream(file.uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: error(
                "Не удалось прочитать " +
                    file.name
            )
}

internal data class LocalChapterSummary(
    val chapterCount: Int,
    val firstChapter: String,
    val lastChapter: String,
    val createdByReaderLB: Boolean
)

internal fun parseLocalChapterSummary(
    chaptersText: String
): LocalChapterSummary =
    JsonReader(
        StringReader(chaptersText)
    ).use(::readLocalChapterSummary)

private fun readLocalChapterSummary(
    reader: JsonReader
): LocalChapterSummary {
    val numbers = HashSet<String>()
    var first: String? = null
    var last: String? = null
    var createdByReaderLB = false

    reader.beginArray()
    while (reader.hasNext()) {
        var number = ""

        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "number" -> {
                    number =
                        readJsonString(reader)
                            .trim()
                }

                "branches" -> {
                    if (
                        readBranchesForReaderLb(
                            reader
                        )
                    ) {
                        createdByReaderLB = true
                    }
                }

                else -> reader.skipValue()
            }
        }
        reader.endObject()

        if (
            number.isNotBlank() &&
            numbers.add(number)
        ) {
            if (
                first == null ||
                compareChapterNumbers(
                    number,
                    requireNotNull(first)
                ) < 0
            ) {
                first = number
            }

            if (
                last == null ||
                compareChapterNumbers(
                    number,
                    requireNotNull(last)
                ) > 0
            ) {
                last = number
            }
        }
    }
    reader.endArray()

    require(
        numbers.isNotEmpty()
    ) {
        "Тайтл не содержит глав"
    }

    return LocalChapterSummary(
        chapterCount = numbers.size,
        firstChapter = requireNotNull(first),
        lastChapter = requireNotNull(last),
        createdByReaderLB =
            createdByReaderLB
    )
}

private fun readBranchesForReaderLb(
    reader: JsonReader
): Boolean {
    if (reader.peek() == JsonToken.NULL) {
        reader.nextNull()
        return false
    }

    var found = false
    reader.beginArray()

    while (reader.hasNext()) {
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "user" -> {
                    if (
                        readUserIsReaderLb(
                            reader
                        )
                    ) {
                        found = true
                    }
                }

                else -> reader.skipValue()
            }
        }
        reader.endObject()
    }

    reader.endArray()
    return found
}

private fun readUserIsReaderLb(
    reader: JsonReader
): Boolean {
    if (reader.peek() == JsonToken.NULL) {
        reader.nextNull()
        return false
    }

    var username = ""
    reader.beginObject()

    while (reader.hasNext()) {
        when (reader.nextName()) {
            "username" ->
                username =
                    readJsonString(reader)
                        .trim()

            else -> reader.skipValue()
        }
    }

    reader.endObject()

    return username.equals(
        "ReaderLB",
        ignoreCase = true
    )
}

private fun readJsonString(
    reader: JsonReader
): String =
    when (reader.peek()) {
        JsonToken.STRING,
        JsonToken.NUMBER ->
            reader.nextString()

        JsonToken.NULL -> {
            reader.nextNull()
            ""
        }

        else -> {
            reader.skipValue()
            ""
        }
    }

internal data class ParsedLocalLibraryMetadata(
    val title: String,
    val slugUrl: String,
    val chapterCount: Int,
    val firstChapter: String,
    val lastChapter: String,
    val coverName: String?,
    val writeTime: Long,
    val createdByReaderLB: Boolean
)

internal fun parseLocalLibraryMetadata(
    infoText: String,
    chaptersText: String,
    folderName: String
): ParsedLocalLibraryMetadata {
    val info = JSONObject(infoText)
    val media = info.getJSONObject("media")
    val chapters = JSONArray(chaptersText)

    val numbers = buildList {
        for (index in 0 until chapters.length()) {
            val chapter = chapters
                .optJSONObject(index)
                ?: continue
            val number = chapter
                .optString("number")
                .trim()
            if (number.isNotBlank()) {
                add(number)
            }
        }
    }.distinct()
        .sortedWith(::compareChapterNumbers)

    require(numbers.isNotEmpty()) {
        "Тайтл не содержит глав"
    }

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

    val coverName = media
        .optString("imageUrl")
        .trim()
        .takeIf(String::isNotBlank)
        ?.substringAfterLast('/')

    val createdByReaderLB =
        (0 until chapters.length()).any {
                index ->
            val chapter =
                chapters.optJSONObject(index)
                    ?: return@any false
            val branches =
                chapter.optJSONArray("branches")
                    ?: return@any false

            (0 until branches.length()).any {
                    branchIndex ->
                val branch =
                    branches.optJSONObject(
                        branchIndex
                    ) ?: return@any false
                val username =
                    branch
                        .optJSONObject("user")
                        ?.optString("username")
                        ?.trim()
                        .orEmpty()

                username.equals(
                    "ReaderLB",
                    ignoreCase = true
                )
            }
        }

    return ParsedLocalLibraryMetadata(
        title = title,
        slugUrl = slugUrl,
        chapterCount = numbers.size,
        firstChapter = numbers.first(),
        lastChapter = numbers.last(),
        coverName = coverName,
        writeTime =
            info.optLong(
                "writeTime",
                0L
            ),
        createdByReaderLB =
            createdByReaderLB
    )
}
