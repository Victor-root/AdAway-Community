package org.adaway.ui.sources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.adaway.R
import org.adaway.data.db.SourceEntity
import org.adaway.ui.common.AppButton
import org.adaway.ui.common.TvScreen
import org.adaway.ui.common.ruleCountText
import org.adaway.ui.common.sourceLabel
import org.adaway.ui.common.sourceStatus
import androidx.tv.material3.ListItem as TvListItem
import androidx.tv.material3.Text as TvText

/** The TV list of hosts sources, made for the remote. */
@Composable
fun TvSourcesScreen(sources: List<Pair<SourceEntity, String>>, viewModel: SourcesViewModel, navigation: SourcesNavigation) {
    val configurationChanged by viewModel.configurationChanged.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val applying = progress != null
    var selected by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDeletion by rememberSaveable { mutableStateOf(false) }
    TvScreen(
        title = stringResource(R.string.sources_title),
        actions = {
            AppButton(stringResource(R.string.source_add), navigation.add, isTelevision = true)
            if (configurationChanged) {
                AppButton(stringResource(R.string.apply), viewModel::apply, isTelevision = true, enabled = !applying)
            }
        },
    ) {
        if (configurationChanged) {
            Text(
                stringResource(if (applying) R.string.apply_in_progress else R.string.apply_needed),
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        SourceList(sources, PaddingValues(vertical = 16.dp)) { source, label ->
            TvListItem(
                selected = false,
                onClick = { selected = source.id },
                headlineContent = { TvText(label) },
                supportingContent = { SourceDetails(source, label = null, isTelevision = true) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    val source = sources.firstOrNull { it.first.id == selected }?.first ?: return
    if (confirmDeletion) {
        AlertDialog(
            onDismissRequest = { confirmDeletion = false },
            title = { Text(stringResource(R.string.source_delete_confirm_title)) },
            text = { Text(sourceLabel(source)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(source); confirmDeletion = false; selected = null }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeletion = false }) { Text(stringResource(R.string.cancel)) } },
        )
    } else {
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(sourceLabel(source)) },
            text = {
                Column {
                    TextButton(onClick = { viewModel.setEnabled(source, !source.enabled); selected = null }) {
                        Text(stringResource(if (source.enabled) R.string.disable else R.string.enable))
                    }
                    TextButton(onClick = { selected = null; navigation.edit(source.id) }) {
                        Text(stringResource(R.string.edit))
                    }
                    TextButton(onClick = { confirmDeletion = true }) { Text(stringResource(R.string.delete)) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { selected = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SourceList(
    sources: List<Pair<SourceEntity, String>>,
    padding: PaddingValues,
    item: @Composable (SourceEntity, String) -> Unit,
) {
    if (sources.isEmpty()) {
        Text(
            stringResource(R.string.sources_empty),
            modifier = Modifier.padding(padding).padding(16.dp),
            color = MaterialTheme.colorScheme.onBackground,
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 88.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sources, key = { it.first.id }) { (source, label) -> item(source, label) }
    }
}

/**
 * Label (unless shown by the container), address, status and rule count of a source. On TV the
 * texts follow the focus colors of the list item.
 */
@Composable
private fun SourceDetails(source: SourceEntity, label: String?, isTelevision: Boolean, modifier: Modifier = Modifier) {
    val line: @Composable (String, TextStyle, Color, Int) -> Unit = { text, style, color, maxLines ->
        if (isTelevision) {
            TvText(text, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        } else {
            Text(text, style = style, color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        }
    }
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = modifier.alpha(if (source.enabled) 1f else DISABLED_ALPHA),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (label != null) {
            line(label, MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold), MaterialTheme.colorScheme.onSurface, 1)
        }
        line(source.url, MaterialTheme.typography.bodySmall, secondary, 1)
        val statusColor = if (source.enabled && source.syncError != null) MaterialTheme.colorScheme.error else secondary
        // The status may explain a failure: it is never cut.
        line(sourceStatus(source), MaterialTheme.typography.bodySmall, statusColor, Int.MAX_VALUE)
        if (source.enabled && source.ruleCount > 0) {
            line(ruleCountText(source.ruleCount), MaterialTheme.typography.labelMedium, MaterialTheme.colorScheme.primary, 1)
        }
    }
}

private const val DISABLED_ALPHA = 0.6f
