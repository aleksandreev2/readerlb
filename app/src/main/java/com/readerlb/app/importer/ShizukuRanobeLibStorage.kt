package com.readerlb.app.importer

import com.readerlb.app.shizuku.RanobeLibPrivilegedFiles
import com.readerlb.app.storage.MAX_INFO_JSON_BYTES

class ShizukuRanobeLibStorage(
    private val bridge:
        RanobeLibPrivilegedFiles,
    private val folderName: String
) : RanobeLibMutableStorage {

    init {
        requireSafeLeaf(
            folderName
        )
        require(
            bridge.isDirectory(
                folderName
            )
        ) {
            "Локальный тайтл RanobeLib не найден"
        }
    }

    override fun exists(
        name: String
    ): Boolean =
        bridge.exists(
            relative(name)
        ) &&
            !bridge.isDirectory(
                relative(name)
            )

    override fun readBytes(
        name: String
    ): ByteArray {
        val path =
            relative(name)
        require(
            exists(name)
        ) {
            "Файл $name отсутствует"
        }

        val limit =
            if (
                name ==
                "info.json"
            ) {
                MAX_INFO_JSON_BYTES
                    .toLong()
            } else {
                MAX_SHIZUKU_STORAGE_READ_BYTES
            }

        return bridge.readBytes(
            relativePath = path,
            limitBytes = limit
        )
    }

    override fun writeBytes(
        name: String,
        bytes: ByteArray
    ) {
        bridge.writeBytes(
            relative(name),
            bytes
        )
    }

    override fun delete(
        name: String
    ): Boolean {
        val path =
            relative(name)

        return !bridge.exists(path) ||
            bridge
                .deleteRecursively(path)
    }

    override fun rename(
        from: String,
        to: String
    ): Boolean =
        bridge.rename(
            relative(from),
            relative(to)
        )

    override fun length(
        name: String
    ): Long =
        bridge.length(
            relative(name)
        )

    override fun names():
        Set<String> =
        bridge.listNames(
            folderName
        )
            .filter {
                !bridge.isDirectory(
                    "$folderName/$it"
                )
            }
            .toCollection(
                linkedSetOf()
            )

    private fun relative(
        name: String
    ): String {
        requireSafeLeaf(name)
        return "$folderName/$name"
    }

    private fun requireSafeLeaf(
        value: String
    ) {
        require(
            value.isNotBlank() &&
                value != "." &&
                value != ".." &&
                '/' !in value &&
                '\\' !in value &&
                ':' !in value
        ) {
            "Недопустимое имя файла: $value"
        }
    }
}

private const val MAX_SHIZUKU_STORAGE_READ_BYTES =
    512L * 1024L * 1024L
