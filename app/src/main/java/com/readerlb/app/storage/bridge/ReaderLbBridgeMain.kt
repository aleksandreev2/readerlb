package com.readerlb.app.storage.bridge

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.FileNotFoundException
import java.io.RandomAccessFile
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Narrow privileged filesystem process started by ReaderLB through local ADB.
 *
 * It never exposes an arbitrary shell. Every operation is constrained to the
 * verified RanobeLib book root and every client request is authenticated with
 * a fresh random launch token.
 */
object ReaderLbBridgeMain {
    private val workers = Executors.newFixedThreadPool(4)

    @JvmStatic
    fun main(args: Array<String>) {
        if (
            args.size == 1 &&
            args[0] == "--probe"
        ) {
            val root =
                findBookRoot()
            if (root == null) {
                println(
                    "READERLB_BRIDGE_ERROR:" +
                        "book_unavailable"
                )
            } else {
                println(
                    "READERLB_BRIDGE_READY:" +
                        root.absolutePath
                )
            }
            return
        }

        require(args.size == 2) {
            "Usage: ReaderLbBridgeMain <port> <token>"
        }

        val port = args[0].toIntOrNull()
            ?.takeIf { it in 1024..65535 }
            ?: error("Invalid bridge port")
        val token = args[1]
        require(token.matches(Regex("[a-f0-9]{64}")))

        val root = findBookRoot()
            ?: error("RanobeLib book directory is unavailable")

        ServerSocket().use { server ->
            server.reuseAddress = false
            server.bind(
                InetSocketAddress(
                    InetAddress.getLoopbackAddress(),
                    port
                ),
                16
            )

            while (true) {
                val client = server.accept()
                client.soTimeout = 15_000
                workers.execute {
                    runCatching {
                        handleClient(
                            client = client,
                            token = token,
                            root = root
                        )
                    }
                    runCatching { client.close() }
                }
            }
        }
    }

