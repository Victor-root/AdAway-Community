package org.adaway.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import org.adaway.R
import org.adaway.data.db.RuleType
import org.adaway.data.settings.BlockingMethod
import org.adaway.sources.UpdateProgress
import org.adaway.ui.common.BrandHeader
import org.adaway.ui.common.UpdateProgressLine
import org.adaway.ui.common.formatCount
import org.adaway.ui.common.ruleTypeLabel
import org.adaway.ui.theme.BrandRed
import org.adaway.ui.theme.LocalAdAwayColors
import org.adaway.ui.theme.ruleTypeColor
import org.adaway.vpn.AlwaysOnMode
import org.adaway.vpn.VpnStatus
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.Text as TvText

/** The TV home screen, made for the remote: every action is a focusable button or card. */
@Composable
fun TvHomeScreen(state: HomeState, progress: UpdateProgress?, viewModel: HomeViewModel, navigation: HomeNavigation, actions: BlockingActions) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BrandHeader()
        TvHomeContent(state, progress, viewModel, navigation, actions)
    }
}

@Composable
private fun TvHomeContent(
    state: HomeState,
    progress: UpdateProgress?,
    viewModel: HomeViewModel,
    navigation: HomeNavigation,
    actions: BlockingActions,
) {
    val usesVpn = state.blocking.method == BlockingMethod.VPN
    val initialFocus = remember { FocusRequester() }
    var alwaysOn by remember { mutableStateOf(false) }
    LifecycleResumeEffect(state.blocking) {
        alwaysOn = viewModel.alwaysOnMode().let { it == AlwaysOnMode.ON || it == AlwaysOnMode.LOCKDOWN }
        onPauseOrDispose {}
    }
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
            if (usesVpn) {
                BlockingStatusContent(state.blocking)
                if (alwaysOn) AlwaysOnIndicator()
                TvBlockingButton(active = state.blocking.active, onClick = actions.toggle, modifier = Modifier.focusRequester(initialFocus))
            }
            TvCard(
                onClick = navigation.openSources,
                modifier = Modifier.fillMaxWidth().then(if (usesVpn) Modifier else Modifier.focusRequester(initialFocus)),
            ) {
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
            if (usesVpn) {
                TvCard(onClick = actions.openPersistence, modifier = Modifier.fillMaxWidth()) {
                    VpnPersistenceContent(modifier = Modifier.padding(16.dp))
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

/** Gray to turn on, red to turn off. */
@Composable
private fun TvBlockingButton(active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = if (active) {
            ButtonDefaults.colors(containerColor = BrandRed, contentColor = Color.White)
        } else {
            ButtonDefaults.colors(containerColor = LocalAdAwayColors.current.headerOff, contentColor = Color.White)
        },
    ) {
        TvIcon(painterResource(if (active) R.drawable.ic_pause_24dp else R.drawable.ic_power), contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        TvText(stringResource(if (active) R.string.home_disable_blocking else R.string.home_enable_blocking))
    }
}

@Composable
private fun AlwaysOnIndicator() {
    val color = ruleTypeColor(RuleType.ALLOWED)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = color)
        Text(stringResource(R.string.home_always_on_active), style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

/** Contents shared by the mobile and TV home screens. Text colors are explicit for both card kinds. */
@Composable
private fun CounterContent(type: RuleType, count: Long, modifier: Modifier = Modifier) {
    @StringRes val description = when (type) {
        RuleType.BLOCKED -> R.string.home_blocked_description
        RuleType.ALLOWED -> R.string.home_allowed_description
        RuleType.REDIRECTED -> R.string.home_redirected_description
    }
    Column(modifier = modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(formatCount(count), style = MaterialTheme.typography.titleLarge, color = ruleTypeColor(type), maxLines = 1, softWrap = false)
        Text(ruleTypeLabel(type), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            stringResource(description),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SourcesSummaryContent(stats: SourceStats, progress: UpdateProgress?, modifier: Modifier = Modifier) {
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
private fun LegacyImportFailedCard(modifier: Modifier = Modifier, dismissButton: @Composable () -> Unit) {
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

/** On or off, and what is going on in between: waiting for a network, reconnecting, consent missing. */
@Composable
private fun BlockingStatusContent(blocking: BlockingState, modifier: Modifier = Modifier) {
    @StringRes val detail = when (blocking.vpnStatus) {
        VpnStatus.STARTING -> R.string.home_blocking_starting
        VpnStatus.WAITING_FOR_NETWORK -> R.string.home_blocking_waiting_for_network
        VpnStatus.RECONNECTING -> R.string.home_blocking_reconnecting
        VpnStatus.CONSENT_REQUIRED -> R.string.home_blocking_consent_required
        VpnStatus.RUNNING, VpnStatus.STOPPED -> null
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(if (blocking.active) R.string.home_blocking_enabled else R.string.home_blocking_disabled),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        detail?.let {
            Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun VpnPersistenceContent(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(painterResource(R.drawable.ic_vpn_key_24dp), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Column {
            Text(stringResource(R.string.vpn_persistence_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(
                stringResource(R.string.vpn_persistence_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
