package com.readerlb.app.storage

import android.content.Context
import com.readerlb.app.importer.ExportResult
import org.json.JSONArray
import org.json.JSONObject

data class ImportHistoryItem(
    val title: String,
    val chapters: Int,
    val firstChapter: String,
    val lastChapter: String,
    val slugUrl: String,
    val installedDirectly: Boolean,
    val updatedExisting: Boolean,
    val addedChapterCount: Int,
    val timestamp: Long,
    val contentType: LocalContentType =
        LocalContentType.BOOK
)

class HistoryStore(
    context: Context
) {
    private val prefs =
        context.getSharedPreferences(
            "readerlb_history",
            Context.MODE_PRIVATE
        )

    fun load(): List<ImportHistoryItem> {
        val raw =
            prefs.getString(
                "items",
                "[]"
            ) ?: "[]"

        return runCatching {
            val array =
                JSONArray(
                    raw
                )
            buildList {
                for (
                    index in 0 until
                        array.length()
                ) {
                    val item =
                        array.getJSONObject(
                            index
                        )
                    val chapters =
                        item.optInt(
                            "chapters"
                        )
                    val first =
                        item
                            .optString(
                                "firstChapter"
                            )
                            .ifBlank {
                                if (
                                    chapters > 0
                                ) {
                                    "1"
                                } else {
                                    "0"
                                }
                            }
                    val last =
                        item
                            .optString(
                                "lastChapter"
                            )
                            .ifBlank {
                                chapters.toString()
                            }

                    add(
                        ImportHistoryItem(
                            title =
                                item.optString(
                                    "title"
                                ),
                            chapters =
                                chapters,
                            firstChapter =
                                first,
                            lastChapter =
                                last,
                            slugUrl =
                                item.optString(
                                    "slugUrl"
                                ),
                            installedDirectly =
                                item.optBoolean(
                                    "installedDirectly"
                                ),
                            updatedExisting =
                                item.optBoolean(
                                    "updatedExisting",
                                    false
                                ),
                            addedChapterCount =
                                if (
                                    item.has(
                                        "addedChapterCount"
                                    )
                                ) {
                                    item.optInt(
                                        "addedChapterCount",
                                        chapters
                                    )
                                } else {
                                    chapters
                                },
                            timestamp =
                                item.optLong(
                                    "timestamp"
                                ),
                            contentType =
                                runCatching {
                                    LocalContentType
                                        .valueOf(
                                            item.optString(
                                                "contentType",
                                                "BOOK"
                                            )
                                        )
                                }.getOrDefault(
                                    LocalContentType.BOOK
                                )
                        )
                    )
                }
            }
        }.getOrDefault(
            emptyList()
        )
    }

    fun add(
        result: ExportResult
    ) {
        val current =
            load().toMutableList()

        current.removeAll {
            it.slugUrl ==
                result.slugUrl
        }

        current.add(
            0,
            ImportHistoryItem(
                title = result.title,
                chapters =
                    result.chapterCount,
                firstChapter =
                    result.firstChapter,
                lastChapter =
                    result.lastChapter,
                slugUrl =
                    result.slugUrl,
                installedDirectly =
                    result.installedDirectly,
                updatedExisting =
                    result.updatedExisting,
                addedChapterCount =
                    result.addedChapterCount,
                timestamp =
                    System.currentTimeMillis(),
                contentType =
                    LocalContentType.BOOK
            )
        )

        save(
            current
        )
    }

    fun addManga(
        title: String,
        folderName: String,
        number: String,
        chapterCount: Int
    ) {
        val current =
            load().toMutableList()

        current.removeAll {
            it.slugUrl ==
                folderName
        }

        current.add(
            0,
            ImportHistoryItem(
                title = title,
                chapters = chapterCount,
                firstChapter = number,
                lastChapter = number,
                slugUrl = folderName,
                installedDirectly = true,
                updatedExisting =
                    chapterCount > 1,
                addedChapterCount = 1,
                timestamp =
                    System.currentTimeMillis(),
                contentType =
                    LocalContentType.MANGA
            )
        )

        save(
            current
        )
    }

    private fun save(
        items: List<ImportHistoryItem>
    ) {
        val out =
            JSONArray()

        items
            .take(50)
            .forEach {
                    item ->
                out.put(
                    JSONObject()
                        .put(
                            "title",
                            item.title
                        )
                        .put(
                            "chapters",
                            item.chapters
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
                            "slugUrl",
                            item.slugUrl
                        )
                        .put(
                            "installedDirectly",
                            item.installedDirectly
                        )
                        .put(
                            "updatedExisting",
                            item.updatedExisting
                        )
                        .put(
                            "addedChapterCount",
                            item.addedChapterCount
                        )
                        .put(
                            "timestamp",
                            item.timestamp
                        )
                        .put(
                            "contentType",
                            item.contentType.name
                        )
                )
            }

        prefs.edit()
            .putString(
                "items",
                out.toString()
            )
            .apply()
    }
}
