package org.adaway.ui.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.adaway.R
import org.adaway.system.AlwaysOnVpn

/** Why Android refused the VPN consent, when it is worth explaining. */
enum class VpnRefusal { OTHER_ALWAYS_ON_VPN, UNKNOWN_CAUSE }

fun vpnRefusal(context: Context): VpnRefusal? = when (val alwaysOn = AlwaysOnVpn.read(context)) {
    is AlwaysOnVpn.App -> if (alwaysOn.packageName != context.packageName) VpnRefusal.OTHER_ALWAYS_ON_VPN else null
    AlwaysOnVpn.Unknown -> VpnRefusal.UNKNOWN_CAUSE
    AlwaysOnVpn.None -> null
}

/** Returns a function that asks Android for the VPN consent, or reports it right away if given. */
@Composable
fun rememberVpnConsentRequest(onGranted: () -> Unit, onRefused: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) onGranted() else onRefused()
    }
    return {
        val consentIntent = VpnService.prepare(context)
        if (consentIntent == null) {
            onGranted()
        } else {
            try {
                launcher.launch(consentIntent)
            } catch (_: ActivityNotFoundException) {
                onRefused()
            }
        }
    }
}

/**
 * Returns a function opening the system VPN settings, which some Android TV devices hide. It tells
 * [unavailableMessage] when they cannot be opened, and returns whether they could.
 */
@Composable
fun rememberOpenVpnSettings(@StringRes unavailableMessage: Int = R.string.vpn_settings_unavailable): () -> Boolean {
    val context = LocalContext.current
    return {
        try {
            context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, unavailableMessage, Toast.LENGTH_LONG).show()
            false
        }
    }
}

@Composable
fun VpnRefusalDialog(refusal: VpnRefusal, onDismiss: () -> Unit) {
    val openVpnSettings = rememberOpenVpnSettings()
    val text = when (refusal) {
        VpnRefusal.OTHER_ALWAYS_ON_VPN -> R.string.welcome_vpn_alwayson_description
        VpnRefusal.UNKNOWN_CAUSE -> R.string.welcome_vpn_alwayson_blocked_description
    }
    AdAwayAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.welcome_vpn_alwayson_title),
        message = stringResource(text),
        confirmButton = DialogButton(stringResource(R.string.welcome_vpn_alwayson_settings_action), onClick = { onDismiss(); openVpnSettings() }),
        dismissButton = DialogButton(stringResource(R.string.close), onClick = onDismiss),
    )
}
