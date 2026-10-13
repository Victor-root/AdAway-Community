package org.adaway.util;

import static android.widget.Toast.LENGTH_SHORT;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import org.adaway.R;

import timber.log.Timber;

/**
 * This class starts activities of other applications, like a browser or a settings screen, that
 * the device may not have (Android TV devices often have no browser).
 */
public final class ActivityStarter {
    private ActivityStarter() {

    }

    /**
     * Start an activity, telling the user if no application can handle it.
     *
     * @param context The context to start the activity from.
     * @param intent  The intent of the activity to start.
     * @return {@code true} if the activity was started, {@code false} otherwise.
     */
    public static boolean startActivity(Context context, Intent intent) {
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            Timber.w(e, "No activity found for %s.", intent);
            Toast.makeText(context, R.string.no_application_found, LENGTH_SHORT).show();
            return false;
        }
    }
}
