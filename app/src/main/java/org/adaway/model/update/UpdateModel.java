package org.adaway.model.update;

import static org.adaway.util.Constants.GITHUB_REPOSITORY;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.adaway.BuildConfig;
import org.adaway.R;
import org.adaway.helper.PreferenceHelper;
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
 * app's external cache directory. {@link org.adaway.ui.update.UpdateActivity} tracks the
 * download and hands the verified APK to the system installer.
 */
public class UpdateModel {
    /** GitHub REST API endpoint for the latest release of the fork. */
    private static final String LATEST_RELEASE_URL =
            "https://api.github.com/repos/" + GITHUB_REPOSITORY + "/releases/latest";
    /** File name of the downloaded APK in the app's external cache dir. */
    public static final String APK_FILE_NAME = "adaway-community-update.apk";

    private final Context context;
    private final OkHttpClient client;
    private final MutableLiveData<Manifest> manifest;

    public UpdateModel(Context context) {
        this.context = context;
        this.manifest = new MutableLiveData<>();
        this.client = new OkHttpClient.Builder().build();
        ApkUpdateService.syncPreferences(context);
        cleanUpPreviousDownload();
    }

    private void cleanUpPreviousDownload() {
        if (PreferenceHelper.isInstallPending(this.context)) {
            // App was killed while the user was in the "install unknown apps" settings
            // screen. Keep the APK so UpdateActivity can resume the install on resume.
            Timber.i("Skipping APK cleanup; install pending.");
            return;
        }
        ApkInstaller.clearDownloads(this.context);
    }

    public LiveData<Manifest> getManifest() {
        return this.manifest;
    }

    /**
     * Fetch the latest release and publish the resulting manifest. Safe to call from any
     * background thread.
     *
     * @return The fetched manifest, or {@code null} on network/parse failure.
     */
    public Manifest checkForUpdate() {
        Manifest fetched = downloadManifest();
        if (fetched != null) {
            this.manifest.postValue(fetched);
        }
        return fetched;
    }

    private Manifest downloadManifest() {
        Request request = new Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build();
        try (Response response = this.client.newCall(request).execute();
             ResponseBody body = response.body()) {
            if (response.isSuccessful() && body != null) {
                return new Manifest(body.string(), BuildConfig.VERSION_NAME);
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
        return download(current);
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
}
