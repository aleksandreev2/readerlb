package com.readerlb.app.storage

import android.os.ParcelFileDescriptor
import android.os.Process
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Runs under Shizuku's shell or root UID. Access is confined to MangaLib's files directory. */
class ShizukuFileService : IReaderLbFiles.Stub() {
    @Volatile private var roots: Map<String, File> = emptyMap()
    @Volatile private var selectedVolumeRoot: File? = null
    private data class PendingWrite(
        val finished: CountDownLatch = CountDownLatch(1),
        @Volatile var failure: String? = null
    )
    private val pendingWrites = ConcurrentHashMap<String, PendingWrite>()

    private fun awaitWrite(path: String) {
        val pending = pendingWrites[path] ?: return
        if (!pending.finished.await(10, TimeUnit.MINUTES)) {
            throw IOException("Timed out writing $path")
        }
        pending.failure?.let { throw IOException("Writing $path failed: $it") }
        pendingWrites.remove(path, pending)
    }

    override fun probe(): Boolean {
        val selection =
            selectConsistentLibraryRoots(
                libraryFilesCandidates()
            )
        roots =
            selection?.roots
                ?: emptyMap()
        selectedVolumeRoot =
            selection?.filesRoot
        return roots.isNotEmpty()
    }

    override fun diagnostics(): String = buildString {
        appendLine("Service UID=${Process.myUid()}, PID=${Process.myPid()}")
        appendLine(
            "Selected volume root: " +
                (
                    selectedVolumeRoot
                        ?.path
                        ?: "none"
                )
        )
        appendLine(
            "Selected roots: " +
                roots.keys
                    .sorted()
                    .joinToString()
                    .ifEmpty { "none" }
        )
        roots.toSortedMap().forEach {
                (kind, root) ->
            appendLine(
                "  $kind -> ${root.path}"
            )
        }

        for (filesRoot in libraryFilesCandidates()) {
            appendLine(
                "volume candidate=${filesRoot.path}"
            )
            for (kind in listOf("files", "book", "manga")) {
                val root =
                    if (kind == "files") {
                        filesRoot
                    } else {
                        File(
                            filesRoot,
                            kind
                        )
                    }
                appendLine(
                    "$kind candidate=${root.path}"
                )
                appendLine(
                    "  exists=${root.exists()}, directory=${root.isDirectory}, canRead=${root.canRead()}, canWrite=${root.canWrite()}"
                )
                appendLine(
                    "  canonical=" +
                        runCatching {
                            root.canonicalPath
                        }.getOrElse {
                            "ERROR ${it.javaClass.simpleName}: ${it.message}"
                        }
                )
                if (root.isDirectory) {
                    val read =
                        runCatching {
                            Files.newDirectoryStream(
                                root.toPath()
                            ).use { }
                        }
                    appendLine(
                        "  directory read=" +
                            read.fold(
                                { "OK" },
                                {
                                    "ERROR ${it.javaClass.simpleName}: ${it.message}"
                                }
                            )
                    )
                    val write =
                        runCatching {
                            val probe =
                                File.createTempFile(
                                    ".readerlb-probe-",
                                    ".tmp",
                                    root
                                )
                            try {
                                probe.writeBytes(
                                    byteArrayOf(1)
                                )
                                check(
                                    probe.readBytes()
                                        .contentEquals(
                                            byteArrayOf(1)
                                        )
                                )
                            } finally {
                                probe.delete()
                            }
                        }
                    appendLine(
                        "  write/read probe=" +
                            write.fold(
                                { "OK" },
                                {
                                    "ERROR ${it.javaClass.simpleName}: ${it.message}"
                                }
                            )
                    )
                }
            }
        }
    }

    override fun list(relativePath: String): Array<String> =
        resolve(relativePath).list().orEmpty().sorted().toTypedArray()

    override fun listEntries(relativePath: String): Array<String> {
        val directory = resolve(relativePath)
        val entries = directory.listFiles()
            ?: throw IOException("Cannot list $relativePath: exists=${directory.exists()} directory=${directory.isDirectory} readable=${directory.canRead()}")
        return entries.sortedBy { it.name }
            .map { (if (it.isDirectory) "D" else "F") + it.name }
            .toTypedArray()
    }

    override fun exists(relativePath: String): Boolean = resolve(relativePath).exists()

    override fun isDirectory(relativePath: String): Boolean = resolve(relativePath).isDirectory

    override fun length(relativePath: String): Long {
        awaitWrite(relativePath)
        return resolve(relativePath).length()
    }

    override fun lastModified(relativePath: String): Long = resolve(relativePath).lastModified()

