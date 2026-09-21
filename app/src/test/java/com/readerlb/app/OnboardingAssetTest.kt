package com.readerlb.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Base64

class OnboardingAssetTest {

    @Test
    fun allThreeOnboardingPanelsAreValidWebPBase64Assets() {
        (1..3).forEach { index ->
            val file = assetFile(
                "onboarding_${index}.webp.b64"
            )
            val encoded = file.readText()
                .trim()

            assertTrue(
                "Onboarding asset $index is unexpectedly small",
                encoded.length > 10_000
            )

            val bytes = Base64
                .getDecoder()
                .decode(encoded)

            assertTrue(
                "Decoded onboarding asset $index is too small",
                bytes.size > 8_000
            )
            assertEquals(
                "RIFF",
                bytes.copyOfRange(0, 4)
                    .toString(Charsets.US_ASCII)
            )
            assertEquals(
                "WEBP",
                bytes.copyOfRange(8, 12)
                    .toString(Charsets.US_ASCII)
            )
        }
    }

    private fun assetFile(
        name: String
    ): File {
        val candidates = listOf(
            File(
                "src/main/assets",
                name
            ),
            File(
                "app/src/main/assets",
                name
            )
        )

        return candidates.firstOrNull(File::isFile)
            ?: error(
                "Onboarding asset not found: $name"
            )
    }
}
