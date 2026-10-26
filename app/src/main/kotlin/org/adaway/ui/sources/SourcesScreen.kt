package org.adaway.ui.sources

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.adaway.R
import org.adaway.data.db.SourceEntity
import org.adaway.ui.common.AdAwayFab
import org.adaway.ui.common.AdAwayScreen
import org.adaway.ui.common.AdAwaySnackbar
import org.adaway.ui.common.ApplyConfigurationSnackbar
import org.adaway.ui.common.adAwayCheckboxColors
import org.adaway.ui.common.hostCountText
import org.adaway.ui.common.sourceUpdateText
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.LocalAdAwayColors

/** How long the undo action of a deletion stays offered. */
private const val UNDO_MILLIS = 10_000L

/** The phone list of hosts sources: a card per source, a checkbox to enable it, and a tap to edit it. */
@Composable
fun SourcesScreen(sources: List<Pair<SourceEntity, String>>, viewModel: SourcesViewModel, navigation: SourcesNavigation) {
    val deleted by viewModel.recentlyDeleted.collectAsStateWithLifecycle()
    LaunchedEffect(deleted) {
        val source = deleted ?: return@LaunchedEffect
        delay(UNDO_MILLIS)
        viewModel.forgetDeleted(source)
    }
    AdAwayScreen(
        title = stringResource(R.string.sources_title),
        onBack = navigation.back,
        floatingActionButton = {
            AdAwayFab(painterResource(R.drawable.ic_add_black_24px), stringResource(R.string.source_add), onClick = navigation.add)
        },
        // The offer to apply shows at once, under the one to undo the deletion.
        snackbar = {
            deleted?.let { source ->
                AdAwaySnackbar(
                    stringResource(R.string.source_deleted),
                    actionLabel = stringResource(R.string.undo),
                    onAction = { viewModel.restore(source) },
                )
            }
            ApplyConfigurationSnackbar(viewModel.applier)
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = padding.calculateBottomPadding() + 88.dp),
        ) {
            items(sources, key = { it.first.id }) { (source, label) ->
                SourceCard(source, label, onToggle = { viewModel.setEnabled(source, !source.enabled) }, onEdit = { navigation.edit(source.id) })
            }
        }
    }
}

@Composable
private fun SourceCard(source: SourceEntity, label: String, onToggle: () -> Unit, onEdit: () -> Unit) {
    val colors = LocalAdAwayColors.current
    Surface(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = colors.card,
        shadowElevation = 2.dp,
    ) {
        Box {
            Checkbox(
                checked = source.enabled,
                onCheckedChange = { onToggle() },
                colors = adAwayCheckboxColors(),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 4.dp),
            )
            Column(modifier = Modifier.padding(start = 72.dp, top = 8.dp, end = 16.dp, bottom = 8.dp)) {
                Text(label, style = AdAwayType.subtitle1, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(source.url, style = AdAwayType.small, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        sourceUpdateText(source),
                        style = AdAwayType.subtitle2,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    if (source.enabled && source.ruleCount > 0) {
                        Text(hostCountText(source.ruleCount), style = AdAwayType.subtitle2, color = colors.textPrimary)
                    }
                }
            }
        }
    }
}
