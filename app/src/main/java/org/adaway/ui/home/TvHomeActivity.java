package org.adaway.ui.home;

import static org.adaway.model.adblocking.AdBlockMethod.VPN;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import org.adaway.R;
import org.adaway.helper.NotificationHelper;
import org.adaway.helper.PreferenceHelper;
import org.adaway.helper.ThemeHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.ui.hosts.HostsSourcesActivity;
import org.adaway.ui.log.TvLogActivity;

public class TvHomeActivity extends AppCompatActivity {

    private HomeViewModel homeViewModel;
    private TextView statusText;
    private TextView stateDetailText;
    private Button toggleButton;
    private Button updateButton;
    private Button syncButton;
    private Button dnsMonitorButton;
    private Button sourcesButton;
    private ProgressBar progressBar;

    private ActivityResultLauncher<Intent> prepareVpnLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        NotificationHelper.clearUpdateNotifications(this);
        setContentView(R.layout.tv_activity_home);

        if (PreferenceHelper.getAdBlockMethod(this) == AdBlockMethod.UNDEFINED) {
            PreferenceHelper.setAbBlockMethod(this, VPN);
        }

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
    }

    private void checkFirstStep() {
        AdBlockMethod adBlockMethod = PreferenceHelper.getAdBlockMethod(this);
        Intent prepareIntent;
        if (adBlockMethod == VPN && (prepareIntent = VpnService.prepare(this)) != null) {
            prepareVpnLauncher.launch(prepareIntent);
        }
    }
}
