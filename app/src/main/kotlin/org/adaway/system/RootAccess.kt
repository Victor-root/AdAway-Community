package org.adaway.system

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RootAccess {
    /** Asks the root manager for access. Shows its prompt the first time. */
    suspend fun request(): Boolean = withContext(Dispatchers.IO) {
        val granted = Shell.getShell().isRoot
        // A refused request leaves a non-root shell cached: drop it so that a retry asks again.
        if (!granted) Shell.getCachedShell()?.close()
        granted
    }
}
