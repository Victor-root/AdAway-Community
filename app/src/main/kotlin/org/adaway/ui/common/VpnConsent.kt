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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    val (title, text) = when (refusal) {
        VpnRefusal.OTHER_ALWAYS_ON_VPN -> R.string.always_on_vpn_title to R.string.always_on_vpn_text
        VpnRefusal.UNKNOWN_CAUSE -> R.string.vpn_request_failed_title to R.string.vpn_request_failed_text
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(text)) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); openVpnSettings() }) { Text(stringResource(R.string.open_vpn_settings)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
