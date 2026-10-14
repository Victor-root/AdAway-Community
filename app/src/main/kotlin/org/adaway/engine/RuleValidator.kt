package org.adaway.engine

/** Validates and canonicalizes the host names and addresses used by the blocking rules. */
interface RuleValidator {
    /** Returns the canonical host name, or null if [host] is not a valid host name or pattern. */
    fun normalizeHostname(host: String, allowWildcards: Boolean): String?

    /** Returns the canonical IPv4 or IPv6 address, or null if [address] is not one. */
    fun normalizeIpAddress(address: String): String?
}
