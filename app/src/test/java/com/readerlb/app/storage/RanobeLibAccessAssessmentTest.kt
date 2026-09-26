package com.readerlb.app.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RanobeLibAccessAssessmentTest {

    @Test
    fun connectedAccessWinsOnModernAndroid() {
        val result =
            assessRanobeLibAccess(
                sdkInt = 36,
                androidRelease = "16",
                connected = true
            )

        assertEquals(
            RanobeLibAccessCapability.CONNECTED,
            result.capability
        )
        assertTrue(result.connected)
        assertEquals(RanobeLibStorageBackend.SAF, result.backend)
        assertFalse(
            result.blockedByModernAndroid
        )
    }

    @Test
    fun androidTenCanUseSystemFolderPicker() {
        val result =
            assessRanobeLibAccess(
                sdkInt = 29,
                androidRelease = "10",
                connected = false
            )

        assertEquals(
            RanobeLibAccessCapability.USER_PICKER,
            result.capability
        )
        assertTrue(
            result.canUseSystemFolderPicker
        )
        assertEquals(
            "Android 10",
            result.androidLabel
        )
        assertEquals(RanobeLibStorageBackend.SAF, result.backend)
    }

    @Test
    fun modernAndroidGuidesToShizukuWhenMissing() {
        listOf(
            30 to "11",
            35 to "15",
            36 to "16"
        ).forEach {
                (sdk, release) ->
            val result =
                assessRanobeLibAccess(
                    sdkInt = sdk,
                    androidRelease =
                        release,
                    connected = false
                )

            assertEquals(RanobeLibAccessCapability.SHIZUKU_NOT_INSTALLED, result.capability)
            assertEquals(RanobeLibStorageBackend.PORTABLE, result.backend)
            assertTrue(
                result
                    .blockedByModernAndroid
            )
            assertFalse(
                result
                    .canUseSystemFolderPicker
            )
        }
    }

    @Test
    fun allShizukuStatesSelectOneActionOnAndroidElevenToSixteen() {
        listOf(30, 35, 36).forEach { sdk ->
            listOf(
                ShizukuAccessState.STOPPED to RanobeLibAccessCapability.SHIZUKU_STOPPED,
                ShizukuAccessState.PERMISSION_REQUIRED to
                    RanobeLibAccessCapability.SHIZUKU_PERMISSION_REQUIRED,
                ShizukuAccessState.CONNECTING to
                    RanobeLibAccessCapability.SHIZUKU_CONNECTING,
                ShizukuAccessState.FOLDER_MISSING to
                    RanobeLibAccessCapability.SHIZUKU_FOLDER_MISSING,
                ShizukuAccessState.READY to RanobeLibAccessCapability.CONNECTED
            ).forEach { (state, expected) ->
                val result = assessRanobeLibAccess(sdk, "", false, state)
                assertEquals(expected, result.capability)
                assertEquals(
                    if (state == ShizukuAccessState.READY)
                        RanobeLibStorageBackend.SHIZUKU
                    else RanobeLibStorageBackend.PORTABLE,
                    result.backend
                )
            }
        }
    }

    @Test
    fun persistedSafAccessWinsAndRevokedShizukuFallsBack() {
        assertEquals(
            RanobeLibStorageBackend.SAF,
            assessRanobeLibAccess(36, "16", true, ShizukuAccessState.READY).backend
        )
        val revoked = assessRanobeLibAccess(
            36, "16", true, ShizukuAccessState.STOPPED,
            connectedWithShizuku = true
        )
        assertEquals(RanobeLibAccessCapability.SHIZUKU_STOPPED, revoked.capability)
        assertEquals(RanobeLibStorageBackend.PORTABLE, revoked.backend)
    }
}
