package com.readerlb.app.importer

import android.content.Context
import android.net.Uri
import java.io.File

class EpubParser(private val context: Context) {

    private val archiveParser = EpubArchiveParser()

    fun parse(uri: Uri, sourceName: String? = null): ParsedBook {
        val temp = File.createTempFile("readerlb_", ".epub", context.cacheDir)
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Не удалось открыть файл" }
            temp.outputStream().use(input::copyTo)
        }

        return try {
            archiveParser.parse(temp, sourceName)
        } finally {
            temp.delete()
        }
    }
}
