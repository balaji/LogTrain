package dev.balaji.moonrose.logtrain.notifications

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.balaji.moonrose.logtrain.model.RestTimerSound
import dev.balaji.moonrose.logtrain.sharedUI.R

private const val REST_TIMER_CHANNEL_ID = "rest_timer"
private const val REST_TIMER_NOTIFICATION_ID = 2001

@Composable
actual fun rememberRestTimerNotifier(): RestTimerNotifier {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidRestTimerNotifier(context) }
}

private class AndroidRestTimerNotifier(private val context: Context) : RestTimerNotifier {
    private var activePlayer: MediaPlayer? = null

    override fun onRestTimerStarted(
        endsAtMillis: Long,
        notificationsEnabled: Boolean,
        sound: RestTimerSound,
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
        sound: RestTimerSound,
        title: String,
        message: String,
        channelName: String,
    ) {
        val foreground = isAppInForeground()
        if (!foreground && notificationsEnabled && canPostNotifications()) {
            postNotification(sound = sound, title = title, message = message, channelName = channelName)
            // Ensure the vibration still fires even if the notification
            // channel is muted by the user or OS.
            vibrate()
            return
        }
        playBellSound(sound)
        vibrate()
    }

    override fun previewSound(sound: RestTimerSound) {
        playBellSound(sound)
    }

    private fun stopActivePlayer() {
        val existing = activePlayer ?: return
        activePlayer = null
        runCatching {
            if (existing.isPlaying) existing.stop()
        }
        runCatching { existing.release() }
    }

    private fun playBellSound(sound: RestTimerSound) {
        stopActivePlayer()
        if (sound == RestTimerSound.NONE) return
        runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            // Ringtone with USAGE_NOTIFICATION_EVENT is silently dropped by
            // Android whenever STREAM_NOTIFICATION volume is 0 (e.g. vibrate
            // mode or muted notification slider). Route in-app previews and
            // foreground timer chimes through media volume (falling back to
            // alarm volume if media is muted) since the user has an explicit
            // "None" option when they want vibration only.
            val usage = if ((am?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 1) > 0) {
                AudioAttributes.USAGE_MEDIA
            } else {
                AudioAttributes.USAGE_ALARM
            }
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(usage)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            val rawRes = rawResIdFor(sound)
            if (rawRes != null) {
                val afd = context.resources.openRawResourceFd(rawRes) ?: run {
                    player.release()
                    return
                }
                afd.use {
                    player.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
            } else {
                val uri = soundUriFor(sound) ?: run {
                    player.release()
                    return
                }
                player.setDataSource(context, uri)
            }
            player.setOnCompletionListener { mp ->
                if (activePlayer === mp) activePlayer = null
                runCatching { mp.release() }
            }
            player.setOnErrorListener { mp, _, _ ->
                if (activePlayer === mp) activePlayer = null
                runCatching { mp.release() }
                true
            }
            player.prepare()
            activePlayer = player
            player.start()
        }
    }

    private fun rawResIdFor(sound: RestTimerSound): Int? = when (sound) {
        RestTimerSound.NONE, RestTimerSound.DEFAULT -> null
        RestTimerSound.PROXIMA -> R.raw.rest_proxima
        RestTimerSound.MIRA -> R.raw.rest_mira
        RestTimerSound.ANTARES -> R.raw.rest_antares
        RestTimerSound.ALTAIR -> R.raw.rest_altair
        RestTimerSound.DENEB -> R.raw.rest_deneb
    }

    private fun soundUriFor(sound: RestTimerSound): Uri? = when (sound) {
        RestTimerSound.NONE -> null
        RestTimerSound.DEFAULT -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        RestTimerSound.PROXIMA -> rawResourceUri(R.raw.rest_proxima)
        RestTimerSound.MIRA -> rawResourceUri(R.raw.rest_mira)
        RestTimerSound.ANTARES -> rawResourceUri(R.raw.rest_antares)
        RestTimerSound.ALTAIR -> rawResourceUri(R.raw.rest_altair)
        RestTimerSound.DENEB -> rawResourceUri(R.raw.rest_deneb)
    }

    private fun rawResourceUri(resId: Int): Uri =
        Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/$resId")

    private fun channelIdFor(sound: RestTimerSound): String = "${REST_TIMER_CHANNEL_ID}_${sound.value}"

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
    private fun postNotification(sound: RestTimerSound, title: String, message: String, channelName: String) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val soundUri = soundUriFor(sound)
            val channelId = channelIdFor(sound)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android locks a NotificationChannel's sound once created, so each
                // sound uses its own channel ID and stale channels are cleaned up.
                nm.deleteNotificationChannel(REST_TIMER_CHANNEL_ID)
                RestTimerSound.entries.forEach { other ->
                    if (other != sound) {
                        nm.deleteNotificationChannel(channelIdFor(other))
                    }
                }
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                val channel = NotificationChannel(
                    channelId,
                    channelName,
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0L, 250L, 120L, 250L)
                    if (sound == RestTimerSound.NONE) {
                        setSound(null, null)
                    } else if (soundUri != null) {
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
                android.app.Notification.Builder(context, channelId)
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
