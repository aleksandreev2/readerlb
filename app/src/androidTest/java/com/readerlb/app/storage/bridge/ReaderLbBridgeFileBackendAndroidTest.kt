package com.readerlb.app.storage.bridge

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
    fun backendUsesAuthenticatedStreamingProtocol() {
        val token = "ab".repeat(32)
        val written =
            AtomicReference<ByteArray>()
        val failure =
            AtomicReference<Throwable>()
        val handled =
            CountDownLatch(4)

        ServerSocket(
            0,
            4,
            InetAddress.getLoopbackAddress()
        ).use { server ->
            thread(
                name =
                    "readerlb-bridge-test"
            ) {
                try {
                    repeat(4) {
                        server.accept()
                            .use { socket ->
                                socket.soTimeout =
                                    5_000
                                val input =
                                    DataInputStream(
                                        BufferedInputStream(
                                            socket.inputStream
                                        )
                                    )
                                val output =
                                    DataOutputStream(
                                        BufferedOutputStream(
                                            socket.outputStream
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

                                when (
                                    input.readUTF()
                                ) {
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

                                    ReaderLbBridgeProtocol.OP_LIST -> {
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
                                        output.writeUTF(
                                            "beta"
                                        )
                                    }

                                    ReaderLbBridgeProtocol.OP_READ -> {
                                        assertEquals(
                                            "chapter/data.txt",
                                            input.readUTF()
                                        )
                                        val payload =
                                            "reader-data"
                                                .toByteArray(
                                                    StandardCharsets.UTF_8
                                                )
                                        output.writeBoolean(
                                            true
                                        )
                                        output.writeLong(
                                            payload.size
                                                .toLong()
                                        )
                                        output.write(
                                            payload
                                        )
                                    }

                                    ReaderLbBridgeProtocol.OP_WRITE -> {
                                        assertEquals(
                                            "chapter/out.txt",
                                            input.readUTF()
                                        )
                                        assertEquals(
                                            false,
                                            input.readBoolean()
                                        )
                                        output.writeBoolean(
                                            true
                                        )
                                        output.flush()

                                        val bytes =
                                            ByteArrayOutputStream()
                                        val buffer =
                                            ByteArray(1024)
                                        while (true) {
                                            val count =
                                                input.read(
                                                    buffer
                                                )
                                            if (count < 0) {
                                                break
                                            }
                                            bytes.write(
                                                buffer,
                                                0,
                                                count
                                            )
                                        }
                                        written.set(
                                            bytes.toByteArray()
                                        )

                                        output.writeBoolean(
                                            true
                                        )
                                    }

                                    else ->
                                        error(
                                            "Unexpected operation"
                                        )
                                }

                                output.flush()
                            }
                        handled.countDown()
                    }
                } catch (error: Throwable) {
                    failure.set(error)
                    while (
                        handled.count >
                        0
                    ) {
                        handled.countDown()
                    }
                }
            }

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

            val readBytes =
                ParcelFileDescriptor
                    .AutoCloseInputStream(
                        backend.open(
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
                    backend.open(
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
                }

            assertTrue(
                handled.await(
                    5,
                    TimeUnit.SECONDS
                )
            )
            assertNull(
                failure.get()
            )
            assertEquals(
                "written-data",
                written.get()
                    ?.toString(
                        StandardCharsets.UTF_8
                    )
            )
        }
    }
}
