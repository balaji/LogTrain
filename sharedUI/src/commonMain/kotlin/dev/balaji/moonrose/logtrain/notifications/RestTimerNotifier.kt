package dev.balaji.moonrose.logtrain.notifications

import androidx.compose.runtime.Composable

/**
 * Platform notifier for the between-set rest timer. Plays a notification bell
 * and vibrates when the countdown reaches zero, and posts a system notification
 * if the user has notifications enabled and the app is in the background.
 */
interface RestTimerNotifier {
    fun onRestTimerStarted(
        endsAtMillis: Long,
        notificationsEnabled: Boolean,
        title: String,
        message: String,
        channelName: String,
    )

    fun onRestTimerCancelled()

    fun onRestTimerFinished(
        notificationsEnabled: Boolean,
        title: String,
        message: String,
        channelName: String,
    )
}

@Composable
expect fun rememberRestTimerNotifier(): RestTimerNotifier
