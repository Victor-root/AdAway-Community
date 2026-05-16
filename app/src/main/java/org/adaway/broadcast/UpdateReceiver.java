package org.adaway.broadcast;

import static android.content.Intent.ACTION_MY_PACKAGE_REPLACED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.helper.NotificationHelper;
import org.adaway.util.Constants;

import timber.log.Timber;

/**
 * This broadcast receiver is executed at application update.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class UpdateReceiver extends BroadcastReceiver {
    /**
     * SharedPreferences key stamped right after the install. A launcher Activity
     * clears it via {@link #clearInstallToast(Context)} when it comes up, so the
     * delayed toast is skipped on devices where the system installer's "Open"
     * button actually launches the app (typically mobile) and only fires on
     * devices where it silently fails (typically Android TV).
     */
    private static final String PREF_INSTALL_TOAST_TIMESTAMP = "updateInstallToastTimestamp";
    /**
     * Delay before the post-install toast fires. Tuned to comfortably outlast a
     * working "Open" button click (mobile launches in well under a second) while
     * staying short enough to feel responsive on TV where the user is waiting.
     */
    private static final long INSTALL_TOAST_DELAY_MS = 5_000L;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            return;
        }
        AdAwayApplication application = (AdAwayApplication) context.getApplicationContext();
        String versionName = application.getUpdateModel().getVersionName();
        Timber.d("UpdateReceiver invoked");
        Timber.i("Application update to version %s", versionName);

        long stamp = System.currentTimeMillis();
        Context appContext = context.getApplicationContext();
        SharedPreferences prefs = appContext.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putLong(PREF_INSTALL_TOAST_TIMESTAMP, stamp).apply();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            long stored = prefs.getLong(PREF_INSTALL_TOAST_TIMESTAMP, 0L);
            if (stored != stamp) {
                // A launcher Activity cleared the stamp (the app was opened, so
                // the user already has a foreground view of the new version) or
                // a later install replaced it; nothing more to do.
                Timber.d("Skipping post-install toast; app was opened.");
                return;
            }
            prefs.edit().remove(PREF_INSTALL_TOAST_TIMESTAMP).apply();
            String message = appContext.getString(R.string.notification_app_installed_title)
                    + " — "
                    + appContext.getString(R.string.notification_app_installed_text);
            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show();
            NotificationHelper.showAppInstalledNotification(appContext);
        }, INSTALL_TOAST_DELAY_MS);
    }

    /**
     * Suppress the upcoming post-install toast/notification. Launcher Activities
     * call this from {@code onCreate} so users who land on the home screen via
     * the system installer's "Open" button don't get a redundant toast on top.
     */
    public static void clearInstallToast(Context context) {
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(PREF_INSTALL_TOAST_TIMESTAMP)
                .apply();
    }
}
