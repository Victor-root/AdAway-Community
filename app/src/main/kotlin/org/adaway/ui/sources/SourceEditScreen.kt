package org.adaway.ui.sources

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.adaway.R
import org.adaway.data.db.SourceFormat
import org.adaway.sources.SourceFieldError
import org.adaway.ui.common.AdAwayCheckboxRow
import org.adaway.ui.common.AppBarAction
import org.adaway.ui.common.AdAwayScreen
import org.adaway.ui.common.AdAwayTextField
import org.adaway.ui.common.AdAwayToggleGroup
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.LocalAdAwayColors

/** The phone source form: delete and save sit in the app bar. A deletion can be undone from the list. */
@Composable
fun SourceEditScreen(title: String, form: SourceForm, viewModel: SourceEditViewModel, save: () -> Unit, onBack: () -> Unit) {
    val colors = LocalAdAwayColors.current
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::usePickedFile)
    }
    AdAwayScreen(
        title = title,
        onBack = onBack,
        actions = {
            if (!viewModel.isNew) {
                AppBarAction(painterResource(R.drawable.outline_delete_24), stringResource(R.string.delete)) { viewModel.delete(undoable = true) }
            }
            AppBarAction(painterResource(R.drawable.baseline_check_24), stringResource(R.string.apply), onClick = save)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
        ) {
            AdAwayTextField(
                value = form.label,
                onValueChange = { label -> viewModel.edit { it.copy(label = label) } },
                label = stringResource(R.string.source_label),
                error = if (SourceFieldError.LABEL_REQUIRED in form.errors) stringResource(R.string.source_label_required) else null,
            )
            ChoiceRow(R.string.source_format) {
                AdAwayToggleGroup(
                    options = listOf(
                        SourceFormat.BLOCK to stringResource(R.string.source_format_block),
                        SourceFormat.ALLOW to stringResource(R.string.source_format_allow),
                    ),
                    selected = form.format,
                    onSelect = { format -> viewModel.edit { it.copy(format = format) } },
                )
            }
            ChoiceRow(R.string.source_type) {
                AdAwayToggleGroup(
                    options = listOf(false to stringResource(R.string.source_type_url), true to stringResource(R.string.source_type_file)),
                    selected = form.isFile,
                    onSelect = { file -> if (file) pickFile.launch(arrayOf("*/*")) else if (form.isFile) viewModel.useUrl() },
                )
            }
            val locationError = when {
                SourceFieldError.LOCATION_REQUIRED in form.errors -> R.string.source_location_required
                SourceFieldError.LOCATION_INVALID in form.errors -> R.string.source_location_invalid
                SourceFieldError.LOCATION_DUPLICATE in form.errors -> R.string.source_location_duplicate
                else -> null
            }
            if (form.isFile) {
                Text(
                    form.url,
                    style = AdAwayType.body2,
                    color = colors.textPrimary,
                    modifier = Modifier.fillMaxWidth().clickable { pickFile.launch(arrayOf("*/*")) }.padding(8.dp),
                )
                locationError?.let { Text(stringResource(it), style = AdAwayType.caption, color = colors.primary, modifier = Modifier.padding(horizontal = 8.dp)) }
            } else {
                AdAwayTextField(
                    value = form.url,
                    onValueChange = { url -> viewModel.edit { it.copy(url = url) } },
                    label = stringResource(R.string.source_location),
                    error = locationError?.let { stringResource(it) },
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            if (form.format == SourceFormat.BLOCK) {
                AdAwayCheckboxRow(
                    stringResource(R.string.source_redirects),
                    checked = form.redirectsEnabled,
                    onCheckedChange = { checked -> viewModel.edit { it.copy(redirectsEnabled = checked) } },
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    stringResource(R.string.source_redirects_warning),
                    style = AdAwayType.small,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Justify,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** A field title on the left, its choices on the right. */
@Composable
private fun ChoiceRow(@StringRes title: Int, choices: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(title),
            style = AdAwayType.subtitle2,
            color = LocalAdAwayColors.current.textPrimary,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        choices()
    }
}
