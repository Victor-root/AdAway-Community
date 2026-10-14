package org.adaway.blocklist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.adaway.data.db.AppDatabase
import org.adaway.data.db.RuleType
import org.adaway.engine.Engine
import org.adaway.engine.EngineFiles
import java.io.File

/** Computes the final list from the synchronized sources and the user rules. */
class BlockListCompiler(
    private val database: AppDatabase,
    private val files: EngineFiles,
    private val state: BlockListState,
) {
    private val mutex = Mutex()

    /** Throws a [RuntimeException] when the engine cannot read or write its files. */
    suspend fun compile(): BlockListSummary = mutex.withLock {
        withContext(Dispatchers.IO) {
            val generation = state.changeGeneration()
            val sources = database.sourceDao().getAll()
            deleteOrphanRuleFiles(sources.map { it.id }.toSet())
            val sourceFiles = sources
                .filter { it.enabled }
                .map { files.sourceRules(it.id) }
                .filter(File::exists)
                .map(File::getPath)
            val rules = database.userRuleDao().getEnabled().groupBy { it.type }
            val redirections = rules[RuleType.REDIRECTED].orEmpty().filter { it.redirection != null }
            val counts = Engine.compile(
                sourceFiles = sourceFiles.toTypedArray(),
                userBlocked = rules[RuleType.BLOCKED].orEmpty().map { it.host }.toTypedArray(),
                userAllowed = rules[RuleType.ALLOWED].orEmpty().map { it.host }.toTypedArray(),
                userRedirectHosts = redirections.map { it.host }.toTypedArray(),
                userRedirectAddresses = redirections.map { checkNotNull(it.redirection) }.toTypedArray(),
                catalogDirectory = files.catalogDirectory.path,
                finalDirectory = files.finalDirectory.path,
            )
            val summary = BlockListSummary(blocked = counts[0], allowed = counts[1], redirected = counts[2])
            state.recordCompilation(summary, generation)
            summary
        }
    }

    private fun deleteOrphanRuleFiles(sourceIds: Set<Long>) {
        files.sourcesDirectory.listFiles()?.forEach { file ->
            if (file.nameWithoutExtension.toLongOrNull() !in sourceIds) file.delete()
        }
    }
}
