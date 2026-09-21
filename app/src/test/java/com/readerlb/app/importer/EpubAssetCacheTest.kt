package com.readerlb.app.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class EpubAssetCacheTest {

    @Test
    fun removesOnlyStaleReaderLbAssetDirectories() {
        val root = Files
            .createTempDirectory(
                "readerlb_cache_cleanup_"
            )
            .toFile()
        val now = 2_000_000_000_000L

        val stale = root.resolve(
            EPUB_ASSET_DIRECTORY_PREFIX +
                "stale"
        ).apply {
            mkdirs()
            resolve("image.bin")
                .writeBytes(
                    byteArrayOf(1, 2, 3)
                )
            setLastModified(
                now -
                    EPUB_ASSET_MAX_AGE_MILLIS -
                    10_000L
            )
        }

        val recent = root.resolve(
            EPUB_ASSET_DIRECTORY_PREFIX +
                "recent"
        ).apply {
            mkdirs()
            resolve("image.bin")
                .writeBytes(
                    byteArrayOf(4, 5, 6)
                )
            setLastModified(
                now - 60_000L
            )
        }

        val unrelated = root.resolve(
            "other_cache"
        ).apply {
            mkdirs()
            setLastModified(
                now -
                    EPUB_ASSET_MAX_AGE_MILLIS -
                    10_000L
            )
        }

        try {
            assertEquals(
                1,
                cleanupStaleEpubAssetDirectories(
                    cacheDir = root,
                    nowMillis = now
                )
            )
            assertTrue(!stale.exists())
            assertTrue(recent.isDirectory)
            assertTrue(unrelated.isDirectory)
        } finally {
            root.deleteRecursively()
        }
    }
}
