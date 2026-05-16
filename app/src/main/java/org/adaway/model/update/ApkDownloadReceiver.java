package org.adaway.model.update;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;

import timber.log.Timber;

/**
 * Receives the {@link DownloadManager#ACTION_DOWNLOAD_COMPLETE} broadcast for the APK
 * download queued by {@link UpdateModel} and launches the system package installer
 * via a {@link FileProvider} URI.
 */
public class ApkDownloadReceiver extends BroadcastReceiver {
    private static final String APK_MIME_TYPE = "application/vnd.android.package-archive";

    private final long downloadId;

    public ApkDownloadReceiver(long downloadId) {
        this.downloadId = downloadId;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
        if (this.downloadId != id) {
            return;
        }
        File apkFile = new File(context.getExternalCacheDir(), UpdateModel.APK_FILE_NAME);
        if (!apkFile.exists()) {
            Timber.w("APK file is missing after download (id=%d).", id);
            return;
        }
        Uri apkUri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                apkFile);
        installApk(context, apkUri);
    }

    private void installApk(Context context, Uri apkUri) {
        Intent install = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(apkUri, APK_MIME_TYPE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(install);
        } catch (Exception e) {
            Timber.e(e, "Failed to launch APK installer.");
        }
    }
}
