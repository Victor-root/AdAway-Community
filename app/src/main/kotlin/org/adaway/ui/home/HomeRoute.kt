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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.adaway.R
import org.adaway.blocklist.UpdateError
import org.adaway.data.db.RuleType
import org.adaway.ui.common.AdAwayAlertDialog
import org.adaway.ui.common.DialogButton
import org.adaway.ui.common.VpnRefusal
import org.adaway.ui.common.VpnRefusalDialog
import org.adaway.ui.common.appViewModel
import org.adaway.ui.common.rememberVpnConsentRequest
import org.adaway.ui.common.updateErrorText
import org.adaway.ui.common.updateErrorTitle
import org.adaway.ui.common.vpnRefusal
import org.adaway.vpn.AlwaysOnMode

class HomeNavigation(val openSources: () -> Unit, val openLists: (RuleType) -> Unit, val openPreferences: () -> Unit)

@Composable
fun HomeRoute(isTelevision: Boolean, navigation: HomeNavigation) {
    val viewModel = appViewModel(::HomeViewModel)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val consentPrompt by viewModel.consentPrompt.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var refusal by remember { mutableStateOf<VpnRefusal?>(null) }
    var persistenceMode by remember { mutableStateOf<AlwaysOnMode?>(null) }
    val requestConsent = rememberVpnConsentRequest(
        onGranted = viewModel::enableBlocking,
        onRefused = { refusal = vpnRefusal(context) },
    )
    LaunchedEffect(consentPrompt) {
        if (consentPrompt) {
            viewModel.consentPromptShown()
            requestConsent()
        }
    }
    val current = state ?: return
    val actions = BlockingActions(
        toggle = { if (current.blocking.active) viewModel.disableBlocking() else requestConsent() },
        openPersistence = { persistenceMode = viewModel.alwaysOnMode() },
    )
    if (isTelevision) {
        TvHomeScreen(current, progress, viewModel, navigation, actions)
    } else {
        HomeScreen(
            state = current,
            progress = progress,
            outcome = outcome,
            actions = HomeScreenActions(
                openLists = navigation.openLists,
                openSources = navigation.openSources,
                openPreferences = navigation.openPreferences,
                sync = viewModel::sync,
                toggleBlocking = actions.toggle,
                openPersistence = actions.openPersistence,
                dismissLegacyImportFailure = viewModel::dismissLegacyImportFailure,
            ),
        )
    }
    error?.let { UpdateErrorDialog(it, onRetry = viewModel::sync, onDismiss = viewModel::dismissError) }
    refusal?.let { VpnRefusalDialog(it, onDismiss = { refusal = null }) }
    persistenceMode?.let { VpnPersistenceDialog(isTelevision, it, onDismiss = { persistenceMode = null }) }
}

/** The help screen does not exist yet: its button is grayed out. */
@Composable
private fun UpdateErrorDialog(error: UpdateError, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val canRetry = error != UpdateError.APPLY_FAILED
    AdAwayAlertDialog(
        onDismissRequest = onDismiss,
        icon = painterResource(android.R.drawable.ic_dialog_alert),
        title = updateErrorTitle(error),
        message = updateErrorText(error) + "\n\n" + stringResource(R.string.error_dialog_help),
        confirmButton = if (canRetry) {
            DialogButton(stringResource(R.string.retry_sync), onClick = { onDismiss(); onRetry() })
        } else {
            DialogButton(stringResource(R.string.close), onClick = onDismiss)
        },
        dismissButton = DialogButton(stringResource(R.string.help), onClick = {}, enabled = false),
    )
}
