package com.readerlb.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun releaseJsonSelectsReaderLbApkAndDigest() {
        val update = parseUpdateInfo(
            payload = """
                {
                  "tag_name": "v0.5.1",
                  "body": "Fixes",
                  "assets": [
                    {
                      "name": "checksums.txt",
                      "browser_download_url": "https://example.invalid/checksums"
                    },
                    {
                      "name": "ReaderLB-0.5.1.apk",
                      "browser_download_url": "https://example.invalid/readerlb.apk",
                      "digest": "sha256:ABCDEF"
                    }
                  ]
                }
            """.trimIndent(),
            currentVersion = "0.5.0"
        )

        assertTrue(update != null)
        assertTrue(
            update?.downloadUrl ==
                "https://example.invalid/readerlb.apk"
        )
        assertTrue(update?.sha256 == "abcdef")
    }

    @Test
    fun releaseJsonIgnoresCurrentVersion() {
        val update = parseUpdateInfo(
            payload = """
                {
                  "tag_name": "v0.5.0",
                  "assets": [
                    {
                      "name": "ReaderLB-0.5.0.apk",
                      "browser_download_url": "https://example.invalid/readerlb.apk"
                    }
                  ]
                }
            """.trimIndent(),
            currentVersion = "0.5.0"
        )

        assertFalse(update != null)
    }

    @Test
    fun semanticVersionComparisonUsesNumericSegments() {
        assertTrue(
            isVersionNewer(
                latest = "0.5.0",
                current = "0.4.2"
            )
        )
        assertTrue(
            isVersionNewer(
                latest = "0.10.0",
                current = "0.9.9"
            )
        )
        assertFalse(
            isVersionNewer(
                latest = "0.4.2",
                current = "0.4.2"
            )
        )
        assertFalse(
            isVersionNewer(
                latest = "0.4.1",
                current = "0.4.2"
            )
        )
    }
}
