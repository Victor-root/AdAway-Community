package org.adaway.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.adaway.AppContainer
import org.adaway.data.settings.BlockingMethod
import org.adaway.system.AlwaysOnVpn

enum class WelcomeDialog { ROOT_REQUIRED, ALWAYS_ON_VPN, VPN_REQUEST_FAILED }

data class WelcomeState(
    val method: BlockingMethod = BlockingMethod.UNSET,
    val checkingRoot: Boolean = false,
    val dialog: WelcomeDialog? = null,
)

class WelcomeViewModel(private val container: AppContainer) : ViewModel() {
    private val checkingRoot = MutableStateFlow(false)
    private val dialog = MutableStateFlow<WelcomeDialog?>(null)

    val state: StateFlow<WelcomeState> = combine(
        container.settingsRepository.settings,
        checkingRoot,
        dialog,
    ) { settings, checking, shownDialog ->
        WelcomeState(settings.blockingMethod, checking, shownDialog)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WelcomeState())

    fun chooseRoot() {
        if (!checkingRoot.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            val granted = container.rootAccess.request()
            setMethod(if (granted) BlockingMethod.ROOT else BlockingMethod.UNSET)
            if (!granted) dialog.value = WelcomeDialog.ROOT_REQUIRED
            checkingRoot.value = false
        }
    }

    fun onVpnGranted() {
        viewModelScope.launch { setMethod(BlockingMethod.VPN) }
    }

    fun onVpnRefused(alwaysOnVpn: AlwaysOnVpn, ownPackage: String) {
        viewModelScope.launch { setMethod(BlockingMethod.UNSET) }
        dialog.value = when {
            alwaysOnVpn is AlwaysOnVpn.App && alwaysOnVpn.packageName != ownPackage -> WelcomeDialog.ALWAYS_ON_VPN
            alwaysOnVpn is AlwaysOnVpn.Unknown -> WelcomeDialog.VPN_REQUEST_FAILED
            else -> null
        }
    }

    fun dismissDialog() {
        dialog.value = null
    }

    fun finish(onFinished: () -> Unit) {
        viewModelScope.launch {
            container.settingsRepository.update { it.copy(welcomeCompleted = true) }
            onFinished()
        }
    }

    private suspend fun setMethod(method: BlockingMethod) {
        container.settingsRepository.update { it.copy(blockingMethod = method) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
