package com.varunmanojkumar.batterylevel.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.varunmanojkumar.batterylevel.ThemeMode

private val Purple = Color(0xFF6200EE)
private val PurpleDark = Color(0xFF3700B3)
private val PurpleLight = Color(0xFFBB86FC)
private val Teal = Color(0xFF018786)

private val LightColorScheme = lightColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    primaryContainer = PurpleDark,
    onPrimaryContainer = Color.White,
    secondary = Teal,
)

private val DarkColorScheme = darkColorScheme(
    primary = PurpleLight,
    onPrimary = Color(0xFF23005C),
    primaryContainer = PurpleDark,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF4FD8D1),
)

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 72.sp,
        lineHeight = 80.sp,
    ),
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
    ),
)

object BatteryDimensions {
    val ScreenPadding = 24.dp
    val SectionSpacing = 24.dp
    val CompactSpacing = 12.dp
    val ContentMaxWidth = 960.dp
    val ButtonMinHeight = 64.dp
    val TabletBreakpoint = 600.dp
}

@Immutable
data class BatteryContainerStyle(
    val container: Color,
    val content: Color,
)

@Immutable
data class BatteryLevelColors(
    val high: BatteryContainerStyle,
    val medium: BatteryContainerStyle,
    val low: BatteryContainerStyle,
    val critical: BatteryContainerStyle,
) {
    fun forLevel(level: Int): BatteryContainerStyle = when {
        level > 90 -> high
        level > 50 -> medium
        level > 15 -> low
        else -> critical
    }
}

private val LightBatteryColors = BatteryLevelColors(
    high = BatteryContainerStyle(Color(0xFFADEE14), Color(0xFF1A1C16)),
    medium = BatteryContainerStyle(Color(0xFF8597FC), Color(0xFF101A43)),
    low = BatteryContainerStyle(Color(0xFFFFEB3B), Color(0xFF1D1B00)),
    critical = BatteryContainerStyle(Color(0xFFF44336), Color(0xFF210100)),
)

private val DarkBatteryColors = BatteryLevelColors(
    high = BatteryContainerStyle(Color(0xFF147500), Color.White),
    medium = BatteryContainerStyle(Color(0xFF3F51B5), Color.White),
    low = BatteryContainerStyle(Color(0xFF877417), Color.White),
    critical = BatteryContainerStyle(Color(0xFFB3261E), Color.White),
)

val LocalBatteryLevelColors = staticCompositionLocalOf { LightBatteryColors }

@Composable
fun BatteryLevelTheme(
    themeMode: ThemeMode,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(
        LocalBatteryLevelColors provides if (darkTheme) DarkBatteryColors else LightBatteryColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
