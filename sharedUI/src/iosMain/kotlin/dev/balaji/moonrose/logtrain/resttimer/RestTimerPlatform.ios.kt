package dev.balaji.moonrose.logtrain.resttimer

import androidx.compose.runtime.Composable

/**
 * No-op: the Compose UI only runs on Android. The native SwiftUI shell
 * injects its own RestTimerAlerts (RestTimerAlertsProvider.swift) into
 * RestTimerController at app start.
 */
@Composable
actual fun rememberRestTimerAlerts(): RestTimerAlerts = NoopRestTimerAlerts

private object NoopRestTimerAlerts : RestTimerAlerts {
    override fun timerFinished(): Boolean = false

    override fun scheduleExpiryNotification(endsAtMillis: Long) {}

    override fun cancelExpiryNotification() {}
}