    override fun open(relativePath: String, mode: String): ParcelFileDescriptor {
        require(mode in setOf("r", "w", "wt", "wa", "rw", "rwt")) { "Unsupported mode" }
        val file = resolve(relativePath)
        if (!file.isFile) throw FileNotFoundException(relativePath)
        if (mode == "r") awaitWrite(relativePath)
        // Android 11 SELinux denies Binder transfer of a descriptor backed by
        // another package's Android/data file. A pipe carries the bytes instead.
        if (mode == "r") {
            val (reader, writer) = ParcelFileDescriptor.createReliablePipe()
            Thread {
                try {
                    file.inputStream().use { input ->
                        ParcelFileDescriptor.AutoCloseOutputStream(writer).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (failure: Exception) {
                    writer.closeWithError(failure.message ?: "Library read failed")
                }
            }.apply { isDaemon = true; start() }
            return reader
        }
        if (mode in setOf("w", "wt", "wa")) {
            awaitWrite(relativePath)
            val (reader, writer) = ParcelFileDescriptor.createReliablePipe()
            val pending = PendingWrite()
            pendingWrites[relativePath] = pending
            Thread {
                try {
                    ParcelFileDescriptor.AutoCloseInputStream(reader).use { input ->
                        java.io.FileOutputStream(file, mode == "wa").use { output -> input.copyTo(output) }
                    }
                } catch (failure: Exception) {
                    pending.failure = "${failure.javaClass.simpleName}: ${failure.message}"
                    reader.closeWithError(failure.message ?: "Library write failed")
                } finally {
                    pending.finished.countDown()
                }
            }.apply { isDaemon = true; start() }
            return writer
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
    }

    override fun create(relativePath: String, directory: Boolean): Boolean {
        val file = resolve(relativePath)
        require(file.parentFile?.isDirectory == true) { "Parent folder missing" }
        return if (directory) file.mkdir() else file.createNewFile()
    }

    override fun delete(relativePath: String): Boolean {
        val file = resolve(relativePath)
        require(relativePath.contains('/')) { "Cannot delete library root" }
        return deleteInsideRoot(file, rootFor(relativePath))
    }

    override fun rename(from: String, to: String): Boolean {
        require(from.contains('/') && to.contains('/')) { "Cannot rename library root" }
        require(from.substringBefore('/') == to.substringBefore('/')) { "Cross-root rename denied" }
        val source = resolve(from)
        val target = resolve(to)
        require(source.parentFile == target.parentFile) { "Cross-folder rename denied" }
        return source.exists() && !target.exists() && source.renameTo(target)
    }

    private fun rootFor(path: String): File =
        roots[path.substringBefore('/')] ?: throw FileNotFoundException(path)

    private fun resolve(relativePath: String): File {
        require(isSafeLibraryPath(relativePath) && relativePath.isNotEmpty()) {
            "Invalid library path"
        }
        val root = rootFor(relativePath)
        val inside = relativePath.substringAfter('/', "")
        val candidate = if (inside.isEmpty()) root else File(root, inside).absoluteFile
        val file = candidate.canonicalFile
        require(candidate.path == file.path) { "Symbolic links are not allowed" }
        require(file.path == root.path || file.path.startsWith(root.path + File.separator)) {
            "Path escapes RanobeLib"
        }
        return file
    }

    private fun deleteInsideRoot(file: File, root: File): Boolean {
        val canonical = file.canonicalFile
        require(canonical.path.startsWith(root.path + File.separator)) {
            "Path escapes RanobeLib"
        }
        if (Files.isSymbolicLink(file.toPath())) return file.delete()
        if (file.isDirectory) {
            for (child in file.listFiles().orEmpty()) {
                if (!deleteInsideRoot(child, root)) return false
            }
        }
        return file.delete()
    }

}

internal data class LibraryRootSelection(
    val filesRoot: File,
    val roots: Map<String, File>
)

internal fun selectConsistentLibraryRoots(
    filesCandidates: List<File>
): LibraryRootSelection? {
    var best: LibraryRootSelection? = null
    var bestScore = -1

    filesCandidates.forEach {
            filesRoot ->
        val selected =
            linkedMapOf<String, File>()

        fun addIfUsable(
            kind: String,
            root: File
        ) {
            if (isUsableLibraryRoot(root)) {
                selected[kind] =
                    runCatching {
                        root.canonicalFile
                    }.getOrDefault(
                        root.absoluteFile
                    )
            }
        }

        addIfUsable(
            "files",
            filesRoot
        )
        addIfUsable(
            "book",
            File(
                filesRoot,
                "book"
            )
        )
        addIfUsable(
            "manga",
            File(
                filesRoot,
                "manga"
            )
        )

        if (selected.isEmpty()) {
            return@forEach
        }

        val contentRoots =
            listOf(
                "book",
                "manga"
            ).count(
                selected::containsKey
            )
        val score =
            contentRoots * 100 +
                if (
                    selected.containsKey(
                        "files"
                    )
                ) {
                    1
                } else {
                    0
                }

        if (score > bestScore) {
            bestScore = score
            best =
                LibraryRootSelection(
                    filesRoot =
                        runCatching {
                            filesRoot
                                .canonicalFile
                        }.getOrDefault(
                            filesRoot.absoluteFile
                        ),
                    roots =
                        selected.toMap()
                )
        }
    }

    return best
}

private fun libraryFilesCandidates(): List<File> =
    buildList {
        add(
            File(
                "/storage/emulated/0/Android/data/ru.libappc/files"
            )
        )
        File("/storage")
            .listFiles()
            .orEmpty()
            .forEach {
                    volume ->
                if (
                    volume.name.matches(
                        Regex(
                            "[A-Fa-f0-9]{4}-[A-Fa-f0-9]{4}"
                        )
                    )
                ) {
                    add(
                        File(
                            volume,
                            "Android/data/ru.libappc/files"
                        )
                    )
                }
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
