package com.readerlb.app.access

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderLbAdbClientTest {
    @Test
    fun shellQuoteHandlesSpacesAndApostrophes() {
        assertEquals(
            "'/data/app/Reader LB/base.apk'",
            shellQuote(
                "/data/app/Reader LB/base.apk"
            )
        )
        assertEquals(
            "'a'\\''b'",
            shellQuote("a'b")
        )
    }
}
