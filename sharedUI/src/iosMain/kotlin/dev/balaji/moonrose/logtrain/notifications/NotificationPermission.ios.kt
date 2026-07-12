package dev.balaji.moonrose.logtrain.notifications

import androidx.compose.runtime.Composable
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenNotificationSettingsURLString
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

// Only reachable from legacy Compose-hosted screens; the native SwiftUI
// ProfileView requests authorization itself.
@Composable
actual fun rememberNotificationPermissionRequester(
    onResult: (NotificationPermissionResult) -> Unit,
): () -> Unit = {
    val center = UNUserNotificationCenter.currentNotificationCenter()
    center.getNotificationSettingsWithCompletionHandler { settings ->
        // Completion handlers arrive on a background queue.
        when (settings?.authorizationStatus) {
            UNAuthorizationStatusNotDetermined -> center.requestAuthorizationWithOptions(
                UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound,
            ) { granted, _ ->
                // A denial at the prompt is final on iOS, but the user just
                // answered it — save the hint for the next attempt.
                val result = if (granted) NotificationPermissionResult.Granted else NotificationPermissionResult.Denied
                dispatch_async(dispatch_get_main_queue()) { onResult(result) }
            }
            UNAuthorizationStatusDenied -> dispatch_async(dispatch_get_main_queue()) {
                onResult(NotificationPermissionResult.DeniedPermanently)
            }
            else -> dispatch_async(dispatch_get_main_queue()) {
                onResult(NotificationPermissionResult.Granted)
            }
        }
    }
}

@Composable
actual fun rememberNotificationSettingsOpener(): () -> Unit = {
    NSURL.URLWithString(UIApplicationOpenNotificationSettingsURLString)?.let { url ->
        UIApplication.sharedApplication.openURL(url, emptyMap<Any?, Any>(), null)
    }
}
