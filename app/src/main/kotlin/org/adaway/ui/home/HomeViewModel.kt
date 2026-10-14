package org.adaway.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.adaway.AppContainer
import org.adaway.blocklist.BlockListSummary
import org.adaway.data.db.SourceEntity
import org.adaway.system.Notifications

/** How the enabled sources stand. */
data class SourceStats(val upToDate: Int, val pending: Int, val failed: Int)

data class HomeState(
    val summary: BlockListSummary,
    val sources: SourceStats,
    val configurationChanged: Boolean,
    val legacyImportFailed: Boolean,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    private val updater = container.blockListUpdater

    val state: StateFlow<HomeState?> = combine(
        container.blockListState.summary,
        container.sourceRepository.sources.map(::sourceStats),
        container.blockListState.configurationChanged,
        container.settingsRepository.legacyImportFailed,
    ) { summary, sources, configurationChanged, legacyImportFailed ->
        HomeState(summary, sources, configurationChanged, legacyImportFailed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    val progress = updater.progress
    val error = updater.error

    init {
        Notifications.cancelSourceUpdateAvailable(container.applicationContext)
        viewModelScope.launch {
            updater.runStartupTasks(checkForUpdates = container.settingsRepository.settings.first().sourcesCheckOnStartup)
        }
    }

    fun sync() = updater.requestSync()

    fun apply() = updater.requestApply()

    fun dismissError() = updater.dismissError()

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
