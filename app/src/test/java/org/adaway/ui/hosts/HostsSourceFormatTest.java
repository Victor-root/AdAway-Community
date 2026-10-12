package org.adaway.ui.hosts;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * This class tests the hosts source host count formatting.
 */
public class HostsSourceFormatTest {
    @Test
    public void testCountsBelowAThousandAreExact() {
        assertEquals("5", HostsSourceFormat.getCompactCount(5));
        assertEquals("10", HostsSourceFormat.getCompactCount(10));
        assertEquals("999", HostsSourceFormat.getCompactCount(999));
    }

    @Test
    public void testThousandsUseKilo() {
        assertEquals("1k", HostsSourceFormat.getCompactCount(1000));
        assertEquals("1k", HostsSourceFormat.getCompactCount(1050));
        assertEquals("2k", HostsSourceFormat.getCompactCount(1500));
        assertEquals("100k", HostsSourceFormat.getCompactCount(99_999));
        assertEquals("123k", HostsSourceFormat.getCompactCount(123_456));
    }

    @Test
    public void testMillionsAndBillions() {
        assertEquals("1M", HostsSourceFormat.getCompactCount(1_234_567));
        assertEquals("2G", HostsSourceFormat.getCompactCount(Integer.MAX_VALUE));
    }
}
