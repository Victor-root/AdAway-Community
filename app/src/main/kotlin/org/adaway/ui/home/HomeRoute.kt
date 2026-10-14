package org.adaway.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import org.adaway.R
import org.adaway.data.db.RuleType
import org.adaway.data.settings.BlockingMethod
import org.adaway.sources.UpdateProgress
import org.adaway.ui.common.ApplyBanner
import org.adaway.ui.common.BrandHeader
import org.adaway.ui.common.UpdateErrorDialog
import org.adaway.ui.common.VpnRefusal
import org.adaway.ui.common.VpnRefusalDialog
import org.adaway.ui.common.appViewModel
import org.adaway.ui.common.rememberVpnConsentRequest
import org.adaway.ui.common.vpnRefusal
import org.adaway.ui.theme.BrandGray
import org.adaway.ui.theme.BrandRed
import org.adaway.ui.theme.ruleTypeColor
import org.adaway.vpn.AlwaysOnMode
import androidx.tv.material3.Card as TvCard
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.Text as TvText

class HomeNavigation(val openSources: () -> Unit, val openLists: (RuleType) -> Unit)

@Composable
fun HomeRoute(isTelevision: Boolean, navigation: HomeNavigation) {
    val viewModel = appViewModel(::HomeViewModel)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val consentPrompt by viewModel.consentPrompt.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var refusal by remember { mutableStateOf<VpnRefusal?>(null) }
    var persistenceMode by remember { mutableStateOf<AlwaysOnMode?>(null) }
    var applyWhileOff by rememberSaveable { mutableStateOf(false) }
    val requestConsent = rememberVpnConsentRequest(
        onGranted = viewModel::enableBlocking,
        onRefused = { refusal = vpnRefusal(context) },
    )
    LaunchedEffect(consentPrompt) {
        if (consentPrompt) {
            viewModel.consentPromptShown()
            requestConsent()
        }
    }
    val current = state
    val actions = BlockingActions(
        toggle = { if (current?.blocking?.active == true) viewModel.disableBlocking() else requestConsent() },
        apply = {
            val blocking = current?.blocking
            if (blocking?.method == BlockingMethod.VPN && !blocking.active) applyWhileOff = true else viewModel.apply()
        },
        openPersistence = { persistenceMode = viewModel.alwaysOnMode() },
    )
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val blockingOff = current?.blocking?.let { it.method == BlockingMethod.VPN && !it.active } == true
        BrandHeader(color = if (blockingOff && !isTelevision) BrandGray else BrandRed)
        if (current == null) return@Column
        if (isTelevision) {
            TvHomeContent(current, progress, viewModel, navigation, actions)
        } else {
            HomeContent(current, progress, viewModel, navigation, actions)
        }
    }
    error?.let { UpdateErrorDialog(it, onRetry = viewModel::sync, onDismiss = viewModel::dismissError) }
    refusal?.let { VpnRefusalDialog(it, onDismiss = { refusal = null }) }
    persistenceMode?.let { VpnPersistenceDialog(isTelevision, it, onDismiss = { persistenceMode = null }) }
    if (applyWhileOff) {
        ApplyWhileOffDialog(
            onApply = viewModel::apply,
            onApplyAndEnable = {
                viewModel.apply()
                requestConsent()
            },
            onDismiss = { applyWhileOff = false },
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeState,
    progress: UpdateProgress?,
    viewModel: HomeViewModel,
    navigation: HomeNavigation,
    actions: BlockingActions,
) {
    val usesVpn = state.blocking.method == BlockingMethod.VPN
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.legacyImportFailed) {
                LegacyImportFailedCard {
                    TextButton(onClick = viewModel::dismissLegacyImportFailure) { Text(stringResource(R.string.dismiss)) }
                }
            }
            if (usesVpn) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    BlockingStatusContent(state.blocking, modifier = Modifier.padding(16.dp))
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
            if (usesVpn) {
                Card(onClick = actions.openPersistence, modifier = Modifier.fillMaxWidth()) {
                    VpnPersistenceContent(modifier = Modifier.padding(16.dp))
                }
            }
        }
        Column(modifier = Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            if (usesVpn) {
                FloatingActionButton(
                    onClick = { if (progress == null) actions.toggle() },
                    modifier = Modifier
                        .then(if (state.configurationChanged) Modifier else Modifier.navigationBarsPadding())
                        .padding(bottom = 16.dp),
                ) {
                    Icon(
                        painterResource(if (state.blocking.active) R.drawable.ic_pause else R.drawable.logo),
                        contentDescription = stringResource(R.string.home_toggle_blocking),
                    )
                }
            }
            if (state.configurationChanged) {
                ApplyBanner(applying = progress != null, onApply = actions.apply)
            }
        }
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
                Button(onClick = actions.apply, enabled = progress == null) {
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
            ButtonDefaults.colors(containerColor = BrandGray, contentColor = Color.White)
        },
    ) {
        TvIcon(painterResource(if (active) R.drawable.ic_pause else R.drawable.ic_power), contentDescription = null)
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
