package com.readerlb.app.storage

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
}
