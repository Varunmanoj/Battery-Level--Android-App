package com.varunmanojkumar.batterylevel

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.firebase.analytics.FirebaseAnalytics
import com.varunmanojkumar.batterylevel.ui.theme.BatteryDimensions
import com.varunmanojkumar.batterylevel.ui.theme.BatteryLevelTheme
import com.varunmanojkumar.batterylevel.ui.theme.LocalBatteryLevelColors
import java.util.Locale

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class BatteryUiState(
    val level: Int = 0,
    val isCharging: Boolean = false,
)

internal fun normalizedBatteryLevel(level: Int, scale: Int): Int =
    if (scale <= 0) 0 else ((level * 100f) / scale).toInt().coerceIn(0, 100)

class MainActivity : ComponentActivity() {
    private var batteryState by mutableStateOf(BatteryUiState())
    private var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    private var textToSpeechReady by mutableStateOf(false)
    private var receiverRegistered = false
    private var textToSpeech: TextToSpeech? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            batteryState = if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                batteryStateFromIntent(intent)
            } else {
                readCurrentBatteryState()
            }

            if (intent.action == Intent.ACTION_POWER_CONNECTED ||
                intent.action == Intent.ACTION_POWER_DISCONNECTED
            ) {
                vibrate()
                announceBatteryState()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        FirebaseAnalytics.getInstance(this)
        themeMode = loadThemeMode()
        batteryState = readCurrentBatteryState()
        initializeTextToSpeech()

        setContent {
            BatteryLevelTheme(themeMode = themeMode) {
                BatteryLevelApp(
                    uiState = batteryState,
                    themeMode = themeMode,
                    canSpeak = textToSpeechReady,
                    onSpeak = ::speakBatteryLevel,
                    onRefresh = { batteryState = readCurrentBatteryState() },
                    onOpenTtsSettings = ::openTextToSpeechSettings,
                    onThemeSelected = ::selectTheme,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            ContextCompat.registerReceiver(
                this,
                batteryReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
        }
    }

    override fun onStop() {
        if (receiverRegistered) {
            unregisterReceiver(batteryReceiver)
            receiverRegistered = false
        }
        textToSpeech?.stop()
        super.onStop()
    }

    override fun onDestroy() {
        textToSpeech?.shutdown()
        textToSpeech = null
        super.onDestroy()
    }

    private fun initializeTextToSpeech() {
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val languageResult = textToSpeech?.setLanguage(Locale.getDefault())
                textToSpeechReady = languageResult != null &&
                    languageResult != TextToSpeech.LANG_MISSING_DATA &&
                    languageResult != TextToSpeech.LANG_NOT_SUPPORTED
            } else {
                textToSpeechReady = false
            }
        }
    }

    private fun speakBatteryLevel() {
        val text = getString(R.string.battery_level_description, batteryState.level)
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "battery-level")
    }

    private fun announceBatteryState() {
        val status = getString(
            if (batteryState.isCharging) R.string.charging else R.string.not_charging,
        )
        val text = getString(R.string.device_status_announcement, batteryState.level, status)
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "power-status")
    }

    private fun openTextToSpeechSettings() {
        val textToSpeechSettings = Intent("com.android.settings.TTS_SETTINGS")
        val destination = textToSpeechSettings.takeIf { it.resolveActivity(packageManager) != null }
            ?: Intent(Settings.ACTION_SETTINGS)
        startActivity(destination)
    }

    private fun selectTheme(mode: ThemeMode) {
        themeMode = mode
        getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE).edit {
            putString(THEME_KEY, mode.name)
        }
    }

    private fun loadThemeMode(): ThemeMode {
        val stored = getSharedPreferences(PREFERENCES_NAME, MODE_PRIVATE)
            .getString(THEME_KEY, ThemeMode.SYSTEM.name)
        return ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM
    }

    private fun readCurrentBatteryState(): BatteryUiState {
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return intent?.let(::batteryStateFromIntent) ?: batteryState
    }

    private fun batteryStateFromIntent(intent: Intent): BatteryUiState {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
        return BatteryUiState(
            level = normalizedBatteryLevel(level, scale),
            isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
        )
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            getSystemService(VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(200)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "battery-level-preferences"
        const val THEME_KEY = "theme-mode"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatteryLevelApp(
    uiState: BatteryUiState,
    themeMode: ThemeMode,
    canSpeak: Boolean,
    onSpeak: () -> Unit,
    onRefresh: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    onThemeSelected: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val batteryStyle = LocalBatteryLevelColors.current.forLevel(uiState.level)
    val backgroundColor by animateColorAsState(
        targetValue = batteryStyle.container,
        label = "battery background",
    )
    var themeMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = backgroundColor,
        contentColor = batteryStyle.content,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            painterResource(R.drawable.symbol_refresh),
                            contentDescription = stringResource(R.string.refresh),
                        )
                    }
                    IconButton(onClick = onOpenTtsSettings) {
                        Icon(
                            painterResource(R.drawable.symbol_settings_voice),
                            contentDescription = stringResource(R.string.tts_settings),
                        )
                    }
                    Box {
                        IconButton(onClick = { themeMenuExpanded = true }) {
                            Icon(
                                painterResource(R.drawable.symbol_brightness_auto),
                                contentDescription = stringResource(R.string.theme),
                            )
                        }
                        ThemeMenu(
                            expanded = themeMenuExpanded,
                            selectedMode = themeMode,
                            onDismiss = { themeMenuExpanded = false },
                            onSelected = {
                                themeMenuExpanded = false
                                onThemeSelected(it)
                            },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(BatteryDimensions.ScreenPadding),
            contentAlignment = Alignment.Center,
        ) {
            val contentModifier = Modifier
                .fillMaxSize()
                .widthIn(max = BatteryDimensions.ContentMaxWidth)
            if (maxWidth >= BatteryDimensions.TabletBreakpoint) {
                WideBatteryContent(
                    uiState = uiState,
                    canSpeak = canSpeak,
                    onSpeak = onSpeak,
                    modifier = contentModifier,
                )
            } else {
                CompactBatteryContent(
                    uiState = uiState,
                    canSpeak = canSpeak,
                    onSpeak = onSpeak,
                    modifier = contentModifier,
                )
            }
        }
    }
}

