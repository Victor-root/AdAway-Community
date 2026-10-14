package org.adaway.vpn

import java.net.Inet4Address
import java.net.InetAddress

/**
 * The addresses inside the tunnel: the tunnel itself is host 1, and each real DNS server gets a
 * virtual address, from host 2 on in the order of the list, both families sharing the numbering.
 * Only documentation ranges are used, so that no real host is ever hidden behind the tunnel.
 */
class VirtualNetwork private constructor(private val ipv4Prefix: ByteArray) {
    val ipv4Address: InetAddress = host(ipv4Prefix, 1)
    val ipv6Address: InetAddress = host(IPV6_PREFIX, 1)

    fun serverAddress(index: Int, server: InetAddress): InetAddress =
        host(if (server is Inet4Address) ipv4Prefix else IPV6_PREFIX, index + 2)

    companion object {
        const val IPV4_PREFIX_LENGTH = 24
        const val IPV6_PREFIX_LENGTH = 120

        private val IPV4_PREFIXES = listOf(
            byteArrayOf(192.toByte(), 0, 2, 0),
            byteArrayOf(198.toByte(), 51, 100, 0),
            byteArrayOf(203.toByte(), 0, 113, 0),
        )
        private val IPV6_PREFIX = InetAddress.getByName("2001:db8::").address

        /**
         * Picks the first IPv4 range that overlaps none of the [used] prefixes (address and prefix
         * length), so that a local network using one keeps working. Falls back to the first range.
         */
        fun avoiding(used: List<Pair<InetAddress, Int>>): VirtualNetwork {
            val usedIpv4 = used.filter { (address, _) -> address is Inet4Address }
            val prefix = IPV4_PREFIXES.firstOrNull { candidate ->
                usedIpv4.none { (address, length) -> overlaps(candidate, IPV4_PREFIX_LENGTH, address.address, length) }
            } ?: IPV4_PREFIXES.first()
            return VirtualNetwork(prefix)
        }

        /** Two prefixes overlap when they share the bits of the shorter one. */
        private fun overlaps(first: ByteArray, firstLength: Int, second: ByteArray, secondLength: Int): Boolean {
            val length = minOf(firstLength, secondLength).coerceIn(0, 32)
            if (length == 0) return true
            val mask = (-1L shl (32 - length)).toInt()
            return (toInt(first) and mask) == (toInt(second) and mask)
        }

        private fun toInt(bytes: ByteArray): Int =
            bytes.fold(0) { value, byte -> (value shl 8) or (byte.toInt() and 0xff) }

        private fun host(prefix: ByteArray, host: Int): InetAddress =
            InetAddress.getByAddress(prefix.copyOf().also { it[it.lastIndex] = host.toByte() })
    }
}
