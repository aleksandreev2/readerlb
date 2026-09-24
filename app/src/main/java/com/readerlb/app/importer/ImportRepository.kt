package com.readerlb.app.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

class ImportRepository(private val context: Context) {

    fun displayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index)
            }
        return uri.lastPathSegment ?: "book.epub"
    }

    fun parse(
        uri: Uri,
        epubLimits: EpubImportLimits =
            EpubImportLimits.DEFAULT
    ): ParsedBook {
        val name = displayName(uri)
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "epub" ->
                EpubParser(
                    context,
                    epubLimits
                ).parse(uri, name)
            "txt" -> PlainTextParser(context).parse(uri, name.substringBeforeLast('.'))
            "zip" ->
                runCatching {
                    EpubParser(
                        context,
                        epubLimits
                    ).parse(uri, name)
                }
                .getOrElse { error("ZIP пока поддерживается только если внутри находится EPUB-структура") }
            "docx" -> error("DOCX будет добавлен следующим этапом. Сейчас используйте EPUB или TXT.")
            else -> error("Формат .$ext пока не поддерживается")
        }
    }
}
