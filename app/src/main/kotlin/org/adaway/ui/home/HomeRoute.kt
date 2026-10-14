package org.adaway.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import org.adaway.R
import org.adaway.ui.common.BrandHeader
import org.adaway.ui.common.appViewModel
import androidx.tv.material3.Text as TvText

@Composable
fun HomeRoute(isTelevision: Boolean) {
    val viewModel = appViewModel(::HomeViewModel)
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BrandHeader()
        val current = state ?: return@Column
        if (isTelevision) {
            TvHomeContent(current, onDismissLegacyImportFailure = viewModel::dismissLegacyImportFailure)
        } else {
            HomeContent(current, onDismissLegacyImportFailure = viewModel::dismissLegacyImportFailure)
        }
    }
}

@Composable
private fun HomeContent(state: HomeState, onDismissLegacyImportFailure: () -> Unit) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.legacyImportFailed) {
            LegacyImportFailedCard {
                TextButton(onClick = onDismissLegacyImportFailure) { Text(stringResource(R.string.dismiss)) }
            }
        }
        MethodCard(state.method)
        SourcesCard(state)
        UserListsCard(state)
    }
}

@Composable
private fun TvHomeContent(state: HomeState, onDismissLegacyImportFailure: () -> Unit) {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.legacyImportFailed) {
            LegacyImportFailedCard {
                Button(onClick = onDismissLegacyImportFailure) { TvText(stringResource(R.string.dismiss)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MethodCard(state.method, modifier = Modifier.weight(1f))
            SourcesCard(state, modifier = Modifier.weight(1f))
        }
        UserListsCard(state)
    }
}
