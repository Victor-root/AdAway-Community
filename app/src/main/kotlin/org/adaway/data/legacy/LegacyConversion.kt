package org.adaway.data.legacy

import org.adaway.data.db.RuleType
import org.adaway.data.db.SourceEntity
import org.adaway.data.db.SourceFormat
import org.adaway.data.db.UserRuleEntity
import org.adaway.data.settings.BlockingMethod
import org.adaway.data.settings.Settings
import org.adaway.data.settings.SystemAppExclusion
import org.adaway.data.settings.ThemeMode
import org.adaway.engine.RuleValidator
import org.adaway.sources.SourceRepository

/** A hosts source row of the previous app database (schema version 7). */
data class LegacySource(
    val label: String,
    val url: String,
    val enabled: Boolean,
    val allowFormat: Boolean,
    val redirectsEnabled: Boolean,
)

/** A user rule row of the previous app database, with its raw type code. */
data class LegacyUserRule(val host: String, val type: Int, val enabled: Boolean, val redirection: String?)

/** Converts the data of the previous app version (6.x) to the current model. */
class LegacyConversion(private val validator: RuleValidator) {

    fun convertSources(sources: List<LegacySource>): List<SourceEntity> =
        sources.mapNotNull { source ->
            val url = SourceRepository.normalizeUrl(source.url) ?: return@mapNotNull null
            val label = SourceRepository.cleanLabel(source.label).ifEmpty { url }
            val format = if (source.allowFormat) SourceFormat.ALLOW else SourceFormat.BLOCK
            SourceEntity(
                url = url,
                label = label,
                builtIn = null,
                format = format,
                redirectsEnabled = source.redirectsEnabled && format == SourceFormat.BLOCK,
                enabled = source.enabled,
            )
        }.distinctBy { it.url }

    /** Keeps one rule per host: an enabled rule wins over a disabled one, then the latest one wins. */
    fun convertRules(rules: List<LegacyUserRule>): List<UserRuleEntity> {
        val byHost = LinkedHashMap<String, UserRuleEntity>()
        for (rule in rules.mapNotNull(::convertRule)) {
            val existing = byHost[rule.host]
            if (existing == null || rule.enabled || !existing.enabled) {
                byHost[rule.host] = rule
            }
        }
        return byHost.values.toList()
    }

    fun convertSettings(preferences: Map<String, Any?>): Settings {
        val defaults = Settings()
        fun boolean(key: String, default: Boolean) = preferences[key] as? Boolean ?: default
        fun string(key: String) = preferences[key] as? String

        val blockingMethod = when (preferences["adBlockMethod"] as? Int) {
            1 -> BlockingMethod.ROOT
            2 -> BlockingMethod.VPN
            else -> BlockingMethod.UNSET
        }
        val legacyVpnStatus = preferences["vpnPaused"] as? Int ?: 0
        @Suppress("UNCHECKED_CAST")
        val excludedApps = (preferences["excludeUserAppFromVpn"] as? Set<String>).orEmpty()
        return Settings(
            blockingMethod = blockingMethod,
            welcomeCompleted = blockingMethod != BlockingMethod.UNSET,
            themeMode = when (string("darkThemeMode")) {
                "MODE_NIGHT_NO" -> ThemeMode.LIGHT
                "MODE_NIGHT_YES" -> ThemeMode.DARK
                "MODE_NIGHT_FOLLOW_SYSTEM" -> ThemeMode.SYSTEM
                else -> null
            },
            vpnWanted = boolean("vpnUserEnabled", legacyVpnStatus != 0),
            vpnStartOnBoot = boolean("vpnOnBoot", defaults.vpnStartOnBoot),
            vpnConnectionMonitor = boolean("vpnWatchdog", defaults.vpnConnectionMonitor),
            vpnSystemAppExclusion = when (string("excludeSystemAppFromVpn")) {
                "allExceptBrowsers" -> SystemAppExclusion.ALL_EXCEPT_BROWSERS
                "all" -> SystemAppExclusion.ALL
                else -> SystemAppExclusion.NONE
            },
            vpnExcludedApps = excludedApps,
            ipv6Enabled = boolean("enableIpv6", defaults.ipv6Enabled),
            rootRedirectionIpv4 = string("redirectionIP")?.let(::normalizeIpv4) ?: defaults.rootRedirectionIpv4,
            rootRedirectionIpv6 = string("redirectionIPv6")?.let(::normalizeIpv6) ?: defaults.rootRedirectionIpv6,
            webServerEnabled = boolean("webserverEnabled", defaults.webServerEnabled),
            webServerIcon = boolean("webserverIcon", defaults.webServerIcon),
            sourcesCheckOnStartup = boolean("updateCheck", defaults.sourcesCheckOnStartup),
            sourcesPeriodicCheck = boolean("updateCheckHostsDaily", defaults.sourcesPeriodicCheck),
            sourcesAutoSync = boolean("automaticUpdateDaily", defaults.sourcesAutoSync),
            sourcesUnmeteredOnly = boolean("updateOnlyOnWifi", defaults.sourcesUnmeteredOnly),
            appUpdateCheckOnStartup = boolean("updateCheckAppStartup", defaults.appUpdateCheckOnStartup),
            appUpdatePeriodicCheck = boolean("updateCheckAppDaily", defaults.appUpdatePeriodicCheck),
            diagnosticLogEnabled = boolean("vpnDiagnosticLogEnabled", defaults.diagnosticLogEnabled),
        )
    }

    private fun convertRule(rule: LegacyUserRule): UserRuleEntity? {
        val type = RULE_TYPES.getOrNull(rule.type) ?: return null
        val host = validator.normalizeHostname(rule.host, allowWildcards = type != RuleType.REDIRECTED)
            ?: return null
        val redirection = if (type == RuleType.REDIRECTED) {
            rule.redirection?.let(validator::normalizeIpAddress) ?: return null
        } else {
            null
        }
        return UserRuleEntity(host = host, type = type, redirection = redirection, enabled = rule.enabled)
    }

    private fun normalizeIpv4(address: String) = validator.normalizeIpAddress(address)?.takeUnless { ':' in it }

    private fun normalizeIpv6(address: String) = validator.normalizeIpAddress(address)?.takeIf { ':' in it }

    companion object {
        /** The rule types by code, in the previous app database and in its backups. */
        private val RULE_TYPES = listOf(RuleType.BLOCKED, RuleType.ALLOWED, RuleType.REDIRECTED)

        fun ruleTypeCode(type: RuleType): Int = RULE_TYPES.indexOf(type)
    }
}
