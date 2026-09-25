package com.readerlb.app.export

import java.io.OutputStream

class LocalBookTxtWriter {

    fun write(
        book: LocalExportBook,
        output: OutputStream,
        readChapter: (
            LocalExportChapterRef
        ) -> LocalExportChapter,
        onProgress: (
            LocalExportProgress
        ) -> Unit = {}
    ) {
        output
            .bufferedWriter(
                Charsets.UTF_8
            )
            .use { writer ->
                writer.appendLine(
                    book.title
                )

                if (
                    book.author.isNotBlank()
                ) {
                    writer.appendLine(
                        book.author
                    )
                }

                writer.appendLine()
                writer.appendLine()

                onProgress(
                    LocalExportProgress(
                        completedChapters = 0,
                        totalChapters =
                            book.chapters.size
                    )
                )

                book.chapters
                    .forEachIndexed {
                            index,
                            reference ->
                        val chapter =
                            readChapter(
                                reference
                            )

                        writer.appendLine(
                            chapterHeading(
                                chapter
                            )
                        )
                        writer.appendLine()

                        chapter.blocks.forEach {
                                block ->
                            when (block) {
                                is LocalExportBlock
                                    .Paragraph -> {
                                    writer.appendLine(
                                        block.text
                                    )
                                    writer.appendLine()
                                }

                                is LocalExportBlock
                                    .Quote -> {
                                    block.lines
                                        .forEach {
                                                line ->
                                            writer
                                                .append(
                                                    "> "
                                                )
                                                .appendLine(
                                                    line
                                                )
                                        }
                                    writer.appendLine()
                                }

                                LocalExportBlock
                                    .HorizontalRule -> {
                                    writer.appendLine(
                                        "* * *"
                                    )
                                    writer.appendLine()
                                }

                                is LocalExportBlock
                                    .Image -> {
                                    writer.appendLine(
                                        imageMarker(
                                            block
                                        )
                                    )
                                    writer.appendLine()
                                }
                            }
                        }

                        if (
                            index !=
                            book.chapters
                                .lastIndex
                        ) {
                            writer.appendLine()
                        }

                        onProgress(
                            LocalExportProgress(
                                completedChapters =
                                    index + 1,
                                totalChapters =
                                    book.chapters
                                        .size
                            )
                        )
                    }
            }
    }

    private fun chapterHeading(
        chapter: LocalExportChapter
    ): String =
        if (
            chapter.title.isBlank()
        ) {
            "Глава ${chapter.number}"
        } else {
            "Глава ${chapter.number} — ${chapter.title}"
        }

    private fun imageMarker(
        image: LocalExportBlock.Image
    ): String =
        image.description
            ?.takeIf(String::isNotBlank)
            ?.let {
                "[Иллюстрация: $it]"
            }
            ?: "[Иллюстрация]"
}
