package com.readerlb.app.importer

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class RanobeLibUpdateResult(
    val addedNumbers: List<String>,
    val overlappingNumbers: List<String>,
    val mergedNumbers: List<String>,
    val totalChapterCount: Int,
    val changed: Boolean
)

/**
 * Crash-aware transaction for updating one already installed local title.
 *
 * Transaction order:
 * 1. stage new chapter ZIPs under temporary names;
 * 2. stage merged chapters/info JSON;
 * 3. publish new ZIPs;
 * 4. backup old metadata;
 * 5. publish chapters.json;
 * 6. publish info.json LAST;
 * 7. remove backups.
 *
 * If a failure occurs before commit finishes, existing metadata is restored
 * and any newly published chapter ZIPs are removed.
 */
class RanobeLibUpdateTransaction(
    private val planner: RanobeLibUpdatePlanner = RanobeLibUpdatePlanner(),
    private val nowMillis: () -> Long = System::currentTimeMillis
) {

    fun apply(
        existing: RanobeLibMutableStorage,
        incomingTitleDir: File
    ): RanobeLibUpdateResult {
        require(incomingTitleDir.isDirectory) {
            "Папка нового пакета не существует"
        }

        recoverInterruptedUpdate(existing)
        recoverMetadataIfNeeded(existing)
        cleanupStaging(existing)

        val existingInfo = readJsonObject(
            existing.readBytes(INFO)
        )
        val existingChapters = readJsonArray(
            existing.readBytes(CHAPTERS)
        )

        val incomingInfoFile = File(incomingTitleDir, INFO)
        val incomingChaptersFile = File(incomingTitleDir, CHAPTERS)

        require(incomingInfoFile.isFile) {
            "Новый пакет не содержит $INFO"
        }
        require(incomingChaptersFile.isFile) {
            "Новый пакет не содержит $CHAPTERS"
        }

        val incomingInfo = readJsonObject(
            incomingInfoFile.readBytes()
        )
        val incomingChapters = readJsonArray(
            incomingChaptersFile.readBytes()
        )

        validateSameTitleIdentity(
            existingInfo = existingInfo,
            incomingInfo = incomingInfo
        )

        val plan = planner.plan(
            existingChapters = existingChapters,
            incomingChapters = incomingChapters
        )

        if (plan.isNoOp) {
            return RanobeLibUpdateResult(
                addedNumbers = emptyList(),
                overlappingNumbers = plan.overlappingNumbers,
                mergedNumbers = plan.mergedNumbers,
                totalChapterCount = existingChapters.length(),
                changed = false
            )
        }

        val incomingZipByNumber = planner.chapterNumberToZip(
            incomingChapters
        )

        val newZipFiles = plan.addedNumbers.associateWith { number ->
            val name = incomingZipByNumber[number]
                ?: error("Для новой главы $number не найден ZIP")
            val file = File(incomingTitleDir, name)
            require(file.isFile && file.length() > 0L) {
                "ZIP новой главы $number отсутствует или пуст"
            }
            file
        }

        val existingNames = existing.names()
        newZipFiles.values.forEach { file ->
            require(file.name !in existingNames) {
                "Файл ${file.name} уже существует. " +
                    "ReaderLB не будет перезаписывать его автоматически."
            }
        }

        val mergedChapters = planner.mergeChapters(
            existingChapters = existingChapters,
            incomingChapters = incomingChapters
        )
        val mergedInfo = planner.mergeInfo(
            existingInfo = existingInfo,
            mergedChapterCount = mergedChapters.length(),
            writeTime = nowMillis()
        )

        val stagedZipNames = mutableListOf<String>()
        val publishedZipNames = mutableListOf<String>()
        var chaptersBackedUp = false
        var infoBackedUp = false
        var chaptersPublished = false
        var infoPublished = false

        try {
            newZipFiles.values.forEach { source ->
                val staged = STAGED_ZIP_PREFIX + source.name
                existing.writeBytes(
                    staged,
                    source.readBytes()
                )
                // Track the staging file immediately. Validation below can
                // fail, and rollback must still remove a corrupt temp file.
                stagedZipNames += staged

                require(
                    existing.length(staged) == source.length()
                ) {
                    "Проверка размера временной копии ${source.name} не пройдена"
                }
                require(
                    sha256(existing.readBytes(staged)) ==
                        sha256(source.readBytes())
                ) {
                    "Контрольная сумма временной копии ${source.name} не совпадает"
                }
            }

            val chaptersBytes = mergedChapters
                .toString()
                .toByteArray(Charsets.UTF_8)
            val infoBytes = mergedInfo
                .toString()
                .toByteArray(Charsets.UTF_8)

            existing.writeBytes(
                STAGED_CHAPTERS,
                chaptersBytes
            )
            existing.writeBytes(
                STAGED_INFO,
                infoBytes
            )

            requireJsonArrayFile(
                existing,
                STAGED_CHAPTERS
            )
            requireJsonObjectFile(
                existing,
                STAGED_INFO
            )

            val journal = JSONObject()
                .put(
                    "addedZipNames",
                    JSONArray(
                        newZipFiles.values
                            .map { it.name }
                    )
                )
                .put(
                    "addedNumbers",
                    JSONArray(plan.addedNumbers)
                )

            existing.writeBytes(
                JOURNAL,
                journal.toString()
                    .toByteArray(Charsets.UTF_8)
            )
            requireJsonObjectFile(
                existing,
                JOURNAL
            )

            newZipFiles.values.forEach { source ->
                val staged = STAGED_ZIP_PREFIX + source.name
                require(
                    existing.rename(
                        staged,
                        source.name
                    )
                ) {
                    "Не удалось опубликовать ${source.name}"
                }
                stagedZipNames.remove(staged)
                publishedZipNames += source.name
            }

            require(
                existing.rename(
                    CHAPTERS,
                    BACKUP_CHAPTERS
                )
            ) {
                "Не удалось создать резервную копию $CHAPTERS"
            }
            chaptersBackedUp = true

            require(
                existing.rename(
                    INFO,
                    BACKUP_INFO
                )
            ) {
                "Не удалось создать резервную копию $INFO"
            }
            infoBackedUp = true

            require(
                existing.rename(
                    STAGED_CHAPTERS,
                    CHAPTERS
                )
            ) {
                "Не удалось опубликовать $CHAPTERS"
            }
            chaptersPublished = true

            // info.json is intentionally the final visibility boundary.
            require(
                existing.rename(
                    STAGED_INFO,
                    INFO
                )
            ) {
                "Не удалось опубликовать $INFO"
            }
            infoPublished = true

            requireJsonArrayFile(
                existing,
                CHAPTERS
            )
            requireJsonObjectFile(
                existing,
                INFO
            )

            existing.delete(BACKUP_CHAPTERS)
            existing.delete(BACKUP_INFO)
            existing.delete(JOURNAL)

            return RanobeLibUpdateResult(
                addedNumbers = plan.addedNumbers,
                overlappingNumbers = plan.overlappingNumbers,
                mergedNumbers = plan.mergedNumbers,
                totalChapterCount = mergedChapters.length(),
                changed = true
            )
        } catch (throwable: Throwable) {
            rollback(
                storage = existing,
                stagedZipNames = stagedZipNames,
                publishedZipNames = publishedZipNames,
                chaptersBackedUp = chaptersBackedUp,
                infoBackedUp = infoBackedUp,
                chaptersPublished = chaptersPublished,
                infoPublished = infoPublished
            )
            throw throwable
        }
    }

    private fun validateSameTitleIdentity(
        existingInfo: JSONObject,
        incomingInfo: JSONObject
    ) {
        val existingMedia = existingInfo.optJSONObject("media")
            ?: error("Существующий info.json не содержит media")
        val incomingMedia = incomingInfo.optJSONObject("media")
            ?: error("Новый info.json не содержит media")

        val existingSlug = existingMedia
            .optString("slugUrl")
            .trim()
        val incomingSlug = incomingMedia
            .optString("slugUrl")
            .trim()

        require(
            existingSlug.isNotBlank() &&
                incomingSlug.isNotBlank() &&
                existingSlug == incomingSlug
        ) {
            "Идентификатор существующего тайтла не совпадает с новым пакетом"
        }
    }

    private fun rollback(
        storage: RanobeLibMutableStorage,
        stagedZipNames: List<String>,
        publishedZipNames: List<String>,
        chaptersBackedUp: Boolean,
        infoBackedUp: Boolean,
        chaptersPublished: Boolean,
        infoPublished: Boolean
    ) {
        stagedZipNames.forEach(storage::delete)

        if (infoPublished) {
            storage.delete(INFO)
        }
        if (chaptersPublished) {
            storage.delete(CHAPTERS)
        }

        if (
            infoBackedUp &&
            storage.exists(BACKUP_INFO) &&
            !storage.exists(INFO)
        ) {
            storage.rename(
                BACKUP_INFO,
                INFO
            )
        }

        if (
            chaptersBackedUp &&
            storage.exists(BACKUP_CHAPTERS) &&
            !storage.exists(CHAPTERS)
        ) {
            storage.rename(
                BACKUP_CHAPTERS,
                CHAPTERS
            )
        }

        storage.delete(STAGED_INFO)
        storage.delete(STAGED_CHAPTERS)

        publishedZipNames.forEach(storage::delete)

        // Backups may still be present if restoration itself failed. Leaving
        // them is safer than deleting the last known-good metadata.
    }

    private fun recoverInterruptedUpdate(
        storage: RanobeLibMutableStorage
    ) {
        if (!storage.exists(JOURNAL)) {
            return
        }

        val journal = runCatching {
            readJsonObject(
                storage.readBytes(JOURNAL)
            )
        }.getOrElse {
            error(
                "Журнал предыдущего обновления повреждён. " +
                    "ReaderLB не будет автоматически менять тайтл."
            )
        }

        val zipNames = journal
            .optJSONArray("addedZipNames")
            ?.let { array ->
                (0 until array.length())
                    .map { array.optString(it).trim() }
                    .filter(String::isNotBlank)
            }
            ?: emptyList()

        val addedNumbers = journal
            .optJSONArray("addedNumbers")
            ?.let { array ->
                (0 until array.length())
                    .map { array.optString(it).trim() }
                    .filter(String::isNotBlank)
            }
            ?: emptyList()

        val canonicalCommitted =
            storage.exists(CHAPTERS) &&
                storage.exists(INFO) &&
                runCatching {
                    val chapters = readJsonArray(
                        storage.readBytes(CHAPTERS)
                    )
                    val numbers = (0 until chapters.length())
                        .map {
                            chapters.getJSONObject(it)
                                .getString("number")
                        }
                        .toSet()

                    addedNumbers.isNotEmpty() &&
                        addedNumbers.all(numbers::contains) &&
                        zipNames.all(storage::exists) &&
                        readJsonObject(
                            storage.readBytes(INFO)
                        ).optJSONObject("media") != null
                }.getOrDefault(false)

        if (canonicalCommitted) {
            // Metadata already crossed the final visibility boundary. The
            // transaction committed; only cleanup was interrupted.
            storage.delete(BACKUP_CHAPTERS)
            storage.delete(BACKUP_INFO)
        } else {
            // The old metadata remains authoritative. Restore backups when
            // needed and remove any new ZIPs that may have been published
            // before the process was killed.
            restoreBackup(
                storage = storage,
                canonical = CHAPTERS,
                backup = BACKUP_CHAPTERS
            )
            restoreBackup(
                storage = storage,
                canonical = INFO,
                backup = BACKUP_INFO
            )

            zipNames.forEach { name ->
                if (storage.exists(name)) {
                    require(storage.delete(name)) {
                        "Не удалось удалить незавершённый файл $name"
                    }
                }
            }
        }

        storage.names()
            .filter {
                it == STAGED_CHAPTERS ||
                    it == STAGED_INFO ||
                    it.startsWith(STAGED_ZIP_PREFIX)
            }
            .forEach { name ->
                require(storage.delete(name)) {
                    "Не удалось очистить файл предыдущей транзакции $name"
                }
            }

        require(storage.delete(JOURNAL)) {
            "Не удалось удалить журнал предыдущего обновления"
        }
    }

    private fun restoreBackup(
        storage: RanobeLibMutableStorage,
        canonical: String,
        backup: String
    ) {
        if (!storage.exists(backup)) {
            return
        }

        if (storage.exists(canonical)) {
            require(storage.delete(canonical)) {
                "Не удалось подготовить восстановление $canonical"
            }
        }

        require(
            storage.rename(
                backup,
                canonical
            )
        ) {
            "Не удалось восстановить $canonical после предыдущего сбоя"
        }
    }

    private fun recoverMetadataIfNeeded(
        storage: RanobeLibMutableStorage
    ) {
        // If both the canonical metadata and a backup exist, the canonical
        // file is authoritative: this is the normal state after a successful
        // commit whose final backup cleanup was interrupted.
        if (
            storage.exists(CHAPTERS) &&
            storage.exists(BACKUP_CHAPTERS)
        ) {
            require(storage.delete(BACKUP_CHAPTERS)) {
                "Не удалось удалить старую резервную копию $CHAPTERS"
            }
        }
        if (
            storage.exists(INFO) &&
            storage.exists(BACKUP_INFO)
        ) {
            require(storage.delete(BACKUP_INFO)) {
                "Не удалось удалить старую резервную копию $INFO"
            }
        }

        if (
            !storage.exists(CHAPTERS) &&
            storage.exists(BACKUP_CHAPTERS)
        ) {
            require(
                storage.rename(
                    BACKUP_CHAPTERS,
                    CHAPTERS
                )
            ) {
                "Не удалось восстановить $CHAPTERS после предыдущего сбоя"
            }
        }

        if (
            !storage.exists(INFO) &&
            storage.exists(BACKUP_INFO)
        ) {
            require(
                storage.rename(
                    BACKUP_INFO,
                    INFO
                )
            ) {
                "Не удалось восстановить $INFO после предыдущего сбоя"
            }
        }

        require(storage.exists(CHAPTERS)) {
            "Существующий тайтл не содержит $CHAPTERS"
        }
        require(storage.exists(INFO)) {
            "Существующий тайтл не содержит $INFO"
        }
    }

    private fun cleanupStaging(
        storage: RanobeLibMutableStorage
    ) {
        storage.names()
            .filter {
                it == STAGED_CHAPTERS ||
                    it == STAGED_INFO ||
                    it.startsWith(STAGED_ZIP_PREFIX)
            }
            .forEach { name ->
                require(storage.delete(name)) {
                    "Не удалось очистить временный файл $name"
                }
            }
    }

    private fun requireJsonArrayFile(
        storage: RanobeLibMutableStorage,
        name: String
    ) {
        readJsonArray(
            storage.readBytes(name)
        )
    }

    private fun requireJsonObjectFile(
        storage: RanobeLibMutableStorage,
        name: String
    ) {
        readJsonObject(
            storage.readBytes(name)
        )
    }

    private fun sha256(
        bytes: ByteArray
    ): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") {
                "%02x".format(it)
            }

    private fun readJsonArray(
        bytes: ByteArray
    ): JSONArray =
        JSONArray(
            bytes.toString(Charsets.UTF_8)
        )

    private fun readJsonObject(
        bytes: ByteArray
    ): JSONObject =
        JSONObject(
            bytes.toString(Charsets.UTF_8)
        )

    private companion object {
        const val INFO = "info.json"
        const val CHAPTERS = "chapters.json"

        const val STAGED_INFO = ".readerlb-info.tmp"
        const val STAGED_CHAPTERS = ".readerlb-chapters.tmp"
        const val BACKUP_INFO = ".readerlb-info.bak"
        const val BACKUP_CHAPTERS = ".readerlb-chapters.bak"
        const val STAGED_ZIP_PREFIX = ".readerlb-new-"
        const val JOURNAL = ".readerlb-update.json"
    }
}
