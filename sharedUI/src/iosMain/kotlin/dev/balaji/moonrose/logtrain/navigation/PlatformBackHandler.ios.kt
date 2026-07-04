package dev.balaji.moonrose.logtrain.navigation

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // The SwiftUI NavigationStack owns back navigation on iOS.
}
