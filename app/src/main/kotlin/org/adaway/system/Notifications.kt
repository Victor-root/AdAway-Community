package org.adaway.system

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.adaway.MainActivity
import org.adaway.R
import org.adaway.broadcast.VpnActionReceiver
import org.adaway.ui.Destination
import org.adaway.vpn.VpnStatus

/** Notification channels keep the identifiers of the previous versions, and so the user choices. */
object Notifications {
    private const val SOURCE_UPDATES_CHANNEL = "UpdateChannel"
    private const val APP_UPDATES_CHANNEL = "AppUpdateChannel"
    private const val VPN_CHANNEL = "VpnServiceChannel"
    private const val SOURCE_UPDATE_ID = 10
    private const val APP_UPDATE_ID = 11
    const val VPN_STATUS_ID = 20
    /** Something the user must do for the VPN to run again. */
    private const val VPN_ATTENTION_ID = 22

    fun createChannels(context: Context) {
        val channels = listOf(
            NotificationChannel(
                SOURCE_UPDATES_CHANNEL,
                context.getString(R.string.notification_channel_source_updates),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.notification_channel_source_updates_description) },
            NotificationChannel(
                APP_UPDATES_CHANNEL,
                context.getString(R.string.notification_channel_app_updates),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.notification_channel_app_updates_description) },
            NotificationChannel(
                VPN_CHANNEL,
                context.getString(R.string.notification_channel_vpn),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.notification_channel_vpn_description) },
        )
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannels(channels)
    }

    /** Opens the home screen, in a new task. */
    fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE,
    )

    fun showSourceUpdateAvailable(context: Context) {
        val notification = builder(context, SOURCE_UPDATES_CHANNEL)
            .setContentTitle(context.getString(R.string.notification_source_update_title))
            .setContentText(context.getString(R.string.notification_source_update_text))
            .setAutoCancel(true)
            .build()
        notify(context, SOURCE_UPDATE_ID, notification)
    }

    fun cancelSourceUpdateAvailable(context: Context) {
        NotificationManagerCompat.from(context).cancel(SOURCE_UPDATE_ID)
    }

    /** Opens the update screen. */
    fun showAppUpdateAvailable(context: Context) {
        val openUpdate = PendingIntent.getActivity(
            context,
            APP_UPDATE_ID,
            Intent(context, MainActivity::class.java).setAction(Destination.UPDATE.action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = builder(context, APP_UPDATES_CHANNEL)
            .setContentTitle(context.getString(R.string.notification_app_update_title))
            .setContentText(context.getString(R.string.notification_app_update_text))
            .setContentIntent(openUpdate)
            .setAutoCancel(true)
            .build()
        notify(context, APP_UPDATE_ID, notification)
    }

    /** The single VPN notification: the foreground one while the service runs, then the stopped one. */
    fun vpnStatus(context: Context, status: VpnStatus): Notification {
        val alive = status.isServiceAlive()
        val action = if (alive) VpnActionReceiver.DISABLE else VpnActionReceiver.ENABLE
        return builder(context, VPN_CHANNEL)
            .setContentTitle(context.getString(R.string.notification_vpn_title, context.getString(statusLabel(status))))
            .setOngoing(alive)
            .setOnlyAlertOnce(true)
            .addAction(
                0,
                context.getString(if (alive) R.string.notification_vpn_disable else R.string.notification_vpn_enable),
                VpnActionReceiver.pendingIntent(context, action),
            )
            .build()
    }

    fun showVpnStatus(context: Context, status: VpnStatus) {
        notify(context, VPN_STATUS_ID, vpnStatus(context, status))
    }

    fun cancelVpnStatus(context: Context) {
        NotificationManagerCompat.from(context).cancel(VPN_STATUS_ID)
    }

    /** An app in the background cannot ask for the VPN consent itself: the user opens the app for it. */
    fun showVpnConsentRequired(context: Context) {
        showVpnAttention(context, R.string.notification_vpn_consent_title, R.string.notification_vpn_consent_text)
    }

    fun showVpnStartRefused(context: Context) {
        showVpnAttention(context, R.string.notification_vpn_refused_title, R.string.notification_vpn_refused_text)
    }

    fun cancelVpnAttention(context: Context) {
        NotificationManagerCompat.from(context).cancel(VPN_ATTENTION_ID)
    }

    private fun showVpnAttention(context: Context, @StringRes title: Int, @StringRes text: Int) {
        val notification = builder(context, VPN_CHANNEL)
            .setContentTitle(context.getString(title))
            .setContentText(context.getString(text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(text)))
            .setAutoCancel(true)
            .build()
        notify(context, VPN_ATTENTION_ID, notification)
    }

    private fun builder(context: Context, channel: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.logo)
        .setColor(context.getColor(R.color.brand_red))
        .setContentIntent(openAppIntent(context))

    private fun notify(context: Context, id: Int, notification: Notification) {
        val notifications = NotificationManagerCompat.from(context)
        if (!notifications.areNotificationsEnabled()) return
        try {
            notifications.notify(id, notification)
        } catch (_: SecurityException) {
            // The notification permission was revoked in the meantime.
        }
    }

    @StringRes
    private fun statusLabel(status: VpnStatus): Int = when (status) {
        VpnStatus.STARTING -> R.string.vpn_status_starting
        VpnStatus.RUNNING -> R.string.vpn_status_running
        VpnStatus.WAITING_FOR_NETWORK -> R.string.vpn_status_waiting_for_network
        VpnStatus.RECONNECTING -> R.string.vpn_status_reconnecting
        VpnStatus.STOPPED, VpnStatus.CONSENT_REQUIRED -> R.string.vpn_status_stopped
    }
}
