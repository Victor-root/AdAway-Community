package org.adaway.engine

import android.content.Context
import org.adaway.data.db.RuleType
import java.io.File

/** Where the engine keeps its files: rules read from each source, final list and catalog. */
class EngineFiles(context: Context) {
    private val root = File(context.filesDir, "engine")
    val sourcesDirectory = File(root, "sources")
    val catalogDirectory = File(root, "catalog")
    val finalDirectory = File(root, "final")
    val downloadsDirectory = File(context.cacheDir, "downloads")

    fun sourceRules(sourceId: Long) = File(sourcesDirectory, "$sourceId.rules")

    fun catalog(type: RuleType) = File(
        catalogDirectory,
        when (type) {
            RuleType.BLOCKED -> "blocked.txt"
            RuleType.ALLOWED -> "allowed.txt"
            RuleType.REDIRECTED -> "redirected.txt"
        },
    )
}
