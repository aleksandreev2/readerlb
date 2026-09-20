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

        // Keep the journal after an in-process rollback. If deleting a file
        // during rollback ever fails on a real SAF provider, the next launch
        // still has enough information to finish recovery safely.
        assertTrue(
            baseStorage.exists(".readerlb-update.json")
        )
        assertEquals(
            beforeNames,
            baseStorage.names() - ".readerlb-update.json"
        )

        val recovered = transaction.apply(
            existing = baseStorage,
            incomingTitleDir = incomingBuilt.titleDir
        )
        assertTrue(recovered.changed)
        assertEquals(listOf("4", "5"), recovered.addedNumbers)
        assertFalse(
            baseStorage.exists(".readerlb-update.json")
        )
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
    fun journalRemovesOrphanZipLeftBeforeMetadataCommit() {
        val root = Files.createTempDirectory("readerlb_update_journal_").toFile()
        val existingBuilt = builder.build(
            book = book("Journal recovery", 1..3),
            rootDir = File(root, "existing")
        )
        val incomingBuilt = builder.build(
            book = book("Journal recovery", 1..4),
            rootDir = File(root, "incoming")
        )

        val storage = FileRanobeLibStorage(
            existingBuilt.titleDir
        )
        val incomingChapters = JSONArray(
            File(
                incomingBuilt.titleDir,
                "chapters.json"
            ).readText()
        )
        val zip4 = RanobeLibUpdatePlanner()
            .chapterNumberToZip(incomingChapters)
            .getValue("4")

        // Simulate process death after publishing the new ZIP but before
        // changing chapters.json/info.json.
        storage.writeBytes(
            zip4,
            byteArrayOf(1, 2, 3, 4)
        )
        storage.writeBytes(
            ".readerlb-update.json",
            JSONObject()
                .put(
                    "addedZipNames",
                    JSONArray().put(zip4)
                )
                .put(
                    "addedNumbers",
                    JSONArray().put("4")
                )
                .toString()
                .toByteArray()
        )

        val result = transaction.apply(
            existing = storage,
            incomingTitleDir = incomingBuilt.titleDir
        )

        assertEquals(listOf("4"), result.addedNumbers)
        assertFalse(storage.exists(".readerlb-update.json"))

        val finalZip = storage.readBytes(zip4)
        assertTrue(finalZip.size > 4)
        assertEquals('P'.code.toByte(), finalZip[0])
        assertEquals('K'.code.toByte(), finalZip[1])
    }

    @Test
    fun journalRecognizesAlreadyCommittedUpdateAndOnlyCleansBackups() {
        val root = Files.createTempDirectory("readerlb_update_committed_").toFile()
        val oldBuilt = builder.build(
            book = book("Committed recovery", 1..3),
            rootDir = File(root, "old")
        )
        val committedBuilt = builder.build(
            book = book("Committed recovery", 1..4),
            rootDir = File(root, "committed")
        )

        val storage = FileRanobeLibStorage(
            committedBuilt.titleDir
        )
        val oldStorage = FileRanobeLibStorage(
            oldBuilt.titleDir
        )

        storage.writeBytes(
            ".readerlb-chapters.bak",
            oldStorage.readBytes("chapters.json")
        )
        storage.writeBytes(
            ".readerlb-info.bak",
            oldStorage.readBytes("info.json")
        )

        val committedChapters = JSONArray(
            storage.readBytes("chapters.json")
                .toString(Charsets.UTF_8)
        )
        val zip4 = RanobeLibUpdatePlanner()
            .chapterNumberToZip(committedChapters)
            .getValue("4")

        storage.writeBytes(
            ".readerlb-update.json",
            JSONObject()
                .put(
                    "addedZipNames",
                    JSONArray().put(zip4)
                )
                .put(
                    "addedNumbers",
                    JSONArray().put("4")
                )
                .toString()
                .toByteArray()
        )

        val result = transaction.apply(
            existing = storage,
            incomingTitleDir = committedBuilt.titleDir
        )

        assertFalse(result.changed)
        assertEquals(4, result.totalChapterCount)
        assertFalse(storage.exists(".readerlb-chapters.bak"))
        assertFalse(storage.exists(".readerlb-info.bak"))
        assertFalse(storage.exists(".readerlb-update.json"))
        assertTrue(storage.exists(zip4))
    }

    @Test
    fun committedMetadataWithMissingNewZipRollsBackToOldState() {
        val root = Files.createTempDirectory("readerlb_update_missing_zip_").toFile()
        val oldBuilt = builder.build(
            book = book("Missing zip recovery", 1..3),
            rootDir = File(root, "old")
        )
        val committedBuilt = builder.build(
            book = book("Missing zip recovery", 1..4),
            rootDir = File(root, "committed")
        )

        val storage = FileRanobeLibStorage(
            committedBuilt.titleDir
        )
        val oldStorage = FileRanobeLibStorage(
            oldBuilt.titleDir
        )

        storage.writeBytes(
            ".readerlb-chapters.bak",
            oldStorage.readBytes("chapters.json")
        )
        storage.writeBytes(
            ".readerlb-info.bak",
            oldStorage.readBytes("info.json")
        )

        val committedChapters = JSONArray(
            storage.readBytes("chapters.json")
                .toString(Charsets.UTF_8)
        )
        val zip4 = RanobeLibUpdatePlanner()
            .chapterNumberToZip(committedChapters)
            .getValue("4")
        assertTrue(storage.delete(zip4))

        storage.writeBytes(
            ".readerlb-update.json",
            JSONObject()
                .put(
                    "addedZipNames",
                    JSONArray().put(zip4)
                )
                .put(
                    "addedNumbers",
                    JSONArray().put("4")
                )
                .toString()
                .toByteArray()
        )

        val result = transaction.apply(
            existing = storage,
            incomingTitleDir = committedBuilt.titleDir
        )

        assertTrue(result.changed)
        assertEquals(listOf("4"), result.addedNumbers)
        val finalChapters = JSONArray(
            storage.readBytes("chapters.json")
                .toString(Charsets.UTF_8)
        )
        assertEquals(
            listOf("1", "2", "3", "4"),
            chapterNumbers(finalChapters)
        )
        assertTrue(storage.exists(zip4))
        assertFalse(storage.exists(".readerlb-update.json"))
    }

    @Test
    fun sameLengthCorruptionInStagingIsDetectedBeforePublishingAnything() {
        val root = Files.createTempDirectory("readerlb_update_hash_").toFile()
        val existingBuilt = builder.build(
            book = book("Hash test", 1..3),
            rootDir = File(root, "existing")
        )
        val incomingBuilt = builder.build(
            book = book("Hash test", 1..4),
            rootDir = File(root, "incoming")
        )

        val base = FileRanobeLibStorage(
            existingBuilt.titleDir
        )
        val beforeInfo = base.readBytes("info.json")
        val beforeChapters = base.readBytes("chapters.json")
        val beforeNames = base.names()

        val corrupting = CorruptingStagedZipStorage(base)

        try {
            transaction.apply(
                existing = corrupting,
                incomingTitleDir = incomingBuilt.titleDir
            )
            fail("Expected hash validation to fail")
        } catch (error: IllegalArgumentException) {
            assertTrue(
                error.message.orEmpty()
                    .contains("Контрольная сумма")
            )
        }

        assertArrayEquals(
            beforeInfo,
            base.readBytes("info.json")
        )
        assertArrayEquals(
            beforeChapters,
            base.readBytes("chapters.json")
        )
        assertEquals(beforeNames, base.names())
    }

    @Test
    fun corruptJournalStopsBeforeChangingExistingTitle() {
        val root = Files.createTempDirectory("readerlb_update_bad_journal_").toFile()
        val existingBuilt = builder.build(
            book = book("Bad journal", 1..3),
            rootDir = File(root, "existing")
        )
        val incomingBuilt = builder.build(
            book = book("Bad journal", 1..4),
            rootDir = File(root, "incoming")
        )

        val storage = FileRanobeLibStorage(
            existingBuilt.titleDir
        )
        val beforeInfo = storage.readBytes("info.json")
        val beforeChapters = storage.readBytes("chapters.json")

        storage.writeBytes(
            ".readerlb-update.json",
            "not-json".toByteArray()
        )

        try {
            transaction.apply(
                existing = storage,
                incomingTitleDir = incomingBuilt.titleDir
            )
            fail("Expected corrupt journal to stop update")
        } catch (error: IllegalStateException) {
            assertTrue(
                error.message.orEmpty()
                    .contains("Журнал")
            )
        }

        assertArrayEquals(
            beforeInfo,
            storage.readBytes("info.json")
        )
        assertArrayEquals(
            beforeChapters,
            storage.readBytes("chapters.json")
        )
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

    private class CorruptingStagedZipStorage(
        private val delegate: RanobeLibMutableStorage
    ) : RanobeLibMutableStorage {

        override fun exists(name: String): Boolean =
            delegate.exists(name)

        override fun readBytes(name: String): ByteArray =
            delegate.readBytes(name)

        override fun writeBytes(
            name: String,
            bytes: ByteArray
        ) {
            val output = if (
                name.startsWith(".readerlb-new-") &&
                bytes.isNotEmpty()
            ) {
                bytes.copyOf().also { copy ->
                    copy[copy.lastIndex] =
                        (copy.last().toInt() xor 0x01).toByte()
                }
            } else {
                bytes
            }
            delegate.writeBytes(name, output)
        }

        override fun delete(name: String): Boolean =
            delegate.delete(name)

        override fun rename(
            from: String,
            to: String
        ): Boolean =
            delegate.rename(from, to)

        override fun length(name: String): Long =
            delegate.length(name)

        override fun names(): Set<String> =
            delegate.names()
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
