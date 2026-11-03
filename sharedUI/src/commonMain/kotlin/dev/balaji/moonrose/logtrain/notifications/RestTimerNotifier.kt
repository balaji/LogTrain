package dev.balaji.moonrose.logtrain.notifications

import androidx.compose.runtime.Composable
import dev.balaji.moonrose.logtrain.model.RestTimerSound

/**
 * Platform notifier for the between-set rest timer. Plays a notification bell
 * and vibrates when the countdown reaches zero, and posts a system notification
 * if the user has notifications enabled and the app is in the background.
 */
interface RestTimerNotifier {
    fun onRestTimerStarted(
        endsAtMillis: Long,
        notificationsEnabled: Boolean,
        sound: RestTimerSound,
        title: String,
        message: String,
        channelName: String,
    )

    fun onRestTimerCancelled()

    fun onRestTimerFinished(
        notificationsEnabled: Boolean,
        sound: RestTimerSound,
        title: String,
        message: String,
        channelName: String,
    )

    /** Plays [sound] immediately so the user can preview it in Settings. */
    fun previewSound(sound: RestTimerSound)
}

@Composable
expect fun rememberRestTimerNotifier(): RestTimerNotifier
