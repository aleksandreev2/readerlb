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
    }

    @Test
    fun androidElevenAndNewerAreMarkedSystemRestricted() {
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

            assertEquals(
                RanobeLibAccessCapability
                    .SYSTEM_RESTRICTED,
                result.capability
            )
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
}
