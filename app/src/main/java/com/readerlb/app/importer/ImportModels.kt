package com.readerlb.app.importer

import java.math.BigDecimal

data class ParsedBook(
    val title: String,
    val author: String = "",
    val description: String = "",
    val language: String = "",
    val chapters: List<ParsedChapter>,
    val coverBytes: ByteArray? = null,
    val coverExtension: String = "jpg",
    val issues: List<ImportIssue> = emptyList()
)

data class ParsedChapter(
    /**
     * Keep the original RanobeLib-compatible number as text.
     *
     * RanobeLib itself uses values such as 0, 0.5, 001, 2.91 and 1476.2.
     * Converting these to Int would silently corrupt real chapter numbering.
     */
    val number: String,
    val title: String,
    val blocks: List<ReaderBlock>
)

enum class ImportIssueSeverity {
    INFO,
    WARNING
}

data class ImportIssue(
    val code: String,
    val message: String,
    val severity: ImportIssueSeverity = ImportIssueSeverity.WARNING
)

sealed interface ReaderBlock {
    data class Paragraph(
        val text: String,
        val centered: Boolean = false
    ) : ReaderBlock

    data object HorizontalRule : ReaderBlock

    data class Quote(
        val lines: List<String>
    ) : ReaderBlock
}

data class ExportResult(
    val title: String,
    val chapterCount: Int,
    val firstChapter: String,
    val lastChapter: String,
    val slugUrl: String,
    val installedDirectly: Boolean,
    val downloadUri: String? = null
)

fun chapterNumberDecimal(value: String): BigDecimal? =
    runCatching {
        BigDecimal(
            value.trim().replace(',', '.')
        )
    }.getOrNull()

fun compareChapterNumbers(
    left: String,
    right: String
): Int {
    val leftDecimal = chapterNumberDecimal(left)
    val rightDecimal = chapterNumberDecimal(right)

    if (leftDecimal != null && rightDecimal != null) {
        val numeric = leftDecimal.compareTo(rightDecimal)
        if (numeric != 0) return numeric
    } else if (leftDecimal != null) {
        return -1
    } else if (rightDecimal != null) {
        return 1
    }

    // Raw values remain distinct: RanobeLib may contain both "001" and "1".
    return left.compareTo(right)
}

fun chapterNumberInRange(
    value: String,
    first: String?,
    last: String?
): Boolean {
    val valueDecimal = chapterNumberDecimal(value)
        ?: return first == null && last == null

    val firstDecimal = first
        ?.takeIf(String::isNotBlank)
        ?.let(::chapterNumberDecimal)
    val lastDecimal = last
        ?.takeIf(String::isNotBlank)
        ?.let(::chapterNumberDecimal)

    return (firstDecimal == null || valueDecimal >= firstDecimal) &&
        (lastDecimal == null || valueDecimal <= lastDecimal)
}

fun sortedChapterNumbers(
    values: Collection<String>
): List<String> =
    values.sortedWith(::compareChapterNumbers)
