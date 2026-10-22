package org.adaway.sources

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.adaway.BuildConfig
import org.adaway.data.db.SourceEntity
import org.adaway.data.db.SyncError
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.net.ssl.SSLException

sealed interface DownloadResult {
    data class Downloaded(val entityTag: String?, val lastModifiedHeader: String?) : DownloadResult

    data object NotModified : DownloadResult

    data class Failed(val error: SyncError, val httpStatus: Int? = null) : DownloadResult
}

/** Fetches source lists: over HTTPS for urls, through the system document picker grant for files. */
class SourceDownloader(private val context: Context) {

    fun hasInternet(): Boolean {
        val connectivity = context.getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Writes the list of [source] to [target]. Asks the server to answer "not modified" when it
     * still has the version read last time, and [target] is then left untouched.
     */
    suspend fun download(source: SourceEntity, target: File, conditional: Boolean): DownloadResult =
        withContext(Dispatchers.IO) {
            if (source.isLocalFile()) readFile(source, target) else fetch(source, target, conditional)
        }

    /** Asks the server whether the list changed since the version read last time, without downloading it. */
    suspend fun hasChanged(source: SourceEntity): Boolean? = withContext(Dispatchers.IO) {
        try {
            val connection = open(source, conditional = true, method = "HEAD")
            try {
                when (connection.responseCode) {
                    HttpURLConnection.HTTP_NOT_MODIFIED -> false
                    HttpURLConnection.HTTP_OK -> changedFromHeaders(source, connection)
                    else -> null
                }
            } finally {
                connection.disconnect()
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun fetch(source: SourceEntity, target: File, conditional: Boolean): DownloadResult = try {
        val connection = open(source, conditional, method = "GET")
        try {
            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> DownloadResult.NotModified
                HttpURLConnection.HTTP_OK -> try {
                    copyLimited(connection.inputStream, target) ?: DownloadResult.Downloaded(
                        entityTag = connection.getHeaderField("ETag"),
                        lastModifiedHeader = connection.getHeaderField("Last-Modified"),
                    )
                } catch (exception: IOException) {
                    DownloadResult.Failed(if (exception is SocketTimeoutException) SyncError.TIMEOUT else SyncError.CONNECTION_LOST)
                }
                else -> DownloadResult.Failed(SyncError.HTTP_STATUS, status)
            }
        } finally {
            connection.disconnect()
        }
    } catch (exception: IOException) {
        DownloadResult.Failed(connectionError(exception))
    }

    private fun connectionError(exception: IOException) = when (exception) {
        is UnknownHostException -> SyncError.HOST_NOT_FOUND
        is SocketTimeoutException -> SyncError.TIMEOUT
        is SSLException -> SyncError.SECURE_CONNECTION
        else -> SyncError.NETWORK
    }

    private fun readFile(source: SourceEntity, target: File): DownloadResult = try {
        val input = context.contentResolver.openInputStream(source.url.toUri())
            ?: return DownloadResult.Failed(SyncError.FILE_UNREADABLE)
        copyLimited(input, target) ?: DownloadResult.Downloaded(entityTag = null, lastModifiedHeader = null)
    } catch (_: IOException) {
        DownloadResult.Failed(SyncError.FILE_UNREADABLE)
    } catch (_: SecurityException) {
        DownloadResult.Failed(SyncError.FILE_UNREADABLE)
    }

    private fun open(source: SourceEntity, conditional: Boolean, method: String): HttpURLConnection {
        val connection = URL(source.url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
        connection.readTimeout = READ_TIMEOUT_MILLIS
        connection.setRequestProperty("User-Agent", "AdAway/${BuildConfig.VERSION_NAME}")
        if (conditional) {
            source.entityTag?.let { connection.setRequestProperty("If-None-Match", it) }
            source.lastModifiedHeader?.let { connection.setRequestProperty("If-Modified-Since", it) }
        }
        return connection
    }

    private fun changedFromHeaders(source: SourceEntity, connection: HttpURLConnection): Boolean {
        val entityTag = connection.getHeaderField("ETag")
        if (entityTag != null && source.entityTag != null) return entityTag != source.entityTag
        val lastModified = parseHttpDate(connection.getHeaderField("Last-Modified"))
        val knownLastModified = source.onlineModifiedAt
        if (lastModified != null && knownLastModified != null) return lastModified > knownLastModified
        // Without validators, a list read more than a week ago is considered outdated.
        val syncedAt = source.syncedAt ?: return true
        return System.currentTimeMillis() / 1000 - syncedAt > UNKNOWN_FRESHNESS_SECONDS
    }

    /** Copies [input] to [target], or returns the failure if it is larger than allowed. */
    /** A failure to read is the caller's to report; a failure to write is the storage's. */
    private fun copyLimited(input: InputStream, target: File): DownloadResult.Failed? {
        input.use { stream ->
            target.parentFile?.mkdirs()
            val output = try {
                target.outputStream()
            } catch (_: IOException) {
                return DownloadResult.Failed(SyncError.STORAGE)
            }
            output.use {
                val buffer = ByteArray(BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_LIST_BYTES) return DownloadResult.Failed(SyncError.TOO_LARGE)
                    try {
                        output.write(buffer, 0, read)
                    } catch (_: IOException) {
                        return DownloadResult.Failed(SyncError.STORAGE)
                    }
                }
            }
        }
        return null
    }

    companion object {
        const val MAX_LIST_MEGABYTES = 128
        const val MAX_LIST_BYTES = MAX_LIST_MEGABYTES * 1024L * 1024
        private const val CONNECT_TIMEOUT_MILLIS = 30_000
        private const val READ_TIMEOUT_MILLIS = 60_000
        private const val BUFFER_SIZE = 64 * 1024
        private const val UNKNOWN_FRESHNESS_SECONDS = 7L * 24 * 60 * 60

        /** Parses an HTTP date header to epoch seconds. */
        fun parseHttpDate(value: String?): Long? = value?.let {
            try {
                ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond()
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }
}
