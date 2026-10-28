package org.adaway.system

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import org.adaway.R

/**
 * The package of Android TV that answers for the apps a TV lacks, such as the file picker, only
 * to tell "You don't have an app that can do this".
 */
private const val TV_STUBS_PACKAGE = "com.android.tv.frameworkpackagestubs"

/** Opens [intent], telling the user when no app of the device can, as a TV without a file picker. */
fun Context.startActivityOrTell(intent: Intent) {
    if (!open(intent) { startActivity(intent) }) tellUnavailable()
}

/** As [startActivityOrTell], through a launcher of the screen. */
fun <I> ActivityResultLauncher<I>.launchOrTell(input: I, context: Context) = launchOr(input, context, context::tellUnavailable)

/** Opens the screen of the launcher, or runs [fallback] when no app of the device can. */
fun <I> ActivityResultLauncher<I>.launchOr(input: I, context: Context, fallback: () -> Unit) {
    if (!context.open(contract.createIntent(context, input)) { launch(input) }) fallback()
}

/** Runs [start] when an app of the device, other than the stubs of Android TV, can open [intent]. */
private fun Context.open(intent: Intent, start: () -> Unit): Boolean {
    val handled = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        .any { it.activityInfo.packageName != TV_STUBS_PACKAGE }
    if (!handled) return false
    return try {
        start()
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

private fun Context.tellUnavailable() {
    Toast.makeText(this, R.string.activity_unavailable, Toast.LENGTH_LONG).show()
}
