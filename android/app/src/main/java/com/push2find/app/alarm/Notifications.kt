package com.push2find.app.alarm

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.push2find.app.R
import com.push2find.app.data.Permissions
import com.push2find.app.ui.MainActivity

object Notifications {
    const val ID_STATUS = 1
    private const val ID_ALARM = 2
    private const val CH_STATUS = "status"
    private const val CH_ALARM = "alarm"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_STATUS, context.getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ALARM, context.getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH).apply {
                // The sound is played by Alarm on the ALARM stream; the notification itself stays quiet.
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
    }

    /** Persistent notification required for the foreground service. */
    fun status(context: Context, text: String): Notification =
        NotificationCompat.Builder(context, CH_STATUS)
            .setSmallIcon(R.drawable.ic_stat_push2find)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(context))
            .build()

    @SuppressLint("MissingPermission")
    fun updateStatus(context: Context, text: String) {
        if (!Permissions.hasNotifications(context)) return
        runCatching { NotificationManagerCompat.from(context).notify(ID_STATUS, status(context, text)) }
    }

    /** High-priority alarm notification; its full-screen intent shows AlarmActivity over the lock screen. */
    @SuppressLint("MissingPermission")
    fun showAlarm(context: Context) {
        if (!Permissions.hasNotifications(context)) return
        val fullScreen = PendingIntent.getActivity(
            context, 1,
            Intent(context, AlarmActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stop = PendingIntent.getBroadcast(
            context, 2, Intent(context, StopAlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, CH_ALARM)
            .setSmallIcon(R.drawable.ic_stat_push2find)
            .setContentTitle(context.getString(R.string.alarm_title))
            .setContentText(context.getString(R.string.alarm_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setDeleteIntent(stop)
            .addAction(0, context.getString(R.string.action_stop), stop)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(ID_ALARM, n) }
    }

    fun cancelAlarm(context: Context) = NotificationManagerCompat.from(context).cancel(ID_ALARM)

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
