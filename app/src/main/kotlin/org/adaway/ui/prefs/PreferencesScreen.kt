package org.adaway.ui.prefs

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.adaway.R
import org.adaway.data.settings.BlockingMethod
import org.adaway.data.settings.SystemAppExclusion
import org.adaway.data.settings.ThemeMode
import org.adaway.ui.common.AdAwayScreen
import org.adaway.ui.common.appViewModel

class PreferencesNavigation(val back: () -> Unit, val openUpdates: () -> Unit, val openExcludedApps: () -> Unit)

/** The order of the dark theme choices. */
private val ThemeModes = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)

/** The order of the system app exclusion choices. */
private val SystemAppExclusions = listOf(SystemAppExclusion.NONE, SystemAppExclusion.ALL_EXCEPT_BROWSERS, SystemAppExclusion.ALL)

private enum class PreferenceChoice { THEME, SYSTEM_APPS }

/**
 * The preferences, all on one screen as in 6.x. Only the section of the blocking method in use is
 * enabled. The settings of features not written yet are grayed out.
 */
@Composable
fun PreferencesScreen(navigation: PreferencesNavigation) {
    val viewModel = appViewModel(::PreferencesViewModel)
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val current = settings ?: return
    val context = LocalContext.current
    var choice by rememberSaveable { mutableStateOf<PreferenceChoice?>(null) }
    val usesVpn = current.blockingMethod == BlockingMethod.VPN
    val resetDone = stringResource(R.string.pref_vpn_reset_done)
    val resetNotRunning = stringResource(R.string.pref_vpn_reset_not_running)
    val themeChoices = stringArrayResource(R.array.pref_dark_theme_modes).toList()
    val systemAppChoices = stringArrayResource(R.array.pref_vpn_excluded_system_apps_entries).toList()
    AdAwayScreen(title = stringResource(R.string.pref_main_title), onBack = navigation.back) { padding ->
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(padding)) {
            PreferenceCategory(stringResource(R.string.pref_general_category), divider = false)
            Preference(R.drawable.ic_brightness_medium_24dp, stringResource(R.string.pref_dark_theme), onClick = { choice = PreferenceChoice.THEME })
            Preference(R.drawable.ic_sync_24dp, stringResource(R.string.pref_update_configuration), onClick = navigation.openUpdates)

            PreferenceCategory(stringResource(R.string.pref_vpn_ad_blocker_configuration), enabled = usesVpn)
            SwitchPreference(
                R.drawable.ic_power_settings_new_24dp,
                stringResource(R.string.pref_vpn_service_on_boot),
                checked = current.vpnStartOnBoot,
                onCheckedChange = { checked -> viewModel.update { it.copy(vpnStartOnBoot = checked) } },
                enabled = usesVpn,
            )
            SwitchPreference(
                R.drawable.ic_network_check_24dp,
                stringResource(R.string.pref_vpn_service_monitor),
                checked = current.vpnConnectionMonitor,
                onCheckedChange = { checked -> viewModel.update { it.copy(vpnConnectionMonitor = checked) } },
                summary = stringResource(R.string.pref_vpn_service_monitor_description),
                enabled = usesVpn,
            )
            Preference(null, null, summary = stringResource(R.string.pref_vpn_excluded_apps_description), enabled = usesVpn)
            Preference(
                R.drawable.ic_android_24dp,
                stringResource(R.string.pref_vpn_exclude_system_apps),
                summary = systemAppChoices[SystemAppExclusions.indexOf(current.vpnSystemAppExclusion)],
                enabled = usesVpn,
                onClick = { choice = PreferenceChoice.SYSTEM_APPS },
            )
            Preference(R.drawable.ic_apps_24dp, stringResource(R.string.pref_vpn_exclude_user_apps), enabled = usesVpn, onClick = navigation.openExcludedApps)
            Preference(
                R.drawable.ic_restart_alt_24dp,
                stringResource(R.string.pref_vpn_reset),
                summary = stringResource(R.string.pref_vpn_reset_description),
                enabled = usesVpn,
                onClick = { Toast.makeText(context, if (viewModel.resetVpn()) resetDone else resetNotRunning, Toast.LENGTH_SHORT).show() },
            )
            SwitchPreference(
                R.drawable.ic_record_24dp,
                stringResource(R.string.pref_vpn_diagnostic_log_enabled),
                checked = false,
                onCheckedChange = {},
                summary = stringResource(R.string.pref_vpn_diagnostic_log_enabled_description),
                enabled = false,
            )
            Preference(R.drawable.ic_article_24dp, stringResource(R.string.pref_vpn_diagnostic_log), stringResource(R.string.pref_vpn_diagnostic_log_description), enabled = false)
            Preference(R.drawable.ic_delete_sweep_24dp, stringResource(R.string.pref_vpn_diagnostic_log_clear), stringResource(R.string.pref_vpn_diagnostic_log_clear_description), enabled = false)

            // Root blocking does not exist yet.
            PreferenceCategory(stringResource(R.string.pref_root_ad_blocker_configuration), enabled = false)
            Preference(R.drawable.ic_edit_note_24dp, stringResource(R.string.pref_root_open_hosts), enabled = false)
            SwitchPreference(R.drawable.ic_speaker_notes_off_24dp, stringResource(R.string.pref_never_reboot), checked = false, onCheckedChange = {}, enabled = false)
            Preference(null, null, summary = stringResource(R.string.pref_hosts_redirection_summary), enabled = false)
            Preference(R.drawable.ic_alt_route_24dp, stringResource(R.string.pref_redirection_ipv4), summary = current.rootRedirectionIpv4, enabled = false)
            Preference(R.drawable.ic_alt_route_24dp, stringResource(R.string.pref_redirection_ipv6), summary = current.rootRedirectionIpv6, enabled = false)
            Preference(null, null, summary = stringResource(R.string.pref_webserver_summary), enabled = false)
            SwitchPreference(R.drawable.ic_dns_24dp, stringResource(R.string.pref_webserver_enabled), checked = current.webServerEnabled, onCheckedChange = {}, enabled = false)
            Preference(R.drawable.ic_open_in_browser_24dp, stringResource(R.string.pref_webserver_test), stringResource(R.string.pref_webserver_state_not_running), enabled = false)
            Preference(R.drawable.ic_verified_user_24dp, stringResource(R.string.pref_webserver_certificate), enabled = false)
            SwitchPreference(R.drawable.ic_image_24dp, stringResource(R.string.pref_webserver_icon), checked = current.webServerIcon, onCheckedChange = {}, enabled = false)

            PreferenceCategory(stringResource(R.string.pref_ad_block_category))
            SwitchPreference(
                R.drawable.ic_ipv6_24dp,
                stringResource(R.string.pref_enable_ipv6),
                checked = current.ipv6Enabled,
                onCheckedChange = { checked -> viewModel.update { it.copy(ipv6Enabled = checked) } },
            )
            Preference(R.drawable.ic_sd_storage_24dp, stringResource(R.string.pref_backup_restore), enabled = false)

            PreferenceCategory(stringResource(R.string.pref_debug_category), enabled = false)
            SwitchPreference(
                R.drawable.ic_bug_report_24dp,
                stringResource(R.string.pref_enable_debug),
                checked = current.diagnosticLogEnabled,
                onCheckedChange = {},
                summary = stringResource(R.string.pref_enable_debug_summary),
                enabled = false,
            )
        }
    }
    when (choice) {
        PreferenceChoice.THEME -> ChoiceDialog(
            title = stringResource(R.string.pref_dark_theme),
            choices = themeChoices,
            selected = ThemeModes.indexOf(current.themeMode ?: ThemeMode.SYSTEM),
            onSelect = { index -> viewModel.update { it.copy(themeMode = ThemeModes[index]) } },
            onDismiss = { choice = null },
        )
        PreferenceChoice.SYSTEM_APPS -> ChoiceDialog(
            title = stringResource(R.string.pref_vpn_exclude_system_apps),
            choices = systemAppChoices,
            selected = SystemAppExclusions.indexOf(current.vpnSystemAppExclusion),
            onSelect = { index -> viewModel.update { it.copy(vpnSystemAppExclusion = SystemAppExclusions[index]) } },
            onDismiss = { choice = null },
        )
        null -> {}
    }
}
