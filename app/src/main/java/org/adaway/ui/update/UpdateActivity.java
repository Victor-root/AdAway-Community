package org.adaway.ui.update;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.lifecycle.ViewModelProvider;

import org.adaway.R;
import org.adaway.databinding.UpdateActityBinding;
import org.adaway.helper.ThemeHelper;
import org.adaway.model.update.Manifest;
import org.adaway.model.update.UpdateModel;

import java.io.File;

import timber.log.Timber;

/**
 * This class is the application main activity.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class UpdateActivity extends AppCompatActivity {
    private UpdateActityBinding binding;
    private UpdateViewModel updateViewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ThemeHelper.applyTheme(this);
        this.binding = UpdateActityBinding.inflate(getLayoutInflater());
        setContentView(this.binding.getRoot());

        this.updateViewModel = new ViewModelProvider(this).get(UpdateViewModel.class);
        bindListeners();
        bindManifest();
        bindProgress();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Resume an install that was interrupted by the system "install unknown
        // apps" permission screen. If permission was not granted, restore the UI
        // so the user can retry from the Update button instead of being stuck on
        // a frozen progress bar.
        if (UpdateModel.isInstallPending(this) && !getPackageManager().canRequestPackageInstalls()) {
            this.binding.downloadProgressBar.setVisibility(GONE);
            this.binding.progressTextView.setText("");
            // updateButton visibility is restored by the manifest LiveData
            // observer re-emitting on resume.
        }
        tryResumePendingInstall(this);
    }

    /**
     * Resume an install that was queued before the system "install unknown apps"
     * permission screen was opened. Safe to call from any Activity's onResume:
     * does nothing if no install is pending, the permission is still denied, or
     * the APK file is missing. Clears the pending flag whenever it acts.
     * <p>
     * Two paths can land here:
     * <ol>
     *     <li>Process survived: the original {@link UpdateActivity} (or this
     *         caller) sees the flag still set when {@code onResume} runs after
     *         returning from the system Settings.</li>
     *     <li>Process was killed while in Settings (common on memory-tight
     *         devices like Shield TV): only the launcher Activity is restored,
     *         not {@code UpdateActivity}, so the launcher must invoke this on
     *         {@code onResume} to complete the install.</li>
     * </ol>
     *
     * @return {@code true} when the install Intent was started.
     */
    public static boolean tryResumePendingInstall(Activity activity) {
        if (!UpdateModel.isInstallPending(activity)) {
            return false;
        }
        UpdateModel.setInstallPending(activity, false);
        if (!activity.getPackageManager().canRequestPackageInstalls()) {
            return false;
        }
        File apkFile = new File(activity.getExternalCacheDir(), UpdateModel.APK_FILE_NAME);
        if (!apkFile.exists()) {
            Timber.w("APK file missing on install resume.");
            return false;
        }
        Uri apkUri = FileProvider.getUriForFile(
                activity,
                activity.getPackageName() + ".fileprovider",
                apkFile);
        Intent install = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(apkUri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivity(install);
        return true;
    }

    private void bindListeners() {
        this.binding.updateButton.setOnClickListener(this::startUpdate);
    }

    private void bindManifest() {
        this.updateViewModel.getAppManifest().observe(this, manifest -> {
            if (manifest.updateAvailable) {
                showUpdate(manifest);
            } else {
                markUpToDate(manifest);
            }
        });
    }

    private void bindProgress() {
        this.updateViewModel.getDownloadProgress().observe(this, progress -> {
            if (progress == null) {
                return;
            }
            this.binding.updateButton.setVisibility(GONE);
            this.binding.downloadProgressBar.setVisibility(VISIBLE);
            this.binding.downloadProgressBar.setProgress(progress.getProgress(), true);
            this.binding.progressTextView.setText(progress.format(this));
            if (progress instanceof CompleteDownloadStatus) {
                launchInstaller();
            }
        });
    }

    private void launchInstaller() {
        File apkFile = new File(getExternalCacheDir(), UpdateModel.APK_FILE_NAME);
        if (!apkFile.exists()) {
            Timber.w("APK file not found after download.");
            return;
        }
        // Android 8+ requires the per-app "install unknown apps" permission to be
        // granted before ACTION_VIEW on an APK opens the system installer. Without
        // this check, the first attempt only prompts the user to grant the
        // permission; the install Intent itself is never re-fired, so the user
        // grants the permission and then nothing happens. Route through the
        // permission screen via startActivity (not ActivityResultLauncher: the
        // result callback is lost if the process is killed while Settings is in
        // the foreground, which happens on memory-tight TVs). The pendingInstall
        // flag bridges across process death so onResume can complete the install,
        // either from this Activity or from the launcher Activity if Android
        // didn't restore us.
        if (!getPackageManager().canRequestPackageInstalls()) {
            UpdateModel.setInstallPending(this, true);
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                    .setData(Uri.parse("package:" + getPackageName()));
            startActivity(settings);
            return;
        }
        Uri apkUri = FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                apkFile);
        Intent install = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(apkUri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(install);
    }

    private void markUpToDate(Manifest manifest) {
        this.binding.headerTextView.setText(R.string.update_up_to_date_header);
        this.binding.updateButton.setVisibility(GONE);
        this.binding.changelogTextView.setText(manifest.changelog);
    }

    private void showUpdate(Manifest manifest) {
        this.binding.headerTextView.setText(R.string.update_update_available_header);
        this.binding.updateButton.setVisibility(VISIBLE);
        this.binding.changelogTextView.setText(manifest.changelog);
    }

    private void startUpdate(View view) {
        this.binding.updateButton.setVisibility(GONE);
        // If a previous attempt left the APK on disk (e.g. the user denied the
        // "install unknown apps" permission and is now retrying), skip the
        // download and jump straight to the install flow.
        File apkFile = new File(getExternalCacheDir(), UpdateModel.APK_FILE_NAME);
        if (apkFile.exists()) {
            launchInstaller();
            return;
        }
        this.binding.downloadProgressBar.setVisibility(VISIBLE);
        this.updateViewModel.update();
    }
}
