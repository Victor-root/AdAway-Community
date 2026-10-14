package org.adaway.sources

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.adaway.data.db.AppDatabase
import org.adaway.data.db.SourceEntity
import org.adaway.data.db.SourceFormat
import org.adaway.data.db.SyncError
import org.adaway.engine.Engine
import org.adaway.engine.EngineFiles
import java.io.File

/** Step of a running update, shown to the user. */
sealed interface UpdateProgress {
    data object Checking : UpdateProgress

    data class Downloading(val source: SourceEntity) : UpdateProgress

    data class Reading(val source: SourceEntity) : UpdateProgress

    data object Applying : UpdateProgress
}

class SyncReport(val attempted: Int, val failed: Int, val noNetwork: Boolean) {
    val allFailed get() = attempted > 0 && failed == attempted
}

/** Downloads the enabled sources and turns them into engine rule files. */
class SourceSync(
    private val database: AppDatabase,
    private val downloader: SourceDownloader,
    private val files: EngineFiles,
) {
    /** Synchronizes the enabled sources, or with [onlyPending] those never read since added or edited. */
    suspend fun sync(onlyPending: Boolean, onProgress: (UpdateProgress) -> Unit): SyncReport =
        withContext(Dispatchers.IO) {
            val sources = database.sourceDao().getAll().filter { source ->
                source.enabled && (!onlyPending || isPending(source))
            }
            val online = downloader.hasInternet()
            var failed = 0
            var noNetwork = false
            for (source in sources) {
                val error = syncSource(source, online, onProgress) ?: continue
                failed++
                noNetwork = noNetwork || error == SyncError.NO_NETWORK
            }
            SyncReport(attempted = sources.size, failed = failed, noNetwork = noNetwork)
        }

    /** Whether an enabled source was never read since it was added or edited. */
    suspend fun hasPendingSources(): Boolean = withContext(Dispatchers.IO) {
        database.sourceDao().getAll().any { it.enabled && isPending(it) }
    }

    private fun isPending(source: SourceEntity) = source.syncedAt == null || !files.sourceRules(source.id).exists()

    /** Asks each synchronized url source whether it changed. Returns whether any source needs a sync. */
    suspend fun check(onProgress: (UpdateProgress) -> Unit): Boolean = withContext(Dispatchers.IO) {
        onProgress(UpdateProgress.Checking)
        val sources = database.sourceDao().getAll().filter { it.enabled }
        // A source that failed last time is retried with the next synchronization.
        var updateAvailable = sources.any { it.syncedAt == null || it.updateAvailable || it.syncError != null }
        if (!downloader.hasInternet()) return@withContext updateAvailable
        for (source in sources.filter { it.syncedAt != null && !it.updateAvailable && it.syncError == null && !it.isLocalFile() }) {
            if (downloader.hasChanged(source) == true) {
                database.sourceDao().markUpdateAvailable(source.id)
                updateAvailable = true
            }
        }
        updateAvailable
    }

    /** Returns the error, or null on success. */
    private suspend fun syncSource(
        source: SourceEntity,
        online: Boolean,
        onProgress: (UpdateProgress) -> Unit,
    ): SyncError? {
        if (!online && !source.isLocalFile()) return fail(source, SyncError.NO_NETWORK)
        onProgress(UpdateProgress.Downloading(source))
        val rules = files.sourceRules(source.id)
        val download = File(files.downloadsDirectory, "${source.id}.download")
        try {
            return when (val result = downloader.download(source, download, conditional = rules.exists())) {
                is DownloadResult.Failed -> fail(source, result.error, result.httpStatus)
                DownloadResult.NotModified -> {
                    database.sourceDao().recordNotModified(source.id, source.url, now())
                    null
                }
                is DownloadResult.Downloaded -> {
                    onProgress(UpdateProgress.Reading(source))
                    read(source, download, rules, result)
                }
            }
        } finally {
            download.delete()
        }
    }

    private suspend fun read(
        source: SourceEntity,
        download: File,
        rules: File,
        result: DownloadResult.Downloaded,
    ): SyncError? {
        rules.parentFile?.mkdirs()
        val count = Engine.parseSource(
            input = download.path,
            output = rules.path,
            allowFormat = source.format == SourceFormat.ALLOW,
            redirects = source.redirectsEnabled,
            maxRules = MAX_RULES,
        )
        return when (count) {
            Engine.PARSE_TOO_MANY_RULES -> fail(source, SyncError.TOO_MANY_RULES)
            Engine.PARSE_NO_RULES -> fail(source, SyncError.NO_RULES)
            Engine.PARSE_IO_ERROR -> fail(source, SyncError.STORAGE)
            else -> {
                database.sourceDao().recordSynchronized(
                    id = source.id,
                    url = source.url,
                    format = source.format,
                    redirectsEnabled = source.redirectsEnabled,
                    entityTag = result.entityTag,
                    lastModifiedHeader = result.lastModifiedHeader,
                    onlineModifiedAt = SourceDownloader.parseHttpDate(result.lastModifiedHeader),
                    syncedAt = now(),
                    ruleCount = count.toInt(),
                )
                null
            }
        }
    }

    private suspend fun fail(source: SourceEntity, error: SyncError, status: Int? = null): SyncError {
        database.sourceDao().recordSyncFailure(source.id, error, status)
        return error
    }

    private fun now() = System.currentTimeMillis() / 1000

    companion object {
        const val MAX_RULES = 2_000_000
    }
}

fun SourceEntity.isLocalFile() = url.startsWith("content://")
