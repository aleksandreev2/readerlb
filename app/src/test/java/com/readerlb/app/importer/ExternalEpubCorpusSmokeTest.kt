package com.readerlb.app.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Optional smoke test for a real local EPUB corpus.
 *
 * Run with:
 * READERLB_EPUB_CORPUS=/path/to/epubs gradle :app:testDebugUnitTest
 *
 * The corpus is intentionally not committed to the repository.
 */
class ExternalEpubCorpusSmokeTest {

    private val parser = EpubArchiveParser()

    @Test
    fun parsesConfiguredCorpusWithoutSilentStructuralCorruption() {
        val dir = System.getenv("READERLB_EPUB_CORPUS")
            ?.let(::File)

        assumeTrue(dir?.isDirectory == true)

        val files = dir!!
            .listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("epub", ignoreCase = true) }
            .sortedBy { it.name }

        assertTrue("EPUB corpus is empty", files.isNotEmpty())

        files.forEach { file ->
            val book = parser.parse(file, file.name)

            assertTrue(
                "No chapters parsed from " + file.name,
                book.chapters.isNotEmpty()
            )
            assertEquals(
                "Duplicate chapter numbers in " + file.name,
                book.chapters.size,
                book.chapters.map { it.number }.distinct().size
            )
            assertTrue(
                "Negative chapter number in " + file.name,
                book.chapters.all { it.number >= 0 }
            )

            val warningText = book.issues.joinToString(" | ") {
                it.code + ": " + it.message
            }
            println(
                file.name + " => " +
                    book.chapters.size + " chapters, " +
                    (book.chapters.minOfOrNull { it.number } ?: 0) + "–" +
                    (book.chapters.maxOfOrNull { it.number } ?: 0) +
                    if (warningText.isBlank()) "" else " | " + warningText
            )
        }
    }
}
