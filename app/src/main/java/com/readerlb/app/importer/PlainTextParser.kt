package com.readerlb.app.importer

import android.content.Context
import android.net.Uri

class PlainTextParser(
    private val context: Context
) {

    fun parse(
        uri: Uri,
        fallbackTitle: String
    ): ParsedBook {
        val text = context.contentResolver
            .openInputStream(uri)
            .use { input ->
                requireNotNull(input) {
                    "Не удалось открыть TXT"
                }
                input
                    .bufferedReader()
                    .readText()
            }
            .replace("\r\n", "\n")

        val chapterHeading = Regex(
            """^(?:глава|chapter)\s+(\d+(?:[.,]\d+)?)\s*[-—.:]?\s*(.*)§""",
            RegexOption.IGNORE_CASE
        )

        val marker = Regex(
            """(?im)^(?=(?:глава|chapter)\s+\d+(?:[.,]\d+)?[^\n]*§)"""
        )

        val parts = text
            .split(marker)
            .map(String::trim)
            .filter(String::isNotBlank)

        val rawParts = if (parts.size > 1) {
            parts
        } else {
            listOf(text)
        }

        val chapters = rawParts.mapIndexed { index, part ->
            val lines = part.lines()
            val first = lines
                .firstOrNull()
                ?.trim()
                .orEmpty()

            val match = chapterHeading.matchEntire(first)
            val number = match
                ?.groupValues
                ?.getOrNull(1)
                ?.replace(',', '.')
                ?: (index + 1).toString()

            val title = match
                ?.groupValues
                ?.getOrNull(2)
                ?.trim()
                .orEmpty()

            val bodyLines = if (match != null) {
                lines.drop(1)
            } else {
                lines
            }

            val paragraphs = bodyLines
                .joinToString("\n")
                .split(
                    Regex("""\n\s*\n""")
                )
                .map {
                    it.replace('\n', ' ')
                        .trim()
                }
                .filter(String::isNotBlank)
                .map {
                    ReaderBlock.Paragraph(it)
                }

            ParsedChapter(
                number = number,
                title = title,
                blocks = paragraphs
            )
        }

        val issues = if (
            chapters.size == 1 &&
            chapterHeading.matchEntire(
                rawParts.firstOrNull()
                    ?.lineSequence()
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
            ) == null
        ) {
            listOf(
                ImportIssue(
                    code = "TXT_NUMBERING_INFERRED",
                    message = "В TXT не найдены заголовки вида «Глава N». " +
                        "Файл импортируется как одна глава.",
                    severity = ImportIssueSeverity.INFO
                )
            )
        } else {
            emptyList()
        }

        return ParsedBook(
            title = fallbackTitle.ifBlank {
                "Локальная новелла"
            },
            chapters = chapters,
            issues = issues
        )
    }
}
