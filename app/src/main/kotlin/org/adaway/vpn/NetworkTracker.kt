package org.adaway.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Follows the networks other than VPNs, and tells which one Android would use by default.
 * Every change is handed to [dispatch], and the tracker must only be used from there.
 */
class NetworkTracker(
    context: Context,
    private val dispatch: (() -> Unit) -> Unit,
    private val onChange: () -> Unit,
) {
    private class Tracked(val capabilities: NetworkCapabilities?, val links: LinkProperties?)

    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val networks = LinkedHashMap<Network, Tracked>()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = dispatch {
            networks[network] = Tracked(capabilities, networks[network]?.links)
            onChange()
        }

        override fun onLinkPropertiesChanged(network: Network, links: LinkProperties) = dispatch {
            networks[network] = Tracked(networks[network]?.capabilities, links)
            onChange()
        }

        override fun onLost(network: Network) = dispatch {
            networks.remove(network)
            onChange()
        }
    }

    /**
     * Starts following, with the current default network known right away. A network lost from now
     * on is always reported, so reading it after registering never leaves a stale one.
     */
    fun start() {
        // The default request leaves VPNs out.
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        connectivity.registerNetworkCallback(request, callback)
        val active = connectivity.activeNetwork ?: return
        val capabilities = connectivity.getNetworkCapabilities(active) ?: return
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return
        networks.putIfAbsent(active, Tracked(capabilities, connectivity.getLinkProperties(active)))
    }

    fun stop() {
        try {
            connectivity.unregisterNetworkCallback(callback)
        } catch (_: IllegalArgumentException) {
            // Never registered.
        }
    }

    /**
     * The network Android prefers: a validated one first, then by type, Ethernet before Wi-Fi
     * before mobile data. Among equals, [preferred] is kept, so that the tunnel never moves for
     * nothing.
     */
    fun target(preferred: Network?): Network? {
        val ranked = networks.filterValues { it.capabilities != null && it.links != null }
            .mapValues { (_, tracked) -> rank(checkNotNull(tracked.capabilities)) }
        val best = ranked.values.maxOrNull() ?: return null
        return preferred?.takeIf { ranked[it] == best } ?: ranked.entries.first { it.value == best }.key
    }

    fun dnsServers(network: Network): List<InetAddress> = networks[network]?.links?.dnsServers.orEmpty()

    /** The prefixes used on [network], which the virtual addresses must not hide. */
    fun usedPrefixes(network: Network): List<Pair<InetAddress, Int>> {
        val links = networks[network]?.links ?: return emptyList()
        return links.linkAddresses.map { it.address to it.prefixLength } +
            links.dnsServers.map { it to it.address.size * 8 }
    }

    fun hasIpv4(): Boolean = globalAddresses().any { it is Inet4Address }

    fun hasIpv6(): Boolean = globalAddresses().any { it is Inet6Address }

    private fun globalAddresses(): List<InetAddress> = networks.values
        .flatMap { it.links?.linkAddresses.orEmpty() }
        .map { it.address }
        .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }

    private fun rank(capabilities: NetworkCapabilities): Int {
        val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val type = when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 3
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 2
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 1
            else -> 0
        }
        return (if (validated) 10 else 0) + type
    }
}
