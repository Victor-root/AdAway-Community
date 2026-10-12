package org.adaway.ui.prefs;

import static java.util.Objects.requireNonNull;
import static org.adaway.ui.prefs.PrefsActivity.PREFERENCE_NOT_FOUND;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.adaway.R;
import org.adaway.ui.prefs.exclusion.PrefsVpnExcludedAppsActivity;
import org.adaway.util.AppExecutors;
import org.adaway.util.log.DiagnosticLog;
import org.adaway.vpn.VpnServiceControls;

/**
 * Wires up the VPN ad blocker section of {@link PrefsMainFragment}.
 * <p>
 * These settings used to live on a sub-screen of their own, behind an entry the user had to
 * open first. They are now part of the main preferences screen, so this holds the behaviour
 * that screen's fragment used to carry, keeping the fragment itself readable.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
final class VpnPrefsBinder {
    private final PreferenceFragmentCompat fragment;
    private ActivityResultLauncher<Intent> startActivityLauncher;

    VpnPrefsBinder(PreferenceFragmentCompat fragment) {
        this.fragment = fragment;
    }

    /**
     * Register the activity launcher and bind every action of the section. Must be called while
     * the fragment is being created, since registering a launcher later is not allowed.
     */
    void bind() {
        registerForStartActivity();
        bindExcludedSystemApps();
        bindExcludedUserApps();
        bindResetVpn();
        bindDiagnosticLogEnabled();
        bindDiagnosticLog();
        bindClearDiagnosticLog();
    }

    private <T extends Preference> T require(int keyResId) {
        T preference = this.fragment.findPreference(this.fragment.getString(keyResId));
        requireNonNull(preference, PREFERENCE_NOT_FOUND);
        return preference;
    }

    private void registerForStartActivity() {
        this.startActivityLauncher = this.fragment.registerForActivityResult(
                new StartActivityForResult(),
                result -> restartVpn()
        );
    }

    private void bindExcludedSystemApps() {
        ListPreference excludeUserAppsPreferences = require(R.string.pref_vpn_excluded_system_apps_key);
        excludeUserAppsPreferences.setOnPreferenceChangeListener((preference, newValue) -> {
            restartVpn();
            return true;
        });
    }

    private void bindExcludedUserApps() {
        Context context = this.fragment.requireContext();
        Preference excludeUserAppsPreferences = require(R.string.pref_vpn_excluded_user_apps_key);
        excludeUserAppsPreferences.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(context, PrefsVpnExcludedAppsActivity.class);
            this.startActivityLauncher.launch(intent);
            return true;
        });
    }

    private void bindResetVpn() {
        Context context = this.fragment.requireContext();
        Preference resetPreference = require(R.string.pref_vpn_reset_key);
        resetPreference.setOnPreferenceClickListener(preference -> {
            // Tear down and re-establish the tunnel so the DNS configuration is read again.
            // This recovers from a tunnel stuck in a broken state (e.g. established with no
            // resolver after a network transition) without needing to recreate the VPN profile.
            if (VpnServiceControls.isRunning(context)) {
                restartVpn();
                Toast.makeText(context, R.string.pref_vpn_reset_done, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, R.string.pref_vpn_reset_not_running, Toast.LENGTH_SHORT).show();
            }
            return true;
        });
    }

    private void restartVpn() {
        Context context = this.fragment.requireContext();
        if (VpnServiceControls.isRunning(context)) {
            VpnServiceControls.stop(context);
            VpnServiceControls.start(context);
        }
    }

    private void bindDiagnosticLogEnabled() {
        Context context = this.fragment.requireContext();
        Preference preference = require(R.string.pref_vpn_diagnostic_log_enabled_key);
        preference.setOnPreferenceChangeListener((p, newValue) -> {
            // The preference widget persists the new value itself; update the cached in-memory
            // flag too so recording starts/stops on the very next log line, not after a restart.
            DiagnosticLog.getInstance(context).setEnabled(Boolean.TRUE.equals(newValue));
            return true;
        });
    }

    private void bindDiagnosticLog() {
        Context context = this.fragment.requireContext();
        Preference preference = require(R.string.pref_vpn_diagnostic_log_key);
        preference.setOnPreferenceClickListener(p -> {
            // Reading the log touches disk and blocks until pending writes flush, so do it off
            // the main thread, then come back to show the dialog.
            AppExecutors executors = AppExecutors.getInstance();
            executors.diskIO().execute(() -> {
                String log = DiagnosticLog.getInstance(context).read();
                executors.mainThread().execute(() -> showDiagnosticLog(log));
            });
            return true;
        });
    }

    private void showDiagnosticLog(String log) {
        // The read is asynchronous: the fragment may have been detached in the meantime.
        if (!this.fragment.isAdded()) {
            return;
        }
        Context context = this.fragment.requireContext();
        boolean empty = log == null || log.trim().isEmpty();
        CharSequence message = empty
                ? this.fragment.getString(R.string.pref_vpn_diagnostic_log_empty)
                : previewOf(log);
        // Render the log in a custom view (a fixed-height ScrollView) instead of setMessage(): a
        // very long log made the dialog's own button row grow past the screen and forced the
        // user to scroll through the BUTTONS to find them. With a custom view, only this content
        // area scrolls; the Share/Copy/OK button bar stays outside it and always visible.
        View view = LayoutInflater.from(context).inflate(R.layout.pref_vpn_diagnostic_log_dialog, null);
        TextView textView = view.findViewById(R.id.diagnosticLogText);
        textView.setText(message);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.pref_vpn_diagnostic_log)
                .setView(view)
                .setNegativeButton(android.R.string.ok, null);
        if (!empty) {
            // Short labels ("Share"/"Copy"/"OK") so the 3 buttons always fit on one row instead
            // of Material's dialog stacking them vertically when the combined text is too wide.
            builder.setPositiveButton(R.string.pref_vpn_diagnostic_log_share, (dialog, which) -> shareLog(log));
            builder.setNeutralButton(R.string.pref_vpn_diagnostic_log_copy, (dialog, which) -> copyLog(log));
        }
        builder.show();
    }

    /**
     * Keep only the tail of the log for the on-screen preview so a large log does not make the
     * dialog sluggish; the full log is still what gets copied or shared.
     */
    private CharSequence previewOf(String log) {
        int maxChars = 20_000;
        if (log.length() <= maxChars) {
            return log;
        }
        return "…\n" + log.substring(log.length() - maxChars);
    }

    private void copyLog(String log) {
        Context context = this.fragment.requireContext();
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(
                this.fragment.getString(R.string.pref_vpn_diagnostic_log), log));
        Toast.makeText(context, R.string.pref_vpn_diagnostic_log_copied, Toast.LENGTH_SHORT).show();
    }

    private void shareLog(String log) {
        // pref_vpn_diagnostic_log_share_title (not the short button label) is used here: the
        // subject/chooser title can be descriptive since it is not competing for space with two
        // other dialog buttons.
        String title = this.fragment.getString(R.string.pref_vpn_diagnostic_log_share_title);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, title);
        intent.putExtra(Intent.EXTRA_TEXT, log);
        this.fragment.startActivity(Intent.createChooser(intent, title));
    }

    private void bindClearDiagnosticLog() {
        Context context = this.fragment.requireContext();
        Preference preference = require(R.string.pref_vpn_diagnostic_log_clear_key);
        preference.setOnPreferenceClickListener(p -> {
            DiagnosticLog.getInstance(context).clear();
            Toast.makeText(context, R.string.pref_vpn_diagnostic_log_cleared, Toast.LENGTH_SHORT).show();
            return true;
        });
    }
}
