package org.adaway.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.adaway.AppContainer
import org.adaway.blocking.isBlockingActive
import org.adaway.blocklist.BlockListSummary
import org.adaway.data.db.SourceEntity
import org.adaway.data.settings.BlockingMethod
import org.adaway.data.settings.ThemeMode
import org.adaway.root.RootState
import org.adaway.update.AppRelease
import org.adaway.system.Notifications
import org.adaway.vpn.AlwaysOnMode
import org.adaway.vpn.StartResult
import org.adaway.vpn.VpnStatus

/** How the enabled sources stand. */
data class SourceStats(val upToDate: Int, val pending: Int, val failed: Int) {
    val outdated: Int get() = pending + failed
}

/** How ad blocking stands. [rootBusy] while the hosts file is being installed or reverted. */
data class BlockingState(val method: BlockingMethod, val vpnStatus: VpnStatus, val rootState: RootState, val rootBusy: Boolean) {
    val active: Boolean get() = isBlockingActive(method, vpnStatus, rootState)
}

data class HomeState(
    val summary: BlockListSummary,
    val sources: SourceStats,
    val configurationChanged: Boolean,
    val legacyImportFailed: Boolean,
    val blocking: BlockingState,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val updater = container.blockListUpdater
    private val vpnController = container.vpnController
    private val rootController = container.rootController
    private val blocking = combine(
        container.settingsRepository.settings,
        vpnController.status,
        rootController.state,
        rootController.busy,
    ) { settings, status, rootState, rootBusy ->
        BlockingState(settings.blockingMethod, status, rootState, rootBusy)
    }
    private val mutableConsentPrompt = MutableStateFlow(false)

    val state: StateFlow<HomeState?> = combine(
        container.blockListState.summary,
        container.sourceRepository.sources.map(::sourceStats),
        container.blockListState.configurationChanged,
        container.settingsRepository.legacyImportFailed,
        blocking,
    ) { summary, sources, configurationChanged, legacyImportFailed, blocking ->
        HomeState(summary, sources, configurationChanged, legacyImportFailed, blocking)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val progress = updater.progress
    val statusMessage = container.statusMessages.current
    val error = updater.error
    val rootError = rootController.error

    /** A newer version of the app, once found. */
    val appUpdate: StateFlow<AppRelease?> = container.appUpdater.available
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Set when the VPN the user wants lacks the consent: the screen asks Android for it. */
    val consentPrompt: StateFlow<Boolean> = mutableConsentPrompt.asStateFlow()

    init {
        Notifications.cancelSourceUpdateAvailable(container.applicationContext)
        viewModelScope.launch {
            val settings = container.settingsRepository.settings.first()
            updater.runStartupTasks(checkForUpdates = settings.sourcesCheckOnStartup)
            if (settings.appUpdateCheckOnStartup) container.appUpdater.checkOnStartup()
        }
        viewModelScope.launch {
            val result = vpnController.ensureRunning()
            if (result == StartResult.CONSENT_REQUIRED && vpnController.claimConsentPrompt()) mutableConsentPrompt.value = true
        }
    }

    fun sync() = updater.requestSync()

    fun apply() = updater.requestApply()

    /** For the VPN, called once the consent is granted. */
    fun enableBlocking() {
        viewModelScope.launch { container.blockingController.enable() }
    }

    fun disableBlocking() {
        viewModelScope.launch { container.blockingController.disable() }
    }

    /** The root blocking state is read from the device, where it may have changed meanwhile. */
    fun refreshRootState() {
        viewModelScope.launch {
            if (container.settingsRepository.settings.first().blockingMethod == BlockingMethod.ROOT) rootController.refresh()
        }
    }

    fun consentPromptShown() {
        mutableConsentPrompt.value = false
    }

    fun alwaysOnMode(): AlwaysOnMode = vpnController.alwaysOnMode()

    fun dismissError() = updater.dismissError()

    fun dismissRootError() = rootController.dismissError()

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.settingsRepository.update { it.copy(themeMode = mode) } }
    }

    fun dismissLegacyImportFailure() {
        viewModelScope.launch { container.settingsRepository.dismissLegacyImportFailure() }
    }

    private fun sourceStats(sources: List<SourceEntity>): SourceStats {
        val enabled = sources.filter { it.enabled }
        val failed = enabled.count { it.syncError != null }
        val pending = enabled.count { it.syncError == null && (it.syncedAt == null || it.updateAvailable) }
        return SourceStats(upToDate = enabled.size - failed - pending, pending = pending, failed = failed)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
