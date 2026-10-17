package org.adaway.ui.prefs

import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import org.adaway.engine.Engine
import org.adaway.system.appLanguage
import org.adaway.system.appLanguages
import org.adaway.system.openAppLanguageSettings
import org.adaway.system.ownName
import org.adaway.system.setAppLanguage
import org.adaway.system.systemKeepsAppLanguage
import org.adaway.ui.common.AdAwayScreen
import org.adaway.ui.common.appViewModel
import org.adaway.ui.theme.LocalTelevision
import org.adaway.ui.theme.defaultThemeMode

class PreferencesNavigation(
    val back: () -> Unit,
    val openUpdates: () -> Unit,
    val openExcludedApps: () -> Unit,
    val openHostsFile: () -> Unit,
    val openBackupRestore: () -> Unit,
)

/** The order of the dark theme choices. */
private val ThemeModes = listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM)

/** The order of the system app exclusion choices. */
private val SystemAppExclusions = listOf(SystemAppExclusion.NONE, SystemAppExclusion.ALL_EXCEPT_BROWSERS, SystemAppExclusion.ALL)

private enum class PreferenceChoice { THEME, LANGUAGE, SYSTEM_APPS, IPV4_REDIRECTION, IPV6_REDIRECTION }

/**
 * The preferences, all on one screen as in 6.x. Only the section of the blocking method in use is
 * enabled.
 */
