package dev.balaji.moonrose.logtrain

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.expressiveLightColorScheme
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
actual fun PlatformTheme(content: @Composable () -> Unit) {
    MaterialExpressiveTheme(
        colorScheme = expressiveLightColorScheme(),
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}
