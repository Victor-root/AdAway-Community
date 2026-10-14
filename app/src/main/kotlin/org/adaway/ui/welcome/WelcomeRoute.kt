package org.adaway.ui.welcome

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.adaway.R
import org.adaway.system.AlwaysOnVpn
import org.adaway.ui.common.appViewModel
import org.adaway.ui.common.rememberOpenVpnSettings
import org.adaway.ui.common.rememberVpnConsentRequest

/** First launch wizard: lets the user pick and grant a blocking method. */
@Composable
fun WelcomeRoute(isTelevision: Boolean, onFinished: () -> Unit) {
    val viewModel = appViewModel(::WelcomeViewModel)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requestVpnConsent = rememberVpnConsentRequest(
        onGranted = viewModel::onVpnGranted,
        onRefused = { viewModel.onVpnRefused(AlwaysOnVpn.read(context), context.packageName) },
    )
    val actions = WelcomeActions(
        chooseRoot = viewModel::chooseRoot,
        chooseVpn = requestVpnConsent,
        finish = { viewModel.finish(onFinished) },
    )
    if (isTelevision) TvWelcomeScreen(state, actions) else WelcomeScreen(state, actions)
    state.dialog?.let { WelcomeDialog(it, onDismiss = viewModel::dismissDialog) }
}

class WelcomeActions(val chooseRoot: () -> Unit, val chooseVpn: () -> Unit, val finish: () -> Unit)

@Composable
private fun WelcomeDialog(dialog: WelcomeDialog, onDismiss: () -> Unit) {
    val openVpnSettings = rememberOpenVpnSettings()
    val (title, text) = when (dialog) {
        WelcomeDialog.ROOT_REQUIRED -> R.string.root_required_title to R.string.root_required_text
        WelcomeDialog.ALWAYS_ON_VPN -> R.string.always_on_vpn_title to R.string.always_on_vpn_text
        WelcomeDialog.VPN_REQUEST_FAILED -> R.string.vpn_request_failed_title to R.string.vpn_request_failed_text
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(text)) },
        confirmButton = {
            if (dialog != WelcomeDialog.ROOT_REQUIRED) {
                TextButton(onClick = { onDismiss(); openVpnSettings() }) {
                    Text(stringResource(R.string.open_vpn_settings))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}
