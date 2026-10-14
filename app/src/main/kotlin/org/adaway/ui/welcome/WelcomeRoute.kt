package org.adaway.ui.welcome

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.adaway.R
import org.adaway.ui.common.AdAwayAlertDialog
import org.adaway.ui.common.DialogButton
import org.adaway.ui.common.VpnRefusalDialog
import org.adaway.ui.common.appViewModel
import org.adaway.ui.common.rememberVpnConsentRequest
import org.adaway.ui.common.vpnRefusal

/**
 * First launch wizard logic shared by the phone and TV screens: the root check, the VPN consent and
 * their dialogs. [screen] draws the wizard of the device.
 */
@Composable
fun WelcomeRoute(onFinished: () -> Unit, screen: @Composable (WelcomeState, WelcomeViewModel, WelcomeActions) -> Unit) {
    val viewModel = appViewModel(::WelcomeViewModel)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requestVpnConsent = rememberVpnConsentRequest(
        onGranted = viewModel::onVpnGranted,
        onRefused = { viewModel.onVpnRefused(vpnRefusal(context)) },
    )
    screen(
        state,
        viewModel,
        WelcomeActions(chooseRoot = viewModel::chooseRoot, chooseVpn = requestVpnConsent, finish = { viewModel.finish(onFinished) }),
    )
    if (state.rootRequired) RootRequiredDialog(onDismiss = viewModel::dismissDialog)
    state.vpnRefusal?.let { VpnRefusalDialog(it, onDismiss = viewModel::dismissDialog) }
}

class WelcomeActions(val chooseRoot: () -> Unit, val chooseVpn: () -> Unit, val finish: () -> Unit)

@Composable
private fun RootRequiredDialog(onDismiss: () -> Unit) {
    AdAwayAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.welcome_root_missing_title),
        message = stringResource(R.string.welcome_root_missile_description),
        confirmButton = DialogButton(stringResource(R.string.close), onClick = onDismiss),
    )
}
