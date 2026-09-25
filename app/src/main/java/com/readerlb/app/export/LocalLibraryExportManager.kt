package com.readerlb.app.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.readerlb.app.storage.LocalLibraryItem

data class ExportedLocalBookFile(
    val uri: Uri,
    val displayName: String,
    val chapterCount: Int,
    val format: String
)

class LocalLibraryExportManager(
    private val context: Context
) {
    private val reader =
        RanobeLibLocalBookReader(
            context
        )
    private val txtWriter =
        LocalBookTxtWriter()

    fun exportTxt(
        treeUri: Uri,
        item: LocalLibraryItem,
        onProgress: (
            LocalExportProgress
        ) -> Unit = {}
    ): ExportedLocalBookFile {
        val opened =
            reader.open(
                treeUri = treeUri,
                item = item
            )
        val displayName =
            safeExportFileName(
                opened.book.title
            ) + ".txt"

        return writeDownload(
            displayName =
                displayName,
            mimeType = "text/plain"
        ) { output ->
            txtWriter.write(
                book = opened.book,
                output = output,
                readChapter = {
                        reference ->
                    reader.readChapter(
                        opened = opened,
                        reference =
                            reference
                    )
                },
                onProgress =
                    onProgress
            )

            ExportedLocalBookFile(
                uri = Uri.EMPTY,
                displayName =
                    displayName,
                chapterCount =
                    opened.book
                        .chapters.size,
                format = "TXT"
            )
        }
    }

    private fun writeDownload(
        displayName: String,
        mimeType: String,
        block: (
            java.io.OutputStream
        ) -> ExportedLocalBookFile
    ): ExportedLocalBookFile {
        val resolver =
            context.contentResolver
        val values =
            ContentValues()
                .apply {
                    put(
                        MediaStore
                            .MediaColumns
                            .DISPLAY_NAME,
                        displayName
                    )
                    put(
                        MediaStore
                            .MediaColumns
                            .MIME_TYPE,
                        mimeType
                    )
                    put(
                        MediaStore
                            .MediaColumns
                            .RELATIVE_PATH,
                        Environment
                            .DIRECTORY_DOWNLOADS +
                            "/ReaderLB"
                    )
                    put(
                        MediaStore
                            .MediaColumns
                            .IS_PENDING,
                        1
                    )
                }

        val uri =
            resolver.insert(
                MediaStore
                    .Downloads
                    .EXTERNAL_CONTENT_URI,
                values
            ) ?: error(
                "Android не создал файл экспорта"
            )

        try {
            val output =
                resolver
                    .openOutputStream(
                        uri,
                        "w"
                    ) ?: error(
                    "Android не дал записать файл экспорта"
                )

            val result =
                output.buffered().use(
                    block
                )

            val published =
                ContentValues()
                    .apply {
                        put(
                            MediaStore
                                .MediaColumns
                                .IS_PENDING,
                            0
                        )
                    }

            resolver.update(
                uri,
                published,
                null,
                null
            )

            return result.copy(
                uri = uri
            )
        } catch (throwable: Throwable) {
            resolver.delete(
                uri,
                null,
                null
            )
            throw throwable
        }
    }
}

internal fun safeExportFileName(
    title: String
): String {
    val cleaned =
        title
            .replace(
                Regex(
                    """[\\/:*?"<>|]"""
                ),
                "_"
            )
            .replace(
                Regex(
                    """\s+"""
                ),
                " "
            )
            .trim()
            .trimEnd('.', ' ')
            .take(120)

    return cleaned
        .ifBlank {
            "ReaderLB-book"
        }
}
