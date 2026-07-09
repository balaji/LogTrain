package dev.balaji.moonrose.logtrain

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable
import dev.balaji.moonrose.logtrain.model.Appearance

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
actual fun PlatformTheme(appearance: Appearance, content: @Composable () -> Unit) {
    val dark = appearance.isDark(isSystemInDarkTheme())
    MaterialExpressiveTheme(
        colorScheme = if (dark) darkColorScheme() else expressiveLightColorScheme(),
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
