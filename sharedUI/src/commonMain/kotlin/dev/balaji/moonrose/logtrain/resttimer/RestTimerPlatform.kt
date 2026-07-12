package dev.balaji.moonrose.logtrain.resttimer

import androidx.compose.runtime.Composable

/**
 * Platform bell/vibration/notification delivery for the rest timer, installed
 * into [dev.balaji.moonrose.logtrain.db.RestTimerController] from App(). The
 * iOS actual is a no-op: the native SwiftUI shell injects its own Swift
 * implementation instead.
 */
@Composable
expect fun rememberRestTimerAlerts(): RestTimerAlerts
