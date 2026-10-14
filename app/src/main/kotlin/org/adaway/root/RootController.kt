package org.adaway.root

import android.content.res.Resources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.adaway.blocklist.StatusMessage
import org.adaway.blocklist.StatusMessages
import org.adaway.data.db.AppDatabase
import org.adaway.data.settings.SettingsRepository
import org.adaway.engine.EngineFiles
import org.adaway.system.RootAccess
import org.adaway.ui.common.sourceLabel

/** Whether the system hosts file is the one of this app. Unknown until first read. */
enum class RootState { UNKNOWN, INSTALLED, NOT_INSTALLED }

/** The steps told under the sources card. */
enum class RootStep { CREATING, COPYING, INSTALLED, REVERTING, REVERTED }

/**
 * The root blocking: it is on while the system hosts file is the one this app wrote, as read from
 * the device. Installing and reverting run one at a time.
 */
class RootController(
    private val resources: Resources,
    rootAccess: RootAccess,
    private val database: AppDatabase,
    private val files: EngineFiles,
    private val settingsRepository: SettingsRepository,
    private val statusMessages: StatusMessages,
) {
    private val installer = HostsInstaller(files.rootDirectory, rootAccess)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(RootState.UNKNOWN)
    private val mutableBusy = MutableStateFlow(false)
    private val mutableError = MutableStateFlow<RootError?>(null)

    val state: StateFlow<RootState> = mutableState.asStateFlow()

    /** While installing or reverting. */
    val busy: StateFlow<Boolean> = mutableBusy.asStateFlow()

    /** The failure of the last installation or revert, until dismissed or the next one. */
    val error: StateFlow<RootError?> = mutableError.asStateFlow()

    /** Reads the system hosts file again, as it may have been changed outside the app. */
    suspend fun refresh() = mutex.withLock { readState() }

    /** Writes the hosts file from the final list and installs it. Returns the failure, if any. */
    suspend fun install(): RootError? = mutex.withLock { change(::writeAndInstall) }

    /** Puts the hosts file of the device back. Returns the failure, if any. */
    suspend fun revert(): RootError? = mutex.withLock {
        change {
            statusMessages.post(StatusMessage.Root(RootStep.REVERTING))
            installer.revert(HostsFile.ANDROID_DEFAULT)
            RootStep.REVERTED
        }
    }

    /** Writes an installed hosts file again, from the current final list and settings. */
    suspend fun reinstall() {
        mutex.withLock { if (readState() == RootState.INSTALLED) change(::writeAndInstall) }
    }

    fun dismissError() {
        mutableError.value = null
    }

    private suspend fun writeAndInstall(): RootStep {
        statusMessages.post(StatusMessage.Root(RootStep.CREATING))
        val hosts = try {
            withContext(Dispatchers.IO) {
                HostsFile.write(files, database.sourceDao().getAll(), { sourceLabel(it, resources) }, settingsRepository.settings.first())
            }
        } catch (_: RuntimeException) {
            throw RootException(RootError.PRIVATE_FILE)
        }
        statusMessages.post(StatusMessage.Root(RootStep.COPYING))
        try {
            installer.install(hosts)
        } finally {
            hosts.delete()
        }
        return RootStep.INSTALLED
    }

    /** Runs under the lock. */
    private suspend fun change(operation: suspend () -> RootStep): RootError? {
        mutableBusy.value = true
        mutableError.value = null
        return try {
            statusMessages.post(StatusMessage.Root(operation()))
            null
        } catch (exception: RootException) {
            statusMessages.post(null)
            mutableError.value = exception.error
            exception.error
        } finally {
            readState()
            mutableBusy.value = false
        }
    }

    private suspend fun readState(): RootState = withContext(Dispatchers.IO) {
        (if (installer.isInstalled()) RootState.INSTALLED else RootState.NOT_INSTALLED).also { mutableState.value = it }
    }
}
