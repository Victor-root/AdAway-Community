package org.adaway.ui.prefs

import android.content.pm.ApplicationInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.adaway.AppContainer
import org.adaway.vpn.VpnExclusions
import java.text.Collator
import java.text.Normalizer

/** An installed app that can be kept out of the VPN. */
data class ExcludableApp(val packageName: String, val label: String, val info: ApplicationInfo)

/**
 * The user apps to exclude from the VPN, or the [system] ones. Both lists share one set of
 * excluded apps, saved when leaving the screen so that the tunnel is rebuilt once rather than at
 * each switch.
 */
class ExcludedAppsViewModel(private val container: AppContainer, private val system: Boolean) : ViewModel() {
    private val mutableApps = MutableStateFlow<List<ExcludableApp>?>(null)
    private val mutableExcluded = MutableStateFlow<Set<String>>(emptySet())
    private val mutableFilter = MutableStateFlow("")
    private var loaded = false

    val excluded: StateFlow<Set<String>> = mutableExcluded.asStateFlow()
    val filter: StateFlow<String> = mutableFilter.asStateFlow()

    /** The apps whose name or package contains the filter, ignoring case and accents. Null while loading. */
    val apps: StateFlow<List<ExcludableApp>?> = combine(mutableApps, mutableFilter) { apps, filter ->
        val wanted = filter.searchable()
        if (wanted.isEmpty()) apps else apps?.filter { wanted in it.label.searchable() || wanted in it.packageName.searchable() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            val excluded = container.settingsRepository.settings.first().vpnExcludedApps
            mutableExcluded.value = excluded
            mutableApps.value = withContext(Dispatchers.IO) { loadApps(excluded) }
            loaded = true
        }
    }

    fun search(filter: String) {
        mutableFilter.value = filter
    }

    fun setExcluded(packageName: String, excluded: Boolean) {
        mutableExcluded.value = if (excluded) mutableExcluded.value + packageName else mutableExcluded.value - packageName
    }

    /** Excludes or includes every app shown, the filtered ones only while searching. */
    fun setAllExcluded(excluded: Boolean) {
        val shown = apps.value.orEmpty().map { it.packageName }
        mutableExcluded.value = if (excluded) mutableExcluded.value + shown else mutableExcluded.value - shown.toSet()
    }

    override fun onCleared() {
        if (!loaded) return
        val chosen = mutableExcluded.value
        container.applicationScope.launch {
            container.settingsRepository.update { settings ->
                if (settings.vpnExcludedApps == chosen) settings else settings.copy(vpnExcludedApps = chosen)
            }
        }
    }

    /**
     * The [excluded] apps first, then the others, each sorted by name, ignoring case and accents.
     * The order stays while the user switches apps, so that none moves under the finger.
     */
    private fun loadApps(excluded: Set<String>): List<ExcludableApp> {
        val context = container.applicationContext
        val packageManager = context.packageManager
        val installed = VpnExclusions.installedApplications(packageManager)
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        return installed
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM != 0) == system && it.packageName != context.packageName }
            .map { ExcludableApp(it.packageName, it.loadLabel(packageManager).toString(), it) }
            .sortedWith(compareBy<ExcludableApp> { it.packageName !in excluded }.thenComparator { first, second -> collator.compare(first.label, second.label) })
    }

    private companion object {
        val ACCENTS = Regex("\\p{Mn}+")

        fun String.searchable(): String = ACCENTS.replace(Normalizer.normalize(trim(), Normalizer.Form.NFD), "").lowercase()
    }
}
