package org.adaway.engine

/** Entry point of the Rust engine (native library `libadaway.so`). */
object Engine : RuleValidator {
    /** Error codes of [parseSource]. */
    const val PARSE_IO_ERROR = -1L
    const val PARSE_TOO_MANY_RULES = -2L
    const val PARSE_NO_RULES = -3L

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

    /** Returns a page of the catalog lines whose host contains [filter] (`*` and `?` wildcards). */
    external fun queryCatalog(path: String, filter: String, offset: Int, limit: Int): Array<String>
}
