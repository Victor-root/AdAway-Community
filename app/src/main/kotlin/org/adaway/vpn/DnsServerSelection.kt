package org.adaway.vpn

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/** Where the tunnel relays the queries it lets through. */
sealed interface DnsUpstream {
    /** Plain DNS servers, and whether they are the public fallback of a network without usable ones. */
    data class Servers(val servers: List<InetAddress>, val usesFallback: Boolean) : DnsUpstream

    /** A DNS over HTTPS server. */
    data class Https(val url: String) : DnsUpstream
}

object DnsServerSelection {
    private val FALLBACK_IPV4: InetAddress = InetAddress.getByAddress(byteArrayOf(1, 1, 1, 1))
    private val FALLBACK_IPV6: InetAddress = InetAddress.getByAddress(
        byteArrayOf(0x26, 0x06, 0x47, 0, 0x47, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x11, 0x11),
    )

    /**
     * Keeps the IPv4 servers of the network, and its IPv6 ones when IPv6 is enabled or when it is
     * the network's only server. Never returns an empty list, which would leave apps without any
     * resolver: the public fallback is then one the device can reach.
     */
    fun select(
        networkServers: List<InetAddress>,
        ipv6Enabled: Boolean,
        hasIpv4: Boolean,
        hasIpv6: Boolean,
    ): DnsUpstream.Servers {
        val ipv4 = networkServers.filterIsInstance<Inet4Address>()
        val ipv6 = networkServers.filterIsInstance<Inet6Address>()
        val onlyServerIsIpv6 = networkServers.size == 1 && ipv6.size == 1
        val selected = networkServers.filter { it in ipv4 || ((ipv6Enabled || onlyServerIsIpv6) && it in ipv6) }
        if (selected.isNotEmpty()) return DnsUpstream.Servers(selected, usesFallback = false)
        val fallback = if (hasIpv6 && !hasIpv4) FALLBACK_IPV6 else FALLBACK_IPV4
        return DnsUpstream.Servers(listOf(fallback), usesFallback = true)
    }
}
