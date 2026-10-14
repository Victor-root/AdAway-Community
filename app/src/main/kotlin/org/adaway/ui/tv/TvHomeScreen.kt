package org.adaway.ui.tv

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import org.adaway.BuildConfig
import org.adaway.R
import org.adaway.data.db.RuleType
import org.adaway.data.settings.BlockingMethod
import org.adaway.data.settings.ThemeMode
import org.adaway.ui.common.AdAwayAlertDialog
import org.adaway.ui.common.DialogButton
import org.adaway.ui.common.PastelIcon
import org.adaway.ui.common.ruleTypeLabel
import org.adaway.ui.common.statusMessageText
import org.adaway.ui.home.BlockingActions
import org.adaway.ui.home.BumpBar
import org.adaway.ui.home.HomeNavigation
import org.adaway.ui.home.HomeState
import org.adaway.ui.home.HomeViewModel
import org.adaway.ui.home.blockingDetail
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.LocalAdAwayColors
import org.adaway.ui.theme.Pastel
import org.adaway.ui.theme.controlHighlight
import org.adaway.ui.theme.highlight
import org.adaway.vpn.AlwaysOnMode

/** The controls of the TV home the remote moves between. */
private enum class HomeControl { TOGGLE, UPDATE, PERSISTENCE, THEME, BLOCKED, ALLOWED, REDIRECTED, SOURCES, SYNC, LOG, HELP, PREFERENCES }

private enum class HomePart { HEADER, PROGRESS, STATUS, SHORTCUTS }

/** Share of the width left of the line splitting the two columns. */
private const val COLUMN_SPLIT = 0.46f
private val ColumnMargin = 24.dp
private val BottomPadding = 8.dp
private val PillShape = RoundedCornerShape(24.dp)
private val AlwaysOnGreen = Color(0xFF388E3C)

/**
 * The TV home: the brand header, then the blocking state and its controls on the left, the
 * counters, the sources and the other screens on the right, both columns centered in the height.
 */
