package dev.balaji.moonrose.trainfree

import androidx.compose.runtime.Composable
import dev.balaji.moonrose.trainfree.model.Appearance

@Composable
expect fun PlatformTheme(appearance: Appearance = Appearance.SYSTEM, content: @Composable () -> Unit)

/** Resolves the appearance setting to dark-mode on/off; SYSTEM follows the OS. */
internal fun Appearance.isDark(systemInDarkTheme: Boolean): Boolean = when (this) {
    Appearance.DARK -> true
    Appearance.LIGHT -> false
    Appearance.SYSTEM -> systemInDarkTheme
}
