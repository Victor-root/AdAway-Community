package org.adaway.ui.help

import android.text.Html
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.adaway.BuildConfig
import org.adaway.R
import org.adaway.system.copyAndTell
import org.adaway.system.openWebPage
import org.adaway.ui.common.AdAwayScreen
import org.adaway.ui.common.RemoteConfirmKeys
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.LocalAdAwayColors
import org.adaway.ui.theme.LocalTelevision
import kotlin.math.roundToInt

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
 * A link of a page, which the remote can select and open: from [top] to [bottom] below the top of
 * the layout [anchor] gives.
 */
private class PageLink(val anchor: () -> LayoutCoordinates?, val top: Int, val bottom: Int, val open: () -> Unit)

/**
 * The links of a page, by their order in it, and the one the remote selected. Moving selects the
 * next link when it is in sight, or else scrolls, as a TV browser does.
 */
private class PageLinks {
    val links = mutableMapOf<Int, PageLink>()
    var selected by mutableStateOf<Int?>(null)

    /** The scrolling content of the page, where the links are placed. */
    var content: LayoutCoordinates? = null

    /** Returns false when going up from the top of the page, which leaves it. */
    fun move(down: Boolean, scroll: ScrollState, scope: CoroutineScope): Boolean {
        val top = scroll.value
        val bottom = top + scroll.viewportSize
        val placed = links.mapNotNull { (index, link) -> placeOf(link)?.let { index to it } }.sortedBy { it.first }
        val current = selected
        val canScroll = if (down) scroll.canScrollForward else top > 0
        val next = when {
            down && current != null -> placed.firstOrNull { it.first > current }
            down -> placed.firstOrNull { it.second.first >= top }
            current != null -> placed.lastOrNull { it.first < current }
            // At the top, nothing selected, going up leaves the page.
            canScroll -> placed.lastOrNull { it.second.last <= bottom }
            else -> null
        }
        when {
            next != null && next.second.first >= top && next.second.last <= bottom -> selected = next.first
            canScroll -> {
                val step = scroll.viewportSize / 2 * if (down) 1 else -1
                val newTop = (top + step).coerceIn(0, scroll.maxValue)
                val place = placed.firstOrNull { it.first == current }?.second
                if (place != null && (place.first < newTop || place.last > newTop + scroll.viewportSize)) selected = null
                scope.launch { scroll.animateScrollBy(step.toFloat()) }
            }
            next != null -> selected = next.first
            else -> return down
        }
        return true
    }

    /** Where [link] lies in the page, null while it is not laid out. */
    private fun placeOf(link: PageLink): IntRange? {
        val page = content?.takeIf { it.isAttached } ?: return null
        val anchor = link.anchor()?.takeIf { it.isAttached } ?: return null
        val y = page.localPositionOf(anchor, Offset.Zero).y.roundToInt()
        return (y + link.top)..(y + link.bottom)
    }
}

/**
 * A scrolling page. On TV, the arrows move through its [links] and scroll it by half its height,
 * as Android scrolls a text, and the OK button opens the link selected.
 */
