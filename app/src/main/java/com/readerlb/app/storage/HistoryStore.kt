package com.readerlb.app.storage

import android.content.Context
import com.readerlb.app.importer.ExportResult
import org.json.JSONArray
import org.json.JSONObject

data class ImportHistoryItem(
    val title: String,
    val chapters: Int,
    val slugUrl: String,
    val installedDirectly: Boolean,
    val timestamp: Long
)

class HistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("readerlb_history", Context.MODE_PRIVATE)

    fun load(): List<ImportHistoryItem> {
        val raw = prefs.getString("items", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(
                        ImportHistoryItem(
                            title = item.optString("title"),
                            chapters = item.optInt("chapters"),
                            slugUrl = item.optString("slugUrl"),
                            installedDirectly = item.optBoolean("installedDirectly"),
                            timestamp = item.optLong("timestamp")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun add(result: ExportResult) {
        val current = load().toMutableList()
        current.removeAll { it.slugUrl == result.slugUrl }
        current.add(
            0,
            ImportHistoryItem(
                title = result.title,
                chapters = result.chapterCount,
                slugUrl = result.slugUrl,
                installedDirectly = result.installedDirectly,
                timestamp = System.currentTimeMillis()
            )
        )

        val out = JSONArray()
        current.take(50).forEach { item ->
            out.put(
                JSONObject()
                    .put("title", item.title)
                    .put("chapters", item.chapters)
                    .put("slugUrl", item.slugUrl)
                    .put("installedDirectly", item.installedDirectly)
                    .put("timestamp", item.timestamp)
            )
        }
        prefs.edit().putString("items", out.toString()).apply()
    }
}
