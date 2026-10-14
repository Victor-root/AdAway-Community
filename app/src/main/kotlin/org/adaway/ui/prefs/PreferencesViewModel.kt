package org.adaway.ui.prefs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.adaway.AppContainer
import org.adaway.data.settings.BlockingMethod
import org.adaway.data.settings.Settings

class PreferencesViewModel(private val container: AppContainer) : ViewModel() {
    val settings: StateFlow<Settings?> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun update(transform: (Settings) -> Settings) {
        viewModelScope.launch { container.settingsRepository.update(transform) }
    }

    /** For a setting written in the hosts file: an installed one is written again. */
    fun updateHostsSetting(transform: (Settings) -> Settings) {
        container.applicationScope.launch {
            val before = container.settingsRepository.settings.first()
            container.settingsRepository.update(transform)
            if (before.blockingMethod == BlockingMethod.ROOT && transform(before) != before) container.rootController.reinstall()
        }
    }

    /** False when the VPN is not running. */
    fun resetVpn(): Boolean = container.vpnController.reset()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
