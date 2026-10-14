package org.adaway.ui.prefs

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
import org.adaway.system.launchOrTell
import org.adaway.ui.common.AdAwayScreen
import org.adaway.ui.common.appViewModel

private const val JSON_MIME_TYPE = "application/json"
private const val BACKUP_FILE_NAME = "adaway-backup.json"

/** Saves the sources and the user rules to a file, or adds those of a file. */
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
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val message = if (viewModel.restore(uri)) R.string.import_success else R.string.import_failed
            Toast.makeText(context, resources.getString(message), Toast.LENGTH_LONG).show()
        }
    }
    AdAwayScreen(title = stringResource(R.string.pref_backup_restore_title), onBack = onBack) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Preference(
                R.drawable.ic_save_24dp,
                stringResource(R.string.pref_backup),
                stringResource(R.string.pref_backup_summary),
                onClick = { export.launchOrTell(BACKUP_FILE_NAME, context) },
            )
            Preference(
                R.drawable.ic_settings_backup_restore_24dp,
                stringResource(R.string.pref_restore),
                stringResource(R.string.pref_restore_summary),
                onClick = { restore.launchOrTell(restoreMimeTypes(), context) },
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
