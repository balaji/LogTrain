package dev.balaji.moonrose.logtrain.notifications

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private const val REST_TIMER_CHANNEL_ID = "rest_timer"
private const val REST_TIMER_NOTIFICATION_ID = 2001

@Composable
actual fun rememberRestTimerNotifier(): RestTimerNotifier {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidRestTimerNotifier(context) }
}

private class AndroidRestTimerNotifier(private val context: Context) : RestTimerNotifier {
    override fun onRestTimerStarted(
        endsAtMillis: Long,
        notificationsEnabled: Boolean,
        title: String,
        message: String,
        channelName: String,
    ) {
        cancelPostedNotification()
    }

    override fun onRestTimerCancelled() {
        cancelPostedNotification()
    }

    override fun onRestTimerFinished(
        notificationsEnabled: Boolean,
        title: String,
        message: String,
        channelName: String,
    ) {
        val foreground = isAppInForeground()
        if (!foreground && notificationsEnabled && canPostNotifications()) {
            postNotification(title = title, message = message, channelName = channelName)
            // Ensure the vibration and bell still fire even if the notification
            // channel is muted by the user or OS.
            vibrate()
            return
        }
        playBellSound()
        vibrate()
    }

    private fun playBellSound() {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: return
            val ringtone = RingtoneManager.getRingtone(context, uri) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            ringtone.play()
        }
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            if (vibrator == null || !vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createWaveform(longArrayOf(0L, 250L, 120L, 250L), -1),
                )
            } else {
                vibrator.vibrate(longArrayOf(0L, 250L, 120L, 250L), -1)
            }
        }
    }

    private fun isAppInForeground(): Boolean {
        val processInfo = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(processInfo)
        return processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
            processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }

    private fun canPostNotifications(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    @Suppress("DEPRECATION")
    private fun postNotification(title: String, message: String, channelName: String) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val channel = NotificationChannel(
                    REST_TIMER_CHANNEL_ID,
                    channelName,
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0L, 250L, 120L, 250L)
                    if (soundUri != null) {
                        setSound(soundUri, audioAttributes)
                    }
                }
                nm.createNotificationChannel(channel)
            }

            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pendingIntent = launchIntent?.let {
                PendingIntent.getActivity(
                    context,
                    0,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.Notification.Builder(context, REST_TIMER_CHANNEL_ID)
            } else {
                android.app.Notification.Builder(context)
                    .setPriority(android.app.Notification.PRIORITY_HIGH)
                    .setVibrate(longArrayOf(0L, 250L, 120L, 250L))
                    .apply { if (soundUri != null) setSound(soundUri) }
            }

            val iconRes = context.applicationInfo.icon.takeIf { it != 0 }
                ?: android.R.drawable.ic_lock_idle_alarm
            builder
                .setSmallIcon(iconRes)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .setCategory(android.app.Notification.CATEGORY_ALARM)
            if (pendingIntent != null) {
                builder.setContentIntent(pendingIntent)
            }
            nm.notify(REST_TIMER_NOTIFICATION_ID, builder.build())
        }
    }

    private fun cancelPostedNotification() {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(REST_TIMER_NOTIFICATION_ID)
        }
    }
}
