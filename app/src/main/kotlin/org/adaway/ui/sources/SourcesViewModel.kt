package org.adaway.ui.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.adaway.AppContainer
import org.adaway.data.db.SourceEntity
import org.adaway.data.settings.BlockingMethod
import org.adaway.system.DocumentAccess

class SourcesViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.sourceRepository

    val sources: StateFlow<List<SourceEntity>?> =
        repository.sources.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)
    val configurationChanged: StateFlow<Boolean> = container.blockListState.configurationChanged
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)
    val progress = container.blockListUpdater.progress
    val error = container.blockListUpdater.error
    val recentlyDeleted = repository.recentlyDeleted

    /** Changes applied while the VPN blocking is off only take effect once it is on. */
    val blockingOff: StateFlow<Boolean> = combine(container.settingsRepository.settings, container.vpnController.status) { settings, status ->
        settings.blockingMethod == BlockingMethod.VPN && !status.isServiceAlive()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    fun setEnabled(source: SourceEntity, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(source.id, enabled) }
    }

    /** Deletes for good, as on TV where a confirmation replaces the undo action. */
    fun delete(source: SourceEntity) {
        viewModelScope.launch {
            repository.delete(source)
            forgetDeleted(source)
        }
    }

    fun restore(source: SourceEntity) {
        viewModelScope.launch { repository.restore(source) }
    }

    /** The deletion can no longer be undone: the access to a picked file is released. */
    fun forgetDeleted(source: SourceEntity) {
        repository.forgetRecentlyDeleted()
        DocumentAccess.release(container.applicationContext, source.url)
    }

    fun apply() = container.blockListUpdater.requestApply()

    /** Called once the consent is granted, when needed. */
    fun enableBlocking() {
        viewModelScope.launch { container.vpnController.enable() }
    }

    fun dismissError() = container.blockListUpdater.dismissError()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
