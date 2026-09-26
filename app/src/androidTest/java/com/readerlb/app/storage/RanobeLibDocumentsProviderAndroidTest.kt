package com.readerlb.app.storage

import android.os.ParcelFileDescriptor
import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RanobeLibDocumentsProviderAndroidTest {
    @Test fun documentOperationsUseTheVerifiedFileInterface() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "provider-test-${System.nanoTime()}")
        assertTrue(root.mkdir())
        RanobeLibBackends.install(
            RanobeLibBackendKind.SHIZUKU,
            ShizukuRanobeLibFileBackend(
                TestFiles(root)
            )
        )
        try {
            val tree = DocumentFile.fromTreeUri(context, ShizukuAccess.treeUri)!!
            assertTrue(tree.isDirectory)
            val title = tree.createDirectory("title")!!
            val info = title.createFile("application/json", "info.json")!!
            context.contentResolver.openOutputStream(info.uri, "w")!!.use {
                it.write("{\"media\":{}}".toByteArray())
            }
            val found = title.findFile("info.json")!!
            assertEquals(
                "{\"media\":{}}",
                context.contentResolver.openInputStream(found.uri)!!.bufferedReader().use { it.readText() }
            )
            assertTrue(found.renameTo("renamed.json"))
            assertTrue(title.findFile("renamed.json")!!.delete())
            assertTrue(title.delete())
        } finally {
            RanobeLibBackends.clear(
                RanobeLibBackendKind.SHIZUKU
            )
            root.deleteRecursively()
        }
    }

    private class TestFiles(private val root: File) : IReaderLbFiles.Stub() {
        private fun file(path: String) = if (path.isEmpty()) root else File(root, path)
        override fun probe() = true
        override fun list(relativePath: String): Array<String> =
            file(relativePath).list()?.toList().orEmpty().toTypedArray()
        override fun exists(relativePath: String) = file(relativePath).exists()
        override fun isDirectory(relativePath: String) = file(relativePath).isDirectory
        override fun length(relativePath: String) = file(relativePath).length()
        override fun lastModified(relativePath: String) = file(relativePath).lastModified()
        override fun open(relativePath: String, mode: String): ParcelFileDescriptor =
            ParcelFileDescriptor.open(file(relativePath), ParcelFileDescriptor.parseMode(mode))
        override fun create(relativePath: String, directory: Boolean): Boolean =
            if (directory) file(relativePath).mkdir() else file(relativePath).createNewFile()
        override fun delete(relativePath: String) = file(relativePath).deleteRecursively()
        override fun rename(from: String, to: String) = file(from).renameTo(file(to))
    }
}
