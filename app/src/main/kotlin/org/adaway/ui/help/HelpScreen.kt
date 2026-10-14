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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
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
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.LocalAdAwayColors

private const val REPOSITORY = "https://github.com/Victor-root/AdAway-Community"
private const val UPSTREAM = "https://github.com/AdAway/AdAway"

private val Tabs = listOf(R.string.help_tab_faq, R.string.help_tab_problems, R.string.help_tab_about)

/** The help, in three tabs that can also be swiped: questions, problems, and about. */
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val pager = rememberPagerState { Tabs.size }
    val scope = rememberCoroutineScope()
    AdAwayScreen(title = stringResource(R.string.help), onBack = onBack) { padding ->
        Column(modifier = Modifier.fillMaxSize()) {
            TabRow(selected = pager.currentPage, onSelect = { scope.launch { pager.animateScrollToPage(it) } })
            HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
                Box(modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
                    when (page) {
                        0 -> HelpPage(R.raw.help_faq)
                        1 -> HelpPage(R.raw.help_problems)
                        else -> AboutPage()
                    }
                }
            }
        }
    }
}

/** Fixed tabs of equal width, the selected one in red over a red line. */
@Composable
private fun TabRow(selected: Int, onSelect: (Int) -> Unit) {
    val colors = LocalAdAwayColors.current
    Row(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
        Tabs.forEachIndexed { index, title ->
            val current = index == selected
            Box(
                modifier = Modifier.weight(1f).height(48.dp).clickable { onSelect(index) },
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

/** An HTML help page, rendered by Android as text with its titles and links. */
@Composable
private fun HelpPage(@RawRes page: Int) {
    val resources = LocalResources.current
    val colors = LocalAdAwayColors.current
    val html = remember(page) { resources.openRawResource(page).bufferedReader().use { it.readText() } }
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        AndroidView(
            factory = { viewContext ->
                TextView(viewContext).apply {
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setLineSpacing(0f, 1.25f)
                    setTextIsSelectable(true)
                    movementMethod = LinkMovementMethod.getInstance()
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
private fun AboutPage() {
    val colors = LocalAdAwayColors.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
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
        Link(R.string.about_source_label, REPOSITORY, Modifier.padding(top = 8.dp))
        Link(R.string.about_issues_label, "$REPOSITORY/issues")
        Link(R.string.about_releases_label, "$REPOSITORY/releases")
        Link(R.string.about_upstream_label, UPSTREAM)
        HelpDivider(Modifier.padding(vertical = 8.dp))
        Text(
            stringResource(R.string.about_license_label),
            style = AdAwayType.small,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun Link(@StringRes text: Int, url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                } catch (_: ActivityNotFoundException) {
                    // No browser on the device.
                }
            }
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
