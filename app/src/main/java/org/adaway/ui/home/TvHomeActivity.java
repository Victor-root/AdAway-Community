package org.adaway.ui.home;

import static org.adaway.model.adblocking.AdBlockMethod.VPN;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.net.VpnService;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.adaway.R;
import org.adaway.helper.NotificationHelper;
import org.adaway.helper.PreferenceHelper;
import org.adaway.helper.ThemeHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.update.Manifest;
import org.adaway.ui.hosts.HostsSourcesActivity;
import org.adaway.ui.log.TvLogActivity;
import org.adaway.ui.prefs.PrefsActivity;
import org.adaway.ui.update.UpdateActivity;

import timber.log.Timber;

public class TvHomeActivity extends AppCompatActivity {

    private HomeViewModel homeViewModel;
    private View statusBadge;
    private ImageView statusIcon;
    private TextView statusText;
    private TextView stateDetailText;
    private TextView alwaysOnIndicator;
    private Button toggleButton;
    private Button appUpdateButton;
    private Button updateButton;
    private Button syncButton;
    private Button dnsMonitorButton;
    private Button sourcesButton;
    private Button persistenceButton;
    private Button themeButton;
    private Button settingsButton;
    private ProgressBar progressBar;

    private ActivityResultLauncher<Intent> prepareVpnLauncher;
    private boolean alwaysOnHintCheckedThisSession = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        NotificationHelper.clearUpdateNotifications(this);
        org.adaway.broadcast.UpdateReceiver.clearInstallToast(this);
        setContentView(R.layout.tv_activity_home);

        if (PreferenceHelper.getAdBlockMethod(this) == AdBlockMethod.UNDEFINED) {
            PreferenceHelper.setAbBlockMethod(this, VPN);
        }

        ((TextView) findViewById(R.id.tv_version)).setText("v" + getCurrentVersionName());

        statusBadge = findViewById(R.id.tv_status_badge);
        statusIcon = findViewById(R.id.tv_status_icon);
        statusText = findViewById(R.id.tv_status_text);
        stateDetailText = findViewById(R.id.tv_state_detail);
        alwaysOnIndicator = findViewById(R.id.tv_always_on_indicator);
        toggleButton = findViewById(R.id.btn_toggle);
        appUpdateButton = findViewById(R.id.btn_app_update);
        updateButton = findViewById(R.id.btn_update);
        syncButton = findViewById(R.id.btn_sync);
        dnsMonitorButton = findViewById(R.id.btn_dns_monitor);
        sourcesButton = findViewById(R.id.btn_sources);
        persistenceButton = findViewById(R.id.btn_persistence);
        themeButton = findViewById(R.id.btn_theme);
        settingsButton = findViewById(R.id.btn_settings);
        progressBar = findViewById(R.id.progress_bar);

        bindThemeButton();

        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        homeViewModel.isAdBlocked().observe(this, this::updateStatus);
        homeViewModel.getState().observe(this, text -> stateDetailText.setText(text));
        homeViewModel.getPending().observe(this, pending -> progressBar.setVisibility(pending ? View.VISIBLE : View.GONE));
        homeViewModel.getAppManifest().observe(this, this::bindAppUpdateBanner);

