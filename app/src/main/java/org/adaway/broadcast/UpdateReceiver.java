package org.adaway.broadcast;

import static android.content.Intent.ACTION_MY_PACKAGE_REPLACED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.helper.NotificationHelper;

import timber.log.Timber;

/**
 * This broadcast receiver is executed at application update.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class UpdateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            AdAwayApplication application = (AdAwayApplication) context.getApplicationContext();
            String versionName = application.getUpdateModel().getVersionName();
            Timber.d("UpdateReceiver invoked");
            Timber.i("Application update to version %s", versionName);
            // Toast shows immediately on top of whatever activity is foreground
            // (typically the system installer's post-install screen). Doesn't
            // depend on POST_NOTIFICATIONS, which is the most reliable signal
            // we can deliver on Android TV — its notification shade is hidden
            // behind a remote-only shortcut and easily missed.
            String message = context.getString(R.string.notification_app_installed_title)
                    + " — "
                    + context.getString(R.string.notification_app_installed_text);
            Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            // Also post a notification so the user still has a tappable shortcut
            // back to the app once the toast disappears. On Android 13+ this
            // requires POST_NOTIFICATIONS to have been granted, otherwise it's a
            // no-op — the toast above is the safety net.
            NotificationHelper.showAppInstalledNotification(context);
        }
    }
}
