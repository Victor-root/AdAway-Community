package org.adaway.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.adaway.R
import org.adaway.ui.common.AdAwayDialog
import org.adaway.ui.common.DialogButton
import org.adaway.ui.theme.AdAwayColors
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.LocalAdAwayColors

private val HeaderButtonShape = RoundedCornerShape(20.dp)
private val CardShape = RoundedCornerShape(16.dp)

/** The highlight the 6.x controls take with the focus or a press: [color] laid over them at [alpha]. */
fun highlight(color: Color, alpha: Float) = RippleConfiguration(color, RippleAlpha(draggedAlpha = alpha, focusedAlpha = alpha, hoveredAlpha = alpha / 3, pressedAlpha = alpha))

/** The gray highlight of the plain list rows and icons, stronger on the dark theme. */
fun rowHighlight(colors: AdAwayColors) =
    if (colors.surface.luminance() < 0.5f) highlight(Color.White, 0.2f) else highlight(Color.Black, 0.12f)

/** The gray highlight of the cards, lighter than the rows' on the dark theme. */
private fun cardHighlight(colors: AdAwayColors) =
    if (colors.surface.luminance() < 0.5f) highlight(Color.White, 0.1f) else highlight(Color.Black, 0.12f)

/** A TV screen: its header, the content under it, and the snackbar at the bottom. */
@Composable
fun TvScreen(header: @Composable () -> Unit, snackbar: @Composable () -> Unit = {}, content: @Composable BoxScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(LocalAdAwayColors.current.surface)) {
        header()
        Box(modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()) {
            content()
            Box(modifier = Modifier.align(Alignment.BottomCenter)) { snackbar() }
        }
    }
}

/**
 * The red header of the TV screens: an icon and the title, the action buttons under them, and the
 * [hint] at the bottom.
 */
@Composable
fun TvHeader(
    icon: Painter,
    title: String,
    horizontalPadding: Dp = 20.dp,
    titlePadding: Dp = 4.dp,
    actionsCentered: Boolean = true,
    hint: String? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    val colors = LocalAdAwayColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.primary)
            .statusBarsPadding()
            .padding(start = horizontalPadding, top = 14.dp, end = horizontalPadding, bottom = 12.dp),
    ) {
        Row(modifier = Modifier.padding(vertical = titlePadding), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Text(title, style = AdAwayType.headline6, color = colors.onPrimary)
        }
        Row(modifier = Modifier.align(if (actionsCentered) Alignment.CenterHorizontally else Alignment.Start).padding(top = 10.dp), content = actions)
        hint?.let {
            Text(it, style = AdAwayType.caption, color = colors.onPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

/** A white outlined pill of the red header. It keeps its look when disabled, as on 6.x, but takes no focus. */
@Composable
fun TvHeaderButton(text: String, icon: Painter, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val white = LocalAdAwayColors.current.onPrimary
    CompositionLocalProvider(LocalRippleConfiguration provides highlight(white, 0.6f)) {
        Row(
            modifier = modifier
                .padding(vertical = 6.dp)
                .heightIn(min = 36.dp)
                .defaultMinSize(minWidth = 64.dp)
                .clip(HeaderButtonShape)
                .border(1.dp, white, HeaderButtonShape)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = white, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text.uppercase(),
                style = AdAwayType.button.copy(fontSize = 13.sp),
                color = white,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The flat card of the TV screens: a thin outline instead of a shadow, which TVs render badly. */
@Composable
fun TvCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = LocalAdAwayColors.current
    CompositionLocalProvider(LocalRippleConfiguration provides cardHighlight(colors)) {
        Box(
            modifier = modifier
                .clip(CardShape)
                .background(colors.surface)
                .border(1.dp, colors.bumpBarStroke, CardShape)
                .clickable(onClick = onClick),
            content = content,
        )
    }
}

/** A plain list row, highlighted across its width. */
@Composable
fun TvListRow(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    CompositionLocalProvider(LocalRippleConfiguration provides rowHighlight(LocalAdAwayColors.current)) {
        Column(modifier = modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), content = content)
    }
}

/** The text of an empty list, in the middle of the screen. */
@Composable
fun TvEmptyText(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        Text(text, style = AdAwayType.body1, color = LocalAdAwayColors.current.textPrimary, textAlign = TextAlign.Center)
    }
}

/** A dialog listing [items] to pick from, the first one focused, then a cancel button. */
@Composable
fun TvListDialog(title: String, items: List<DialogButton>, onDismiss: () -> Unit) {
    val colors = LocalAdAwayColors.current
    val firstItem = remember { FocusRequester() }
    AdAwayDialog(onDismissRequest = onDismiss, title = title, confirmButton = DialogButton(stringResource(R.string.cancel), onClick = onDismiss)) {
        CompositionLocalProvider(LocalRippleConfiguration provides rowHighlight(colors)) {
            Column {
                items.forEachIndexed { index, item ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .then(if (index == 0) Modifier.focusRequester(firstItem) else Modifier)
                            .clickable {
                                onDismiss()
                                item.onClick()
                            }
                            .padding(horizontal = 24.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(item.text, style = AdAwayType.subtitle1, color = colors.textPrimary)
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { firstItem.requestFocus() }
}
