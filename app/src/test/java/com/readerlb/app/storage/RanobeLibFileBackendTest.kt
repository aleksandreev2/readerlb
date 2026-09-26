package com.readerlb.app.storage

import android.os.ParcelFileDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class RanobeLibFileBackendTest {
    private class FakeBackend : RanobeLibFileBackend {
        override fun list(relativePath: String) = emptyArray<String>()
        override fun exists(relativePath: String) = true
        override fun isDirectory(relativePath: String) = true
        override fun length(relativePath: String) = 0L
        override fun lastModified(relativePath: String) = 0L
        override fun open(relativePath: String, mode: String): ParcelFileDescriptor =
            error("Not used by this test")
        override fun create(relativePath: String, directory: Boolean) = true
        override fun delete(relativePath: String) = true
        override fun rename(from: String, to: String) = true
    }

    @Test fun clearingOldBackendDoesNotRemoveNewerBridge() {
        RanobeLibBackends.install(
            RanobeLibBackendKind.SHIZUKU,
            FakeBackend()
        )
        RanobeLibBackends.install(
            RanobeLibBackendKind.READERLB_BRIDGE,
            FakeBackend()
        )

        RanobeLibBackends.clear(
            RanobeLibBackendKind.SHIZUKU
        )

        assertEquals(
            RanobeLibBackendKind.READERLB_BRIDGE,
            RanobeLibBackends.currentKind()
        )
        assertNotNull(RanobeLibBackends.requireBackend())

        RanobeLibBackends.clear(
            RanobeLibBackendKind.READERLB_BRIDGE
        )
    }
}
