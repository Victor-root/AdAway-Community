package org.adaway.ui

import android.content.Intent

/** The screens the launcher shortcuts and the notifications open, over the home screen. */
enum class Destination(val action: String) {
    PREFERENCES("org.adaway.action.OPEN_PREFERENCES"),
    DNS_LOG("org.adaway.action.OPEN_DNS_LOG"),
    LISTS("org.adaway.action.OPEN_LISTS"),
    UPDATE("org.adaway.action.OPEN_UPDATE"),
    ;

    companion object {
        fun from(intent: Intent?): Destination? = entries.firstOrNull { it.action == intent?.action }
    }
}
