package org.adaway.ui.welcome

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.adaway.AppContainer
import org.adaway.blocklist.UpdateError
import org.adaway.data.settings.BlockingMethod
import org.adaway.ui.common.VpnRefusal

/** The first download of the sources, then the start of the blocking. */
sealed interface WelcomeSync {
    data object Running : WelcomeSync

    data object Done : WelcomeSync

    data class Failed(val error: UpdateError) : WelcomeSync
}

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
    private val mutableStep = MutableStateFlow(0)
    private val mutableSync = MutableStateFlow<WelcomeSync?>(null)

    /** The phone wizard step: method, synchronization, battery, then the brand battery manager. */
    val step: StateFlow<Int> = mutableStep.asStateFlow()
    val sync: StateFlow<WelcomeSync?> = mutableSync.asStateFlow()

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

    fun goTo(step: Int) {
        mutableStep.value = step
        if (step == SYNC_STEP && mutableSync.value == null) startSync()
    }

    /** Downloads the sources, then turns the blocking on, as the end of the first launch setup. */
    fun startSync() {
        mutableSync.value = WelcomeSync.Running
        viewModelScope.launch {
            val error = container.blockListUpdater.syncAndApply(onlyPending = false)
            mutableSync.value = if (error != null) {
                WelcomeSync.Failed(error)
            } else {
                container.vpnController.enable()
                WelcomeSync.Done
            }
        }
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

    companion object {
        const val SYNC_STEP = 1
        const val STEP_COUNT = 4
        private const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
