package com.readerlb.app.importer

import android.content.Context
import android.net.Uri
import java.io.File

class EpubParser(
    private val context: Context
) {

    private val archiveParser =
        EpubArchiveParser()

    fun parse(
        uri: Uri,
        sourceName: String? = null
    ): ParsedBook {
        cleanupStaleAssetDirectories()

        val sourceTemp = File.createTempFile(
            "readerlb_",
            ".epub",
            context.cacheDir
        )
        val assetDirectory = File(
            context.cacheDir,
            ASSET_DIRECTORY_PREFIX +
                System.nanoTime()
        )

        require(assetDirectory.mkdirs()) {
            "Не удалось создать временную папку EPUB"
        }

        return try {
            context.contentResolver
                .openInputStream(uri)
                .use { input ->
                    requireNotNull(input) {
                        "Не удалось открыть файл"
                    }
                    sourceTemp.outputStream()
                        .buffered()
                        .use { output ->
                            input.copyTo(output)
                        }
                }

            archiveParser.parse(
                file = sourceTemp,
                sourceName = sourceName,
                assetDirectory =
                    assetDirectory
            )
        } catch (throwable: Throwable) {
            assetDirectory.deleteRecursively()
            throw throwable
        } finally {
            sourceTemp.delete()
        }
    }

    private fun cleanupStaleAssetDirectories() {
        cleanupStaleEpubAssetDirectories(
            cacheDir = context.cacheDir,
            nowMillis =
                System.currentTimeMillis()
        )
    }

    private companion object {
        const val ASSET_DIRECTORY_PREFIX =
            EPUB_ASSET_DIRECTORY_PREFIX
    }
}

internal const val EPUB_ASSET_DIRECTORY_PREFIX =
    "readerlb_epub_assets_"

internal const val EPUB_ASSET_MAX_AGE_MILLIS =
    24L * 60L * 60L * 1000L

internal fun cleanupStaleEpubAssetDirectories(
    cacheDir: File,
    nowMillis: Long
): Int {
    val cutoff =
        nowMillis -
            EPUB_ASSET_MAX_AGE_MILLIS
    var removed = 0

    cacheDir
        .listFiles()
        .orEmpty()
        .asSequence()
        .filter(File::isDirectory)
        .filter {
            it.name.startsWith(
                EPUB_ASSET_DIRECTORY_PREFIX
            )
        }
        .filter {
            it.lastModified() in
                1 until cutoff
        }
        .forEach { directory ->
            if (
                runCatching {
                    directory.deleteRecursively()
                }.getOrDefault(false)
            ) {
                removed += 1
            }
        }

    return removed
}
