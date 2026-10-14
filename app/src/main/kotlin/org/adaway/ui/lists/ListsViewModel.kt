package org.adaway.ui.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.adaway.AppContainer
import org.adaway.blocklist.RuleSaveResult
import org.adaway.data.db.RuleType
import org.adaway.data.db.UserRuleEntity
import org.adaway.engine.Engine

@OptIn(ExperimentalCoroutinesApi::class)
class ListsViewModel(private val container: AppContainer, initialType: RuleType) : ViewModel() {
    private val repository = container.userRuleRepository
    private val mutableType = MutableStateFlow(initialType)
    private val mutableFilter = MutableStateFlow("")
    private val mutableShowSourceRules = MutableStateFlow(true)

    val type: StateFlow<RuleType> = mutableType.asStateFlow()
    val filter: StateFlow<String> = mutableFilter.asStateFlow()
    val showSourceRules: StateFlow<Boolean> = mutableShowSourceRules.asStateFlow()

    val userRules: StateFlow<List<UserRuleEntity>?> = combine(mutableType, mutableFilter, ::Pair)
        .flatMapLatest { (type, filter) -> repository.observe(type, filter) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    /** Rules from the enabled sources, as `host` or `host address` lines, reloaded after each computation. */
    val sourceRules: Flow<PagingData<String>> = combine(
        mutableType,
        mutableFilter,
        mutableShowSourceRules,
        container.blockListState.compilations,
    ) { type, filter, show, _ -> Triple(type, filter, show) }
        .flatMapLatest { (type, filter, show) ->
            if (!show) {
                flowOf(PagingData.empty())
            } else {
                Pager(PagingConfig(pageSize = PAGE_SIZE, enablePlaceholders = false)) {
                    CatalogPagingSource(container.engineFiles.catalog(type), filter)
                }.flow
            }
        }
        .cachedIn(viewModelScope)

    val configurationChanged: StateFlow<Boolean> = container.blockListState.configurationChanged
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)
    val progress = container.blockListUpdater.progress

    fun selectType(type: RuleType) {
        mutableType.value = type
    }

    fun search(filter: String) {
        mutableFilter.value = filter
    }

    fun setShowSourceRules(show: Boolean) {
        mutableShowSourceRules.value = show
    }

    /** Whether the dialog fields can be saved, checked as the user types. */
    fun isValid(host: String, redirection: String): Boolean {
        val type = mutableType.value
        val validHost = Engine.normalizeHostname(host, allowWildcards = type != RuleType.REDIRECTED) != null
        return validHost && (type != RuleType.REDIRECTED || Engine.normalizeIpAddress(redirection) != null)
    }

    fun save(rule: UserRuleEntity?, host: String, redirection: String, onResult: (RuleSaveResult) -> Unit) {
        viewModelScope.launch {
            onResult(repository.save(rule?.id, mutableType.value, host, redirection.takeIf { it.isNotBlank() }))
        }
    }

    fun setEnabled(rule: UserRuleEntity, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(rule.id, enabled) }
    }

    fun delete(rule: UserRuleEntity) {
        viewModelScope.launch { repository.delete(rule) }
    }

    fun restore(rule: UserRuleEntity) {
        viewModelScope.launch { repository.restore(rule) }
    }

    fun apply() = container.blockListUpdater.requestApply()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val PAGE_SIZE = 50
    }
}
