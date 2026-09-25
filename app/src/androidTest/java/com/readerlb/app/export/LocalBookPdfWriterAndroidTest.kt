package com.readerlb.app.export

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalBookPdfWriterAndroidTest {

    @Test
    fun writesMobilePdfAndPaginatesLongText() {
        val context =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        val output =
            ByteArrayOutputStream()
        val progress =
            mutableListOf<
                Pair<Int, Int>
            >()
        val longParagraph =
            (1..220)
                .joinToString(" ") {
                    "Предложение номер $it."
                }

        val book =
            LocalExportBook(
                title = "PDF тест",
                author = "ReaderLB",
                description =
                    "Проверка мобильной пагинации.",
                languageLabel = "",
                slugUrl = "pdf-test",
                coverName = null,
                chapters = listOf(
                    LocalExportChapterRef(
                        number = "1",
                        title = "Длинная глава",
                        volume = "1",
                        chapterId = 1L,
                        archiveName =
                            "v1-n1-1.zip"
                    )
                )
            )

        LocalBookPdfWriter(
            context
        ).write(
            book = book,
            output = output,
            readChapter = {
                LocalExportChapter(
                    number = "1",
                    title =
                        "Длинная глава",
                    blocks = listOf(
                        LocalExportBlock
                            .Paragraph(
                                longParagraph
                            ),
                        LocalExportBlock
                            .Quote(
                                listOf(
                                    "Цитата"
                                )
                            ),
                        LocalExportBlock
                            .HorizontalRule
                    )
                )
            },
            copyCover = null,
            copyChapterImage = {
                    _,
                    _,
                    _ ->
            },
            onProgress = {
                progress +=
                    it.completedChapters to
                        it.totalChapters
            }
        )

        val bytes =
            output.toByteArray()

        assertTrue(
            bytes.size > 500
        )
        assertEquals(
            "%PDF",
            bytes
                .copyOfRange(
                    0,
                    4
                )
                .toString(
                    Charsets.US_ASCII
                )
        )
        assertEquals(
            listOf(
                0 to 1,
                1 to 1
            ),
            progress
        )
    }
    @Test
    fun writesEmbeddedIllustrationWithoutKeepingBookImagesInMemory() {
        val context =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        val output =
            ByteArrayOutputStream()
        val bitmap =
            Bitmap.createBitmap(
                320,
                480,
                Bitmap.Config.ARGB_8888
            ).apply {
                eraseColor(
                    android.graphics.Color
                        .LTGRAY
                )
            }

        try {
            val book =
                LocalExportBook(
                    title =
                        "PDF с иллюстрацией",
                    author = "",
                    description = "",
                    languageLabel = "",
                    slugUrl =
                        "pdf-image-test",
                    coverName = null,
                    chapters = listOf(
                        LocalExportChapterRef(
                            number = "1",
                            title = "",
                            volume = "1",
                            chapterId = 1L,
                            archiveName =
                                "v1-n1-1.zip"
                        )
                    )
                )

            LocalBookPdfWriter(
                context
            ).write(
                book = book,
                output = output,
                readChapter = {
                    LocalExportChapter(
                        number = "1",
                        title = "",
                        blocks = listOf(
                            LocalExportBlock
                                .Image(
                                    entryName =
                                        "image.png",
                                    extension =
                                        "png",
                                    description =
                                        "Тест"
                                )
                        )
                    )
                },
                copyCover = null,
                copyChapterImage = {
                        _,
                        _,
                        imageOutput ->
                    check(
                        bitmap.compress(
                            Bitmap.CompressFormat
                                .PNG,
                            100,
                            imageOutput
                        )
                    )
                }
            )

            val bytes =
                output.toByteArray()
            assertTrue(
                bytes.size > 1_000
            )
            assertEquals(
                "%PDF",
                bytes
                    .copyOfRange(
                        0,
                        4
                    )
                    .toString(
                        Charsets.US_ASCII
                    )
            )
        } finally {
            bitmap.recycle()
        }
    }

}
