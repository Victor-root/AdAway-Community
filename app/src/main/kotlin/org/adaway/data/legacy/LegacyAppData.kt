package org.adaway.data.legacy

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.work.WorkManager
import org.adaway.BuildConfig
import java.io.File

class LegacyDatabase(val sources: List<LegacySource>, val userRules: List<LegacyUserRule>)

/** Data left on the device by the previous app version (6.x), which this version replaces in place. */
class LegacyAppData(private val context: Context) {
    private val preferencesFile = File(context.dataDir, "shared_prefs/$PREFERENCES_NAME.xml")
    private val databaseFile = context.getDatabasePath(DATABASE_NAME)

    fun exists(): Boolean = preferencesFile.exists() || databaseFile.exists()

    fun databaseExists(): Boolean = databaseFile.exists()

    fun readPreferences(): Map<String, Any?> =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).all

    /** Returns null when there is no database, or when it is not a readable version 7 database. */
    fun readDatabase(): LegacyDatabase? {
        if (!databaseFile.exists()) return null
        return try {
            // Opened writable so that SQLite can replay a write-ahead log left by a killed process.
            SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
                if (database.version != SUPPORTED_DATABASE_VERSION) return null
                LegacyDatabase(readSources(database), readUserRules(database))
            }
        } catch (exception: SQLiteException) {
            if (BuildConfig.DEBUG) Log.w(TAG, "Failed to read the previous app database", exception)
            null
        }
    }

    /** Removes everything the previous version stored. Safe to call again after an interruption. */
    fun delete() {
        LEGACY_WORK_NAMES.forEach(WorkManager.getInstance(context)::cancelUniqueWork)
        context.deleteSharedPreferences(PREFERENCES_NAME)
        context.deleteDatabase(DATABASE_NAME)
        LEGACY_FILES.forEach { File(context.filesDir, it).deleteRecursively() }
        context.cacheDir.listFiles()?.forEach(File::deleteRecursively)
        context.externalCacheDir?.let { File(it, LEGACY_UPDATE_APK).delete() }
    }

    private fun readSources(database: SQLiteDatabase): List<LegacySource> =
        database.rawQuery(
            "SELECT label, url, enabled, allowEnabled, redirectEnabled FROM hosts_sources " +
                "WHERE id != $USER_SOURCE_ID ORDER BY id",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        LegacySource(
                            label = cursor.getString(0),
                            url = cursor.getString(1),
                            enabled = cursor.getInt(2) != 0,
                            allowFormat = cursor.getInt(3) != 0,
                            redirectsEnabled = cursor.getInt(4) != 0,
                        ),
                    )
                }
            }
        }

    private fun readUserRules(database: SQLiteDatabase): List<LegacyUserRule> =
        database.rawQuery(
            "SELECT host, type, enabled, redirection FROM hosts_lists " +
                "WHERE source_id = $USER_SOURCE_ID ORDER BY id",
            null,
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        LegacyUserRule(
                            host = cursor.getString(0),
                            type = cursor.getInt(1),
                            enabled = cursor.getInt(2) != 0,
                            redirection = if (cursor.isNull(3)) null else cursor.getString(3),
                        ),
                    )
                }
            }
        }

    private companion object {
        const val TAG = "LegacyAppData"
        const val PREFERENCES_NAME = "preferences"
        const val DATABASE_NAME = "app.db"
        const val SUPPORTED_DATABASE_VERSION = 7
        const val USER_SOURCE_ID = 1
        const val LEGACY_UPDATE_APK = "adaway-community-update.apk"
        val LEGACY_WORK_NAMES = listOf("HostsUpdateWork", "ApkUpdateWork", "vpnHeartbeat")
        val LEGACY_FILES = listOf("hosts", "default_hosts", "webserver", "diagnostic.log", "diagnostic.log.1")
    }
}
