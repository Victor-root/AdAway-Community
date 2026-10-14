package org.adaway.backup

import android.content.res.Resources
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.adaway.blocklist.BlockListState
import org.adaway.data.db.AppDatabase
import org.adaway.data.db.BuiltInSource
import org.adaway.data.db.RuleType
import org.adaway.data.db.SourceFormat
import org.adaway.data.legacy.LegacyConversion
import org.adaway.data.legacy.LegacySource
import org.adaway.data.legacy.LegacyUserRule
import org.adaway.ui.common.sourceLabel
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/**
 * The backup file of the sources and the user rules, in the JSON of the 6.x versions so that their
 * backups restore too. A restore adds to what exists: a known source is kept, a known host takes
 * the rule of the backup. Invalid entries are skipped, as the previous data is when imported.
 */
class BackupFile(
    private val database: AppDatabase,
    private val conversion: LegacyConversion,
    private val state: BlockListState,
    private val resources: Resources,
) {
    /** Leaves [output] open. */
    suspend fun export(output: OutputStream) = withContext(Dispatchers.IO) {
        val rules = database.userRuleDao().getAll().groupBy { it.type }
        val backup = JSONObject().put(SOURCES, JSONArray(database.sourceDao().getAll().map { source ->
            JSONObject()
                .put(LABEL, sourceLabel(source, resources))
                .put(URL, source.url)
                .put(ENABLED, source.enabled)
                .put(ALLOW, source.format == SourceFormat.ALLOW)
                .put(REDIRECT, source.redirectsEnabled)
        }))
        for ((type, key) in RULE_KEYS) {
            backup.put(key, JSONArray(rules[type].orEmpty().map { rule ->
                JSONObject().put(HOST, rule.host).putOpt(REDIRECT, rule.redirection).put(ENABLED, rule.enabled)
            }))
        }
        output.write(backup.toString(INDENT).toByteArray())
    }

    /** Leaves [input] open. Throws a [JSONException] when the file is not a backup. */
    suspend fun restore(input: InputStream) = withContext(Dispatchers.IO) {
        val backup = JSONObject(input.readBytes().decodeToString())
        val sources = conversion.convertSources(backup.getJSONArray(SOURCES).objects().mapNotNull(::readSource))
            .map { source ->
                val builtIn = BuiltInSource.entries.firstOrNull { it.url == source.url }
                // A built-in source keeps its translated name, unless the user renamed it.
                if (builtIn != null && source.label == sourceLabel(source.copy(label = null, builtIn = builtIn), resources)) {
                    source.copy(label = null, builtIn = builtIn)
                } else {
                    source.copy(builtIn = builtIn)
                }
            }
        val rules = conversion.convertRules(
            RULE_KEYS.flatMap { (type, key) -> backup.getJSONArray(key).objects().mapNotNull { readRule(it, type) } },
        )
        val sourceDao = database.sourceDao()
        val ruleDao = database.userRuleDao()
        database.withTransaction {
            sources.forEach { sourceDao.insertIfAbsent(it) }
            for (rule in rules) {
                val existing = ruleDao.findByHost(rule.host)
                if (existing == null) ruleDao.insert(rule) else ruleDao.update(rule.copy(id = existing.id))
            }
        }
        state.markConfigurationChanged()
    }

    private fun readSource(source: JSONObject): LegacySource? = try {
        LegacySource(
            label = source.getString(LABEL),
            url = source.getString(URL),
            enabled = source.getBoolean(ENABLED),
            allowFormat = source.getBoolean(ALLOW),
            redirectsEnabled = source.getBoolean(REDIRECT),
        )
    } catch (_: JSONException) {
        null
    }

    private fun readRule(rule: JSONObject, type: RuleType): LegacyUserRule? = try {
        LegacyUserRule(
            host = rule.getString(HOST),
            type = LegacyConversion.ruleTypeCode(type),
            enabled = rule.getBoolean(ENABLED),
            redirection = rule.optString(REDIRECT).ifEmpty { null },
        )
    } catch (_: JSONException) {
        null
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

    private companion object {
        const val SOURCES = "sources"
        const val LABEL = "label"
        const val URL = "url"
        const val ENABLED = "enabled"
        const val ALLOW = "allow"
        const val REDIRECT = "redirect"
        const val HOST = "host"
        const val INDENT = 4
        val RULE_KEYS = listOf(RuleType.BLOCKED to "blocked", RuleType.ALLOWED to "allowed", RuleType.REDIRECTED to "redirected")
    }
}
