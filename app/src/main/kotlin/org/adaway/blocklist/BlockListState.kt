package org.adaway.blocklist

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.adaway.data.db.RuleType

/** Counts of the last computed final list. */
data class BlockListSummary(val blocked: Long, val allowed: Long, val redirected: Long) {
    fun count(type: RuleType) = when (type) {
        RuleType.BLOCKED -> blocked
        RuleType.ALLOWED -> allowed
        RuleType.REDIRECTED -> redirected
    }
}

/** What the last computed final list contains, and whether the user changed rules since. */
class BlockListState(private val dataStore: DataStore<Preferences>) {
    val summary: Flow<BlockListSummary> = dataStore.data.map { preferences ->
        BlockListSummary(
            blocked = preferences[Keys.blocked] ?: 0,
            allowed = preferences[Keys.allowed] ?: 0,
            redirected = preferences[Keys.redirected] ?: 0,
        )
    }

    /** Incremented by each computation, so that views of the catalog know when to reload. */
    val compilations: Flow<Long> = dataStore.data.map { it[Keys.compilations] ?: 0 }

    /** Whether sources or user rules changed since the final list was last computed. */
    val configurationChanged: Flow<Boolean> = dataStore.data.map { it[Keys.changed] == true }

    suspend fun markConfigurationChanged() {
        dataStore.edit { preferences ->
            preferences[Keys.changed] = true
            preferences[Keys.changeGeneration] = (preferences[Keys.changeGeneration] ?: 0) + 1
        }
    }

    /** Identifies the changes made so far, to tell later whether a computation included them all. */
    suspend fun changeGeneration(): Long = dataStore.data.first()[Keys.changeGeneration] ?: 0

    /** Records a computed list. Changes made after [generation] still need to be applied. */
    suspend fun recordCompilation(summary: BlockListSummary, generation: Long) {
        dataStore.edit { preferences ->
            preferences[Keys.blocked] = summary.blocked
            preferences[Keys.allowed] = summary.allowed
            preferences[Keys.redirected] = summary.redirected
            preferences[Keys.compilations] = (preferences[Keys.compilations] ?: 0) + 1
            if ((preferences[Keys.changeGeneration] ?: 0) == generation) preferences[Keys.changed] = false
        }
    }

    private object Keys {
        val blocked = longPreferencesKey("blocked")
        val allowed = longPreferencesKey("allowed")
        val redirected = longPreferencesKey("redirected")
        val changed = booleanPreferencesKey("configuration_changed")
        val changeGeneration = longPreferencesKey("change_generation")
        val compilations = longPreferencesKey("compilations")
    }
}