@Composable
fun TvHomeScreen(state: HomeState, viewModel: HomeViewModel, navigation: HomeNavigation, blocking: BlockingActions) {
    val colors = LocalAdAwayColors.current
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val appUpdate by viewModel.appUpdate.collectAsStateWithLifecycle()
    var alwaysOn by remember { mutableStateOf(false) }
    LifecycleResumeEffect(state.blocking) {
        alwaysOn = viewModel.alwaysOnMode().let { it == AlwaysOnMode.ON || it == AlwaysOnMode.LOCKDOWN }
        onPauseOrDispose {}
    }
    val usesVpn = state.blocking.method == BlockingMethod.VPN
    val busy = progress != null || state.blocking.rootBusy
    val dark = colors.surface.luminance() < 0.5f
    val focus = remember { HomeControl.entries.associateWith { FocusRequester() } }
    // The control focused last, focused again when the screen comes back, as Android does.
    var focused by rememberSaveable { mutableStateOf(HomeControl.TOGGLE) }
    val rowEntry = if (usesVpn) HomeControl.PERSISTENCE else HomeControl.THEME
    val links: FocusProperties.(HomeControl) -> Unit = { control ->
        val to = focus::getValue
        when (control) {
            HomeControl.TOGGLE -> {
                right = to(HomeControl.BLOCKED)
                down = to(if (appUpdate != null) HomeControl.UPDATE else rowEntry)
            }
            HomeControl.UPDATE -> {
                up = to(HomeControl.TOGGLE)
                down = to(rowEntry)
            }
            HomeControl.PERSISTENCE -> {
                up = to(HomeControl.TOGGLE)
                right = to(HomeControl.THEME)
            }
            HomeControl.THEME -> {
                up = to(HomeControl.TOGGLE)
                if (usesVpn) left = to(HomeControl.PERSISTENCE)
            }
            HomeControl.BLOCKED -> {
                left = to(HomeControl.TOGGLE)
                right = to(HomeControl.ALLOWED)
                down = to(HomeControl.SOURCES)
            }
            HomeControl.ALLOWED -> {
                left = to(HomeControl.BLOCKED)
                right = to(HomeControl.REDIRECTED)
                down = to(HomeControl.SOURCES)
            }
            HomeControl.REDIRECTED -> {
                left = to(HomeControl.ALLOWED)
                down = to(HomeControl.SOURCES)
            }
            HomeControl.SOURCES -> {
                up = to(HomeControl.BLOCKED)
                right = to(HomeControl.SYNC)
                down = to(HomeControl.LOG)
            }
            HomeControl.SYNC -> {
                left = to(HomeControl.SOURCES)
                down = to(HomeControl.LOG)
            }
            HomeControl.LOG -> {
                up = to(HomeControl.SOURCES)
                right = to(HomeControl.HELP)
                down = to(rowEntry)
            }
            HomeControl.HELP -> {
                left = to(HomeControl.LOG)
                up = to(HomeControl.SOURCES)
                right = to(HomeControl.PREFERENCES)
                down = to(rowEntry)
            }
            HomeControl.PREFERENCES -> {
                left = to(HomeControl.HELP)
                up = to(HomeControl.SOURCES)
                down = to(rowEntry)
            }
        }
    }
    val control: (HomeControl) -> Modifier = { control ->
        Modifier
            .focusRequester(focus.getValue(control))
            .onFocusChanged { if (it.isFocused) focused = control }
            .focusProperties { links(control) }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(colors.surface).navigationBarsPadding()) {
        val viewportHeight = constraints.maxHeight
        Layout(
            content = {
                Header(Modifier.layoutId(HomePart.HEADER))
                if (busy) {
                    LinearProgressIndicator(
                        modifier = Modifier.layoutId(HomePart.PROGRESS).fillMaxWidth().height(3.dp),
                        color = colors.primary,
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Butt,
                        gapSize = 0.dp,
                    )
                }
                Column(modifier = Modifier.layoutId(HomePart.STATUS), horizontalAlignment = Alignment.CenterHorizontally) {
                    StatusBadge(state.blocking.active)
                    Text(
                        stringResource(if (state.blocking.active) R.string.home_blocking_enabled else R.string.home_blocking_disabled),
                        style = AdAwayType.subtitle1,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    val detail = blockingDetail(state.blocking.vpnStatus)?.let { stringResource(it) }
                        ?: statusMessage?.let { statusMessageText(it, usesVpn) }
                    detail?.let {
                        Text(it, style = AdAwayType.caption, color = colors.textPrimary.copy(alpha = 0.7f), modifier = Modifier.padding(top = 2.dp))
                    }
                    if (alwaysOn) AlwaysOnIndicator()
                    PillButton(
                        text = stringResource(if (state.blocking.active) R.string.home_disable_blocking else R.string.home_enable_blocking),
                        icon = if (state.blocking.active) R.drawable.ic_pause_24dp else R.drawable.ic_power_settings_new_24dp,
                        color = if (state.blocking.active) colors.primary else colors.pastelGrey.foreground,
                        fontSize = 14.sp,
                        onClick = { if (!busy) blocking.toggle() },
                        modifier = control(HomeControl.TOGGLE).padding(top = 10.dp),
                    )
                    appUpdate?.let { release ->
                        PillButton(
                            text = stringResource(R.string.pref_update_install_summary, release.version),
                            icon = R.drawable.ic_get_app_24dp,
                            color = colors.primary,
                            fontSize = 13.sp,
                            onClick = navigation.openUpdate,
                            modifier = control(HomeControl.UPDATE).padding(top = 6.dp),
                        )
                    }
                    Row(modifier = Modifier.padding(top = 16.dp)) {
                        if (usesVpn) {
                            ActionCard(
                                R.drawable.ic_vpn_key_24dp,
                                colors.pastelBlue,
                                stringResource(R.string.vpn_persistence_title),
                                onClick = blocking.openPersistence,
                                modifier = control(HomeControl.PERSISTENCE).padding(end = 10.dp),
                            )
                        }
                        ActionCard(
                            R.drawable.ic_brightness_medium_24dp,
                            colors.pastelAmber,
                            stringResource(if (dark) R.string.tv_button_theme_light else R.string.tv_button_theme_dark),
                            onClick = { viewModel.setThemeMode(if (dark) ThemeMode.LIGHT else ThemeMode.DARK) },
                            modifier = control(HomeControl.THEME),
                        )
                    }
                }
                Column(modifier = Modifier.layoutId(HomePart.SHORTCUTS)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(RuleType.BLOCKED, state.summary.count(RuleType.BLOCKED), navigation.openLists, control(HomeControl.BLOCKED).weight(1f))
                        StatCard(RuleType.ALLOWED, state.summary.count(RuleType.ALLOWED), navigation.openLists, control(HomeControl.ALLOWED).weight(1f))
                        StatCard(RuleType.REDIRECTED, state.summary.count(RuleType.REDIRECTED), navigation.openLists, control(HomeControl.REDIRECTED).weight(1f))
                    }
                    TvCard(onClick = navigation.openSources, modifier = control(HomeControl.SOURCES).padding(top = 10.dp).fillMaxWidth()) {
                        Row(modifier = Modifier.padding(12.dp)) {
                            PastelIcon(R.drawable.ic_collections_bookmark_24dp, colors.pastelRed, contentDescription = null, size = 40.dp, padding = 10.dp)
                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp).align(Alignment.CenterVertically)) {
                                val stats = state.sources
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
                            CompositionLocalProvider(LocalRippleConfiguration provides controlHighlight(colors)) {
                                PastelIcon(
                                    R.drawable.ic_sync_24dp,
                                    colors.pastelBlue,
                                    contentDescription = stringResource(R.string.sync_sources),
                                    modifier = control(HomeControl.SYNC).align(Alignment.CenterVertically),
                                    size = 38.dp,
                                    padding = 9.dp,
                                    onClick = viewModel::sync,
                                )
                            }
                        }
                    }
                    Row(modifier = Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Tile(
                            R.drawable.ic_outline_rule_24,
                            colors.pastelBlue,
                            R.string.home_tile_log,
                            R.string.home_log_subtitle,
                            navigation.openLog,
                            control(HomeControl.LOG).weight(1f),
                        )
                        Tile(
                            R.drawable.ic_help_24dp,
                            colors.pastelAmber,
                            R.string.home_tile_help,
                            R.string.home_help_subtitle,
                            navigation.openHelp,
                            control(HomeControl.HELP).weight(1f),
                        )
                        Tile(
                            R.drawable.ic_settings_24dp,
                            colors.pastelGrey,
                            R.string.home_tile_preferences,
                            R.string.home_preferences_subtitle,
                            navigation.openPreferences,
                            control(HomeControl.PREFERENCES).weight(1f),
                        )
                    }
                }
            },
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val margin = ColumnMargin.roundToPx()
            val split = (width * COLUMN_SPLIT).roundToInt()
            fun part(part: HomePart, columnWidth: Int) = measurables.firstOrNull { it.layoutId == part }?.measure(Constraints.fixedWidth(columnWidth.coerceAtLeast(0)))
            val header = part(HomePart.HEADER, width)
            val bar = part(HomePart.PROGRESS, width)
            val status = part(HomePart.STATUS, split - 2 * margin)
            val shortcuts = part(HomePart.SHORTCUTS, width - split - 2 * margin)
            val top = (header?.height ?: 0) + (bar?.height ?: 0)
            val bottom = BottomPadding.roundToPx()
            // The space under the header, as tall as the screen allows, or as the tallest column needs.
            val area = maxOf(viewportHeight - top - bottom, status?.height ?: 0, shortcuts?.height ?: 0)
            layout(width, top + area + bottom) {
                header?.place(0, 0)
                bar?.place(0, header?.height ?: 0)
                status?.placeRelative(margin, top + (area - status.height) / 2)
                shortcuts?.placeRelative(split + margin, top + (area - shortcuts.height) / 2)
            }
        }
    }
    if (state.legacyImportFailed) {
        AdAwayAlertDialog(
            onDismissRequest = viewModel::dismissLegacyImportFailure,
            title = stringResource(R.string.app_name),
            message = stringResource(R.string.home_legacy_import_failed),
            confirmButton = DialogButton(stringResource(R.string.dismiss), onClick = viewModel::dismissLegacyImportFailure),
        )
    }
    LaunchedEffect(Unit) {
        val available = when (focused) {
            HomeControl.UPDATE -> appUpdate != null
            HomeControl.PERSISTENCE -> usesVpn
            else -> true
        }
        focus.getValue(if (available) focused else HomeControl.TOGGLE).requestFocus()
    }
}

