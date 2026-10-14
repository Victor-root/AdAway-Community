package org.adaway.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.adaway.BuildConfig
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/** A release of the app on GitHub, with its APK. */
data class AppRelease(val version: String, val changelog: String, val apkUrl: String) {
    val isNewer: Boolean get() = AppVersion(version) > AppVersion(BuildConfig.VERSION_NAME)
}

/**
 * Finds the latest release of the app, downloads its APK and has Android install it. A pre-release
 * is only offered to the users of a pre-release.
 */
class AppUpdater(private val context: Context) {
    private val mutableLatest = MutableStateFlow<AppRelease?>(null)
    private val startupCheckDone = AtomicBoolean(false)
    private val apk = File(File(context.cacheDir, "update"), "adaway-update.apk")

    /** The latest release found by the last check. */
    val latest: StateFlow<AppRelease?> = mutableLatest.asStateFlow()

    /** The latest release when newer than the installed version. */
    val available: Flow<AppRelease?> = latest.map { release -> release?.takeIf { it.isNewer } }

    /** The check asked at startup, done once per launch of the app. */
    suspend fun checkOnStartup() {
        if (startupCheckDone.compareAndSet(false, true)) check()
    }

    /** Returns the latest release, or null when it cannot be known. */
    suspend fun check(): AppRelease? = withContext(Dispatchers.IO) {
        val releases = try {
            JSONArray(get(RELEASES_URL, GITHUB_API_TYPE).use { it.inputStream.bufferedReader().readText() })
        } catch (_: IOException) {
            return@withContext null
        } catch (_: JSONException) {
            return@withContext null
        }
        val includePreReleases = AppVersion(BuildConfig.VERSION_NAME).isPreRelease
        val latest = (0 until releases.length())
            .mapNotNull { releases.optJSONObject(it) }
            .filter { !it.optBoolean("draft") && (includePreReleases || !it.optBoolean("prerelease")) }
            .mapNotNull(::toRelease)
            .maxWithOrNull(compareBy { AppVersion(it.version) })
        latest?.also { mutableLatest.value = it }
    }

    /** Downloads the APK of [release]. Throws an [IOException] when it fails. */
    suspend fun download(release: AppRelease, onProgress: (downloaded: Long, total: Long) -> Unit): File = withContext(Dispatchers.IO) {
        apk.parentFile?.mkdirs()
        get(release.apkUrl).use { connection ->
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                apk.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }
        }
        apk
    }

    /**
     * Hands the APK to the Android installer, which asks the user to confirm, and checks itself that
     * it is signed like the installed app. Throws an [IOException] when the file is not this app.
     */
    suspend fun install(file: File) = withContext(Dispatchers.IO) {
        val archive = context.packageManager.getPackageArchiveInfo(file.path, 0)
        if (archive?.packageName != context.packageName) throw IOException("${file.name} is not ${context.packageName}")
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            try {
                session.openWrite(file.name, 0, file.length()).use { output ->
                    file.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
            } catch (exception: IOException) {
                session.abandon()
                throw exception
            }
            val status = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, InstallStatusReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(status.intentSender)
        }
        file.delete()
    }

    private fun toRelease(release: JSONObject): AppRelease? {
        val version = release.optString("tag_name").ifEmpty { return null }
        val assets = release.optJSONArray("assets") ?: return null
        val apkUrl = (0 until assets.length())
            .mapNotNull { assets.optJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            ?.optString("browser_download_url")
            ?.takeIf { it.startsWith("https://") }
            ?: return null
        return AppRelease(version, release.optString("body"), apkUrl)
    }

    private fun get(url: String, accept: String? = null): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = TIMEOUT_MILLIS
        connection.readTimeout = TIMEOUT_MILLIS
        accept?.let { connection.setRequestProperty("Accept", it) }
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK) {
            connection.disconnect()
            throw IOException("$url answered $code")
        }
        return connection
    }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T = try {
        block(this)
    } finally {
        disconnect()
    }

    private companion object {
        const val RELEASES_URL = "https://api.github.com/repos/Victor-root/AdAway-Community/releases?per_page=30"
        const val GITHUB_API_TYPE = "application/vnd.github+json"
        const val TIMEOUT_MILLIS = 30_000
        const val BUFFER_SIZE = 64 * 1024
    }
}