        toggleButton.setOnClickListener(v -> homeViewModel.toggleAdBlocking());
        updateButton.setOnClickListener(v -> homeViewModel.update());
        syncButton.setOnClickListener(v -> homeViewModel.sync());
        dnsMonitorButton.setOnClickListener(v -> startActivity(new Intent(this, TvLogActivity.class)));
        sourcesButton.setOnClickListener(v -> startActivity(new Intent(this, HostsSourcesActivity.class)));
        persistenceButton.setOnClickListener(v -> {
            // Manual open: also flip the "shown" pref so we stop auto-popping the
            // dialog on subsequent VPN activations.
            PreferenceHelper.setTvAlwaysOnVpnHintShown(this, true);
            showAlwaysOnVpnDialog();
        });
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, PrefsActivity.class)));

        prepareVpnLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                homeViewModel.toggleAdBlocking();
            }
        });

        // Only on a true fresh start. On a configuration-change recreation
        // (theme application during the first launch is the usual trigger here)
        // savedInstanceState is non-null, and re-running checkFirstStep would
        // queue a second VpnService consent dialog, forcing the user to click
        // "OK" twice.
        if (savedInstanceState == null) {
            checkFirstStep();
        }

        if (savedInstanceState == null && PreferenceHelper.getUpdateCheckAppStartup(this)) {
            homeViewModel.checkForAppUpdate();
        }
    }

    /**
     * Wire the theme toggle button. The label reflects what tapping will switch
     * TO, based on the effective ui-mode (UI_MODE_NIGHT_YES/NO from the current
     * Configuration). Tapping persists the new value and lets AppCompatDelegate
     * recreate the activity with the new theme applied.
     */
    private void bindThemeButton() {
        boolean isNightNow = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        themeButton.setText(isNightNow
                ? R.string.tv_button_theme_light
                : R.string.tv_button_theme_dark);
        themeButton.setOnClickListener(v -> {
            String newMode = isNightNow ? "MODE_NIGHT_NO" : "MODE_NIGHT_YES";
            PreferenceHelper.setDarkThemeMode(this, newMode);
            AppCompatDelegate.setDefaultNightMode(isNightNow
                    ? AppCompatDelegate.MODE_NIGHT_NO
                    : AppCompatDelegate.MODE_NIGHT_YES);
        });
    }

    private void updateStatus(boolean isBlocked) {
        statusText.setText(isBlocked ? R.string.tv_status_enabled : R.string.tv_status_disabled);
        toggleButton.setText(isBlocked ? R.string.button_disable_hosts : R.string.button_enable_hosts);
        int badgeColor = ContextCompat.getColor(this,
                isBlocked ? R.color.cardEnabledBackground : R.color.cardBackground);
        statusBadge.setBackgroundTintList(ColorStateList.valueOf(badgeColor));
        statusIcon.setImageResource(isBlocked ? R.drawable.baseline_check_24 : R.drawable.baseline_block_24);
        // Refresh the passive always-on indicator whenever the VPN flips, since the
        // user might have just toggled the system setting from another screen.
        updateAlwaysOnIndicator();
        // First time the user sees the VPN running on the TV, suggest enabling
        // Always-on VPN. Skip the nag if it's already configured.
        if (isBlocked && !alwaysOnHintCheckedThisSession) {
            alwaysOnHintCheckedThisSession = true;
            boolean alreadyConfigured = isAlwaysOnVpnConfiguredForUs();
            if (!alreadyConfigured && !PreferenceHelper.isTvAlwaysOnVpnHintShown(this)) {
                PreferenceHelper.setTvAlwaysOnVpnHintShown(this, true);
                showAlwaysOnVpnDialog();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // User may have just toggled Always-on VPN in system settings (or via ADB)
        // — refresh the indicator when we come back to the foreground.
        if (alwaysOnIndicator != null) {
            updateAlwaysOnIndicator();
        }
        // Resume an APK install that was queued before the user was sent to the
        // "install unknown apps" Settings screen. On Shield TV the AdAway process
        // is often killed during that screen and Android restores only this
        // launcher Activity (not UpdateActivity), so we have to drive the
        // resume from here.
        UpdateActivity.tryResumePendingInstall(this);
    }

    /**
     * Returns true when the Android system has AdAway set as the Always-on VPN app
     * (Settings.Secure.ALWAYS_ON_VPN_APP, with fallback to the Global namespace for
     * older or customized builds). Returns false in any other case, including when
     * the setting is not readable.
     */
    private boolean isAlwaysOnVpnConfiguredForUs() {
        String pkg = getPackageName();
        ContentResolver cr = getContentResolver();
        try {
            String alwaysOnApp = Settings.Secure.getString(cr, "always_on_vpn_app");
            if (alwaysOnApp == null || alwaysOnApp.isEmpty()) {
                alwaysOnApp = Settings.Global.getString(cr, "always_on_vpn_app");
            }
            return pkg.equals(alwaysOnApp);
        } catch (SecurityException e) {
            Timber.w(e, "Cannot read always-on VPN setting; treating as not configured.");
            return false;
        }
    }

    private void updateAlwaysOnIndicator() {
        alwaysOnIndicator.setVisibility(isAlwaysOnVpnConfiguredForUs() ? View.VISIBLE : View.GONE);
    }

    /**
     * Show the unified "VPN persistence" dialog. Always renders the current
     * always-on detection status plus the ADB commands, so the user can
     * verify the current state and copy the commands at any time.
     */
    private void showAlwaysOnVpnDialog() {
        boolean configured = isAlwaysOnVpnConfiguredForUs();
        String statusLine = getString(configured
                ? R.string.tv_persistence_status_enabled
                : R.string.tv_persistence_status_disabled);
        String message = statusLine
                + "\n\n"
                + getString(R.string.tv_always_on_hint_message)
                + "\n\n"
                + getString(R.string.tv_persistence_adb_commands, getPackageName());
        // Build with null click listeners so the auto-dismiss-on-click is wired,
        // then override the positive button after show(): if the Intent fails
        // (Toast shown), keep the dialog up so the user can still read the ADB
        // commands instead of having to re-open the dialog.
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.tv_always_on_hint_title)
                .setMessage(message)
                .setPositiveButton(R.string.tv_always_on_hint_open_settings, null)
                .setNegativeButton(R.string.tv_always_on_hint_later, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (tryOpenVpnSettings()) {
                        dialog.dismiss();
                    }
                    // else: VPN settings are hidden, Toast already shown,
                    // keep the dialog open so the ADB commands stay visible.
                }));
        dialog.show();
    }

    /**
     * Tries to open the system VPN settings via {@link Settings#ACTION_VPN_SETTINGS}.
     * Non-blocking: if the Activity is unresolved or {@code startActivity} throws,
     * a Toast points the user back to the ADB commands. Returns {@code true} when
     * the system Activity successfully started, {@code false} otherwise so the
     * caller can decide whether to keep its UI on screen.
     */
    private boolean tryOpenVpnSettings() {
        Intent intent = new Intent(Settings.ACTION_VPN_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(getPackageManager()) != null) {
            try {
                startActivity(intent);
                return true;
            } catch (ActivityNotFoundException | SecurityException e) {
                Timber.w(e, "Failed to open VPN settings.");
            }
        }
        Toast.makeText(this, R.string.tv_persistence_intent_failed, Toast.LENGTH_LONG).show();
        return false;
    }

    private void checkFirstStep() {
        AdBlockMethod adBlockMethod = PreferenceHelper.getAdBlockMethod(this);
        Intent prepareIntent;
        if (adBlockMethod == VPN && (prepareIntent = VpnService.prepare(this)) != null) {
            prepareVpnLauncher.launch(prepareIntent);
        }
    }

    private String getCurrentVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return "?";
        }
    }

    private void bindAppUpdateBanner(@Nullable Manifest manifest) {
        if (manifest != null && manifest.updateAvailable) {
            appUpdateButton.setText(getString(R.string.pref_update_install_summary, manifest.version));
            appUpdateButton.setVisibility(View.VISIBLE);
            appUpdateButton.setOnClickListener(v -> startActivity(new Intent(this, UpdateActivity.class)));
            toggleButton.setNextFocusDownId(R.id.btn_app_update);
            updateButton.setNextFocusUpId(R.id.btn_app_update);
        } else {
            appUpdateButton.setVisibility(View.GONE);
            toggleButton.setNextFocusDownId(R.id.btn_update);
            updateButton.setNextFocusUpId(R.id.btn_toggle);
        }
    }
}
