package org.adaway.ui.prefs

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.adaway.R
import org.adaway.system.copyToClipboard
import org.adaway.ui.common.AdAwayDialog
import org.adaway.ui.common.DialogButton
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.LocalAdAwayColors

/** Beyond, only the end of the log is shown, to keep the dialog fluid. It is still copied and shared whole. */
private const val PREVIEW_CHARS = 20_000

/** The recorded log, to copy or share. Only its text scrolls, the buttons stay in view. */
@Composable
fun DiagnosticLogDialog(log: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val empty = log.isBlank()
    val copied = stringResource(R.string.pref_vpn_diagnostic_log_copied)
    val shareTitle = stringResource(R.string.pref_vpn_diagnostic_log_share_title)
    val ok = DialogButton(stringResource(android.R.string.ok), onClick = onDismiss)
    AdAwayDialog(
        onDismissRequest = onDismiss,
        title = stringResource(R.string.pref_vpn_diagnostic_log),
        confirmButton = if (empty) {
            ok
        } else {
            DialogButton(stringResource(R.string.pref_vpn_diagnostic_log_share), onClick = {
                onDismiss()
                val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, shareTitle).putExtra(Intent.EXTRA_TEXT, log)
                context.startActivity(Intent.createChooser(share, shareTitle))
            })
        },
        dismissButton = if (empty) null else ok,
        neutralButton = if (empty) {
            null
        } else {
            DialogButton(stringResource(R.string.pref_vpn_diagnostic_log_copy), onClick = {
                onDismiss()
                context.copyToClipboard(log)
                Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
            })
        },
    ) {
        SelectionContainer {
            Text(
                if (empty) stringResource(R.string.pref_vpn_diagnostic_log_empty) else preview(log),
                style = AdAwayType.body2,
                fontFamily = FontFamily.Monospace,
                color = LocalAdAwayColors.current.textPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }
}

private fun preview(log: String) = if (log.length <= PREVIEW_CHARS) log else "…\n" + log.takeLast(PREVIEW_CHARS)
