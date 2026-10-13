package org.adaway.ui.welcome;

import static org.adaway.model.adblocking.AdBlockMethod.ROOT;
import static org.adaway.model.adblocking.AdBlockMethod.VPN;
import static java.lang.Boolean.TRUE;

import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.topjohnwu.superuser.Shell;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.helper.ThemeHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.model.error.HostError;
import org.adaway.ui.home.HomeViewModel;
import org.adaway.ui.home.TvHomeActivity;

import timber.log.Timber;

/**
 * Android TV's own first-run wizard: three screens (welcome, method choice, then activate)
 * reached only from {@link TvHomeActivity} when the ad-block method is still undefined, playing
 * the role {@link WelcomeActivity} plays for a fresh mobile install. See tv_activity_welcome.xml
 * for why this is not just that Activity adapted for D-pad: the root-vs-VPN choice is kept (as
 * two stacked buttons instead of mobile's tappable cards), but the phone-OS/phone-brand battery
 * guidance mobile shows does not apply to a TV box at all, so that part is dropped.
 * <p>
 * Reuses the exact {@link HomeViewModel#enable()} mobile's own {@link WelcomeSyncFragment} calls
 * for this same first activation: sources are synced before ad blocking starts, and it does not
 * start at all if that sync fails, rather than starting immediately against an empty database the
 * way {@link HomeViewModel#toggleAdBlocking()} would.
 *
 * @author AdAway Community
 */
public class TvWelcomeActivity extends AppCompatActivity {
    private HomeViewModel homeViewModel;
    private View introPage;
    private View methodPage;
    private View activatePage;
    private View promptGroup;
    private View progressGroup;
    private View successGroup;
    private View errorGroup;
    private TextView progressDetailText;
    private TextView errorText;
    private MaterialButton rootButton;
    private MaterialButton vpnButton;
    private MaterialButton activateButton;
    private MaterialButton finishButton;
    private MaterialButton retryButton;
    private ActivityResultLauncher<Intent> prepareVpnLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        setContentView(R.layout.tv_activity_welcome);
        bindViews();

