package com.readerlb.app.storage

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
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
                File(root, "title-$index").mkdir()
            }
            File(root, "title-with-random-files").mkdir()
            File(File(root, "title-with-random-files"), "not-ranobelib.txt")
                .writeText("fixture")

            assertTrue(isUsableLibraryRoot(root))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rootSelectionKeepsBookAndMangaOnOneVolume() {
        val parent =
            Files.createTempDirectory(
                "readerlb-volumes"
            ).toFile()
        try {
            val primaryFiles =
                File(
                    parent,
                    "primary/files"
                ).apply {
                    mkdirs()
                }
            File(
                primaryFiles,
                "book"
            ).mkdir()

            val sdFiles =
                File(
                    parent,
                    "sd/files"
                ).apply {
                    mkdirs()
                }
            File(
                sdFiles,
                "book"
            ).mkdir()
            File(
                sdFiles,
                "manga"
            ).mkdir()

            val selection =
                requireNotNull(
                    selectConsistentLibraryRoots(
                        listOf(
                            primaryFiles,
                            sdFiles
                        )
                    )
                )

            assertEquals(
                sdFiles.canonicalFile,
                selection.filesRoot
            )
            assertEquals(
                sdFiles.canonicalFile,
                selection.roots
                    .getValue(
                        "files"
                    )
            )
            assertEquals(
                File(
                    sdFiles,
                    "book"
                ).canonicalFile,
                selection.roots
                    .getValue(
                        "book"
                    )
            )
            assertEquals(
                File(
                    sdFiles,
                    "manga"
                ).canonicalFile,
                selection.roots
                    .getValue(
                        "manga"
                    )
            )
        } finally {
            parent.deleteRecursively()
        }
    }

    @Test fun accessProbeRejectsMissingDirectory() {
        val parent = Files.createTempDirectory("readerlb-missing").toFile()
        try {
            assertFalse(isUsableLibraryRoot(File(parent, "book")))
        } finally {
            parent.deleteRecursively()
        }
    }

}
