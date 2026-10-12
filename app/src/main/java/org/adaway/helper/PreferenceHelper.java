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

import androidx.appcompat.app.AppCompatDelegate;

import org.adaway.R;
import org.adaway.model.adblocking.AdBlockMethod;
import org.adaway.util.Constants;
import org.adaway.vpn.VpnStatus;

import java.util.Collections;
import java.util.Set;

public final class PreferenceHelper {
    private PreferenceHelper() {

    }

    public static int getDarkThemeMode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
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
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(context.getString(R.string.pref_dark_theme_mode_key), mode)
                .apply();
    }

    public static boolean getUpdateCheck(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_update_check_key),
                context.getResources().getBoolean(R.bool.pref_update_check_def)
        );
    }

    public static boolean getEnableIpv6(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_enable_ipv6_key),
                context.getResources().getBoolean(R.bool.pref_enable_ipv6_def)
        );
    }

    public static boolean getUpdateCheckAppStartup(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_update_check_app_startup_key),
                context.getResources().getBoolean(R.bool.pref_update_check_app_startup_def)
        );
    }

    public static boolean getUpdateCheckAppDaily(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_update_check_app_daily_key),
                context.getResources().getBoolean(R.bool.pref_update_check_app_daily_def)
        );
    }

    public static boolean getUpdateCheckHostsDaily(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_update_check_hosts_daily_key),
                context.getResources().getBoolean(R.bool.pref_update_check_hosts_daily_def)
        );
    }

    public static boolean getAutomaticUpdateDaily(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_automatic_update_daily_key),
                context.getResources().getBoolean(R.bool.pref_automatic_update_daily_def)
        );
    }

    public static boolean getUpdateOnlyOnWifi(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_update_only_on_wifi_key),
                context.getResources().getBoolean(R.bool.pref_update_only_on_wifi_def)
        );
    }

    public static String getRedirectionIpv4(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getString(
                context.getString(R.string.pref_redirection_ipv4_key),
                context.getString(R.string.pref_redirection_ipv4_def)
        );
    }

    public static String getRedirectionIpv6(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getString(
                context.getString(R.string.pref_redirection_ipv6_key),
                context.getString(R.string.pref_redirection_ipv6_def)
        );
    }

    public static boolean getWebServerEnabled(Context context) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_webserver_enabled_key),
                context.getResources().getBoolean(R.bool.pref_webserver_enabled_def)
        );
    }

    public static boolean getWebServerIcon(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_webserver_icon_key),
                context.getResources().getBoolean(R.bool.pref_webserver_icon_def)
        );
    }

    public static AdBlockMethod getAdBlockMethod(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return AdBlockMethod.fromCode(prefs.getInt(
                context.getString(R.string.pref_ad_block_method_key),
                context.getResources().getInteger(R.integer.pref_ad_block_method_key_def)
        ));
    }

    public static void setAbBlockMethod(Context context, AdBlockMethod method) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(context.getString(R.string.pref_ad_block_method_key), method.toCode());
        editor.apply();
    }

    public static VpnStatus getVpnServiceStatus(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return VpnStatus.fromCode(prefs.getInt(
                context.getString(R.string.pref_vpn_service_status_key),
                context.getResources().getInteger(R.integer.pref_vpn_service_status_def)
        ));
    }

    public static void setVpnServiceStatus(Context context, VpnStatus status) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
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
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        String key = context.getString(R.string.pref_vpn_service_user_enabled_key);
        if (prefs.contains(key)) {
            return prefs.getBoolean(key, false);
        }
        // Migration fallback: derive from existing service status pref.
        return getVpnServiceStatus(context).isStarted();
    }

    public static void setVpnServiceUserEnabled(Context context, boolean userEnabled) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        prefs.edit()
                .putBoolean(context.getString(R.string.pref_vpn_service_user_enabled_key), userEnabled)
                .apply();
    }

    public static boolean getVpnServiceOnBoot(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_vpn_service_on_boot_key),
                context.getResources().getBoolean(R.bool.pref_vpn_service_on_boot_def)
        );
    }

    public static boolean getVpnWatchdogEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_vpn_watchdog_enabled_key),
                context.getResources().getBoolean(R.bool.pref_vpn_watchdog_enabled_def)
        );
    }

    /**
     * Whether the diagnostic log is allowed to record events. Off by default: the user must
     * explicitly opt in (Preferences > VPN > Record diagnostic log) before anything is captured.
     */
    public static boolean getVpnDiagnosticLogEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_vpn_diagnostic_log_enabled_key),
                context.getResources().getBoolean(R.bool.pref_vpn_diagnostic_log_enabled_def)
        );
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
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_tv_welcome_done_key),
                context.getResources().getBoolean(R.bool.pref_tv_welcome_done_def)
        );
    }

    public static void setTvWelcomeDone(Context context, boolean done) {
        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        prefs.edit()
                .putBoolean(context.getString(R.string.pref_tv_welcome_done_key), done)
                .apply();
    }

    public static boolean getDebugEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getBoolean(
                context.getString(R.string.pref_enable_debug_key),
                context.getResources().getBoolean(R.bool.pref_enable_debug_def)
        );
    }

    public static String getVpnExcludedSystemApps(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getString(
                context.getString(R.string.pref_vpn_excluded_system_apps_key),
                context.getString(R.string.pref_vpn_excluded_system_apps_default)
        );
    }

    public static Set<String> getVpnExcludedApps(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        return prefs.getStringSet(
                context.getString(R.string.pref_vpn_excluded_user_apps_key),
                Collections.emptySet()
        );
    }

    public static void setVpnExcludedApps(Context context, Set<String> excludedApplicationPackageNames) {
        SharedPreferences prefs = context.getSharedPreferences(
                Constants.PREFS_NAME,
                Context.MODE_PRIVATE
        );
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(context.getString(R.string.pref_vpn_excluded_user_apps_key), excludedApplicationPackageNames);
        editor.apply();
    }
}
