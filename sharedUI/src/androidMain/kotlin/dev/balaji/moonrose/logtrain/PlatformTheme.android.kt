package dev.balaji.moonrose.logtrain

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Wallpaper-derived dynamic palettes often produce a muted primary that makes
// link-style text hard to spot, so pin primary to a vivid blue in both modes.
private val LightLinkBlue = Color(0xFF007AFF)
private val DarkLinkBlue = Color(0xFF0A84FF)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
actual fun PlatformTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> expressiveLightColorScheme()
    }.let {
        it.copy(
            primary = if (dark) DarkLinkBlue else LightLinkBlue,
            onPrimary = Color.White,
        )
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
