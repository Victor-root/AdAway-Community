package org.adaway.vpn

import android.content.pm.PackageManager
import android.net.Network
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import org.adaway.engine.Engine
import org.adaway.system.Notifications
import java.io.File
import java.io.IOException
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress

/** A tunnel created by the engine, filtering on its own thread until closed. */
class ActiveTunnel(val handle: Long, val network: Network, val selection: DnsUpstream) {
    private var thread: Thread? = null

    fun run(onExit: (Int) -> Unit) {
        thread = Thread({ onExit(Engine.runTunnel(handle)) }, "AdAway DNS filter").apply { start() }
    }

    /** Stops the filter, waits for its thread, and frees the engine resources. Call it once. */
    fun close() {
        Engine.stopTunnel(handle)
        thread?.join()
        Engine.destroyTunnel(handle)
    }
}

/** Creates the Android VPN interface and the engine tunnel behind it. */
class TunnelBuilder(private val service: VpnService, private val finalDirectory: File) {
    /**
     * Returns null when Android refuses the VPN because the user consent is missing. Throws an
     * [IOException] when the network cannot be used, and a [RuntimeException] for anything else.
     */
    fun build(
        network: Network,
        selection: DnsUpstream,
        virtualNetwork: VirtualNetwork,
        excludedPackages: Set<String>,
        monitor: Boolean,
    ): ActiveTunnel? {
        // Each server, with the descriptor of its socket, handed over to the engine which then closes it.
        val upstreams = when (selection) {
            is DnsUpstream.Servers -> connect(network, selection.servers)
            is DnsUpstream.Https -> listOf(startRelay(network, selection.url))
        }
        try {
            val servers = upstreams.map { it.first }
            val virtualAddresses = servers.mapIndexed { index, server ->
                checkNotNull(virtualNetwork.serverAddress(index, server).hostAddress)
            }
            val tun = configure(network, servers, virtualNetwork, excludedPackages).establish() ?: return null
            val handle = Engine.createTunnel(
                tunFd = tun.detachFd(),
                virtualAddresses = virtualAddresses.toTypedArray(),
                upstreamFds = upstreams.map { (_, descriptor) -> descriptor.detachFd() }.toIntArray(),
                finalDirectory = finalDirectory.path,
                monitor = monitor,
            )
            return ActiveTunnel(handle, network, selection)
        } finally {
            upstreams.forEach { (_, descriptor) -> descriptor.close() }
        }
    }

    /** Connects a socket outside the VPN to each server, leaving out the ones not reachable. */
    private fun connect(network: Network, servers: List<InetAddress>): List<Pair<InetAddress, ParcelFileDescriptor>> {
        val upstreams = servers.mapNotNull { server ->
            DatagramSocket().use { socket ->
                try {
                    if (!service.protect(socket)) throw IOException("Socket not protected from the VPN")
                    network.bindSocket(socket)
                    socket.connect(InetSocketAddress(server, DNS_PORT))
                    server to ParcelFileDescriptor.fromDatagramSocket(socket)
                } catch (_: IOException) {
                    null
                }
            }
        }
        if (upstreams.isEmpty()) throw IOException("No DNS server reachable")
        return upstreams
    }

    /**
     * Starts the relay to the DNS over HTTPS server, its name resolved by the network itself. The
     * virtual server takes the family of the address used first.
     */
    private fun startRelay(network: Network, url: String): Pair<InetAddress, ParcelFileDescriptor> {
        val host = Engine.dohHost(url) ?: throw IllegalArgumentException("Invalid DNS over HTTPS address")
        val addresses = network.getAllByName(host).toList()
        val descriptor = ParcelFileDescriptor.adoptFd(Engine.startDohRelay(url, addresses.mapNotNull { it.hostAddress }.toTypedArray()))
        return addresses.first() to descriptor
    }

    private fun configure(
        network: Network,
        servers: List<InetAddress>,
        virtualNetwork: VirtualNetwork,
        excludedPackages: Set<String>,
    ): VpnService.Builder {
        val builder = service.Builder()
            .setSession(SESSION_NAME)
            .setConfigureIntent(Notifications.openAppIntent(service))
            .setMtu(MTU)
            .allowFamily(OsConstants.AF_INET)
            .allowFamily(OsConstants.AF_INET6)
            .allowBypass()
            .setUnderlyingNetworks(arrayOf(network))
            .addAddress(virtualNetwork.ipv4Address, VirtualNetwork.IPV4_PREFIX_LENGTH)
        // Not metered by itself: Android then follows the network underneath.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
        if (servers.any { it !is Inet4Address }) {
            builder.addAddress(virtualNetwork.ipv6Address, VirtualNetwork.IPV6_PREFIX_LENGTH)
        }
        servers.forEachIndexed { index, server ->
            val address = virtualNetwork.serverAddress(index, server)
            builder.addDnsServer(address).addRoute(address, address.address.size * 8)
        }
        excludedPackages.forEach { packageName ->
            try {
                builder.addDisallowedApplication(packageName)
            } catch (_: PackageManager.NameNotFoundException) {
                // Uninstalled in the meantime.
            }
        }
        return builder
    }

    private companion object {
        const val SESSION_NAME = "AdAway"
        const val DNS_PORT = 53
        /** Large enough for the biggest DNS answers relayed (4096 bytes) to fit in one packet. */
        const val MTU = 8192
    }
}
