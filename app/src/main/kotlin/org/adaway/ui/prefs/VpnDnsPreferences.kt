package org.adaway.ui.prefs

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import org.adaway.R
import org.adaway.data.settings.DohServer
import org.adaway.data.settings.Settings
import org.adaway.data.settings.VpnDnsMode
import org.adaway.engine.Engine

private enum class DnsDialog { MODE, CUSTOM_SERVERS, DOH_SERVER, DOH_ADDRESS }

/** Between the addresses the user types. */
private val ADDRESS_SEPARATORS = Regex("[,\\s]+")

/**
 * Where the VPN sends the queries it lets through: the servers of the network, chosen ones, or a
 * DNS over HTTPS server, well known or by its address. The rows of the other modes are grayed out.
 */
@Composable
fun VpnDnsPreferences(settings: Settings, enabled: Boolean, update: ((Settings) -> Settings) -> Unit) {
    val context = LocalContext.current
    var dialog by rememberSaveable { mutableStateOf<DnsDialog?>(null) }
    val modes = stringArrayResource(R.array.pref_vpn_dns_modes).toList()
    val customDoh = stringResource(R.string.pref_vpn_doh_custom)
    val invalidServers = stringResource(R.string.pref_vpn_dns_custom_invalid)
    val invalidDoh = stringResource(R.string.pref_vpn_doh_invalid)
    val dohServer = DohServer.entries.firstOrNull { it.url == settings.vpnDohUrl }
    Preference(
        R.drawable.ic_dns_24dp,
        stringResource(R.string.pref_vpn_dns),
        summary = modes[VpnDnsMode.entries.indexOf(settings.vpnDnsMode)],
        enabled = enabled,
        onClick = { dialog = DnsDialog.MODE },
    )
    Preference(
        null,
        stringResource(R.string.pref_vpn_dns_custom),
        summary = settings.vpnCustomDnsServers.joinToString(", ").ifEmpty { stringResource(R.string.pref_vpn_dns_custom_summary) },
        enabled = enabled && settings.vpnDnsMode == VpnDnsMode.CUSTOM,
        onClick = { dialog = DnsDialog.CUSTOM_SERVERS },
    )
    Preference(
        null,
        stringResource(R.string.pref_vpn_doh),
        summary = dohServer?.label ?: settings.vpnDohUrl,
        enabled = enabled && settings.vpnDnsMode == VpnDnsMode.HTTPS,
        onClick = { dialog = DnsDialog.DOH_SERVER },
    )
    when (dialog) {
        DnsDialog.MODE -> ChoiceDialog(
            title = stringResource(R.string.pref_vpn_dns),
            choices = modes,
            selected = VpnDnsMode.entries.indexOf(settings.vpnDnsMode),
            onSelect = { index -> update { it.copy(vpnDnsMode = VpnDnsMode.entries[index]) } },
            onDismiss = { dialog = null },
        )
        DnsDialog.CUSTOM_SERVERS -> TextInputDialog(
            title = stringResource(R.string.pref_vpn_dns_custom),
            initialValue = settings.vpnCustomDnsServers.joinToString(", "),
            onConfirm = { text ->
                val addresses = text.split(ADDRESS_SEPARATORS).filter { it.isNotEmpty() }.map(Engine::normalizeIpAddress)
                if (addresses.isEmpty() || null in addresses) {
                    Toast.makeText(context, invalidServers, Toast.LENGTH_SHORT).show()
                } else {
                    update { it.copy(vpnCustomDnsServers = addresses.filterNotNull().distinct()) }
                }
            },
            onDismiss = { dialog = null },
        )
        DnsDialog.DOH_SERVER -> ChoiceDialog(
            title = stringResource(R.string.pref_vpn_doh),
            choices = DohServer.entries.map { it.label } + customDoh,
            selected = dohServer?.ordinal ?: DohServer.entries.size,
            onSelect = { index ->
                val server = DohServer.entries.getOrNull(index)
                if (server == null) dialog = DnsDialog.DOH_ADDRESS else update { it.copy(vpnDohUrl = server.url) }
            },
            // Choosing the custom address opens its own dialog.
            onDismiss = { if (dialog == DnsDialog.DOH_SERVER) dialog = null },
        )
        DnsDialog.DOH_ADDRESS -> TextInputDialog(
            title = stringResource(R.string.pref_vpn_doh_address),
            initialValue = settings.vpnDohUrl,
            onConfirm = { text ->
                val url = text.trim()
                if (Engine.dohHost(url) == null) {
                    Toast.makeText(context, invalidDoh, Toast.LENGTH_SHORT).show()
                } else {
                    update { it.copy(vpnDohUrl = url) }
                }
            },
            onDismiss = { dialog = null },
        )
        null -> {}
    }
}
