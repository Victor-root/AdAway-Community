package org.adaway.data.legacy

import org.adaway.data.db.RuleType
import org.adaway.data.db.SourceFormat
import org.adaway.data.settings.BlockingMethod
import org.adaway.data.settings.Settings
import org.adaway.data.settings.SystemAppExclusion
import org.adaway.data.settings.ThemeMode
import org.adaway.engine.RuleValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyConversionTest {
    /** Simplified rules: lowercase names, wildcards only when allowed, dotted IPv4 or IPv6. */
    private val validator = object : RuleValidator {
        override fun normalizeHostname(host: String, allowWildcards: Boolean): String? {
            val name = host.trim().lowercase()
            val pattern = if (allowWildcards) Regex("[a-z0-9*?.-]+") else Regex("[a-z0-9.-]+")
            return name.takeIf { it.isNotEmpty() && pattern.matches(it) }
        }

        override fun normalizeIpAddress(address: String): String? =
            address.trim().lowercase().takeIf { Regex("[0-9.]+|[0-9a-f:]+").matches(it) && it.isNotEmpty() }
    }

    private val conversion = LegacyConversion(validator)

    @Test
    fun convertsSources() {
        val sources = conversion.convertSources(
            listOf(
                LegacySource("StevenBlack", "https://example.org/hosts", enabled = true, allowFormat = false, redirectsEnabled = true),
                LegacySource("Allow\nlist", "content://provider/allow", enabled = false, allowFormat = true, redirectsEnabled = true),
            ),
        )

        assertEquals(2, sources.size)
        val (block, allow) = sources
        assertEquals("StevenBlack", block.label)
        assertEquals(SourceFormat.BLOCK, block.format)
        assertTrue(block.redirectsEnabled)
        assertTrue(block.enabled)
        assertEquals("Allow list", allow.label)
        assertEquals(SourceFormat.ALLOW, allow.format)
        assertFalse("an allow list never redirects", allow.redirectsEnabled)
        assertFalse(allow.enabled)
        assertNull(block.builtIn)
    }

    @Test
    fun normalizesOrDropsSourceUrls() {
        val sources = conversion.convertSources(
            listOf(
                LegacySource("Upper case scheme", "HTTPS://example.org/Hosts", true, false, false),
                LegacySource("Duplicate", "https://example.org/Hosts", true, false, false),
                LegacySource("Plain HTTP", "http://example.org/hosts", true, false, false),
                LegacySource("Old file", "file://app/user/hosts", true, false, false),
                LegacySource("Empty", "https://", true, false, false),
                LegacySource("  ", "https://example.net/list", true, false, false),
            ),
        )

        assertEquals(listOf("https://example.org/Hosts", "https://example.net/list"), sources.map { it.url })
        assertEquals("a blank label falls back to the url", "https://example.net/list", sources[1].label)
    }

    @Test
    fun convertsUserRules() {
        val rules = conversion.convertRules(
            listOf(
                LegacyUserRule("Ads.Example.com", type = 0, enabled = true, redirection = null),
                LegacyUserRule("*.example.org", type = 1, enabled = true, redirection = null),
                LegacyUserRule("nas.lan", type = 2, enabled = false, redirection = " 192.168.1.10 "),
            ),
        )

        assertEquals(3, rules.size)
        assertEquals("ads.example.com", rules[0].host)
        assertEquals(RuleType.BLOCKED, rules[0].type)
        assertNull(rules[0].redirection)
        assertEquals(RuleType.ALLOWED, rules[1].type)
        assertEquals(RuleType.REDIRECTED, rules[2].type)
        assertEquals("192.168.1.10", rules[2].redirection)
        assertFalse(rules[2].enabled)
    }

    @Test
    fun dropsInvalidUserRules() {
        val rules = conversion.convertRules(
            listOf(
                LegacyUserRule("bad host!", type = 0, enabled = true, redirection = null),
                LegacyUserRule("*.wildcard.org", type = 2, enabled = true, redirection = "10.0.0.1"),
                LegacyUserRule("no-target.org", type = 2, enabled = true, redirection = null),
                LegacyUserRule("bad-target.org", type = 2, enabled = true, redirection = "nowhere"),
                LegacyUserRule("unknown-type.org", type = 7, enabled = true, redirection = null),
            ),
        )

        assertTrue(rules.isEmpty())
    }

    @Test
    fun keepsOneRulePerHost() {
        val rules = conversion.convertRules(
            listOf(
                LegacyUserRule("a.com", type = 0, enabled = true, redirection = null),
                LegacyUserRule("A.com", type = 1, enabled = false, redirection = null),
                LegacyUserRule("b.com", type = 0, enabled = false, redirection = null),
                LegacyUserRule("b.com", type = 1, enabled = true, redirection = null),
                LegacyUserRule("c.com", type = 0, enabled = true, redirection = null),
                LegacyUserRule("c.com", type = 1, enabled = true, redirection = null),
            ),
        )

        assertEquals(listOf("a.com", "b.com", "c.com"), rules.map { it.host })
        assertEquals("an enabled rule wins over a disabled one", RuleType.BLOCKED, rules[0].type)
        assertEquals("an enabled rule wins over a disabled one", RuleType.ALLOWED, rules[1].type)
        assertEquals("the latest enabled rule wins", RuleType.ALLOWED, rules[2].type)
    }

    @Test
    fun convertsSettings() {
        val settings = conversion.convertSettings(
            mapOf(
                "adBlockMethod" to 2,
                "darkThemeMode" to "MODE_NIGHT_YES",
                "vpnUserEnabled" to true,
                "vpnOnBoot" to false,
                "vpnWatchdog" to true,
                "excludeSystemAppFromVpn" to "allExceptBrowsers",
                "excludeUserAppFromVpn" to setOf("com.bank.app"),
                "enableIpv6" to true,
                "redirectionIP" to "0.0.0.0",
                "redirectionIPv6" to "::",
                "webserverEnabled" to true,
                "webserverIcon" to false,
                "updateCheck" to false,
                "updateCheckHostsDaily" to false,
                "automaticUpdateDaily" to false,
                "updateOnlyOnWifi" to true,
                "updateCheckAppStartup" to false,
                "updateCheckAppDaily" to false,
                "vpnDiagnosticLogEnabled" to true,
            ),
        )

        assertEquals(
            Settings(
                blockingMethod = BlockingMethod.VPN,
                welcomeCompleted = true,
                themeMode = ThemeMode.DARK,
                vpnWanted = true,
                vpnStartOnBoot = false,
                vpnConnectionMonitor = true,
                vpnSystemAppExclusion = SystemAppExclusion.ALL_EXCEPT_BROWSERS,
                vpnExcludedApps = setOf("com.bank.app"),
                ipv6Enabled = true,
                rootRedirectionIpv4 = "0.0.0.0",
                rootRedirectionIpv6 = "::",
                webServerEnabled = true,
                webServerIcon = false,
                sourcesCheckOnStartup = false,
                sourcesPeriodicCheck = false,
                sourcesAutoSync = false,
                sourcesUnmeteredOnly = true,
                appUpdateCheckOnStartup = false,
                appUpdatePeriodicCheck = false,
                diagnosticLogEnabled = true,
            ),
            settings,
        )
    }

    @Test
    fun usesDefaultsForMissingOrInvalidSettings() {
        val settings = conversion.convertSettings(
            mapOf(
                "adBlockMethod" to "1",
                "redirectionIP" to "::1",
                "redirectionIPv6" to "127.0.0.1",
                "vpnOnBoot" to "yes",
            ),
        )

        assertEquals(Settings(), settings)
    }

    @Test
    fun deducesTheVpnWishFromTheLegacyStatus() {
        assertTrue(conversion.convertSettings(mapOf("vpnPaused" to 11)).vpnWanted)
        assertFalse(conversion.convertSettings(mapOf("vpnPaused" to 0)).vpnWanted)
        assertFalse(conversion.convertSettings(mapOf("vpnPaused" to 11, "vpnUserEnabled" to false)).vpnWanted)
    }

    @Test
    fun marksTheWelcomeAsCompletedOnlyWithAMethod() {
        assertEquals(BlockingMethod.ROOT, conversion.convertSettings(mapOf("adBlockMethod" to 1)).blockingMethod)
        assertTrue(conversion.convertSettings(mapOf("adBlockMethod" to 1)).welcomeCompleted)
        assertFalse(conversion.convertSettings(mapOf("adBlockMethod" to 0)).welcomeCompleted)
    }
}