@Composable
private fun PageColumn(
    focus: PageFocus,
    links: PageLinks,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val remote = if (focus.isTelevision) {
        Modifier
            .focusRequester(focus.page)
            .focusProperties { focus.tab?.let { up = it } }
            .onFocusChanged { if (!it.isFocused) links.selected = null }
            .onPreviewKeyEvent { event ->
                when {
                    event.key in RemoteConfirmKeys -> {
                        val link = links.selected?.let { links.links[it] } ?: return@onPreviewKeyEvent false
                        if (event.type == KeyEventType.KeyUp) link.open()
                        true
                    }
                    event.type != KeyEventType.KeyDown -> false
                    event.key == Key.DirectionDown -> links.move(down = true, scroll, scope)
                    event.key == Key.DirectionUp -> links.move(down = false, scroll, scope)
                    else -> false
                }
            }
            .focusable()
    } else {
        Modifier
    }
    Column(
        modifier = Modifier.fillMaxSize().then(remote).verticalScroll(scroll).onGloballyPositioned { links.content = it }.then(modifier),
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

/** A link of an HTML page, opened in a browser. */
private class WebLinkSpan(private val url: String) : ClickableSpan() {
    override fun onClick(widget: View) = widget.context.openWebPage(url)
}

/** Where the text in monospace lies, the code of the help, from its start to its end. */
private fun Spanned.codes(): List<IntRange> = getSpans(0, length, TypefaceSpan::class.java)
    .filter { it.family == "monospace" }
    .map { getSpanStart(it)..getSpanEnd(it) }

/** A part of an HTML help page: some text with its links, or an ADB command in a code block. */
private sealed interface HelpPart {
    /** [firstLink] is the order in the page of the first link of the text. */
    class Text(val text: Spanned, val firstLink: Int) : HelpPart

    class Command(val command: String) : HelpPart
}

/** An ADB command, written alone in monospace in the help, which becomes a code block. */
private val COMMAND = Regex("<tt><code>(adb [^<]*)</code></tt>")

/** Splits [html] around its ADB commands, the texts rendered by Android with their links. */
private fun helpParts(html: String): List<HelpPart> {
    val parts = mutableListOf<HelpPart>()
    var links = 0
    fun addText(part: String) {
        val text = SpannableStringBuilder(Html.fromHtml(part, Html.FROM_HTML_MODE_LEGACY))
        text.getSpans(0, text.length, URLSpan::class.java).forEach { span ->
            text.setSpan(WebLinkSpan(span.url), text.getSpanStart(span), text.getSpanEnd(span), text.getSpanFlags(span))
            text.removeSpan(span)
        }
        // The space of the paragraphs around a code block is the block's own.
        while (text.isNotEmpty() && text.first().isWhitespace()) text.delete(0, 1)
        while (text.isNotEmpty() && text.last().isWhitespace()) text.delete(text.length - 1, text.length)
        if (text.isEmpty()) return
        parts += HelpPart.Text(text, links)
        links += text.getSpans(0, text.length, WebLinkSpan::class.java).size
    }
    var start = 0
    COMMAND.findAll(html).forEach { match ->
        addText(html.substring(start, match.range.first))
        parts += HelpPart.Command(Html.fromHtml(match.groupValues[1], Html.FROM_HTML_MODE_LEGACY).toString())
        start = match.range.last + 1
    }
    addText(html.substring(start))
    return parts
}

/** The layout of the text of a page, which its links are placed in. */
private class Anchor {
    var coordinates: LayoutCoordinates? = null
}

/** An HTML help page, rendered by Android as text with its titles and links, its ADB commands in code blocks. */
@Composable
private fun HelpPage(@RawRes page: Int, focus: PageFocus) {
    val resources = LocalResources.current
    val links = remember { PageLinks() }
    val parts = remember(page) { helpParts(resources.openRawResource(page).bufferedReader().use { it.readText() }) }
    PageColumn(focus, links, modifier = Modifier.padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 44.dp)) {
        parts.forEach { part ->
            when (part) {
                is HelpPart.Text -> HelpText(part, focus.isTelevision, links)
                is HelpPart.Command -> CommandBlock(part.command, focus.isTelevision)
            }
        }
    }
}

/** A text of a help page, whose links are those of [links] from [HelpPart.Text.firstLink] on. */
@Composable
private fun HelpText(part: HelpPart.Text, isTelevision: Boolean, links: PageLinks) {
    val colors = LocalAdAwayColors.current
    val anchor = remember { Anchor() }
    val selection = colors.primary.copy(alpha = 0.2f).toArgb()
    val codeBackground = colors.textPrimary.copy(alpha = 0.1f).toArgb()
    val text = part.text
    AndroidView(
        factory = { viewContext ->
            TextView(viewContext).apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setLineSpacing(0f, 1.25f)
                // Selecting text replaces the movement of the links, which must come after it.
                if (!isTelevision) setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
                // On TV, the page moves through the links: the text only takes the clicks of a mouse.
                if (isTelevision) isFocusable = false
            }
        },
        update = { view ->
            view.setTextColor(colors.textPrimary.toArgb())
            view.setLinkTextColor(colors.primary.toArgb())
            val spans = text.getSpans(0, text.length, WebLinkSpan::class.java).sortedBy(text::getSpanStart)
            val shown = SpannableString(text)
            text.codes().forEach { code ->
                shown.setSpan(BackgroundColorSpan(codeBackground), code.first, code.last, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            links.selected?.let { spans.getOrNull(it - part.firstLink) }?.let { span ->
                shown.setSpan(BackgroundColorSpan(selection), text.getSpanStart(span), text.getSpanEnd(span), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            view.text = shown
            view.doOnLayout {
                val layout = view.layout ?: return@doOnLayout
                spans.forEachIndexed { index, span ->
                    val first = layout.getLineForOffset(text.getSpanStart(span))
                    val last = layout.getLineForOffset(text.getSpanEnd(span))
                    links.links[part.firstLink + index] = PageLink(
                        anchor = anchor::coordinates,
                        top = view.paddingTop + layout.getLineTop(first),
                        bottom = view.paddingTop + layout.getLineBottom(last),
                        open = { span.onClick(view) },
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth().onGloballyPositioned { anchor.coordinates = it },
    )
}

/** An ADB command in a code block, with a button that copies it on phones, where it can be pasted. */
@Composable
private fun CommandBlock(command: String, isTelevision: Boolean) {
    val colors = LocalAdAwayColors.current
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .padding(top = 6.dp, bottom = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.textPrimary.copy(alpha = 0.08f)),
    ) {
        Text(
            command,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = colors.textPrimary,
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = if (isTelevision) 14.dp else 48.dp, bottom = 12.dp),
        )
        if (!isTelevision) {
            IconButton(
                onClick = { context.copyAndTell(command, R.string.command_copied) },
                modifier = Modifier.align(Alignment.TopEnd).size(44.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_content_copy_24dp),
                    contentDescription = stringResource(R.string.pref_vpn_diagnostic_log_copy),
                    tint = colors.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun AboutPage(focus: PageFocus, openNotices: () -> Unit) {
    val colors = LocalAdAwayColors.current
    val context = LocalContext.current
    val links = remember { PageLinks() }
    PageColumn(focus, links, modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
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
        TextLink(R.string.about_source_label, links, 0, Modifier.padding(top = 8.dp)) { context.openWebPage(REPOSITORY) }
        TextLink(R.string.about_issues_label, links, 1) { context.openWebPage("$REPOSITORY/issues") }
        TextLink(R.string.about_releases_label, links, 2) { context.openWebPage("$REPOSITORY/releases") }
        TextLink(R.string.about_upstream_label, links, 3) { context.openWebPage(UPSTREAM) }
        HelpDivider(Modifier.padding(vertical = 8.dp))
        Text(
            stringResource(R.string.about_license_label),
            style = AdAwayType.small,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).padding(horizontal = 4.dp),
        )
        TextLink(R.string.about_third_party_label, links, 4, onClick = openNotices)
    }
}

/** The link [index] of the page [links], highlighted when the remote selects it. */
@Composable
private fun TextLink(@StringRes text: Int, links: PageLinks, index: Int, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = LocalAdAwayColors.current
    val isTelevision = LocalTelevision.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .onGloballyPositioned { coordinates ->
                links.links[index] = PageLink({ coordinates }, 0, coordinates.size.height, onClick)
            }
            .background(if (links.selected == index) colors.primary.copy(alpha = 0.2f) else Color.Transparent)
            // On TV, the page moves through the links: they only take the clicks of a mouse.
            .focusProperties { canFocus = !isTelevision }
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(stringResource(text), style = AdAwayType.small, color = colors.primary)
    }
}

@Composable
private fun HelpDivider(modifier: Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).background(LocalAdAwayColors.current.textPrimary.copy(alpha = 0.12f)))
}
