package org.adaway.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun comparesTheVersionNumbers() {
        assertTrue(AppVersion("7.0.0-c") > AppVersion("6.6.1-c"))
        assertTrue(AppVersion("6.10.0-c") > AppVersion("6.9.9-c"))
        assertEquals(0, AppVersion("7.0-c").compareTo(AppVersion("7.0.0-c")))
        assertEquals(0, AppVersion("v7.0.0").compareTo(AppVersion("7.0.0-c")))
    }

    @Test
    fun putsThePreReleasesBeforeTheirVersion() {
        assertTrue(AppVersion("7.0.0-beta1-c").isPreRelease)
        assertFalse(AppVersion("7.0.0-c").isPreRelease)
        assertTrue(AppVersion("7.0.0-beta2-c") > AppVersion("7.0.0-beta1-c"))
        assertTrue(AppVersion("7.0.0-c") > AppVersion("7.0.0-beta2-c"))
        assertTrue(AppVersion("7.0.0-beta1-c") > AppVersion("6.6.1-c"))
    }
}
