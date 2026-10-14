package org.adaway.system

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.adaway.BuildConfig
import org.adaway.data.settings.Settings
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The technical events the user can record, then share when reporting a problem: VPN lifecycle,
 * network changes, failures. Never a host name. Off until the user turns it on. The file rotates
 * into a single backup past [MAX_FILE_SIZE], and writes happen in order on their own thread.
 */
class DiagnosticLog(directory: File, private val scope: CoroutineScope, settings: Flow<Settings>) {
    private val file = File(directory, "diagnostic.log")
    private val backup = File(directory, "diagnostic.log.1")
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)

    @Volatile
    private var enabled = false

    init {
        scope.launch {
            settings.map { it.diagnosticLogEnabled }.distinctUntilChanged().collect { enabled = it }
        }
    }

    fun info(tag: String, message: String) = record(Log.INFO, tag, message)

    fun warn(tag: String, message: String) = record(Log.WARN, tag, message)

    /** The whole log, oldest events first. Empty when nothing was recorded. */
    suspend fun read(): String = withContext(dispatcher) {
        listOf(backup, file).filter(File::exists).joinToString("") { log ->
            try {
                log.readText()
            } catch (_: IOException) {
                ""
            }
        }
    }

    suspend fun clear() = withContext(dispatcher) {
        file.delete()
        backup.delete()
    }

    /** Also in the Android log of the debug builds. */
    private fun record(priority: Int, tag: String, message: String) {
        if (BuildConfig.DEBUG) Log.println(priority, tag, message)
        if (!enabled) return
        val line = "${LocalDateTime.now().format(TIMESTAMP)} ${if (priority == Log.WARN) 'W' else 'I'}/$tag: $message\n"
        scope.launch(dispatcher) {
            try {
                if (file.length() >= MAX_FILE_SIZE) {
                    backup.delete()
                    file.renameTo(backup)
                }
                file.appendText(line)
            } catch (_: IOException) {
                // The event is lost, the app goes on.
            }
        }
    }

    private companion object {
        const val MAX_FILE_SIZE = 100 * 1024L
        val TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss", Locale.ROOT)
    }
}
