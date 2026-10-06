package com.readerlb.app.storage

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShizukuPipeWriteAndroidTest {
    @Test fun lengthWaitsForPipeWriterToFinish() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "pipe-write-${System.nanoTime()}")
        val book = File(root, "book")
        assertTrue(book.mkdirs())
        val service = ShizukuFileService()
        val roots = ShizukuFileService::class.java.getDeclaredField("roots")
        roots.isAccessible = true
        roots.set(service, mapOf("book" to book.canonicalFile))
        val target = File(book, "chapter.zip")
        assertTrue(target.createNewFile())
        try {
            ParcelFileDescriptor.AutoCloseOutputStream(service.open("book/chapter.zip", "w")).use { output ->
                val block = ByteArray(8192) { (it % 251).toByte() }
                repeat(2048) { output.write(block) }
            }
            assertEquals(16L * 1024 * 1024, service.length("book/chapter.zip"))
            assertEquals(16L * 1024 * 1024, target.length())
        } finally {
            root.deleteRecursively()
        }
    }
}
