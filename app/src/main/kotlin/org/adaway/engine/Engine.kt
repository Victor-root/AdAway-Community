package org.adaway.engine

/** Entry point of the Rust engine (native library `libadaway.so`). */
object Engine : RuleValidator {
    /** Error codes of [parseSource]. */
    const val PARSE_IO_ERROR = -1L
    const val PARSE_TOO_MANY_RULES = -2L
    const val PARSE_NO_RULES = -3L

    /** Verdict codes of [verdicts]. */
    const val VERDICT_ALLOW = 0
    const val VERDICT_BLOCK = 1
    const val VERDICT_REDIRECT = 2

    init {
        System.loadLibrary("adaway")
    }

    external override fun normalizeHostname(host: String, allowWildcards: Boolean): String?

    external override fun normalizeIpAddress(address: String): String?

    /**
     * Converts a downloaded list to a rule file, returning its rule count or a `PARSE_` error code.
     * The rule file is only replaced when the whole list could be read.
     */
    external fun parseSource(
        input: String,
        output: String,
        allowFormat: Boolean,
        redirects: Boolean,
        maxRules: Int,
    ): Long

    /**
     * Computes the final list from the rule files of the enabled sources (in priority order) and
     * the enabled user rules. Returns the blocked, allowed and redirected counts.
     * Throws a [RuntimeException] when the files cannot be read or written.
     */
    external fun compile(
        sourceFiles: Array<String>,
        userBlocked: Array<String>,
        userAllowed: Array<String>,
        userRedirectHosts: Array<String>,
        userRedirectAddresses: Array<String>,
        catalogDirectory: String,
        finalDirectory: String,
    ): LongArray

    /**
     * Writes the hosts file of the root blocking at [output] from the final list: the [header]
     * comment lines, the localhost entries, then each name. Blocked names point to [ipv4Redirection],
     * and also to [ipv6Redirection] when given.
     * Throws a [RuntimeException] when the files cannot be read or written.
     */
    external fun writeHosts(
        finalDirectory: String,
        output: String,
        header: Array<String>,
        ipv4Redirection: String,
        ipv6Redirection: String?,
    )

    /**
     * Writes a new certificate of the root web server at [certificatePath], and its private key at
     * [keyPath], both in PEM. Throws a [RuntimeException] when they cannot be written.
     */
    external fun generateWebServerCertificate(certificatePath: String, keyPath: String)

    /** Returns a page of the catalog lines whose host contains [filter] (`*` and `?` wildcards). */
    external fun queryCatalog(path: String, filter: String, offset: Int, limit: Int): Array<String>

    /**
     * Creates the DNS filter of the VPN, reading the final list. Takes the ownership of the tun and
     * socket descriptors, closed when the tunnel ends: one socket connected to a real DNS server per
     * virtual address. Returns a handle to free with [destroyTunnel].
     * Throws a [RuntimeException] when the final list cannot be read.
     */
    external fun createTunnel(
        tunFd: Int,
        virtualAddresses: Array<String>,
        upstreamFds: IntArray,
        finalDirectory: String,
        monitor: Boolean,
    ): Long

    /**
     * Filters until [stopTunnel], or until the tunnel or the network fails. Returns why: 0 when
     * stopped, 1 when the tunnel closed, 2 when the network became unreachable.
     */
    external fun runTunnel(handle: Long): Int

    /** Makes [runTunnel] return, from any thread. */
    external fun stopTunnel(handle: Long)

    /**
     * Loads the final list again, used by the tunnel from its next query.
     * Throws a [RuntimeException] when it cannot be read: the tunnel then keeps the previous one.
     */
    external fun reloadTunnelRules(handle: Long)

    /** Frees a handle whose tunnel is not running. */
    external fun destroyTunnel(handle: Long)

    /** Whether the VPN tunnels note the names asked for, for the DNS log. Off when the process starts. */
    external fun setQueryLogRecording(recording: Boolean)

    external fun isQueryLogRecording(): Boolean

    /** The names noted by the VPN tunnels, each once, in the order first asked. */
    external fun queryLogNames(): Array<String>

    external fun clearQueryLog()

    /**
     * What the final list in [finalDirectory] decides for each of [names], as `VERDICT_` codes.
     * Throws a [RuntimeException] when it cannot be read.
     */
    external fun verdicts(finalDirectory: String, names: Array<String>): IntArray
}
