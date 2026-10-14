package org.adaway.ui.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.adaway.BuildConfig
import org.adaway.R
import org.adaway.blocklist.UpdateOutcome
import org.adaway.data.db.RuleType
import org.adaway.data.settings.BlockingMethod
import org.adaway.sources.UpdateProgress
import org.adaway.ui.common.DISABLED_ALPHA
import org.adaway.ui.common.updateProgressText
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.LocalAdAwayColors
import org.adaway.ui.theme.Pastel

class HomeScreenActions(
    val openLists: (RuleType) -> Unit,
    val openSources: () -> Unit,
    val sync: () -> Unit,
    val toggleBlocking: () -> Unit,
    val openPersistence: () -> Unit,
    val dismissLegacyImportFailure: () -> Unit,
)

/** Header share of the screen height, and the logo's. */
private const val HEADER_HEIGHT_FRACTION = 0.25f
private const val LOGO_HEIGHT_FRACTION = 0.12f
/** How far the counter cards climb onto the header. */
private val CounterOverlap = 30.dp
private val BadgeSize = 48.dp
private val HomeCardShape = RoundedCornerShape(22.dp)

/** Phone home screen: header, counters straddling it, a scrolling zone of cards, and the bottom bar. */
@Composable
fun HomeScreen(state: HomeState, progress: UpdateProgress?, outcome: UpdateOutcome?, actions: HomeScreenActions) {
    val colors = LocalAdAwayColors.current
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val usesVpn = state.blocking.method == BlockingMethod.VPN
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(colors.homeBackground)) {
        val contentHeight = maxHeight - statusBar - navigationBar
        val headerHeight = statusBar + contentHeight * HEADER_HEIGHT_FRACTION
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Header(
                    blockingOn = !usesVpn || state.blocking.active,
                    logoSize = contentHeight * LOGO_HEIGHT_FRACTION,
                    modifier = Modifier.fillMaxWidth().height(headerHeight),
                    contentTop = statusBar,
                )
                Column {
                    Spacer(modifier = Modifier.height(headerHeight - CounterOverlap - BadgeSize / 2))
                    Counters(state, actions.openLists)
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 12.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp + BumpBottomBarReservedHeight + navigationBar),
            ) {
                if (state.legacyImportFailed) {
                    LegacyImportFailedCard(actions.dismissLegacyImportFailure, modifier = Modifier.padding(bottom = 20.dp))
                }
                SourcesCard(state.sources, progress, outcome, actions, modifier = Modifier.padding(bottom = 20.dp))
                Tiles()
                if (usesVpn) PersistenceCard(actions.openPersistence, modifier = Modifier.padding(top = 20.dp))
            }
        }
        BumpBottomBar(
            icon = painterResource(if (state.blocking.active) R.drawable.ic_pause_24dp else R.drawable.logo),
            contentDescription = stringResource(R.string.home_toggle_blocking),
            enabled = usesVpn,
            onClick = { if (progress == null) actions.toggleBlocking() },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * Brand color while blocking, gray otherwise; it also colors the status bar behind it. The title
 * and the subtitle share the logo height, weighted 1 to 1.4, each as large as it fits, and the
 * title stops before the version in the corner.
 */
@Composable
private fun Header(blockingOn: Boolean, logoSize: Dp, contentTop: Dp, modifier: Modifier) {
    val colors = LocalAdAwayColors.current
    val white = colors.onPrimary
    Layout(
        modifier = modifier.background(if (blockingOn) colors.primary else colors.headerOff),
        content = {
            Box(modifier = Modifier.clip(RoundedCornerShape(25)).background(colors.primary)) {
                Image(
                    painter = painterResource(R.mipmap.icon_foreground),
                    contentDescription = stringResource(R.string.logo_description),
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(contentAlignment = Alignment.BottomStart) {
                BasicText(
                    text = stringResource(R.string.app_title),
                    style = AdAwayType.large.copy(color = white, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(minFontSize = 13.sp, maxFontSize = 34.sp, stepSize = 1.sp),
                )
            }
            BasicText(
                text = stringResource(R.string.app_subtitle),
                style = AdAwayType.medium.copy(color = white),
                maxLines = 3,
                autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 20.sp, stepSize = 1.sp),
            )
            Text(text = BuildConfig.VERSION_NAME, style = AdAwayType.medium, color = white, modifier = Modifier.padding(2.dp))
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val top = contentTop.roundToPx()
        val logo = logoSize.roundToPx()
        val margin = 16.dp.roundToPx()
        val gap = 8.dp.roundToPx()
        val version = measurables[3].measure(Constraints())
        val textLeft = margin + logo + gap
        val textHeight = (logo - 2 * gap).coerceAtLeast(0)
        val titleHeight = (textHeight / 2.4f).toInt()
        val title = measurables[1].measure(
            Constraints.fixed((width - margin - version.width - gap - textLeft).coerceAtLeast(0), titleHeight),
        )
        val subtitle = measurables[2].measure(
            Constraints.fixed((width - gap - textLeft).coerceAtLeast(0), textHeight - titleHeight),
        )
        val logoPlaceable = measurables[0].measure(Constraints.fixed(logo, logo))
        layout(width, constraints.maxHeight) {
            logoPlaceable.placeRelative(margin, top + margin)
            title.placeRelative(textLeft, top + margin + gap)
            subtitle.placeRelative(textLeft, top + margin + gap + titleHeight)
            version.placeRelative(width - margin - version.width, top + gap)
        }
    }
}

@Composable
private fun Counters(state: HomeState, openLists: (RuleType) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.Top) {
        RuleType.entries.forEachIndexed { index, type ->
            if (index > 0) Spacer(modifier = Modifier.weight(0.2f))
            CounterCard(type, state.summary.count(type), onClick = { openLists(type) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CounterCard(type: RuleType, count: Long, onClick: () -> Unit, modifier: Modifier) {
    val colors = LocalAdAwayColors.current
    @StringRes val label: Int
    @StringRes val description: Int
    @DrawableRes val icon: Int
    val pastel: Pastel
    when (type) {
        RuleType.BLOCKED -> {
            label = R.string.rules_blocked; description = R.string.home_blocked_description
            icon = R.drawable.baseline_block_24; pastel = colors.pastelRed
        }
        RuleType.ALLOWED -> {
            label = R.string.rules_allowed; description = R.string.home_allowed_description
            icon = R.drawable.baseline_check_24; pastel = colors.pastelGreen
        }
        RuleType.REDIRECTED -> {
            label = R.string.rules_redirected; description = R.string.home_redirected_description
            icon = R.drawable.baseline_compare_arrows_24; pastel = colors.pastelBlue
        }
    }
    Box(modifier = modifier) {
        Surface(
            onClick = onClick,
            modifier = Modifier.padding(top = BadgeSize / 2).fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = colors.counterCard,
            shadowElevation = 10.dp,
        ) {
            Column(
                modifier = Modifier.padding(start = 8.dp, top = 30.dp, end = 8.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The raw count, without thousands separators.
                Text(count.toString(), style = AdAwayType.large, fontWeight = FontWeight.Bold, color = colors.textPrimary, maxLines = 1)
                Text(stringResource(label), style = AdAwayType.small, color = colors.textSecondary)
                BasicText(
                    text = stringResource(description),
                    style = AdAwayType.caption.copy(color = colors.homeCardSubtitle, textAlign = TextAlign.Center),
                    maxLines = 2,
                    autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 12.sp, stepSize = 1.sp),
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                )
            }
        }
        PastelIcon(icon, pastel, contentDescription = stringResource(label), modifier = Modifier.align(Alignment.TopCenter).shadow(12.dp, CircleShape))
    }
}

@Composable
private fun SourcesCard(stats: SourceStats, progress: UpdateProgress?, outcome: UpdateOutcome?, actions: HomeScreenActions, modifier: Modifier) {
    val colors = LocalAdAwayColors.current
    val message = progress?.let { updateProgressText(it) } ?: outcome?.let { stringResource(outcomeText(it)) }
    HomeCard(onClick = actions.openSources, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PastelIcon(R.drawable.ic_collections_bookmark_24dp, colors.pastelRed, contentDescription = null)
                Column(modifier = Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(
                        pluralStringResource(R.plurals.home_sources_up_to_date, stats.upToDate, stats.upToDate),
                        style = AdAwayType.subtitle1,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                    )
                    Text(
                        pluralStringResource(R.plurals.home_sources_outdated, stats.outdated, stats.outdated),
                        style = AdAwayType.body2,
                        color = colors.homeCardSubtitle,
                    )
                }
                PastelIcon(
                    R.drawable.ic_sync_24dp,
                    colors.pastelBlue,
                    contentDescription = stringResource(R.string.sync_sources),
                    size = 44.dp,
                    padding = 10.dp,
                    onClick = actions.sync,
                )
            }
            if (progress != null) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    color = colors.primary,
                    trackColor = colors.primary.copy(alpha = 0.24f),
                    strokeCap = StrokeCap.Butt,
                    gapSize = 0.dp,
                )
            }
            message?.let {
                Text(it, style = AdAwayType.body2, color = colors.homeCardSubtitle, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

@StringRes
private fun outcomeText(outcome: UpdateOutcome): Int = when (outcome) {
    UpdateOutcome.UPDATE_AVAILABLE -> R.string.status_update_available
    UpdateOutcome.NO_UPDATE_FOUND -> R.string.status_no_update_found
    UpdateOutcome.APPLIED -> R.string.status_vpn_configuration_updated
}

/** The DNS log, help and preferences screens do not exist yet: their tiles are grayed out. */
@Composable
private fun Tiles() {
    val colors = LocalAdAwayColors.current
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Tile(R.drawable.ic_outline_rule_24, colors.pastelBlue, R.string.home_tile_log, R.string.home_log_subtitle)
        Tile(R.drawable.ic_help_24dp, colors.pastelAmber, R.string.home_tile_help, R.string.home_help_subtitle)
        Tile(R.drawable.ic_settings_24dp, colors.pastelGrey, R.string.home_tile_preferences, R.string.home_preferences_subtitle)
    }
}

@Composable
private fun RowScope.Tile(
    @DrawableRes icon: Int,
    pastel: Pastel,
    @StringRes title: Int,
    @StringRes subtitle: Int,
) {
    val colors = LocalAdAwayColors.current
    HomeCard(onClick = null, modifier = Modifier.weight(1f).fillMaxHeight()) {
        Column(
            modifier = Modifier.fillMaxSize().alpha(DISABLED_ALPHA).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            PastelIcon(icon, pastel, contentDescription = null)
            BasicText(
                text = stringResource(title),
                style = AdAwayType.body2.copy(color = colors.textPrimary, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 14.sp, stepSize = 1.sp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Text(
                stringResource(subtitle),
                style = AdAwayType.caption,
                color = colors.homeCardSubtitle,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun PersistenceCard(onClick: () -> Unit, modifier: Modifier) {
    val colors = LocalAdAwayColors.current
    HomeCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            PastelIcon(R.drawable.ic_vpn_key_24dp, colors.pastelGreen, contentDescription = null)
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(stringResource(R.string.vpn_persistence_title), style = AdAwayType.subtitle1, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                Text(stringResource(R.string.vpn_persistence_summary), style = AdAwayType.body2, color = colors.homeCardSubtitle)
            }
        }
    }
}

@Composable
private fun LegacyImportFailedCard(onDismiss: () -> Unit, modifier: Modifier) {
    val colors = LocalAdAwayColors.current
    HomeCard(onClick = null, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PastelIcon(R.drawable.ic_collections_bookmark_24dp, colors.pastelAmber, contentDescription = null)
                Text(
                    stringResource(R.string.home_legacy_import_failed),
                    style = AdAwayType.body2,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f).padding(start = 16.dp),
                )
            }
            Text(
                stringResource(R.string.dismiss).uppercase(),
                style = AdAwayType.button,
                color = colors.primary,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable(onClick = onDismiss)
                    .padding(8.dp),
            )
        }
    }
}

/** The rounded white cards of the scrolling zone, with their soft shadow. */
@Composable
private fun HomeCard(onClick: (() -> Unit)?, modifier: Modifier, content: @Composable () -> Unit) {
    val colors = LocalAdAwayColors.current
    if (onClick == null) {
        Surface(modifier = modifier, shape = HomeCardShape, color = colors.homeCard, shadowElevation = 2.dp, content = content)
    } else {
        Surface(onClick = onClick, modifier = modifier, shape = HomeCardShape, color = colors.homeCard, shadowElevation = 2.dp, content = content)
    }
}

/** An icon on a pastel disc. */
@Composable
private fun PastelIcon(
    @DrawableRes icon: Int,
    pastel: Pastel,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = BadgeSize,
    padding: Dp = 12.dp,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(pastel.background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
    ) {
        Icon(painterResource(icon), contentDescription = contentDescription, tint = pastel.foreground, modifier = Modifier.fillMaxSize())
    }
}
