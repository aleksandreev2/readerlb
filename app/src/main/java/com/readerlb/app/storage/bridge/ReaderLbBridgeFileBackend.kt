package com.readerlb.app.storage.bridge

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.os.ParcelFileDescriptor
import com.readerlb.app.storage.RanobeLibFileBackend
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.concurrent.Executors

/**
 * App-side backend for ReaderLB's privileged local bridge.
 */
class ReaderLbBridgeFileBackend(
    private val socketName: String,
    private val token: String
) : RanobeLibFileBackend {
    private val ioExecutor =
        Executors.newCachedThreadPool()

    fun ping(): Boolean =
        runCatching {
            request(
                ReaderLbBridgeProtocol.OP_PING
            ) { _, input ->
                input.readInt() ==
                    ReaderLbBridgeProtocol.VERSION &&
                    input.readUTF().isNotBlank()
            }
        }.getOrDefault(false)

    override fun list(
        relativePath: String
    ): Array<String> =
        request(
            ReaderLbBridgeProtocol.OP_LIST,
            relativePath
        ) { _, input ->
            val count = input.readInt()
            require(
                count in
                    0..ReaderLbBridgeProtocol
                        .MAX_LIST_ENTRIES
            ) {
                "Invalid directory entry count"
            }
            Array(count) {
                input.readUTF()
            }
        }

    override fun exists(
        relativePath: String
    ): Boolean =
        request(
            ReaderLbBridgeProtocol.OP_EXISTS,
            relativePath
        ) { _, input ->
            input.readBoolean()
        }

    override fun isDirectory(
        relativePath: String
    ): Boolean =
        request(
            ReaderLbBridgeProtocol
                .OP_IS_DIRECTORY,
            relativePath
        ) { _, input ->
            input.readBoolean()
        }

    override fun length(
        relativePath: String
    ): Long =
        request(
            ReaderLbBridgeProtocol.OP_LENGTH,
            relativePath
        ) { _, input ->
            input.readLong()
        }

    override fun lastModified(
        relativePath: String
    ): Long =
        request(
            ReaderLbBridgeProtocol
                .OP_LAST_MODIFIED,
            relativePath
        ) { _, input ->
            input.readLong()
        }

    override fun create(
        relativePath: String,
        directory: Boolean
    ): Boolean =
        request(
            ReaderLbBridgeProtocol.OP_CREATE,
            relativePath,
            writeExtra = {
                it.writeBoolean(directory)
            }
        ) { _, input ->
            input.readBoolean()
        }

    override fun delete(
        relativePath: String
    ): Boolean =
        request(
            ReaderLbBridgeProtocol.OP_DELETE,
            relativePath
        ) { _, input ->
            input.readBoolean()
        }

    override fun rename(
        from: String,
        to: String
    ): Boolean =
        request(
            ReaderLbBridgeProtocol.OP_RENAME,
            from,
            writeExtra = {
                it.writeUTF(to)
            }
        ) { _, input ->
            input.readBoolean()
        }

    override fun open(
        relativePath: String,
        mode: String
    ): ParcelFileDescriptor {
        require(
            mode in setOf(
                "r",
                "w",
                "wt",
                "wa",
                "rw",
                "rwt"
            )
        ) {
            "Unsupported mode"
        }

        return if (mode == "r") {
            openReadPipe(relativePath)
        } else {
            openWritePipe(
                relativePath,
                append = mode == "wa"
            )
        }
    }

    private fun openReadPipe(
        relativePath: String
    ): ParcelFileDescriptor {
        val pipe =
            ParcelFileDescriptor
                .createPipe()
        val readEnd = pipe[0]
        val writeEnd = pipe[1]

        ioExecutor.execute {
            ParcelFileDescriptor
                .AutoCloseOutputStream(
                    writeEnd
                )
                .use { destination ->
                    runCatching {
                        openSocket()
                            .use { socket ->
                                val output =
                                    DataOutputStream(
                                        BufferedOutputStream(
                                            socket.outputStream
                                        )
                                    )
                                val input =
                                    DataInputStream(
                                        BufferedInputStream(
                                            socket.inputStream
                                        )
                                    )

                                writeHeader(
                                    output,
                                    ReaderLbBridgeProtocol
                                        .OP_READ
                                )
                                output.writeUTF(
                                    relativePath
                                )
                                output.flush()

                                readStatus(input)
                                val length =
                                    input.readLong()
                                require(
                                    length >= 0L
                                ) {
                                    "Invalid file length"
                                }

                                var remaining =
                                    length
                                val buffer =
                                    ByteArray(
                                        DEFAULT_BUFFER_SIZE
                                    )
                                while (
                                    remaining > 0L
                                ) {
                                    val count =
                                        input.read(
                                            buffer,
                                            0,
                                            minOf(
                                                buffer.size
                                                    .toLong(),
                                                remaining
                                            ).toInt()
                                        )
                                    if (count < 0) {
                                        throw IOException(
                                            "Bridge stream ended early"
                                        )
                                    }
                                    destination.write(
                                        buffer,
                                        0,
                                        count
                                    )
                                    remaining -=
                                        count
                                }
                            }
                    }
                }
        }

        return readEnd
    }

    private fun openWritePipe(
        relativePath: String,
        append: Boolean
    ): ParcelFileDescriptor {
        val pipe =
            ParcelFileDescriptor
                .createPipe()
        val readEnd = pipe[0]
        val writeEnd = pipe[1]

        ioExecutor.execute {
            ParcelFileDescriptor
                .AutoCloseInputStream(
                    readEnd
                )
                .use { source ->
                    runCatching {
                        openSocket()
                            .use { socket ->
                                val output =
                                    DataOutputStream(
                                        BufferedOutputStream(
                                            socket.outputStream
                                        )
                                    )
                                val input =
                                    DataInputStream(
                                        BufferedInputStream(
                                            socket.inputStream
                                        )
                                    )

                                writeHeader(
                                    output,
                                    ReaderLbBridgeProtocol
                                        .OP_WRITE
                                )
                                output.writeUTF(
                                    relativePath
                                )
                                output.writeBoolean(
                                    append
                                )
                                output.flush()

                                readStatus(input)

                                source.copyTo(output)
                                output.flush()
                                socket.shutdownOutput()

                                readStatus(input)
                            }
                    }
                }
        }

        return writeEnd
    }

    private fun <T> request(
        operation: String,
        relativePath: String? = null,
        writeExtra:
            (DataOutputStream) -> Unit =
            {},
        readResult:
            (
                DataOutputStream,
                DataInputStream
            ) -> T
    ): T =
        openSocket().use { socket ->
            val output =
                DataOutputStream(
                    BufferedOutputStream(
                        socket.outputStream
                    )
                )
            val input =
                DataInputStream(
                    BufferedInputStream(
                        socket.inputStream
                    )
                )

            writeHeader(
                output,
                operation
            )
            if (relativePath != null) {
                output.writeUTF(
                    relativePath
                )
            }
            writeExtra(output)
            output.flush()

            readStatus(input)
            readResult(
                output,
                input
            )
        }

    private fun openSocket(): LocalSocket =
        LocalSocket().apply {
            soTimeout = 15_000
            connect(
                LocalSocketAddress(
                    socketName,
                    LocalSocketAddress
                        .Namespace.ABSTRACT
                )
            )
        }

    private fun writeHeader(
        output: DataOutputStream,
        operation: String
    ) {
        output.writeInt(
            ReaderLbBridgeProtocol.MAGIC
        )
        output.writeInt(
            ReaderLbBridgeProtocol.VERSION
        )
        output.writeUTF(token)
        output.writeUTF(operation)
    }

    private fun readStatus(
        input: DataInputStream
    ) {
        if (!input.readBoolean()) {
            throw IOException(
                input.readUTF()
            )
        }
    }
}