@Composable
private fun Header(modifier: Modifier) {
    val colors = LocalAdAwayColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.primary)
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.logo), contentDescription = stringResource(R.string.logo_description), tint = colors.onPrimary, modifier = Modifier.size(32.dp))
        Column(modifier = Modifier.padding(start = 14.dp)) {
            Text(stringResource(R.string.app_title), style = AdAwayType.headline6, fontWeight = FontWeight.Bold, color = colors.onPrimary)
            Text(stringResource(R.string.app_subtitle), style = AdAwayType.caption, color = colors.onPrimary.copy(alpha = 0.85f))
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), style = AdAwayType.body2, color = colors.onPrimary.copy(alpha = 0.85f))
    }
}

/** The blocking state on the crest of the swelling bar: the logo in gray when off, pause in red when on. */
@Composable
private fun StatusBadge(active: Boolean) {
    val colors = LocalAdAwayColors.current
    val pastel = if (active) colors.pastelRed else colors.pastelGrey
    BumpBar {
        Box(modifier = Modifier.size(56.dp).clip(CircleShape).background(pastel.background), contentAlignment = Alignment.Center) {
            Icon(
                painterResource(if (active) R.drawable.ic_pause_24dp else R.drawable.logo),
                contentDescription = stringResource(R.string.tv_log_entry_status_description),
                tint = pastel.foreground,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun AlwaysOnIndicator() {
    Row(modifier = Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(R.drawable.baseline_check_24), contentDescription = null, tint = AlwaysOnGreen, modifier = Modifier.size(24.dp))
        Text(
            stringResource(R.string.home_always_on_active),
            style = AdAwayType.caption,
            color = AlwaysOnGreen,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

/** The filled pill of the 6.x buttons, lifted a little more with the focus. */
@Composable
private fun PillButton(text: String, @DrawableRes icon: Int, color: Color, fontSize: TextUnit, onClick: () -> Unit, modifier: Modifier) {
    val white = LocalAdAwayColors.current.onPrimary
    val interactions = remember { MutableInteractionSource() }
    val focused by interactions.collectIsFocusedAsState()
    val elevation by animateDpAsState(if (focused) 4.dp else 2.dp, label = "elevation")
    CompositionLocalProvider(LocalRippleConfiguration provides highlight(white, 0.24f)) {
        Row(
            modifier = modifier
                .padding(vertical = 6.dp)
                .shadow(elevation, PillShape)
                .clip(PillShape)
                .background(color)
                .clickable(interactionSource = interactions, indication = ripple(), onClick = onClick)
                .heightIn(min = 36.dp)
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = white, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text.uppercase(), style = AdAwayType.button.copy(fontSize = fontSize), color = white, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ActionCard(@DrawableRes icon: Int, pastel: Pastel, text: String, onClick: () -> Unit, modifier: Modifier) {
    TvCard(onClick = onClick, modifier = modifier) {
        Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            PastelIcon(icon, pastel, contentDescription = null, size = 28.dp, padding = 6.dp)
            Text(
                text,
                style = AdAwayType.body2,
                fontWeight = FontWeight.Bold,
                color = LocalAdAwayColors.current.textPrimary,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Composable
private fun StatCard(type: RuleType, count: Long, openLists: (RuleType) -> Unit, modifier: Modifier) {
    val colors = LocalAdAwayColors.current
    @DrawableRes val icon: Int
    @StringRes val description: Int
    val pastel: Pastel
    when (type) {
        RuleType.BLOCKED -> {
            icon = R.drawable.baseline_block_24; description = R.string.home_blocked_description; pastel = colors.pastelRed
        }
        RuleType.ALLOWED -> {
            icon = R.drawable.baseline_check_24; description = R.string.home_allowed_description; pastel = colors.pastelGreen
        }
        RuleType.REDIRECTED -> {
            icon = R.drawable.baseline_compare_arrows_24; description = R.string.home_redirected_description; pastel = colors.pastelBlue
        }
    }
    val label = ruleTypeLabel(type)
    TvCard(onClick = { openLists(type) }, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PastelIcon(icon, pastel, contentDescription = label, size = 36.dp, padding = 9.dp)
            // The raw count, without thousands separators.
            Text(count.toString(), style = AdAwayType.headline6, fontWeight = FontWeight.Bold, color = colors.textPrimary, modifier = Modifier.padding(top = 6.dp))
            Text(label, style = AdAwayType.body2, color = colors.textPrimary)
            Text(
                stringResource(description),
                style = AdAwayType.caption,
                color = colors.homeCardSubtitle,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun Tile(@DrawableRes icon: Int, pastel: Pastel, @StringRes title: Int, @StringRes subtitle: Int, onClick: () -> Unit, modifier: Modifier) {
    val colors = LocalAdAwayColors.current
    TvCard(onClick = onClick, modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PastelIcon(icon, pastel, contentDescription = null, size = 32.dp, padding = 8.dp)
            Text(
                stringResource(title),
                style = AdAwayType.body2,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
            Text(
                stringResource(subtitle),
                style = AdAwayType.caption,
                color = colors.homeCardSubtitle,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
