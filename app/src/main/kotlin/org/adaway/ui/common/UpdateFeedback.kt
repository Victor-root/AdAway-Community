package org.adaway.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.adaway.R
import org.adaway.blocklist.UpdateError
import org.adaway.sources.UpdateProgress

@Composable
fun UpdateErrorDialog(error: UpdateError, onRetry: () -> Unit, onDismiss: () -> Unit) {
    val canRetry = error != UpdateError.APPLY_FAILED
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(updateErrorTitle(error)) },
        text = { Text(updateErrorText(error)) },
        confirmButton = {
            if (canRetry) {
                TextButton(onClick = { onDismiss(); onRetry() }) { Text(stringResource(R.string.retry_sync)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

/** Progress bar and text of a running update. */
@Composable
fun UpdateProgressLine(progress: UpdateProgress, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            text = updateProgressText(progress),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Bottom banner telling that the changes are not applied yet. */
@Composable
fun ApplyBanner(applying: Boolean, onApply: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(if (applying) R.string.apply_in_progress else R.string.apply_needed),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (applying) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.inversePrimary,
                )
            } else {
                TextButton(onClick = onApply) {
                    Text(stringResource(R.string.apply), color = MaterialTheme.colorScheme.inversePrimary)
                }
            }
        }
    }
}
