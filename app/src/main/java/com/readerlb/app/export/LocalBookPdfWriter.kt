package com.readerlb.app.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.OutputStream
import kotlin.math.min

class LocalBookPdfWriter(
    private val context: Context
) {

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
        val document =
            PdfDocument()
        val paginator =
            PdfPaginator(document)

        try {
            if (
                book.coverName != null &&
                copyCover != null
            ) {
                drawCover(
                    paginator =
                        paginator,
                    copy = copyCover
                )
            }

            drawTitlePage(
                paginator =
                    paginator,
                book = book
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
                    checkInterrupted()

                    val chapter =
                        readChapter(
                            reference
                        )

                    paginator.ensureRoom(
                        72f
                    )
                    drawText(
                        paginator =
                            paginator,
                        text =
                            chapterHeading(
                                chapter
                            ),
                        paint =
                            headingPaint,
                        alignment =
                            Layout.Alignment
                                .ALIGN_CENTER,
                        spaceAfter = 18f
                    )

                    chapter.blocks.forEach {
                            block ->
                        checkInterrupted()

                        when (block) {
                            is LocalExportBlock
                                .Paragraph -> {
                                drawText(
                                    paginator =
                                        paginator,
                                    text =
                                        block.text,
                                    paint =
                                        bodyPaint,
                                    alignment =
                                        if (
                                            block.centered
                                        ) {
                                            Layout.Alignment
                                                .ALIGN_CENTER
                                        } else {
                                            Layout.Alignment
                                                .ALIGN_NORMAL
                                        },
                                    spaceAfter =
                                        8f
                                )
                            }

                            is LocalExportBlock
                                .Quote -> {
                                block.lines
                                    .forEach {
                                            line ->
                                        drawText(
                                            paginator =
                                                paginator,
                                            text =
                                                line,
                                            paint =
                                                quotePaint,
                                            alignment =
                                                Layout.Alignment
                                                    .ALIGN_NORMAL,
                                            horizontalInset =
                                                18f,
                                            spaceAfter =
                                                5f
                                        )
                                    }
                                paginator.y +=
                                    5f
                            }

                            LocalExportBlock
                                .HorizontalRule -> {
                                drawRule(
                                    paginator
                                )
                            }

                            is LocalExportBlock
                                .Image -> {
                                drawCopiedImage(
                                    paginator =
                                        paginator,
                                    prefix =
                                        "chapter",
                                    copy = {
                                            imageOutput ->
                                        copyChapterImage(
                                            reference,
                                            block,
                                            imageOutput
                                        )
                                    }
                                )
                            }
                        }
                    }

                    paginator.y += 14f

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

            paginator.finishCurrentPage()
            document.writeTo(
                output
            )
        } finally {
            paginator.finishCurrentPage()
            document.close()
        }
    }

    private fun drawCover(
        paginator: PdfPaginator,
        copy: (
            OutputStream
        ) -> Unit
    ) {
        val bitmap =
            decodeCopiedImage(
                prefix = "cover",
                copy = copy,
                maxWidth =
                    PAGE_WIDTH,
                maxHeight =
                    PAGE_HEIGHT
            )

        try {
            paginator.newPage()
            val canvas =
                requireNotNull(
                    paginator.canvas
                )
            val maxWidth =
                PAGE_WIDTH -
                    COVER_MARGIN * 2f
            val maxHeight =
                PAGE_HEIGHT -
                    COVER_MARGIN * 2f
            val scale =
                min(
                    maxWidth /
                        bitmap.width,
                    maxHeight /
                        bitmap.height
                )
            val width =
                bitmap.width *
                    scale
            val height =
                bitmap.height *
                    scale
            val left =
                (PAGE_WIDTH -
                    width) / 2f
            val top =
                (PAGE_HEIGHT -
                    height) / 2f

            canvas.drawBitmap(
                bitmap,
                null,
                RectF(
                    left,
                    top,
                    left + width,
                    top + height
                ),
                imagePaint
            )
            paginator.finishCurrentPage()
        } finally {
            bitmap.recycle()
        }
    }

    private fun drawTitlePage(
        paginator: PdfPaginator,
        book: LocalExportBook
    ) {
        paginator.newPage()
        paginator.y =
            PAGE_HEIGHT *
                0.28f

        drawText(
            paginator =
                paginator,
            text = book.title,
            paint = titlePaint,
            alignment =
                Layout.Alignment
                    .ALIGN_CENTER,
            spaceAfter = 16f
        )

        if (
            book.author.isNotBlank()
        ) {
            drawText(
                paginator =
                    paginator,
                text = book.author,
                paint = authorPaint,
                alignment =
                    Layout.Alignment
                        .ALIGN_CENTER,
                spaceAfter = 20f
            )
        }

        if (
            book.description.isNotBlank()
        ) {
            paginator.y +=
                22f
            drawText(
                paginator =
                    paginator,
                text =
                    book.description,
                paint = bodyPaint,
                alignment =
                    Layout.Alignment
                        .ALIGN_NORMAL,
                spaceAfter = 0f
            )
        }

        paginator.finishCurrentPage()
    }

    private fun drawText(
        paginator: PdfPaginator,
        text: String,
        paint: TextPaint,
        alignment: Layout.Alignment,
        horizontalInset: Float = 0f,
        spaceAfter: Float
    ) {
        var remaining =
            text.trim()
        if (
            remaining.isBlank()
        ) {
            return
        }

        val width =
            (
                CONTENT_WIDTH -
                    horizontalInset * 2f
                )
                .toInt()
                .coerceAtLeast(1)

        while (
            remaining.isNotEmpty()
        ) {
            checkInterrupted()
            paginator.ensurePage()

            val available =
                CONTENT_BOTTOM -
                    paginator.y

            if (
                available <
                paint.textSize *
                    1.8f
            ) {
                paginator.newPage()
                continue
            }

            val layout =
                textLayout(
                    text = remaining,
                    paint = paint,
                    width = width,
                    alignment =
                        alignment
                )

            if (
                layout.height <=
                available
            ) {
                drawLayout(
                    paginator =
                        paginator,
                    layout = layout,
                    inset =
                        horizontalInset
                )
                paginator.y +=
                    layout.height +
                        spaceAfter
                return
            }

            var fittingLines = 0
            for (
                line
                in 0 until layout.lineCount
            ) {
                if (
                    layout.getLineBottom(
                        line
                    ) <= available
                ) {
                    fittingLines =
                        line + 1
                } else {
                    break
                }
            }

            if (
                fittingLines == 0
            ) {
                paginator.newPage()
                continue
            }

            val end =
                layout.getLineEnd(
                    fittingLines - 1
                )
                    .coerceAtLeast(1)
            val part =
                remaining
                    .substring(
                        0,
                        end
                    )
            val partLayout =
                textLayout(
                    text = part,
                    paint = paint,
                    width = width,
                    alignment =
                        alignment
                )

            drawLayout(
                paginator =
                    paginator,
                layout = partLayout,
                inset =
                    horizontalInset
            )
            remaining =
                remaining
                    .substring(
                        end
                    )
                    .trimStart()
            paginator.newPage()
        }
    }

    private fun drawLayout(
        paginator: PdfPaginator,
        layout: StaticLayout,
        inset: Float
    ) {
        val canvas =
            requireNotNull(
                paginator.canvas
            )
        val save =
            canvas.save()
        canvas.translate(
            CONTENT_LEFT +
                inset,
            paginator.y
        )
        layout.draw(canvas)
        canvas.restoreToCount(save)
    }

    private fun drawRule(
        paginator: PdfPaginator
    ) {
        paginator.ensureRoom(
            24f
        )
        val canvas =
            requireNotNull(
                paginator.canvas
            )
        val y =
            paginator.y +
                8f
        canvas.drawLine(
            CONTENT_LEFT +
                20f,
            y,
            CONTENT_RIGHT -
                20f,
            y,
            rulePaint
        )
        paginator.y +=
            22f
    }

    private fun drawCopiedImage(
        paginator: PdfPaginator,
        prefix: String,
        copy: (
            OutputStream
        ) -> Unit
    ) {
        val bitmap =
            decodeCopiedImage(
                prefix = prefix,
                copy = copy,
                maxWidth =
                    CONTENT_WIDTH
                        .toInt(),
                maxHeight =
                    CONTENT_HEIGHT
                        .toInt()
            )

        try {
            var scale =
                min(
                    CONTENT_WIDTH /
                        bitmap.width,
                    CONTENT_HEIGHT /
                        bitmap.height
                )
            scale =
                min(
                    scale,
                    1f
                )

            var width =
                bitmap.width *
                    scale
            var height =
                bitmap.height *
                    scale

            if (
                paginator.y >
                    CONTENT_TOP &&
                paginator.y +
                    height >
                    CONTENT_BOTTOM
            ) {
                paginator.newPage()
            }

            val available =
                CONTENT_BOTTOM -
                    paginator.y
            if (
                height >
                available
            ) {
                val fit =
                    available /
                        height
                width *= fit
                height *= fit
            }

            val left =
                CONTENT_LEFT +
                    (
                        CONTENT_WIDTH -
                            width
                        ) / 2f
            val top =
                paginator.y

            requireNotNull(
                paginator.canvas
            ).drawBitmap(
                bitmap,
                null,
                RectF(
                    left,
                    top,
                    left + width,
                    top + height
                ),
                imagePaint
            )

            paginator.y +=
                height +
                    12f
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeCopiedImage(
        prefix: String,
        copy: (
            OutputStream
        ) -> Unit,
        maxWidth: Int,
        maxHeight: Int
    ): Bitmap {
        val directory =
            File(
                context.cacheDir,
                "pdf-export"
            ).apply {
                require(
                    exists() ||
                        mkdirs()
                ) {
                    "Не удалось создать временную папку PDF"
                }
            }
        val file =
            File.createTempFile(
                prefix + "-",
                ".image",
                directory
            )

        try {
            file.outputStream()
                .buffered()
                .use(copy)

            val bounds =
                BitmapFactory.Options()
                    .apply {
                        inJustDecodeBounds =
                            true
                    }
            BitmapFactory.decodeFile(
                file.absolutePath,
                bounds
            )

            require(
                bounds.outWidth > 0 &&
                    bounds.outHeight > 0
            ) {
                "Не удалось прочитать иллюстрацию для PDF"
            }

            var sample = 1
            while (
                bounds.outWidth /
                    sample >
                    maxWidth * 2 ||
                bounds.outHeight /
                    sample >
                    maxHeight * 2
            ) {
                sample *= 2
            }

            val options =
                BitmapFactory.Options()
                    .apply {
                        inSampleSize =
                            sample
                        inPreferredConfig =
                            Bitmap.Config.RGB_565
                    }

            return requireNotNull(
                BitmapFactory.decodeFile(
                    file.absolutePath,
                    options
                )
            ) {
                "Не удалось декодировать иллюстрацию для PDF"
            }
        } finally {
            file.delete()
        }
    }

    private fun textLayout(
        text: String,
        paint: TextPaint,
        width: Int,
        alignment: Layout.Alignment
    ): StaticLayout =
        StaticLayout.Builder.obtain(
            text,
            0,
            text.length,
            paint,
            width
        )
            .setAlignment(
                alignment
            )
            .setIncludePad(
                false
            )
            .setLineSpacing(
                1.5f,
                1.18f
            )
            .build()

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

    private fun checkInterrupted() {
        if (
            Thread.currentThread()
                .isInterrupted
        ) {
            throw InterruptedException(
                "Экспорт PDF отменён"
            )
        }
    }

    private inner class PdfPaginator(
        private val document: PdfDocument
    ) {
        var page:
            PdfDocument.Page? =
            null
            private set
        var pageNumber = 0
            private set
        var y: Float =
            CONTENT_TOP

        val canvas
            get() =
                page?.canvas

        fun ensurePage() {
            if (
                page == null
            ) {
                newPage()
            }
        }

        fun ensureRoom(
            minimumHeight: Float
        ) {
            ensurePage()
            if (
                y + minimumHeight >
                CONTENT_BOTTOM
            ) {
                newPage()
            }
        }

        fun newPage() {
            finishCurrentPage()
            pageNumber += 1
            page =
                document.startPage(
                    PdfDocument.PageInfo
                        .Builder(
                            PAGE_WIDTH,
                            PAGE_HEIGHT,
                            pageNumber
                        )
                        .create()
                )
            y = CONTENT_TOP
        }

        fun finishCurrentPage() {
            page?.let {
                document.finishPage(
                    it
                )
            }
            page = null
        }
    }

    private companion object {
        const val PAGE_WIDTH =
            306
        const val PAGE_HEIGHT =
            544

        const val CONTENT_LEFT =
            28f
        const val CONTENT_RIGHT =
            278f
        const val CONTENT_TOP =
            30f
        const val CONTENT_BOTTOM =
            506f
        const val CONTENT_WIDTH =
            CONTENT_RIGHT -
                CONTENT_LEFT
        const val CONTENT_HEIGHT =
            CONTENT_BOTTOM -
                CONTENT_TOP
        const val COVER_MARGIN =
            18f

        val bodyPaint =
            TextPaint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                textSize = 11.5f
                typeface =
                    Typeface.SERIF
                color =
                    android.graphics
                        .Color.BLACK
            }

        val quotePaint =
            TextPaint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                textSize = 11.2f
                typeface =
                    Typeface.create(
                        Typeface.SERIF,
                        Typeface.ITALIC
                    )
                color =
                    android.graphics
                        .Color.DKGRAY
            }

        val headingPaint =
            TextPaint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                textSize = 17f
                typeface =
                    Typeface.create(
                        Typeface.SERIF,
                        Typeface.BOLD
                    )
                color =
                    android.graphics
                        .Color.BLACK
            }

        val titlePaint =
            TextPaint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                textSize = 20f
                typeface =
                    Typeface.create(
                        Typeface.SERIF,
                        Typeface.BOLD
                    )
                color =
                    android.graphics
                        .Color.BLACK
            }

        val authorPaint =
            TextPaint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                textSize = 12.5f
                typeface =
                    Typeface.SERIF
                color =
                    android.graphics
                        .Color.DKGRAY
            }

        val rulePaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color =
                    android.graphics
                        .Color.GRAY
                strokeWidth = 0.8f
            }

        val imagePaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG or
                    Paint.FILTER_BITMAP_FLAG
            )
    }
}
