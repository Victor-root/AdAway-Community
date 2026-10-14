package org.adaway.root

import android.content.Context
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.adaway.data.settings.BlockingMethod
import org.adaway.data.settings.Settings
import org.adaway.engine.Engine
import org.adaway.system.RootAccess
import org.adaway.system.shellQuote
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.HttpsURLConnection

enum class WebServerError { ROOT_DENIED, RESOURCES, ADDRESS_IN_USE, START_FAILED }

enum class WebServerState { NOT_RUNNING, RUNNING_NOT_INSTALLED, RUNNING_AND_INSTALLED }

/**
 * The local web server of the root blocking: a root process of its own, which outlives the app.
 * Its certificate is generated once per device, so that the one the user installs stays valid.
 */
class WebServer(private val context: Context, private val rootAccess: RootAccess) {
    private val directory = File(context.filesDir, "webserver")
    private val certificate = File(directory, "certificate.pem")
    private val key = File(directory, "key.pem")
    private val executable get() = File(context.applicationInfo.nativeLibraryDir, EXECUTABLE)
    private val mutex = Mutex()

    /** Starts the server, replacing a running one. Returns the failure, if any. */
    suspend fun start(icon: Boolean): WebServerError? = mutex.withLock {
        if (rootAccess.request()) withContext(Dispatchers.IO) { launch(icon) } else WebServerError.ROOT_DENIED
    }

    suspend fun stop() = mutex.withLock {
        if (rootAccess.request()) withContext(Dispatchers.IO) { kill() }
    }

    /** Starts the server the user wants when it is not running, as after a reboot. */
    suspend fun ensureRunning(settings: Settings) {
        if (isWanted(settings) && !withContext(Dispatchers.IO) { isRunning() }) start(settings.webServerIcon)
    }

    /** Starts again the server the user wants, as with a new version of it. */
    suspend fun restartIfWanted(settings: Settings) {
        if (isWanted(settings)) start(settings.webServerIcon)
    }

    private fun isWanted(settings: Settings) = settings.blockingMethod == BlockingMethod.ROOT && settings.webServerEnabled

    private fun launch(icon: Boolean): WebServerError? {
        try {
            prepareResources()
        } catch (_: IOException) {
            return WebServerError.RESOURCES
        } catch (_: RuntimeException) {
            return WebServerError.RESOURCES
        }
        kill()
        val command = "${shellQuote(executable.path)} --resources ${shellQuote(directory.path)}" + if (icon) " --icon" else ""
        return when (Shell.cmd(command).exec().code) {
            0 -> null
            EXIT_RESOURCES -> WebServerError.RESOURCES
            EXIT_ADDRESS_IN_USE -> WebServerError.ADDRESS_IN_USE
            else -> WebServerError.START_FAILED
        }
    }

    /** Asked to the server itself, which needs no root. */
    suspend fun state(): WebServerState = withContext(Dispatchers.IO) {
        when {
            !isRunning() -> WebServerState.NOT_RUNNING
            isCertificateTrusted() -> WebServerState.RUNNING_AND_INSTALLED
            else -> WebServerState.RUNNING_NOT_INSTALLED
        }
    }

    /** The certificate to install, in PEM. Throws an [IOException] or a [RuntimeException] when it cannot be prepared. */
    suspend fun certificate(): ByteArray = withContext(Dispatchers.IO) {
        prepareCertificate()
        certificate.readBytes()
    }

    private fun prepareResources() {
        directory.mkdirs()
        // Always the ones of the installed version.
        for (name in listOf("test.html", "icon.svg")) {
            context.assets.open(name).use { input -> File(directory, name).outputStream().use(input::copyTo) }
        }
        prepareCertificate()
    }

    private fun prepareCertificate() {
        if (certificate.exists() && key.exists()) return
        directory.mkdirs()
        Engine.generateWebServerCertificate(certificate.path, key.path)
    }

    /** Also stops the server of a previous app version, which still holds the ports. */
    private fun kill() {
        Shell.cmd("pkill -f $EXECUTABLE", "pkill -f $LEGACY_EXECUTABLE").exec()
    }

    private fun isRunning(): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(InetAddress.getLoopbackAddress(), HTTP_PORT), PROBE_TIMEOUT_MILLIS)
            socket.soTimeout = PROBE_TIMEOUT_MILLIS
            socket.getOutputStream().write("GET $TEST_PATH HTTP/1.0\r\n\r\n".toByteArray())
            socket.getInputStream().bufferedReader().readLine()?.startsWith("HTTP/1.1 200") == true
        }
    } catch (_: IOException) {
        false
    }

    /** Whether the device trusts the certificate the server presents, the user having installed it. */
    private fun isCertificateTrusted(): Boolean = try {
        val connection = URL(TEST_URL).openConnection() as HttpsURLConnection
        connection.connectTimeout = PROBE_TIMEOUT_MILLIS
        connection.readTimeout = PROBE_TIMEOUT_MILLIS
        try {
            connection.responseCode == HttpsURLConnection.HTTP_OK
        } finally {
            connection.disconnect()
        }
    } catch (_: IOException) {
        false
    }

    companion object {
        /** The test page, which tells whether the server runs with its certificate installed. */
        const val TEST_URL = "https://localhost/internal-test"
        private const val TEST_PATH = "/internal-test"
        private const val EXECUTABLE = "libadaway_webserver.so"
        /** The server of the 6.x versions. */
        private const val LEGACY_EXECUTABLE = "libwebserver_exec.so"
        private const val HTTP_PORT = 80
        private const val PROBE_TIMEOUT_MILLIS = 1_000

        /** Exit codes of the server, see its source. */
        private const val EXIT_RESOURCES = 2
        private const val EXIT_ADDRESS_IN_USE = 3
    }
}