@Composable
private fun CompactBatteryContent(
    uiState: BatteryUiState,
    canSpeak: Boolean,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        BatteryReading(uiState)
        SpeakButton(canSpeak, onSpeak)
        ChargingStatus(uiState.isCharging)
    }
}

@Composable
private fun WideBatteryContent(
    uiState: BatteryUiState,
    canSpeak: Boolean,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BatteryDimensions.SectionSpacing),
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            BatteryReading(uiState)
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            SpeakButton(canSpeak, onSpeak)
            ChargingStatus(uiState.isCharging)
        }
    }
}

@Composable
private fun BatteryReading(uiState: BatteryUiState) {
    val description = stringResource(R.string.battery_level_description, uiState.level)
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = description
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BatteryDimensions.CompactSpacing),
    ) {
        Text(
            text = stringResource(R.string.battery_level),
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = stringResource(R.string.battery_percentage, uiState.level),
            style = MaterialTheme.typography.displayLarge,
        )
    }
}

@Composable
private fun SpeakButton(canSpeak: Boolean, onSpeak: () -> Unit) {
    Button(
        onClick = onSpeak,
        enabled = canSpeak,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BatteryDimensions.ButtonMinHeight),
    ) {
        Icon(
            painter = painterResource(R.drawable.symbol_volume_up),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.size(BatteryDimensions.CompactSpacing))
        Text(stringResource(R.string.speak_battery))
    }
}

@Composable
private fun ChargingStatus(isCharging: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(
            text = stringResource(if (isCharging) R.string.charging else R.string.not_charging),
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(BatteryDimensions.SectionSpacing),
        )
    }
}

@Composable
private fun ThemeMenu(
    expanded: Boolean,
    selectedMode: ThemeMode,
    onDismiss: () -> Unit,
    onSelected: (ThemeMode) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        ThemeMenuItem(ThemeMode.SYSTEM, selectedMode, R.string.theme_system, R.drawable.symbol_brightness_auto, onSelected)
        ThemeMenuItem(ThemeMode.LIGHT, selectedMode, R.string.theme_light, R.drawable.symbol_light_mode, onSelected)
        ThemeMenuItem(ThemeMode.DARK, selectedMode, R.string.theme_dark, R.drawable.symbol_dark_mode, onSelected)
    }
}

@Composable
private fun ThemeMenuItem(
    mode: ThemeMode,
    selectedMode: ThemeMode,
    label: Int,
    icon: Int,
    onSelected: (ThemeMode) -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = stringResource(label),
                fontWeight = if (mode == selectedMode) FontWeight.Bold else FontWeight.Normal,
            )
        },
        onClick = { onSelected(mode) },
        leadingIcon = {
            Icon(painterResource(icon), contentDescription = null)
        },
    )
}

@Preview(name = "Phone", device = Devices.PHONE, showSystemUi = true)
@Preview(name = "Tablet", device = Devices.TABLET, showSystemUi = true)
@Composable
private fun BatteryLevelPreview() {
    BatteryLevelTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false) {
        BatteryLevelApp(
            uiState = BatteryUiState(level = 72, isCharging = true),
            themeMode = ThemeMode.LIGHT,
            canSpeak = true,
            onSpeak = {},
            onRefresh = {},
            onOpenTtsSettings = {},
            onThemeSelected = {},
        )
    }
}
