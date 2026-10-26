package org.adaway.ui.help

import android.content.ActivityNotFoundException
import android.content.Intent
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.widget.TextView
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import org.adaway.BuildConfig
import org.adaway.R
import org.adaway.ui.common.AdAwayScreen
import org.adaway.ui.common.RemoteConfirmKeys
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.LocalAdAwayColors
import org.adaway.ui.theme.LocalTelevision

private const val REPOSITORY = "https://github.com/Victor-root/AdAway-Community"
private const val UPSTREAM = "https://github.com/AdAway/AdAway"

private val Tabs = listOf(R.string.help_tab_faq, R.string.help_tab_problems, R.string.help_tab_about)

/**
 * The help, in three tabs that can also be swiped: questions, problems, and about. On TV, the
 * remote picks a tab by moving on it, goes down to read the page, and back up at its top.
 */
@Composable
fun HelpScreen(onBack: () -> Unit, openNotices: () -> Unit) {
    val isTelevision = LocalTelevision.current
    val pager = rememberPagerState { Tabs.size }
    val scope = rememberCoroutineScope()
    val tabs = remember { Tabs.map { FocusRequester() } }
    // One per page: the pager keeps a neighbor page while it scrolls, which the focus could reach.
    val pages = remember { Tabs.map { FocusRequester() } }
    AdAwayScreen(title = stringResource(R.string.help), onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(
                selected = pager.currentPage,
                onSelect = { scope.launch { pager.animateScrollToPage(it) } },
                isTelevision = isTelevision,
                tabs = tabs,
                pages = pages,
            )
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
                val pageFocus = PageFocus(isTelevision, pages[index], tabs[index])
                Box(modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                    when (index) {
                        0 -> HelpPage(R.raw.help_faq, pageFocus)
                        1 -> HelpPage(R.raw.help_problems, pageFocus)
                        else -> AboutPage(pageFocus, openNotices)
                    }
                }
            }
        }
    }
    if (isTelevision) LaunchedEffect(Unit) { tabs.first().requestFocus() }
}

/** The licenses of the components the app is built with. */
@Composable
fun NoticesScreen(onBack: () -> Unit) {
    val isTelevision = LocalTelevision.current
    val page = remember { FocusRequester() }
    AdAwayScreen(title = stringResource(R.string.about_third_party_label), onBack = onBack) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            HelpPage(R.raw.third_party_notices, PageFocus(isTelevision, page, tab = null))
        }
    }
    if (isTelevision) LaunchedEffect(Unit) { page.requestFocus() }
}

/** On TV, the page takes the focus to scroll, and gives it back to its [tab], if any, from its top. */
private class PageFocus(val isTelevision: Boolean, val page: FocusRequester, val tab: FocusRequester?)

/** Fixed tabs of equal width, the selected one in red over a red line. */
@Composable
private fun TabRow(selected: Int, onSelect: (Int) -> Unit, isTelevision: Boolean, tabs: List<FocusRequester>, pages: List<FocusRequester>) {
    val colors = LocalAdAwayColors.current
    Row(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
        Tabs.forEachIndexed { index, title ->
            val current = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .then(
                        if (isTelevision) {
                            Modifier
                                .focusRequester(tabs[index])
                                .onFocusChanged { if (it.isFocused) onSelect(index) }
                                .focusProperties {
                                    up = FocusRequester.Cancel
                                    down = pages[index]
                                }
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(title).uppercase(),
                    style = AdAwayType.button,
                    color = if (current) colors.primary else colors.textSecondary,
                    maxLines = 1,
                )
                if (current) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(colors.primary))
                }
            }
        }
    }
}

/**
 * A scrolling page. On TV, the arrows scroll it by half its height, as Android scrolls a text, and
 * the OK button runs [onConfirm].
 */
