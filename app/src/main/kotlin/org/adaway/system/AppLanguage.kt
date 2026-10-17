package org.adaway.system

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import java.util.Locale

/** Android lets each app have its own language since Android 13, picked in the system settings. */
val appLanguageSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/** The language picked for the app in the Android settings, null when it follows the system. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun Context.appLanguage(): Locale? = getSystemService(LocaleManager::class.java).applicationLocales[0]

/** Opens the Android settings page where the language of the app is picked. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun Context.openAppLanguageSettings() =
    startActivityOrTell(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", packageName, null)))
