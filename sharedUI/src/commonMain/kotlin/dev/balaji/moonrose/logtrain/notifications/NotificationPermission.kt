package dev.balaji.moonrose.logtrain.notifications

import androidx.compose.runtime.Composable

/**
 * Returns a callback that asks the OS for permission to send notifications.
 * Invoked when the user turns the notifications setting on; the stored
 * setting is the user's intent, the OS grant is tracked by the platform.
 * No-op where there is no permission model (desktop) or below Android 13.
 */
@Composable
expect fun rememberNotificationPermissionRequester(): () -> Unit
