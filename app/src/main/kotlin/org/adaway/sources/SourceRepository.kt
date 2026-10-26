package org.adaway.sources

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.adaway.blocklist.BlockListState
import org.adaway.data.db.AppDatabase
import org.adaway.data.db.SourceEntity
import org.adaway.data.db.SourceFormat
import org.adaway.engine.EngineFiles
import java.net.URI
import java.net.URISyntaxException

/** A source as edited in the form. A null [label] keeps the translated name of a built-in source. */
data class SourceDraft(
    val label: String?,
    val url: String,
    val format: SourceFormat,
    val redirectsEnabled: Boolean,
)

enum class SourceFieldError { LABEL_REQUIRED, LOCATION_REQUIRED, LOCATION_INVALID, LOCATION_DUPLICATE }

sealed interface SourceSaveResult {
    data object Saved : SourceSaveResult

    data class Invalid(val errors: Set<SourceFieldError>) : SourceSaveResult
}

class SourceRepository(
    private val database: AppDatabase,
    private val files: EngineFiles,
    private val state: BlockListState,
) {
    private val dao = database.sourceDao()

    private val mutableRecentlyDeleted = MutableStateFlow<SourceEntity?>(null)

    val sources: Flow<List<SourceEntity>> = dao.observeAll()

    /** The last deleted source, which the user can still restore. */
    val recentlyDeleted: StateFlow<SourceEntity?> = mutableRecentlyDeleted.asStateFlow()

    suspend fun get(id: Long): SourceEntity? = dao.get(id)

    /** Adds a source when [id] is null, or edits it in place. */
    suspend fun save(id: Long?, draft: SourceDraft): SourceSaveResult {
        val existing = id?.let { dao.get(it) }
        val url = normalizeUrl(draft.url)
        val label = draft.label?.let(::cleanLabel)
        val errors = buildSet {
            val keepsBuiltInName = label == null && existing?.builtIn != null && url == existing.url
            if (label.isNullOrEmpty() && !keepsBuiltInName) add(SourceFieldError.LABEL_REQUIRED)
            when {
                draft.url.isBlank() -> add(SourceFieldError.LOCATION_REQUIRED)
                url == null -> add(SourceFieldError.LOCATION_INVALID)
                dao.findByUrl(url)?.let { it.id != id } == true -> add(SourceFieldError.LOCATION_DUPLICATE)
            }
        }
        if (errors.isNotEmpty() || url == null) return SourceSaveResult.Invalid(errors)
        val redirectsEnabled = draft.redirectsEnabled && draft.format == SourceFormat.BLOCK
        if (existing == null) {
            dao.insert(
                SourceEntity(
                    url = url,
                    label = label,
                    builtIn = null,
                    format = draft.format,
                    redirectsEnabled = redirectsEnabled,
                    enabled = true,
                ),
            )
        } else {
            val listChanged = url != existing.url || draft.format != existing.format ||
                redirectsEnabled != existing.redirectsEnabled
            val edited = existing.copy(
                url = url,
                label = label,
                builtIn = existing.builtIn.takeIf { url == existing.url },
                format = draft.format,
                redirectsEnabled = redirectsEnabled,
            )
            if (listChanged) files.sourceRules(existing.id).delete()
            dao.update(if (listChanged) edited.withoutSyncState() else edited)
        }
        state.markConfigurationChanged()
        return SourceSaveResult.Saved
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) {
        dao.setEnabled(id, enabled)
        state.markConfigurationChanged()
    }

    suspend fun delete(source: SourceEntity) {
        dao.delete(source.id)
        mutableRecentlyDeleted.value = source
        state.markConfigurationChanged()
    }

    fun forgetRecentlyDeleted() {
        mutableRecentlyDeleted.value = null
    }

    /** Puts back a deleted source. Its list is read again if it was removed meanwhile. */
    suspend fun restore(source: SourceEntity) {
        forgetRecentlyDeleted()
        dao.insertIfAbsent(if (files.sourceRules(source.id).exists()) source else source.withoutSyncState())
        state.markConfigurationChanged()
    }

    private fun SourceEntity.withoutSyncState() = copy(
        entityTag = null,
        lastModifiedHeader = null,
        onlineModifiedAt = null,
        syncedAt = null,
        ruleCount = 0,
        syncError = null,
        syncErrorStatus = null,
        updateAvailable = false,
    )

    companion object {
        private const val HTTPS = "https://"
        private const val CONTENT = "content://"
        private val LINE_BREAKS = Regex("[\\r\\n]+")

        /** The "https://" the field starts with, before a whole url pasted after it. */
        private val PREFIX_BEFORE_PASTED_URL = Regex("^https://(?=[a-z]+://)", RegexOption.IGNORE_CASE)

        /** A label on a single line. */
        fun cleanLabel(label: String) = label.replace(LINE_BREAKS, " ").trim()

        /** Returns the url to store, or null unless it is an HTTPS url or a picked document. */
        fun normalizeUrl(input: String): String? {
            var url = input.trim()
            while (PREFIX_BEFORE_PASTED_URL.containsMatchIn(url)) url = url.replaceFirst(PREFIX_BEFORE_PASTED_URL, "")
            return when {
                url.startsWith(CONTENT) && url.length > CONTENT.length -> url
                url.startsWith(HTTPS, ignoreCase = true) -> {
                    val normalized = HTTPS + url.substring(HTTPS.length)
                    val host = try {
                        URI(normalized).host
                    } catch (_: URISyntaxException) {
                        null
                    }
                    normalized.takeUnless { host.isNullOrEmpty() }
                }
                else -> null
            }
        }
    }
}
