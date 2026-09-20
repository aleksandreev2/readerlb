package com.readerlb.app.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {

    @Test
    fun semanticVersionComparisonUsesNumericSegments() {
        assertTrue(
            isVersionNewer(
                latest = "0.5.0",
                current = "0.4.2"
            )
        )
        assertTrue(
            isVersionNewer(
                latest = "0.10.0",
                current = "0.9.9"
            )
        )
        assertFalse(
            isVersionNewer(
                latest = "0.4.2",
                current = "0.4.2"
            )
        )
        assertFalse(
            isVersionNewer(
                latest = "0.4.1",
                current = "0.4.2"
            )
        )
    }
}
