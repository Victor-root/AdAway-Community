package org.adaway.ui.common

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.adaway.AppContainer
import org.adaway.R
import org.adaway.data.settings.BlockingMethod
import org.adaway.ui.theme.LocalAdAwayColors

/** Applying the configuration changed on a screen, as the sources and lists screens offer it. */
class ConfigurationApplier(private val container: AppContainer, private val scope: CoroutineScope) {
    private val updater = container.blockListUpdater

    val configurationChanged: StateFlow<Boolean> = container.blockListState.configurationChanged
        .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)
    val progress = updater.progress
    val error = updater.error

    /** Changes applied while the VPN blocking is off only take effect once it is on. */
    val blockingOff: StateFlow<Boolean> = combine(container.settingsRepository.settings, container.vpnController.status) { settings, status ->
        settings.blockingMethod == BlockingMethod.VPN && !status.isServiceAlive()
    }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    fun apply() = updater.requestApply()

    /** Called once the consent is granted, when needed. */
    fun enableBlocking() {
        scope.launch { container.vpnController.enable() }
    }

    fun dismissError() = updater.dismissError()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

/** How long the failure message stays shown. */
private const val FAILURE_MILLIS = 2_750L

/**
 * The 6.x snackbars of a changed configuration: apply it, applying, or the failure. Applying while
 * the blocking is off first offers to turn it on.
 */
@Composable
fun ApplyConfigurationSnackbar(applier: ConfigurationApplier) {
    val configurationChanged by applier.configurationChanged.collectAsStateWithLifecycle()
    val progress by applier.progress.collectAsStateWithLifecycle()
    val error by applier.error.collectAsStateWithLifecycle()
    val blockingOff by applier.blockingOff.collectAsStateWithLifecycle()
    var applyWhileOff by rememberSaveable { mutableStateOf(false) }
    val requestConsent = rememberVpnConsentRequest(onGranted = applier::enableBlocking, onRefused = {})
    val colors = LocalAdAwayColors.current
    LaunchedEffect(error) {
        if (error == null) return@LaunchedEffect
        delay(FAILURE_MILLIS)
        applier.dismissError()
    }
    if (applyWhileOff) {
        AdAwayAlertDialog(
            onDismissRequest = { applyWhileOff = false },
            title = stringResource(R.string.apply_blocking_off_title),
            message = stringResource(R.string.apply_blocking_off_text),
            confirmButton = DialogButton(stringResource(R.string.apply_and_enable), onClick = {
                applyWhileOff = false
                applier.apply()
                requestConsent()
            }),
            dismissButton = DialogButton(stringResource(R.string.apply_only), onClick = {
                applyWhileOff = false
                applier.apply()
            }),
        )
    }
    when {
        error != null -> AdAwaySnackbar(stringResource(R.string.configuration_failed)) {
            Icon(
                painterResource(R.drawable.ic_error_outline_24dp),
                contentDescription = null,
                tint = colors.snackbarText,
                modifier = Modifier.padding(8.dp),
            )
        }
        configurationChanged && progress != null -> AdAwaySnackbar(stringResource(R.string.configuration_installing)) {
            CircularProgressIndicator(modifier = Modifier.padding(8.dp).size(32.dp), color = colors.primary)
        }
        configurationChanged -> AdAwaySnackbar(
            stringResource(R.string.configuration_changed),
            actionLabel = stringResource(R.string.apply),
            onAction = { if (blockingOff) applyWhileOff = true else applier.apply() },
        )
    }
}
