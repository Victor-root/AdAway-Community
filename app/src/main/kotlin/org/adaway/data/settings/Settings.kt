package org.adaway.data.settings

enum class BlockingMethod { UNSET, ROOT, VPN }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class SystemAppExclusion { NONE, ALL_EXCEPT_BROWSERS, ALL }

/** Where the VPN sends the queries it lets through: the servers of the network, chosen ones, or one over HTTPS. */
enum class VpnDnsMode { NETWORK, CUSTOM, HTTPS }

/** Well-known DNS over HTTPS servers, by name. */
enum class DohServer(val label: String, val url: String) {
    CLOUDFLARE("Cloudflare", "https://cloudflare-dns.com/dns-query"),
    GOOGLE("Google", "https://dns.google/dns-query"),
    QUAD9("Quad9", "https://dns.quad9.net/dns-query"),
}

/** User settings. Every default here is also the default of the previous app versions. */
data class Settings(
    val blockingMethod: BlockingMethod = BlockingMethod.UNSET,
    val welcomeCompleted: Boolean = false,
    /** Null until the user picks a theme: the default then depends on the device (dark on TV). */
    val themeMode: ThemeMode? = null,
    /** Whether the user wants the VPN running, as opposed to its actual state. */
    val vpnWanted: Boolean = false,
    val vpnStartOnBoot: Boolean = true,
    val vpnConnectionMonitor: Boolean = false,
    val vpnSystemAppExclusion: SystemAppExclusion = SystemAppExclusion.NONE,
    val vpnExcludedApps: Set<String> = emptySet(),
    val vpnDnsMode: VpnDnsMode = VpnDnsMode.NETWORK,
    /** The addresses of the chosen DNS servers, in order. */
    val vpnCustomDnsServers: List<String> = emptyList(),
    val vpnDohUrl: String = DohServer.CLOUDFLARE.url,
    val ipv6Enabled: Boolean = false,
    val rootRedirectionIpv4: String = "127.0.0.1",
    val rootRedirectionIpv6: String = "::1",
    val webServerEnabled: Boolean = false,
    val webServerIcon: Boolean = true,
    val sourcesCheckOnStartup: Boolean = true,
    val sourcesPeriodicCheck: Boolean = true,
    val sourcesAutoSync: Boolean = true,
    val sourcesUnmeteredOnly: Boolean = false,
    val appUpdateCheckOnStartup: Boolean = true,
    val appUpdatePeriodicCheck: Boolean = true,
    val diagnosticLogEnabled: Boolean = false,
)
