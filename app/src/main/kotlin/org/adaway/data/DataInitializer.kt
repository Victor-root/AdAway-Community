package org.adaway.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.adaway.data.db.AppDatabase
import org.adaway.data.db.BuiltInSource
import org.adaway.data.db.SourceEntity
import org.adaway.data.db.SourceFormat
import org.adaway.data.legacy.LegacyAppData
import org.adaway.data.legacy.LegacyConversion
import org.adaway.data.settings.Settings
import org.adaway.data.settings.SettingsRepository

/**
 * Creates the app data on first launch, either from the previous app version installed in place
 * or from the defaults. Anything reading sources, rules or settings must await [ensureInitialized]
 * first, so that it never sees an empty, half-initialized state.
 */
class DataInitializer(
    private val database: AppDatabase,
    private val settingsRepository: SettingsRepository,
    private val legacyAppData: LegacyAppData,
    private val legacyConversion: LegacyConversion,
) {
    private val mutex = Mutex()

    suspend fun ensureInitialized() = mutex.withLock {
        withContext(Dispatchers.IO) {
            if (!settingsRepository.isDataInitialized()) initialize()
            // Removing the previous data only after the import is recorded keeps an interrupted
            // first launch from losing it, and this resumes the removal on the next launch.
            if (legacyAppData.exists()) legacyAppData.delete()
        }
    }

    private suspend fun initialize() {
        val hasLegacyData = legacyAppData.exists()
        val legacyDatabase = if (hasLegacyData) legacyAppData.readDatabase() else null
        val sources = legacyDatabase?.let { legacyConversion.convertSources(it.sources) } ?: defaultSources()
        val userRules = legacyDatabase?.let { legacyConversion.convertRules(it.userRules) }.orEmpty()
        database.withTransaction {
            database.sourceDao().deleteAll()
            database.userRuleDao().deleteAll()
            database.sourceDao().insertAll(sources)
            database.userRuleDao().insertAll(userRules)
        }
        val settings = if (hasLegacyData) {
            legacyConversion.convertSettings(legacyAppData.readPreferences())
        } else {
            Settings()
        }
        settingsRepository.completeDataInitialization(
            initial = settings,
            legacyImportFailed = legacyAppData.databaseExists() && legacyDatabase == null,
        )
    }

    private fun defaultSources() = BuiltInSource.entries.map { source ->
        SourceEntity(
            url = source.url,
            label = null,
            builtIn = source,
            format = SourceFormat.BLOCK,
            redirectsEnabled = false,
            enabled = source.enabledByDefault,
        )
    }
}
