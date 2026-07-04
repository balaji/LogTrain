package dev.balaji.moonrose.logtrain.navigation

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Desktop has no system back gesture; screens render their own back buttons.
}
