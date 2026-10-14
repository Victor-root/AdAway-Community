package org.adaway.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.adaway.AppContainer
import org.adaway.data.db.RuleType
import org.adaway.data.db.SourceCounts
import org.adaway.data.settings.BlockingMethod

data class HomeState(
    val method: BlockingMethod,
    val sources: SourceCounts,
    val userRules: Map<RuleType, Int>,
    val legacyImportFailed: Boolean,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    val state: StateFlow<HomeState?> = combine(
        container.settingsRepository.settings,
        container.database.sourceDao().observeCounts(),
        container.database.userRuleDao().observeCountsByType(),
        container.settingsRepository.legacyImportFailed,
    ) { settings, sources, ruleCounts, legacyImportFailed ->
        HomeState(
            method = settings.blockingMethod,
            sources = sources,
            userRules = ruleCounts.associate { it.type to it.count },
            legacyImportFailed = legacyImportFailed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun dismissLegacyImportFailure() {
        viewModelScope.launch { container.settingsRepository.dismissLegacyImportFailure() }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
