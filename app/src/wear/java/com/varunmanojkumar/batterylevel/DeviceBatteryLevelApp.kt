package com.varunmanojkumar.batterylevel

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.wear.compose.material3.ScreenScaffold
import com.varunmanojkumar.batterylevel.ui.theme.BatteryDimensions

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
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    key(settingsVisible) {
        if (settingsVisible) {
            WearSettingsPage(
                selectedMode = themeMode,
                onBack = { settingsVisible = false },
                onSelected = onThemeSelected,
                onRefresh = onRefresh,
                onOpenTtsSettings = onOpenTtsSettings,
                modifier = modifier,
            )
        } else {
            WearHomePage(
                uiState = uiState,
                canSpeak = canSpeak,
                onSpeak = onSpeak,
                onOpenSettings = { settingsVisible = true },
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun WearHomePage(
    uiState: BatteryUiState,
    canSpeak: Boolean,
    onSpeak: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    ScreenScaffold(
        scrollState = scrollState,
        modifier = modifier.fillMaxSize(),
    ) { contentPadding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val viewportHeight = maxHeight
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(BatteryDimensions.CompactSpacing),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(contentPadding)
                    .padding(
                        horizontal = maxWidth * 0.10f,
                        vertical = BatteryDimensions.CompactSpacing,
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(viewportHeight - (BatteryDimensions.CompactSpacing * 2)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                painter = painterResource(R.drawable.symbol_settings),
                                contentDescription = stringResource(R.string.settings),
                            )
                        }
                    }
                    Spacer(Modifier.height(BatteryDimensions.CompactSpacing))
                    BatteryReading(uiState = uiState, compactTypography = true)
                }
                SpeakButton(canSpeak = canSpeak, onSpeak = onSpeak, compact = true)
                ChargingStatus(uiState = uiState, compact = true)
                Spacer(Modifier.height(BatteryDimensions.CompactSpacing))
            }
        }
    }
}

@Composable
private fun WearSettingsPage(
    selectedMode: ThemeMode,
    onBack: () -> Unit,
    onSelected: (ThemeMode) -> Unit,
    onRefresh: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val scrollState = rememberScrollState()
    ScreenScaffold(
        scrollState = scrollState,
        modifier = modifier.fillMaxSize(),
    ) { contentPadding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(BatteryDimensions.CompactSpacing),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(contentPadding)
                    .padding(horizontal = maxWidth * 0.10f),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        painter = painterResource(R.drawable.symbol_arrow_back),
                        contentDescription = stringResource(R.string.back),
                    )
                }
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                SettingsAction(
                    label = R.string.refresh,
                    icon = R.drawable.symbol_refresh,
                    onClick = onRefresh,
                )
                SettingsAction(
                    label = R.string.tts_settings_short,
                    icon = R.drawable.symbol_settings_voice,
                    onClick = onOpenTtsSettings,
                )
                Text(
                    text = stringResource(R.string.theme),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                ThemeOption(
                    mode = ThemeMode.SYSTEM,
                    selectedMode = selectedMode,
                    label = R.string.theme_system_short,
                    onSelected = onSelected,
                )
                ThemeOption(
                    mode = ThemeMode.LIGHT,
                    selectedMode = selectedMode,
                    label = R.string.theme_light,
                    onSelected = onSelected,
                )
                ThemeOption(
                    mode = ThemeMode.DARK,
                    selectedMode = selectedMode,
                    label = R.string.theme_dark,
                    onSelected = onSelected,
                )
                AppInfo(versionName = BuildConfig.VERSION_NAME)
            }
        }
    }
}
