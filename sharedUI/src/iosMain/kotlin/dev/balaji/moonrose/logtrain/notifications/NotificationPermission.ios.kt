package dev.balaji.moonrose.logtrain.notifications

import androidx.compose.runtime.Composable
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter

// Only reachable from legacy Compose-hosted screens; the native SwiftUI
// ProfileView requests authorization itself.
@Composable
actual fun rememberNotificationPermissionRequester(): () -> Unit = {
    UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
        UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound,
    ) { _, _ -> }
}
