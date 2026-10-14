package org.adaway.ui.prefs

import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.adaway.AppContainer
import org.json.JSONException
import java.io.IOException

class BackupRestoreViewModel(private val container: AppContainer) : ViewModel() {
    private val contentResolver = container.applicationContext.contentResolver

    /** Returns whether it worked. */
    suspend fun export(uri: Uri): Boolean = try {
        withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri)?.use { container.backupFile.export(it) } } != null
    } catch (_: IOException) {
        false
    } catch (_: SecurityException) {
        false
    }

    /** Returns whether it worked. */
    suspend fun restore(uri: Uri): Boolean = try {
        withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)?.use { container.backupFile.restore(it) } } != null
    } catch (_: IOException) {
        false
    } catch (_: JSONException) {
        false
    } catch (_: SecurityException) {
        false
    }

    /** The file name the user picked. */
    suspend fun displayName(uri: Uri): String = withContext(Dispatchers.IO) {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
    }
}
