package com.readerlb.app.importer

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
    val number: Int,
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
    data class Paragraph(val text: String, val centered: Boolean = false) : ReaderBlock
    data object HorizontalRule : ReaderBlock
    data class Quote(val lines: List<String>) : ReaderBlock
}

data class ExportResult(
    val title: String,
    val chapterCount: Int,
    val firstChapter: Int,
    val lastChapter: Int,
    val slugUrl: String,
    val installedDirectly: Boolean,
    val downloadUri: String? = null
)
