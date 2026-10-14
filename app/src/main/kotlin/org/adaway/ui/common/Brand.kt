package org.adaway.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.adaway.BuildConfig
import org.adaway.R
import org.adaway.ui.theme.BrandRed

/** The app mark, red on a white disc so that it stands out on the red backgrounds. */
@Composable
fun LogoBadge(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.logo),
        contentDescription = stringResource(R.string.logo_description),
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White)
            .padding(size / 8),
    )
}

/** Red header with the app name and version, drawn behind the status bar. */
@Composable
fun BrandHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BrandRed)
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        LogoBadge(size = 56.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
            )
            Text(
                text = stringResource(R.string.app_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
        }
        Text(
            text = stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
    }
}
