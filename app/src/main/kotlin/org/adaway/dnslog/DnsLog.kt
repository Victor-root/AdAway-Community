package org.adaway.dnslog

import android.content.Context
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.adaway.data.settings.BlockingMethod
import org.adaway.data.settings.SettingsRepository
import org.adaway.engine.Engine
import org.adaway.system.RootAccess
import org.adaway.system.shellQuote
import java.io.File
import java.io.IOException

enum class DnsLogError { ROOT_DENIED, CAPTURE_FAILED }

/**
 * The names the device asked for while the user records them: noted by the VPN tunnels, or in
 * root mode by a capture process of its own, which only sees the names the hosts file lets through.
 */
class DnsLog(private val context: Context, private val settingsRepository: SettingsRepository, private val rootAccess: RootAccess) {
    /** Where the capture writes, the file of the 6.x versions. */
    private val captureFile = File(context.cacheDir, "dns_log.txt")
    private val captureExecutable get() = File(context.applicationInfo.nativeLibraryDir, CAPTURE_EXECUTABLE)

    suspend fun isRecording(): Boolean = when (method()) {
        BlockingMethod.VPN -> Engine.isQueryLogRecording()
        BlockingMethod.ROOT -> rootAccess.request() && withContext(Dispatchers.IO) { Shell.cmd("pgrep -f $CAPTURE_EXECUTABLE").exec().isSuccess }
        BlockingMethod.UNSET -> false
    }

    /** Returns the failure, if any. */
    suspend fun setRecording(recording: Boolean): DnsLogError? = when (method()) {
        BlockingMethod.VPN -> {
            Engine.setQueryLogRecording(recording)
            null
        }
        BlockingMethod.ROOT -> when {
            !rootAccess.request() -> DnsLogError.ROOT_DENIED
            recording -> withContext(Dispatchers.IO) { startCapture() }
            else -> {
                withContext(Dispatchers.IO) { stopCapture() }
                null
            }
        }
        BlockingMethod.UNSET -> null
    }

    /** Each name once, in the order first asked. */
    suspend fun names(): List<String> = withContext(Dispatchers.IO) {
        val names = when (method()) {
            BlockingMethod.VPN -> Engine.queryLogNames().asList()
            BlockingMethod.ROOT -> readCaptureFile()
            BlockingMethod.UNSET -> emptyList()
        }
        names.mapNotNull { Engine.normalizeHostname(it, allowWildcards = false) }.distinct()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        when (method()) {
            BlockingMethod.VPN -> Engine.clearQueryLog()
            // The capture keeps appending to the emptied file.
            BlockingMethod.ROOT -> if (captureFile.exists()) captureFile.writeText("")
            BlockingMethod.UNSET -> {}
        }
    }

    private fun startCapture(): DnsLogError? {
        stopCapture()
        try {
            // Created by the app, so that it can read and empty what the root process writes.
            captureFile.createNewFile()
        } catch (_: IOException) {
            return DnsLogError.CAPTURE_FAILED
        }
        val started = Shell.cmd("${shellQuote(captureExecutable.path)} --output ${shellQuote(captureFile.path)}").exec().isSuccess
        return if (started) null else DnsLogError.CAPTURE_FAILED
    }

    /** Also stops the capture of the 6.x versions, which wrote to the same file. */
    private fun stopCapture() {
        Shell.cmd("pkill -f $CAPTURE_EXECUTABLE", "pkill -f $LEGACY_CAPTURE_EXECUTABLE").exec()
    }

    private fun readCaptureFile(): List<String> = try {
        if (captureFile.exists()) captureFile.readLines() else emptyList()
    } catch (_: IOException) {
        emptyList()
    }

    private suspend fun method() = settingsRepository.settings.first().blockingMethod

    private companion object {
        const val CAPTURE_EXECUTABLE = "libadaway_capture.so"
        const val LEGACY_CAPTURE_EXECUTABLE = "libtcpdump_exec.so"
    }
}
