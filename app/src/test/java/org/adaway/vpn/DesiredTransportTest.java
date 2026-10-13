package org.adaway.vpn;

import static org.adaway.vpn.VpnService.NetworkType.CELLULAR;
import static org.adaway.vpn.VpnService.NetworkType.ETHERNET;
import static org.adaway.vpn.VpnService.NetworkType.WIFI;
import static org.adaway.vpn.VpnService.computeDesiredTransport;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * This class tests the choice of the network the VPN tunnel is built for, which must follow the
 * default network Android routes the traffic through.
 */
public class DesiredTransportTest {
    @Test
    public void testNoNetwork() {
        assertNull(computeDesiredTransport(false, false, false, false));
    }

    @Test
    public void testEthernetIsPreferred() {
        assertEquals(ETHERNET, computeDesiredTransport(true, false, false, false));
        assertEquals(ETHERNET, computeDesiredTransport(true, true, true, false));
        assertEquals(ETHERNET, computeDesiredTransport(true, true, true, true));
    }

    @Test
    public void testWifiIsPreferredOnceValidated() {
        assertEquals(WIFI, computeDesiredTransport(false, true, true, true));
        assertEquals(CELLULAR, computeDesiredTransport(false, true, false, true));
    }

    @Test
    public void testSingleNetwork() {
        assertEquals(WIFI, computeDesiredTransport(false, true, false, false));
        assertEquals(CELLULAR, computeDesiredTransport(false, false, false, true));
    }
}
