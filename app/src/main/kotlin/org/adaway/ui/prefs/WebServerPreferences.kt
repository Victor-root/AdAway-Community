package org.adaway.ui.prefs

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.security.KeyChain
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.adaway.R
import org.adaway.data.settings.Settings as AppSettings
import org.adaway.root.WebServer
import org.adaway.root.WebServerError
import org.adaway.root.WebServerState
import org.adaway.ui.common.AdAwayAlertDialog
import org.adaway.ui.common.DialogButton
import org.adaway.ui.common.appViewModel

private const val CERTIFICATE_FILE_NAME = "adaway-webserver-certificate.crt"

/**
 * The root web server rows of the preferences. Its test, certificate and icon depend on it being
 * on. Since Android 11, the certificate can only be installed by the user, from a file.
 */
@Composable
fun WebServerPreferences(settings: AppSettings, enabled: Boolean) {
    val viewModel = appViewModel(::WebServerViewModel)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exported by rememberSaveable { mutableStateOf(false) }
    val certificateFailed = stringResource(R.string.pref_webserver_certificate_failed)
    val exportCertificate = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-x509-ca-cert")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            if (viewModel.exportCertificate(uri)) exported = true else Toast.makeText(context, certificateFailed, Toast.LENGTH_SHORT).show()
        }
    }
    val dependentEnabled = enabled && settings.webServerEnabled
    LifecycleResumeEffect(enabled) {
        if (enabled) viewModel.refreshState()
        onPauseOrDispose {}
    }
    Preference(null, null, summary = stringResource(R.string.pref_webserver_summary), enabled = enabled)
    SwitchPreference(
        R.drawable.ic_dns_24dp,
        stringResource(R.string.pref_webserver_enabled),
        checked = settings.webServerEnabled,
        onCheckedChange = viewModel::setEnabled,
        enabled = enabled,
    )
    Preference(
        R.drawable.ic_open_in_browser_24dp,
        stringResource(R.string.pref_webserver_test),
        stringResource(
            when (state) {
                null -> R.string.pref_webserver_state_checking
                WebServerState.NOT_RUNNING -> R.string.pref_webserver_state_not_running
                WebServerState.RUNNING_NOT_INSTALLED -> R.string.pref_webserver_state_running_not_installed
                WebServerState.RUNNING_AND_INSTALLED -> R.string.pref_webserver_state_running_and_installed
            },
        ),
        enabled = dependentEnabled,
        onClick = {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, WebServer.TEST_URL.toUri()))
            } catch (_: ActivityNotFoundException) {
                // No browser on the device.
            }
        },
    )
    Preference(
        R.drawable.ic_verified_user_24dp,
        stringResource(R.string.pref_webserver_certificate),
        enabled = dependentEnabled,
        onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                exportCertificate.launch(CERTIFICATE_FILE_NAME)
            } else {
                scope.launch {
                    val certificate = viewModel.certificateDer()
                    if (certificate == null) {
                        Toast.makeText(context, certificateFailed, Toast.LENGTH_SHORT).show()
                    } else {
                        context.startActivity(
                            KeyChain.createInstallIntent().putExtra(KeyChain.EXTRA_CERTIFICATE, certificate).putExtra(KeyChain.EXTRA_NAME, "AdAway"),
                        )
                    }
                }
            }
        },
    )
    SwitchPreference(
        R.drawable.ic_image_24dp,
        stringResource(R.string.pref_webserver_icon),
        checked = settings.webServerIcon,
        onCheckedChange = viewModel::setIcon,
        enabled = dependentEnabled,
    )
    error?.let { WebServerErrorDialog(it, onDismiss = viewModel::dismissError) }
    if (exported) {
        AdAwayAlertDialog(
            onDismissRequest = { exported = false },
            title = stringResource(R.string.pref_webserver_certificate_dialog_title),
            message = stringResource(R.string.pref_webserver_certificate_dialog_content),
            confirmButton = DialogButton(stringResource(R.string.pref_webserver_certificate_dialog_action), onClick = {
                exported = false
                try {
                    context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                    // No security settings to open on this device.
                }
            }),
        )
    }
}

@Composable
private fun WebServerErrorDialog(error: WebServerError, onDismiss: () -> Unit) {
    AdAwayAlertDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.webserver_error_title),
        message = stringResource(
            when (error) {
                WebServerError.ROOT_DENIED -> R.string.root_error_denied_text
                WebServerError.RESOURCES -> R.string.webserver_error_resources
                WebServerError.ADDRESS_IN_USE -> R.string.webserver_error_address_in_use
                WebServerError.START_FAILED -> R.string.webserver_error_start_failed
            },
        ),
        confirmButton = DialogButton(stringResource(R.string.close), onClick = onDismiss),
    )
}
