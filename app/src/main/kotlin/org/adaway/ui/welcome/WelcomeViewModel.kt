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
import org.adaway.ui.common.VpnRefusal

data class WelcomeState(
    val method: BlockingMethod = BlockingMethod.UNSET,
    val checkingRoot: Boolean = false,
    val rootRequired: Boolean = false,
    val vpnRefusal: VpnRefusal? = null,
)

class WelcomeViewModel(private val container: AppContainer) : ViewModel() {
    private val checkingRoot = MutableStateFlow(false)
    private val rootRequired = MutableStateFlow(false)
    private val vpnRefusal = MutableStateFlow<VpnRefusal?>(null)

    val state: StateFlow<WelcomeState> = combine(
        container.settingsRepository.settings,
        checkingRoot,
        rootRequired,
        vpnRefusal,
    ) { settings, checking, root, refusal ->
        WelcomeState(settings.blockingMethod, checking, root, refusal)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), WelcomeState())

    fun chooseRoot() {
        if (!checkingRoot.compareAndSet(expect = false, update = true)) return
        viewModelScope.launch {
            val granted = container.rootAccess.request()
            setMethod(if (granted) BlockingMethod.ROOT else BlockingMethod.UNSET)
            rootRequired.value = !granted
            checkingRoot.value = false
        }
    }

    fun onVpnGranted() {
        viewModelScope.launch { setMethod(BlockingMethod.VPN) }
    }

    fun onVpnRefused(refusal: VpnRefusal?) {
        viewModelScope.launch { setMethod(BlockingMethod.UNSET) }
        vpnRefusal.value = refusal
    }

    fun dismissDialog() {
        rootRequired.value = false
        vpnRefusal.value = null
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
