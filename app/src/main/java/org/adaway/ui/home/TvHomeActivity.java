package org.adaway.ui.home;

import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static org.adaway.ui.lists.ListsActivity.ALLOWED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.BLOCKED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.REDIRECTED_HOSTS_TAB;
import static org.adaway.ui.lists.ListsActivity.TAB;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.net.VpnService;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;

import org.adaway.BuildConfig;
import org.adaway.broadcast.UpdateReceiver;
import org.adaway.R;
import org.adaway.helper.NotificationHelper;
import org.adaway.helper.PreferenceHelper;
import org.adaway.helper.ThemeHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.error.HostError;
import org.adaway.model.update.Manifest;
import org.adaway.ui.dialog.HostErrorDialog;
import org.adaway.ui.dialog.VpnPersistenceDialog;
import org.adaway.ui.help.HelpActivity;
import org.adaway.ui.hosts.TvHostsSourcesActivity;
import org.adaway.ui.lists.TvListsActivity;
import org.adaway.ui.log.TvLogActivity;
import org.adaway.ui.prefs.PrefsActivity;
import org.adaway.ui.update.UpdateActivity;
import org.adaway.ui.welcome.TvWelcomeActivity;
import org.adaway.vpn.VpnServiceControls;

import kotlin.jvm.functions.Function1;

import java.text.NumberFormat;

public class TvHomeActivity extends AppCompatActivity {

    private HomeViewModel homeViewModel;
    private View statusBadge;
    private ImageView statusIcon;
    private TextView statusText;
    private TextView stateDetailText;
    private TextView alwaysOnIndicator;
    private MaterialButton toggleButton;
    private Button appUpdateButton;
    private View blockedCardView;
    private View allowedCardView;
    private View redirectCardView;
    private View sourcesCardView;
    private View syncSourcesIcon;
    private View dnsMonitorTile;
    private View helpTile;
    private View preferencesTile;
    private View persistenceButton;
    private View themeButton;
    private TextView themeText;
    private ProgressBar progressBar;

