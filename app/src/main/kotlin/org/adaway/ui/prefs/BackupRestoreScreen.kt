package org.adaway.ui.prefs

import android.Manifest
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import org.adaway.R
import org.adaway.system.launchOr
import org.adaway.system.launchOrTell
import org.adaway.ui.common.AdAwayAlertDialog
import org.adaway.ui.common.AdAwayScreen
import org.adaway.ui.common.DialogButton
import org.adaway.ui.common.appViewModel
import org.adaway.ui.prefs.BackupRestoreViewModel.Companion.BACKUP_FILE_NAME
import org.adaway.ui.prefs.BackupRestoreViewModel.Companion.JSON_MIME_TYPE

/**
 * Saves the sources and the user rules to a file, or adds those of a file. Without the file picker
 * of Android, as on some TVs, the backup goes to the Downloads folder and the restore opens the
 * file manager installed.
 */
@Composable
fun BackupRestoreScreen(onBack: () -> Unit) {
    val viewModel = appViewModel(::BackupRestoreViewModel)
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    // Where the last backup went, told in a dialog that stays until read.
    var savedTo by rememberSaveable { mutableStateOf<String?>(null) }
    val exportFailed = { name: String ->
        Toast.makeText(context, resources.getString(R.string.export_failed, name), Toast.LENGTH_LONG).show()
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON_MIME_TYPE)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = viewModel.displayName(uri)
            if (viewModel.export(uri)) savedTo = name else exportFailed(name)
        }
    }
    val exportToDownloads = {
        scope.launch {
            val path = viewModel.exportToDownloads()
            if (path != null) savedTo = path else exportFailed(BACKUP_FILE_NAME)
        }
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            exportToDownloads()
        } else {
            exportFailed(BACKUP_FILE_NAME)
        }
    }
    val restoreFrom = { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val message = if (viewModel.restore(uri)) R.string.import_success else R.string.import_failed
                Toast.makeText(context, resources.getString(message), Toast.LENGTH_LONG).show()
            }
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), restoreFrom)
    val restoreWithFileManager = rememberLauncherForActivityResult(ActivityResultContracts.GetContent(), restoreFrom)
    AdAwayScreen(title = stringResource(R.string.pref_backup_restore_title), onBack = onBack) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Preference(
                R.drawable.ic_save_24dp,
                stringResource(R.string.pref_backup),
                stringResource(R.string.pref_backup_summary),
                onClick = {
                    export.launchOr(BACKUP_FILE_NAME, context) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            exportToDownloads()
                        } else {
                            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        }
                    }
                },
            )
            Preference(
                R.drawable.ic_settings_backup_restore_24dp,
                stringResource(R.string.pref_restore),
                stringResource(R.string.pref_restore_summary),
                onClick = { restore.launchOr(restoreMimeTypes(), context) { restoreWithFileManager.launchOrTell(restoreMimeTypes().first(), context) } },
            )
        }
    }
    savedTo?.let { path ->
        AdAwayAlertDialog(
            onDismissRequest = { savedTo = null },
            title = stringResource(R.string.pref_backup),
            message = stringResource(R.string.export_success, path),
            confirmButton = DialogButton(stringResource(android.R.string.ok), onClick = { savedTo = null }),
        )
    }
}

/**
 * Before Android 10, the file pickers do not all know the JSON type of a file. Any type is only
 * asked before Android 9: later, it also wakes Android's photo picker, which, without the file
 * picker of Android, hands the request back to itself endlessly.
 */
private fun restoreMimeTypes(): Array<String> = when {
    Build.VERSION.SDK_INT < Build.VERSION_CODES.P -> arrayOf("*/*")
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> arrayOf(JSON_MIME_TYPE, "application/octet-stream")
    else -> arrayOf(JSON_MIME_TYPE)
}
