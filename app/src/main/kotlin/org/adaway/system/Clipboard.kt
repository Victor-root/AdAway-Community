package org.adaway.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import org.adaway.R

fun Context.copyToClipboard(text: String) {
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(text, text))
}

/** Copies a host name and tells it, as the lists and the DNS log do. */
fun Context.copyHost(host: String) {
    copyToClipboard(host)
    Toast.makeText(this, R.string.host_copied, Toast.LENGTH_SHORT).show()
}
