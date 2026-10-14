package org.adaway.system

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.adaway.MainActivity
import org.adaway.R

/** Notification channels keep the identifiers of the previous versions, and so the user choices. */
object Notifications {
    private const val SOURCE_UPDATES_CHANNEL = "UpdateChannel"
    private const val SOURCE_UPDATE_ID = 10

    fun createChannels(context: Context) {
        val channel = NotificationChannel(
            SOURCE_UPDATES_CHANNEL,
            context.getString(R.string.notification_channel_source_updates),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = context.getString(R.string.notification_channel_source_updates_description) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun showSourceUpdateAvailable(context: Context) {
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, SOURCE_UPDATES_CHANNEL)
            .setSmallIcon(R.drawable.logo)
            .setColor(context.getColor(R.color.brand_red))
            .setContentTitle(context.getString(R.string.notification_source_update_title))
            .setContentText(context.getString(R.string.notification_source_update_text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        try {
            notifications.notify(SOURCE_UPDATE_ID, notification)
        } catch (_: SecurityException) {
            // The notification permission was revoked in the meantime.
        }
    }

    fun cancelSourceUpdateAvailable(context: Context) {
        NotificationManagerCompat.from(context).cancel(SOURCE_UPDATE_ID)
    }
}
