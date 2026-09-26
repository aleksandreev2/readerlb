package com.readerlb.app.storage

import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files

/** Runs under Shizuku's shell or root UID. Only RanobeLib's known book directory is exposed. */
class ShizukuFileService : IReaderLbFiles.Stub() {
    @Volatile private var bookRoot: File? = null

    override fun probe(): Boolean {
        val candidates = buildList {
            add(File("/storage/emulated/0/Android/data/ru.libappc/files/book"))
            File("/storage").listFiles().orEmpty().forEach { volume ->
                if (volume.name.matches(Regex("[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}"))) {
                    add(File(volume, "Android/data/ru.libappc/files/book"))
                }
            }
        }

        // Access probing must stay constant-time with respect to library size.
        // Do not enumerate title folders or parse title metadata here: a user can
        // legitimately have tens of gigabytes of books. The normal library scanner
        // owns title discovery and reports progress separately after connection.
        bookRoot = candidates
            .firstOrNull(::isUsableLibraryRoot)
            ?.canonicalFile
        return bookRoot != null
    }

    override fun list(relativePath: String): Array<String> =
        resolve(relativePath).list().orEmpty().sorted().toTypedArray()

    override fun exists(relativePath: String): Boolean = resolve(relativePath).exists()

    override fun isDirectory(relativePath: String): Boolean = resolve(relativePath).isDirectory

    override fun length(relativePath: String): Long = resolve(relativePath).length()

    override fun lastModified(relativePath: String): Long = resolve(relativePath).lastModified()

    override fun open(relativePath: String, mode: String): ParcelFileDescriptor {
        require(mode in setOf("r", "w", "wt", "wa", "rw", "rwt")) { "Unsupported mode" }
        val file = resolve(relativePath)
        if (!file.isFile) throw FileNotFoundException(relativePath)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun create(relativePath: String, directory: Boolean): Boolean {
        val file = resolve(relativePath)
        require(file.parentFile?.isDirectory == true) { "Parent folder missing" }
        return if (directory) file.mkdir() else file.createNewFile()
    }

    override fun delete(relativePath: String): Boolean {
        val file = resolve(relativePath)
        require(relativePath.isNotEmpty()) { "Cannot delete library root" }
        return deleteInsideRoot(file)
    }

    override fun rename(from: String, to: String): Boolean {
        require(from.isNotEmpty() && to.isNotEmpty()) { "Cannot rename library root" }
        val source = resolve(from)
        val target = resolve(to)
        require(source.parentFile == target.parentFile) { "Cross-folder rename denied" }
        return source.exists() && !target.exists() && source.renameTo(target)
    }

    private fun resolve(relativePath: String): File {
        val root = bookRoot ?: throw IOException("RanobeLib access is disconnected")
        require(isSafeLibraryPath(relativePath)) { "Invalid library path" }
        val candidate = if (relativePath.isEmpty()) root else File(root, relativePath).absoluteFile
        val file = candidate.canonicalFile
        require(candidate.path == file.path) { "Symbolic links are not allowed" }
        require(file.path == root.path || file.path.startsWith(root.path + File.separator)) {
            "Path escapes RanobeLib"
        }
        return file
    }

    private fun deleteInsideRoot(file: File): Boolean {
        val root = bookRoot ?: return false
        val canonical = file.canonicalFile
        require(canonical.path.startsWith(root.path + File.separator)) {
            "Path escapes RanobeLib"
        }
        if (Files.isSymbolicLink(file.toPath())) return file.delete()
        if (file.isDirectory) {
            for (child in file.listFiles().orEmpty()) {
                if (!deleteInsideRoot(child)) return false
            }
        }
        return file.delete()
    }

}

internal fun isUsableLibraryRoot(root: File): Boolean {
    if (!root.isDirectory) return false

    return runCatching {
        // Opening a directory stream proves that the directory can be read without
        // materialising every title name. This keeps the access check independent
        // of the number and total size of downloaded books.
        Files.newDirectoryStream(root.toPath()).use { }

        val probe = File.createTempFile(".readerlb-probe-", ".tmp", root)
        try {
            probe.outputStream().use { output -> output.write(1) }
            probe.inputStream().use { it.read() == 1 }
        } finally {
            probe.delete()
        }
    }.getOrDefault(false)
}

internal fun isSafeLibraryPath(relativePath: String): Boolean =
    relativePath.isEmpty() || relativePath.split('/').all { part ->
        part.isNotEmpty() && part != "." && part != ".." &&
            '\\' !in part && '\u0000' !in part
    }
