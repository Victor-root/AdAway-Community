package org.adaway.ui.prefs;

import static android.os.Build.VERSION.SDK_INT;
import static android.provider.Settings.ACTION_SECURITY_SETTINGS;
import static android.widget.Toast.LENGTH_SHORT;
import static java.util.Objects.requireNonNull;
import static org.adaway.ui.prefs.PrefsActivity.PREFERENCE_NOT_FOUND;
import static org.adaway.util.Constants.ANDROID_SYSTEM_ETC_HOSTS;
import static org.adaway.util.WebServerUtils.TEST_URL;
import static org.adaway.util.WebServerUtils.copyCertificate;
import static org.adaway.util.WebServerUtils.getWebServerState;
import static org.adaway.util.WebServerUtils.installCertificate;
import static org.adaway.util.WebServerUtils.isWebServerRunning;
import static org.adaway.util.WebServerUtils.startWebServer;
import static org.adaway.util.WebServerUtils.stopWebServer;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build.VERSION_CODES;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.FileProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.net.InetAddresses;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.ui.dialog.MissingAppDialog;
import org.adaway.util.AppExecutors;
import org.adaway.util.ActivityStarter;

import java.io.File;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

import timber.log.Timber;

/**
 * Wires up the root ad blocker section of {@link PrefsMainFragment}.
 * <p>
 * These settings used to live on a sub-screen of their own, behind an entry the user had to
 * open first. They are now part of the main preferences screen, so this holds the behaviour
 * that screen's fragment used to carry, keeping the fragment itself readable.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
final class RootPrefsBinder {
    /**
     * The webserver certificate mime type.
     */
    private static final String CERTIFICATE_MIME_TYPE = "application/x-x509-ca-cert";

    private final PreferenceFragmentCompat fragment;
    /**
     * The launcher to prepare web service certificate activity.
     */
    private ActivityResultLauncher<String> prepareCertificateLauncher;

    RootPrefsBinder(PreferenceFragmentCompat fragment) {
        this.fragment = fragment;
    }

    /**
     * Register the activity launchers and bind every action of the section. Must be called while
     * the fragment is being created, since registering a launcher later is not allowed.
     */
    void bind() {
        registerForPrepareCertificateActivity();
        bindOpenHostsFile();
        bindRedirection();
        bindWebServerPrefAction();
        bindWebServerTest();
        bindWebServerCertificate();
        // Probing the local web server costs a background request and a short wait, so only do
        // it when these settings actually apply. On VPN mode the whole section is dimmed.
        if (isRootMethodActive()) {
            updateWebServerState();
        }
    }

    /**
     * Whether root is the ad blocking method in use, which is when this section applies.
     *
     * @return <code>true</code> in root mode.
     */
    private boolean isRootMethodActive() {
        return PreferenceHelper.getAdBlockMethod(this.fragment.requireContext()) == AdBlockMethod.ROOT;
    }

    /**
     * Refresh the reported web server state, on resume like the former screen did.
     */
    void onResume() {
        if (isRootMethodActive()) {
            updateWebServerState();
        }
    }

    /**
     * Restart the web server when its icon setting changes, so the change takes effect at once.
     *
     * @param key The preference key that changed.
     */
    void onSharedPreferenceChanged(String key) {
        Context context = this.fragment.requireContext();
        if (context.getString(R.string.pref_webserver_icon_key).equals(key) && isWebServerRunning()) {
            stopWebServer();
            startWebServer(context);
            updateWebServerState();
        }
    }

    private <T extends Preference> T require(int keyResId) {
        T preference = this.fragment.findPreference(this.fragment.getString(keyResId));
        requireNonNull(preference, PREFERENCE_NOT_FOUND);
        return preference;
    }

    private void registerForPrepareCertificateActivity() {
        this.prepareCertificateLauncher = this.fragment.registerForActivityResult(
                new ActivityResultContracts.CreateDocument(CERTIFICATE_MIME_TYPE),
                this::prepareWebServerCertificate
        );
    }

    private void bindOpenHostsFile() {
        Preference openHostsFilePreference = require(R.string.pref_open_hosts_key);
        openHostsFilePreference.setOnPreferenceClickListener(this::openHostsFile);
    }

    private boolean openHostsFile(Preference preference) {
        Context context = this.fragment.requireContext();
        try {
            // Read-only: another app cannot write the root owned hosts file
            File hostFile = new File(ANDROID_SYSTEM_ETC_HOSTS).getCanonicalFile();
            Uri hostsUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", hostFile);
            Intent intent = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(hostsUri, "text/plain")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            this.fragment.startActivity(intent);
            return true;
        } catch (IOException | IllegalArgumentException e) {
            Timber.e(e, "Failed to share the hosts file.");
        } catch (ActivityNotFoundException e) {
            MissingAppDialog.showTextEditorMissingDialog(context);
        }
        return false;
    }

    private void bindRedirection() {
        Context context = this.fragment.requireContext();
        boolean ipv6Enabled = PreferenceHelper.getEnableIpv6(context);
        Preference ipv4RedirectionPreference = require(R.string.pref_redirection_ipv4_key);
        ipv4RedirectionPreference.setOnPreferenceChangeListener(
                (preference, newValue) -> validateRedirection(Inet4Address.class, (String) newValue)
        );
        Preference ipv6RedirectionPreference = require(R.string.pref_redirection_ipv6_key);
        ipv6RedirectionPreference.setEnabled(ipv6Enabled);
        ipv6RedirectionPreference.setOnPreferenceChangeListener(
                (preference, newValue) -> validateRedirection(Inet6Address.class, (String) newValue)
        );
    }

    private boolean validateRedirection(Class<? extends InetAddress> addressType, String redirection) {
        boolean valid;
        try {
            InetAddress inetAddress = InetAddresses.forString(redirection);
            valid = addressType.isAssignableFrom(inetAddress.getClass());
        } catch (IllegalArgumentException exception) {
            valid = false;
        }
        if (!valid) {
            Toast.makeText(this.fragment.requireContext(), R.string.pref_redirection_invalid, LENGTH_SHORT).show();
        }
        return valid;
    }

    private void bindWebServerPrefAction() {
        Context context = this.fragment.requireContext();
        // Start web server when preference is enabled
        SwitchPreferenceCompat webServerEnabledPref = require(R.string.pref_webserver_enabled_key);
        webServerEnabledPref.setOnPreferenceChangeListener((preference, newValue) -> {
            boolean enabled = newValue.equals(true);
            if (enabled) {
                startWebServer(context);
            } else {
                stopWebServer();
            }
            updateWebServerState();
            // The web server binary is started or killed in a background shell command, so it
            // needs a moment before it actually shows up (or disappears) from the process list.
            // Accept the switch change right away and correct it afterwards if the process
            // never reached the expected state, instead of checking synchronously here and
            // rejecting a change that simply hasn't happened yet.
            AppExecutors executors = AppExecutors.getInstance();
            executors.networkIO().execute(() -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                boolean running = isWebServerRunning();
                if (running != enabled) {
                    executors.mainThread().execute(() -> webServerEnabledPref.setChecked(running));
                }
            });
            return true;
        });
    }

    private void bindWebServerTest() {
        Preference webServerTest = require(R.string.pref_webserver_test_key);
        webServerTest.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(TEST_URL));
            ActivityStarter.startActivity(this.fragment.requireContext(), intent);
            return true;
        });
    }

    private void bindWebServerCertificate() {
        Preference webServerCertificate = require(R.string.pref_webserver_certificate_key);
        webServerCertificate.setOnPreferenceClickListener(preference -> {
            if (SDK_INT < VERSION_CODES.R) {
                installCertificate(this.fragment.requireContext());
            } else {
                this.prepareCertificateLauncher.launch("adaway-webserver-certificate.crt");
            }
            return true;
        });
    }

    private void prepareWebServerCertificate(Uri uri) {
        // Check user selected document
        if (uri == null) {
            return;
        }
        Timber.d("Certificate URI: %s", uri);
        copyCertificate(this.fragment.requireActivity(), uri);
        new MaterialAlertDialogBuilder(this.fragment.requireContext())
                .setCancelable(true)
                .setTitle(R.string.pref_webserver_certificate_dialog_title)
                .setMessage(R.string.pref_webserver_certificate_dialog_content)
                .setPositiveButton(
                        R.string.pref_webserver_certificate_dialog_action,
                        (dialog, which) -> {
                            dialog.dismiss();
                            Intent intent = new Intent(ACTION_SECURITY_SETTINGS);
                            ActivityStarter.startActivity(this.fragment.requireContext(), intent);
                        })
                .create()
                .show();
    }

    private void updateWebServerState() {
        Preference webServerTest = require(R.string.pref_webserver_test_key);
        webServerTest.setSummary(R.string.pref_webserver_state_checking);
        AppExecutors executors = AppExecutors.getInstance();
        executors.networkIO().execute(() -> {
                    // Wait for server to start
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    int summaryResId = getWebServerState();
                    executors.mainThread().execute(
                            () -> webServerTest.setSummary(summaryResId)
                    );
                }
        );
    }
}
