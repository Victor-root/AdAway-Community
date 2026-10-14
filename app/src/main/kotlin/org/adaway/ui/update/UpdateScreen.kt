package org.adaway.ui.update

import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.adaway.R
import org.adaway.ui.common.AdAwayScreen
import org.adaway.ui.common.appViewModel
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.BrandRed

/** The latest release and its changes, on the red background of the first launch screens. */
@Composable
fun UpdateScreen(onBack: () -> Unit) {
    val viewModel = appViewModel(::UpdateViewModel)
    val latest by viewModel.latest.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    val failure by viewModel.failure.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    LaunchedEffect(failure) {
        val message = failure ?: return@LaunchedEffect
        Toast.makeText(context, resources.getString(message), Toast.LENGTH_LONG).show()
        viewModel.failureShown()
    }
    val release = latest
    val updateAvailable = release?.isNewer == true
    AdAwayScreen(title = stringResource(R.string.update_title), onBack = onBack) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(BrandRed).padding(padding)) {
            val iconSize = maxHeight * 0.2f
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // The logo of the launcher icon, whose background is this red.
                Image(
                    painterResource(R.mipmap.icon_foreground),
                    contentDescription = stringResource(R.string.logo_description),
                    modifier = Modifier.size(iconSize),
                )
                Text(
                    stringResource(
                        when {
                            progress != null -> R.string.update_updating_header
                            updateAvailable -> R.string.update_update_available_header
                            else -> R.string.update_up_to_date_header
                        },
                    ),
                    fontSize = 34.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                when {
                    checking -> CircularProgressIndicator(color = Color.White, modifier = Modifier.padding(top = 24.dp))
                    release == null -> Text(
                        stringResource(R.string.pref_update_check_now_failed),
                        style = AdAwayType.medium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(start = 32.dp, top = 24.dp, end = 32.dp),
                    )
                }
                val current = progress
                if (current != null) {
                    DownloadProgress(current)
                } else if (updateAvailable) {
                    OutlinedWhiteButton(stringResource(R.string.update_update_button), viewModel::update)
                }
                if (release != null) {
                    Text(
                        stringResource(if (updateAvailable) R.string.update_new_changelog_title else R.string.update_last_changelog_title),
                        style = AdAwayType.large,
                        color = Color.White,
                        modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 32.dp, end = 32.dp),
                    )
                    Text(
                        markdown(release.changelog),
                        style = AdAwayType.medium,
                        color = Color.White,
                        textAlign = TextAlign.Justify,
                        modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 16.dp, end = 32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadProgress(progress: UpdateProgress) {
    val context = LocalContext.current
    val downloading = progress as? UpdateProgress.Downloading
    val fraction = when {
        downloading == null -> 1f
        downloading.total > 0 -> downloading.downloaded.toFloat() / downloading.total
        else -> null
    }
    val trackColor = Color.White.copy(alpha = 0.3f)
    if (fraction == null) {
        LinearProgressIndicator(color = Color.White, trackColor = trackColor, modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 24.dp, end = 32.dp))
    } else {
        LinearProgressIndicator(
            progress = { fraction },
            color = Color.White,
            trackColor = trackColor,
            modifier = Modifier.fillMaxWidth().padding(start = 32.dp, top = 24.dp, end = 32.dp),
        )
    }
    Text(
        if (downloading == null) {
            stringResource(R.string.update_complete_label)
        } else {
            stringResource(
                R.string.update_progress_label,
                Formatter.formatShortFileSize(context, downloading.downloaded),
                Formatter.formatShortFileSize(context, downloading.total.coerceAtLeast(0)),
            )
        },
        style = AdAwayType.large,
        color = Color.White,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp),
    )
}

/** The outlined white button of the first launch screens. */
@Composable
private fun OutlinedWhiteButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 48.dp, top = 24.dp, end = 48.dp)
            .clip(shape)
            .border(BorderStroke(2.dp, Color.White), shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = AdAwayType.button, color = Color.White)
    }
}

/** The release notes, in the Markdown GitHub shows: titles and bold in bold, lists with bullets. */
private fun markdown(text: String): AnnotatedString = buildAnnotatedString {
    val bold = SpanStyle(fontWeight = FontWeight.Bold)
    text.lines().forEachIndexed { index, rawLine ->
        if (index > 0) append('\n')
        val line = rawLine.trimEnd()
        val item = line.trimStart()
        when {
            line.startsWith("#") -> withStyle(bold) { append(line.trimStart('#').trim().replace("**", "")) }
            item.startsWith("- ") || item.startsWith("* ") -> {
                append("• ")
                appendWithBold(item.substring(2), bold)
            }
            else -> appendWithBold(line, bold)
        }
    }
}

/** Every other part between `**` is bold. */
private fun AnnotatedString.Builder.appendWithBold(text: String, bold: SpanStyle) {
    text.split("**").forEachIndexed { index, part ->
        if (index % 2 == 1) withStyle(bold) { append(part) } else append(part)
    }
}
