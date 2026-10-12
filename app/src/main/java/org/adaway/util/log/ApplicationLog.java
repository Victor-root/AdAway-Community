package org.adaway.util.log;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;

import com.topjohnwu.superuser.Shell;

import org.adaway.helper.PreferenceHelper;

import timber.log.Timber;

/**
 * This class is an utility class that configures the application log.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public final class ApplicationLog {
    /**
     * Private constructor.
     */
    private ApplicationLog() {

    }

    /**
     * Initialize application logging.
     *
     * @param application The application instance.
     */
    public static void init(Application application) {
        // Always capture INFO+ to a small, exportable on-disk log so users can share a
        // diagnostic trail with us even when the device is not connected to a computer.
        Timber.plant(new DiagnosticLogTree(DiagnosticLog.getInstance(application)));
        if (isApplicationDebuggable(application) || PreferenceHelper.getDebugEnabled(application)) {
            Shell.enableVerboseLogging = true;
            Timber.plant(new Timber.DebugTree());
        } else {
            Shell.enableVerboseLogging = false;
        }
    }

    /**
     * Check whether the application is a debuggable (debug) build.
     *
     * @param context The application context.
     * @return {@code true} for a debug build, {@code false} for a release build.
     */
    public static boolean isApplicationDebuggable(Context context) {
        return (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }
}
