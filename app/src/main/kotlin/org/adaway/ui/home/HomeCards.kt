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
import org.adaway.sources.UpdateProgress
import org.adaway.ui.common.UpdateProgressLine
import org.adaway.ui.common.formatCount
import org.adaway.ui.common.ruleTypeLabel
import org.adaway.ui.theme.ruleTypeColor

/** Contents shared by the mobile and TV home screens. Text colors are explicit for both card kinds. */
@Composable
fun CounterContent(type: RuleType, count: Long, modifier: Modifier = Modifier) {
    @StringRes val description = when (type) {
        RuleType.BLOCKED -> R.string.home_blocked_description
        RuleType.ALLOWED -> R.string.home_allowed_description
        RuleType.REDIRECTED -> R.string.home_redirected_description
    }
    Column(modifier = modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(formatCount(count), style = MaterialTheme.typography.headlineSmall, color = ruleTypeColor(type))
        Text(ruleTypeLabel(type), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            stringResource(description),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun SourcesSummaryContent(stats: SourceStats, progress: UpdateProgress?, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(R.string.home_sources),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            pluralStringResource(R.plurals.home_sources_up_to_date, stats.upToDate, stats.upToDate),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (stats.pending > 0) {
            Text(
                pluralStringResource(R.plurals.home_sources_pending, stats.pending, stats.pending),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (stats.failed > 0) {
            Text(
                pluralStringResource(R.plurals.home_sources_failed, stats.failed, stats.failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        progress?.let { UpdateProgressLine(it, modifier = Modifier.padding(top = 4.dp)) }
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
