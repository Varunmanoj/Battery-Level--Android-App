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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.firebase.analytics.FirebaseAnalytics
import com.varunmanojkumar.batterylevel.ui.theme.BatteryDimensions
import com.varunmanojkumar.batterylevel.ui.theme.BatteryLevelTheme
import java.util.Locale

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class BatteryUiState(
    val level: Int = 0,
    val isCharging: Boolean = false,
    val isFullyCharged: Boolean = false,
)

internal fun normalizedBatteryLevel(level: Int, scale: Int): Int =
    if (scale <= 0) 0 else ((level * 100f) / scale).toInt().coerceIn(0, 100)

internal fun batteryUiState(level: Int, scale: Int, status: Int): BatteryUiState {
    val normalizedLevel = normalizedBatteryLevel(level, scale)
    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL
    return BatteryUiState(
        level = normalizedLevel,
        isCharging = isCharging,
        isFullyCharged = normalizedLevel == 100 && isCharging,
    )
}

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
            val currentBatteryIntent = ContextCompat.registerReceiver(
                this,
                batteryReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            receiverRegistered = true
            if (currentBatteryIntent?.action == Intent.ACTION_BATTERY_CHANGED) {
                batteryState = batteryStateFromIntent(currentBatteryIntent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        batteryState = readCurrentBatteryState()
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
            when {
                batteryState.isFullyCharged -> R.string.fully_charged
                batteryState.isCharging -> R.string.charging
                else -> R.string.not_charging
            },
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
        return batteryUiState(level = level, scale = scale, status = status)
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
    var settingsVisible by rememberSaveable { mutableStateOf(false) }
    val isWearSized = LocalConfiguration.current.screenWidthDp < 300

    if (settingsVisible) {
        SettingsPage(
            selectedMode = themeMode,
            isWearSized = isWearSized,
            onBack = { settingsVisible = false },
            onSelected = onThemeSelected,
            onRefresh = onRefresh,
            onOpenTtsSettings = onOpenTtsSettings,
            modifier = modifier,
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            if (isWearSized) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = BatteryDimensions.WearHeaderTopInset),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        IconButton(onClick = { settingsVisible = true }) {
                            Icon(
                                painter = painterResource(R.drawable.symbol_settings),
                                contentDescription = stringResource(R.string.settings),
                            )
                        }
                    }
                }
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    actions = {
                        IconButton(onClick = { settingsVisible = true }) {
                            Icon(
                                painter = painterResource(R.drawable.symbol_settings),
                                contentDescription = stringResource(R.string.settings),
                            )
                        }
                    },
                )
            }
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
            if (maxWidth < BatteryDimensions.WearBreakpoint) {
                WearBatteryContent(
                    uiState = uiState,
                    canSpeak = canSpeak,
                    onSpeak = onSpeak,
                    modifier = contentModifier,
                )
            } else if (maxWidth >= BatteryDimensions.TabletBreakpoint) {
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
private fun SettingsPage(
    selectedMode: ThemeMode,
    isWearSized: Boolean,
    onBack: () -> Unit,
    onSelected: (ThemeMode) -> Unit,
    onRefresh: () -> Unit,
    onOpenTtsSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = if (isWearSized) 32.dp else 24.dp,
                        vertical = 24.dp,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(BatteryDimensions.CompactSpacing),
            ) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.Start),
                ) {
                    Text(stringResource(R.string.back))
                }
                Text(
                    text = stringResource(R.string.settings),
                    style = MaterialTheme.typography.headlineMedium,
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
                    modifier = Modifier.padding(top = 4.dp),
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
                Text(
                    text = stringResource(R.string.app_info),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                AppInfo(versionName = BuildConfig.VERSION_NAME)
            }
        }
    }
}

