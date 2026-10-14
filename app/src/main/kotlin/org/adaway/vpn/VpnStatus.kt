package org.adaway.vpn

enum class VpnStatus {
    STOPPED,
    STARTING,
    RUNNING,
    WAITING_FOR_NETWORK,
    RECONNECTING,

    /** Stopped because Android no longer lets the app create a VPN until the user agrees again. */
    CONSENT_REQUIRED,
    ;

    /** Whether the service runs, in the foreground, in this status. */
    fun isServiceAlive() = this == STARTING || this == RUNNING || this == WAITING_FOR_NETWORK || this == RECONNECTING
}
