/*
 * Copyright (C) 2011-2012 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This file is part of AdAway.
 *
 * AdAway is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AdAway is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AdAway.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.adaway.util;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.net.Uri;
import android.security.KeyChain;
import android.view.ContextThemeWrapper;

import androidx.annotation.StringRes;

import org.adaway.R;
import org.adaway.helper.PreferenceHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javax.net.ssl.SSLHandshakeException;
import javax.security.cert.CertificateException;
import javax.security.cert.X509Certificate;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import timber.log.Timber;

import static org.adaway.model.root.ShellUtils.isBundledExecutableRunning;
import static org.adaway.model.root.ShellUtils.killBundledExecutable;
import static org.adaway.model.root.ShellUtils.quote;
import static org.adaway.model.root.ShellUtils.runBundledExecutable;

/**
 * This class is an utility class to control web server execution.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public final class WebServerUtils {
    public static final String TEST_URL = "https://localhost/internal-test";
    private static final String WEB_SERVER_EXECUTABLE = "webserver";
    private static final String LOCALHOST_CERTIFICATE = "localhost-2410.crt";
    private static final String LOCALHOST_CERTIFICATE_KEY = "localhost-2410.key";

    private WebServerUtils() {

    }

    /**
     * Start the web server in new thread with RootTools
     *
     * @param context The application context.
     */
    public static void startWebServer(Context context) {
        Timber.d("Starting web server…");
        // The web server runs as a detached root process, so it survives an app update or
        // reinstall: a stale instance from a previous install can still be holding the ports,
        // which makes the freshly started one fail to bind them. Clear it first so the new
        // process is the one actually listening.
        killBundledExecutable(WEB_SERVER_EXECUTABLE);

        Path resourcePath = context.getFilesDir().toPath().resolve(WEB_SERVER_EXECUTABLE);
        inflateResources(context, resourcePath);

        String parameters = "--resources " + quote(resourcePath.toAbsolutePath().toString()) +
                (PreferenceHelper.getWebServerIcon(context) ? " --icon" : "") +
                " > /dev/null 2>&1";
        runBundledExecutable(context, WEB_SERVER_EXECUTABLE, parameters);
    }

    /**
     * Stop the web server.
     */
    public static void stopWebServer() {
        killBundledExecutable(WEB_SERVER_EXECUTABLE);
    }

    /**
     * Checks if web server is running
     *
     * @return <code>true</code> if webs server is running, <code>false</code> otherwise.
     */
    public static boolean isWebServerRunning() {
        return isBundledExecutableRunning(WEB_SERVER_EXECUTABLE);
    }

    /**
     * Get the web server state description.
     *
     * @return The web server state description.
     */
    @StringRes
    public static int getWebServerState() {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(TEST_URL)
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.isSuccessful() ?
                    R.string.pref_webserver_state_running_and_installed :
                    R.string.pref_webserver_state_not_running;
        } catch (SSLHandshakeException e) {
            Timber.d(e, "Web server test failed with a TLS handshake error.");
            return R.string.pref_webserver_state_running_not_installed;
        } catch (ConnectException e) {
            Timber.d(e, "Web server test failed to connect.");
            return R.string.pref_webserver_state_not_running;
        } catch (IOException e) {
            Timber.w(e, "Failed to test web server.");
            return R.string.pref_webserver_state_not_running;
        }
    }

    /**
     * Prompt user to install web server certificate.
     *
     * @param context The application context.
     */
    public static void installCertificate(Context context) {
        AssetManager assetManager = context.getAssets();
        try (InputStream inputStream = assetManager.open(LOCALHOST_CERTIFICATE);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            byte[] bytes = outputStream.toByteArray();
            X509Certificate x509 = X509Certificate.getInstance(bytes);
            Intent intent = KeyChain.createInstallIntent();
            intent.putExtra(KeyChain.EXTRA_CERTIFICATE, x509.getEncoded());
            intent.putExtra(KeyChain.EXTRA_NAME, "AdAway");
            context.startActivity(intent);
        } catch (IOException e) {
            Timber.w(e, "Failed to read certificate.");
        } catch (CertificateException e) {
            Timber.w(e, "Failed to parse certificate.");
        }
    }

    public static void copyCertificate(ContextThemeWrapper wrapper, Uri uri) {
        ContentResolver contentResolver = wrapper.getContentResolver();
        AssetManager assetManager = wrapper.getAssets();
        try (InputStream inputStream = assetManager.open(LOCALHOST_CERTIFICATE);
             OutputStream outputStream = contentResolver.openOutputStream(uri)) {
            if (outputStream == null) {
                throw new IOException("Failed to open "+uri);
            }
            byte[] buffer = new byte[1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
        } catch (IOException e) {
            Timber.w(e, "Failed to copy certificate.");
        }
    }

    private static void inflateResources(Context context, Path target) {
        AssetManager assetManager = context.getAssets();
        try {
            inflateResource(assetManager, LOCALHOST_CERTIFICATE, target);
            inflateResource(assetManager, LOCALHOST_CERTIFICATE_KEY, target);
            inflateResource(assetManager, "icon.svg", target);
            inflateResource(assetManager, "test.html", target);
        } catch (IOException e) {
            Timber.w(e, "Failed to inflate web server resources.");
        }
    }

    private static void inflateResource(AssetManager assetManager, String resource, Path target) throws IOException {
        if (!Files.isDirectory(target)) {
            Files.createDirectories(target);
        }
        Path targetFile = target.resolve(resource);
        // Always mirror the current app assets: these files are never user data, only ever
        // ship in the APK, and skipping the copy when a stale one is already on disk (e.g. from
        // an older app version) would keep serving that stale copy forever, certificate
        // included.
        try (InputStream inputStream = assetManager.open(resource)) {
            Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
