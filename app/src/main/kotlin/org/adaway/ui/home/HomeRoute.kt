package org.adaway.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import org.adaway.R
import org.adaway.data.db.RuleType
import org.adaway.sources.UpdateProgress
import org.adaway.ui.common.ApplyBanner
import org.adaway.ui.common.BrandHeader
import org.adaway.ui.common.UpdateErrorDialog
import org.adaway.ui.common.appViewModel
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.Text as TvText

class HomeNavigation(val openSources: () -> Unit, val openLists: (RuleType) -> Unit)

@Composable
fun HomeRoute(isTelevision: Boolean, navigation: HomeNavigation) {
    val viewModel = appViewModel(::HomeViewModel)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BrandHeader()
        val current = state ?: return@Column
        if (isTelevision) {
            TvHomeContent(current, progress, viewModel, navigation)
        } else {
            HomeContent(current, progress, viewModel, navigation)
        }
    }
    error?.let { UpdateErrorDialog(it, onRetry = viewModel::sync, onDismiss = viewModel::dismissError) }
}

@Composable
private fun HomeContent(
    state: HomeState,
    progress: UpdateProgress?,
    viewModel: HomeViewModel,
    navigation: HomeNavigation,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.legacyImportFailed) {
                LegacyImportFailedCard {
                    TextButton(onClick = viewModel::dismissLegacyImportFailure) { Text(stringResource(R.string.dismiss)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuleType.entries.forEach { type ->
                    Card(onClick = { navigation.openLists(type) }, modifier = Modifier.weight(1f)) {
                        CounterContent(type, state.summary.count(type), modifier = Modifier.fillMaxWidth())
                    }
                }
            }
            Card(onClick = navigation.openSources, modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    SourcesSummaryContent(state.sources, progress, modifier = Modifier.weight(1f))
                    IconButton(onClick = viewModel::sync, enabled = progress == null) {
                        Icon(painterResource(R.drawable.ic_sync), contentDescription = stringResource(R.string.sync_sources))
                    }
                }
            }
        }
        if (state.configurationChanged) {
            ApplyBanner(
                applying = progress != null,
                onApply = viewModel::apply,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun TvHomeContent(
    state: HomeState,
    progress: UpdateProgress?,
    viewModel: HomeViewModel,
    navigation: HomeNavigation,
) {
    val initialFocus = remember { FocusRequester() }
    Row(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.legacyImportFailed) {
                LegacyImportFailedCard {
                    Button(onClick = viewModel::dismissLegacyImportFailure) { TvText(stringResource(R.string.dismiss)) }
                }
            }
            TvCard(onClick = navigation.openSources, modifier = Modifier.fillMaxWidth().focusRequester(initialFocus)) {
                SourcesSummaryContent(state.sources, progress, modifier = Modifier.padding(16.dp))
            }
            Button(onClick = viewModel::sync, enabled = progress == null) {
                TvText(stringResource(R.string.sync_sources))
            }
            if (state.configurationChanged) {
                Text(
                    text = stringResource(R.string.apply_needed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Button(onClick = viewModel::apply, enabled = progress == null) {
                    TvText(stringResource(R.string.apply))
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            RuleType.entries.forEach { type ->
                TvCard(onClick = { navigation.openLists(type) }, modifier = Modifier.fillMaxWidth()) {
                    CounterContent(type, state.summary.count(type), modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
    LaunchedEffect(Unit) { initialFocus.requestFocus() }
}
