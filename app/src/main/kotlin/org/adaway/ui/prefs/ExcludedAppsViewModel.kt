package org.adaway.ui.prefs

import android.content.pm.ApplicationInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.adaway.AppContainer
import org.adaway.vpn.VpnExclusions
import java.text.Collator

/** A user app that can be kept out of the VPN. */
data class UserApp(val packageName: String, val label: String, val info: ApplicationInfo)

/**
 * The user apps to exclude from the VPN. The choice is saved when leaving the screen, so that the
 * tunnel is rebuilt once rather than at each switch.
 */
class ExcludedAppsViewModel(private val container: AppContainer) : ViewModel() {
    private val mutableApps = MutableStateFlow<List<UserApp>?>(null)
    private val mutableExcluded = MutableStateFlow<Set<String>>(emptySet())
    private var loaded = false

    val apps: StateFlow<List<UserApp>?> = mutableApps.asStateFlow()
    val excluded: StateFlow<Set<String>> = mutableExcluded.asStateFlow()

    init {
        viewModelScope.launch {
            mutableExcluded.value = container.settingsRepository.settings.first().vpnExcludedApps
            mutableApps.value = withContext(Dispatchers.IO) { loadUserApps() }
            loaded = true
        }
    }

    fun setExcluded(packageName: String, excluded: Boolean) {
        mutableExcluded.value = if (excluded) mutableExcluded.value + packageName else mutableExcluded.value - packageName
    }

    fun setAllExcluded(excluded: Boolean) {
        mutableExcluded.value = if (excluded) mutableApps.value.orEmpty().map { it.packageName }.toSet() else emptySet()
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

    /** Sorted by name, ignoring case and accents. */
    private fun loadUserApps(): List<UserApp> {
        val context = container.applicationContext
        val packageManager = context.packageManager
        val installed = VpnExclusions.installedApplications(packageManager)
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        return installed
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != context.packageName }
            .map { UserApp(it.packageName, it.loadLabel(packageManager).toString(), it) }
            .sortedWith { first, second -> collator.compare(first.label, second.label) }
    }
}
