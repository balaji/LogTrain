package dev.balaji.moonrose.trainfree.navigation

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * True when the platform shell (SwiftUI on iOS) owns navigation chrome.
 * Shared screens must not render their own top bars or back buttons then.
 */
val LocalUseNativeNavigation = staticCompositionLocalOf { false }
