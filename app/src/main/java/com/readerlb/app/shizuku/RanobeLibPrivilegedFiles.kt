package com.readerlb.app.shizuku

import java.io.InputStream
import java.io.OutputStream

interface RanobeLibPrivilegedFiles {
    fun listNames(
        relativePath: String = ""
    ): List<String>

    fun exists(
        relativePath: String
    ): Boolean

    fun isDirectory(
        relativePath: String
    ): Boolean

    fun length(
        relativePath: String
    ): Long

    fun mkdirs(
        relativePath: String
    ): Boolean

    fun deleteRecursively(
        relativePath: String
    ): Boolean

    fun rename(
        fromRelativePath: String,
        toRelativePath: String
    ): Boolean

    fun openInput(
        relativePath: String
    ): InputStream

    fun openOutput(
        relativePath: String
    ): OutputStream

    fun readBytes(
        relativePath: String,
        limitBytes: Long
    ): ByteArray

    fun writeBytes(
        relativePath: String,
        bytes: ByteArray
    )
}
