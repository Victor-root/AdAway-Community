package org.adaway.ui.prefs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val mutableDiagnosticLog = MutableStateFlow<String?>(null)

    /** The recorded log while shown. */
    val diagnosticLog: StateFlow<String?> = mutableDiagnosticLog.asStateFlow()

    fun showDiagnosticLog() {
        viewModelScope.launch { mutableDiagnosticLog.value = container.diagnosticLog.read() }
    }

    fun hideDiagnosticLog() {
        mutableDiagnosticLog.value = null
    }

    fun clearDiagnosticLog() {
        container.applicationScope.launch { container.diagnosticLog.clear() }
    }

    /** False when the VPN is not running. */
    fun resetVpn(): Boolean = container.vpnController.reset()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
