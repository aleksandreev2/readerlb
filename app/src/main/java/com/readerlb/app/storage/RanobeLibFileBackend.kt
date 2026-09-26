package com.readerlb.app.storage

import android.os.ParcelFileDescriptor
import java.io.FileNotFoundException

/**
 * Narrow filesystem contract used by ReaderLB's local RanobeLib document provider.
 *
 * Implementations may be backed by SAF, Shizuku or ReaderLB's own privileged bridge.
 * The contract deliberately exposes only filesystem operations under the verified
 * RanobeLib book root; it is not an arbitrary shell-command interface.
 */
data class RanobeLibFileEntry(
    val name: String,
    val isDirectory: Boolean,
    val length: Long,
    val lastModified: Long
)

interface RanobeLibFileBackend {
    fun list(relativePath: String): Array<String>

    fun listEntries(
        relativePath: String
    ): List<RanobeLibFileEntry> =
        list(relativePath).map {
                name ->
            val child =
                if (
                    relativePath.isEmpty()
                ) {
                    name
                } else {
                    "$relativePath/$name"
                }

            RanobeLibFileEntry(
                name = name,
                isDirectory =
                    isDirectory(child),
                length = length(child),
                lastModified =
                    lastModified(child)
            )
        }
    fun exists(relativePath: String): Boolean
    fun isDirectory(relativePath: String): Boolean
    fun length(relativePath: String): Long
    fun lastModified(relativePath: String): Long
    fun open(relativePath: String, mode: String): ParcelFileDescriptor
    fun create(relativePath: String, directory: Boolean): Boolean
    fun delete(relativePath: String): Boolean
    fun rename(from: String, to: String): Boolean
}

enum class RanobeLibBackendKind {
    SHIZUKU,
    READERLB_BRIDGE
}

private data class ActiveRanobeLibBackend(
    val kind: RanobeLibBackendKind,
    val backend: RanobeLibFileBackend
)

/**
 * Process-wide backend selector used by RanobeLibDocumentsProvider.
 *
 * Clearing is kind-aware so a dying Shizuku connection cannot accidentally remove
 * a newer ReaderLB Bridge backend that was installed afterwards.
 */
object RanobeLibBackends {
    @Volatile
    private var active: ActiveRanobeLibBackend? = null

    fun install(kind: RanobeLibBackendKind, backend: RanobeLibFileBackend) {
        active = ActiveRanobeLibBackend(kind, backend)
    }

    fun clear(kind: RanobeLibBackendKind) {
        if (active?.kind == kind) {
            active = null
        }
    }

    fun currentKind(): RanobeLibBackendKind? = active?.kind

    fun requireBackend(): RanobeLibFileBackend =
        active?.backend ?: throw FileNotFoundException("RanobeLib access is disconnected")
}

/** Adapter around the existing Shizuku AIDL service. */
class ShizukuRanobeLibFileBackend(
    private val remote: IReaderLbFiles
) : RanobeLibFileBackend {
    override fun list(relativePath: String): Array<String> =
        remote.list(relativePath)

    override fun exists(relativePath: String): Boolean =
        remote.exists(relativePath)

    override fun isDirectory(relativePath: String): Boolean =
        remote.isDirectory(relativePath)

    override fun length(relativePath: String): Long =
        remote.length(relativePath)

    override fun lastModified(relativePath: String): Long =
        remote.lastModified(relativePath)

    override fun open(relativePath: String, mode: String): ParcelFileDescriptor =
        remote.open(relativePath, mode)

    override fun create(relativePath: String, directory: Boolean): Boolean =
        remote.create(relativePath, directory)

    override fun delete(relativePath: String): Boolean =
        remote.delete(relativePath)

    override fun rename(from: String, to: String): Boolean =
        remote.rename(from, to)
}
