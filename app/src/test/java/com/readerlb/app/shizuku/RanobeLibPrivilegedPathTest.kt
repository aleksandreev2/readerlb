package com.readerlb.app.shizuku

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RanobeLibPrivilegedPathTest {

    @Test
    fun resolvesOnlyInsideConfiguredRanobeLibRoot() {
        val root =
            Files.createTempDirectory(
                "readerlb-shizuku-root"
            )
                .toFile()

        try {
            val resolved =
                resolveRestrictedRanobeLibPath(
                    root = root,
                    relativePath =
                        "title/info.json"
                )

            assertEquals(
                File(
                    root,
                    "title/info.json"
                ).canonicalFile,
                resolved
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun allowsTheConfiguredRootForReadOnlyOperations() {
        val root =
            Files.createTempDirectory(
                "readerlb-shizuku-root"
            )
                .toFile()

        try {
            assertEquals(
                root.canonicalFile,
                resolveRestrictedRanobeLibPath(
                    root = root,
                    relativePath = ""
                )
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsParentTraversalOutsideRanobeLib() {
        val root =
            Files.createTempDirectory(
                "readerlb-shizuku-root"
            )
                .toFile()

        try {
            assertRejected {
                resolveRestrictedRanobeLibPath(
                    root = root,
                    relativePath =
                        "../outside.txt"
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsAbsolutePaths() {
        val root =
            Files.createTempDirectory(
                "readerlb-shizuku-root"
            )
                .toFile()

        try {
            assertRejected {
                resolveRestrictedRanobeLibPath(
                    root = root,
                    relativePath =
                        "/storage/emulated/0/Download/file.txt"
                )
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsSymlinkEscapeWhenSupportedByHost() {
        val temp =
            Files.createTempDirectory(
                "readerlb-shizuku-symlink"
            )
                .toFile()
        val root =
            File(
                temp,
                "book"
            )
        val outside =
            File(
                temp,
                "outside"
            )

        root.mkdirs()
        outside.mkdirs()

        val link =
            File(
                root,
                "link"
            )

        try {
            val created =
                runCatching {
                    Files.createSymbolicLink(
                        link.toPath(),
                        outside.toPath()
                    )
                }.isSuccess

            if (!created) {
                return
            }

            assertRejected {
                resolveRestrictedRanobeLibPath(
                    root = root,
                    relativePath =
                        "link/secret.txt"
                )
            }
        } finally {
            temp.deleteRecursively()
        }
    }

    private fun assertRejected(
        block: () -> Unit
    ) {
        try {
            block()
            fail(
                "Ожидалось отклонение пути"
            )
        } catch (
            expected:
                IllegalArgumentException
        ) {
            assertTrue(
                expected.message
                    .orEmpty()
                    .contains(
                        "путь",
                        ignoreCase = true
                    )
            )
        }
    }
}
