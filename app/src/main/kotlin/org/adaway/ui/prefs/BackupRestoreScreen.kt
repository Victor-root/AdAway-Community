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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import org.adaway.R
import org.adaway.system.launchOr
import org.adaway.system.launchOrTell
import org.adaway.ui.common.AdAwayScreen
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
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON_MIME_TYPE)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = viewModel.displayName(uri)
            val message = if (viewModel.export(uri)) R.string.export_success else R.string.export_failed
            Toast.makeText(context, resources.getString(message, name), Toast.LENGTH_LONG).show()
        }
    }
    val exportToDownloads = {
        scope.launch {
            val path = viewModel.exportToDownloads()
            val message = path?.let { resources.getString(R.string.export_success, it) }
                ?: resources.getString(R.string.export_failed, BACKUP_FILE_NAME)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            exportToDownloads()
        } else {
            Toast.makeText(context, resources.getString(R.string.export_failed, BACKUP_FILE_NAME), Toast.LENGTH_LONG).show()
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
                    export.launchOr(BACKUP_FILE_NAME) {
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
                onClick = { restore.launchOr(restoreMimeTypes()) { restoreWithFileManager.launchOrTell("*/*", context) } },
            )
        }
    }
}

/** Before Android 10, the file pickers do not all know the JSON type of a file. */
private fun restoreMimeTypes(): Array<String> = when {
    Build.VERSION.SDK_INT < Build.VERSION_CODES.P -> arrayOf("*/*")
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> arrayOf(JSON_MIME_TYPE, "application/octet-stream")
    else -> arrayOf(JSON_MIME_TYPE)
}
