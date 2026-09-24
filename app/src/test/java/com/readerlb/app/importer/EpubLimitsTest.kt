package com.readerlb.app.importer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InterruptedIOException
import java.io.InputStream
import java.nio.file.Files

class EpubLimitsTest {
    @Test
    fun cumulativeImageBudgetRejectsNextImage() {
        checkImageBudget(7, 2, 1, singleLimit = 5, totalLimit = 10)
        try {
            checkImageBudget(7, 2, 2, singleLimit = 5, totalLimit = 10)
            fail("Expected total image limit")
        } catch (_: EpubLimitException) {
        }
    }
    @Test
    fun sourceCopyRejectsOversizeAndDeletesPartialFile() {
        val target = Files.createTempFile("epub_limit_", ".epub").toFile()
        try {
            copyEpubSource(ByteArrayInputStream(ByteArray(12)), target, 10)
            fail("Expected source limit")
        } catch (expected: IllegalArgumentException) {
            assertFalse(target.exists())
        }
    }

    @Test
    fun sourceCopyPreservesAllowedInput() {
        val target = Files.createTempFile("epub_limit_", ".epub").toFile()
        try {
            copyEpubSource(ByteArrayInputStream(byteArrayOf(1, 2, 3)), target, 3)
            assertArrayEquals(byteArrayOf(1, 2, 3), target.readBytes())
        } finally {
            target.delete()
        }
    }

    @Test
    fun sourceCopyHonorsInterruptionAndDeletesPartialFile() {
        val target = Files.createTempFile("epub_cancel_", ".epub").toFile()
        Thread.currentThread().interrupt()
        try {
            copyEpubSource(ByteArrayInputStream(byteArrayOf(1)), target, 10)
            fail("Expected interruption")
        } catch (_: InterruptedIOException) {
            assertFalse(target.exists())
        } finally {
            Thread.interrupted()
            target.delete()
        }
    }

    @Test
    fun imageReadHonorsInterruptionAfterProgress() {
        var firstRead = true
        val input =
            object : InputStream() {
                override fun read(): Int = -1

                override fun read(
                    buffer: ByteArray,
                    offset: Int,
                    length: Int
                ): Int {
                    if (!firstRead) return -1
                    firstRead = false
                    buffer[offset] = 1
                    Thread.currentThread().interrupt()
                    return 1
                }
            }

        try {
            readBoundedEpubImage(input)
            fail("Expected interruption")
        } catch (_: InterruptedIOException) {
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun corruptArchiveRemovesTemporaryAssets() {
        val source = Files.createTempFile("corrupt_", ".epub").toFile()
        val assets = Files.createTempDirectory("epub_assets_").toFile()
        source.writeBytes(byteArrayOf(1, 2, 3))
        try {
            parseEpubArchiveWithCleanup(source, null, assets, EpubArchiveParser())
            fail("Expected corrupt EPUB")
        } catch (_: java.util.zip.ZipException) {
            assertFalse(assets.exists())
        } finally {
            source.delete()
            assets.deleteRecursively()
        }
    }

    @Test fun archiveParserRejectsOversizedSourceBeforeOpeningZip() {
        val source = Files.createTempFile("oversized_", ".epub").toFile()
        try {
            java.io.RandomAccessFile(source, "rw").use { it.setLength(MAX_EPUB_SOURCE_BYTES + 1) }
            try {
                EpubArchiveParser().parse(source)
                fail("Expected source limit")
            } catch (_: EpubLimitException) {
                assertTrue(source.exists())
            }
        } finally {
            source.delete()
        }
    }
}
