package com.varunmanojkumar.batterylevel

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import com.varunmanojkumar.batterylevel.ui.theme.BatteryLevelTheme
import org.junit.Rule
import org.junit.Test

class BatteryLevelScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displaysAccessibleBatteryStatusAndActions() {
        composeRule.setContent {
            BatteryLevelTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false) {
                BatteryLevelApp(
                    uiState = BatteryUiState(level = 37, isCharging = false),
                    themeMode = ThemeMode.LIGHT,
                    canSpeak = true,
                    onSpeak = {},
                    onRefresh = {},
                    onOpenTtsSettings = {},
                    onThemeSelected = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Battery level 37 percent").assertIsDisplayed()
        composeRule.onNodeWithText("37%").assertIsDisplayed()
        composeRule.onAllNodesWithText("Not charging").assertCountEquals(0)
        composeRule.onNodeWithText("Speak it").assertHasClickAction()
        val wearSettings = composeRule.onAllNodesWithContentDescription("Settings")
            .fetchSemanticsNodes()
        if (wearSettings.isNotEmpty()) {
            composeRule.onNodeWithContentDescription("Settings").performClick()
            composeRule.onNodeWithText("Refresh battery status").assertHasClickAction()
            composeRule.onNodeWithText("TTS settings").assertHasClickAction()
        } else {
            composeRule.onNodeWithContentDescription("Refresh battery status")
                .assertHasClickAction()
            composeRule.onNodeWithContentDescription("Text-to-speech settings")
                .assertHasClickAction()
        }
    }
}