@Composable
fun PreferencesScreen(navigation: PreferencesNavigation) {
    val viewModel = appViewModel(::PreferencesViewModel)
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val diagnosticLog by viewModel.diagnosticLog.collectAsStateWithLifecycle()
    val current = settings ?: return
    val context = LocalContext.current
    var choice by rememberSaveable { mutableStateOf<PreferenceChoice?>(null) }
    val usesVpn = current.blockingMethod == BlockingMethod.VPN
    val usesRoot = current.blockingMethod == BlockingMethod.ROOT
    val invalidRedirection = stringResource(R.string.pref_redirection_invalid)
    val resetDone = stringResource(R.string.pref_vpn_reset_done)
    val resetNotRunning = stringResource(R.string.pref_vpn_reset_not_running)
    val diagnosticLogCleared = stringResource(R.string.pref_vpn_diagnostic_log_cleared)
    val themeChoices = stringArrayResource(R.array.pref_dark_theme_modes).toList()
    val systemAppChoices = stringArrayResource(R.array.pref_vpn_excluded_system_apps_entries).toList()
    AdAwayScreen(title = stringResource(R.string.pref_main_title), onBack = navigation.back) { padding ->
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(padding)) {
            PreferenceCategory(stringResource(R.string.pref_general_category), divider = false)
            Preference(R.drawable.ic_brightness_medium_24dp, stringResource(R.string.pref_dark_theme), onClick = { choice = PreferenceChoice.THEME })
            Preference(
                R.drawable.ic_language_24dp,
                stringResource(R.string.pref_language),
                summary = context.appLanguage()?.ownName ?: stringResource(R.string.pref_language_system),
                onClick = { if (!(systemKeepsAppLanguage && context.openAppLanguageSettings())) choice = PreferenceChoice.LANGUAGE },
            )
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
            VpnDnsPreferences(current, enabled = usesVpn, update = viewModel::update)
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
                checked = current.diagnosticLogEnabled,
                onCheckedChange = { checked -> viewModel.update { it.copy(diagnosticLogEnabled = checked) } },
                summary = stringResource(R.string.pref_vpn_diagnostic_log_enabled_description),
                enabled = usesVpn,
            )
            Preference(
                R.drawable.ic_article_24dp,
                stringResource(R.string.pref_vpn_diagnostic_log),
                stringResource(R.string.pref_vpn_diagnostic_log_description),
                enabled = usesVpn,
                onClick = viewModel::showDiagnosticLog,
            )
            Preference(
                R.drawable.ic_delete_sweep_24dp,
                stringResource(R.string.pref_vpn_diagnostic_log_clear),
                stringResource(R.string.pref_vpn_diagnostic_log_clear_description),
                enabled = usesVpn,
                onClick = {
                    viewModel.clearDiagnosticLog()
                    Toast.makeText(context, diagnosticLogCleared, Toast.LENGTH_SHORT).show()
                },
            )

            PreferenceCategory(stringResource(R.string.pref_root_ad_blocker_configuration), enabled = usesRoot)
            Preference(R.drawable.ic_edit_note_24dp, stringResource(R.string.pref_root_open_hosts), enabled = usesRoot, onClick = navigation.openHostsFile)
            Preference(null, null, summary = stringResource(R.string.pref_hosts_redirection_summary), enabled = usesRoot)
            Preference(
                R.drawable.ic_alt_route_24dp,
                stringResource(R.string.pref_redirection_ipv4),
                summary = current.rootRedirectionIpv4,
                enabled = usesRoot,
                onClick = { choice = PreferenceChoice.IPV4_REDIRECTION },
            )
            // Only written in the hosts file with the IPv6 support.
            Preference(
                R.drawable.ic_alt_route_24dp,
                stringResource(R.string.pref_redirection_ipv6),
                summary = current.rootRedirectionIpv6,
                enabled = usesRoot && current.ipv6Enabled,
                onClick = { choice = PreferenceChoice.IPV6_REDIRECTION },
            )
            WebServerPreferences(current, enabled = usesRoot)

            PreferenceCategory(stringResource(R.string.pref_ad_block_category))
            SwitchPreference(
                R.drawable.ic_ipv6_24dp,
                stringResource(R.string.pref_enable_ipv6),
                checked = current.ipv6Enabled,
                onCheckedChange = { checked -> viewModel.updateHostsSetting { it.copy(ipv6Enabled = checked) } },
            )
            Preference(R.drawable.ic_sd_storage_24dp, stringResource(R.string.pref_backup_restore), onClick = navigation.openBackupRestore)
        }
    }
    diagnosticLog?.let { DiagnosticLogDialog(it, onDismiss = viewModel::hideDiagnosticLog) }
    when (choice) {
        PreferenceChoice.THEME -> ChoiceDialog(
            title = stringResource(R.string.pref_dark_theme),
            choices = themeChoices,
            selected = ThemeModes.indexOf(current.themeMode ?: defaultThemeMode(LocalTelevision.current)),
            onSelect = { index -> viewModel.update { it.copy(themeMode = ThemeModes[index]) } },
            onDismiss = { choice = null },
        )
        PreferenceChoice.LANGUAGE -> {
            val activity = LocalActivity.current
            val languages = remember { context.appLanguages().sortedBy { it.ownName.lowercase(it) } }
            ChoiceDialog(
                title = stringResource(R.string.pref_language),
                choices = listOf(stringResource(R.string.pref_language_system)) + languages.map { it.ownName },
                selected = languages.indexOf(context.appLanguage()) + 1,
                onSelect = { index -> activity?.setAppLanguage(languages.getOrNull(index - 1)) },
                onDismiss = { choice = null },
            )
        }
        PreferenceChoice.SYSTEM_APPS -> ChoiceDialog(
            title = stringResource(R.string.pref_vpn_exclude_system_apps),
            choices = systemAppChoices,
            selected = SystemAppExclusions.indexOf(current.vpnSystemAppExclusion),
            onSelect = { index -> viewModel.update { it.copy(vpnSystemAppExclusion = SystemAppExclusions[index]) } },
            onDismiss = { choice = null },
        )
        PreferenceChoice.IPV4_REDIRECTION, PreferenceChoice.IPV6_REDIRECTION -> {
            val ipv6 = choice == PreferenceChoice.IPV6_REDIRECTION
            TextInputDialog(
                title = stringResource(if (ipv6) R.string.pref_redirection_ipv6 else R.string.pref_redirection_ipv4),
                initialValue = if (ipv6) current.rootRedirectionIpv6 else current.rootRedirectionIpv4,
                onConfirm = { text ->
                    val address = Engine.normalizeIpAddress(text)?.takeIf { it.contains(':') == ipv6 }
                    when {
                        address == null -> Toast.makeText(context, invalidRedirection, Toast.LENGTH_SHORT).show()
                        ipv6 -> viewModel.updateHostsSetting { it.copy(rootRedirectionIpv6 = address) }
                        else -> viewModel.updateHostsSetting { it.copy(rootRedirectionIpv4 = address) }
                    }
                },
                onDismiss = { choice = null },
            )
        }
        null -> {}
    }
}
