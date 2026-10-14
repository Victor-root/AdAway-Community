package org.adaway.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.adaway.R
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.LocalAdAwayColors

/**
 * A phone screen of the 6.x interface: the red app bar with its back arrow, the content, an optional
 * bottom bar, and at the bottom end the round button above the snackbar, which pushes it up.
 */
@Composable
fun AdAwayScreen(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    topBar: (@Composable () -> Unit)? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    floatingActionButton: @Composable () -> Unit = {},
    snackbar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = LocalAdAwayColors.current
    // Without a bottom bar, the content runs under the system navigation bar.
    val bottomInset = if (bottomBar == null) WindowInsets.navigationBars.asPaddingValues() else PaddingValues()
    Column(modifier = Modifier.fillMaxSize().background(colors.surface)) {
        topBar?.invoke() ?: AdAwayAppBar(onNavigation = onBack, actions = actions) { AppBarTitle(title) }
        Box(modifier = Modifier.weight(1f)) {
            content(bottomInset)
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = bottomInset.calculateBottomPadding()),
                horizontalAlignment = Alignment.End,
            ) {
                Box(modifier = Modifier.padding(16.dp)) { floatingActionButton() }
                snackbar()
            }
        }
        bottomBar?.invoke()
    }
}

/** The red 6.x app bar: a navigation icon, the [title] taking the room left, then the actions. */
@Composable
fun AdAwayAppBar(
    onNavigation: () -> Unit,
    navigationIcon: Painter = painterResource(R.drawable.ic_arrow_back),
    navigationDescription: String = stringResource(R.string.back),
    actions: @Composable RowScope.() -> Unit = {},
    title: @Composable RowScope.() -> Unit,
) {
    val colors = LocalAdAwayColors.current
    Surface(color = colors.primary, shadowElevation = 4.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().statusBarsPadding().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigation) {
                Icon(navigationIcon, contentDescription = navigationDescription, tint = colors.onPrimary)
            }
            title()
            actions()
        }
    }
}

/** The title starts 72 dp from the edge, as on the 6.x app bar. */
@Composable
fun RowScope.AppBarTitle(title: String) {
    Text(
        title,
        style = AdAwayType.headline6,
        color = LocalAdAwayColors.current.onPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).padding(start = 20.dp),
    )
}

/** An app bar action icon, white on the red bar. */
@Composable
fun AppBarAction(icon: Painter, contentDescription: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = contentDescription, tint = LocalAdAwayColors.current.onPrimary)
    }
}

/** The round red button of the 6.x screens. */
@Composable
fun AdAwayFab(icon: Painter, contentDescription: String, onClick: () -> Unit) {
    val colors = LocalAdAwayColors.current
    Box(
        modifier = Modifier
            .size(56.dp)
            .shadow(6.dp, CircleShape)
            .clip(CircleShape)
            .background(colors.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = colors.onPrimary, modifier = Modifier.size(24.dp))
    }
}

/** The 6.x snackbar: a message, then an action or any [trailing] content such as a progress. */
@Composable
fun AdAwaySnackbar(
    message: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    trailing: @Composable () -> Unit = {},
) {
    val colors = LocalAdAwayColors.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        shape = RoundedCornerShape(4.dp),
        color = colors.snackbarBackground,
        shadowElevation = 6.dp,
    ) {
        Row(modifier = Modifier.defaultMinSize(minHeight = 48.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                message,
                style = AdAwayType.body2,
                color = colors.snackbarText,
                modifier = Modifier.weight(1f).padding(top = 14.dp, end = 8.dp, bottom = 14.dp),
            )
            if (actionLabel != null) {
                Text(
                    actionLabel.uppercase(),
                    style = AdAwayType.button,
                    color = colors.snackbarAction,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onAction)
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                )
            }
            trailing()
        }
    }
}
