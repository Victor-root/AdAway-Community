package org.adaway.helper;

import static android.app.PendingIntent.FLAG_IMMUTABLE;
import static android.app.PendingIntent.getActivity;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static androidx.core.app.NotificationCompat.PRIORITY_LOW;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import org.adaway.R;
import org.adaway.ui.home.HomeActivity;
import org.adaway.ui.update.UpdateActivity;

/**
 * Helper class for application notifications.
 * <p>
 * Hosts and app-update notifications live on separate channels so users can mute the
 * "new app version available" channel from system settings without losing the rest of
 * the app's notifications (VPN status, hosts updates, etc.).
 */
public final class NotificationHelper {
    /**
     * Channel for hosts source update notifications.
     */
    public static final String UPDATE_HOSTS_NOTIFICATION_CHANNEL = "UpdateChannel";
    /**
     * Channel for app self-update notifications (a new version of the app is available).
     */
    public static final String UPDATE_APP_NOTIFICATION_CHANNEL = "AppUpdateChannel";
    /**
     * Channel for the foreground VPN service.
     */
    public static final String VPN_SERVICE_NOTIFICATION_CHANNEL = "VpnServiceChannel";

    private static final int UPDATE_HOSTS_NOTIFICATION_ID = 10;
    private static final int UPDATE_APP_NOTIFICATION_ID = 11;
    private static final int APP_INSTALLED_NOTIFICATION_ID = 12;
    public static final int VPN_RUNNING_SERVICE_NOTIFICATION_ID = 20;
    public static final int VPN_RESUME_SERVICE_NOTIFICATION_ID = 21;

    private NotificationHelper() {
    }

    public static void createNotificationChannels(@NonNull Context context) {
        NotificationChannel hostsUpdateChannel = new NotificationChannel(
                UPDATE_HOSTS_NOTIFICATION_CHANNEL,
                context.getString(R.string.notification_update_hosts_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        hostsUpdateChannel.setDescription(context.getString(R.string.notification_update_hosts_channel_description));

        NotificationChannel appUpdateChannel = new NotificationChannel(
                UPDATE_APP_NOTIFICATION_CHANNEL,
                context.getString(R.string.notification_update_app_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        appUpdateChannel.setDescription(context.getString(R.string.notification_update_app_channel_description));

        NotificationChannel vpnServiceChannel = new NotificationChannel(
                VPN_SERVICE_NOTIFICATION_CHANNEL,
                context.getString(R.string.notification_vpn_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        vpnServiceChannel.setDescription(context.getString(R.string.notification_vpn_channel_description));

        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(hostsUpdateChannel);
            notificationManager.createNotificationChannel(appUpdateChannel);
            notificationManager.createNotificationChannel(vpnServiceChannel);
        }
    }

    public static void showUpdateHostsNotification(@NonNull Context context) {
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager == null || !notificationManager.areNotificationsEnabled()) {
            return;
        }
        int color = context.getColor(R.color.notification);
        String title = context.getString(R.string.notification_update_host_available_title);
        String text = context.getString(R.string.notification_update_host_available_text);
        Intent intent = new Intent(context, HomeActivity.class);
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = getActivity(context, 0, intent, FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, UPDATE_HOSTS_NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.logo)
                .setColorized(true)
                .setColor(color)
                .setShowWhen(false)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setPriority(PRIORITY_LOW)
                .setAutoCancel(true);
        notificationManager.notify(UPDATE_HOSTS_NOTIFICATION_ID, builder.build());
    }

    public static void showUpdateApplicationNotification(@NonNull Context context) {
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager == null || !notificationManager.areNotificationsEnabled()) {
            return;
        }
        int color = context.getColor(R.color.notification);
        String title = context.getString(R.string.notification_update_app_available_title);
        String text = context.getString(R.string.notification_update_app_available_text);
        Intent intent = new Intent(context, UpdateActivity.class);
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = getActivity(context, 0, intent, FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, UPDATE_APP_NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.logo)
                .setColorized(true)
                .setColor(color)
                .setShowWhen(false)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setPriority(PRIORITY_LOW)
                .setAutoCancel(true);
        notificationManager.notify(UPDATE_APP_NOTIFICATION_ID, builder.build());
    }

    /**
     * Notification posted from {@link org.adaway.broadcast.UpdateReceiver} after the
     * system replaces the running APK. Workaround for the Android TV / Shield bug
     * where the system installer's "Open" button caches the pre-install APK path
     * and silently fails to launch the app once the path has changed. Tapping the
     * notification routes through the package's launch Intent, which Android
     * re-resolves at click time, so it correctly picks LEANBACK_LAUNCHER on TV
     * and LAUNCHER on mobile.
     */
    public static void showAppInstalledNotification(@NonNull Context context) {
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager == null || !notificationManager.areNotificationsEnabled()) {
            return;
        }
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (intent == null) {
            return;
        }
        intent.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = getActivity(context, 0, intent, FLAG_IMMUTABLE);
        int color = context.getColor(R.color.notification);
        String title = context.getString(R.string.notification_app_installed_title);
        String text = context.getString(R.string.notification_app_installed_text);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, UPDATE_APP_NOTIFICATION_CHANNEL)
                .setSmallIcon(R.drawable.logo)
                .setColorized(true)
                .setColor(color)
                .setShowWhen(false)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setPriority(PRIORITY_LOW)
                .setAutoCancel(true);
        notificationManager.notify(APP_INSTALLED_NOTIFICATION_ID, builder.build());
    }

    public static void clearUpdateNotifications(@NonNull Context context) {
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            return;
        }
        notificationManager.cancel(UPDATE_HOSTS_NOTIFICATION_ID);
        notificationManager.cancel(UPDATE_APP_NOTIFICATION_ID);
        notificationManager.cancel(APP_INSTALLED_NOTIFICATION_ID);
    }
}
