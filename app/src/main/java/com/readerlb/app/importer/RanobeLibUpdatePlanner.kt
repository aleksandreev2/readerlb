package com.readerlb.app.importer

import org.json.JSONArray
import org.json.JSONObject

data class RanobeLibUpdatePlan(
    val existingNumbers: List<String>,
    val incomingNumbers: List<String>,
    val addedNumbers: List<String>,
    val overlappingNumbers: List<String>,
    val mergedNumbers: List<String>
) {
    val addedCount: Int get() = addedNumbers.size
    val isNoOp: Boolean get() = addedNumbers.isEmpty()
}

/**
 * Pure merge logic for ReaderLB 0.3.
 *
 * Existing chapters always win on an exact chapter-number collision. This is
 * deliberate: adding a newer EPUB must not replace already-installed chapter
 * IDs, branch metadata or chapter ZIPs and therefore cannot destroy reading
 * progress tied to those IDs.
 */
class RanobeLibUpdatePlanner {

    fun plan(
        existingChapters: JSONArray,
        incomingChapters: JSONArray
    ): RanobeLibUpdatePlan {
        val existing = indexByNumber(
            array = existingChapters,
            label = "существующем chapters.json"
        )
        val incoming = indexByNumber(
            array = incomingChapters,
            label = "новом chapters.json"
        )

        val existingNumbers = sortedChapterNumbers(existing.keys)
        val incomingNumbers = sortedChapterNumbers(incoming.keys)
        val overlapping = incomingNumbers.filter(existing::containsKey)
        val added = incomingNumbers.filterNot(existing::containsKey)
        val merged = sortedChapterNumbers(existing.keys + incoming.keys)

        return RanobeLibUpdatePlan(
            existingNumbers = existingNumbers,
            incomingNumbers = incomingNumbers,
            addedNumbers = added,
            overlappingNumbers = overlapping,
            mergedNumbers = merged
        )
    }

    fun mergeChapters(
        existingChapters: JSONArray,
        incomingChapters: JSONArray
    ): JSONArray {
        val existing = indexByNumber(
            array = existingChapters,
            label = "существующем chapters.json"
        )
        val incoming = indexByNumber(
            array = incomingChapters,
            label = "новом chapters.json"
        )

        val numbers = sortedChapterNumbers(
            existing.keys + incoming.keys
        )

        val merged = JSONArray()
        numbers.forEachIndexed { index, number ->
            val source = existing[number] ?: incoming.getValue(number)
            val copy = JSONObject(source.toString())

            copy.put("itemNumber", index + 1)
            merged.put(copy)
        }

        return merged
    }

    fun mergeInfo(
        existingInfo: JSONObject,
        mergedChapterCount: Int,
        writeTime: Long = System.currentTimeMillis()
    ): JSONObject {
        require(mergedChapterCount >= 0) {
            "Количество глав не может быть отрицательным"
        }

        val copy = JSONObject(existingInfo.toString())
        val media = copy.optJSONObject("media")
            ?: error("Существующий info.json не содержит media")

        media.put("uploadedCount", mergedChapterCount)
        copy.put("writeTime", writeTime)
        return copy
    }

    fun chapterNumberToZip(
        chapters: JSONArray
    ): Map<String, String> {
        val indexed = indexByNumber(
            array = chapters,
            label = "chapters.json"
        )

        return indexed.mapValues { (_, chapter) ->
            val number = chapter.getString("number")
            val id = chapter.optLong("id", Long.MIN_VALUE)
            require(id != Long.MIN_VALUE) {
                "У главы $number отсутствует id"
            }
            "v1-n$number-$id.zip"
        }
    }

    private fun indexByNumber(
        array: JSONArray,
        label: String
    ): LinkedHashMap<String, JSONObject> {
        val result = linkedMapOf<String, JSONObject>()

        for (index in 0 until array.length()) {
            val chapter = array.optJSONObject(index)
                ?: error("Элемент #${index + 1} в $label не является объектом")

            val number = chapter.optString("number").trim()
            require(number.isNotBlank()) {
                "У элемента #${index + 1} в $label отсутствует номер"
            }
            require(chapterNumberDecimal(number) != null) {
                "Некорректный номер главы $number в $label"
            }
            require(result.put(number, chapter) == null) {
                "В $label повторяется глава $number"
            }
        }

        return result
    }
}
