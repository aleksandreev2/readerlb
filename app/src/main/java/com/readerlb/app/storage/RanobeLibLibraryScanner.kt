package com.readerlb.app.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.readerlb.app.importer.compareChapterNumbers
import org.json.JSONArray
import org.json.JSONObject

data class LocalLibraryItem(
    val title: String,
    val slugUrl: String,
    val chapterCount: Int,
    val firstChapter: String,
    val lastChapter: String,
    val coverUri: Uri?,
    val writeTime: Long
)

data class LocalLibrarySnapshot(
    val items: List<LocalLibraryItem>,
    val skippedTitles: Int
)

class RanobeLibLibraryScanner(
    private val context: Context
) {
    fun scan(
        treeUri: Uri
    ): LocalLibrarySnapshot {
        val root = DocumentFile.fromTreeUri(
            context,
            treeUri
        ) ?: error("Нет доступа к папке RanobeLib")

        var skipped = 0
        val items = root
            .listFiles()
            .asSequence()
            .filter(DocumentFile::isDirectory)
            .mapNotNull { directory ->
                val result = runCatching {
                    readTitle(directory)
                }

                result.getOrElse {
                    skipped++
                    null
                }
            }
            .sortedWith(
                compareByDescending<LocalLibraryItem> {
                    it.writeTime
                }.thenBy {
                    it.title.lowercase()
                }
            )
            .toList()

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

        val infoText = readText(infoFile)
        val chaptersText = readText(chaptersFile)
        val parsed = parseLocalLibraryMetadata(
            infoText = infoText,
            chaptersText = chaptersText,
            folderName = directory.name.orEmpty()
        )

        val coverName = parsed.coverName
        val coverUri = coverName
            ?.let(directory::findFile)
            ?.takeIf(DocumentFile::isFile)
            ?.uri

        return LocalLibraryItem(
            title = parsed.title,
            slugUrl = parsed.slugUrl,
            chapterCount = parsed.chapterCount,
            firstChapter = parsed.firstChapter,
            lastChapter = parsed.lastChapter,
            coverUri = coverUri,
            writeTime = parsed.writeTime
        )
    }

    private fun readText(
        file: DocumentFile
    ): String =
        context.contentResolver
            .openInputStream(file.uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { it.readText() }
            ?: error(
                "Не удалось прочитать ${file.name}"
            )
}

internal data class ParsedLocalLibraryMetadata(
    val title: String,
    val slugUrl: String,
    val chapterCount: Int,
    val firstChapter: String,
    val lastChapter: String,
    val coverName: String?,
    val writeTime: Long
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
            val chapter = chapters.optJSONObject(index)
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

    return ParsedLocalLibraryMetadata(
        title = title,
        slugUrl = slugUrl,
        chapterCount = numbers.size,
        firstChapter = numbers.first(),
        lastChapter = numbers.last(),
        coverName = coverName,
        writeTime = info.optLong("writeTime", 0L)
    )
}
