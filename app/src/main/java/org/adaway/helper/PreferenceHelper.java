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

package org.adaway.helper;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.BoolRes;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatDelegate;

import org.adaway.R;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.util.Constants;
import org.adaway.vpn.VpnStatus;

import java.util.Collections;
import java.util.Set;

public final class PreferenceHelper {
    // Internal state, not user settings, so not declared in the preferences resources
    private static final String INSTALL_PENDING_KEY = "updateInstallPending";
    private static final String INSTALL_TOAST_TIMESTAMP_KEY = "updateInstallToastTimestamp";

    private PreferenceHelper() {

    }

    public static int getDarkThemeMode(Context context) {
        SharedPreferences prefs = prefs(context);
        String pref = prefs.getString(
                context.getString(R.string.pref_dark_theme_mode_key),
                context.getResources().getString(R.string.pref_dark_theme_mode_def)
        );
        switch (pref) {
            case "MODE_NIGHT_NO":
                return AppCompatDelegate.MODE_NIGHT_NO;
            case "MODE_NIGHT_YES":
                return AppCompatDelegate.MODE_NIGHT_YES;
            default:
                return AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
    }

    public static void setDarkThemeMode(Context context, String mode) {
        prefs(context)
                .edit()
                .putString(context.getString(R.string.pref_dark_theme_mode_key), mode)
                .apply();
    }

    public static boolean getUpdateCheck(Context context) {
        return getBoolean(context, R.string.pref_update_check_key, R.bool.pref_update_check_def);
    }

    public static boolean getEnableIpv6(Context context) {
        return getBoolean(context, R.string.pref_enable_ipv6_key, R.bool.pref_enable_ipv6_def);
    }

    public static boolean getUpdateCheckAppStartup(Context context) {
        return getBoolean(context, R.string.pref_update_check_app_startup_key, R.bool.pref_update_check_app_startup_def);
    }

    public static boolean getUpdateCheckAppDaily(Context context) {
        return getBoolean(context, R.string.pref_update_check_app_daily_key, R.bool.pref_update_check_app_daily_def);
    }

    public static boolean getUpdateCheckHostsDaily(Context context) {
        return getBoolean(context, R.string.pref_update_check_hosts_daily_key, R.bool.pref_update_check_hosts_daily_def);
    }

    public static boolean getAutomaticUpdateDaily(Context context) {
        return getBoolean(context, R.string.pref_automatic_update_daily_key, R.bool.pref_automatic_update_daily_def);
    }

    public static boolean getUpdateOnlyOnWifi(Context context) {
        return getBoolean(context, R.string.pref_update_only_on_wifi_key, R.bool.pref_update_only_on_wifi_def);
    }

    public static String getRedirectionIpv4(Context context) {
        return getString(context, R.string.pref_redirection_ipv4_key, R.string.pref_redirection_ipv4_def);
    }

    public static String getRedirectionIpv6(Context context) {
        return getString(context, R.string.pref_redirection_ipv6_key, R.string.pref_redirection_ipv6_def);
    }

    public static boolean getWebServerEnabled(Context context) {
        return getBoolean(context, R.string.pref_webserver_enabled_key, R.bool.pref_webserver_enabled_def);
    }

    public static boolean getWebServerIcon(Context context) {
        return getBoolean(context, R.string.pref_webserver_icon_key, R.bool.pref_webserver_icon_def);
    }

    public static AdBlockMethod getAdBlockMethod(Context context) {
        SharedPreferences prefs = prefs(context);
        return AdBlockMethod.fromCode(prefs.getInt(
                context.getString(R.string.pref_ad_block_method_key),
                context.getResources().getInteger(R.integer.pref_ad_block_method_key_def)
        ));
    }

    public static void setAbBlockMethod(Context context, AdBlockMethod method) {
        SharedPreferences prefs = prefs(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(context.getString(R.string.pref_ad_block_method_key), method.toCode());
        editor.apply();
    }

    public static VpnStatus getVpnServiceStatus(Context context) {
        SharedPreferences prefs = prefs(context);
        return VpnStatus.fromCode(prefs.getInt(
                context.getString(R.string.pref_vpn_service_status_key),
                context.getResources().getInteger(R.integer.pref_vpn_service_status_def)
        ));
    }

    public static void setVpnServiceStatus(Context context, VpnStatus status) {
        SharedPreferences prefs = prefs(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(context.getString(R.string.pref_vpn_service_status_key), status.toCode());
        editor.apply();
    }

    /**
     * Get whether the user has explicitly enabled the VPN (user intent).
     * <p>
     * This is distinct from the runtime service status: it tracks what the user has asked
     * for, not whether the Android service is currently running. Background paths (host
     * updates, connection monitor, sticky resurrection) must use this signal, never the
     * runtime status, to decide whether to (re)start the VPN.
     * <p>
     * When unset (existing installs / first read), this falls back to the persisted runtime
     * status to avoid breaking users who already had the VPN running.
     */
    public static boolean getVpnServiceUserEnabled(Context context) {
        SharedPreferences prefs = prefs(context);
        String key = context.getString(R.string.pref_vpn_service_user_enabled_key);
        if (prefs.contains(key)) {
            return prefs.getBoolean(key, false);
        }
        // Migration fallback: derive from existing service status pref.
        return getVpnServiceStatus(context).isStarted();
    }

    public static void setVpnServiceUserEnabled(Context context, boolean userEnabled) {
        SharedPreferences prefs = prefs(context);
        prefs.edit()
                .putBoolean(context.getString(R.string.pref_vpn_service_user_enabled_key), userEnabled)
                .apply();
    }

    public static boolean getVpnServiceOnBoot(Context context) {
        return getBoolean(context, R.string.pref_vpn_service_on_boot_key, R.bool.pref_vpn_service_on_boot_def);
    }

    public static boolean getVpnWatchdogEnabled(Context context) {
        return getBoolean(context, R.string.pref_vpn_watchdog_enabled_key, R.bool.pref_vpn_watchdog_enabled_def);
    }

    /**
     * Whether the diagnostic log is allowed to record events. Off by default: the user must
     * explicitly opt in (Preferences > VPN > Record diagnostic log) before anything is captured.
     */
    public static boolean getVpnDiagnosticLogEnabled(Context context) {
        return getBoolean(context, R.string.pref_vpn_diagnostic_log_enabled_key, R.bool.pref_vpn_diagnostic_log_enabled_def);
    }

    /**
     * Whether the TV first-run wizard ({@code TvWelcomeActivity}) has already been handled for
     * this install, either by actually completing it or by having been detected as an existing
     * install that already had ad-blocking working before the wizard existed.
     * <p>
     * Deliberately separate from {@link #getAdBlockMethod}: {@code TvWelcomeActivity} has to
     * persist {@code AdBlockMethod = VPN} the moment it opens, before the user has done
     * anything, so {@code HomeViewModel} resolves a real VPN model instead of
     * {@code UndefinedBlockModel}. That means {@code AdBlockMethod} alone can't tell
     * {@code TvHomeActivity} whether the wizard actually finished - only this flag can.
     */
    public static boolean isTvWelcomeDone(Context context) {
        return getBoolean(context, R.string.pref_tv_welcome_done_key, R.bool.pref_tv_welcome_done_def);
    }

    public static void setTvWelcomeDone(Context context, boolean done) {
        SharedPreferences prefs = prefs(context);
        prefs.edit()
                .putBoolean(context.getString(R.string.pref_tv_welcome_done_key), done)
                .apply();
    }

    public static boolean getDebugEnabled(Context context) {
        return getBoolean(context, R.string.pref_enable_debug_key, R.bool.pref_enable_debug_def);
    }

    public static String getVpnExcludedSystemApps(Context context) {
        return getString(context, R.string.pref_vpn_excluded_system_apps_key, R.string.pref_vpn_excluded_system_apps_default);
    }

    public static Set<String> getVpnExcludedApps(Context context) {
        SharedPreferences prefs = prefs(context);
        return prefs.getStringSet(
                context.getString(R.string.pref_vpn_excluded_user_apps_key),
                Collections.emptySet()
        );
    }

    public static void setVpnExcludedApps(Context context, Set<String> excludedApplicationPackageNames) {
        SharedPreferences prefs = prefs(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(context.getString(R.string.pref_vpn_excluded_user_apps_key), excludedApplicationPackageNames);
        editor.apply();
    }

    /**
     * Get whether an app update install was started but the system installer was not launched
     * yet, while the user goes through the "install unknown apps" permission screen.
     */
    public static boolean isInstallPending(Context context) {
        return prefs(context).getBoolean(INSTALL_PENDING_KEY, false);
    }

    public static void setInstallPending(Context context, boolean pending) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (pending) {
            editor.putBoolean(INSTALL_PENDING_KEY, true);
        } else {
            editor.remove(INSTALL_PENDING_KEY);
        }
        editor.apply();
    }

    /**
     * Get the time the post-install toast was scheduled at, {@code 0} if none is pending.
     */
    public static long getInstallToastTimestamp(Context context) {
        return prefs(context).getLong(INSTALL_TOAST_TIMESTAMP_KEY, 0L);
    }

    public static void setInstallToastTimestamp(Context context, long timestamp) {
        prefs(context).edit().putLong(INSTALL_TOAST_TIMESTAMP_KEY, timestamp).apply();
    }

    public static void clearInstallToastTimestamp(Context context) {
        prefs(context).edit().remove(INSTALL_TOAST_TIMESTAMP_KEY).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static boolean getBoolean(Context context, @StringRes int key, @BoolRes int defaultValue) {
        return prefs(context).getBoolean(
                context.getString(key),
                context.getResources().getBoolean(defaultValue)
        );
    }

    private static String getString(Context context, @StringRes int key, @StringRes int defaultValue) {
        return prefs(context).getString(context.getString(key), context.getString(defaultValue));
    }
}