    private ActivityResultLauncher<Intent> prepareVpnLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);

        // Defaulting straight into this screen used to just set the method here and continue,
        // skipping onboarding outright: the right call for a TV where the mobile welcome
        // screens are not remote-friendly, but that also meant a completely fresh install
        // immediately enabled the VPN with nothing in the local database yet - blocking zero
        // hosts, no error, no explanation why. TvWelcomeActivity now plays the role
        // WelcomeActivity plays for a fresh mobile install instead: sources are synced before
        // the VPN starts, in TvWelcomeActivity itself. This Activity is exported, so without
        // the TV check any app could start it on a phone and silently reach a wizard meant
        // only for a TV's own first run.
        //
        // isTvWelcomeDone, not AdBlockMethod == UNDEFINED: TvWelcomeActivity has to persist
        // AdBlockMethod = VPN the moment it opens (HomeViewModel resolves the ad-block model
        // once, in its own constructor), before the user has done anything - so that signal
        // is gone the instant the wizard is reached, even if the user never finishes it.
        // getVpnServiceUserEnabled() is only ever set by VpnModel.apply(), which the wizard
        // only reaches after a successful sync, so an abandoned attempt (D-pad trouble, a
        // sync failure, pressing Back) still reports false here and is sent back into the
        // wizard next time, instead of landing on a normal-looking Home screen stuck at zero
        // with no way back in.
        if (isRunningOnTv() && !PreferenceHelper.isTvWelcomeDone(this)) {
            if (!PreferenceHelper.getVpnServiceUserEnabled(this)) {
                startActivity(new Intent(this, TvWelcomeActivity.class));
                finish();
                return;
            }
            // Either a pre-existing install from before this wizard existed, or the wizard's
            // own sync-then-activate already genuinely succeeded before the user reached its
            // finish button: either way the VPN has really been turned on, so mark this
            // install as onboarded instead of re-deriving it on every single launch.
            PreferenceHelper.setTvWelcomeDone(this, true);
        }

        NotificationHelper.clearUpdateNotifications(this);
        UpdateReceiver.clearInstallToast(this);
        setContentView(R.layout.tv_activity_home);

        // Opening the app is the first moment recovery is possible after an OEM
        // battery-manager force-stop (which blocks sticky resurrection, the heartbeat
        // and broadcasts until then), so restart the VPN here if it was killed.
        VpnServiceControls.resurrectIfKilledExternally(this);

        ((TextView) findViewById(R.id.tv_version)).setText(getString(R.string.tv_version, BuildConfig.VERSION_NAME));

        statusBadge = findViewById(R.id.tv_status_badge);
        statusIcon = findViewById(R.id.tv_status_icon);
        statusText = findViewById(R.id.tv_status_text);
        stateDetailText = findViewById(R.id.tv_state_detail);
        alwaysOnIndicator = findViewById(R.id.tv_always_on_indicator);
        toggleButton = findViewById(R.id.btn_toggle);
        appUpdateButton = findViewById(R.id.btn_app_update);
        blockedCardView = findViewById(R.id.tv_blocked_card);
        allowedCardView = findViewById(R.id.tv_allowed_card);
        redirectCardView = findViewById(R.id.tv_redirect_card);
        sourcesCardView = findViewById(R.id.tv_sources_card);
        syncSourcesIcon = findViewById(R.id.tv_sync_sources_icon);
        dnsMonitorTile = findViewById(R.id.tv_tile_dns_monitor);
        helpTile = findViewById(R.id.tv_tile_help);
        preferencesTile = findViewById(R.id.tv_tile_preferences);
        persistenceButton = findViewById(R.id.btn_persistence);
        themeButton = findViewById(R.id.btn_theme);
        themeText = findViewById(R.id.tv_theme_text);
        progressBar = findViewById(R.id.progress_bar);

        bindThemeButton();

        homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        homeViewModel.isAdBlocked().observe(this, this::updateStatus);
        homeViewModel.getState().observe(this, text -> stateDetailText.setText(text));
        homeViewModel.getPending().observe(this, pending -> progressBar.setVisibility(pending ? View.VISIBLE : View.GONE));
        homeViewModel.getError().observe(this, this::notifyError);
        homeViewModel.getAppManifest().observe(this, this::bindAppUpdateBanner);
        bindHostCounter();
        bindSourceCounter();

        toggleButton.setOnClickListener(v -> homeViewModel.toggleAdBlocking());
        blockedCardView.setOnClickListener(v -> startHostListActivity(BLOCKED_HOSTS_TAB));
        allowedCardView.setOnClickListener(v -> startHostListActivity(ALLOWED_HOSTS_TAB));
        redirectCardView.setOnClickListener(v -> startHostListActivity(REDIRECTED_HOSTS_TAB));
        sourcesCardView.setOnClickListener(v -> startActivity(new Intent(this, TvHostsSourcesActivity.class)));
        syncSourcesIcon.setOnClickListener(v -> homeViewModel.sync());
        dnsMonitorTile.setOnClickListener(v -> startActivity(new Intent(this, TvLogActivity.class)));
        helpTile.setOnClickListener(v -> startActivity(new Intent(this, HelpActivity.class)));
        preferencesTile.setOnClickListener(v -> startActivity(new Intent(this, PrefsActivity.class)));
        persistenceButton.setOnClickListener(v -> showAlwaysOnVpnDialog());

        // Reached once, right after the user grants the VPN consent dialog checkFirstStep()
        // triggered below: the TV's only "first activation" moment, since it skips the welcome
        // wizard mobile uses for this (see the onCreate comment above). enable(), not
        // toggleAdBlocking(): the latter starts the VPN with whatever is already in the local
        // database, nothing at all on a fresh install, no error and no warning, just an
        // enabled-looking toggle blocking zero hosts. enable() syncs sources first and only
        // starts the VPN if that succeeds, the same ordering WelcomeSyncFragment.onCreateView()
        // already uses for mobile's own first activation.
        prepareVpnLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK) {
                homeViewModel.enable();
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
     * Bind the blocked/allowed/redirected counters shown in the stat cards, same
     * source and formatting as {@code HomeActivity.bindHostCounter()} on mobile.
     */
    private void bindHostCounter() {
        // Group the digits the locale way (e.g. 123,456)
        NumberFormat numberFormat = NumberFormat.getIntegerInstance();
        Function1<Integer, CharSequence> stringMapper = numberFormat::format;

        TextView blockedCountTextView = findViewById(R.id.tv_blocked_count);
        Transformations.map(homeViewModel.getBlockedHostCount(), stringMapper)
                .observe(this, blockedCountTextView::setText);

        TextView allowedCountTextView = findViewById(R.id.tv_allowed_count);
        Transformations.map(homeViewModel.getAllowedHostCount(), stringMapper)
                .observe(this, allowedCountTextView::setText);

        TextView redirectCountTextView = findViewById(R.id.tv_redirect_count);
        Transformations.map(homeViewModel.getRedirectHostCount(), stringMapper)
                .observe(this, redirectCountTextView::setText);
    }

    /**
     * Bind the sources card's up-to-date/outdated counts, same source and
     * formatting as {@code HomeActivity.bindSourceCounter()} on mobile.
     */
    private void bindSourceCounter() {
        Resources resources = getResources();

        TextView upToDateSourcesTextView = findViewById(R.id.tv_up_to_date_sources);
        homeViewModel.getUpToDateSourceCount().observe(this, count ->
                upToDateSourcesTextView.setText(resources.getQuantityString(R.plurals.up_to_date_source_label, count, count))
        );

        TextView outdatedSourcesTextView = findViewById(R.id.tv_outdated_sources);
        homeViewModel.getOutdatedSourceCount().observe(this, count ->
                outdatedSourcesTextView.setText(resources.getQuantityString(R.plurals.outdated_source_label, count, count))
        );

        TextView failedSourcesTextView = findViewById(R.id.tv_failed_sources);
        homeViewModel.getFailedSourceCount().observe(this, count -> {
            failedSourcesTextView.setText(resources.getQuantityString(R.plurals.failed_source_label, count, count));
            failedSourcesTextView.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        });
    }

    /**
     * Show the sync/toggle error dialog, the TV counterpart of {@code HomeActivity.notifyError()}
     * on mobile: {@code TvHomeActivity} had never observed {@code getError()} at all, so a failed
     * background sync (a hosts source timing out, no connection, …) previously showed nothing
     * here beyond the progress bar briefly appearing and disappearing again, count unchanged and
     * no indication anything had gone wrong. Same dialog shape, same reused strings: a plain
     * Close button for most errors, replaced with the welcome screen's own "Retry sync" action
     * for the two that a sync retry can actually fix.
     */
    private void notifyError(@Nullable HostError error) {
        if (error == null) {
            return;
        }
        HostErrorDialog.show(this, error, homeViewModel::sync);
    }

    /**
     * Start the TV hosts lists activity on the given tab.
     * <p>
     * {@code TvListsActivity}, not {@code ListsActivity}: the phone screen's swipeable tabs and
     * floating add button have no clean D-pad path, patched as far as that gets in two previous
     * commits; this is a from-scratch TV layout instead, built the way this app's other TV
     * screens already are. Same target/extra key as {@code HomeActivity.startHostListActivity()}
     * on mobile, read by {@code TvListsActivity} the same way {@code ListsActivity} does.
     */
    private void startHostListActivity(int tab) {
        Intent intent = new Intent(this, TvListsActivity.class);
        intent.putExtra(TAB, tab);
        startActivity(intent);
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
        themeText.setText(isNightNow
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
        // Same icon language as HomeActivity.notifyAdBlocked() on mobile: pause icon
        // (tap to stop) while active, power icon (tap to start) while off.
        toggleButton.setIconResource(isBlocked ? R.drawable.ic_pause_24dp : R.drawable.ic_tv_power_24);
        toggleButton.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this,
                isBlocked ? R.color.primary : R.color.pastelGreyFg)));
        int badgeBackground = ContextCompat.getColor(this,
                isBlocked ? R.color.pastelRedBg : R.color.pastelGreyBg);
        int badgeForeground = ContextCompat.getColor(this,
                isBlocked ? R.color.pastelRedFg : R.color.pastelGreyFg);
        statusBadge.setBackgroundTintList(ColorStateList.valueOf(badgeBackground));
        // Mobile shows its own pause icon while active and the app logo while off (tap
        // the mascot to start); mirrored here instead of the old block/check icons.
        statusIcon.setImageResource(isBlocked ? R.drawable.ic_pause_24dp : R.drawable.logo);
        statusIcon.setImageTintList(ColorStateList.valueOf(badgeForeground));
        // Refresh the passive always-on indicator whenever the VPN flips, since the
        // user might have just toggled the system setting from another screen.
        updateAlwaysOnIndicator();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // User may have just toggled Always-on VPN in system settings (or via ADB):
        // refresh the indicator when we come back to the foreground. Null-guarded because
        // onCreate returns early, before any of these are bound, when it redirects to the
        // welcome wizard.
        if (alwaysOnIndicator != null) {
            updateAlwaysOnIndicator();
            updatePersistenceVisibility();
        }
        // Resume an APK install that was queued before the user was sent to the
        // "install unknown apps" Settings screen. On Shield TV the AdAway process
        // is often killed during that screen and Android restores only this
        // launcher Activity (not UpdateActivity), so we have to drive the
        // resume from here.
        UpdateActivity.tryResumePendingInstall(this);
    }

    private void updateAlwaysOnIndicator() {
        alwaysOnIndicator.setVisibility(
                VpnPersistenceDialog.isAlwaysOnVpnEnabled(this) ? View.VISIBLE : View.GONE);
    }

    /**
     * Show the shared "VPN persistence" dialog, with the ADB commands included: the system VPN
     * settings screen is hidden outright on a fair share of Android TV builds, so the commands
     * are the only way in on those.
     */
    private void showAlwaysOnVpnDialog() {
        VpnPersistenceDialog.show(this, true, R.string.tv_persistence_intent_failed);
    }

    private void checkFirstStep() {
        AdBlockMethod adBlockMethod = PreferenceHelper.getAdBlockMethod(this);
        Intent prepareIntent;
        if (adBlockMethod == VPN && (prepareIntent = VpnService.prepare(this)) != null) {
            prepareVpnLauncher.launch(prepareIntent);
        }
    }

    private void bindAppUpdateBanner(@Nullable Manifest manifest) {
        // Only the left column's own vertical chain is affected by the banner's
        // visibility (it sits directly below the toggle, above persistence/theme,
        // all in that same column); the right column's sources card keeps its
        // static nextFocusUp to the first stat card regardless, since the banner
        // is not adjacent to it in either column layout.
        if (manifest != null && manifest.updateAvailable) {
            appUpdateButton.setText(getString(R.string.pref_update_install_summary, manifest.version));
            appUpdateButton.setVisibility(View.VISIBLE);
            appUpdateButton.setOnClickListener(v -> startActivity(new Intent(this, UpdateActivity.class)));
        } else {
            appUpdateButton.setVisibility(View.GONE);
        }
        applyBottomRowFocus();
    }

    /**
     * Show the persistence card in VPN mode only. Root mode installs the block list into the
     * system hosts file, where it stays on its own: there is no service to keep alive and no
     * Always-on VPN to pin, so the card would open a dialog with nothing to act on.
     * <p>
     * Refreshed on every resume rather than once at creation: the method is chosen in the
     * welcome wizard, which finishes back onto this screen.
     */
    private void updatePersistenceVisibility() {
        boolean vpnMode = PreferenceHelper.getAdBlockMethod(this) == VPN;
        persistenceButton.setVisibility(vpnMode ? View.VISIBLE : View.GONE);
        applyBottomRowFocus();
    }

    /**
     * Re-point every explicit D-pad link that lands on the bottom actions row, which holds the
     * persistence and theme cards.
     * <p>
     * An explicit {@code nextFocus*} aimed at a GONE view leaves the focus stuck where it is
     * rather than falling back to a geometric search, so hiding either the persistence card or
     * the update banner has to be followed by this. Both callers go through it, so whichever
     * runs last leaves the whole graph coherent regardless of their order.
     */
    private void applyBottomRowFocus() {
        boolean persistenceShown = persistenceButton.getVisibility() == View.VISIBLE;
        // What the row is entered on from above: the persistence card, or the theme card when
        // that row is down to it alone.
        int rowEntry = persistenceShown ? R.id.btn_persistence : R.id.btn_theme;
        appUpdateButton.setNextFocusDownId(rowEntry);
        dnsMonitorTile.setNextFocusDownId(rowEntry);
        helpTile.setNextFocusDownId(rowEntry);
        preferencesTile.setNextFocusDownId(rowEntry);
        // Nothing sits left of the theme card once persistence is hidden: clearing the id lets
        // Android run its own search, which then correctly finds nothing in that direction.
        themeButton.setNextFocusLeftId(persistenceShown ? R.id.btn_persistence : View.NO_ID);
        // The toggle drops into the update banner while it is showing, into the row otherwise.
        toggleButton.setNextFocusDownId(appUpdateButton.getVisibility() == View.VISIBLE
                ? R.id.btn_app_update
                : rowEntry);
    }

    /**
     * Check whether this Activity is actually running on a television.
     *
     * @return <code>true</code> on a TV, <code>false</code> anywhere else.
     */
    private boolean isRunningOnTv() {
        UiModeManager uiModeManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        return uiModeManager != null
                && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }
}
