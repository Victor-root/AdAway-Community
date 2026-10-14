package org.adaway.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    val settings: Flow<Settings> = dataStore.data.map(::read)

    suspend fun update(transform: (Settings) -> Settings) {
        dataStore.edit { preferences -> write(preferences, transform(read(preferences))) }
    }

    suspend fun isDataInitialized(): Boolean = dataStore.data.first()[Keys.dataInitialized] == true

    /** Whether the sources and lists of the previous app version could not be recovered. */
    val legacyImportFailed: Flow<Boolean> = dataStore.data.map { it[Keys.legacyImportFailed] == true }

    /** Stores the initial settings and marks the app data as initialized, in one write. */
    suspend fun completeDataInitialization(initial: Settings, legacyImportFailed: Boolean) {
        dataStore.edit { preferences ->
            write(preferences, initial)
            preferences[Keys.legacyImportFailed] = legacyImportFailed
            preferences[Keys.dataInitialized] = true
        }
    }

    suspend fun dismissLegacyImportFailure() {
        dataStore.edit { it.remove(Keys.legacyImportFailed) }
    }

    private fun read(preferences: Preferences): Settings {
        val defaults = Settings()
        return Settings(
            blockingMethod = preferences.enum(Keys.blockingMethod) ?: defaults.blockingMethod,
            welcomeCompleted = preferences[Keys.welcomeCompleted] ?: defaults.welcomeCompleted,
            themeMode = preferences.enum<ThemeMode>(Keys.themeMode),
            vpnWanted = preferences[Keys.vpnWanted] ?: defaults.vpnWanted,
            vpnStartOnBoot = preferences[Keys.vpnStartOnBoot] ?: defaults.vpnStartOnBoot,
            vpnConnectionMonitor = preferences[Keys.vpnConnectionMonitor] ?: defaults.vpnConnectionMonitor,
            vpnSystemAppExclusion = preferences.enum(Keys.vpnSystemAppExclusion) ?: defaults.vpnSystemAppExclusion,
            vpnExcludedApps = preferences[Keys.vpnExcludedApps] ?: defaults.vpnExcludedApps,
            ipv6Enabled = preferences[Keys.ipv6Enabled] ?: defaults.ipv6Enabled,
            rootRedirectionIpv4 = preferences[Keys.rootRedirectionIpv4] ?: defaults.rootRedirectionIpv4,
            rootRedirectionIpv6 = preferences[Keys.rootRedirectionIpv6] ?: defaults.rootRedirectionIpv6,
            webServerEnabled = preferences[Keys.webServerEnabled] ?: defaults.webServerEnabled,
            webServerIcon = preferences[Keys.webServerIcon] ?: defaults.webServerIcon,
            sourcesCheckOnStartup = preferences[Keys.sourcesCheckOnStartup] ?: defaults.sourcesCheckOnStartup,
            sourcesPeriodicCheck = preferences[Keys.sourcesPeriodicCheck] ?: defaults.sourcesPeriodicCheck,
            sourcesAutoSync = preferences[Keys.sourcesAutoSync] ?: defaults.sourcesAutoSync,
            sourcesUnmeteredOnly = preferences[Keys.sourcesUnmeteredOnly] ?: defaults.sourcesUnmeteredOnly,
            appUpdateCheckOnStartup = preferences[Keys.appUpdateCheckOnStartup] ?: defaults.appUpdateCheckOnStartup,
            appUpdatePeriodicCheck = preferences[Keys.appUpdatePeriodicCheck] ?: defaults.appUpdatePeriodicCheck,
            diagnosticLogEnabled = preferences[Keys.diagnosticLogEnabled] ?: defaults.diagnosticLogEnabled,
        )
    }

    private fun write(preferences: MutablePreferences, settings: Settings) {
        preferences[Keys.blockingMethod] = settings.blockingMethod.name
        preferences[Keys.welcomeCompleted] = settings.welcomeCompleted
        val themeMode = settings.themeMode
        if (themeMode == null) preferences.remove(Keys.themeMode) else preferences[Keys.themeMode] = themeMode.name
        preferences[Keys.vpnWanted] = settings.vpnWanted
        preferences[Keys.vpnStartOnBoot] = settings.vpnStartOnBoot
        preferences[Keys.vpnConnectionMonitor] = settings.vpnConnectionMonitor
        preferences[Keys.vpnSystemAppExclusion] = settings.vpnSystemAppExclusion.name
        preferences[Keys.vpnExcludedApps] = settings.vpnExcludedApps
        preferences[Keys.ipv6Enabled] = settings.ipv6Enabled
        preferences[Keys.rootRedirectionIpv4] = settings.rootRedirectionIpv4
        preferences[Keys.rootRedirectionIpv6] = settings.rootRedirectionIpv6
        preferences[Keys.webServerEnabled] = settings.webServerEnabled
        preferences[Keys.webServerIcon] = settings.webServerIcon
        preferences[Keys.sourcesCheckOnStartup] = settings.sourcesCheckOnStartup
        preferences[Keys.sourcesPeriodicCheck] = settings.sourcesPeriodicCheck
        preferences[Keys.sourcesAutoSync] = settings.sourcesAutoSync
        preferences[Keys.sourcesUnmeteredOnly] = settings.sourcesUnmeteredOnly
        preferences[Keys.appUpdateCheckOnStartup] = settings.appUpdateCheckOnStartup
        preferences[Keys.appUpdatePeriodicCheck] = settings.appUpdatePeriodicCheck
        preferences[Keys.diagnosticLogEnabled] = settings.diagnosticLogEnabled
    }

    private inline fun <reified T : Enum<T>> Preferences.enum(key: Preferences.Key<String>): T? =
        this[key]?.let { name -> enumValues<T>().firstOrNull { it.name == name } }

    private object Keys {
        val dataInitialized = booleanPreferencesKey("data_initialized")
        val legacyImportFailed = booleanPreferencesKey("legacy_import_failed")
        val blockingMethod = stringPreferencesKey("blocking_method")
        val welcomeCompleted = booleanPreferencesKey("welcome_completed")
        val themeMode = stringPreferencesKey("theme_mode")
        val vpnWanted = booleanPreferencesKey("vpn_wanted")
        val vpnStartOnBoot = booleanPreferencesKey("vpn_start_on_boot")
        val vpnConnectionMonitor = booleanPreferencesKey("vpn_connection_monitor")
        val vpnSystemAppExclusion = stringPreferencesKey("vpn_system_app_exclusion")
        val vpnExcludedApps = stringSetPreferencesKey("vpn_excluded_apps")
        val ipv6Enabled = booleanPreferencesKey("ipv6_enabled")
        val rootRedirectionIpv4 = stringPreferencesKey("root_redirection_ipv4")
        val rootRedirectionIpv6 = stringPreferencesKey("root_redirection_ipv6")
        val webServerEnabled = booleanPreferencesKey("web_server_enabled")
        val webServerIcon = booleanPreferencesKey("web_server_icon")
        val sourcesCheckOnStartup = booleanPreferencesKey("sources_check_on_startup")
        val sourcesPeriodicCheck = booleanPreferencesKey("sources_periodic_check")
        val sourcesAutoSync = booleanPreferencesKey("sources_auto_sync")
        val sourcesUnmeteredOnly = booleanPreferencesKey("sources_unmetered_only")
        val appUpdateCheckOnStartup = booleanPreferencesKey("app_update_check_on_startup")
        val appUpdatePeriodicCheck = booleanPreferencesKey("app_update_periodic_check")
        val diagnosticLogEnabled = booleanPreferencesKey("diagnostic_log_enabled")
    }
}
