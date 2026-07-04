package dev.balaji.moonrose.logtrain.navigation

import androidx.compose.runtime.Composable

/**
 * System back integration. Android hooks the (predictive) back gesture;
 * iOS and Desktop are no-ops (SwiftUI owns back on iOS, Desktop has none).
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)