    private fun handleClient(
        client: Socket,
        token: String,
        root: File
    ) {
        val input = DataInputStream(
            BufferedInputStream(
                client.inputStream
            )
        )
        val output = DataOutputStream(
            BufferedOutputStream(
                client.outputStream
            )
        )

        try {
            require(input.readInt() == ReaderLbBridgeProtocol.MAGIC) {
                "Invalid bridge magic"
            }
            require(input.readInt() == ReaderLbBridgeProtocol.VERSION) {
                "Unsupported bridge protocol"
            }
            require(constantTimeEquals(input.readUTF(), token)) {
                "Bridge authentication failed"
            }

            when (val operation = input.readUTF()) {
                ReaderLbBridgeProtocol.OP_PING -> {
                    ok(output)
                    output.writeInt(
                        ReaderLbBridgeProtocol.VERSION
                    )
                    output.writeUTF(root.absolutePath)
                    output.flush()
                }

                ReaderLbBridgeProtocol.OP_LIST -> {
                    val directory = resolve(
                        root,
                        readRelativePath(input)
                    )
                    require(directory.isDirectory) {
                        "Not a directory"
                    }
                    val names = directory.list()
                        ?.sorted()
                        ?: error(
                            "Unable to list directory"
                        )
                    require(
                        names.size <=
                            ReaderLbBridgeProtocol
                                .MAX_LIST_ENTRIES
                    ) {
                        "Directory contains too many entries"
                    }

                    ok(output)
                    output.writeInt(names.size)
                    names.forEach(output::writeUTF)
                    output.flush()
                }

                ReaderLbBridgeProtocol.OP_LIST_META -> {
                    val relative =
                        readRelativePath(input)
                    val directory =
                        resolve(
                            root,
                            relative
                        )
                    require(directory.isDirectory) {
                        "Not a directory"
                    }

                    val entries =
                        directory
                            .listFiles()
                            .orEmpty()
                            .asSequence()
                            .mapNotNull {
                                    child ->
                                val childRelative =
                                    if (
                                        relative.isEmpty()
                                    ) {
                                        child.name
                                    } else {
                                        relative +
                                            "/" +
                                            child.name
                                    }

                                runCatching {
                                    resolve(
                                        root,
                                        childRelative
                                    )
                                }.getOrNull()
                            }
                            .sortedBy {
                                it.name
                            }
                            .toList()

                    require(
                        entries.size <=
                            ReaderLbBridgeProtocol
                                .MAX_LIST_ENTRIES
                    ) {
                        "Directory contains too many entries"
                    }

                    ok(output)
                    output.writeInt(
                        entries.size
                    )
                    entries.forEach {
                            child ->
                        output.writeUTF(
                            child.name
                        )
                        output.writeBoolean(
                            child.isDirectory
                        )
                        output.writeLong(
                            child.length()
                        )
                        output.writeLong(
                            child.lastModified()
                        )
                    }
                    output.flush()
                }

                ReaderLbBridgeProtocol.OP_EXISTS ->
                    writeBoolean(
                        output,
                        resolve(
                            root,
                            readRelativePath(input)
                        ).exists()
                    )

                ReaderLbBridgeProtocol.OP_IS_DIRECTORY ->
                    writeBoolean(
                        output,
                        resolve(
                            root,
                            readRelativePath(input)
                        ).isDirectory
                    )

                ReaderLbBridgeProtocol.OP_LENGTH ->
                    writeLong(
                        output,
                        resolve(
                            root,
                            readRelativePath(input)
                        ).length()
                    )

                ReaderLbBridgeProtocol.OP_LAST_MODIFIED ->
                    writeLong(
                        output,
                        resolve(
                            root,
                            readRelativePath(input)
                        ).lastModified()
                    )

                ReaderLbBridgeProtocol.OP_CREATE -> {
                    val file = resolve(
                        root,
                        readRelativePath(input)
                    )
                    val directory =
                        input.readBoolean()
                    require(
                        file.parentFile?.isDirectory == true
                    ) {
                        "Parent directory is missing"
                    }
                    val result =
                        if (directory) {
                            file.mkdir()
                        } else {
                            file.createNewFile()
                        }
                    writeBoolean(output, result)
                }

                ReaderLbBridgeProtocol.OP_DELETE -> {
                    val relative =
                        readRelativePath(input)
                    require(relative.isNotEmpty()) {
                        "Cannot delete library root"
                    }
                    writeBoolean(
                        output,
                        deleteInsideRoot(
                            resolve(root, relative),
                            root
                        )
                    )
                }

                ReaderLbBridgeProtocol.OP_RENAME -> {
                    val fromRelative =
                        readRelativePath(input)
                    val toRelative =
                        readRelativePath(input)
                    require(
                        fromRelative.isNotEmpty() &&
                            toRelative.isNotEmpty()
                    ) {
                        "Cannot rename library root"
                    }
                    val from =
                        resolve(root, fromRelative)
                    val to =
                        resolve(root, toRelative)
                    require(
                        from.parentFile == to.parentFile
                    ) {
                        "Cross-folder rename denied"
                    }
                    writeBoolean(
                        output,
                        from.exists() &&
                            !to.exists() &&
                            from.renameTo(to)
                    )
                }

                ReaderLbBridgeProtocol.OP_READ -> {
                    val file = resolve(
                        root,
                        readRelativePath(input)
                    )
                    require(file.isFile) {
                        "File does not exist"
                    }

                    ok(output)
                    output.writeLong(file.length())
                    file.inputStream().buffered()
                        .use { source ->
                            source.copyTo(output)
                        }
                    output.flush()
                }

                ReaderLbBridgeProtocol.OP_WRITE -> {
                    val file = resolve(
                        root,
                        readRelativePath(input)
                    )
                    val append =
                        input.readBoolean()
                    require(
                        file.parentFile?.isDirectory == true
                    ) {
                        "Parent directory is missing"
                    }
                    require(
                        !file.exists() || file.isFile
                    ) {
                        "Target is not a file"
                    }

                    ok(output)
                    output.flush()

                    java.io.FileOutputStream(
                        file,
                        append
                    )
                        .buffered()
                        .use { destination ->
                            input.copyTo(destination)
                            destination.flush()
                        }

                    ok(output)
                    output.flush()
                }

                ReaderLbBridgeProtocol.OP_READ_AT -> {
                    val file = resolve(
                        root,
                        readRelativePath(input)
                    )
                    val offset =
                        input.readLong()
                    val requested =
                        input.readInt()
                    require(
                        file.isFile &&
                            offset >= 0L &&
                            requested in
                                0..ReaderLbBridgeProtocol
                                    .MAX_IO_CHUNK_BYTES
                    ) {
                        "Invalid read request"
                    }

                    val buffer =
                        ByteArray(requested)
                    val count =
                        RandomAccessFile(
                            file,
                            "r"
                        ).use { random ->
                            random.seek(offset)
                            if (requested == 0) {
                                0
                            } else {
                                random.read(buffer)
                                    .coerceAtLeast(0)
                            }
                        }

                    ok(output)
                    output.writeInt(count)
                    if (count > 0) {
                        output.write(
                            buffer,
                            0,
                            count
                        )
                    }
                    output.flush()
                }

                ReaderLbBridgeProtocol.OP_WRITE_AT -> {
                    val file = resolve(
                        root,
                        readRelativePath(input)
                    )
                    val offset =
                        input.readLong()
                    val count =
                        input.readInt()
                    require(
                        offset >= 0L &&
                            count in
                                0..ReaderLbBridgeProtocol
                                    .MAX_IO_CHUNK_BYTES &&
                            file.parentFile?.isDirectory == true &&
                            (!file.exists() ||
                                file.isFile)
                    ) {
                        "Invalid write request"
                    }

                    val bytes =
                        ByteArray(count)
                    input.readFully(bytes)

                    RandomAccessFile(
                        file,
                        "rw"
                    ).use { random ->
                        random.seek(offset)
                        random.write(bytes)
                    }

                    ok(output)
                    output.writeInt(count)
                    output.flush()
                }

                ReaderLbBridgeProtocol.OP_TRUNCATE -> {
                    val file = resolve(
                        root,
                        readRelativePath(input)
                    )
                    val length =
                        input.readLong()
                    require(
                        length >= 0L &&
                            file.parentFile?.isDirectory == true &&
                            (!file.exists() ||
                                file.isFile)
                    ) {
                        "Invalid truncate request"
                    }

                    RandomAccessFile(
                        file,
                        "rw"
                    ).use {
                        it.setLength(length)
                    }

                    ok(output)
                    output.flush()
                }

                ReaderLbBridgeProtocol.OP_FSYNC -> {
                    val file = resolve(
                        root,
                        readRelativePath(input)
                    )
                    require(file.isFile) {
                        "File does not exist"
                    }

                    RandomAccessFile(
                        file,
                        "rw"
                    ).use {
                        it.fd.sync()
                    }

                    ok(output)
                    output.flush()
                }

                else ->
                    error(
                        "Unsupported operation: $operation"
                    )
            }
        } catch (error: Throwable) {
            runCatching {
                fail(
                    output,
                    error.message
                        ?: error.javaClass.simpleName
                )
                output.flush()
            }
        }
    }

