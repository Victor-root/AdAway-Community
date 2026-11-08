package org.adaway.system

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes
import org.adaway.R

fun Context.copyToClipboard(text: String) {
    getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(text, text))
}

/** Copies a host name and tells it, as the lists and the DNS log do. */
fun Context.copyHost(host: String) = copyAndTell(host, R.string.host_copied)

/** Copies [text] and tells it with [copied]. */
fun Context.copyAndTell(text: String, @StringRes copied: Int) {
    copyToClipboard(text)
    Toast.makeText(this, copied, Toast.LENGTH_SHORT).show()
}
