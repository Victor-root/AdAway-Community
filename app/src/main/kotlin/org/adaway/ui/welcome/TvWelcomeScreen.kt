package org.adaway.ui.welcome

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.adaway.R
import org.adaway.data.settings.BlockingMethod
import org.adaway.ui.common.LogoBadge
import org.adaway.ui.theme.BrandRed

@Composable
fun TvWelcomeScreen(state: WelcomeState, actions: WelcomeActions) {
    var started by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandRed)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 96.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        LogoBadge(size = 96.dp)
        if (started) MethodPage(state, actions) else IntroPage(onStart = { started = true })
    }
}

@Composable
private fun IntroPage(onStart: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    WhiteText(R.string.app_title, MaterialTheme.typography.headlineLarge)
    WhiteText(R.string.app_subtitle, MaterialTheme.typography.titleMedium)
    WhiteText(R.string.tv_welcome_intro_subtitle, MaterialTheme.typography.bodyLarge)
    WelcomeButton(R.string.tv_welcome_start_button, onClick = onStart, modifier = Modifier.focusRequester(focusRequester))
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

@Composable
private fun MethodPage(state: WelcomeState, actions: WelcomeActions) {
    val methodFocus = remember { FocusRequester() }
    val nextFocus = remember { FocusRequester() }
    WhiteText(R.string.tv_welcome_method_header, MaterialTheme.typography.headlineMedium)
    WhiteText(R.string.welcome_method_summary, MaterialTheme.typography.bodyLarge)
    WelcomeButton(
        text = R.string.tv_welcome_method_root_button,
        onClick = actions.chooseRoot,
        enabled = !state.checkingRoot,
        modifier = Modifier.focusRequester(methodFocus),
    )
    WhiteText(R.string.tv_welcome_method_root_body, MaterialTheme.typography.bodyMedium)
    WelcomeButton(text = R.string.tv_welcome_method_vpn_button, onClick = actions.chooseVpn)
    WhiteText(R.string.tv_welcome_method_vpn_body, MaterialTheme.typography.bodyMedium)
    val methodChosen = state.method != BlockingMethod.UNSET
    if (methodChosen) {
        WelcomeButton(R.string.welcome_next_button, onClick = actions.finish, modifier = Modifier.focusRequester(nextFocus))
    }
    LaunchedEffect(methodChosen) {
        if (methodChosen) nextFocus.requestFocus() else methodFocus.requestFocus()
    }
}

@Composable
private fun WelcomeButton(
    @StringRes text: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.widthIn(min = 320.dp),
        colors = ButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.2f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = BrandRed,
        ),
    ) {
        Text(stringResource(text))
    }
}

@Composable
private fun WhiteText(@StringRes text: Int, style: TextStyle) {
    Text(text = stringResource(text), style = style, color = Color.White, textAlign = TextAlign.Center)
}
