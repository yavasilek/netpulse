package ru.yavasilek.netpulse.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = PulseGreen,
    onPrimary = Color.White,
    primaryContainer = PulseGreenLight,
    onPrimaryContainer = Color(0xFF002116),
    secondary = PulseBlue,
    background = LightBackground,
    surface = Color.White,
    error = PulseRed,
)

private val DarkColors = darkColorScheme(
    primary = PulseGreenDark,
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF00513B),
    onPrimaryContainer = PulseGreenLight,
    secondary = Color(0xFFBCC2FF),
    background = DarkBackground,
    surface = Color(0xFF171D19),
    error = Color(0xFFFFB4AB),
)

@Composable
fun NetPulseTheme(
    dynamicColor: Boolean,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = androidx.compose.material3.Typography(),
        content = content,
    )
}
