package com.readerlb.app.export

import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class LocalBookEpubWriter {

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
        val identifier =
            UUID.nameUUIDFromBytes(
                (
                    "readerlb:" +
                        book.slugUrl +
                        ":" +
                        book.title
                    )
                    .toByteArray(
                        Charsets.UTF_8
                    )
            )
                .toString()
        val coverExtension =
            book.coverName
                ?.substringAfterLast(
                    '.',
                    ""
                )
                ?.lowercase()
                ?.takeIf {
                    mediaTypeForImage(
                        it
                    ) != null
                }
                ?.takeIf {
                    copyCover != null
                }
        val resources =
            mutableListOf<
                EpubImageResource
            >()
        val chapterItems =
            mutableListOf<
                EpubChapterItem
            >()

        ZipOutputStream(
            output.buffered()
        ).use { zip ->
            writeMimetype(zip)
            writeTextEntry(
                zip,
                "META-INF/container.xml",
                containerXml()
            )
            writeTextEntry(
                zip,
                "OEBPS/styles.css",
                defaultCss()
            )

            if (
                coverExtension != null &&
                copyCover != null
            ) {
                val path =
                    "images/cover.$coverExtension"
                writeBinaryEntry(
                    zip = zip,
                    path = "OEBPS/$path",
                    write = copyCover
                )
                resources +=
                    EpubImageResource(
                        id = "cover-image",
                        href = path,
                        mediaType =
                            requireNotNull(
                                mediaTypeForImage(
                                    coverExtension
                                )
                            ),
                        properties =
                            "cover-image"
                    )
                writeTextEntry(
                    zip,
                    "OEBPS/cover.xhtml",
                    coverXhtml(
                        imageHref = path
                    )
                )
            }

            writeTextEntry(
                zip,
                "OEBPS/title.xhtml",
                titleXhtml(book)
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
                        index,
                        reference ->
                    val chapter =
                        readChapter(
                            reference
                        )
                    val chapterNumber =
                        index + 1
                    val chapterHref =
                        "chapters/chapter-" +
                            chapterNumber +
                            ".xhtml"
                    val imageMap =
                        linkedMapOf<
                            String,
                            String
                        >()

                    chapter.blocks
                        .filterIsInstance<
                            LocalExportBlock
                                .Image
                        >()
                        .forEachIndexed {
                                imageIndex,
                                image ->
                            val extension =
                                image.extension
                                    .lowercase()
                            val mediaType =
                                mediaTypeForImage(
                                    extension
                                )
                                    ?: return@forEachIndexed
                            val href =
                                "images/chapter-" +
                                    chapterNumber +
                                    "-image-" +
                                    (imageIndex + 1) +
                                    "." +
                                    extension
                            val id =
                                "image-" +
                                    chapterNumber +
                                    "-" +
                                    (imageIndex + 1)

                            writeBinaryEntry(
                                zip = zip,
                                path =
                                    "OEBPS/$href"
                            ) { imageOutput ->
                                copyChapterImage(
                                    reference,
                                    image,
                                    imageOutput
                                )
                            }

                            imageMap[
                                image.entryName
                            ] = "../$href"
                            resources +=
                                EpubImageResource(
                                    id = id,
                                    href = href,
                                    mediaType =
                                        mediaType
                                )
                        }

                    writeTextEntry(
                        zip,
                        "OEBPS/$chapterHref",
                        chapterXhtml(
                            chapter = chapter,
                            imageHrefs =
                                imageMap
                        )
                    )
                    chapterItems +=
                        EpubChapterItem(
                            id =
                                "chapter-" +
                                    chapterNumber,
                            href =
                                chapterHref,
                            title =
                                chapterHeading(
                                    chapter
                                )
                        )

                    onProgress(
                        LocalExportProgress(
                            completedChapters =
                                chapterNumber,
                            totalChapters =
                                book.chapters
                                    .size
                        )
                    )
                }

            writeTextEntry(
                zip,
                "OEBPS/nav.xhtml",
                navXhtml(
                    chapterItems
                )
            )
            writeTextEntry(
                zip,
                "OEBPS/content.opf",
                packageDocument(
                    book = book,
                    identifier =
                        identifier,
                    chapters =
                        chapterItems,
                    images =
                        resources,
                    hasCoverPage =
                        coverExtension !=
                            null
                )
            )
        }
    }

    private fun writeMimetype(
        zip: ZipOutputStream
    ) {
        val bytes =
            "application/epub+zip"
                .toByteArray(
                    StandardCharsets.US_ASCII
                )
        val crc =
            CRC32().apply {
                update(bytes)
            }

        val entry =
            ZipEntry("mimetype")
                .apply {
                    method =
                        ZipEntry.STORED
                    size =
                        bytes.size.toLong()
                    compressedSize =
                        bytes.size.toLong()
                    this.crc =
                        crc.value
                }

        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun writeTextEntry(
        zip: ZipOutputStream,
        path: String,
        text: String
    ) {
        val bytes =
            text.toByteArray(
                Charsets.UTF_8
            )

        zip.putNextEntry(
            ZipEntry(path)
        )
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun writeBinaryEntry(
        zip: ZipOutputStream,
        path: String,
        write: (
            OutputStream
        ) -> Unit
    ) {
        zip.putNextEntry(
            ZipEntry(path)
        )
        write(zip)
        zip.closeEntry()
    }

    private fun containerXml(): String =
        """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>
"""

    private fun defaultCss(): String =
        """body {
  font-family: serif;
  line-height: 1.5;
  margin: 5%;
}
h1, h2 {
  text-align: center;
  page-break-after: avoid;
}
p {
  margin: 0 0 0.8em 0;
}
p.centered {
  text-align: center;
}
blockquote {
  margin: 1em 8%;
}
img.illustration {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 1em auto;
}
.cover {
  margin: 0;
  padding: 0;
  text-align: center;
}
.cover img {
  max-width: 100%;
  max-height: 100vh;
}
.description {
  margin-top: 2em;
}
"""

    private fun coverXhtml(
        imageHref: String
    ): String =
        """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" lang="ru" xml:lang="ru">
<head>
  <title>Обложка</title>
  <link rel="stylesheet" type="text/css" href="styles.css"/>
</head>
<body class="cover">
  <img src="${escapeXml(imageHref)}" alt="Обложка"/>
</body>
</html>
"""

    private fun titleXhtml(
        book: LocalExportBook
    ): String =
        buildString {
            append(
                """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" lang="ru" xml:lang="ru">
<head>
  <title>"""
            )
            append(
                escapeXml(
                    book.title
                )
            )
            append(
                """</title>
  <link rel="stylesheet" type="text/css" href="styles.css"/>
</head>
<body>
  <h1>"""
            )
            append(
                escapeXml(
                    book.title
                )
            )
            append("</h1>")

            if (
                book.author.isNotBlank()
            ) {
                append("<p class=\"centered\">")
                append(
                    escapeXml(
                        book.author
                    )
                )
                append("</p>")
            }

            if (
                book.description.isNotBlank()
            ) {
                append("<p class=\"description\">")
                append(
                    escapeXml(
                        book.description
                    )
                )
                append("</p>")
            }

            append(
                """
</body>
</html>
"""
            )
        }

    private fun chapterXhtml(
        chapter: LocalExportChapter,
        imageHrefs: Map<String, String>
    ): String =
        buildString {
            append(
                """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" lang="ru" xml:lang="ru">
<head>
  <title>"""
            )
            append(
                escapeXml(
                    chapterHeading(
                        chapter
                    )
                )
            )
            append(
                """</title>
  <link rel="stylesheet" type="text/css" href="../styles.css"/>
</head>
<body>
  <h2>"""
            )
            append(
                escapeXml(
                    chapterHeading(
                        chapter
                    )
                )
            )
            append("</h2>")

            chapter.blocks.forEach {
                    block ->
                when (block) {
                    is LocalExportBlock
                        .Paragraph -> {
                        append(
                            if (
                                block.centered
                            ) {
                                "<p class=\"centered\">"
                            } else {
                                "<p>"
                            }
                        )
                        append(
                            escapeXmlWithBreaks(
                                block.text
                            )
                        )
                        append("</p>")
                    }

                    is LocalExportBlock
                        .Quote -> {
                        append(
                            "<blockquote>"
                        )
                        block.lines
                            .forEach {
                                    line ->
                                append("<p>")
                                append(
                                    escapeXmlWithBreaks(
                                        line
                                    )
                                )
                                append("</p>")
                            }
                        append(
                            "</blockquote>"
                        )
                    }

                    LocalExportBlock
                        .HorizontalRule -> {
                        append("<hr/>")
                    }

                    is LocalExportBlock
                        .Image -> {
                        val href =
                            imageHrefs[
                                block.entryName
                            ] ?: return@forEach
                        append(
                            "<img class=\"illustration\" src=\""
                        )
                        append(
                            escapeXml(
                                href
                            )
                        )
                        append("\" alt=\"")
                        append(
                            escapeXml(
                                block.description
                                    ?: "Иллюстрация"
                            )
                        )
                        append("\"/>")
                    }
                }
            }

            append(
                """
</body>
</html>
"""
            )
        }

    private fun navXhtml(
        chapters: List<EpubChapterItem>
    ): String =
        buildString {
            append(
                """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" lang="ru" xml:lang="ru">
<head>
  <title>Оглавление</title>
</head>
<body>
  <nav epub:type="toc" id="toc">
    <h1>Оглавление</h1>
    <ol>
      <li><a href="title.xhtml">О книге</a></li>
"""
            )

            chapters.forEach {
                append("      <li><a href=\"")
                append(
                    escapeXml(
                        it.href
                    )
                )
                append("\">")
                append(
                    escapeXml(
                        it.title
                    )
                )
                append("</a></li>\n")
            }

            append(
                """    </ol>
  </nav>
</body>
</html>
"""
            )
        }

    private fun packageDocument(
        book: LocalExportBook,
        identifier: String,
        chapters: List<EpubChapterItem>,
        images: List<EpubImageResource>,
        hasCoverPage: Boolean
    ): String =
        buildString {
            append(
                """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="book-id" xml:lang="ru">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:identifier id="book-id">urn:uuid:"""
            )
            append(
                escapeXml(
                    identifier
                )
            )
            append("</dc:identifier>\n")
            append("    <dc:title>")
            append(
                escapeXml(
                    book.title
                )
            )
            append("</dc:title>\n")
            append(
                "    <dc:language>ru</dc:language>\n"
            )

            if (
                book.author.isNotBlank()
            ) {
                append(
                    "    <dc:creator>"
                )
                append(
                    escapeXml(
                        book.author
                    )
                )
                append(
                    "</dc:creator>\n"
                )
            }

            if (
                book.description.isNotBlank()
            ) {
                append(
                    "    <dc:description>"
                )
                append(
                    escapeXml(
                        book.description
                    )
                )
                append(
                    "</dc:description>\n"
                )
            }

            append(
                "    <meta property=\"dcterms:modified\">"
            )
            append(
                Instant.now()
                    .truncatedTo(
                        ChronoUnit.SECONDS
                    )
                    .toString()
            )
            append("</meta>\n")
            append("  </metadata>\n")
            append("  <manifest>\n")
            append(
                "    <item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/>\n"
            )
            append(
                "    <item id=\"css\" href=\"styles.css\" media-type=\"text/css\"/>\n"
            )
            append(
                "    <item id=\"title\" href=\"title.xhtml\" media-type=\"application/xhtml+xml\"/>\n"
            )

            if (hasCoverPage) {
                append(
                    "    <item id=\"cover-page\" href=\"cover.xhtml\" media-type=\"application/xhtml+xml\"/>\n"
                )
            }

            chapters.forEach {
                append(
                    "    <item id=\""
                )
                append(it.id)
                append(
                    "\" href=\""
                )
                append(
                    escapeXml(
                        it.href
                    )
                )
                append(
                    "\" media-type=\"application/xhtml+xml\"/>\n"
                )
            }

            images.forEach {
                append(
                    "    <item id=\""
                )
                append(it.id)
                append(
                    "\" href=\""
                )
                append(
                    escapeXml(
                        it.href
                    )
                )
                append(
                    "\" media-type=\""
                )
                append(
                    it.mediaType
                )
                append("\"")
                if (
                    it.properties != null
                ) {
                    append(
                        " properties=\""
                    )
                    append(
                        it.properties
                    )
                    append("\"")
                }
                append("/>\n")
            }

            append("  </manifest>\n")
            append("  <spine>\n")
            if (hasCoverPage) {
                append(
                    "    <itemref idref=\"cover-page\"/>\n"
                )
            }
            append(
                "    <itemref idref=\"title\"/>\n"
            )
            chapters.forEach {
                append(
                    "    <itemref idref=\""
                )
                append(it.id)
                append("\"/>\n")
            }
            append(
                """  </spine>
</package>
"""
            )
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

    private fun escapeXmlWithBreaks(
        value: String
    ): String =
        escapeXml(value)
            .replace(
                "\n",
                "<br/>"
            )

    private fun mediaTypeForImage(
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

            "svg" ->
                "image/svg+xml"

            else -> null
        }

    private data class EpubChapterItem(
        val id: String,
        val href: String,
        val title: String
    )

    private data class EpubImageResource(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: String? = null
    )
}
