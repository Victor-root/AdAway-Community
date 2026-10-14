package org.adaway.blocklist

import kotlinx.coroutines.flow.Flow
import org.adaway.data.db.AppDatabase
import org.adaway.data.db.RuleType
import org.adaway.data.db.UserRuleEntity
import org.adaway.engine.RuleValidator

sealed interface RuleSaveResult {
    data object Saved : RuleSaveResult

    data object InvalidHost : RuleSaveResult

    data object InvalidAddress : RuleSaveResult

    /** The host is already in the user lists, in the list of [type]. */
    data class Duplicate(val type: RuleType) : RuleSaveResult
}

/** The rules the user adds by hand: one per host, in one of the three lists. */
class UserRuleRepository(
    database: AppDatabase,
    private val validator: RuleValidator,
    private val state: BlockListState,
) {
    private val dao = database.userRuleDao()

    /** Rules of [type] whose host contains [filter], where `*` and `?` are wildcards. */
    fun observe(type: RuleType, filter: String): Flow<List<UserRuleEntity>> = dao.observe(type, likePattern(filter))

    /** Adds a rule when [id] is null, or edits the host and address of an existing one. */
    suspend fun save(id: Long?, type: RuleType, host: String, redirection: String?): RuleSaveResult {
        val rule = when (val normalized = normalize(type, host, redirection)) {
            is Normalization.Valid -> normalized
            is Normalization.Invalid -> return normalized.result
        }
        dao.findByHost(rule.host)?.takeIf { it.id != id }?.let { return RuleSaveResult.Duplicate(it.type) }
        val existing = id?.let { dao.findById(it) }
        if (existing == null) {
            dao.insert(UserRuleEntity(host = rule.host, type = type, redirection = rule.redirection, enabled = true))
        } else {
            dao.update(existing.copy(host = rule.host, redirection = rule.redirection))
        }
        state.markConfigurationChanged()
        return RuleSaveResult.Saved
    }

    /** Puts [host] in the list of [type], in place of the rule it had, as the DNS log does. */
    suspend fun putHost(host: String, type: RuleType, redirection: String?): RuleSaveResult {
        val rule = when (val normalized = normalize(type, host, redirection)) {
            is Normalization.Valid -> normalized
            is Normalization.Invalid -> return normalized.result
        }
        val existing = dao.findByHost(rule.host)
        if (existing == null) {
            dao.insert(UserRuleEntity(host = rule.host, type = type, redirection = rule.redirection, enabled = true))
        } else {
            dao.update(existing.copy(type = type, redirection = rule.redirection, enabled = true))
        }
        state.markConfigurationChanged()
        return RuleSaveResult.Saved
    }

    suspend fun removeHost(host: String) {
        dao.findByHost(host)?.let { delete(it) }
    }

    /** The list of each host with an enabled rule. */
    suspend fun enabledTypes(): Map<String, RuleType> = dao.getEnabled().associate { it.host to it.type }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        dao.setEnabled(id, enabled)
        state.markConfigurationChanged()
    }

    suspend fun delete(rule: UserRuleEntity) {
        dao.delete(rule.id)
        state.markConfigurationChanged()
    }

    suspend fun restore(rule: UserRuleEntity) {
        dao.insertIfAbsent(rule)
        state.markConfigurationChanged()
    }

    private sealed interface Normalization {
        data class Valid(val host: String, val redirection: String?) : Normalization

        data class Invalid(val result: RuleSaveResult) : Normalization
    }

    private fun normalize(type: RuleType, host: String, redirection: String?): Normalization {
        val normalizedHost = validator.normalizeHostname(host, allowWildcards = type != RuleType.REDIRECTED)
            ?: return Normalization.Invalid(RuleSaveResult.InvalidHost)
        if (type != RuleType.REDIRECTED) return Normalization.Valid(normalizedHost, null)
        val normalizedRedirection = redirection?.let(validator::normalizeIpAddress)
            ?: return Normalization.Invalid(RuleSaveResult.InvalidAddress)
        return Normalization.Valid(normalizedHost, normalizedRedirection)
    }

    companion object {
        /** Turns a search text into an SQL LIKE pattern escaped with `\`. */
        fun likePattern(filter: String): String {
            val escaped = filter.trim().lowercase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
                .replace('*', '%')
                .replace('?', '_')
            return "%$escaped%"
        }
    }
}
