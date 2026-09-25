package com.readerlb.app.export

import android.net.Uri
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalExportMediaStoreAndroidTest {

    @Test
    fun publishesCompletedDownloadAndMakesItReadable() {
        val context =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        val resolver =
            context.contentResolver
        val name =
            "readerlb-export-success-" +
                System.nanoTime() +
                ".txt"

        val result =
            writePendingDownload(
                resolver = resolver,
                displayName = name,
                mimeType = "text/plain"
            ) { output ->
                output.write(
                    "готово"
                        .toByteArray(
                            Charsets.UTF_8
                        )
                )
                ExportedLocalBookFile(
                    uri = Uri.EMPTY,
                    displayName = name,
                    chapterCount = 1,
                    format =
                        LocalBookExportFormat
                            .TXT
                )
            }

        try {
            assertEquals(
                "готово",
                resolver
                    .openInputStream(
                        result.uri
                    )
                    ?.bufferedReader(
                        Charsets.UTF_8
                    )
                    ?.use {
                        it.readText()
                    }
            )

            resolver.query(
                result.uri,
                arrayOf(
                    MediaStore
                        .MediaColumns
                        .IS_PENDING
                ),
                null,
                null,
                null
            )?.use {
                assertTrue(
                    it.moveToFirst()
                )
                assertEquals(
                    0,
                    it.getInt(0)
                )
            } ?: fail(
                "MediaStore не вернул опубликованный файл"
            )
        } finally {
            resolver.delete(
                result.uri,
                null,
                null
            )
        }
    }

    @Test
    fun removesPendingDownloadAfterWriterFailure() {
        val context =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        val resolver =
            context.contentResolver
        val name =
            "readerlb-export-failure-" +
                System.nanoTime() +
                ".txt"

        try {
            writePendingDownload(
                resolver = resolver,
                displayName = name,
                mimeType = "text/plain"
            ) { output ->
                output.write(
                    "частичный файл"
                        .toByteArray(
                            Charsets.UTF_8
                        )
                )
                error(
                    "Искусственная ошибка записи"
                )
            }
            fail(
                "Ожидалась ошибка записи"
            )
        } catch (
            expected:
                IllegalStateException
        ) {
            assertTrue(
                expected.message
                    .orEmpty()
                    .contains(
                        "Искусственная ошибка"
                    )
            )
        }

        val count =
            resolver.query(
                MediaStore
                    .Downloads
                    .EXTERNAL_CONTENT_URI,
                arrayOf(
                    MediaStore
                        .MediaColumns
                        ._ID
                ),
                MediaStore
                    .MediaColumns
                    .DISPLAY_NAME +
                    " = ?",
                arrayOf(name),
                null
            )?.use {
                it.count
            } ?: 0

        assertEquals(
            0,
            count
        )
    }
}
