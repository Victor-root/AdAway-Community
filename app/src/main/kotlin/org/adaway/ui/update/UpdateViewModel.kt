package org.adaway.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.adaway.AppContainer
import org.adaway.R
import org.adaway.update.AppRelease
import java.io.IOException

/** How far the update went: downloading, then handed to the Android installer. */
sealed interface UpdateProgress {
    data class Downloading(val downloaded: Long, val total: Long) : UpdateProgress

    data object Downloaded : UpdateProgress
}

class UpdateViewModel(container: AppContainer) : ViewModel() {
    private val updater = container.appUpdater
    private val mutableChecking = MutableStateFlow(false)
    private val mutableProgress = MutableStateFlow<UpdateProgress?>(null)
    private val mutableFailure = MutableStateFlow<Int?>(null)

    val latest: StateFlow<AppRelease?> = updater.latest
    val checking: StateFlow<Boolean> = mutableChecking.asStateFlow()
    val progress: StateFlow<UpdateProgress?> = mutableProgress.asStateFlow()

    /** The message of the last failure, until shown. */
    val failure: StateFlow<Int?> = mutableFailure.asStateFlow()

    init {
        if (latest.value == null) {
            mutableChecking.value = true
            viewModelScope.launch {
                updater.check()
                mutableChecking.value = false
            }
        }
    }

    fun update() {
        val release = latest.value?.takeIf { it.isNewer } ?: return
        if (mutableProgress.value != null) return
        mutableProgress.value = UpdateProgress.Downloading(0, 0)
        viewModelScope.launch {
            val apk = try {
                updater.download(release) { downloaded, total -> mutableProgress.value = UpdateProgress.Downloading(downloaded, total) }
            } catch (_: IOException) {
                fail(R.string.update_download_failed)
                return@launch
            }
            mutableProgress.value = UpdateProgress.Downloaded
            try {
                updater.install(apk)
                // The Android installer takes over; the update is offered again if the user cancels it.
                mutableProgress.value = null
            } catch (_: IOException) {
                fail(R.string.update_install_failed)
            } catch (_: SecurityException) {
                fail(R.string.update_install_failed)
            }
        }
    }

    fun failureShown() {
        mutableFailure.value = null
    }

    private fun fail(message: Int) {
        mutableProgress.value = null
        mutableFailure.value = message
    }
}
