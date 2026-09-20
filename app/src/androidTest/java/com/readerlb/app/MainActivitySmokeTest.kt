package com.readerlb.app

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule
    val composeRule =
        createAndroidComposeRule<MainActivity>()

    @Test
    fun firstLaunchCanReachHomeScreen() {
        composeRule
            .onNodeWithContentDescription(
                "Экран знакомства ReaderLB 1"
            )
            .assertExists()

        composeRule
            .onNodeWithText("Пропустить")
            .performClick()

        composeRule
            .onNodeWithText(
                "Импортер глав для"
            )
            .assertExists()

        composeRule
            .onNodeWithText(
                "Добавить новеллу",
                substring = true
            )
            .assertExists()
    }
}
