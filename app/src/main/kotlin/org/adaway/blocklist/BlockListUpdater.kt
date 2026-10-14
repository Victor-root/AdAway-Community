package org.adaway.blocklist

import android.database.sqlite.SQLiteException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.adaway.sources.SourceSync
import org.adaway.sources.UpdateProgress
import org.adaway.system.DiagnosticLog
import java.util.concurrent.atomic.AtomicBoolean

enum class UpdateError { NO_NETWORK, DOWNLOAD_FAILED, APPLY_FAILED }

/** How the last successful update ended, told until the next one starts. */
enum class UpdateOutcome { UPDATE_AVAILABLE, NO_UPDATE_FOUND, APPLIED }

/**
 * Runs the source updates and the computation of the final list, one at a time. Requests made
 * from the screens run in the app scope, so they finish even if the user leaves the screen.
 */
class BlockListUpdater(
    private val scope: CoroutineScope,
    private val sourceSync: SourceSync,
    private val compiler: BlockListCompiler,
    private val statusMessages: StatusMessages,
    private val diagnosticLog: DiagnosticLog,
    /** Called after each new final list, for the blocking in use to take it. */
    private val onApplied: suspend () -> Unit,
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

    /**
     * Downloads the sources added or edited since their last read, applies, then runs [andThen]
     * unless it failed or another update was running.
     */
    fun requestApply(andThen: suspend () -> Unit = {}) = launchForScreen {
        exclusive { syncAndApplyNow(onlyPending = true).also { if (it == null) andThen() } }
    }

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

    /** Returns null when another update is already running, or when the result cannot be recorded. */
    suspend fun checkForUpdates(): Boolean? = exclusive {
        try {
            sourceSync.check(::publish).also { available ->
                statusMessages.post(StatusMessage.Outcome(if (available) UpdateOutcome.UPDATE_AVAILABLE else UpdateOutcome.NO_UPDATE_FOUND))
            }
        } catch (exception: SQLiteException) {
            diagnosticLog.warn(TAG, "Update check not recorded: $exception")
            null
        }
    }

    /** Returns the error, or null on success or when another update is already running. */
    suspend fun syncAndApply(onlyPending: Boolean): UpdateError? = exclusive { syncAndApplyNow(onlyPending) }

    private suspend fun syncAndApplyNow(onlyPending: Boolean): UpdateError? {
        val report = try {
            sourceSync.sync(onlyPending, ::publish)
        } catch (exception: SQLiteException) {
            // The storage is full, for instance.
            diagnosticLog.warn(TAG, "Sources not recorded: $exception")
            statusMessages.post(null)
            return UpdateError.APPLY_FAILED
        }
        diagnosticLog.info(TAG, "Sources synchronized: ${report.attempted - report.failed} of ${report.attempted}")
        val syncError = when {
            report.allFailed && report.noNetwork -> UpdateError.NO_NETWORK
            report.allFailed -> UpdateError.DOWNLOAD_FAILED
            else -> null
        }
        // The line under the sources card leaves room for the error.
        return (compile() ?: syncError)?.also { statusMessages.post(null) }
    }

    private suspend fun compile(): UpdateError? {
        publish(UpdateProgress.Applying)
        try {
            compiler.compile()
        } catch (exception: RuntimeException) {
            diagnosticLog.warn(TAG, "Final list not computed: $exception")
            return UpdateError.APPLY_FAILED
        }
        // Before the blocking takes it, so that its own steps come after.
        statusMessages.post(StatusMessage.Outcome(UpdateOutcome.APPLIED))
        onApplied()
        return null
    }

    private fun launchForScreen(update: suspend () -> UpdateError?) {
        scope.launch { update()?.let { mutableError.value = it } }
    }

    private fun publish(progress: UpdateProgress) {
        mutableProgress.value = progress
        statusMessages.post(StatusMessage.Update(progress))
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

    private companion object {
        const val TAG = "AdAwayUpdate"
    }
}
