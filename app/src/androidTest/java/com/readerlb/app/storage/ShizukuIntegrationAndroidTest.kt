package com.readerlb.app.storage

import android.content.pm.PackageManager
import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import rikka.shizuku.Shizuku

/** Optional live test: install and start Shizuku, then add a valid test title under RanobeLib. */
@RunWith(AndroidJUnit4::class)
class ShizukuIntegrationAndroidTest {
    @Test fun runningShizukuCanScanTheKnownLibrary() {
        assumeTrue(InstrumentationRegistry.getArguments()
            .getString("readerlb_shizuku_fixture") == "true")
        assumeTrue(Shizuku.pingBinder())
        // The fixture is created by the developer's test device setup, never by the app.
        assumeTrue(File(
            "/storage/emulated/0/Android/data/ru.libappc/files/book/test-title/info.json"
        ).isFile)
        for (attempt in 0 until 50) {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) break
            Thread.sleep(100)
        }
        assertEquals(PackageManager.PERMISSION_GRANTED, Shizuku.checkSelfPermission())
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            ShizukuAccess.refresh(instrumentation.targetContext)
        }
        for (attempt in 0 until 100) {
            if (ShizukuAccess.state == ShizukuAccessState.READY) break
            Thread.sleep(100)
        }
        assertEquals(ShizukuAccessState.READY, ShizukuAccess.state)
        val items = RanobeLibLibraryScanner(instrumentation.targetContext)
            .scan(ShizukuAccess.treeUri).items
        assertTrue(items.any { it.slugUrl == "test-title" })
        val title = DocumentFile.fromTreeUri(
            instrumentation.targetContext, ShizukuAccess.treeUri
        )!!.findFile("test-title")!!
        val file = title.createFile("text/plain", ".readerlb-integration.tmp")!!
        try {
            instrumentation.targetContext.contentResolver.openOutputStream(file.uri, "w")!!.use {
                it.write("Shizuku works".toByteArray())
            }
            assertEquals(
                "Shizuku works",
                instrumentation.targetContext.contentResolver.openInputStream(file.uri)!!
                    .bufferedReader().use { it.readText() }
            )
            assertTrue(file.renameTo(".readerlb-integration-renamed.tmp"))
        } finally {
            title.findFile(".readerlb-integration-renamed.tmp")?.delete()
            title.findFile(".readerlb-integration.tmp")?.delete()
        }
    }
}
