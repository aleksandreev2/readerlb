package com.readerlb.app.storage.bridge

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderLbBridgeFileBackendAndroidTest {
    @Test
    fun backendUsesAuthenticatedProxyFileProtocol() {
        val context =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
        val token =
            "ab".repeat(32)
        val readPayload =
            "reader-data"
                .toByteArray(
                    StandardCharsets.UTF_8
                )
        val written =
            AtomicReference(
                ByteArray(0)
            )
        val failure =
            AtomicReference<Throwable>()
        val stop =
            AtomicBoolean(false)
        val operations =
            Collections.synchronizedSet(
                linkedSetOf<String>()
            )

        val server =
            ServerSocket(
                0,
                8,
                InetAddress
                    .getLoopbackAddress()
            ).apply {
                soTimeout = 500
            }

        val serverThread =
            thread(
                name =
                    "readerlb-bridge-test"
            ) {
                try {
                    while (!stop.get()) {
                        val socket =
                            try {
                                server.accept()
                            } catch (
                                timeout:
                                SocketTimeoutException
                            ) {
                                continue
                            }

                        socket.use {
                            it.soTimeout =
                                5_000
                            val input =
                                DataInputStream(
                                    BufferedInputStream(
                                        it.inputStream
                                    )
                                )
                            val output =
                                DataOutputStream(
                                    BufferedOutputStream(
                                        it.outputStream
                                    )
                                )

                            assertEquals(
                                ReaderLbBridgeProtocol.MAGIC,
                                input.readInt()
                            )
                            assertEquals(
                                ReaderLbBridgeProtocol.VERSION,
                                input.readInt()
                            )
                            assertEquals(
                                token,
                                input.readUTF()
                            )

                            val operation =
                                input.readUTF()
                            operations.add(
                                operation
                            )

                            when (operation) {
                                ReaderLbBridgeProtocol.OP_PING -> {
                                    output.writeBoolean(
                                        true
                                    )
                                    output.writeInt(
                                        ReaderLbBridgeProtocol.VERSION
                                    )
                                    output.writeUTF(
                                        "/fake/book"
                                    )
                                }

                                ReaderLbBridgeProtocol.OP_LIST_META -> {
                                    assertEquals(
                                        "",
                                        input.readUTF()
                                    )
                                    output.writeBoolean(
                                        true
                                    )
                                    output.writeInt(2)

                                    output.writeUTF(
                                        "alpha"
                                    )
                                    output.writeBoolean(
                                        true
                                    )
                                    output.writeLong(0L)
                                    output.writeLong(11L)

                                    output.writeUTF(
                                        "beta"
                                    )
                                    output.writeBoolean(
                                        false
                                    )
                                    output.writeLong(7L)
                                    output.writeLong(22L)
                                }

                                ReaderLbBridgeProtocol.OP_LENGTH -> {
                                    val path =
                                        input.readUTF()
                                    output.writeBoolean(
                                        true
                                    )
                                    output.writeLong(
                                        if (
                                            path ==
                                                "chapter/data.txt"
                                        ) {
                                            readPayload.size
                                                .toLong()
                                        } else {
                                            written.get()
                                                .size
                                                .toLong()
                                        }
                                    )
                                }

                                ReaderLbBridgeProtocol.OP_READ_AT -> {
                                    assertEquals(
                                        "chapter/data.txt",
                                        input.readUTF()
                                    )
                                    val offset =
                                        input.readLong()
                                            .toInt()
                                    val requested =
                                        input.readInt()
                                    val count =
                                        if (
                                            offset >=
                                                readPayload.size
                                        ) {
                                            0
                                        } else {
                                            minOf(
                                                requested,
                                                readPayload.size -
                                                    offset
                                            )
                                        }

                                    output.writeBoolean(
                                        true
                                    )
                                    output.writeInt(
                                        count
                                    )
                                    if (count > 0) {
                                        output.write(
                                            readPayload,
                                            offset,
                                            count
                                        )
                                    }
                                }

                                ReaderLbBridgeProtocol.OP_TRUNCATE -> {
                                    assertEquals(
                                        "chapter/out.txt",
                                        input.readUTF()
                                    )
                                    val length =
                                        input.readLong()
                                    assertEquals(
                                        0L,
                                        length
                                    )
                                    written.set(
                                        ByteArray(0)
                                    )
                                    output.writeBoolean(
                                        true
                                    )
                                }

                                ReaderLbBridgeProtocol.OP_WRITE_AT -> {
                                    assertEquals(
                                        "chapter/out.txt",
                                        input.readUTF()
                                    )
                                    val offset =
                                        input.readLong()
                                            .toInt()
                                    val count =
                                        input.readInt()
                                    val bytes =
                                        ByteArray(count)
                                    input.readFully(
                                        bytes
                                    )

                                    val old =
                                        written.get()
                                    val next =
                                        ByteArray(
                                            maxOf(
                                                old.size,
                                                offset +
                                                    count
                                            )
                                        )
                                    old.copyInto(next)
                                    bytes.copyInto(
                                        next,
                                        offset
                                    )
                                    written.set(next)

                                    output.writeBoolean(
                                        true
                                    )
                                    output.writeInt(
                                        count
                                    )
                                }

                                ReaderLbBridgeProtocol.OP_FSYNC -> {
                                    input.readUTF()
                                    output.writeBoolean(
                                        true
                                    )
                                }

                                else ->
                                    error(
                                        "Unexpected operation: " +
                                            operation
                                    )
                            }

                            output.flush()
                        }
                    }
                } catch (
                    closed:
                    SocketException
                ) {
                    if (!stop.get()) {
                        failure.set(
                            closed
                        )
                    }
                } catch (error: Throwable) {
                    failure.set(error)
                }
            }

        try {
            val backend =
                ReaderLbBridgeFileBackend(
                    server.localPort,
                    token
                )

            assertTrue(
                backend.ping()
            )
            assertArrayEquals(
                arrayOf(
                    "alpha",
                    "beta"
                ),
                backend.list("")
            )

            val entries =
                backend.listEntries("")
            assertEquals(
                2,
                entries.size
            )
            assertEquals(
                "alpha",
                entries[0].name
            )
            assertTrue(
                entries[0].isDirectory
            )
            assertEquals(
                11L,
                entries[0].lastModified
            )
            assertEquals(
                "beta",
                entries[1].name
            )
            assertEquals(
                7L,
                entries[1].length
            )

            val readBytes =
                ParcelFileDescriptor
                    .AutoCloseInputStream(
                        backend.openProxy(
                            context,
                            "chapter/data.txt",
                            "r"
                        )
                    )
                    .use {
                        it.readBytes()
                    }
            assertEquals(
                "reader-data",
                readBytes.toString(
                    StandardCharsets.UTF_8
                )
            )

            ParcelFileDescriptor
                .AutoCloseOutputStream(
                    backend.openProxy(
                        context,
                        "chapter/out.txt",
                        "w"
                    )
                )
                .use {
                    it.write(
                        "written-data"
                            .toByteArray(
                                StandardCharsets.UTF_8
                            )
                    )
                    it.flush()
                }

            val deadline =
                System.nanoTime() +
                    TimeUnit.SECONDS
                        .toNanos(5)
            while (
                System.nanoTime() <
                    deadline &&
                !operations.containsAll(
                    setOf(
                        ReaderLbBridgeProtocol
                            .OP_PING,
                        ReaderLbBridgeProtocol
                            .OP_LIST_META,
                        ReaderLbBridgeProtocol
                            .OP_READ_AT,
                        ReaderLbBridgeProtocol
                            .OP_TRUNCATE,
                        ReaderLbBridgeProtocol
                            .OP_WRITE_AT
                    )
                )
            ) {
                Thread.sleep(25)
            }

            assertTrue(
                operations.containsAll(
                    setOf(
                        ReaderLbBridgeProtocol
                            .OP_PING,
                        ReaderLbBridgeProtocol
                            .OP_LIST_META,
                        ReaderLbBridgeProtocol
                            .OP_READ_AT,
                        ReaderLbBridgeProtocol
                            .OP_TRUNCATE,
                        ReaderLbBridgeProtocol
                            .OP_WRITE_AT
                    )
                )
            )
            assertEquals(
                "written-data",
                written.get()
                    .toString(
                        StandardCharsets.UTF_8
                    )
            )
            assertNull(
                failure.get()
            )
        } finally {
            stop.set(true)
            server.close()
            serverThread.join(
                2_000
            )
        }
    }
}
