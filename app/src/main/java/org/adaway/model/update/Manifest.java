package org.adaway.model.update;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Locale;

/**
 * Parsed representation of a GitHub release used to update the application.
 * <p>
 * Built from the JSON payload of {@code /repos/{owner}/{repo}/releases/latest}: pulls
 * the tag name, release notes and the {@code .apk} asset download URL, and decides
 * whether the release is newer than the currently installed version.
 */
public class Manifest {
    public final String version;
    public final String changelog;
    @Nullable
    public final String apkUrl;
    public final boolean updateAvailable;

    public Manifest(String releaseJson, String currentVersionName) throws JSONException {
        JSONObject release = new JSONObject(releaseJson);
        this.version = release.optString("tag_name", "");
        this.changelog = release.optString("body", "");
        this.apkUrl = findApkAssetUrl(release.optJSONArray("assets"));
        this.updateAvailable = !this.version.isEmpty()
                && this.apkUrl != null
                && VersionCompare.compare(this.version, currentVersionName) > 0;
    }

    @Nullable
    private static String findApkAssetUrl(@Nullable JSONArray assets) {
        if (assets == null) {
            return null;
        }
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) {
                continue;
            }
            String name = asset.optString("name", "");
            if (!name.toLowerCase(Locale.ROOT).endsWith(".apk")) {
                continue;
            }
            String url = asset.optString("browser_download_url", "");
            if (!url.isEmpty()) {
                return url;
            }
        }
        return null;
    }
}
