package com.readerlb.app.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import java.util.zip.ZipFile

/**
 * Optional end-to-end smoke test for a real local EPUB corpus.
 *
 * Run with:
 * READERLB_EPUB_CORPUS=/path/to/epubs gradle :app:testDebugUnitTest
 *
 * Full private books are intentionally never committed to the repository.
 */
class ExternalEpubCorpusSmokeTest {

    private val parser = EpubArchiveParser()
    private val packageBuilder = RanobeLibPackageBuilder(
        nowMillis = { 1_700_000_000_000L }
    )

    @Test
    fun parsesAndBuildsConfiguredCorpusWithoutSilentStructuralCorruption() {
        val dir = System.getenv(
            "READERLB_EPUB_CORPUS"
        )?.let(::File)

        assumeTrue(dir?.isDirectory == true)

        val files = dir!!
            .listFiles()
            .orEmpty()
            .filter {
                it.isFile &&
                    it.extension.equals(
                        "epub",
                        ignoreCase = true
                    )
            }
            .sortedBy { it.name }

        assertTrue(
            "EPUB corpus is empty",
            files.isNotEmpty()
        )

        files.forEach { file ->
            val assetDirectory = Files
                .createTempDirectory(
                    "readerlb_corpus_assets_"
                )
                .toFile()
            val book = parser.parse(
                file = file,
                sourceName = file.name,
                assetDirectory =
                    assetDirectory
            )

            assertTrue(
                "No chapters parsed from " + file.name,
                book.chapters.isNotEmpty()
            )

            assertEquals(
                "Duplicate chapter numbers in " + file.name,
                book.chapters.size,
                book.chapters
                    .map { it.number }
                    .distinct()
                    .size
            )

            assertTrue(
                "Invalid chapter number in " + file.name,
                book.chapters.all {
                    val value = chapterNumberDecimal(
                        it.number
                    )
                    value != null &&
                        value >= BigDecimal.ZERO
                }
            )

            val sorted = book.chapters
                .sortedWith { left, right ->
                    compareChapterNumbers(
                        left.number,
                        right.number
                    )
                }

            val packageRoot = Files
                .createTempDirectory(
                    "readerlb_corpus_"
                )
                .toFile()

            try {
                val built = packageBuilder.build(
                    book = book,
                    rootDir = packageRoot
                )

                val report = packageBuilder.verify(
                    built = built,
                    expectedChapterNumbers = sorted
                        .map { it.number }
                )

                assertTrue(
                    "Generated package failed verification for " +
                        file.name + ": " +
                        report.errors.joinToString(),
                    report.isValid
                )

                assertEquals(
                    book.chapters.size,
                    built.chapterCount
                )

                val sourceImageCount =
                    book.chapters.sumOf { chapter ->
                        chapter.blocks.count {
                            it is ReaderBlock.Image
                        }
                    }

                val packagedImageCount =
                    built.titleDir
                        .listFiles()
                        .orEmpty()
                        .filter {
                            it.extension.equals(
                                "zip",
                                ignoreCase = true
                            )
                        }
                        .sumOf { chapterZip ->
                            ZipFile(chapterZip).use { zip ->
                                zip.entries()
                                    .asSequence()
                                    .count { entry ->
                                        val extension = entry.name
                                            .substringAfterLast(
                                                '.',
                                                ""
                                            )
                                            .lowercase()

                                        !entry.isDirectory &&
                                            entry.name != "data.txt" &&
                                            extension in setOf(
                                                "jpg",
                                                "jpeg",
                                                "png",
                                                "webp",
                                                "gif"
                                            )
                                    }
                            }
                        }

                assertEquals(
                    "Illustrations were lost while packaging " +
                        file.name,
                    sourceImageCount,
                    packagedImageCount
                )
            } finally {
                packageRoot.deleteRecursively()
                assetDirectory.deleteRecursively()
            }

            val warningText = book.issues
                .joinToString(" | ") {
                    it.code + ": " + it.message
                }

            println(
                file.name + " => " +
                    book.chapters.size + " chapters, " +
                    (sorted.firstOrNull()?.number ?: "—") +
                    "–" +
                    (sorted.lastOrNull()?.number ?: "—") +
                    if (warningText.isBlank()) {
                        ""
                    } else {
                        " | " + warningText
                    }
            )
        }
    }
}