@Composable
private fun PageColumn(
    focus: PageFocus,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    onConfirm: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val remote = if (focus.isTelevision) {
        Modifier
            .focusRequester(focus.page)
            .focusProperties { focus.tab?.let { up = it } }
            .onPreviewKeyEvent { event ->
                val step = scroll.viewportSize / 2f
                when {
                    onConfirm != null && event.key in RemoteConfirmKeys -> {
                        if (event.type == KeyEventType.KeyUp) onConfirm()
                        true
                    }
                    event.type != KeyEventType.KeyDown -> false
                    event.key == Key.DirectionDown -> {
                        scope.launch { scroll.animateScrollBy(step) }
                        true
                    }
                    event.key == Key.DirectionUp && scroll.value > 0 -> {
                        scope.launch { scroll.animateScrollBy(-step) }
                        true
                    }
                    else -> false
                }
            }
            .focusable()
    } else {
        Modifier
    }
    Column(modifier = Modifier.fillMaxSize().then(remote).verticalScroll(scroll).then(modifier), horizontalAlignment = horizontalAlignment, content = content)
}

/** An HTML help page, rendered by Android as text with its titles and links, inert on TV. */
@Composable
private fun HelpPage(@RawRes page: Int, focus: PageFocus) {
    val resources = LocalResources.current
    val colors = LocalAdAwayColors.current
    val html = remember(page) { resources.openRawResource(page).bufferedReader().use { it.readText() } }
    PageColumn(focus, modifier = Modifier.padding(bottom = 24.dp)) {
        AndroidView(
            factory = { viewContext ->
                TextView(viewContext).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setLineSpacing(0f, 1.25f)
                    if (focus.isTelevision) {
                        isFocusable = false
                    } else {
                        setTextIsSelectable(true)
                        movementMethod = LinkMovementMethod.getInstance()
                    }
                }
            },
            update = { view ->
                view.setTextColor(colors.textPrimary.toArgb())
                view.setLinkTextColor(colors.primary.toArgb())
                view.text = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
            },
            modifier = Modifier.fillMaxWidth().padding(20.dp),
        )
    }
}

@Composable
private fun AboutPage(focus: PageFocus, openNotices: () -> Unit) {
    val colors = LocalAdAwayColors.current
    PageColumn(focus, modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, onConfirm = openNotices) {
        Box(modifier = Modifier.size(72.dp).clip(RoundedCornerShape(22)).background(colors.primary)) {
            Image(
                painterResource(R.mipmap.icon_foreground),
                contentDescription = stringResource(R.string.logo_description),
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(stringResource(R.string.app_title), fontSize = 24.sp, color = colors.textPrimary, modifier = Modifier.padding(top = 16.dp))
        Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = AdAwayType.small, color = colors.textPrimary, modifier = Modifier.padding(top = 4.dp))
        Text(
            stringResource(R.string.about_description),
            style = AdAwayType.small,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        HelpDivider(Modifier.padding(top = 24.dp, bottom = 8.dp))
        Link(R.string.about_source_label, REPOSITORY, focus.isTelevision, Modifier.padding(top = 8.dp))
        Link(R.string.about_issues_label, "$REPOSITORY/issues", focus.isTelevision)
        Link(R.string.about_releases_label, "$REPOSITORY/releases", focus.isTelevision)
        Link(R.string.about_upstream_label, UPSTREAM, focus.isTelevision)
        HelpDivider(Modifier.padding(vertical = 8.dp))
        Text(
            stringResource(R.string.about_license_label),
            style = AdAwayType.small,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).padding(horizontal = 4.dp),
        )
        // On TV, the OK button of the page opens it.
        TextLink(R.string.about_third_party_label, enabled = !focus.isTelevision, onClick = openNotices)
    }
}

/** A link to a web page. On TV, where a browser is rare, the page scrolls past it instead. */
@Composable
private fun Link(@StringRes text: Int, url: String, isTelevision: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    TextLink(text, modifier, enabled = !isTelevision) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        } catch (_: ActivityNotFoundException) {
            // No browser on the device.
        }
    }
}

@Composable
private fun TextLink(@StringRes text: Int, modifier: Modifier = Modifier, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(stringResource(text), style = AdAwayType.small, color = LocalAdAwayColors.current.primary)
    }
}

@Composable
private fun HelpDivider(modifier: Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).background(LocalAdAwayColors.current.textPrimary.copy(alpha = 0.12f)))
}
