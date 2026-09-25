package com.readerlb.app.storage

import android.content.Context
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import com.readerlb.app.importer.compareChapterNumbers
import org.json.JSONArray
import org.json.JSONObject
import android.provider.DocumentsContract
import java.io.StringReader

data class LocalLibraryItem(
    val title: String,
    val slugUrl: String,
    val chapterCount: Int,
    val firstChapter: String,
    val lastChapter: String,
    val coverUri: Uri?,
    val writeTime: Long,
    val createdByReaderLB: Boolean,
    val folderName: String = slugUrl
)

data class LocalLibrarySnapshot(
    val items: List<LocalLibraryItem>,
    val skippedTitles: Int
)

internal fun encodeLocalLibraryCache(
    items: List<LocalLibraryItem>
): String {
    val array = JSONArray()

    items.forEach { item ->
        array.put(
            JSONObject()
                .put("title", item.title)
                .put("slugUrl", item.slugUrl)
                .put(
                    "chapterCount",
                    item.chapterCount
                )
                .put(
                    "firstChapter",
                    item.firstChapter
                )
                .put(
                    "lastChapter",
                    item.lastChapter
                )
                .put(
                    "writeTime",
                    item.writeTime
                )
                .put(
                    "createdByReaderLB",
                    item.createdByReaderLB
                )
                .put(
                    "folderName",
                    item.folderName
                )
                .apply {
                    item.coverUri?.let {
                        put(
                            "coverUri",
                            it.toString()
                        )
                    }
                }
        )
    }

    return array.toString()
}

