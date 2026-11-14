package dev.balaji.moonrose.trainfree.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.balaji.moonrose.trainfree.model.RestTimerSound

// Compose UI is Android-only; iOS handles rest-timer notifications natively in SwiftUI.
@Composable
actual fun rememberRestTimerNotifier(): RestTimerNotifier = remember {
    object : RestTimerNotifier {
        override fun onRestTimerStarted(
            endsAtMillis: Long,
            notificationsEnabled: Boolean,
            sound: RestTimerSound,
            title: String,
            message: String,
            channelName: String,
        ) {}

        override fun onRestTimerCancelled() {}

        override fun onRestTimerFinished(
            notificationsEnabled: Boolean,
            sound: RestTimerSound,
            title: String,
            message: String,
            channelName: String,
        ) {}

        override fun previewSound(sound: RestTimerSound) {}
    }
}
