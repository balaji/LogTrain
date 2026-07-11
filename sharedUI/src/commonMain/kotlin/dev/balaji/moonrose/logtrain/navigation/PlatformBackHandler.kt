package dev.balaji.moonrose.logtrain.navigation

import androidx.compose.runtime.Composable

/**
 * System back integration. Android hooks the (predictive) back gesture;
 * iOS is a no-op (SwiftUI owns back on iOS).
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
