package org.adaway.model.update;

import static android.app.DownloadManager.ACTION_DOWNLOAD_COMPLETE;

import android.app.DownloadManager;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.adaway.R;
import org.adaway.util.Constants;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import timber.log.Timber;

/**
 * Model in charge of checking for application updates and triggering the install flow.
 * <p>
 * Polls the AdAway-Community GitHub repository for the latest release, exposes the
 * resulting {@link Manifest} as LiveData, downloads the matching APK asset into the
 * app's external cache directory, and hands off to the system installer via
 * {@link ApkDownloadReceiver}.
 */
public class UpdateModel {
    /** GitHub REST API endpoint for the latest release of the fork. */
    private static final String LATEST_RELEASE_URL =
            "https://api.github.com/repos/Victor-root/AdAway-Community/releases/latest";
    /** File name of the downloaded APK in the app's external cache dir. */
    public static final String APK_FILE_NAME = "adaway-community-update.apk";
    /**
     * SharedPreferences key set by {@link org.adaway.ui.update.UpdateActivity} while the
     * user is in the system "install unknown apps" permission screen. The flag prevents
     * {@link #cleanUpPreviousDownload()} from deleting the downloaded APK if the app
     * process is killed for memory while that screen is in the foreground, so the
     * install flow can resume on next launch instead of forcing a fresh download.
     */
    private static final String PREF_INSTALL_PENDING = "updateInstallPending";

    private final Context context;
    private final VersionInfo versionInfo;
    private final OkHttpClient client;
    private final MutableLiveData<Manifest> manifest;
    private ApkDownloadReceiver receiver;

    public UpdateModel(Context context) {
        this.context = context;
        this.versionInfo = VersionInfo.get(context);
        this.manifest = new MutableLiveData<>();
        this.client = new OkHttpClient.Builder().build();
        ApkUpdateService.syncPreferences(context);
        cleanUpPreviousDownload();
    }

    private void cleanUpPreviousDownload() {
        if (isInstallPending(this.context)) {
            // App was killed while the user was in the "install unknown apps" settings
            // screen. Keep the APK so UpdateActivity can resume the install on resume.
            Timber.i("Skipping APK cleanup; install pending.");
            return;
        }
        File apkFile = new File(this.context.getExternalCacheDir(), APK_FILE_NAME);
        if (apkFile.exists() && apkFile.delete()) {
            Timber.i("Cleaned up previous APK download.");
        }
    }

    /**
     * Returns whether an install was started but the system installer has not yet
     * been launched. Set by {@link org.adaway.ui.update.UpdateActivity} while it
     * routes the user through the "install unknown apps" permission screen.
     */
    public static boolean isInstallPending(Context context) {
        return context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(PREF_INSTALL_PENDING, false);
    }

    /**
     * @see #isInstallPending(Context)
     */
    public static void setInstallPending(Context context, boolean pending) {
        SharedPreferences.Editor editor = context
                .getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                .edit();
        if (pending) {
            editor.putBoolean(PREF_INSTALL_PENDING, true);
        } else {
            editor.remove(PREF_INSTALL_PENDING);
        }
        editor.apply();
    }

    public int getVersionCode() {
        return this.versionInfo.code;
    }

    public String getVersionName() {
        return this.versionInfo.name;
    }

    public LiveData<Manifest> getManifest() {
        return this.manifest;
    }

    /**
     * Fetch the latest release and publish the resulting manifest. Safe to call from
     * any background thread.
     */
    public void checkForUpdate() {
        Manifest fetched = downloadManifest();
        if (fetched != null) {
            this.manifest.postValue(fetched);
        }
    }

    /**
     * Fetch the latest release, publish the manifest, and return it directly.
     * Safe to call from any background thread.
     *
     * @return The fetched manifest, or {@code null} on network/parse failure.
     */
    public Manifest checkForUpdateNow() {
        Manifest fetched = downloadManifest();
        if (fetched != null) {
            this.manifest.postValue(fetched);
        }
        return fetched;
    }

    private Manifest downloadManifest() {
        if (!this.versionInfo.isValid()) {
            return null;
        }
        Request request = new Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build();
        try (Response response = this.client.newCall(request).execute();
             ResponseBody body = response.body()) {
            if (response.isSuccessful() && body != null) {
                return new Manifest(body.string(), this.versionInfo.name);
            }
            Timber.w("GitHub release endpoint returned HTTP %d.", response.code());
            return null;
        } catch (IOException | JSONException exception) {
            Timber.e(exception, "Unable to fetch latest release.");
            return null;
        }
    }

    /**
     * Start downloading the APK of the latest known manifest.
     *
     * @return The DownloadManager id, or {@code -1} if no download was queued.
     */
    public long update() {
        Manifest current = this.manifest.getValue();
        if (current == null || current.apkUrl == null) {
            return -1;
        }
        if (this.receiver != null) {
            try {
                this.context.unregisterReceiver(this.receiver);
            } catch (IllegalArgumentException ignored) {
                // Receiver was already unregistered.
            }
        }
        long downloadId = download(current);
        this.receiver = new ApkDownloadReceiver(downloadId);
        ContextCompat.registerReceiver(
                this.context,
                this.receiver,
                new IntentFilter(ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        return downloadId;
    }

    private long download(Manifest current) {
        Timber.i("Downloading %s from %s.", current.version, current.apkUrl);
        File apkFile = new File(this.context.getExternalCacheDir(), APK_FILE_NAME);
        if (apkFile.exists() && !apkFile.delete()) {
            Timber.w("Failed to delete previous APK at %s.", apkFile.getAbsolutePath());
        }
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(current.apkUrl))
                .setTitle(this.context.getString(R.string.app_name) + " " + current.version)
                .setDescription(this.context.getString(R.string.update_notification_description))
                .setDestinationUri(Uri.fromFile(apkFile))
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN);
        DownloadManager downloadManager = this.context.getSystemService(DownloadManager.class);
        return downloadManager.enqueue(request);
    }

    private static class VersionInfo {
        private final int code;
        private final String name;

        private VersionInfo(int code, String name) {
            this.code = code;
            this.name = name;
        }

        static VersionInfo get(Context context) {
            try {
                PackageInfo packageInfo = context.getPackageManager()
                        .getPackageInfo(context.getPackageName(), 0);
                return new VersionInfo(packageInfo.versionCode, packageInfo.versionName);
            } catch (PackageManager.NameNotFoundException e) {
                return new VersionInfo(0, "development");
            }
        }

        boolean isValid() {
            return this.code > 0;
        }
    }
}
