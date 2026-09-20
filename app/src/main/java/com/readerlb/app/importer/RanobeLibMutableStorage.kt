package com.readerlb.app.importer

import java.io.File

/**
 * Minimal relative-file storage used by the 0.3 update transaction.
 *
 * Keeping transaction logic independent from Android SAF lets CI inject
 * failures at every write/rename step and prove rollback behaviour.
 */
interface RanobeLibMutableStorage {
    fun exists(name: String): Boolean
    fun readBytes(name: String): ByteArray
    fun writeBytes(name: String, bytes: ByteArray)
    fun delete(name: String): Boolean
    fun rename(from: String, to: String): Boolean
    fun length(name: String): Long
    fun names(): Set<String>
}

class FileRanobeLibStorage(
    private val directory: File
) : RanobeLibMutableStorage {

    init {
        require(directory.isDirectory || directory.mkdirs()) {
            "Не удалось открыть папку RanobeLib"
        }
    }

    override fun exists(name: String): Boolean =
        file(name).isFile

    override fun readBytes(name: String): ByteArray {
        val target = file(name)
        require(target.isFile) {
            "Файл $name отсутствует"
        }
        return target.readBytes()
    }

    override fun writeBytes(
        name: String,
        bytes: ByteArray
    ) {
        val target = file(name)
        target.outputStream().use {
            it.write(bytes)
            it.fd.sync()
        }
    }

    override fun delete(name: String): Boolean {
        val target = file(name)
        return !target.exists() || target.delete()
    }

    override fun rename(
        from: String,
        to: String
    ): Boolean {
        val source = file(from)
        val target = file(to)
        if (!source.exists() || target.exists()) {
            return false
        }
        return source.renameTo(target)
    }

    override fun length(name: String): Long =
        file(name).takeIf(File::isFile)?.length() ?: -1L

    override fun names(): Set<String> =
        directory.listFiles()
            .orEmpty()
            .filter(File::isFile)
            .mapTo(linkedSetOf()) { it.name }

    private fun file(name: String): File {
        require(
            name.isNotBlank() &&
                '/' !in name &&
                '\\' !in name
        ) {
            "Недопустимое имя файла: $name"
        }
        return File(directory, name)
    }
}
