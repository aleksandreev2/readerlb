package com.readerlb.app.export

import java.io.FilterOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.Base64

class LocalBookFb2Writer {

    fun write(
        book: LocalExportBook,
        output: OutputStream,
        readChapter: (
            LocalExportChapterRef
        ) -> LocalExportChapter,
        copyCover: ((
            OutputStream
        ) -> Unit)? = null,
        copyChapterImage: (
            reference: LocalExportChapterRef,
            image: LocalExportBlock.Image,
            output: OutputStream
        ) -> Unit,
        onProgress: (
            LocalExportProgress
        ) -> Unit = {}
    ) {
        val writer =
            OutputStreamWriter(
                output,
                Charsets.UTF_8
            )
        val binaries =
            mutableListOf<
                Fb2Binary
            >()

        val coverExtension =
            book.coverName
                ?.substringAfterLast(
                    '.',
                    ""
                )
                ?.lowercase()
                ?.takeIf {
                    imageMediaType(it) !=
                        null
                }
                ?.takeIf {
                    copyCover != null
                }

        writer.append(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
        )
        writer.append(
            "<FictionBook xmlns=\"http://www.gribuser.ru/xml/fictionbook/2.0\" xmlns:l=\"http://www.w3.org/1999/xlink\">\n"
        )
        writer.append(
            "  <description>\n"
        )
        writer.append(
            "    <title-info>\n"
        )
        writer.append(
            "      <genre>prose_contemporary</genre>\n"
        )
        writer.append(
            "      <book-title>"
        )
        writer.append(
            escapeXml(book.title)
        )
        writer.append(
            "</book-title>\n"
        )

        if (
            book.author.isNotBlank()
        ) {
            writer.append(
                "      <author><nickname>"
            )
            writer.append(
                escapeXml(
                    book.author
                )
            )
            writer.append(
                "</nickname></author>\n"
            )
        }

        if (
            book.description.isNotBlank()
        ) {
            writer.append(
                "      <annotation><p>"
            )
            writer.append(
                escapeXml(
                    book.description
                )
            )
            writer.append(
                "</p></annotation>\n"
            )
        }

        writer.append(
            "      <lang>ru</lang>\n"
        )

        if (
            coverExtension != null &&
            copyCover != null
        ) {
            writer.append(
                "      <coverpage><image l:href=\"#cover-image\"/></coverpage>\n"
            )
            binaries +=
                Fb2Binary(
                    id = "cover-image",
                    contentType =
                        requireNotNull(
                            imageMediaType(
                                coverExtension
                            )
                        ),
                    copy = copyCover
                )
        }

        writer.append(
            "    </title-info>\n"
        )
        writer.append(
            "    <document-info><program-used>ReaderLB</program-used><id>"
        )
        writer.append(
            escapeXml(
                book.slugUrl
            )
        )
        writer.append(
            "</id><version>1.0</version></document-info>\n"
        )
        writer.append(
            "  </description>\n"
        )
        writer.append(
            "  <body>\n"
        )
        writer.append(
            "    <title><p>"
        )
        writer.append(
            escapeXml(
                book.title
            )
        )
        writer.append(
            "</p></title>\n"
        )

        onProgress(
            LocalExportProgress(
                completedChapters = 0,
                totalChapters =
                    book.chapters.size
            )
        )

        book.chapters
            .forEachIndexed {
                    chapterIndex,
                    reference ->
                val chapter =
                    readChapter(
                        reference
                    )

                writer.append(
                    "    <section>\n"
                )
                writer.append(
                    "      <title><p>"
                )
                writer.append(
                    escapeXml(
                        chapterHeading(
                            chapter
                        )
                    )
                )
                writer.append(
                    "</p></title>\n"
                )

                chapter.blocks
                    .forEachIndexed {
                            blockIndex,
                            block ->
                        when (block) {
                            is LocalExportBlock
                                .Paragraph -> {
                                writer.append(
                                    "      <p>"
                                )
                                writer.append(
                                    escapeXml(
                                        block.text
                                    )
                                        .replace(
                                            "\n",
                                            "<br/>"
                                        )
                                )
                                writer.append(
                                    "</p>\n"
                                )
                            }

                            is LocalExportBlock
                                .Quote -> {
                                writer.append(
                                    "      <cite>\n"
                                )
                                block.lines
                                    .forEach {
                                            line ->
                                        writer.append(
                                            "        <p>"
                                        )
                                        writer.append(
                                            escapeXml(
                                                line
                                            )
                                        )
                                        writer.append(
                                            "</p>\n"
                                        )
                                    }
                                writer.append(
                                    "      </cite>\n"
                                )
                            }

                            LocalExportBlock
                                .HorizontalRule -> {
                                writer.append(
                                    "      <p>* * *</p>\n"
                                )
                            }

                            is LocalExportBlock
                                .Image -> {
                                val contentType =
                                    imageMediaType(
                                        block.extension
                                    )
                                        ?: return@forEachIndexed
                                val id =
                                    "image-" +
                                        (chapterIndex + 1) +
                                        "-" +
                                        (blockIndex + 1)

                                writer.append(
                                    "      <image l:href=\"#"
                                )
                                writer.append(id)
                                writer.append(
                                    "\"/>\n"
                                )

                                binaries +=
                                    Fb2Binary(
                                        id = id,
                                        contentType =
                                            contentType,
                                        copy = {
                                                binaryOutput ->
                                            copyChapterImage(
                                                reference,
                                                block,
                                                binaryOutput
                                            )
                                        }
                                    )
                            }
                        }
                    }

                writer.append(
                    "    </section>\n"
                )

                onProgress(
                    LocalExportProgress(
                        completedChapters =
                            chapterIndex + 1,
                        totalChapters =
                            book.chapters.size
                    )
                )
            }

        writer.append(
            "  </body>\n"
        )
        writer.flush()

        binaries.forEach {
                binary ->
            writer.append(
                "  <binary id=\""
            )
            writer.append(
                escapeXml(
                    binary.id
                )
            )
            writer.append(
                "\" content-type=\""
            )
            writer.append(
                binary.contentType
            )
            writer.append(
                "\">"
            )
            writer.flush()

            Base64.getMimeEncoder(
                76,
                "\n".toByteArray(
                    Charsets.US_ASCII
                )
            )
                .wrap(
                    NonClosingOutputStream(
                        output
                    )
                )
                .use {
                    binary.copy(it)
                }

            writer.append(
                "</binary>\n"
            )
        }

        writer.append(
            "</FictionBook>\n"
        )
        writer.flush()
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

    private fun escapeXml(
        value: String
    ): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun imageMediaType(
        extension: String
    ): String? =
        when (
            extension.lowercase()
        ) {
            "jpg",
            "jpeg" ->
                "image/jpeg"

            "png" ->
                "image/png"

            "webp" ->
                "image/webp"

            "gif" ->
                "image/gif"

            else -> null
        }

    private data class Fb2Binary(
        val id: String,
        val contentType: String,
        val copy: (
            OutputStream
        ) -> Unit
    )

    private class NonClosingOutputStream(
        output: OutputStream
    ) : FilterOutputStream(output) {
        override fun close() {
            flush()
        }
    }
}
