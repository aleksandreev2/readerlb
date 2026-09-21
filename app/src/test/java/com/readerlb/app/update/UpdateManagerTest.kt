package com.readerlb.app.update

import org.junit.Assert.assertEquals
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
    fun updateErrorsExplainSigningMigrationAndIntegrityFailures() {
        assertTrue(
            friendlyUpdateDownloadError(
                IllegalArgumentException(
                    "Подпись обновления не совпадает с установленной версией ReaderLB"
                )
            ).contains("0.3.1–0.4.2")
        )

        assertTrue(
            friendlyUpdateDownloadError(
                IllegalArgumentException(
                    "SHA-256 обновления не совпадает"
                )
            ).contains("целостности")
        )
    }

    @Test
    fun installerStatusesHaveActionableMessages() {
        assertEquals(
            "Установка ReaderLB отменена.",
            installStatusMessage(
                android.content.pm.PackageInstaller
                    .STATUS_FAILURE_ABORTED
            )
        )
        assertTrue(
            installStatusMessage(
                android.content.pm.PackageInstaller
                    .STATUS_FAILURE_STORAGE
            ).contains("места")
        )
        assertTrue(
            installStatusMessage(
                android.content.pm.PackageInstaller
                    .STATUS_FAILURE_CONFLICT
            ).contains("0.3.1–0.4.2")
        )
    }

    @Test
    fun updateDownloadSizeLimitAllowsUnknownOrExpectedSizes() {
        requireUpdateApkSizeWithinLimit(-1L)
        requireUpdateApkSizeWithinLimit(
            MAX_UPDATE_APK_BYTES
        )
    }

    @Test
    fun updateDownloadSizeLimitRejectsOversizedApks() {
        var failed = false

        try {
            requireUpdateApkSizeWithinLimit(
                MAX_UPDATE_APK_BYTES + 1L
            )
        } catch (
            expected:
                IllegalArgumentException
        ) {
            failed = true
        }

        assertTrue(failed)
        assertTrue(
            friendlyUpdateDownloadError(
                IllegalArgumentException(
                    "APK обновления слишком большой"
                )
            ).contains("неожиданный размер")
        )
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
        assertFalse(
            isVersionNewer(
                latest = "1.0.0-beta",
                current = "0.9.6"
            )
        )
        assertFalse(
            isVersionNewer(
                latest = "nightly",
                current = "0.9.6"
            )
        )
    }
}
