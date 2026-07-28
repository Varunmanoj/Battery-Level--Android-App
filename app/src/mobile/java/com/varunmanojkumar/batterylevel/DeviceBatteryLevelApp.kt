package com.varunmanojkumar.batterylevel

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun DeviceBatteryLevelApp(
    uiState: BatteryUiState,
    themeMode: ThemeMode,
    canSpeak: Boolean,
    onSpeak: () -> Unit,
    onRefresh: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    BatteryLevelApp(
        uiState = uiState,
        themeMode = themeMode,
        canSpeak = canSpeak,
        onSpeak = onSpeak,
        onRefresh = onRefresh,
        onOpenTtsSettings = onOpenTtsSettings,
        onThemeSelected = onThemeSelected,
        modifier = modifier,
    )
}
