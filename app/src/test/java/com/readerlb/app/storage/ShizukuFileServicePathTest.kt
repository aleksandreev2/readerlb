package com.readerlb.app.storage

import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuFileServicePathTest {
    @Test fun onlyRelativeBookPathsAreAccepted() {
        assertTrue(isSafeLibraryPath(""))
        assertTrue(isSafeLibraryPath("title/info.json"))
        listOf(
            "/title", "title/", "title//info.json", "../secret", "title/../secret",
            "title/./info.json", "title\\info.json", "title/\u0000file"
        ).forEach { assertFalse(it, isSafeLibraryPath(it)) }
    }
    @Test fun accessProbeDoesNotInspectTitleMetadata() {
        val root = Files.createTempDirectory("readerlb-book").toFile()
        try {
            repeat(50) { index ->
                root.resolve("title-$index").mkdir()
            }
            root.resolve("title-with-random-files").mkdir().also { title ->
                title.resolve("not-ranobelib.txt").writeText("fixture")
            }

            assertTrue(isUsableLibraryRoot(root))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun accessProbeRejectsMissingDirectory() {
        val parent = Files.createTempDirectory("readerlb-missing").toFile()
        try {
            assertFalse(isUsableLibraryRoot(parent.resolve("book")))
        } finally {
            parent.deleteRecursively()
        }
    }

}
