package org.adaway.ui.welcome

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.adaway.R
import org.adaway.data.settings.BlockingMethod
import org.adaway.ui.common.LogoBadge
import org.adaway.ui.theme.BrandRed

private val AdvantageColor = Color(0xFF2E7D32)
private val DrawbackColor = Color(0xFFC62828)

private class MethodPoint(@StringRes val text: Int, val advantage: Boolean)

private val RootPoints = listOf(
    MethodPoint(R.string.welcome_root_faster, advantage = true),
    MethodPoint(R.string.welcome_root_battery, advantage = true),
    MethodPoint(R.string.welcome_root_required, advantage = false),
)

private val VpnPoints = listOf(
    MethodPoint(R.string.welcome_vpn_compatibility, advantage = true),
    MethodPoint(R.string.welcome_vpn_slower, advantage = false),
    MethodPoint(R.string.welcome_vpn_background, advantage = false),
)

@Composable
fun WelcomeScreen(state: WelcomeState, actions: WelcomeActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandRed)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LogoBadge(size = 96.dp)
        Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.welcome_method_text),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        MethodCard(
            title = R.string.welcome_method_root,
            points = RootPoints,
            selected = state.method == BlockingMethod.ROOT,
            checking = state.checkingRoot,
            onClick = actions.chooseRoot,
        )
        MethodCard(
            title = R.string.welcome_method_vpn,
            points = VpnPoints,
            selected = state.method == BlockingMethod.VPN,
            checking = false,
            onClick = actions.chooseVpn,
        )
        if (state.method != BlockingMethod.UNSET) {
            Button(
                onClick = actions.finish,
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = BrandRed),
            ) {
                Text(stringResource(R.string.welcome_next))
            }
        }
    }
}

@Composable
private fun MethodCard(
    @StringRes title: Int,
    points: List<MethodPoint>,
    selected: Boolean,
    checking: Boolean,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        enabled = !checking,
        modifier = Modifier.fillMaxWidth(),
        border = if (selected) BorderStroke(3.dp, AdvantageColor) else null,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                when {
                    checking -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    selected -> Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = AdvantageColor)
                }
            }
            points.forEach { point -> MethodPointRow(point) }
        }
    }
}

@Composable
private fun MethodPointRow(point: MethodPoint) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            painter = painterResource(if (point.advantage) R.drawable.ic_add else R.drawable.ic_remove),
            contentDescription = stringResource(
                if (point.advantage) R.string.welcome_advantage else R.string.welcome_drawback,
            ),
            tint = if (point.advantage) AdvantageColor else DrawbackColor,
            modifier = Modifier.size(20.dp),
        )
        Text(text = stringResource(point.text), style = MaterialTheme.typography.bodyMedium)
    }
}
