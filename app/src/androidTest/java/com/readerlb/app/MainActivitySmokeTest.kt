package com.readerlb.app

import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @Test
    fun modernAndroidExplainsRestrictedRanobeLibAccessUpFront() {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.R
        ) {
            return
        }

        skipOnboardingIfNeeded()

        composeRule
            .onNodeWithText(
                "Доступ к RanobeLib"
            )
            .fetchSemanticsNode()
        composeRule
            .onNodeWithText(
                "Android защищает папку RanobeLib"
            )
            .fetchSemanticsNode()
        composeRule
            .onNodeWithText(
                "Работать через Downloads"
            )
            .fetchSemanticsNode()
        assertTrue(
            composeRule
                .onAllNodesWithText(
                    "Дать доступ к RanobeLib"
                )
                .fetchSemanticsNodes()
                .isEmpty()
        )

        composeRule
            .onNodeWithText(
                "Работать через Downloads"
            )
            .performClick()

        composeRule
            .onNodeWithText(
                "Настройки"
            )
            .performClick()
        composeRule
            .onNodeWithText(
                "Настроить доступ"
            )
            .fetchSemanticsNode()
    }

    @get:Rule
    val composeRule =
        createAndroidComposeRule<MainActivity>()

    @Test
    fun launchCanReachHomeScreen() {
        skipOnboardingIfNeeded()
        dismissAccessSetupIfNeeded()

        composeRule
            .onNodeWithText(
                "Импортер глав для"
            )
            .fetchSemanticsNode()

        val addNovelNodes =
            composeRule
                .onAllNodesWithText(
                    "Добавить новеллу",
                    substring = true
                )
                .fetchSemanticsNodes()

        assertTrue(
            "Home screen should expose the add-novel action",
            addNovelNodes.isNotEmpty()
        )
    }

    @Test
    fun emptyImportHeroIsCenteredInTheScreen() {
        skipOnboardingIfNeeded()
        dismissAccessSetupIfNeeded()

        composeRule
            .onAllNodesWithText(
                "Добавить новеллу",
                substring = true
            )[0]
            .performClick()

        val rootBounds =
            composeRule
                .onRoot()
                .fetchSemanticsNode()
                .boundsInRoot
        val heroBounds =
            composeRule
                .onNodeWithTag(
                    "import-file-hero"
                )
                .fetchSemanticsNode()
                .boundsInRoot

        val tolerancePx =
            24f *
                composeRule.activity
                    .resources
                    .displayMetrics
                    .density

        assertTrue(
            "The empty import hero should stay centered regardless of optional content",
            abs(
                heroBounds.center.y -
                    rootBounds.center.y
            ) <= tolerancePx
        )
    }

    @Test
    fun systemBackReturnsFromSettingsToHome() {
        skipOnboardingIfNeeded()
        dismissAccessSetupIfNeeded()

        composeRule
            .onNodeWithContentDescription(
                "Настройки"
            )
            .performClick()

        composeRule.waitForIdle()
        composeRule
            .onNodeWithText("Лимиты EPUB")
            .fetchSemanticsNode()

        composeRule.activity.runOnUiThread {
            composeRule.activity
                .onBackPressedDispatcher
                .onBackPressed()
        }
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText("Импортер глав для")
            .fetchSemanticsNode()
    }

    @Test
    fun systemBackReturnsFromImportToHome() {
        skipOnboardingIfNeeded()
        dismissAccessSetupIfNeeded()

        composeRule
            .onAllNodesWithText(
                "Добавить новеллу",
                substring = true
            )[0]
            .performClick()

        composeRule.waitForIdle()
        composeRule
            .onNodeWithTag(
                "import-file-hero"
            )
            .fetchSemanticsNode()

        composeRule.activity.runOnUiThread {
            composeRule.activity
                .onBackPressedDispatcher
                .onBackPressed()
        }
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText("Импортер глав для")
            .fetchSemanticsNode()
    }

    private fun skipOnboardingIfNeeded() {
        if (
            composeRule
                .onAllNodesWithText(
                    "Пропустить"
                )
                .fetchSemanticsNodes()
                .isNotEmpty()
        ) {
            composeRule
                .onNodeWithText(
                    "Пропустить"
                )
                .performClick()
            composeRule.waitForIdle()
        }
    }

    private fun dismissAccessSetupIfNeeded() {
        val modern =
            composeRule
                .onAllNodesWithText(
                    "Работать через Downloads"
                )
                .fetchSemanticsNodes()

        if (modern.isNotEmpty()) {
            composeRule
                .onNodeWithText(
                    "Работать через Downloads"
                )
                .performClick()
            composeRule.waitForIdle()
            return
        }

        val legacy =
            composeRule
                .onAllNodesWithText(
                    "Пока работать через Downloads"
                )
                .fetchSemanticsNodes()

        if (legacy.isNotEmpty()) {
            composeRule
                .onNodeWithText(
                    "Пока работать через Downloads"
                )
                .performClick()
            composeRule.waitForIdle()
        }
    }

}
