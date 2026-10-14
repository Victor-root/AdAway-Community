package org.adaway.ui.log

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.adaway.AppContainer
import org.adaway.R
import org.adaway.data.db.RuleType
import org.adaway.data.settings.BlockingMethod
import org.adaway.dnslog.DnsLogError
import org.adaway.engine.Engine
import org.adaway.ui.common.ConfigurationApplier

/** A name asked for, with the rule of the user for it, if any, and what the final list decides. */
data class DnsLogEntry(val host: String, val userType: RuleType?, val verdict: Int) {
    /** The list the host is in: the one of the user, else the one of the sources. */
    val type: RuleType?
        get() = userType ?: when (verdict) {
            Engine.VERDICT_BLOCK -> RuleType.BLOCKED
            Engine.VERDICT_REDIRECT -> RuleType.REDIRECTED
            else -> null
        }
}

enum class DnsLogSort(@StringRes val label: Int, val comparator: Comparator<DnsLogEntry>) {
    ALPHABETICAL(R.string.log_sort_alphabetical, compareBy { it.host }),

    /** By domain, then subdomain: the names of a site stay together. */
    TOP_LEVEL_DOMAIN(R.string.log_sort_top_level_domain, compareBy<DnsLogEntry> { entry ->
        entry.host.split('.').asReversed().joinToString("\u0000")
    }),
}

class DnsLogViewModel(private val container: AppContainer) : ViewModel() {
    private val dnsLog = container.dnsLog
    private val userRules = container.userRuleRepository
    private val mutableEntries = MutableStateFlow<List<DnsLogEntry>?>(null)
    private val mutableRecording = MutableStateFlow(false)
    private val mutableRefreshing = MutableStateFlow(false)
    private val mutableSort = MutableStateFlow(DnsLogSort.TOP_LEVEL_DOMAIN)
    private val mutableError = MutableStateFlow<DnsLogError?>(null)

    val applier = ConfigurationApplier(container, viewModelScope)

    /** Null until first read. */
    val entries: StateFlow<List<DnsLogEntry>?> = mutableEntries.asStateFlow()
    val recording: StateFlow<Boolean> = mutableRecording.asStateFlow()
    val refreshing: StateFlow<Boolean> = mutableRefreshing.asStateFlow()
    val error: StateFlow<DnsLogError?> = mutableError.asStateFlow()

    /** In root mode, the names the hosts file blocks never reach the network. */
    val blockedRequestsIgnored: StateFlow<Boolean> = container.settingsRepository.settings
        .map { it.blockingMethod == BlockingMethod.ROOT }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    fun refresh() {
        mutableRefreshing.value = true
        viewModelScope.launch {
            val names = dnsLog.names()
            val types = userRules.enabledTypes()
            val verdicts = verdicts(names)
            mutableEntries.value = names.mapIndexed { index, host -> DnsLogEntry(host, types[host], verdicts[index]) }
                .sortedWith(mutableSort.value.comparator)
            mutableRecording.value = dnsLog.isRecording()
            mutableRefreshing.value = false
        }
    }

    fun toggleRecording() {
        viewModelScope.launch {
            val recording = !mutableRecording.value
            when (val error = dnsLog.setRecording(recording)) {
                null -> mutableRecording.value = recording
                else -> mutableError.value = error
            }
        }
    }

    /** Returns the new sort. */
    fun toggleSort(): DnsLogSort {
        val sort = if (mutableSort.value == DnsLogSort.ALPHABETICAL) DnsLogSort.TOP_LEVEL_DOMAIN else DnsLogSort.ALPHABETICAL
        mutableSort.value = sort
        mutableEntries.value = mutableEntries.value?.sortedWith(sort.comparator)
        return sort
    }

    fun clear() {
        viewModelScope.launch {
            dnsLog.clear()
            mutableEntries.value = emptyList()
        }
    }

    /** Puts the host in the list of [type], with the [redirection] address for a redirection. */
    fun putHost(host: String, type: RuleType, redirection: String? = null) {
        viewModelScope.launch {
            userRules.putHost(host, type, redirection)
            setUserType(host, type)
        }
    }

    fun removeHost(host: String) {
        viewModelScope.launch {
            userRules.removeHost(host)
            setUserType(host, null)
        }
    }

    fun dismissError() {
        mutableError.value = null
    }

    private fun setUserType(host: String, type: RuleType?) {
        mutableEntries.value = mutableEntries.value?.map { if (it.host == host) it.copy(userType = type) else it }
    }

    /** All allowed when the final list cannot be read. */
    private suspend fun verdicts(names: List<String>): IntArray = withContext(Dispatchers.IO) {
        try {
            Engine.verdicts(container.engineFiles.finalDirectory.path, names.toTypedArray())
        } catch (_: RuntimeException) {
            IntArray(names.size) { Engine.VERDICT_ALLOW }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
