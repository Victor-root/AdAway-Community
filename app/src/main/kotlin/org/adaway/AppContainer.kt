package org.adaway

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import org.adaway.data.DataInitializer
import org.adaway.data.db.AppDatabase
import org.adaway.data.legacy.LegacyAppData
import org.adaway.data.legacy.LegacyConversion
import org.adaway.data.settings.SettingsRepository
import org.adaway.engine.Engine
import org.adaway.system.RootAccess

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** Holds the app-wide services, created once by [AdAwayApplication]. */
class AppContainer(context: Context) {
    val settingsRepository = SettingsRepository(context.settingsDataStore)
    val database = Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME).build()
    val dataInitializer = DataInitializer(
        database = database,
        settingsRepository = settingsRepository,
        legacyAppData = LegacyAppData(context),
        legacyConversion = LegacyConversion(Engine),
    )
    val rootAccess = RootAccess()
}