        this.prepareVpnLauncher = registerForActivityResult(new StartActivityForResult(), result -> {
            Timber.d("TvWelcomeActivity: VPN prepare result=%d.", result.getResultCode());
            if (result.getResultCode() == RESULT_OK) {
                showProgress();
                this.homeViewModel.enable();
            }
            // Cancelled: whatever this screen was already showing (the prompt, or an error
            // from a previous attempt) stays exactly as it was; the button is still there.
        });
    }

    /**
     * Lazily builds {@link #homeViewModel} bound to the chosen method, the first time either
     * method is actually committed to (root only once its grant is confirmed; VPN as soon as
     * it's chosen, {@link VpnService#prepare} being the actual consent step for that one).
     * <p>
     * Fixed before anything touches it: {@code AdAwayApplication.getAdBlockModel()} only
     * re-resolves the method when asked again, and {@code HomeViewModel} reads it once, in its
     * own constructor - so the preference has to be set first, or {@code enable()} later would
     * run against an {@code UndefinedBlockModel} that does nothing at all. Never called twice
     * with two different methods in practice: this screen only ever moves forward, never back
     * to a fresh method choice once one has actually been committed to.
     */
    private HomeViewModel homeViewModel(AdBlockMethod method) {
        if (this.homeViewModel == null) {
            PreferenceHelper.setAbBlockMethod(this, method);
            this.homeViewModel = new ViewModelProvider(this).get(HomeViewModel.class);
            this.homeViewModel.getState().observe(this, state -> this.progressDetailText.setText(state));
            this.homeViewModel.getError().observe(this, this::showError);
            this.homeViewModel.isEnableCompleted().observe(this, completed -> {
                Timber.d("TvWelcomeActivity: enableCompleted=%s.", completed);
                if (completed) {
                    showSuccess();
                }
            });
        }
        return this.homeViewModel;
    }

    private void bindViews() {
        this.introPage = findViewById(R.id.tv_welcome_intro_page);
        this.methodPage = findViewById(R.id.tv_welcome_method_page);
        this.activatePage = findViewById(R.id.tv_welcome_activate_page);
        this.promptGroup = findViewById(R.id.tv_welcome_prompt_group);
        this.progressGroup = findViewById(R.id.tv_welcome_progress_group);
        this.successGroup = findViewById(R.id.tv_welcome_success_group);
        this.errorGroup = findViewById(R.id.tv_welcome_error_group);
        this.progressDetailText = findViewById(R.id.tv_welcome_progress_detail);
        this.errorText = findViewById(R.id.tv_welcome_error_text);

        MaterialButton startButton = findViewById(R.id.tv_welcome_start_button);
        this.rootButton = findViewById(R.id.tv_welcome_root_button);
        this.vpnButton = findViewById(R.id.tv_welcome_vpn_button);
        this.activateButton = findViewById(R.id.tv_welcome_activate_button);
        this.finishButton = findViewById(R.id.tv_welcome_finish_button);
        this.retryButton = findViewById(R.id.tv_welcome_retry_button);

        startButton.setOnClickListener(v -> showMethodPage());
        this.rootButton.setOnClickListener(v -> chooseRoot());
        this.vpnButton.setOnClickListener(v -> chooseVpn());
        this.activateButton.setOnClickListener(v -> activate());
        this.retryButton.setOnClickListener(v -> retry());
        this.finishButton.setOnClickListener(v -> finishWizard());
    }

    /**
     * Toggling a sibling's visibility to VISIBLE does not move D-pad focus there on its own -
     * {@code focusedByDefault} only applies to an Activity's very first layout pass, not to
     * later visibility changes. Posting the request runs it after the layout pass the
     * visibility change above just triggered, once the view is actually focusable; requesting
     * it immediately, before that pass runs, is what left the wizard almost never focused on
     * its own buttons.
     */
    private void focusWhenLaidOut(View view) {
        view.post(view::requestFocus);
    }

    private void showMethodPage() {
        this.introPage.setVisibility(View.GONE);
        this.methodPage.setVisibility(View.VISIBLE);
        focusWhenLaidOut(this.rootButton);
    }

    private void showActivatePage() {
        this.methodPage.setVisibility(View.GONE);
        this.activatePage.setVisibility(View.VISIBLE);
    }

    private void chooseVpn() {
        homeViewModel(VPN);
        showActivatePage();
        // Default-visible sub-state of activatePage (see tv_activity_welcome.xml): the prompt
        // group needs no extra setVisibility call here, only its button focused.
        focusWhenLaidOut(this.activateButton);
    }

    /**
     * Requests root the same way mobile's {@code WelcomeMethodFragment.checkRoot()} does, via
     * libsu, then either commits to root and jumps straight to syncing - no root equivalent of
     * {@link VpnService#prepare}'s consent dialog to show a prompt sub-state for first, granting
     * the request IS the consent moment - or reports it missing and leaves the user on this same
     * screen, free to try again or pick VPN instead.
     */
    private void chooseRoot() {
        this.rootButton.setEnabled(false);
        Shell.getShell(shell -> {
            boolean granted = TRUE.equals(Shell.isAppGrantedRoot());
            Timber.d("TvWelcomeActivity.chooseRoot: granted=%s.", granted);
            runOnUiThread(() -> {
                // The root prompt can outlive the activity
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                this.rootButton.setEnabled(true);
                if (granted) {
                    HomeViewModel viewModel = homeViewModel(ROOT);
                    showActivatePage();
                    showProgress();
                    viewModel.enable();
                } else {
                    showRootMissingDialog();
                }
            });
        });
    }

    private void showRootMissingDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.welcome_root_missing_title)
                .setMessage(R.string.welcome_root_missile_description)
                .setPositiveButton(R.string.button_close, null)
                .create()
                .show();
    }

    /**
     * Used both for the first attempt and for a retry after an error: {@link VpnService#prepare}
     * only returns a non-null intent when consent is actually still needed, so a retry after the
     * user already granted it the first time skips straight to re-syncing, no repeat dialog.
     */
    private void activate() {
        Intent prepareIntent = VpnService.prepare(this);
        Timber.d("TvWelcomeActivity.activate: prepareIntent=%s.", prepareIntent);
        if (prepareIntent == null) {
            showProgress();
            this.homeViewModel.enable();
        } else {
            this.prepareVpnLauncher.launch(prepareIntent);
        }
    }

    /**
     * The error sub-state's retry button, shared by both methods: VPN goes through {@link
     * #activate()} again since consent can theoretically have been revoked between attempts,
     * root has no equivalent re-check and just retries the sync directly.
     */
    private void retry() {
        if (PreferenceHelper.getAdBlockMethod(this) == VPN) {
            activate();
        } else {
            showProgress();
            this.homeViewModel.enable();
        }
    }

    private void showProgress() {
        this.promptGroup.setVisibility(View.GONE);
        this.errorGroup.setVisibility(View.GONE);
        this.progressGroup.setVisibility(View.VISIBLE);
    }

    private void showSuccess() {
        this.promptGroup.setVisibility(View.GONE);
        this.progressGroup.setVisibility(View.GONE);
        this.errorGroup.setVisibility(View.GONE);
        this.successGroup.setVisibility(View.VISIBLE);
        focusWhenLaidOut(this.finishButton);
    }

    private void showError(@Nullable HostError error) {
        if (error == null) {
            return;
        }
        Timber.d("TvWelcomeActivity.showError: %s.", error);
        this.errorText.setText(getString(R.string.welcome_sync_error, getString(error.getMessageKey())));
        this.progressGroup.setVisibility(View.GONE);
        this.errorGroup.setVisibility(View.VISIBLE);
        focusWhenLaidOut(this.retryButton);
    }

    private void finishWizard() {
        PreferenceHelper.setTvWelcomeDone(this, true);
        startActivity(new Intent(this, TvHomeActivity.class));
        finish();
    }
}