    private fun findBookRoot(): File? {
        val candidates = buildList {
            add(
                File(
                    "/storage/emulated/0/Android/data/" +
                        "ru.libappc/files/book"
                )
            )
            File("/storage")
                .listFiles()
                .orEmpty()
                .filter {
                    it.name.matches(
                        Regex(
                            "[A-Fa-f0-9]{4}-" +
                                "[A-Fa-f0-9]{4}"
                        )
                    )
                }
                .forEach { volume ->
                    add(
                        File(
                            volume,
                            "Android/data/" +
                                "ru.libappc/files/book"
                        )
                    )
                }
        }

        return candidates
            .firstOrNull(
                ::isUsableRoot
            )
            ?.canonicalFile
    }

    private fun isUsableRoot(
        root: File
    ): Boolean {
        if (!root.isDirectory) {
            return false
        }

        return runCatching {
            Files.newDirectoryStream(
                root.toPath()
            ).use { }

            val probe =
                File.createTempFile(
                    ".readerlb-bridge-",
                    ".tmp",
                    root
                )
            try {
                probe.outputStream().use {
                    it.write(1)
                }
                probe.inputStream().use {
                    require(it.read() == 1)
                }
            } finally {
                probe.delete()
            }

            true
        }.getOrDefault(false)
    }

    private fun readRelativePath(
        input: DataInputStream
    ): String {
        val path = input.readUTF()
        require(
            path.toByteArray().size <=
                ReaderLbBridgeProtocol
                    .MAX_RELATIVE_PATH_BYTES
        ) {
            "Path is too long"
        }
        require(isSafeRelativePath(path)) {
            "Invalid relative path"
        }
        return path
    }

    private fun resolve(
        root: File,
        relativePath: String
    ): File {
        val candidate =
            if (relativePath.isEmpty()) {
                root
            } else {
                File(root, relativePath)
                    .absoluteFile
            }

        val canonical =
            candidate.canonicalFile

        require(
            candidate.path ==
                canonical.path
        ) {
            "Symbolic links are not allowed"
        }
        require(
            canonical.path == root.path ||
                canonical.path.startsWith(
                    root.path +
                        File.separator
                )
        ) {
            "Path escapes RanobeLib"
        }

        return canonical
    }

    private fun deleteInsideRoot(
        file: File,
        root: File
    ): Boolean {
        val canonical =
            file.canonicalFile
        require(
            canonical.path.startsWith(
                root.path +
                    File.separator
            )
        ) {
            "Path escapes RanobeLib"
        }

        if (
            Files.isSymbolicLink(
                file.toPath()
            )
        ) {
            return file.delete()
        }

        if (file.isDirectory) {
            file.listFiles()
                .orEmpty()
                .forEach {
                    if (
                        !deleteInsideRoot(
                            it,
                            root
                        )
                    ) {
                        return false
                    }
                }
        }

        return !file.exists() ||
            file.delete()
    }

    private fun isSafeRelativePath(
        relativePath: String
    ): Boolean =
        relativePath.isEmpty() ||
            relativePath.split('/')
                .all { part ->
                    part.isNotEmpty() &&
                        part != "." &&
                        part != ".." &&
                        '\\' !in part &&
                        '\u0000' !in part
                }

    private fun constantTimeEquals(
        actual: String,
        expected: String
    ): Boolean =
        MessageDigest.isEqual(
            actual.toByteArray(
                Charsets.UTF_8
            ),
            expected.toByteArray(
                Charsets.UTF_8
            )
        )

    private fun ok(
        output: DataOutputStream
    ) {
        output.writeBoolean(true)
    }

    private fun fail(
        output: DataOutputStream,
        message: String
    ) {
        output.writeBoolean(false)
        output.writeUTF(
            message.take(2048)
        )
    }

    private fun writeBoolean(
        output: DataOutputStream,
        value: Boolean
    ) {
        ok(output)
        output.writeBoolean(value)
        output.flush()
    }

    private fun writeLong(
        output: DataOutputStream,
        value: Long
    ) {
        ok(output)
        output.writeLong(value)
        output.flush()
    }
}
