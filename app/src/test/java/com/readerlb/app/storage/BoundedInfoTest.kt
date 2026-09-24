package com.readerlb.app.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream

class BoundedInfoTest {
    @Test
    fun acceptsInfoAtLimit() {
        assertEquals("abc", readBoundedInfo(ByteArrayInputStream("abc".toByteArray()), 3))
    }

    @Test
    fun rejectsInfoAboveLimit() {
        try {
            readBoundedInfo(ByteArrayInputStream("abcd".toByteArray()), 3)
            fail("Expected info limit")
        } catch (_: IllegalArgumentException) {
        }
    }
}
