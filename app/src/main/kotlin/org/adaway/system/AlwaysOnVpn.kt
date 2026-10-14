package org.adaway.system

import android.content.Context
import android.provider.Settings

/** The app set as the system "Always-on VPN", as far as Android lets this app know. */
sealed interface AlwaysOnVpn {
    data object None : AlwaysOnVpn

    data class App(val packageName: String) : AlwaysOnVpn

    /** Android refused to tell. */
    data object Unknown : AlwaysOnVpn

    companion object {
        private const val ALWAYS_ON_VPN_APP = "always_on_vpn_app"

        fun read(context: Context): AlwaysOnVpn = try {
            Settings.Secure.getString(context.contentResolver, ALWAYS_ON_VPN_APP)
                ?.takeIf { it.isNotEmpty() }
                ?.let(::App)
                ?: None
        } catch (_: SecurityException) {
            Unknown
        }
    }
}
