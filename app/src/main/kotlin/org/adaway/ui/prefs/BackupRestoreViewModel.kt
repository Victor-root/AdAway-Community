package org.adaway.ui.prefs

import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.adaway.AppContainer
import org.json.JSONException
import java.io.File
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

    /**
     * Saves the backup in the Downloads folder, for the devices without a file picker, as some TVs.
     * Returns its path, or null if it failed. Before Android 10 it needs the storage permission.
     */
    suspend fun exportToDownloads(): String? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) exportToMediaStore() else exportToDownloadsFolder()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun exportToMediaStore(): String? {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, BACKUP_FILE_NAME)
            put(MediaStore.Downloads.MIME_TYPE, JSON_MIME_TYPE)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
        if (!export(uri)) {
            contentResolver.delete(uri, null, null)
            return null
        }
        contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
        return "${Environment.DIRECTORY_DOWNLOADS}/${displayName(uri)}"
    }

    /** Before Android 10, the public folders are reached by their path. A former backup is kept. */
    @Suppress("DEPRECATION")
    private suspend fun exportToDownloadsFolder(): String? {
        val folder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!folder.isDirectory && !folder.mkdirs()) return null
        val base = BACKUP_FILE_NAME.substringBeforeLast('.')
        val file = generateSequence(1) { it + 1 }
            .map { index -> File(folder, if (index == 1) BACKUP_FILE_NAME else "$base ($index).json") }
            .first { !it.exists() }
        return if (export(Uri.fromFile(file))) "${Environment.DIRECTORY_DOWNLOADS}/${file.name}" else null
    }

    /** The name of the file of [uri]. */
    suspend fun displayName(uri: Uri): String = withContext(Dispatchers.IO) {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
    }

    companion object {
        const val JSON_MIME_TYPE = "application/json"
        const val BACKUP_FILE_NAME = "adaway-backup.json"
    }
}
