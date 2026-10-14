package org.adaway.ui.home

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import org.adaway.R
import org.adaway.ui.common.AdAwayAlertDialog
import org.adaway.ui.common.DialogButton
import org.adaway.ui.common.rememberOpenVpnSettings
import org.adaway.ui.theme.LocalTelevision
import org.adaway.vpn.AlwaysOnMode
import org.adaway.vpn.VpnStatus

/** The blocking actions of the home screens. [toggle] asks for the VPN consent when needed. */
class BlockingActions(val toggle: () -> Unit, val openPersistence: () -> Unit)

/** What goes on between on and off: starting, waiting for a network, reconnecting, consent missing. */
@StringRes
fun blockingDetail(status: VpnStatus): Int? = when (status) {
    VpnStatus.STARTING -> R.string.home_blocking_starting
    VpnStatus.WAITING_FOR_NETWORK -> R.string.home_blocking_waiting_for_network
    VpnStatus.RECONNECTING -> R.string.home_blocking_reconnecting
    VpnStatus.CONSENT_REQUIRED -> R.string.home_blocking_consent_required
    VpnStatus.RUNNING, VpnStatus.STOPPED -> null
}

/**
 * Explains how the VPN stays on, and leads to the Android always-on VPN setting. On TV, where that
 * setting is often hidden, it also gives the ADB commands, and stays open if the setting cannot open.
 */
@Composable
fun VpnPersistenceDialog(mode: AlwaysOnMode, onDismiss: () -> Unit) {
    val isTelevision = LocalTelevision.current
    val packageName = LocalContext.current.packageName
    val openVpnSettings = rememberOpenVpnSettings(
        if (isTelevision) R.string.vpn_persistence_tv_settings_unavailable else R.string.vpn_settings_unavailable,
    )
    val paragraphs = listOfNotNull(
        when (mode) {
            AlwaysOnMode.ON, AlwaysOnMode.LOCKDOWN -> stringResource(R.string.vpn_persistence_enabled)
            AlwaysOnMode.OFF -> stringResource(R.string.vpn_persistence_disabled)
            AlwaysOnMode.UNKNOWN -> null
        },
        if (mode == AlwaysOnMode.LOCKDOWN) stringResource(R.string.vpn_persistence_lockdown) else null,
        stringResource(R.string.vpn_persistence_message),
        if (isTelevision) stringResource(R.string.vpn_persistence_adb_commands, packageName) else null,
    )
    AdAwayAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.vpn_persistence_title),
        message = paragraphs.joinToString("\n\n"),
        confirmButton = DialogButton(stringResource(R.string.open_vpn_settings), onClick = {
            if (openVpnSettings() || !isTelevision) onDismiss()
        }),
        dismissButton = DialogButton(stringResource(R.string.vpn_persistence_dismiss), onClick = onDismiss),
    )
}
