package org.adaway.model.update;

import static android.content.pm.PackageManager.GET_SIGNATURES;
import static android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES;
import static android.os.Build.VERSION.SDK_INT;
import static android.os.Build.VERSION_CODES.P;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

import timber.log.Timber;

/**
 * Verifies a downloaded APK and hands it to the system installer.
 * <p>
 * DownloadManager can only write to a shared location, so the APK is staged in the app's
 * external cache directory. On Android 8 to 10 another application holding
 * {@code WRITE_EXTERNAL_STORAGE} can write there, so the staged file must be treated as
 * untrusted: it may have been replaced after the download finished, or planted before the
 * download ever ran. Nothing in the download itself proves where those bytes came from.
 * <p>
 * Every install therefore goes through {@link #verifyAndInstall}, which
 * <ol>
 *     <li>checks the archive declares this very package and is signed by the same key as the
 *     running app, which a foreign or tampered APK cannot be, and</li>
 *     <li>copies the verified bytes into the app's private cache and installs from there, so
 *     the file that was checked is the file that gets installed and nothing can swap it in
 *     between.</li>
 * </ol>
 *
 * @author AdAway Community
 */
public final class ApkInstaller {
    private static final String APK_MIME_TYPE = "application/vnd.android.package-archive";
    /** Name of the private copy actually handed to the installer. */
    private static final String VERIFIED_APK_FILE_NAME = "verified-update.apk";

    private ApkInstaller() {
    }

    /**
     * Verify the staged APK and launch the system installer for it.
     *
     * @param context   The context to install from.
     * @param stagedApk The downloaded APK, treated as untrusted.
     * @return <code>true</code> if the installer was launched, <code>false</code> if the APK
     * failed verification or could not be prepared. On failure the staged file is deleted, so
     * the caller can simply download again.
     */
    public static boolean verifyAndInstall(Context context, File stagedApk) {
        if (stagedApk == null || !stagedApk.isFile()) {
            Timber.w("No APK to install.");
            return false;
        }
        if (!isSignedLikeInstalledApp(context, stagedApk)) {
            // Either the archive is not this application, or it is signed by someone else.
            // A legitimate update can never fail this check, so the file is discarded.
            Timber.e("Refusing to install %s: it is not signed like the installed app.",
                    stagedApk.getName());
            delete(stagedApk);
            return false;
        }
        File verifiedApk = copyToPrivateCache(context, stagedApk);
        if (verifiedApk == null) {
            return false;
        }
        // The staged copy has served its purpose and is in a location other apps may read.
        delete(stagedApk);
        return install(context, verifiedApk);
    }

    /**
     * Check the archive declares this package and carries the same signing certificates as the
     * installed application.
     *
     * @param context The context to read the installed signature from.
     * @param apk     The APK to check.
     * @return <code>true</code> if the archive matches the running app, <code>false</code> for
     * any mismatch, and for any error, since an APK that cannot be verified must not be trusted.
     */
    // Two deprecations here, both deliberate: GET_SIGNATURES is the only way to read signing
    // information below Android 9, which is why it is picked by the version check below, and the
    // PackageInfoFlags overloads that replace the int-flag calls only exist from Android 13 on.
    @SuppressWarnings("deprecation")
    static boolean isSignedLikeInstalledApp(Context context, File apk) {
        try {
            PackageManager packageManager = context.getPackageManager();
            int flags = SDK_INT >= P ? GET_SIGNING_CERTIFICATES : GET_SIGNATURES;
            PackageInfo archiveInfo = packageManager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
            if (archiveInfo == null) {
                Timber.w("Could not parse %s as a package.", apk.getName());
                return false;
            }
            if (!context.getPackageName().equals(archiveInfo.packageName)) {
                Timber.w("APK declares package %s, expected %s.",
                        archiveInfo.packageName, context.getPackageName());
                return false;
            }
            PackageInfo installedInfo = packageManager.getPackageInfo(context.getPackageName(), flags);
            Set<String> archiveSignatures = signaturesOf(archiveInfo);
            Set<String> installedSignatures = signaturesOf(installedInfo);
            if (archiveSignatures.isEmpty() || installedSignatures.isEmpty()) {
                Timber.w("Missing signing information; refusing the APK.");
                return false;
            }
            return archiveSignatures.equals(installedSignatures);
        } catch (Exception e) {
            // Includes NameNotFoundException and anything thrown while parsing a malformed
            // archive. Treat every failure as "not verified".
            Timber.e(e, "Failed to verify the APK signature.");
            return false;
        }
    }

    // PackageInfo.signatures is deprecated in favour of signingInfo, which only exists from
    // Android 9 on: it stays the fallback for Android 8 and for a missing signingInfo.
    @SuppressWarnings("deprecation")
    private static Set<String> signaturesOf(PackageInfo info) {
        Signature[] signatures;
        if (SDK_INT >= P && info.signingInfo != null) {
            signatures = info.signingInfo.hasMultipleSigners()
                    ? info.signingInfo.getApkContentsSigners()
                    : info.signingInfo.getSigningCertificateHistory();
        } else {
            signatures = info.signatures;
        }
        Set<String> result = new HashSet<>();
        if (signatures != null) {
            for (Signature signature : signatures) {
                if (signature != null) {
                    result.add(signature.toCharsString());
                }
            }
        }
        return result;
    }

    /**
     * Copy the APK into the app's private cache, where no other application can reach it.
     *
     * @return The private copy, or <code>null</code> if the copy failed.
     */
    private static File copyToPrivateCache(Context context, File source) {
        File target = new File(context.getCacheDir(), VERIFIED_APK_FILE_NAME);
        try (InputStream input = new FileInputStream(source);
             OutputStream output = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } catch (IOException e) {
            Timber.e(e, "Failed to stage the APK for install.");
            delete(target);
            return null;
        }
        return target;
    }

    private static boolean install(Context context, File apk) {
        try {
            Uri apkUri = FileProvider.getUriForFile(
                    context, context.getPackageName() + ".fileprovider", apk);
            Intent install = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(apkUri, APK_MIME_TYPE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(install);
            return true;
        } catch (Exception e) {
            Timber.e(e, "Failed to launch the APK installer.");
            return false;
        }
    }

    /**
     * Delete both the staged and the verified copies. Called when the update flow is abandoned
     * or completed so an APK never lingers on disk.
     *
     * @param context The context to resolve the cache directories from.
     */
    public static void clearDownloads(Context context) {
        File external = context.getExternalCacheDir();
        if (external != null) {
            delete(new File(external, UpdateModel.APK_FILE_NAME));
        }
        delete(new File(context.getCacheDir(), VERIFIED_APK_FILE_NAME));
    }

    private static void delete(File file) {
        if (file != null && file.exists() && !file.delete()) {
            Timber.w("Failed to delete %s.", file.getAbsolutePath());
        }
    }

    /**
     * Get the staged (untrusted) APK location used by the download.
     *
     * @param context The context to resolve the external cache from.
     * @return The staged APK file, or <code>null</code> if external storage is unavailable.
     */
    public static File getStagedApk(Context context) {
        File external = context.getExternalCacheDir();
        return external == null ? null : new File(external, UpdateModel.APK_FILE_NAME);
    }
}
