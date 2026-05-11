package org.adaway.ui.home;

import static org.adaway.model.adblocking.AdBlockMethod.VPN;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.VpnService;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.adaway.R;
import org.adaway.helper.NotificationHelper;
import org.adaway.helper.PreferenceHelper;
import org.adaway.helper.ThemeHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.ui.hosts.HostsSourcesActivity;
import org.adaway.ui.log.TvLogActivity;

import timber.log.Timber;

public class TvHomeActivity extends AppCompatActivity {

    private HomeViewModel homeViewModel;
    private View statusBadge;
    private ImageView statusIcon;
    private TextView statusText;
    private TextView stateDetailText;
    private Button toggleButton;
    private Button updateButton;
    private Button syncButton;
    private Button dnsMonitorButton;
    private Button sourcesButton;
    private ProgressBar progressBar;

    private ActivityResultLauncher<Intent> prepareVpnLauncher;
    private boolean alwaysOnHintCheckedThisSession = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        NotificationHelper.clearUpdateNotifications(this);
        setContentView(R.layout.tv_activity_home);

        if (PreferenceHelper.getAdBlockMethod(this) == AdBlockMethod.UNDEFINED) {
            PreferenceHelper.setAbBlockMethod(this, VPN);
        }

        statusBadge = findViewById(R.id.tv_status_badge);
        statusIcon = findViewById(R.id.tv_status_icon);
        statusText = findViewById(R.id.tv_status_text);
        stateDetailText = findViewById(R.id.tv_state_detail);
        toggleButton = findViewById(R.id.btn_toggle);
        updateButton = findViewById(R.id.btn_update);
        syncButton = findViewById(R.id.btn_sync);
        dnsMonitorButton = findViewById(R.id.btn_dns_monitor);
        sourcesButton = findViewById(R.id.btn_sources);
        progressBar = findViewById(R.id.progress_bar);

        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        homeViewModel.isAdBlocked().observe(this, this::updateStatus);
        homeViewModel.getState().observe(this, text -> stateDetailText.setText(text));
        homeViewModel.getPending().observe(this, pending -> progressBar.setVisibility(pending ? View.VISIBLE : View.GONE));

        toggleButton.setOnClickListener(v -> homeViewModel.toggleAdBlocking());
        updateButton.setOnClickListener(v -> homeViewModel.update());
        syncButton.setOnClickListener(v -> homeViewModel.sync());
        dnsMonitorButton.setOnClickListener(v -> startActivity(new Intent(this, TvLogActivity.class)));
        sourcesButton.setOnClickListener(v -> startActivity(new Intent(this, HostsSourcesActivity.class)));

        prepareVpnLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                homeViewModel.toggleAdBlocking();
            }
        });

        checkFirstStep();
    }

    private void updateStatus(boolean isBlocked) {
        statusText.setText(isBlocked ? R.string.tv_status_enabled : R.string.tv_status_disabled);
        toggleButton.setText(isBlocked ? R.string.button_disable_hosts : R.string.button_enable_hosts);
        int badgeColor = ContextCompat.getColor(this,
                isBlocked ? R.color.cardEnabledBackground : R.color.cardBackground);
        statusBadge.setBackgroundTintList(ColorStateList.valueOf(badgeColor));
        statusIcon.setImageResource(isBlocked ? R.drawable.baseline_check_24 : R.drawable.baseline_block_24);
        // First time the user sees the VPN running on the TV, offer to enable the
        // Android system "Always-on VPN" setting. One-shot per install (dismissible).
        if (isBlocked && !alwaysOnHintCheckedThisSession) {
            alwaysOnHintCheckedThisSession = true;
            if (!PreferenceHelper.isTvAlwaysOnVpnHintShown(this)) {
                showAlwaysOnVpnHint();
            }
        }
    }

    private void showAlwaysOnVpnHint() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.tv_always_on_hint_title)
                .setMessage(R.string.tv_always_on_hint_message)
                .setPositiveButton(R.string.tv_always_on_hint_open_settings, (dialog, which) -> {
                    PreferenceHelper.setTvAlwaysOnVpnHintShown(this, true);
                    tryOpenVpnSettings();
                })
                .setNegativeButton(R.string.tv_always_on_hint_later, (dialog, which) ->
                        PreferenceHelper.setTvAlwaysOnVpnHintShown(this, true))
                .show();
    }

    private void tryOpenVpnSettings() {
        Intent intent = new Intent(Settings.ACTION_VPN_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Some Android TV launchers (NVIDIA SHIELD, Google TV, …) hide the VPN
        // settings activity. Resolve before launching, and fall back to ADB
        // instructions if either the resolver or startActivity says no.
        if (intent.resolveActivity(getPackageManager()) == null) {
            Timber.i("VPN settings activity is not exposed by this Android TV launcher.");
            showAdbFallbackDialog();
            return;
        }
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Timber.w(e, "Failed to open VPN settings, showing ADB fallback.");
            showAdbFallbackDialog();
        }
    }

    private void showAdbFallbackDialog() {
        String message = getString(R.string.tv_always_on_fallback_message, getPackageName());
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.tv_always_on_fallback_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void checkFirstStep() {
        AdBlockMethod adBlockMethod = PreferenceHelper.getAdBlockMethod(this);
        Intent prepareIntent;
        if (adBlockMethod == VPN && (prepareIntent = VpnService.prepare(this)) != null) {
            prepareVpnLauncher.launch(prepareIntent);
        }
    }
}
