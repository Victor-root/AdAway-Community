package org.adaway.vpn

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.net.toUri
import org.adaway.data.settings.Settings
import org.adaway.data.settings.SystemAppExclusion

/** The apps kept out of the VPN, whose DNS queries are never filtered. */
object VpnExclusions {
    /** Any app able to open this link counts as a browser. */
    private const val BROWSER_TEST_URL = "https://isabrowser.adaway.org/"

    /** System apps carrying web traffic for other apps, kept in the VPN like browsers. */
    private val WEB_SYSTEM_APPS = setOf(
        "com.google.android.webview",
        "com.android.htmlviewer",
        "com.google.android.backuptransport",
        "com.google.android.gms",
        "com.google.android.gsf",
    )

    fun packages(context: Context, settings: Settings): Set<String> {
        val packageManager = context.packageManager
        val (system, user) = installedApplications(packageManager)
            .filter { it.packageName != context.packageName }
            .partition { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
        val systemPackages = system.map { it.packageName }
        val excludedSystem = when (settings.vpnSystemAppExclusion) {
            SystemAppExclusion.NONE -> emptyList()
            SystemAppExclusion.ALL -> systemPackages
            SystemAppExclusion.ALL_EXCEPT_BROWSERS -> {
                val browsers = browsers(packageManager) + WEB_SYSTEM_APPS
                systemPackages.filter { it !in browsers }
            }
        }
        val excludedUser = user.map { it.packageName }.filter { it in settings.vpnExcludedApps }
        return (excludedSystem + excludedUser).toSet()
    }

    fun installedApplications(packageManager: PackageManager): List<ApplicationInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledApplications(0)
        }

    private fun browsers(packageManager: PackageManager): Set<String> {
        val intent = Intent(Intent.ACTION_VIEW, BROWSER_TEST_URL.toUri())
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }
        return activities.map { it.activityInfo.packageName }.toSet()
    }
}
