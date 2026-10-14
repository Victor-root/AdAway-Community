package org.adaway.ui

import android.content.Intent

/** The launcher shortcuts, each opening a screen over the home screen. */
enum class Shortcut(private val action: String) {
    PREFERENCES("org.adaway.action.OPEN_PREFERENCES"),
    DNS_LOG("org.adaway.action.OPEN_DNS_LOG"),
    LISTS("org.adaway.action.OPEN_LISTS"),
    ;

    companion object {
        fun from(intent: Intent?): Shortcut? = entries.firstOrNull { it.action == intent?.action }
    }
}