internal fun decodeLocalLibraryCache(
    json: String?
): List<LocalLibraryItem> {
    if (json.isNullOrBlank()) {
        return emptyList()
    }

    val array = JSONArray(json)

    return buildList {
        for (index in 0 until array.length()) {
            val item = array
                .optJSONObject(index)
                ?: continue

            val title = item
                .optString("title")
                .trim()
            val slugUrl = item
                .optString("slugUrl")
                .trim()
            val chapterCount =
                item.optInt(
                    "chapterCount",
                    0
                )
            val firstChapter = item
                .optString("firstChapter")
                .trim()
            val lastChapter = item
                .optString("lastChapter")
                .trim()

            if (
                title.isBlank() ||
                slugUrl.isBlank() ||
                chapterCount <= 0 ||
                firstChapter.isBlank() ||
                lastChapter.isBlank()
            ) {
                continue
            }

            add(
                LocalLibraryItem(
                    title = title,
                    slugUrl = slugUrl,
                    chapterCount =
                        chapterCount,
                    firstChapter =
                        firstChapter,
                    lastChapter =
                        lastChapter,
                    coverUri = item
                        .optString(
                            "coverUri"
                        )
                        .trim()
                        .takeIf(
                            String::isNotBlank
                        )
                        ?.let(Uri::parse),
                    writeTime =
                        item.optLong(
                            "writeTime",
                            0L
                        ),
                    createdByReaderLB =
                        item.optBoolean(
                            "createdByReaderLB",
                            false
                        ),
                    folderName =
                        item.optString(
                            "folderName"
                        )
                            .trim()
                            .ifBlank {
                                slugUrl
                            }
                )
            )
        }
    }
}

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
        val rootDocumentId =
            runCatching {
                DocumentsContract
                    .getTreeDocumentId(
                        treeUri
                    )
            }.getOrElse {
                error(
                    "Не удалось определить папку RanobeLib"
                )
            }

        var completed = 0
        var skipped = 0
        val items =
            ArrayList<LocalLibraryItem>()

        forEachChild(
            treeUri = treeUri,
            parentDocumentId =
                rootDocumentId
        ) { child ->
            if (
                child.mimeType !=
                DocumentsContract.Document
                    .MIME_TYPE_DIR
            ) {
                return@forEachChild
            }

            val item = runCatching {
                readTitle(
                    treeUri = treeUri,
                    directory = child
                )
            }.getOrElse {
                skipped += 1
                null
            }

            if (item != null) {
                items += item
            }

            completed += 1
            onProgress(
                completed,
                0
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

        onProgress(
            completed,
            completed
        )

        return LocalLibrarySnapshot(
            items = items,
            skippedTitles = skipped
        )
    }

    private fun readTitle(
        treeUri: Uri,
        directory: SafDocument
    ): LocalLibraryItem? {
        val children =
            LinkedHashMap<String, SafDocument>()

        forEachChild(
            treeUri = treeUri,
            parentDocumentId =
                directory.documentId
        ) { child ->
            if (
                child.mimeType !=
                DocumentsContract.Document
                    .MIME_TYPE_DIR
            ) {
                children[
                    child.name
                ] = child
            }
        }

        val infoFile =
            children["info.json"]
                ?: return null
        val chaptersFile =
            children["chapters.json"]
                ?: return null

        val info = JSONObject(
            readText(
                uri = infoFile.uri,
                displayName =
                    infoFile.name
            )
        )
        val media = info.getJSONObject(
            "media"
        )
        val chapters = readChapterSummary(
            uri = chaptersFile.uri,
            displayName =
                chaptersFile.name
        )

        val title = sequenceOf(
            media.optString("rusName"),
            media.optString("name"),
            media.optString("engName"),
            directory.name
        )
            .map(String::trim)
            .firstOrNull(
                String::isNotBlank
            )
            ?: error(
                "У тайтла нет названия"
            )

        val slugUrl = media
            .optString("slugUrl")
            .trim()
            .ifBlank {
                directory.name
            }

        val coverName = media
            .optString("imageUrl")
            .trim()
            .takeIf(
                String::isNotBlank
            )
            ?.substringAfterLast('/')

        val coverUri = coverName
            ?.let(children::get)
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
                chapters.createdByReaderLB,
            folderName =
                directory.name
        )
    }

    private fun readChapterSummary(
        uri: Uri,
        displayName: String
    ): LocalChapterSummary =
        context.contentResolver
            .openInputStream(uri)
            ?.bufferedReader(
                Charsets.UTF_8
            )
            ?.use { reader ->
                readLocalChapterSummary(
                    JsonReader(reader)
                )
            }
            ?: error(
                "Не удалось прочитать " +
                    displayName
            )

    private fun readText(
        uri: Uri,
        displayName: String
    ): String =
        context.contentResolver
            .openInputStream(uri)
            ?.use {
                readBoundedInfo(it)
            }
            ?: error(
                "Не удалось прочитать " +
                    displayName
            )

    private fun forEachChild(
        treeUri: Uri,
        parentDocumentId: String,
        block: (SafDocument) -> Unit
    ) {
        val childrenUri =
            DocumentsContract
                .buildChildDocumentsUriUsingTree(
                    treeUri,
                    parentDocumentId
                )

        val projection = arrayOf(
            DocumentsContract.Document
                .COLUMN_DOCUMENT_ID,
            DocumentsContract.Document
                .COLUMN_DISPLAY_NAME,
            DocumentsContract.Document
                .COLUMN_MIME_TYPE
        )

        val cursor =
            context.contentResolver.query(
                childrenUri,
                projection,
                null,
                null,
                null
            ) ?: error(
                "Android не дал прочитать папку RanobeLib"
            )

        cursor.use {
            val idIndex =
                it.getColumnIndexOrThrow(
                    DocumentsContract.Document
                        .COLUMN_DOCUMENT_ID
                )
            val nameIndex =
                it.getColumnIndexOrThrow(
                    DocumentsContract.Document
                        .COLUMN_DISPLAY_NAME
                )
            val mimeIndex =
                it.getColumnIndexOrThrow(
                    DocumentsContract.Document
                        .COLUMN_MIME_TYPE
                )

            while (it.moveToNext()) {
                val documentId =
                    it.getString(idIndex)
                val name =
                    it.getString(nameIndex)
                        .orEmpty()
                val mimeType =
                    it.getString(mimeIndex)
                        .orEmpty()

                if (
                    documentId.isBlank()
                ) {
                    continue
                }

                block(
                    SafDocument(
                        documentId =
                            documentId,
                        name = name,
                        mimeType =
                            mimeType,
                        uri =
                            DocumentsContract
                                .buildDocumentUriUsingTree(
                                    treeUri,
                                    documentId
                                )
                    )
                )
            }
        }
    }

    private data class SafDocument(
        val documentId: String,
        val name: String,
        val mimeType: String,
        val uri: Uri
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
