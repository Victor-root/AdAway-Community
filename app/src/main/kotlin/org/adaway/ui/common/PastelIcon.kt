package org.adaway.ui.common

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.adaway.ui.theme.Pastel

/** An icon on a pastel disc, the icon taking the disc less its [padding]. */
@Composable
fun PastelIcon(
    @DrawableRes icon: Int,
    pastel: Pastel,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    padding: Dp = 12.dp,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(pastel.background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
    ) {
        Icon(painterResource(icon), contentDescription = contentDescription, tint = pastel.foreground, modifier = Modifier.fillMaxSize())
    }
}
