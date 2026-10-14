package org.adaway.root

import android.os.Process
import android.util.Log
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.adaway.BuildConfig
import org.adaway.system.RootAccess
import org.adaway.system.shellQuote
import java.io.File
import java.io.IOException

enum class RootError { ROOT_DENIED, PRIVATE_FILE, NOT_ENOUGH_SPACE, COPY_FAILED, REVERT_FAILED }

class RootException(val error: RootError) : Exception(error.name)

/**
 * Installs a hosts file as the system one, with the root rights. The file the apps read is always
 * `/system/etc/hosts`. When a "systemless hosts" module (Magisk, KernelSU, APatch) provides it,
 * the module file is written, otherwise the system partition, remounted for the time of the copy.
 * The file is rewritten in place, then read back through `/system/etc/hosts` to check it.
 */
class HostsInstaller(private val directory: File, private val rootAccess: RootAccess) {
    /** The hosts file of the device before the first installation, put back when blocking stops. */
    private val original = File(directory, "original_hosts")

    /** Whether the system hosts file is one this app wrote. Readable without root. */
    fun isInstalled(): Boolean = try {
        HostsFile.SYSTEM.bufferedReader().use { it.readLine() }?.startsWith(HostsFile.SIGNATURE) == true
    } catch (_: IOException) {
        false
    }

    /** Throws a [RootException] when the file cannot be installed. */
    suspend fun install(hosts: File) = withContext(Dispatchers.IO) {
        requireRoot()
        if (!original.exists() && !isInstalled()) saveOriginal()
        copyToSystem(hosts, RootError.COPY_FAILED)
    }

    /** Puts back the hosts file of the device, or the Android default one when it was never saved. */
    suspend fun revert(defaultContent: String) = withContext(Dispatchers.IO) {
        requireRoot()
        val source = if (original.exists()) {
            original
        } else {
            File(directory, "default_hosts").apply {
                try {
                    writeText(defaultContent)
                } catch (exception: IOException) {
                    debugLog("Default hosts not written: $exception")
                    throw RootException(RootError.REVERT_FAILED)
                }
            }
        }
        copyToSystem(source, RootError.REVERT_FAILED)
        source.delete()
    }

    private suspend fun requireRoot() {
        if (!rootAccess.request()) throw RootException(RootError.ROOT_DENIED)
    }

    /** Keeps the device file in the private directory, readable and deletable by the app. */
    private fun saveOriginal() {
        directory.mkdirs()
        val uid = Process.myUid()
        val saved = exec("cat ${shellQuote(HostsFile.SYSTEM.path)} > ${shellQuote(original.path)} && chown $uid:$uid ${shellQuote(original.path)}")
        if (!saved.isSuccess) {
            original.delete()
            debugLog("Original hosts not saved: ${saved.err}")
        }
    }

    private fun copyToSystem(source: File, failure: RootError) {
        val target = moduleHosts() ?: HostsFile.SYSTEM.path
        val remounted = if (exec("[ -w ${shellQuote(target)} ]").isSuccess) {
            null
        } else {
            remountWritable(target) ?: run {
                debugLog("No systemless hosts module, and $target cannot be made writable")
                throw RootException(failure)
            }
        }
        try {
            val copy = exec("cat ${shellQuote(source.path)} > ${shellQuote(target)} && chown 0:0 ${shellQuote(target)} && chmod 644 ${shellQuote(target)}")
            if (!copy.isSuccess) {
                debugLog("Copy to $target failed: ${copy.err}")
                val noSpace = copy.err.any { it.contains("No space left", ignoreCase = true) }
                throw RootException(if (noSpace && failure == RootError.COPY_FAILED) RootError.NOT_ENOUGH_SPACE else failure)
            }
            // What the apps read, through the module mount if any.
            if (!exec("cmp -s ${shellQuote(source.path)} ${shellQuote(HostsFile.SYSTEM.path)}").isSuccess) {
                debugLog("The installed hosts file differs from the written one")
                throw RootException(failure)
            }
        } finally {
            remounted?.let { exec("mount -o ro,remount ${shellQuote(it)}") }
        }
    }

    /** The hosts file of an enabled systemless module, if one provides it. */
    private fun moduleHosts(): String? {
        val found = exec(
            "for file in /data/adb/modules/*/system/etc/hosts; do " +
                "module=\${file%/system/etc/hosts}; " +
                "[ -f \"\$file\" ] && [ ! -e \"\$module/disable\" ] && [ ! -e \"\$module/remove\" ] && echo \"\$file\" && break; " +
                "done",
        )
        return found.out.firstOrNull { it.isNotBlank() }
    }

    /** Remounts writable the mount holding [path], returning its mount point, or null when it fails. */
    private fun remountWritable(path: String): String? {
        val mountPoints = exec("cat /proc/mounts").out.mapNotNull { line -> line.split(' ').getOrNull(1) }
        val mountPoint = mountPoints
            .filter { path == it || path.startsWith(if (it.endsWith('/')) it else "$it/") }
            .maxByOrNull { it.length }
            ?: return null
        val remount = exec("mount -o rw,remount ${shellQuote(mountPoint)}")
        if (!remount.isSuccess) {
            debugLog("Remount of $mountPoint failed: ${remount.err}")
            return null
        }
        return mountPoint
    }

    private fun exec(command: String): Shell.Result = Shell.cmd(command).exec()

    private fun debugLog(message: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, message)
    }

    private companion object {
        const val TAG = "AdAwayRoot"
    }
}
