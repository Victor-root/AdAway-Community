package org.adaway.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri

/** Read access to the documents picked as list sources, kept across reboots. */
object DocumentAccess {
    fun keep(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // The provider does not offer lasting access: the file can still be read until a reboot.
        }
    }

    fun release(context: Context, url: String) {
        if (!url.startsWith("content://")) return
        try {
            context.contentResolver.releasePersistableUriPermission(url.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // The access was already gone.
        }
    }
}
