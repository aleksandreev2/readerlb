package com.readerlb.app.storage

import android.content.Context
import android.net.Uri
import android.util.JsonReader
import androidx.core.content.FileProvider
import com.readerlb.app.BuildConfig
import com.readerlb.app.shizuku.RanobeLibPrivilegedFiles
import org.json.JSONObject
import java.io.File

class ShizukuRanobeLibLibraryScanner(
    private val context: Context,
    private val bridge: RanobeLibPrivilegedFiles
) {
    fun scan(
        onProgress: (
            completed: Int,
            total: Int
        ) -> Unit = { _, _ -> }
    ): LocalLibrarySnapshot {
        val directories =
            bridge.listNames()
                .filter {
                    bridge.isDirectory(it)
                }

        var completed = 0
        var skipped = 0
        val items =
            ArrayList<LocalLibraryItem>(
                directories.size
            )

        onProgress(
            0,
            directories.size
        )

        directories.forEach {
                directoryName ->
            val item =
                runCatching {
                    readTitle(
                        directoryName
                    )
                }.getOrElse {
                    skipped += 1
                    null
                }

            if (item != null) {
                items += item
            }

            completed += 1
            onProgress(
                completed,
                directories.size
            )
        }

        items.sortWith(
            compareByDescending<
                LocalLibraryItem
            > {
                it.writeTime
            }.thenBy {
                it.title.lowercase()
            }
        )

        return LocalLibrarySnapshot(
            items = items,
            skippedTitles = skipped
        )
    }

    private fun readTitle(
        directoryName: String
    ): LocalLibraryItem? {
        val names =
            bridge.listNames(
                directoryName
            )
                .toSet()

        if (
            "info.json" !in names ||
            "chapters.json" !in names
        ) {
            return null
        }

        val infoText =
            bridge.readBytes(
                relativePath =
                    "$directoryName/info.json",
                limitBytes =
                    MAX_INFO_JSON_BYTES
                        .toLong()
            )
                .toString(
                    Charsets.UTF_8
                )

        val info =
            JSONObject(infoText)
        val media =
            info.getJSONObject(
                "media"
            )

        val chapters =
            bridge.openInput(
                "$directoryName/chapters.json"
            )
                .bufferedReader(
                    Charsets.UTF_8
                )
                .use {
                    reader ->
                    readLocalChapterSummary(
                        JsonReader(reader)
                    )
                }

        val title =
            sequenceOf(
                media.optString(
                    "rusName"
                ),
                media.optString(
                    "name"
                ),
                media.optString(
                    "engName"
                ),
                directoryName
            )
                .map(String::trim)
                .firstOrNull(
                    String::isNotBlank
                )
                ?: error(
                    "У тайтла нет названия"
                )

        val slugUrl =
            media.optString(
                "slugUrl"
            )
                .trim()
                .ifBlank {
                    directoryName
                }

        val coverName =
            resolveLocalCoverName(
                imageUrl =
                    media.optString(
                        "imageUrl"
                    ),
                availableFileNames =
                    names
            )

        val coverUri =
            coverName?.let {
                cacheCover(
                    folderName =
                        directoryName,
                    coverName = it
                )
            }

        return LocalLibraryItem(
            title = title,
            slugUrl = slugUrl,
            chapterCount =
                chapters.chapterCount,
            firstChapter =
                chapters.firstChapter,
            lastChapter =
                chapters.lastChapter,
            coverUri = coverUri,
            writeTime =
                info.optLong(
                    "writeTime",
                    0L
                ),
            createdByReaderLB =
                chapters.createdByReaderLB,
            folderName =
                directoryName
        )
    }

    private fun cacheCover(
        folderName: String,
        coverName: String
    ): Uri {
        val extension =
            coverName
                .substringAfterLast(
                    '.',
                    "img"
                )
                .lowercase()
                .take(8)
        val safeKey =
            (
                folderName +
                    "|" +
                    coverName
                )
                .hashCode()
                .toUInt()
                .toString(16)

        val directory =
            File(
                context.cacheDir,
                "shizuku_covers"
            ).apply {
                require(
                    isDirectory ||
                        mkdirs()
                ) {
                    "Не удалось подготовить кэш обложек"
                }
            }

        val target =
            File(
                directory,
                "$safeKey.$extension"
            )

        bridge.openInput(
            "$folderName/$coverName"
        ).use {
                input ->
            target.outputStream()
                .buffered()
                .use {
                    output ->
                    input.copyTo(
                        output
                    )
                }
        }

        require(
            target.isFile &&
                target.length() > 0L
        ) {
            "Не удалось скопировать обложку"
        }

        return FileProvider.getUriForFile(
            context,
            BuildConfig
                .APPLICATION_ID +
                ".files",
            target
        )
    }
}

