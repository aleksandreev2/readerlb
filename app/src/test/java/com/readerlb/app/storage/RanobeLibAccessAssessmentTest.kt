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
        assertEquals(
            RanobeLibStorageBackend.SAF,
            result.backend
        )
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
        assertEquals(
            RanobeLibStorageBackend.SAF,
            result.backend
        )
    }

    @Test
    fun modernAndroidDefaultsToBuiltInReaderLbSetup() {
        listOf(
            30 to "11",
            35 to "15",
            36 to "16"
        ).forEach {
                (sdk, release) ->
            val result =
                assessRanobeLibAccess(
                    sdkInt = sdk,
                    androidRelease = release,
                    connected = false
                )

            assertEquals(
                RanobeLibAccessCapability
                    .READERLB_SETUP_REQUIRED,
                result.capability
            )
            assertEquals(
                RanobeLibStorageBackend.PORTABLE,
                result.backend
            )
            assertTrue(
                result.blockedByModernAndroid
            )
        }
    }

    @Test
    fun builtInFlowReportsProgressAndReadyBackend() {
        listOf(
            ReaderLbBuiltInAccessState.CHECKING,
            ReaderLbBuiltInAccessState.PAIRING,
            ReaderLbBuiltInAccessState.STARTING
        ).forEach { state ->
            val result =
                assessRanobeLibAccess(
                    sdkInt = 36,
                    androidRelease = "16",
                    connected = false,
                    builtInState = state
                )
            assertEquals(
                RanobeLibAccessCapability
                    .READERLB_CONNECTING,
                result.capability
            )
        }

        val ready =
            assessRanobeLibAccess(
                sdkInt = 36,
                androidRelease = "16",
                connected = false,
                builtInState =
                    ReaderLbBuiltInAccessState.READY
            )
        assertEquals(
            RanobeLibAccessCapability.CONNECTED,
            ready.capability
        )
        assertEquals(
            RanobeLibStorageBackend
                .READERLB_BRIDGE,
            ready.backend
        )
    }

    @Test
    fun builtInFailureCanBeRetried() {
        val result =
            assessRanobeLibAccess(
                sdkInt = 35,
                androidRelease = "15",
                connected = false,
                builtInState =
                    ReaderLbBuiltInAccessState.ERROR
            )

        assertEquals(
            RanobeLibAccessCapability
                .READERLB_ERROR,
            result.capability
        )
    }

    @Test
    fun alreadyReadyShizukuRemainsACompatibleBackend() {
        val result =
            assessRanobeLibAccess(
                sdkInt = 36,
                androidRelease = "16",
                connected = false,
                shizukuState =
                    ShizukuAccessState.READY
            )

        assertEquals(
            RanobeLibAccessCapability.CONNECTED,
            result.capability
        )
        assertEquals(
            RanobeLibStorageBackend.SHIZUKU,
            result.backend
        )
    }

    @Test
    fun connectedBackendIdentityIsPreserved() {
        assertEquals(
            RanobeLibStorageBackend
                .READERLB_BRIDGE,
            assessRanobeLibAccess(
                sdkInt = 36,
                androidRelease = "16",
                connected = true,
                builtInState =
                    ReaderLbBuiltInAccessState.READY,
                connectedWithBuiltInBridge = true
            ).backend
        )

        assertEquals(
            RanobeLibStorageBackend.SHIZUKU,
            assessRanobeLibAccess(
                sdkInt = 36,
                androidRelease = "16",
                connected = true,
                shizukuState =
                    ShizukuAccessState.READY,
                connectedWithShizuku = true
            ).backend
        )
    }
}
