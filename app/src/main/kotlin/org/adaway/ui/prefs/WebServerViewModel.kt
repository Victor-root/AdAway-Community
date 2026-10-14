package org.adaway.ui.prefs

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.adaway.AppContainer
import org.adaway.root.WebServerError
import org.adaway.root.WebServerState
import java.io.IOException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory

/** The root web server settings. Starting and stopping it outlive the screen. */
class WebServerViewModel(private val container: AppContainer) : ViewModel() {
    private val webServer = container.webServer
    private val settingsRepository = container.settingsRepository
    private val mutableState = MutableStateFlow<WebServerState?>(null)
    private val mutableError = MutableStateFlow<WebServerError?>(null)

    /** Null while checking. */
    val state: StateFlow<WebServerState?> = mutableState.asStateFlow()

    /** Why the server the user just turned on did not start. */
    val error: StateFlow<WebServerError?> = mutableError.asStateFlow()

    fun refreshState() {
        viewModelScope.launch { checkState() }
    }

    /** The setting is only kept on once the server started. */
    fun setEnabled(enabled: Boolean) {
        container.applicationScope.launch {
            if (enabled) {
                val error = webServer.start(settingsRepository.settings.first().webServerIcon)
                if (error == null) settingsRepository.update { it.copy(webServerEnabled = true) } else mutableError.value = error
            } else {
                settingsRepository.update { it.copy(webServerEnabled = false) }
                webServer.stop()
            }
            checkState()
        }
    }

    fun setIcon(icon: Boolean) {
        container.applicationScope.launch {
            settingsRepository.update { it.copy(webServerIcon = icon) }
            webServer.restartIfWanted(settingsRepository.settings.first())
            checkState()
        }
    }

    fun dismissError() {
        mutableError.value = null
    }

    /** The certificate in DER, as Android installs it, or null when it cannot be prepared. */
    suspend fun certificateDer(): ByteArray? = try {
        val pem = webServer.certificate()
        withContext(Dispatchers.Default) {
            CertificateFactory.getInstance("X.509").generateCertificate(pem.inputStream()).encoded
        }
    } catch (_: IOException) {
        null
    } catch (_: CertificateException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    /** Writes the certificate to the document the user created. Returns whether it worked. */
    suspend fun exportCertificate(uri: Uri): Boolean = try {
        val pem = webServer.certificate()
        withContext(Dispatchers.IO) {
            container.applicationContext.contentResolver.openOutputStream(uri)?.use { it.write(pem) } != null
        }
    } catch (_: IOException) {
        false
    } catch (_: RuntimeException) {
        false
    }

    private suspend fun checkState() {
        mutableState.value = null
        mutableState.value = webServer.state()
    }
}
