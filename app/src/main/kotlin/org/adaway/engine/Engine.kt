package org.adaway.engine

/** Entry point of the Rust engine (native library `libadaway.so`). */
object Engine : RuleValidator {
    init {
        System.loadLibrary("adaway")
    }

    external override fun normalizeHostname(host: String, allowWildcards: Boolean): String?

    external override fun normalizeIpAddress(address: String): String?
}
