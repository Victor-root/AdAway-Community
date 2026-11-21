package org.adaway.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.adaway.R
import org.adaway.system.copyAndTell
import org.adaway.ui.theme.LocalAdAwayColors
import org.adaway.ui.theme.LocalTelevision

/** An ADB command in a code block, with a button that copies it on phones, where it can be pasted. */
@Composable
fun CommandBlock(command: String) {
    val colors = LocalAdAwayColors.current
    val context = LocalContext.current
    val isTelevision = LocalTelevision.current
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
