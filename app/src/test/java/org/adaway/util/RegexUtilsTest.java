package org.adaway.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * This class tests the host name and IP address validation of the user lists.
 */
public class RegexUtilsTest {
    @Test
    public void testHostnames() {
        assertTrue(RegexUtils.isValidHostname("example.com"));
        assertTrue(RegexUtils.isValidHostname("ads.example.co.uk"));
        assertFalse(RegexUtils.isValidHostname(""));
        assertFalse(RegexUtils.isValidHostname("exa mple.com"));
        assertFalse(RegexUtils.isValidHostname("*.example.com"));
    }

    @Test
    public void testWildcardHostnames() {
        assertTrue(RegexUtils.isValidWildcardHostname("*.example.com"));
        assertTrue(RegexUtils.isValidWildcardHostname("ad?.example.com"));
        assertTrue(RegexUtils.isValidWildcardHostname("example.com"));
        assertFalse(RegexUtils.isValidWildcardHostname("exa mple.*"));
    }

    @Test
    public void testIpAddresses() {
        assertTrue(RegexUtils.isValidIP("127.0.0.1"));
        assertTrue(RegexUtils.isValidIP("0.0.0.0"));
        assertTrue(RegexUtils.isValidIP("::1"));
        assertTrue(RegexUtils.isValidIP("2001:db8::ff00:42:8329"));
        assertFalse(RegexUtils.isValidIP(""));
        assertFalse(RegexUtils.isValidIP("256.0.0.1"));
        assertFalse(RegexUtils.isValidIP("example.com"));
        assertFalse(RegexUtils.isValidIP("1.2.3"));
    }
}
