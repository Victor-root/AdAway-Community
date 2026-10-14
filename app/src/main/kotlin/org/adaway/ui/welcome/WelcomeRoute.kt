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
import org.adaway.ui.common.VpnRefusalDialog
import org.adaway.ui.common.appViewModel
import org.adaway.ui.common.rememberVpnConsentRequest
import org.adaway.ui.common.vpnRefusal

/** First launch wizard: lets the user pick and grant a blocking method. */
@Composable
fun WelcomeRoute(isTelevision: Boolean, onFinished: () -> Unit) {
    val viewModel = appViewModel(::WelcomeViewModel)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val requestVpnConsent = rememberVpnConsentRequest(
        onGranted = viewModel::onVpnGranted,
        onRefused = { viewModel.onVpnRefused(vpnRefusal(context)) },
    )
    val actions = WelcomeActions(
        chooseRoot = viewModel::chooseRoot,
        chooseVpn = requestVpnConsent,
        finish = { viewModel.finish(onFinished) },
    )
    if (isTelevision) TvWelcomeScreen(state, actions) else WelcomeScreen(state, actions)
    if (state.rootRequired) RootRequiredDialog(onDismiss = viewModel::dismissDialog)
    state.vpnRefusal?.let { VpnRefusalDialog(it, onDismiss = viewModel::dismissDialog) }
}

class WelcomeActions(val chooseRoot: () -> Unit, val chooseVpn: () -> Unit, val finish: () -> Unit)

@Composable
private fun RootRequiredDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.root_required_title)) },
        text = { Text(stringResource(R.string.root_required_text)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
