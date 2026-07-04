package dev.balaji.moonrose.logtrain

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// M3's default neutral tokens (Surface, OutlineVariant, etc.) are derived from
// a purple seed color, not true grays — left alone they tint ListItem rows,
// dividers, and secondary text/icons lavender instead of iOS system neutrals.
// Values below are Apple's system color constants (systemBackground, label,
// secondaryLabel, separator, secondarySystemBackground).
private val iosLightColorScheme = lightColorScheme(
    primary = Color(0xFF007AFF),
    primaryContainer = Color(0xFFD6E7FF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFF2F2F7),
    onSurfaceVariant = Color(0xFF6C6C70),
    outline = Color(0xFFC6C6C8),
    outlineVariant = Color(0xFFE5E5EA),
)

private val iosDarkColorScheme = darkColorScheme(
    primary = Color(0xFF0A84FF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFFEBEBF0),
    outline = Color(0xFF545458),
    outlineVariant = Color(0xFF38383A),
)

@Composable
actual fun PlatformTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) iosDarkColorScheme else iosLightColorScheme,
        content = content,
    )
}
