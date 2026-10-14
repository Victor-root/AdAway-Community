package org.adaway.ui.tv

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.adaway.R
import org.adaway.data.settings.BlockingMethod
import org.adaway.ui.common.rootErrorTitle
import org.adaway.ui.common.statusMessageText
import org.adaway.ui.common.updateErrorTitle
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.BrandRed
import org.adaway.ui.theme.RedNavigationBar
import org.adaway.ui.welcome.WelcomeActions
import org.adaway.ui.welcome.WelcomeState
import org.adaway.ui.welcome.WelcomeSync
import org.adaway.ui.welcome.WelcomeViewModel

/** The ring of the focused welcome button. */
private val FocusRing = Color(0xFFFFC107)
private const val SECONDARY_ALPHA = 0.85f

/** A press darkens the welcome buttons; the focus only shows the ring. */
private val WelcomeButtonHighlight = RippleConfiguration(BrandRed, RippleAlpha(draggedAlpha = 0.24f, focusedAlpha = 0f, hoveredAlpha = 0f, pressedAlpha = 0.24f))

private enum class WelcomePage { INTRO, METHOD, ACTIVATE }

/**
 * The TV first launch wizard on the red background: an introduction, the blocking method, then
 * turning the blocking on, which downloads the sources first.
 */
@Composable
fun TvWelcomeScreen(state: WelcomeState, viewModel: WelcomeViewModel, actions: WelcomeActions) {
    RedNavigationBar()
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    var page by rememberSaveable { mutableStateOf(WelcomePage.INTRO) }
    // Back to the previous page, until the download starts: Back then leaves, as on 6.x.
    BackHandler(enabled = page == WelcomePage.METHOD || (page == WelcomePage.ACTIVATE && sync == null)) {
        page = if (page == WelcomePage.METHOD) WelcomePage.INTRO else WelcomePage.METHOD
    }
    LaunchedEffect(Unit) {
        viewModel.methodGranted.collect {
            page = WelcomePage.ACTIVATE
            viewModel.startSync()
        }
    }
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(BrandRed).systemBarsPadding()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (page) {
                WelcomePage.INTRO -> IntroPage(onStart = { page = WelcomePage.METHOD })
                WelcomePage.METHOD -> MethodPage(state.checkingRoot, actions.chooseRoot, onVpn = { page = WelcomePage.ACTIVATE })
                WelcomePage.ACTIVATE -> ActivatePage(sync, viewModel, actions, state.method)
            }
        }
    }
}

@Composable
private fun ColumnScope.IntroPage(onStart: () -> Unit) {
    // The adaptive icon shows its foreground layer two thirds of its size, its red background lost in the page.
    Box(modifier = Modifier.size(96.dp).clipToBounds(), contentAlignment = Alignment.Center) {
        Image(painterResource(R.mipmap.icon_foreground), contentDescription = stringResource(R.string.logo_description), modifier = Modifier.requiredSize(144.dp))
    }
    WhiteText(stringResource(R.string.app_title), AdAwayType.headline4, bold = true, modifier = Modifier.padding(top = 24.dp))
    WhiteText(stringResource(R.string.app_subtitle), AdAwayType.subtitle1, alpha = SECONDARY_ALPHA, modifier = Modifier.padding(top = 6.dp))
    WhiteText(stringResource(R.string.tv_welcome_intro_subtitle), AdAwayType.body1, alpha = SECONDARY_ALPHA, maxWidth = 720.dp, modifier = Modifier.padding(top = 20.dp))
    WelcomeButton(stringResource(R.string.tv_welcome_start_button), onClick = onStart, focused = true, modifier = Modifier.padding(top = 32.dp))
}

@Composable
private fun ColumnScope.MethodPage(checkingRoot: Boolean, chooseRoot: () -> Unit, onVpn: () -> Unit) {
    WhiteText(stringResource(R.string.tv_welcome_method_header), AdAwayType.headline5, bold = true)
    WhiteText(stringResource(R.string.welcome_method_summary), AdAwayType.body1, alpha = SECONDARY_ALPHA, maxWidth = 720.dp, modifier = Modifier.padding(top = 8.dp))
    WelcomeButton(
        stringResource(R.string.tv_welcome_method_root_button),
        onClick = chooseRoot,
        icon = R.drawable.ic_superuser_24dp,
        enabled = !checkingRoot,
        focused = true,
        modifier = Modifier.padding(top = 32.dp),
    )
    WhiteText(stringResource(R.string.tv_welcome_method_root_body), AdAwayType.body2, alpha = SECONDARY_ALPHA, maxWidth = 480.dp, modifier = Modifier.padding(top = 8.dp))
    WelcomeButton(stringResource(R.string.tv_welcome_method_vpn_button), onClick = onVpn, icon = R.drawable.ic_vpn_key_24dp, modifier = Modifier.padding(top = 24.dp))
    WhiteText(stringResource(R.string.tv_welcome_method_vpn_body), AdAwayType.body2, alpha = SECONDARY_ALPHA, maxWidth = 480.dp, modifier = Modifier.padding(top = 8.dp))
}

