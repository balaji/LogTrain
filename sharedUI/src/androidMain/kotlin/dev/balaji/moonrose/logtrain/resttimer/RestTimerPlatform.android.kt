package dev.balaji.moonrose.logtrain.resttimer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.balaji.moonrose.logtrain.sharedui.resources.Res
import dev.balaji.moonrose.logtrain.sharedui.resources.rest_timer_channel_name
import dev.balaji.moonrose.logtrain.sharedui.resources.rest_timer_notification_body
import dev.balaji.moonrose.logtrain.sharedui.resources.rest_timer_notification_title
import org.jetbrains.compose.resources.stringResource

internal const val REST_TIMER_CHANNEL_ID = "rest_timer"
internal const val REST_TIMER_NOTIFICATION_ID = 1001
internal const val EXTRA_REST_TIMER_TITLE = "rest_timer_title"
internal const val EXTRA_REST_TIMER_BODY = "rest_timer_body"
private const val REST_TIMER_REQUEST_CODE = 1001
private const val VIBRATION_MS = 400L

@Composable
actual fun rememberRestTimerAlerts(): RestTimerAlerts {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Resolved at composition so the receiver gets localized text via extras;
    // the shared strings.xml stays the single source of notification copy.
    val title = stringResource(Res.string.rest_timer_notification_title)
    val body = stringResource(Res.string.rest_timer_notification_body)
    val channelName = stringResource(Res.string.rest_timer_channel_name)
    return remember(context, lifecycle, title, body, channelName) {
        AndroidRestTimerAlerts(context, lifecycle, title, body, channelName)
    }
}

private class AndroidRestTimerAlerts(
    private val context: Context,
    private val lifecycle: Lifecycle,
    private val notificationTitle: String,
    private val notificationBody: String,
    private val channelName: String,
) : RestTimerAlerts {
    override fun timerFinished(): Boolean {
        // Foreground only: backgrounded, the scheduled alarm notification
        // carries the sound and vibration instead, so ringing here would
        // double up. Returning false keeps that notification scheduled.
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return false
        runCatching {
            RingtoneManager
                .getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                ?.play()
        }
        runCatching { vibrate() }
        return true
    }

    override fun scheduleExpiryNotification(endsAtMillis: Long) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = alarmPendingIntent()
        // Exact when the user allows it; the inexact fallback fires close
        // enough on a device that was interactive seconds ago.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMillis, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endsAtMillis, pendingIntent)
        }
    }

    override fun cancelExpiryNotification() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(alarmPendingIntent())
        NotificationManagerCompat.from(context).cancel(REST_TIMER_NOTIFICATION_ID)
    }

    /** Fixed request code, so rescheduling always replaces the pending alarm. */
    private fun alarmPendingIntent(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REST_TIMER_REQUEST_CODE,
            Intent(context, RestTimerAlarmReceiver::class.java)
                .putExtra(EXTRA_REST_TIMER_TITLE, notificationTitle)
                .putExtra(EXTRA_REST_TIMER_BODY, notificationBody),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun ensureChannel() {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat
                .Builder(REST_TIMER_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName(channelName)
                .setVibrationEnabled(true)
                .build(),
        )
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(VIBRATION_MS)
        }
    }
}
