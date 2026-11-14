package dev.balaji.moonrose.trainfree.notifications

import android.Manifest
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
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
import dev.balaji.moonrose.trainfree.db.WorkoutSessionRepository
import dev.balaji.moonrose.trainfree.model.RestTimerSound
import dev.balaji.moonrose.trainfree.sharedUI.R

private const val REST_TIMER_CHANNEL_ID = "rest_timer"
private const val REST_TIMER_PROGRESS_CHANNEL_ID = "rest_timer_progress"
private const val REST_TIMER_NOTIFICATION_ID = 2001
private const val REST_TIMER_PROGRESS_NOTIFICATION_ID = 2002
private const val REST_TIMER_ALARM_REQUEST_CODE = 2003

private const val ACTION_REST_TIMER_EXPIRED = "dev.balaji.moonrose.trainfree.action.REST_TIMER_EXPIRED"
private const val EXTRA_ENDS_AT_MILLIS = "ends_at_millis"
private const val EXTRA_NOTIFICATIONS_ENABLED = "notifications_enabled"
private const val EXTRA_SOUND = "sound"
private const val EXTRA_TITLE = "title"
private const val EXTRA_MESSAGE = "message"
private const val EXTRA_CHANNEL_NAME = "channel_name"

@Composable
actual fun rememberRestTimerNotifier(): RestTimerNotifier {
    val context = LocalContext.current.applicationContext
    return remember(context) { AndroidRestTimerNotifier(context) }
}

/**
 * Fires when the OS-scheduled [AlarmManager] exact alarm reaches zero so the
 * rest timer alert and notification still trigger reliably even when the app
 * is backgrounded or the device is in Doze mode.
 */
class RestTimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REST_TIMER_EXPIRED) return
        val endsAtMillis = intent.getLongExtra(EXTRA_ENDS_AT_MILLIS, 0L)
        val notificationsEnabled = intent.getBooleanExtra(EXTRA_NOTIFICATIONS_ENABLED, false)
        val sound = RestTimerSound.fromValue(intent.getStringExtra(EXTRA_SOUND))
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val message = intent.getStringExtra(EXTRA_MESSAGE) ?: return
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: title
        WorkoutSessionRepository.clearActiveRestTimer()
        AndroidRestTimerCoordinator.onTimerFinished(
            context = context.applicationContext,
            endsAtMillis = endsAtMillis,
            notificationsEnabled = notificationsEnabled,
            sound = sound,
            title = title,
            message = message,
            channelName = channelName,
        )
    }
}

private class AndroidRestTimerNotifier(private val context: Context) : RestTimerNotifier {
    override fun onRestTimerStarted(
        endsAtMillis: Long,
        notificationsEnabled: Boolean,
        sound: RestTimerSound,
        title: String,
        message: String,
        channelName: String,
    ) {
        AndroidRestTimerCoordinator.onTimerStarted(
            context = context,
            endsAtMillis = endsAtMillis,
            notificationsEnabled = notificationsEnabled,
            sound = sound,
            title = title,
            message = message,
            channelName = channelName,
        )
    }

    override fun onRestTimerCancelled() {
        AndroidRestTimerCoordinator.onTimerCancelled(context)
    }

    override fun onRestTimerFinished(
        notificationsEnabled: Boolean,
        sound: RestTimerSound,
        title: String,
        message: String,
        channelName: String,
    ) {
        AndroidRestTimerCoordinator.onTimerFinished(
            context = context,
            endsAtMillis = 0L,
            notificationsEnabled = notificationsEnabled,
            sound = sound,
            title = title,
            message = message,
            channelName = channelName,
        )
    }

    override fun previewSound(sound: RestTimerSound) {
        AndroidRestTimerCoordinator.playBellSound(context, sound)
    }
}

private object AndroidRestTimerCoordinator {
    private var activePlayer: MediaPlayer? = null
    private var activeEndsAtMillis: Long = 0L
    private var lastCompletedEndsAtMillis: Long = 0L

    @Synchronized
    fun onTimerStarted(
        context: Context,
        endsAtMillis: Long,
        notificationsEnabled: Boolean,
        sound: RestTimerSound,
        title: String,
        message: String,
        channelName: String,
    ) {
        val nowMillis = System.currentTimeMillis()
        if (endsAtMillis <= nowMillis) return
        activeEndsAtMillis = endsAtMillis
        cancelFinishedNotification(context)

        if (notificationsEnabled && canPostNotifications(context)) {
            postOngoingCountdownNotification(
                context = context,
                endsAtMillis = endsAtMillis,
                channelName = channelName,
            )
        } else {
            cancelProgressNotification(context)
        }

        scheduleExactAlarm(
            context = context,
            endsAtMillis = endsAtMillis,
            notificationsEnabled = notificationsEnabled,
            sound = sound,
            title = title,
            message = message,
            channelName = channelName,
        )
    }

    @Synchronized
    fun onTimerCancelled(context: Context) {
        activeEndsAtMillis = 0L
        cancelScheduledAlarm(context)
        cancelProgressNotification(context)
        cancelFinishedNotification(context)
    }

    @Synchronized
    fun onTimerFinished(
        context: Context,
        endsAtMillis: Long,
        notificationsEnabled: Boolean,
        sound: RestTimerSound,
        title: String,
        message: String,
        channelName: String,
    ) {
        val targetEndsAtMillis = if (endsAtMillis != 0L) endsAtMillis else activeEndsAtMillis
        if (targetEndsAtMillis != 0L && targetEndsAtMillis == lastCompletedEndsAtMillis) {
            return
        }
        lastCompletedEndsAtMillis = targetEndsAtMillis
        activeEndsAtMillis = 0L

        cancelScheduledAlarm(context)
        cancelProgressNotification(context)

        val foreground = isAppInForeground()
        if (!foreground && notificationsEnabled && canPostNotifications(context)) {
            postFinishedNotification(
                context = context,
                sound = sound,
                title = title,
                message = message,
                channelName = channelName,
            )
            // Ensure the vibration still fires even if the notification
            // channel is muted by the user or OS.
            vibrate(context)
            return
        }
        playBellSound(context, sound)
        vibrate(context)
    }

