package com.readerlb.app.importer

data class ParsedBook(
    val title: String,
    val author: String = "",
    val description: String = "",
    val language: String = "",
    val chapters: List<ParsedChapter>,
    val coverBytes: ByteArray? = null,
    val coverExtension: String = "jpg"
)

data class ParsedChapter(
    val number: Int,
    val title: String,
    val blocks: List<ReaderBlock>
)

sealed interface ReaderBlock {
    data class Paragraph(val text: String, val centered: Boolean = false) : ReaderBlock
    data object HorizontalRule : ReaderBlock
    data class Quote(val lines: List<String>) : ReaderBlock
}

data class ExportResult(
    val title: String,
    val chapterCount: Int,
    val slugUrl: String,
    val installedDirectly: Boolean,
    val downloadUri: String? = null
)
