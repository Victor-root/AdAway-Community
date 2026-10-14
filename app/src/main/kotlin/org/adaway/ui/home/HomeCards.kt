package org.adaway.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.adaway.R
import org.adaway.data.db.RuleType
import org.adaway.data.settings.BlockingMethod

/** Summary cards shared by the mobile and TV home screens. */
@Composable
fun MethodCard(method: BlockingMethod, modifier: Modifier = Modifier) {
    val label = when (method) {
        BlockingMethod.ROOT -> stringResource(R.string.home_method_root)
        BlockingMethod.VPN -> stringResource(R.string.home_method_vpn)
        BlockingMethod.UNSET -> return
    }
    SummaryCard(title = R.string.home_blocking_method, modifier = modifier) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun SourcesCard(state: HomeState, modifier: Modifier = Modifier) {
    SummaryCard(title = R.string.home_sources, modifier = modifier) {
        Text(
            text = pluralStringResource(
                R.plurals.home_sources_enabled,
                state.sources.enabled,
                state.sources.enabled,
                state.sources.total,
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
fun UserListsCard(state: HomeState, modifier: Modifier = Modifier) {
    SummaryCard(title = R.string.home_your_lists, modifier = modifier) {
        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
            RuleCount(R.string.home_blocked, state.userRules[RuleType.BLOCKED] ?: 0)
            RuleCount(R.string.home_allowed, state.userRules[RuleType.ALLOWED] ?: 0)
            RuleCount(R.string.home_redirected, state.userRules[RuleType.REDIRECTED] ?: 0)
        }
    }
}

@Composable
fun LegacyImportFailedCard(modifier: Modifier = Modifier, dismissButton: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.home_legacy_import_failed), style = MaterialTheme.typography.bodyMedium)
            Row(modifier = Modifier.align(Alignment.End)) { dismissButton() }
        }
    }
}

@Composable
private fun RuleCount(@StringRes label: Int, count: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SummaryCard(@StringRes title: Int, modifier: Modifier, content: @Composable () -> Unit) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}
