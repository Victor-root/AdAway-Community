package org.adaway.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import org.adaway.R

/** Opens [intent], telling the user when no app of the device can, as a TV without a file picker. */
fun Context.startActivityOrTell(intent: Intent) = tellIfMissing { startActivity(intent) }

/** As [startActivityOrTell], through a launcher of the screen. */
fun <I> ActivityResultLauncher<I>.launchOrTell(input: I, context: Context) = context.tellIfMissing { launch(input) }

/** Opens the screen of the launcher, or runs [fallback] when no app of the device can. */
fun <I> ActivityResultLauncher<I>.launchOr(input: I, fallback: () -> Unit) {
    try {
        launch(input)
    } catch (_: ActivityNotFoundException) {
        fallback()
    }
}

private fun Context.tellIfMissing(start: () -> Unit) {
    try {
        start()
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, R.string.activity_unavailable, Toast.LENGTH_LONG).show()
    }
}
