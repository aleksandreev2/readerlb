package com.readerlb.app.importer

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RanobeLibUpdatePlannerTest {

    private val planner = RanobeLibUpdatePlanner()

    @Test
    fun healingStoreRegressionUpdates600ChapterCopyTo1122WithoutDuplicates() {
        val existing = chapters(1..600)
        val incoming = chapters(1..1122, idOffset = 50_000)

        val plan = planner.plan(existing, incoming)

        assertEquals(522, plan.addedCount)
        assertEquals("601", plan.addedNumbers.first())
        assertEquals("1122", plan.addedNumbers.last())
        assertEquals(600, plan.overlappingNumbers.size)
        assertEquals(1122, plan.mergedNumbers.size)
        assertEquals(
            (1..1122).map(Int::toString),
            plan.mergedNumbers
        )
    }

    @Test
    fun addsOnlyNewTailChapters() {
        val existing = chapters(1..600)
        val incoming = chapters(1..653, idOffset = 10_000)

        val plan = planner.plan(existing, incoming)

        assertEquals(53, plan.addedCount)
        assertEquals("601", plan.addedNumbers.first())
        assertEquals("653", plan.addedNumbers.last())
        assertEquals(600, plan.overlappingNumbers.size)
        assertEquals(653, plan.mergedNumbers.size)
        assertEquals("1", plan.mergedNumbers.first())
        assertEquals("653", plan.mergedNumbers.last())
    }

    @Test
    fun partialIncomingRangeAddsOnlyMissingTail() {
        val existing = chapters(1..653)
        val incoming = chapters(431..700, idOffset = 20_000)

        val plan = planner.plan(existing, incoming)

        assertEquals(47, plan.addedCount)
        assertEquals("654", plan.addedNumbers.first())
        assertEquals("700", plan.addedNumbers.last())
        assertEquals(223, plan.overlappingNumbers.size)
        assertEquals(700, plan.mergedNumbers.size)
    }

    @Test
    fun overlappingIncomingChapterNeverReplacesExistingIdentity() {
        val existing = JSONArray().put(
            chapter(
                number = "500",
                id = 500L,
                name = "Старый перевод",
                branchId = 77
            )
        )
        val incoming = JSONArray().put(
            chapter(
                number = "500",
                id = 999_500L,
                name = "Новый перевод",
                branchId = 999
            )
        )

        val merged = planner.mergeChapters(existing, incoming)
        val kept = merged.getJSONObject(0)

        assertEquals(500L, kept.getLong("id"))
        assertEquals("Старый перевод", kept.getString("name"))
        assertEquals(
            77,
            kept.getJSONArray("branches")
                .getJSONObject(0)
                .getInt("branchId")
        )
    }

    @Test
    fun incomingContainingOnlyExistingChaptersIsNoOp() {
        val existing = chapters(1..100)
        val incoming = chapters(20..80, idOffset = 30_000)

        val plan = planner.plan(existing, incoming)

        assertTrue(plan.isNoOp)
        assertTrue(plan.addedNumbers.isEmpty())
        assertEquals(61, plan.overlappingNumbers.size)
        assertEquals(100, plan.mergedNumbers.size)
    }

    @Test
    fun preservesRawDecimalAndZeroPaddedNumbers() {
        val existing = JSONArray()
            .put(chapter("0", 1))
            .put(chapter("001", 2))
            .put(chapter("2.91", 3))

        val incoming = JSONArray()
            .put(chapter("0.5", 101))
            .put(chapter("1", 102))
            .put(chapter("2.91", 999))

        val plan = planner.plan(existing, incoming)
        val merged = planner.mergeChapters(existing, incoming)

        assertEquals(
            listOf("0", "0.5", "001", "1", "2.91"),
            plan.mergedNumbers
        )
        assertEquals(
            listOf("0", "0.5", "001", "1", "2.91"),
            numbers(merged)
        )
        assertEquals(
            3L,
            merged.getJSONObject(4).getLong("id")
        )
    }

    @Test
    fun mergeInfoPreservesExistingIdentityAndOnlyUpdatesCountAndWriteTime() {
        val existing = JSONObject()
            .put(
                "media",
                JSONObject()
                    .put("id", 123456)
                    .put("name", "Существующий тайтл")
                    .put("slug", "old-slug")
                    .put("slugUrl", "123456--old-slug")
                    .put(
                        "imageUrl",
                        "file:///storage/emulated/0/existing-cover.jpg"
                    )
                    .put("uploadedCount", 600)
            )
            .put("writeTime", 1L)
            .put("version", 1)

        val merged = planner.mergeInfo(
            existingInfo = existing,
            mergedChapterCount = 653,
            writeTime = 777L
        )

        val media = merged.getJSONObject("media")
        assertEquals(123456, media.getInt("id"))
        assertEquals("Существующий тайтл", media.getString("name"))
        assertEquals("old-slug", media.getString("slug"))
        assertEquals(
            "123456--old-slug",
            media.getString("slugUrl")
        )
        assertEquals(
            "file:///storage/emulated/0/existing-cover.jpg",
            media.getString("imageUrl")
        )
        assertEquals(653, media.getInt("uploadedCount"))
        assertEquals(777L, merged.getLong("writeTime"))

        // The caller's object must not be mutated while planning/staging.
        assertEquals(
            600,
            existing.getJSONObject("media").getInt("uploadedCount")
        )
        assertEquals(1L, existing.getLong("writeTime"))
    }

    @Test
    fun derivesExactZipNamesFromExistingChapterIds() {
        val chapters = JSONArray()
            .put(chapter("0.5", 111))
            .put(chapter("431", 222))

        assertEquals(
            mapOf(
                "0.5" to "v1-n0.5-111.zip",
                "431" to "v1-n431-222.zip"
            ),
            planner.chapterNumberToZip(chapters)
        )
    }

    @Test
    fun duplicateExistingChapterNumberIsRejected() {
        val existing = JSONArray()
            .put(chapter("10", 1))
            .put(chapter("10", 2))

        try {
            planner.plan(
                existingChapters = existing,
                incomingChapters = chapters(11..12)
            )
            fail("Expected duplicate validation to fail")
        } catch (error: IllegalArgumentException) {
            assertTrue(
                error.message.orEmpty().contains("повторяется глава 10")
            )
        }
    }

    @Test
    fun duplicateIncomingChapterNumberIsRejected() {
        val incoming = JSONArray()
            .put(chapter("10", 1))
            .put(chapter("10", 2))

        try {
            planner.plan(
                existingChapters = chapters(1..9),
                incomingChapters = incoming
            )
            fail("Expected duplicate validation to fail")
        } catch (error: IllegalArgumentException) {
            assertTrue(
                error.message.orEmpty().contains("повторяется глава 10")
            )
        }
    }

    @Test
    fun plansThreeThousandChapterUpdateWithoutRenumberingExistingRange() {
        val existing = chapters(1..3000)
        val incoming = chapters(2501..3050, idOffset = 100_000)

        val plan = planner.plan(existing, incoming)

        assertEquals(50, plan.addedCount)
        assertEquals("3001", plan.addedNumbers.first())
        assertEquals("3050", plan.addedNumbers.last())
        assertEquals(3050, plan.mergedNumbers.size)
        assertEquals("1", plan.mergedNumbers.first())
        assertEquals("3050", plan.mergedNumbers.last())
    }

    @Test
    fun rejectsIdCollisionAcrossDifferentChapterNumbers() {
        val existing = JSONArray()
            .put(chapter("1", 101))
            .put(chapter("2", 202))

        val incoming = JSONArray()
            .put(chapter("3", 202))

        try {
            planner.mergeChapters(
                existingChapters = existing,
                incomingChapters = incoming
            )
            fail("Expected merged ID collision to fail")
        } catch (error: IllegalArgumentException) {
            assertTrue(
                error.message.orEmpty()
                    .contains("повторяется id 202")
            )
        }
    }

    @Test
    fun itemNumbersAreRebuiltWithoutChangingChapterIds() {
        val existing = JSONArray()
            .put(chapter("1", 101).put("itemNumber", 99))
            .put(chapter("3", 103).put("itemNumber", 100))

        val incoming = JSONArray()
            .put(chapter("2", 202).put("itemNumber", 55))

        val merged = planner.mergeChapters(existing, incoming)

        assertEquals(listOf("1", "2", "3"), numbers(merged))
        assertEquals(
            listOf(101L, 202L, 103L),
            (0 until merged.length()).map {
                merged.getJSONObject(it).getLong("id")
            }
        )
        assertEquals(
            listOf(1, 2, 3),
            (0 until merged.length()).map {
                merged.getJSONObject(it).getInt("itemNumber")
            }
        )
    }

    private fun chapters(
        range: IntRange,
        idOffset: Int = 0
    ): JSONArray =
        JSONArray().apply {
            range.forEach { number ->
                put(
                    chapter(
                        number = number.toString(),
                        id = (idOffset + number).toLong()
                    )
                )
            }
        }

    private fun chapter(
        number: String,
        id: Long,
        name: String = "Глава $number",
        branchId: Int = -1
    ): JSONObject =
        JSONObject()
            .put("id", id)
            .put("volume", "1")
            .put("number", number)
            .put("name", name)
            .put("itemNumber", 1)
            .put(
                "branches",
                JSONArray().put(
                    JSONObject()
                        .put("id", id)
                        .put("branchId", branchId)
                )
            )
            .put("withBranches", false)
            .put("totalBranchesSize", 1)

    private fun numbers(
        array: JSONArray
    ): List<String> =
        (0 until array.length()).map {
            array.getJSONObject(it).getString("number")
        }
}
