package com.readerlb.app.export

data class LocalExportBook(
    val title: String,
    val author: String,
    val description: String,
    val languageLabel: String,
    val slugUrl: String,
    val coverName: String?,
    val chapters: List<LocalExportChapterRef>
)

data class LocalExportChapterRef(
    val number: String,
    val title: String,
    val volume: String,
    val chapterId: Long?,
    val archiveName: String?
)

data class LocalExportChapter(
    val number: String,
    val title: String,
    val blocks: List<LocalExportBlock>,
    val warnings: List<String> = emptyList()
)

sealed interface LocalExportBlock {
    data class Paragraph(
        val text: String,
        val centered: Boolean = false
    ) : LocalExportBlock

    data class Quote(
        val lines: List<String>
    ) : LocalExportBlock

    data object HorizontalRule : LocalExportBlock

    data class Image(
        val entryName: String,
        val extension: String,
        val description: String? = null
    ) : LocalExportBlock
}

data class LocalExportProgress(
    val completedChapters: Int,
    val totalChapters: Int
)

enum class LocalBookExportFormat(
    val displayName: String,
    val extension: String,
    val mimeType: String
) {
    EPUB(
        displayName = "EPUB",
        extension = "epub",
        mimeType =
            "application/epub+zip"
    ),
    PDF(
        displayName = "PDF",
        extension = "pdf",
        mimeType = "application/pdf"
    ),
    FB2(
        displayName = "FB2",
        extension = "fb2",
        mimeType =
            "application/x-fictionbook+xml"
    ),
    TXT(
        displayName = "TXT",
        extension = "txt",
        mimeType = "text/plain"
    )
}

data class LocalExportOptions(
    val firstChapter: String? = null,
    val lastChapter: String? = null,
    val includeCover: Boolean = true,
    val includeImages: Boolean = true
)

internal fun checkLocalExportInterrupted() {
    if (
        Thread.currentThread()
            .isInterrupted
    ) {
        throw InterruptedException(
            "Экспорт отменён"
        )
    }
}
