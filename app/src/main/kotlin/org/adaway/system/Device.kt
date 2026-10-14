package org.adaway.system

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration

fun Context.isTelevision(): Boolean =
    getSystemService(UiModeManager::class.java)?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
