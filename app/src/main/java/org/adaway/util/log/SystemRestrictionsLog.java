package org.adaway.util.log;

import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_ACTIVE;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_FREQUENT;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_RARE;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_RESTRICTED;
import static android.app.usage.UsageStatsManager.STANDBY_BUCKET_WORKING_SET;
import static android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED;
import static android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED;
import static android.net.ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.P;

import android.app.ActivityManager;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.PowerManager;

import timber.log.Timber;

/**
 * Logs the system-level restrictions that can cut this app's own network access while the network
 * itself looks perfectly fine: Doze, battery saver, battery optimization, Data Saver, the user's
 * background restriction and the app standby bucket. None of them shows in the network state, yet
 * any of them makes the system firewall refuse the app's packets ({@code EPERM}), which for the
 * VPN means every forwarded DNS query of every app.
 * <p>
 * Logged at {@code INFO} so it reaches the {@link DiagnosticLog}.
 *
 * @author AdAway Community
 */
public final class SystemRestrictionsLog {
    /**
     * Private constructor.
     */
    private SystemRestrictionsLog() {

    }

    /**
     * Log the current system restrictions.
     *
     * @param context The application context.
     * @param reason  What prompted the log, to tell the lines apart.
     */
    public static void log(Context context, String reason) {
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        ConnectivityManager connectivityManager = context.getSystemService(ConnectivityManager.class);
        Timber.i("%s System restrictions: deviceIdle=%s, powerSave=%s, batteryOptimizationIgnored=%s, dataSaver=%s, backgroundRestricted=%s, standbyBucket=%s.",
                reason,
                powerManager == null ? "unknown" : powerManager.isDeviceIdleMode(),
                powerManager == null ? "unknown" : powerManager.isPowerSaveMode(),
                powerManager == null ? "unknown" : powerManager.isIgnoringBatteryOptimizations(context.getPackageName()),
                connectivityManager == null ? "unknown" : describeDataSaver(connectivityManager.getRestrictBackgroundStatus()),
                describeBackgroundRestriction(context),
                describeStandbyBucket(context));
    }

    private static String describeDataSaver(int status) {
        switch (status) {
            case RESTRICT_BACKGROUND_STATUS_DISABLED:
                return "off";
            case RESTRICT_BACKGROUND_STATUS_WHITELISTED:
                return "on (app exempted)";
            case RESTRICT_BACKGROUND_STATUS_ENABLED:
                return "on";
            default:
                return String.valueOf(status);
        }
    }

    private static String describeBackgroundRestriction(Context context) {
        if (SDK_INT < P) {
            return "n/a";
        }
        ActivityManager activityManager = context.getSystemService(ActivityManager.class);
        return activityManager == null ? "unknown" : String.valueOf(activityManager.isBackgroundRestricted());
    }

    private static String describeStandbyBucket(Context context) {
        if (SDK_INT < P) {
            return "n/a";
        }
        UsageStatsManager usageStatsManager = context.getSystemService(UsageStatsManager.class);
        if (usageStatsManager == null) {
            return "unknown";
        }
        int bucket = usageStatsManager.getAppStandbyBucket();
        switch (bucket) {
            case STANDBY_BUCKET_ACTIVE:
                return "active";
            case STANDBY_BUCKET_WORKING_SET:
                return "working set";
            case STANDBY_BUCKET_FREQUENT:
                return "frequent";
            case STANDBY_BUCKET_RARE:
                return "rare";
            case STANDBY_BUCKET_RESTRICTED:
                return "restricted";
            default:
                return String.valueOf(bucket);
        }
    }
}
