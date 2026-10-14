package org.adaway.ui.sources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.adaway.AppContainer
import org.adaway.data.db.SourceEntity
import org.adaway.system.DocumentAccess
import org.adaway.ui.common.ConfigurationApplier

class SourcesViewModel(private val container: AppContainer) : ViewModel() {
    private val repository = container.sourceRepository

    val sources: StateFlow<List<SourceEntity>?> =
        repository.sources.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)
    val applier = ConfigurationApplier(container, viewModelScope)
    val recentlyDeleted = repository.recentlyDeleted

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

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
