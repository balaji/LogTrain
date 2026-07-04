package dev.balaji.moonrose.logtrain

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val iosLightColorScheme = lightColorScheme(
    primary = Color(0xFF007AFF),
    primaryContainer = Color(0xFFD6E7FF),
)

private val iosDarkColorScheme = darkColorScheme(
    primary = Color(0xFF0A84FF),
)

@Composable
actual fun PlatformTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) iosDarkColorScheme else iosLightColorScheme,
        content = content,
    )
}
