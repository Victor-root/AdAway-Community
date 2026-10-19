package org.adaway.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.adaway.R
import org.adaway.blocklist.UpdateError
import org.adaway.data.db.RuleType
import org.adaway.data.db.SourceEntity
import org.adaway.data.settings.BlockingMethod
import org.adaway.ui.common.AdAwayAlertDialog
import org.adaway.ui.common.DialogButton
import org.adaway.ui.common.VpnRefusal
import org.adaway.ui.common.VpnRefusalDialog
import org.adaway.ui.common.appViewModel
import org.adaway.ui.common.rootErrorText
import org.adaway.ui.common.rootErrorTitle
import org.adaway.ui.common.rememberVpnConsentRequest
import org.adaway.ui.common.sourceLabel
import org.adaway.ui.common.syncErrorText
import org.adaway.ui.common.updateErrorText
import org.adaway.ui.common.updateErrorTitle
import org.adaway.ui.common.vpnRefusal
import org.adaway.vpn.AlwaysOnMode

class HomeNavigation(
    val openSources: () -> Unit,
    val openLists: (RuleType) -> Unit,
    val openPreferences: () -> Unit,
    val openHelp: () -> Unit,
    val openLog: () -> Unit,
    val openUpdate: () -> Unit,
)

/**
 * The home logic shared by the phone and TV screens: the VPN consent, the blocking actions and the
 * dialogs. [screen] draws the home of the device.
 */
@Composable
fun HomeRoute(navigation: HomeNavigation, screen: @Composable (HomeState, HomeViewModel, BlockingActions) -> Unit) {
    val viewModel = appViewModel(::HomeViewModel)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val failedSources by viewModel.failedSources.collectAsStateWithLifecycle()
    val rootError by viewModel.rootError.collectAsStateWithLifecycle()
    val consentPrompt by viewModel.consentPrompt.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var refusal by remember { mutableStateOf<VpnRefusal?>(null) }
    var persistenceMode by remember { mutableStateOf<AlwaysOnMode?>(null) }
    val requestConsent = rememberVpnConsentRequest(
        onGranted = viewModel::enableBlocking,
        onRefused = { refusal = vpnRefusal(context) },
    )
    LifecycleResumeEffect(Unit) {
        viewModel.refreshRootState()
        onPauseOrDispose {}
    }
    LaunchedEffect(consentPrompt) {
        if (consentPrompt) {
            viewModel.consentPromptShown()
            requestConsent()
        }
    }
    val current = state ?: return
    screen(
        current,
        viewModel,
        BlockingActions(
            toggle = {
                when {
                    current.blocking.active -> viewModel.disableBlocking()
                    current.blocking.method == BlockingMethod.ROOT -> viewModel.enableBlocking()
                    else -> requestConsent()
                }
            },
            openPersistence = { persistenceMode = viewModel.alwaysOnMode() },
        ),
    )
    error?.let { UpdateErrorDialog(it, failedSources, onRetry = viewModel::sync, onHelp = navigation.openHelp, onDismiss = viewModel::dismissError) }
    rootError?.let { ErrorDialog(rootErrorTitle(it), rootErrorText(it), confirmButton = null, onHelp = navigation.openHelp, onDismiss = viewModel::dismissRootError) }
    refusal?.let { VpnRefusalDialog(it, onDismiss = { refusal = null }) }
    persistenceMode?.let { VpnPersistenceDialog(it, onDismiss = { persistenceMode = null }) }
}

@Composable
private fun UpdateErrorDialog(
    error: UpdateError,
    failedSources: List<SourceEntity>,
    onRetry: () -> Unit,
    onHelp: () -> Unit,
    onDismiss: () -> Unit,
) {
    val canRetry = error != UpdateError.APPLY_FAILED
    val details = if (error == UpdateError.DOWNLOAD_FAILED) {
        failedSources.mapNotNull { source ->
            source.syncError?.let { stringResource(R.string.update_error_failed_source, sourceLabel(source), syncErrorText(it, source.syncErrorStatus)) }
        }
    } else {
        emptyList()
    }
    ErrorDialog(
        title = updateErrorTitle(error),
        text = (listOf(updateErrorText(error)) + details).joinToString("\n"),
        confirmButton = if (canRetry) DialogButton(stringResource(R.string.retry_sync), onClick = { onDismiss(); onRetry() }) else null,
        onHelp = onHelp,
        onDismiss = onDismiss,
    )
}

/** A failure, pointing to the help. Without [confirmButton], the dialog offers to close. */
@Composable
private fun ErrorDialog(title: String, text: String, confirmButton: DialogButton?, onHelp: () -> Unit, onDismiss: () -> Unit) {
    AdAwayAlertDialog(
        onDismissRequest = onDismiss,
        icon = painterResource(android.R.drawable.ic_dialog_alert),
        title = title,
        message = text + "\n\n" + stringResource(R.string.error_dialog_help),
        confirmButton = confirmButton ?: DialogButton(stringResource(R.string.close), onClick = onDismiss),
        dismissButton = DialogButton(stringResource(R.string.help), onClick = { onDismiss(); onHelp() }),
    )
}
