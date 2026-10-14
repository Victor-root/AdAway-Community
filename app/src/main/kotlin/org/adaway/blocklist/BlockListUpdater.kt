package org.adaway.blocklist

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.adaway.sources.SourceSync
import org.adaway.sources.UpdateProgress
import java.util.concurrent.atomic.AtomicBoolean

enum class UpdateError { NO_NETWORK, DOWNLOAD_FAILED, APPLY_FAILED }

/**
 * Runs the source updates and the computation of the final list, one at a time. Requests made
 * from the screens run in the app scope, so they finish even if the user leaves the screen.
 */
class BlockListUpdater(
    private val scope: CoroutineScope,
    private val sourceSync: SourceSync,
    private val compiler: BlockListCompiler,
) {
    private val mutex = Mutex()
    private val startupTasksStarted = AtomicBoolean(false)
    private val mutableProgress = MutableStateFlow<UpdateProgress?>(null)
    private val mutableError = MutableStateFlow<UpdateError?>(null)

    val progress: StateFlow<UpdateProgress?> = mutableProgress.asStateFlow()

    /** The failure of the last update requested from a screen, until dismissed. */
    val error: StateFlow<UpdateError?> = mutableError.asStateFlow()

    /** Downloads all the enabled sources, then applies. */
    fun requestSync() = launchForScreen { syncAndApply(onlyPending = false) }

    /** Downloads the sources added or edited since their last read, then applies. */
    fun requestApply() = launchForScreen { syncAndApply(onlyPending = true) }

    /** On the first screen display of the process: reads new sources, or checks for updates. */
    fun runStartupTasks(checkForUpdates: Boolean) {
        if (!startupTasksStarted.compareAndSet(false, true)) return
        scope.launch {
            when {
                sourceSync.hasPendingSources() -> mutableError.value = syncAndApply(onlyPending = true)
                checkForUpdates -> checkForUpdates()
            }
        }
    }

    fun dismissError() {
        mutableError.value = null
    }

    /** Returns null when another update is already running. */
    suspend fun checkForUpdates(): Boolean? = exclusive { sourceSync.check(::publish) }

    /** Returns the error, or null on success or when another update is already running. */
    suspend fun syncAndApply(onlyPending: Boolean): UpdateError? = exclusive {
        val report = sourceSync.sync(onlyPending, ::publish)
        val syncError = when {
            report.allFailed && report.noNetwork -> UpdateError.NO_NETWORK
            report.allFailed -> UpdateError.DOWNLOAD_FAILED
            else -> null
        }
        compile() ?: syncError
    }

    private suspend fun compile(): UpdateError? {
        publish(UpdateProgress.Applying)
        return try {
            compiler.compile()
            null
        } catch (_: RuntimeException) {
            UpdateError.APPLY_FAILED
        }
    }

    private fun launchForScreen(update: suspend () -> UpdateError?) {
        scope.launch { update()?.let { mutableError.value = it } }
    }

    private fun publish(progress: UpdateProgress) {
        mutableProgress.value = progress
    }

    private suspend fun <T> exclusive(block: suspend () -> T): T? {
        if (!mutex.tryLock()) return null
        return try {
            block()
        } finally {
            mutableProgress.value = null
            mutex.unlock()
        }
    }
}
