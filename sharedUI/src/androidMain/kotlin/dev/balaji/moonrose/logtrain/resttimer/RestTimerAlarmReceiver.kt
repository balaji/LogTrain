package dev.balaji.moonrose.logtrain.resttimer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Fires at rest-timer expiry (scheduled via AlarmManager) so the bell and
 * vibration reach the user even with the app backgrounded or the screen off.
 * A countdown that finishes in the foreground cancels this alarm and rings
 * in-app instead. Notification text arrives as extras, localized at the
 * scheduling site. Registered in androidApp's manifest.
 */
class RestTimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return

        val contentIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.let {
                PendingIntent.getActivity(
                    context,
                    0,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
        val notification = NotificationCompat.Builder(context, REST_TIMER_CHANNEL_ID)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle(intent.getStringExtra(EXTRA_REST_TIMER_TITLE).orEmpty())
            .setContentText(intent.getStringExtra(EXTRA_REST_TIMER_BODY).orEmpty())
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        // areNotificationsEnabled() was checked above; SecurityException here
        // means the permission was revoked in the same instant — drop it.
        runCatching { notifications.notify(REST_TIMER_NOTIFICATION_ID, notification) }
    }
}
