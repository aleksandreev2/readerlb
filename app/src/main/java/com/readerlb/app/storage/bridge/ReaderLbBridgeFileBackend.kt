package com.readerlb.app.storage.bridge

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.system.ErrnoException
import android.system.OsConstants
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import android.os.ParcelFileDescriptor
import com.readerlb.app.storage.RanobeLibFileBackend
import com.readerlb.app.storage.RanobeLibFileEntry
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
    private val port: Int,
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
        listEntries(
            relativePath
        )
            .map {
                it.name
            }
            .toTypedArray()

    override fun listEntries(
        relativePath: String
    ): List<RanobeLibFileEntry> =
        request(
            ReaderLbBridgeProtocol
                .OP_LIST_META,
            relativePath
        ) { _, input ->
            val count =
                input.readInt()
            require(
                count in
                    0..ReaderLbBridgeProtocol
                        .MAX_LIST_ENTRIES
            ) {
                "Invalid directory entry count"
            }

            List(count) {
                RanobeLibFileEntry(
                    name =
                        input.readUTF(),
                    isDirectory =
                        input.readBoolean(),
                    length =
                        input.readLong(),
                    lastModified =
                        input.readLong()
                )
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

    fun openProxy(
        context: Context,
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

        val append =
            mode == "wa"
        val writable =
            mode != "r"

        if (
            writable &&
            mode in setOf(
                "w",
                "wt",
                "rwt"
            )
        ) {
            truncate(
                relativePath,
                0L
            )
        }

        val appendBase =
            if (append) {
                length(relativePath)
            } else {
                0L
            }

        val proxyMode =
            when (mode) {
                "r" ->
                    ParcelFileDescriptor
                        .MODE_READ_ONLY

                "rw",
                "rwt" ->
                    ParcelFileDescriptor
                        .MODE_READ_WRITE

                else ->
                    ParcelFileDescriptor
                        .MODE_WRITE_ONLY
            }

        val callback =
            object :
                ProxyFileDescriptorCallback() {
                override fun onGetSize():
                    Long =
                    bridgeCall(
                        "getSize"
                    ) {
                        length(
                            relativePath
                        )
                    }

                override fun onRead(
                    offset: Long,
                    size: Int,
                    data: ByteArray
                ): Int =
                    bridgeCall(
                        "read"
                    ) {
                        readAt(
                            relativePath,
                            offset,
                            size,
                            data
                        )
                    }

                override fun onWrite(
                    offset: Long,
                    size: Int,
                    data: ByteArray
                ): Int =
                    bridgeCall(
                        "write"
                    ) {
                        require(writable) {
                            "File is read-only"
                        }
                        writeAt(
                            relativePath,
                            appendBase +
                                offset,
                            size,
                            data
                        )
                    }

                override fun onFsync() {
                    bridgeCall(
                        "fsync"
                    ) {
                        if (writable) {
                            fsync(
                                relativePath
                            )
                        }
                    }
                }

                override fun onRelease() =
                    Unit
            }

        return context
            .getSystemService(
                StorageManager::class.java
            )
            .openProxyFileDescriptor(
                proxyMode,
                callback,
                proxyHandler
            )
    }

    private fun readAt(
        relativePath: String,
        offset: Long,
        size: Int,
        destination: ByteArray
    ): Int {
        require(
            offset >= 0L &&
                size in
                    0..minOf(
                        destination.size,
                        ReaderLbBridgeProtocol
                            .MAX_IO_CHUNK_BYTES
                    )
        )

        return request(
            ReaderLbBridgeProtocol
                .OP_READ_AT,
            relativePath,
            writeExtra = {
                it.writeLong(offset)
                it.writeInt(size)
            }
        ) { _, input ->
            val count =
                input.readInt()
            require(
                count in 0..size
            ) {
                "Invalid bridge read size"
            }
            if (count > 0) {
                input.readFully(
                    destination,
                    0,
                    count
                )
            }
            count
        }
    }

    private fun writeAt(
        relativePath: String,
        offset: Long,
        size: Int,
        source: ByteArray
    ): Int {
        require(
            offset >= 0L &&
                size in
                    0..minOf(
                        source.size,
                        ReaderLbBridgeProtocol
                            .MAX_IO_CHUNK_BYTES
                    )
        )

        return request(
            ReaderLbBridgeProtocol
                .OP_WRITE_AT,
            relativePath,
            writeExtra = {
                it.writeLong(offset)
                it.writeInt(size)
                if (size > 0) {
                    it.write(
                        source,
                        0,
                        size
                    )
                }
            }
        ) { _, input ->
            input.readInt()
                .also {
                    require(
                        it == size
                    ) {
                        "Incomplete bridge write"
                    }
                }
        }
    }

    private fun truncate(
        relativePath: String,
        length: Long
    ) {
        require(length >= 0L)

        request(
            ReaderLbBridgeProtocol
                .OP_TRUNCATE,
            relativePath,
            writeExtra = {
                it.writeLong(length)
            }
        ) { _, _ ->
            Unit
        }
    }

    private fun fsync(
        relativePath: String
    ) {
        request(
            ReaderLbBridgeProtocol
                .OP_FSYNC,
            relativePath
        ) { _, _ ->
            Unit
        }
    }

    private inline fun <T> bridgeCall(
        operation: String,
        block: () -> T
    ): T =
        try {
            block()
        } catch (
            error:
            ErrnoException
        ) {
            throw error
        } catch (error: Throwable) {
            val errno =
                ErrnoException(
                    "ReaderLB bridge $operation",
                    OsConstants.EIO
                )
            errno.initCause(error)
            throw errno
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

    private fun openSocket(): Socket =
        Socket().apply {
            soTimeout = 15_000
            connect(
                InetSocketAddress(
                    InetAddress.getLoopbackAddress(),
                    port
                ),
                5_000
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
    private companion object {
        val proxyThread:
            HandlerThread by lazy {
                HandlerThread(
                    "ReaderLB-Bridge-PFD"
                ).apply {
                    start()
                }
            }

        val proxyHandler:
            Handler by lazy {
                Handler(
                    proxyThread.looper
                )
            }
    }

}
