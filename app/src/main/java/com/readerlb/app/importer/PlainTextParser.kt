package com.readerlb.app.importer

import android.content.Context
import android.net.Uri

class PlainTextParser(private val context: Context) {

    fun parse(uri: Uri, fallbackTitle: String): ParsedBook {
        val text = context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Не удалось открыть TXT" }
            input.bufferedReader().readText()
        }.replace("\r\n", "\n")

        val marker = Regex(
            """(?im)^(?=(?:глава|chapter)\s+\d+[^\n]*$)""",
            setOf(RegexOption.MULTILINE, RegexOption.IGNORE_CASE)
        )
        val parts = text.split(marker).map(String::trim).filter(String::isNotBlank)

        val chapters = (if (parts.size > 1) parts else listOf(text)).mapIndexed { index, part ->
            val lines = part.lines()
            val first = lines.firstOrNull()?.trim().orEmpty()
            val hasHeading = first.matches(
                Regex("""(?:глава|chapter)\s+\d+.*""", RegexOption.IGNORE_CASE)
            )
            val title = if (hasHeading) {
                first.replace(
                    Regex("""^(?:глава|chapter)\s*\d+\s*[-—.:]?\s*""", RegexOption.IGNORE_CASE),
                    ""
                ).trim()
            } else ""

            val bodyLines = if (hasHeading) lines.drop(1) else lines
            val paragraphs = bodyLines.joinToString("\n")
                .split(Regex("""\n\s*\n"""))
                .map { it.replace('\n', ' ').trim() }
                .filter(String::isNotBlank)
                .map { ReaderBlock.Paragraph(it) }

            ParsedChapter(index + 1, title, paragraphs)
        }

        return ParsedBook(
            title = fallbackTitle.ifBlank { "Локальная новелла" },
            chapters = chapters
        )
    }
}
