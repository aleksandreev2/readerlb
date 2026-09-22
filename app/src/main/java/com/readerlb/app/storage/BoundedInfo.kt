package com.readerlb.app.storage

import java.io.InputStream
import java.io.ByteArrayOutputStream

internal const val MAX_INFO_JSON_BYTES = 2 * 1024 * 1024

internal fun readBoundedInfo(input: InputStream, maxBytes: Int = MAX_INFO_JSON_BYTES): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        require(output.size() + count <= maxBytes) { "info.json слишком большой" }
        output.write(buffer, 0, count)
    }
    return output.toString(Charsets.UTF_8.name())
}
