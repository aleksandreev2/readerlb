package com.readerlb.app

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

    @get:Rule
    val composeRule =
        createAndroidComposeRule<MainActivity>()

    @Test
    fun launchCanReachHomeScreen() {
        val skipNodes =
            composeRule
                .onAllNodesWithText("Пропустить")
                .fetchSemanticsNodes()

        if (skipNodes.isNotEmpty()) {
            composeRule
                .onNodeWithContentDescription(
                    "Экран знакомства ReaderLB 1"
                )
                .fetchSemanticsNode()

            composeRule
                .onNodeWithText("Пропустить")
                .performClick()
        }

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
        val skipNodes =
            composeRule
                .onAllNodesWithText("Пропустить")
                .fetchSemanticsNodes()

        if (skipNodes.isNotEmpty()) {
            composeRule
                .onNodeWithText("Пропустить")
                .performClick()
        }

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

}