/** Asks to turn the VPN on, then shows the synchronization, its success or its failure. */
@Composable
private fun ColumnScope.ActivatePage(sync: WelcomeSync?, viewModel: WelcomeViewModel, actions: WelcomeActions, method: BlockingMethod) {
    when (sync) {
        null -> {
            Icon(painterResource(R.drawable.ic_vpn_key_24dp), contentDescription = null, tint = Color.White, modifier = Modifier.size(72.dp))
            WhiteText(stringResource(R.string.tv_welcome_activate_header), AdAwayType.headline5, bold = true, modifier = Modifier.padding(top = 20.dp))
            WhiteText(stringResource(R.string.tv_welcome_activate_body), AdAwayType.body1, alpha = SECONDARY_ALPHA, maxWidth = 720.dp, modifier = Modifier.padding(top = 12.dp))
            WelcomeButton(
                stringResource(R.string.home_enable_blocking),
                onClick = actions.chooseVpn,
                icon = R.drawable.ic_power_settings_new_24dp,
                focused = true,
                modifier = Modifier.padding(top = 28.dp),
            )
        }
        WelcomeSync.Running -> {
            val message by viewModel.statusMessage.collectAsStateWithLifecycle()
            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(56.dp))
            WhiteText(stringResource(R.string.welcome_sync_header), AdAwayType.headline5, bold = true, modifier = Modifier.padding(top = 24.dp))
            message?.let { statusMessageText(it, method == BlockingMethod.VPN) }?.let {
                WhiteText(it, AdAwayType.body1, alpha = SECONDARY_ALPHA, maxWidth = 720.dp, modifier = Modifier.padding(top = 8.dp))
            }
        }
        WelcomeSync.Done -> {
            Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(Color.White).padding(16.dp)) {
                Icon(painterResource(R.drawable.baseline_check_24), contentDescription = null, tint = BrandRed, modifier = Modifier.fillMaxSize())
            }
            WhiteText(stringResource(R.string.welcome_synced_header), AdAwayType.headline5, bold = true, modifier = Modifier.padding(top = 20.dp))
            WelcomeButton(stringResource(R.string.welcome_finish_button), onClick = actions.finish, focused = true, modifier = Modifier.padding(top = 28.dp))
        }
        is WelcomeSync.Failed, is WelcomeSync.InstallFailed -> {
            val failure = when (sync) {
                is WelcomeSync.Failed -> updateErrorTitle(sync.error)
                is WelcomeSync.InstallFailed -> rootErrorTitle(sync.error)
                else -> ""
            }
            Icon(painterResource(android.R.drawable.ic_dialog_alert), contentDescription = null, tint = Color.White, modifier = Modifier.size(56.dp))
            WhiteText(stringResource(R.string.welcome_sync_error, failure), AdAwayType.body1, maxWidth = 720.dp, modifier = Modifier.padding(top = 20.dp))
            // The VPN may need its consent again.
            WelcomeButton(
                stringResource(R.string.welcome_sync_retry_logo),
                onClick = { if (method == BlockingMethod.VPN) actions.chooseVpn() else viewModel.startSync() },
                focused = true,
                modifier = Modifier.padding(top = 24.dp),
            )
        }
    }
}

/**
 * The white pill of the welcome pages, a 3 dp amber ring around it with the focus. [focused] gives
 * it the focus when it shows.
 */
@Composable
private fun WelcomeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    focused: Boolean = false,
) {
    val interactions = remember { MutableInteractionSource() }
    val hasFocus by interactions.collectIsFocusedAsState()
    val focus = remember { FocusRequester() }
    val content = if (enabled) BrandRed else BrandRed.copy(alpha = 0.7f)
    CompositionLocalProvider(LocalRippleConfiguration provides WelcomeButtonHighlight) {
        Row(
            modifier = modifier
                .padding(vertical = 6.dp)
                .focusRequester(focus)
                .heightIn(min = 36.dp)
                .clip(CircleShape)
                .background(if (enabled) Color.White else Color.White.copy(alpha = 0.5f))
                .border(3.dp, if (hasFocus) FocusRing else Color.Transparent, CircleShape)
                .clickable(interactionSource = interactions, indication = ripple(), enabled = enabled, onClick = onClick)
                .padding(horizontal = 32.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Icon(painterResource(it), contentDescription = null, tint = content, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text.uppercase(), style = AdAwayType.button.copy(fontSize = 16.sp), fontWeight = FontWeight.Bold, color = content)
        }
    }
    // Again once enabled back, as after the root check.
    if (focused && enabled) LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun WhiteText(text: String, style: TextStyle, modifier: Modifier = Modifier, bold: Boolean = false, alpha: Float = 1f, maxWidth: Dp = Dp.Unspecified) {
    Text(
        text,
        style = style,
        fontWeight = if (bold) FontWeight.Bold else null,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = modifier.widthIn(max = maxWidth).alpha(alpha),
    )
}
