package org.adaway.ui.tv

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.adaway.R
import org.adaway.data.db.SourceEntity
import org.adaway.ui.common.ApplyProgressSnackbar
import org.adaway.ui.common.DialogButton
import org.adaway.ui.common.appViewModel
import org.adaway.ui.common.hostCountText
import org.adaway.ui.common.sourceUpdateText
import org.adaway.ui.sources.SourcesNavigation
import org.adaway.ui.sources.SourcesViewModel
import org.adaway.ui.sources.sortedByLabel
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.LocalAdAwayColors

/** No source card focused: the add button is, as when the screen opens. */
private const val NO_SOURCE = -1L

/** The TV list of hosts sources: a card per source, whose choices enable or edit it. */
@Composable
fun TvSourcesScreen(navigation: SourcesNavigation) {
    val viewModel = appViewModel(::SourcesViewModel)
    val sources by viewModel.sources.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Pair<SourceEntity, String>?>(null) }
    // The card focused last, focused again when coming back from the edit screen, as Android does.
    var focused by rememberSaveable { mutableLongStateOf(NO_SOURCE) }
    val addFocus = remember { FocusRequester() }
    val pendingApply = isApplyPending(viewModel.applier)
    TvScreen(
        header = {
            TvHeader(
                icon = painterResource(R.drawable.ic_collections_bookmark_24dp),
                title = stringResource(R.string.sources_title),
                hint = if (pendingApply) stringResource(R.string.configuration_changed) else null,
            ) {
                TvHeaderButton(
                    stringResource(R.string.add),
                    painterResource(R.drawable.ic_add_black_24px),
                    onClick = navigation.add,
                    modifier = Modifier
                        .focusRequester(addFocus)
                        .onFocusChanged { if (it.isFocused) focused = NO_SOURCE }
                        .padding(end = 8.dp),
                )
                if (pendingApply) TvApplyButton(viewModel.applier, afterApply = addFocus)
            }
        },
        snackbar = { ApplyProgressSnackbar(viewModel.applier) },
    ) {
        val list = sortedByLabel(sources.orEmpty())
        if (sources != null && list.isEmpty()) {
            TvEmptyText(stringResource(R.string.tv_lists_empty))
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp)) {
                items(list, key = { it.first.id }) { item ->
                    val (source, label) = item
                    val cardFocus = remember { FocusRequester() }
                    SourceCard(
                        source,
                        label,
                        onClick = { selected = item },
                        modifier = Modifier
                            .focusRequester(cardFocus)
                            .onFocusChanged { if (it.isFocused) focused = source.id },
                    )
                    LaunchedEffect(Unit) { if (source.id == focused) cardFocus.requestFocus() }
                }
            }
        }
    }
    selected?.let { (source, label) ->
        TvListDialog(
            title = label,
            items = listOf(
                DialogButton(stringResource(if (source.enabled) R.string.disable else R.string.enable), onClick = { viewModel.setEnabled(source, !source.enabled) }),
                DialogButton(stringResource(R.string.edit), onClick = { navigation.edit(source.id) }),
            ),
            onDismiss = { selected = null },
        )
    }
    // Also when the card focused last is gone, its source deleted.
    val loaded = sources
    LaunchedEffect(loaded != null) {
        if (loaded != null && loaded.none { it.id == focused }) addFocus.requestFocus()
    }
}

/** Name, address, update status and size of a source, faded when disabled. */
@Composable
private fun SourceCard(source: SourceEntity, label: String, onClick: () -> Unit, modifier: Modifier) {
    val colors = LocalAdAwayColors.current
    TvCard(
        onClick = onClick,
        modifier = modifier
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
            .fillMaxWidth()
            .alpha(if (source.enabled) 1f else 0.5f),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(label, style = AdAwayType.subtitle1, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(source.url, style = AdAwayType.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row {
                Text(
                    sourceUpdateText(source),
                    style = AdAwayType.subtitle2,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (source.enabled && source.ruleCount > 0) {
                    Text(hostCountText(source.ruleCount), style = AdAwayType.subtitle2, color = colors.textPrimary)
                }
            }
        }
    }
}
