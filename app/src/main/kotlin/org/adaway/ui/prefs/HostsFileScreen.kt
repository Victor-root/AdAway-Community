package org.adaway.ui.prefs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.adaway.R
import org.adaway.root.HostsFile
import org.adaway.ui.common.AdAwayScreen
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.LocalAdAwayColors
import java.io.IOException

/** What the system hosts file holds, as the apps read it. */
private sealed interface HostsContent {
    data object Loading : HostsContent

    data class Lines(val lines: List<String>) : HostsContent

    data object Unreadable : HostsContent
}

/** The system hosts file, read only. */
@Composable
fun HostsFileScreen(onBack: () -> Unit) {
    val colors = LocalAdAwayColors.current
    val content by produceState<HostsContent>(HostsContent.Loading) {
        value = withContext(Dispatchers.IO) {
            try {
                HostsContent.Lines(HostsFile.SYSTEM.readLines())
            } catch (_: IOException) {
                HostsContent.Unreadable
            }
        }
    }
    AdAwayScreen(title = HostsFile.SYSTEM.path, onBack = onBack) { padding ->
        when (val current = content) {
            HostsContent.Loading -> Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary)
            }
            is HostsContent.Lines -> LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = padding) {
                items(current.lines) { line ->
                    Text(
                        line,
                        style = AdAwayType.caption,
                        fontFamily = FontFamily.Monospace,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 1.dp),
                    )
                }
            }
            HostsContent.Unreadable -> Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.hosts_file_unreadable), style = AdAwayType.body2, color = colors.textSecondary)
            }
        }
    }
}
