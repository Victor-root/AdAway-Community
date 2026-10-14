package org.adaway.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class VpnLogicTest {
    private val routerV4 = InetAddress.getByName("192.168.1.1")
    private val routerV6 = InetAddress.getByName("fd00::1")
    private val otherV4 = InetAddress.getByName("192.168.1.2")

    @Test
    fun keepsIpv4ServersAndIpv6OnlyWhenEnabled() {
        val servers = listOf(routerV6, routerV4, otherV4)
        assertEquals(listOf(routerV4, otherV4), DnsServerSelection.select(servers, false, true, true).servers)
        assertEquals(servers, DnsServerSelection.select(servers, true, true, true).servers)
    }

    @Test
    fun keepsASingleIpv6Server() {
        val selection = DnsServerSelection.select(listOf(routerV6), ipv6Enabled = false, hasIpv4 = false, hasIpv6 = true)
        assertEquals(listOf(routerV6), selection.servers)
        assertFalse(selection.usesFallback)
    }

    @Test
    fun fallsBackToAReachablePublicServer() {
        val ipv4 = DnsServerSelection.select(emptyList(), ipv6Enabled = false, hasIpv4 = true, hasIpv6 = true)
        assertTrue(ipv4.usesFallback)
        assertEquals(listOf(InetAddress.getByName("1.1.1.1")), ipv4.servers)
        val ipv6Only = DnsServerSelection.select(emptyList(), ipv6Enabled = false, hasIpv4 = false, hasIpv6 = true)
        assertEquals(listOf(InetAddress.getByName("2606:4700:4700::1111")), ipv6Only.servers)
    }

    @Test
    fun spacesOutRepeatedRebuilds() {
        var now = 0L
        val throttle = ReconnectThrottle { now }
        assertEquals(0, throttle.nextDelay())
        assertEquals(1_000, throttle.nextDelay())
        assertEquals(2_000, throttle.nextDelay())
        repeat(10) { throttle.nextDelay() }
        assertEquals(128_000, throttle.nextDelay())
        now += 1_000_000
        assertEquals("stable for long enough", 0, throttle.nextDelay())
        assertEquals(1_000, throttle.nextDelay())
        throttle.reset()
        assertEquals(0, throttle.nextDelay())
    }

    @Test
    fun numbersVirtualServersAcrossFamilies() {
        val network = VirtualNetwork.avoiding(emptyList())
        assertEquals(InetAddress.getByName("192.0.2.1"), network.ipv4Address)
        assertEquals(InetAddress.getByName("2001:db8::1"), network.ipv6Address)
        assertEquals(InetAddress.getByName("192.0.2.2"), network.serverAddress(0, routerV4))
        assertEquals(InetAddress.getByName("2001:db8::3"), network.serverAddress(1, routerV6))
    }

    @Test
    fun avoidsTheRangesOfTheLocalNetwork() {
        val local = listOf(InetAddress.getByName("192.0.2.17") to 24, routerV6 to 64)
        assertEquals(InetAddress.getByName("198.51.100.1"), VirtualNetwork.avoiding(local).ipv4Address)
        val wide = listOf(InetAddress.getByName("192.0.0.5") to 16, InetAddress.getByName("198.51.100.53") to 32)
        assertEquals(InetAddress.getByName("203.0.113.1"), VirtualNetwork.avoiding(wide).ipv4Address)
    }
}
