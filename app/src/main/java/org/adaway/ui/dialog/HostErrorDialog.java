package org.adaway.ui.dialog;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.adaway.R;
import org.adaway.model.error.HostError;
import org.adaway.ui.help.HelpActivity;

/**
 * This class is an utility class to show the sync and ad blocking errors, the same way on mobile
 * and on Android TV.
 */
public final class HostErrorDialog {
    private HostErrorDialog() {

    }

    /**
     * Show the dialog for an error: a plain Close button for most errors, replaced with the
     * welcome screen's own "Retry sync" action for the two that a sync retry can actually fix.
     *
     * @param activity  The activity to show the dialog on.
     * @param error     The error to show.
     * @param retrySync The action to retry the hosts sources sync.
     */
    public static void show(@NonNull Activity activity, @NonNull HostError error, @NonNull Runnable retrySync) {
        String message = activity.getString(error.getDetailsKey()) + "\n\n" + activity.getString(R.string.error_dialog_help);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(error.getMessageKey())
                .setMessage(message)
                .setNegativeButton(R.string.button_help, (dialog, id) -> {
                    dialog.dismiss();
                    activity.startActivity(new Intent(activity, HelpActivity.class));
                });
        if (error == HostError.DOWNLOAD_FAILED || error == HostError.NO_CONNECTION) {
            // Both mean the last hosts sources fetch could not complete. Retrying is the
            // one thing to actually do about it, so offer it right where the error shows
            // up, the same "Retry sync" action the welcome screen already offers for this
            // exact case, instead of sending the user hunting for the sync button elsewhere.
            builder.setPositiveButton(R.string.welcome_sync_retry_logo, (dialog, id) -> {
                dialog.dismiss();
                retrySync.run();
            });
        } else {
            builder.setPositiveButton(R.string.button_close, (dialog, id) -> dialog.dismiss());
        }
        builder.create().show();
    }
}
