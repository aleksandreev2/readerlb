package com.readerlb.app.access

import android.content.Context
import io.github.muntashirakon.adb.AdbStream
import java.io.BufferedInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID

internal object ReaderLbAdbClient {
    private const val HOST = "127.0.0.1"

    fun pair(
        context: Context,
        port: Int,
        code: String
    ) {
        require(port in 1..65535)
        require(code.matches(Regex("\\d{6}"))) {
            "Нужен шестизначный код"
        }

        manager(context).pair(
            HOST,
            port,
            code
        )
    }

    fun ensureConnected(
        context: Context,
        discoveryTimeoutMillis: Long =
            20_000L
    ) {
        val manager = manager(context)
        if (manager.isConnected) {
            return
        }

        val connected =
            manager.connectTls(
                context.applicationContext,
                discoveryTimeoutMillis
            )

        if (
            !connected &&
            !manager.isConnected
        ) {
            throw IOException(
                "Не удалось подключиться к Wireless Debugging"
            )
        }
    }

    fun runShell(
        context: Context,
        command: String
    ): String {
        val marker =
            "__READERLB_END_" +
                UUID.randomUUID()
                    .toString()
                    .replace("-", "") +
                "__"
        val wrapped =
            "( $command ) 2>&1; " +
                "echo $marker"

        val stream =
            manager(context)
                .openStream(
                    "shell:$wrapped"
                )

        return stream.useAndReadUntil(
            marker
        )
    }

    fun disconnect(
        context: Context
    ) {
        runCatching {
            manager(context)
                .disconnect()
        }
    }

    private fun manager(
        context: Context
    ): ReaderLbAdbConnectionManager =
        ReaderLbAdbConnectionManager
            .getInstance(
                context.applicationContext
            )
}

private fun AdbStream.useAndReadUntil(
    marker: String
): String {
    try {
        val input =
            BufferedInputStream(
                openInputStream()
            )
        val bytes =
            java.io.ByteArrayOutputStream()
        val markerBytes =
            marker.toByteArray(
                StandardCharsets.UTF_8
            )
        val buffer =
            ByteArray(4096)

        while (true) {
            val count =
                input.read(buffer)
            if (count < 0) {
                break
            }
            bytes.write(
                buffer,
                0,
                count
            )

            val current =
                bytes.toByteArray()
            if (
                current.indexOfBytes(
                    markerBytes
                ) >= 0
            ) {
                break
            }

            require(
                bytes.size() <=
                    1024 * 1024
            ) {
                "ADB command produced too much output"
            }
        }

        val raw =
            bytes.toString(
                StandardCharsets.UTF_8
                    .name()
            )
        return raw
            .substringBefore(marker)
            .trim()
    } finally {
        runCatching {
            close()
        }
    }
}

private fun ByteArray.indexOfBytes(
    needle: ByteArray
): Int {
    if (needle.isEmpty()) {
        return 0
    }
    if (needle.size > size) {
        return -1
    }

    outer@ for (
        index in
        0..size - needle.size
    ) {
        for (
            offset in
            needle.indices
        ) {
            if (
                this[index + offset] !=
                needle[offset]
            ) {
                continue@outer
            }
        }
        return index
    }

    return -1
}

internal fun shellQuote(
    value: String
): String =
    "'" +
        value.replace(
            "'",
            "'\\''"
        ) +
        "'"
