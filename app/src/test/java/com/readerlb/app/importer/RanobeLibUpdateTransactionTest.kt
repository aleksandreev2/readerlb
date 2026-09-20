package com.readerlb.app.importer

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RanobeLibUpdateTransactionTest {

    private val builder = RanobeLibPackageBuilder(
        nowMillis = { 1_700_000_000_000L }
    )

    private val transaction = RanobeLibUpdateTransaction(
        nowMillis = { 1_800_000_000_000L }
    )

    @Test
    fun safelyAddsNewTailWithoutTouchingExistingChapterFiles() {
        val root = Files.createTempDirectory("readerlb_update_").toFile()
        val existingBuilt = builder.build(
            book = book("Тест обновления", 1..3),
            rootDir = File(root, "existing")
        )
        val incomingBuilt = builder.build(
            book = book("Тест обновления", 1..5),
            rootDir = File(root, "incoming")
        )

        val storage = FileRanobeLibStorage(
            existingBuilt.titleDir
        )
        val oldChapters = JSONArray(
            storage.readBytes("chapters.json")
                .toString(Charsets.UTF_8)
        )
        val oldZipByNumber = RanobeLibUpdatePlanner()
            .chapterNumberToZip(oldChapters)
        val oldZipBytes = oldZipByNumber.mapValues {
            storage.readBytes(it.value)
        }

        val result = transaction.apply(
            existing = storage,
            incomingTitleDir = incomingBuilt.titleDir
        )

        assertTrue(result.changed)
        assertEquals(listOf("4", "5"), result.addedNumbers)
        assertEquals(5, result.totalChapterCount)

        val merged = JSONArray(
            storage.readBytes("chapters.json")
                .toString(Charsets.UTF_8)
        )
        assertEquals(
            listOf("1", "2", "3", "4", "5"),
            chapterNumbers(merged)
        )

        val info = JSONObject(
            storage.readBytes("info.json")
                .toString(Charsets.UTF_8)
        )
        assertEquals(
            5,
            info.getJSONObject("media")
                .getInt("uploadedCount")
        )
        assertEquals(
            1_800_000_000_000L,
            info.getLong("writeTime")
        )

        oldZipBytes.forEach { (number, bytes) ->
            assertArrayEquals(
                "Existing chapter $number was rewritten",
                bytes,
                storage.readBytes(
                    oldZipByNumber.getValue(number)
                )
            )
        }

        assertFalse(
            storage.names().any {
                it.startsWith(".readerlb-")
            }
        )
    }

    @Test
    fun failureAtFinalInfoPublishRestoresOldMetadataAndRemovesNewZips() {
        val root = Files.createTempDirectory("readerlb_update_fail_").toFile()
        val existingBuilt = builder.build(
            book = book("Rollback test", 1..3),
            rootDir = File(root, "existing")
        )
        val incomingBuilt = builder.build(
            book = book("Rollback test", 1..5),
            rootDir = File(root, "incoming")
        )

        val baseStorage = FileRanobeLibStorage(
            existingBuilt.titleDir
        )
        val beforeInfo = baseStorage.readBytes("info.json")
        val beforeChapters = baseStorage.readBytes("chapters.json")
        val beforeNames = baseStorage.names()

        val failing = FaultingStorage(
            delegate = baseStorage,
            shouldFailRename = { from, to ->
                from == ".readerlb-info.tmp" &&
                    to == "info.json"
            }
        )

        try {
            transaction.apply(
                existing = failing,
                incomingTitleDir = incomingBuilt.titleDir
            )
            fail("Expected transaction to fail")
        } catch (_: IllegalArgumentException) {
            // require(...) uses IllegalArgumentException.
        }

        assertArrayEquals(
            beforeInfo,
            baseStorage.readBytes("info.json")
        )
        assertArrayEquals(
            beforeChapters,
            baseStorage.readBytes("chapters.json")
        )
        assertEquals(beforeNames, baseStorage.names())
    }

    @Test
    fun noNewChaptersDoesNotRewriteExistingPackage() {
        val root = Files.createTempDirectory("readerlb_update_noop_").toFile()
        val existingBuilt = builder.build(
            book = book("No-op test", 1..5),
            rootDir = File(root, "existing")
        )
        val incomingBuilt = builder.build(
            book = book("No-op test", 2..4),
            rootDir = File(root, "incoming")
        )

        val storage = FileRanobeLibStorage(
            existingBuilt.titleDir
        )
        val before = storage.names().associateWith(storage::readBytes)

        val result = transaction.apply(
            existing = storage,
            incomingTitleDir = incomingBuilt.titleDir
        )

        assertFalse(result.changed)
        assertTrue(result.addedNumbers.isEmpty())
        assertEquals(5, result.totalChapterCount)

        val after = storage.names().associateWith(storage::readBytes)
        assertEquals(before.keys, after.keys)
        before.forEach { (name, bytes) ->
            assertArrayEquals(bytes, after.getValue(name))
        }
    }

    @Test
    fun partialIncomingRangeAddsOnlyMissingNumbers() {
        val root = Files.createTempDirectory("readerlb_update_partial_").toFile()
        val existingBuilt = builder.build(
            book = book("Partial test", 1..6),
            rootDir = File(root, "existing")
        )
        val incomingBuilt = builder.build(
            book = book("Partial test", 4..8),
            rootDir = File(root, "incoming")
        )

        val storage = FileRanobeLibStorage(
            existingBuilt.titleDir
        )

        val result = transaction.apply(
            existing = storage,
            incomingTitleDir = incomingBuilt.titleDir
        )

        assertEquals(listOf("7", "8"), result.addedNumbers)
        assertEquals(listOf("4", "5", "6"), result.overlappingNumbers)

        val merged = JSONArray(
            storage.readBytes("chapters.json")
                .toString(Charsets.UTF_8)
        )
        assertEquals(
            (1..8).map(Int::toString),
            chapterNumbers(merged)
        )
    }

    @Test
    fun refusesPackageWithDifferentTitleIdentity() {
        val root = Files.createTempDirectory("readerlb_update_identity_").toFile()
        val existingBuilt = builder.build(
            book = book("Первый тайтл", 1..3),
            rootDir = File(root, "existing")
        )
        val incomingBuilt = builder.build(
            book = book("Другой тайтл", 1..5),
            rootDir = File(root, "incoming")
        )

        val storage = FileRanobeLibStorage(
            existingBuilt.titleDir
        )
        val beforeNames = storage.names()

        try {
            transaction.apply(
                existing = storage,
                incomingTitleDir = incomingBuilt.titleDir
            )
            fail("Expected identity mismatch")
        } catch (error: IllegalArgumentException) {
            assertTrue(
                error.message.orEmpty()
                    .contains("не совпадает")
            )
        }

        assertEquals(beforeNames, storage.names())
    }

    @Test
    fun recoversMetadataBackupLeftByInterruptedPreviousAttempt() {
        val root = Files.createTempDirectory("readerlb_update_recover_").toFile()
        val existingBuilt = builder.build(
            book = book("Recovery test", 1..3),
            rootDir = File(root, "existing")
        )
        val incomingBuilt = builder.build(
            book = book("Recovery test", 1..4),
            rootDir = File(root, "incoming")
        )

        val storage = FileRanobeLibStorage(
            existingBuilt.titleDir
        )

        assertTrue(
            storage.rename(
                "chapters.json",
                ".readerlb-chapters.bak"
            )
        )
        assertFalse(storage.exists("chapters.json"))

        val result = transaction.apply(
            existing = storage,
            incomingTitleDir = incomingBuilt.titleDir
        )

        assertTrue(result.changed)
        assertEquals(listOf("4"), result.addedNumbers)
        assertTrue(storage.exists("chapters.json"))
        assertFalse(storage.exists(".readerlb-chapters.bak"))
    }

    private fun book(
        title: String,
        range: IntRange
    ): ParsedBook =
        ParsedBook(
            title = title,
            chapters = range.map { number ->
                ParsedChapter(
                    number = number.toString(),
                    title = "Глава $number",
                    blocks = listOf(
                        ReaderBlock.Paragraph(
                            "Содержимое главы $number"
                        )
                    )
                )
            }
        )

    private fun chapterNumbers(
        array: JSONArray
    ): List<String> =
        (0 until array.length()).map {
            array.getJSONObject(it)
                .getString("number")
        }

    private class FaultingStorage(
        private val delegate: RanobeLibMutableStorage,
        private val shouldFailRename: (
            from: String,
            to: String
        ) -> Boolean
    ) : RanobeLibMutableStorage {

        override fun exists(name: String): Boolean =
            delegate.exists(name)

        override fun readBytes(name: String): ByteArray =
            delegate.readBytes(name)

        override fun writeBytes(
            name: String,
            bytes: ByteArray
        ) =
            delegate.writeBytes(name, bytes)

        override fun delete(name: String): Boolean =
            delegate.delete(name)

        override fun rename(
            from: String,
            to: String
        ): Boolean =
            if (shouldFailRename(from, to)) {
                false
            } else {
                delegate.rename(from, to)
            }

        override fun length(name: String): Long =
            delegate.length(name)

        override fun names(): Set<String> =
            delegate.names()
    }
}