    @Synchronized
    private fun stopActivePlayer() {
        val existing = activePlayer ?: return
        activePlayer = null
        runCatching {
            if (existing.isPlaying) existing.stop()
        }
        runCatching { existing.release() }
    }

    @Synchronized
    fun playBellSound(context: Context, sound: RestTimerSound) {
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
                val uri = soundUriFor(context, sound) ?: run {
                    player.release()
                    return
                }
                player.setDataSource(context, uri)
            }
            player.setOnCompletionListener { mp ->
                synchronized(this) {
                    if (activePlayer === mp) activePlayer = null
                }
                runCatching { mp.release() }
            }
            player.setOnErrorListener { mp, _, _ ->
                synchronized(this) {
                    if (activePlayer === mp) activePlayer = null
                }
                runCatching { mp.release() }
                true
            }
            player.prepare()
            activePlayer = player
            player.start()
        }
    }

    private fun scheduleExactAlarm(
        context: Context,
        endsAtMillis: Long,
        notificationsEnabled: Boolean,
        sound: RestTimerSound,
        title: String,
        message: String,
        channelName: String,
    ) {
        runCatching {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, RestTimerAlarmReceiver::class.java).apply {
                action = ACTION_REST_TIMER_EXPIRED
                putExtra(EXTRA_ENDS_AT_MILLIS, endsAtMillis)
                putExtra(EXTRA_NOTIFICATIONS_ENABLED, notificationsEnabled)
                putExtra(EXTRA_SOUND, sound.value)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_MESSAGE, message)
                putExtra(EXTRA_CHANNEL_NAME, channelName)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REST_TIMER_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMillis, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, endsAtMillis, pendingIntent)
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, endsAtMillis, pendingIntent)
            }
        }
    }

    private fun cancelScheduledAlarm(context: Context) {
        runCatching {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, RestTimerAlarmReceiver::class.java).apply {
                action = ACTION_REST_TIMER_EXPIRED
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REST_TIMER_ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: return
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
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

    private fun soundUriFor(context: Context, sound: RestTimerSound): Uri? = when (sound) {
        RestTimerSound.NONE -> null
        RestTimerSound.DEFAULT -> RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        RestTimerSound.PROXIMA -> rawResourceUri(context, R.raw.rest_proxima)
        RestTimerSound.MIRA -> rawResourceUri(context, R.raw.rest_mira)
        RestTimerSound.ANTARES -> rawResourceUri(context, R.raw.rest_antares)
        RestTimerSound.ALTAIR -> rawResourceUri(context, R.raw.rest_altair)
        RestTimerSound.DENEB -> rawResourceUri(context, R.raw.rest_deneb)
    }

    private fun rawResourceUri(context: Context, resId: Int): Uri =
        Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/$resId")

    private fun channelIdFor(sound: RestTimerSound): String = "${REST_TIMER_CHANNEL_ID}_${sound.value}"

    @Suppress("DEPRECATION")
    private fun vibrate(context: Context) {
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

    private fun canPostNotifications(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    private fun launchPendingIntent(context: Context): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    @Suppress("DEPRECATION")
    private fun postOngoingCountdownNotification(
        context: Context,
        endsAtMillis: Long,
        channelName: String,
    ) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    REST_TIMER_PROGRESS_CHANNEL_ID,
                    channelName,
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(channel)
            }

            val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                android.app.Notification.Builder(context, REST_TIMER_PROGRESS_CHANNEL_ID)
                    .setTimeoutAfter((endsAtMillis - System.currentTimeMillis()).coerceAtLeast(0L))
            } else {
                android.app.Notification.Builder(context)
                    .setPriority(android.app.Notification.PRIORITY_LOW)
            }

            val iconRes = context.applicationInfo.icon.takeIf { it != 0 }
                ?: android.R.drawable.ic_lock_idle_alarm
            builder
                .setSmallIcon(iconRes)
                .setContentTitle(channelName)
                .setWhen(endsAtMillis)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(android.app.Notification.CATEGORY_PROGRESS)
                .setVisibility(android.app.Notification.VISIBILITY_PUBLIC)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setChronometerCountDown(true)
            }
            launchPendingIntent(context)?.let(builder::setContentIntent)
            nm.notify(REST_TIMER_PROGRESS_NOTIFICATION_ID, builder.build())
        }
    }

    @Suppress("DEPRECATION")
    private fun postFinishedNotification(
        context: Context,
        sound: RestTimerSound,
        title: String,
        message: String,
        channelName: String,
    ) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val soundUri = soundUriFor(context, sound)
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
                    setBypassDnd(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    if (sound == RestTimerSound.NONE) {
                        setSound(null, null)
                    } else if (soundUri != null) {
                        setSound(soundUri, audioAttributes)
                    }
                }
                nm.createNotificationChannel(channel)
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
                .setVisibility(android.app.Notification.VISIBILITY_PUBLIC)
            launchPendingIntent(context)?.let(builder::setContentIntent)
            nm.notify(REST_TIMER_NOTIFICATION_ID, builder.build())
        }
    }

    private fun cancelProgressNotification(context: Context) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(REST_TIMER_PROGRESS_NOTIFICATION_ID)
        }
    }

    private fun cancelFinishedNotification(context: Context) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(REST_TIMER_NOTIFICATION_ID)
        }
    }
}
