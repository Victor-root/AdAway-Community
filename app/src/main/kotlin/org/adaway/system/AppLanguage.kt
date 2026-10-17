package org.adaway.system

import android.app.Activity
import android.app.LocaleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import org.adaway.R
import org.xmlpull.v1.XmlPullParser
import java.util.Locale

/** Since Android 13, Android keeps the language of each app, picked in its settings. Before, the app keeps it. */
@ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
val systemKeepsAppLanguage = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

private const val LANGUAGE_PREFERENCES = "app_language"
private const val LANGUAGE_TAG = "tag"
private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"

/** The language picked for the app, null when it follows the system. */
fun Context.appLanguage(): Locale? =
    if (systemKeepsAppLanguage) {
        getSystemService(LocaleManager::class.java).applicationLocales[0]
    } else {
        storedLanguageTag()?.let(Locale::forLanguageTag)
    }

/** The languages the app is translated into, from the locale configuration generated at build time. */
fun Context.appLanguages(): List<Locale> = buildList {
    resources.getXml(R.xml._generated_res_locale_config).use { parser ->
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "locale") {
                add(Locale.forLanguageTag(parser.getAttributeValue(ANDROID_NAMESPACE, "name")))
            }
        }
    }
}

/** The name of the language in itself, as a language picker shows it. */
val Locale.ownName: String get() = getDisplayName(this).replaceFirstChar { it.titlecase(this) }

/** Opens the Android settings page where the language of the app is picked, false if the device has none, as some TVs. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun Context.openAppLanguageSettings(): Boolean = try {
    startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", packageName, null)))
    true
} catch (_: ActivityNotFoundException) {
    false
}

/**
 * Keeps [language] for the app, null to follow the system. From Android 13 Android shows the app
 * again in it; before, the app does, and the services started before keep their language until
 * they start again.
 */
fun Activity.setAppLanguage(language: Locale?) {
    if (systemKeepsAppLanguage) {
        getSystemService(LocaleManager::class.java).applicationLocales =
            language?.let { LocaleList.forLanguageTags(it.toLanguageTag()) } ?: LocaleList.getEmptyLocaleList()
        return
    }
    getSharedPreferences(LANGUAGE_PREFERENCES, Context.MODE_PRIVATE).edit {
        if (language == null) remove(LANGUAGE_TAG) else putString(LANGUAGE_TAG, language.toLanguageTag())
    }
    recreate()
}

/** Before Android 13, this context in the language picked for the app, for `attachBaseContext`. */
fun Context.withAppLanguage(): Context {
    if (systemKeepsAppLanguage) return this
    val language = storedLanguageTag()?.let(Locale::forLanguageTag)
    // Also what dates and numbers are formatted with, back to the system one when no longer picked.
    Locale.setDefault(language ?: Resources.getSystem().configuration.locales[0])
    if (language == null) return this
    return createConfigurationContext(Configuration(resources.configuration).apply { setLocale(language) })
}

private fun Context.storedLanguageTag(): String? =
    getSharedPreferences(LANGUAGE_PREFERENCES, Context.MODE_PRIVATE).getString(LANGUAGE_TAG, null)