@Composable
private fun AppInfo(versionName: String) {
    val versionDescription = stringResource(R.string.version_name_description, versionName)
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = versionDescription
                }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.version_name),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = versionName,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun SettingsAction(
    label: Int,
    icon: Int,
    onClick: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
            )
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun ThemeOption(
    mode: ThemeMode,
    selectedMode: ThemeMode,
    label: Int,
    onSelected: (ThemeMode) -> Unit,
) {
    val selected = mode == selectedMode
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable { onSelected(mode) }
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null)
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun WearBatteryContent(
    uiState: BatteryUiState,
    canSpeak: Boolean,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val viewportHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BatteryDimensions.CompactSpacing),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(viewportHeight),
                contentAlignment = Alignment.Center,
            ) {
                BatteryReading(uiState = uiState, compactTypography = true)
            }
            SpeakButton(canSpeak = canSpeak, onSpeak = onSpeak, compact = true)
            ChargingStatus(uiState = uiState, compact = true)
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
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BatteryDimensions.SectionSpacing),
    ) {
        BatteryReading(uiState)
        ChargingStatus(uiState)
        SpeakButton(canSpeak, onSpeak)
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
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BatteryDimensions.SectionSpacing),
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            BatteryReading(uiState)
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(BatteryDimensions.SectionSpacing),
        ) {
            ChargingStatus(uiState)
            SpeakButton(canSpeak, onSpeak)
        }
    }
}

@Composable
private fun BatteryReading(
    uiState: BatteryUiState,
    compactTypography: Boolean = false,
) {
    val description = stringResource(R.string.battery_level_description, uiState.level)
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = description
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            if (compactTypography) 4.dp else BatteryDimensions.CompactSpacing,
        ),
    ) {
        Text(
            text = stringResource(R.string.battery_level),
            style = if (compactTypography) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.headlineLarge
            },
            maxLines = 1,
        )
        Text(
            text = stringResource(R.string.battery_percentage, uiState.level),
            style = if (compactTypography) {
                MaterialTheme.typography.displayLarge.copy(
                    fontSize = 64.sp,
                    lineHeight = 72.sp,
                )
            } else {
                MaterialTheme.typography.displayLarge
            },
            maxLines = 1,
        )
    }
}

@Composable
private fun SpeakButton(
    canSpeak: Boolean,
    onSpeak: () -> Unit,
    compact: Boolean = false,
) {
    val speakDescription = stringResource(R.string.speak_battery_description)
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val buttonContainerColor = if (isDarkTheme) Color.White else Color.Black
    val buttonContentColor = if (isDarkTheme) Color.Black else Color.White
    Button(
        onClick = onSpeak,
        enabled = canSpeak,
        colors = ButtonDefaults.buttonColors(
            containerColor = buttonContainerColor,
            contentColor = buttonContentColor,
            disabledContainerColor = buttonContainerColor,
            disabledContentColor = buttonContentColor.copy(alpha = 0.72f),
        ),
        shape = MaterialTheme.shapes.large,
        contentPadding = if (compact) {
            PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        } else {
            PaddingValues(horizontal = 32.dp, vertical = 24.dp)
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(
                min = if (compact) {
                    BatteryDimensions.WearButtonMinHeight
                } else {
                    BatteryDimensions.ButtonMinHeight
                },
            )
            .semantics { contentDescription = speakDescription },
    ) {
        Icon(
            painter = painterResource(R.drawable.symbol_volume_up),
            contentDescription = null,
            modifier = Modifier.size(if (compact) 28.dp else 40.dp),
        )
        Spacer(Modifier.size(if (compact) 4.dp else BatteryDimensions.CompactSpacing))
        Text(
            text = stringResource(R.string.speak_battery),
            style = if (compact) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.headlineMedium
            },
            fontWeight = FontWeight.Black,
            maxLines = 1,
        )
    }
}

@Composable
private fun ChargingStatus(
    uiState: BatteryUiState,
    compact: Boolean = false,
) {
    if (!uiState.isCharging) return

    val chargingText = stringResource(
        if (uiState.isFullyCharged) R.string.fully_charged else R.string.charging,
    )
    val (compactFontSize, compactLineHeight) = when {
        chargingText.length > 14 -> 22.sp to 26.sp
        chargingText.length > 10 -> 26.sp to 30.sp
        else -> 36.sp to 40.sp
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Text(
            text = chargingText,
            style = if (compact) {
                MaterialTheme.typography.headlineLarge.copy(
                    fontSize = compactFontSize,
                    lineHeight = compactLineHeight,
                )
            } else {
                MaterialTheme.typography.headlineLarge
            },
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(
                    if (compact) {
                        BatteryDimensions.WearStatusPadding
                    } else {
                        BatteryDimensions.SectionSpacing
                    },
                ),
        )
    }
}

@Preview(name = "Phone", device = Devices.PHONE, showSystemUi = true)
@Preview(name = "Phone - 200% font", device = Devices.PHONE, fontScale = 2f, showSystemUi = true)
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
