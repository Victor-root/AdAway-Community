package org.adaway.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.adaway.R
import org.adaway.ui.common.rememberOpenVpnSettings
import org.adaway.vpn.AlwaysOnMode
import org.adaway.vpn.VpnStatus

/** The blocking actions of the home screens. [toggle] asks for the VPN consent when needed. */
class BlockingActions(val toggle: () -> Unit, val apply: () -> Unit, val openPersistence: () -> Unit)

/** On or off, and what is going on in between: waiting for a network, reconnecting, consent missing. */
@Composable
fun BlockingStatusContent(blocking: BlockingState, modifier: Modifier = Modifier) {
    @StringRes val detail = when (blocking.vpnStatus) {
        VpnStatus.STARTING -> R.string.home_blocking_starting
        VpnStatus.WAITING_FOR_NETWORK -> R.string.home_blocking_waiting_for_network
        VpnStatus.RECONNECTING -> R.string.home_blocking_reconnecting
        VpnStatus.CONSENT_REQUIRED -> R.string.home_blocking_consent_required
        VpnStatus.RUNNING, VpnStatus.STOPPED -> null
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(if (blocking.active) R.string.home_blocking_enabled else R.string.home_blocking_disabled),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        detail?.let {
            Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun VpnPersistenceContent(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(painterResource(R.drawable.ic_vpn_key), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(stringResource(R.string.vpn_persistence_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                stringResource(R.string.vpn_persistence_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Explains how the VPN stays on, and leads to the Android always-on VPN setting. On TV, where that
 * setting is often hidden, it also gives the ADB commands, and stays open if the setting cannot open.
 */
@Composable
fun VpnPersistenceDialog(isTelevision: Boolean, mode: AlwaysOnMode, onDismiss: () -> Unit) {
    val packageName = LocalContext.current.packageName
    val openVpnSettings = rememberOpenVpnSettings(
        if (isTelevision) R.string.vpn_persistence_tv_settings_unavailable else R.string.vpn_settings_unavailable,
    )
    @StringRes val status = when (mode) {
        AlwaysOnMode.ON, AlwaysOnMode.LOCKDOWN -> R.string.vpn_persistence_enabled
        AlwaysOnMode.OFF -> R.string.vpn_persistence_disabled
        AlwaysOnMode.UNKNOWN -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.vpn_persistence_title)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                status?.let { Text(stringResource(it), style = MaterialTheme.typography.titleSmall) }
                if (mode == AlwaysOnMode.LOCKDOWN) {
                    Text(stringResource(R.string.vpn_persistence_lockdown), color = MaterialTheme.colorScheme.error)
                }
                Text(stringResource(R.string.vpn_persistence_message))
                if (isTelevision) {
                    Text(stringResource(R.string.vpn_persistence_adb_commands, packageName), fontFamily = FontFamily.Monospace)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (openVpnSettings() || !isTelevision) onDismiss() }) {
                Text(stringResource(R.string.open_vpn_settings))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.vpn_persistence_dismiss)) } },
    )
}

/** Changes applied while ad blocking is off only take effect once it is on: offers to turn it on. */
@Composable
fun ApplyWhileOffDialog(onApply: () -> Unit, onApplyAndEnable: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.apply_blocking_off_title)) },
        text = { Text(stringResource(R.string.apply_blocking_off_text)) },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onApplyAndEnable() }) { Text(stringResource(R.string.apply_and_enable)) }
        },
        dismissButton = { TextButton(onClick = { onDismiss(); onApply() }) { Text(stringResource(R.string.apply_only)) } },
    )
}
