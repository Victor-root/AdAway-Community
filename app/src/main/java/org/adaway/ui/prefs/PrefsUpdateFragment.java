package org.adaway.ui.prefs;

import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.TIRAMISU;
import static android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS;
import static android.provider.Settings.EXTRA_APP_PACKAGE;
import static org.adaway.ui.prefs.PrefsActivity.PREFERENCE_NOT_FOUND;
import static org.adaway.util.Constants.PREFS_NAME;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import org.adaway.AdAwayApplication;
import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.source.SourceUpdateService;
import org.adaway.model.update.ApkUpdateService;
import org.adaway.model.update.Manifest;
import org.adaway.model.update.UpdateModel;
import org.adaway.ui.update.UpdateActivity;
import org.adaway.util.AppExecutors;

public class PrefsUpdateFragment extends PreferenceFragmentCompat {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName(PREFS_NAME);
        addPreferencesFromResource(R.xml.preferences_update);
        bindNotificationPreferencesAction();
        bindAppUpdatePrefAction();
        bindCheckNowAction();
        bindInstallUpdateAction();
        bindHostsUpdatePrefAction();
        updateNotificationPreferencesState();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        PrefsActivity.setAppBarTitle(this, R.string.pref_update_title);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateNotificationPreferencesState();
    }

    private void bindNotificationPreferencesAction() {
        Context context = requireContext();
        Preference openNotificationPref = findPreference(getString(R.string.pref_update_open_notification_preferences_key));
        assert openNotificationPref != null : PREFERENCE_NOT_FOUND;
        openNotificationPref.setOnPreferenceClickListener(preference -> {
            Intent settingsIntent = new Intent(ACTION_APP_NOTIFICATION_SETTINGS)
                    .addFlags(FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_APP_PACKAGE, context.getPackageName());
            context.startActivity(settingsIntent);
            return true;
        });
    }

    private void bindAppUpdatePrefAction() {
        Context context = requireContext();
        SwitchPreferenceCompat checkAppDailyPref = findPreference(getString(R.string.pref_update_check_app_daily_key));
        assert checkAppDailyPref != null : PREFERENCE_NOT_FOUND;
        checkAppDailyPref.setOnPreferenceChangeListener((preference, newValue) -> {
            if (Boolean.TRUE.equals(newValue)) {
                ApkUpdateService.enable(context);
            } else {
                ApkUpdateService.disable(context);
            }
            return true;
        });
    }

    private void bindCheckNowAction() {
        Context context = requireContext();
        Preference checkNowPref = findPreference(getString(R.string.pref_update_check_now_key));
        assert checkNowPref != null : PREFERENCE_NOT_FOUND;
        UpdateModel updateModel = ((AdAwayApplication) context.getApplicationContext()).getUpdateModel();
        checkNowPref.setOnPreferenceClickListener(preference -> {
            preference.setEnabled(false);
            AppExecutors.getInstance().networkIO().execute(() -> {
                Manifest result = updateModel.checkForUpdateNow();
                AppExecutors.getInstance().mainThread().execute(() -> {
                    preference.setEnabled(true);
                    String message;
                    if (result != null && result.updateAvailable) {
                        message = getString(R.string.pref_update_check_now_update_available, result.version);
                    } else {
                        message = getString(R.string.pref_update_check_now_up_to_date);
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                });
            });
            return true;
        });
    }

    private void bindInstallUpdateAction() {
        Context context = requireContext();
        Preference installPref = findPreference(getString(R.string.pref_update_install_key));
        assert installPref != null : PREFERENCE_NOT_FOUND;
        UpdateModel updateModel = ((AdAwayApplication) context.getApplicationContext()).getUpdateModel();
        installPref.setVisible(false);
        installPref.setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(context, UpdateActivity.class));
            return true;
        });
        updateModel.getManifest().observe(this, manifest -> {
            if (manifest != null && manifest.updateAvailable) {
                installPref.setSummary(getString(R.string.pref_update_install_summary, manifest.version));
                installPref.setVisible(true);
            } else {
                installPref.setVisible(false);
            }
        });
    }

    private void bindHostsUpdatePrefAction() {
        Context context = requireContext();
        Preference checkHostsDailyPref = findPreference(getString(R.string.pref_update_check_hosts_daily_key));
        assert checkHostsDailyPref != null : PREFERENCE_NOT_FOUND;
        checkHostsDailyPref.setOnPreferenceChangeListener((preference, newValue) -> {
            if (Boolean.TRUE.equals(newValue)) {
                boolean unmeteredNetworkOnly = PreferenceHelper.getUpdateOnlyOnWifi(context);
                SourceUpdateService.enable(context, unmeteredNetworkOnly);
            } else {
                SourceUpdateService.disable(context);
            }
            return true;
        });
        Preference updateOnlyOnWifiPref = findPreference(this.getString(R.string.pref_update_only_on_wifi_key));
        assert updateOnlyOnWifiPref != null : PREFERENCE_NOT_FOUND;
        updateOnlyOnWifiPref.setOnPreferenceChangeListener((preference, newValue) -> {
            SourceUpdateService.enable(context, Boolean.TRUE.equals(newValue));
            return true;
        });
    }

    private void updateNotificationPreferencesState() {
        Context context = requireContext();
        Preference openNotificationPref = findPreference(getString(R.string.pref_update_open_notification_preferences_key));
        assert openNotificationPref != null : PREFERENCE_NOT_FOUND;
        boolean notificationsDisabled = SDK_INT >= TIRAMISU && context.checkSelfPermission(POST_NOTIFICATIONS) != PERMISSION_GRANTED;
        openNotificationPref.setVisible(notificationsDisabled);
    }
}
