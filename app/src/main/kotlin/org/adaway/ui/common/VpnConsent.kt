package org.adaway.ui.common

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.VpnService
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import org.adaway.R

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

/** Returns a function opening the system VPN settings, which some Android TV devices hide. */
@Composable
fun rememberOpenVpnSettings(): () -> Unit {
    val context = LocalContext.current
    return {
        try {
            context.startActivity(Intent(Settings.ACTION_VPN_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.vpn_settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }
}
