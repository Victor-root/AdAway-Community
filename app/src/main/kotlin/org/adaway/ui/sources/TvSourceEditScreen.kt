package org.adaway.ui.sources

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.annotation.StringRes
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.adaway.R
import org.adaway.data.db.SourceFormat
import org.adaway.sources.SourceFieldError
import org.adaway.ui.common.AppButton
import org.adaway.ui.common.ChoiceChip
import org.adaway.ui.common.TvScreen

/** The TV source form, made for the remote: the actions sit in the header. */
@Composable
fun TvSourceEditScreen(title: String, form: SourceForm, viewModel: SourceEditViewModel, save: () -> Unit) {
    var confirmDeletion by rememberSaveable { mutableStateOf(false) }
    TvScreen(
        title = title,
        actions = {
            AppButton(stringResource(R.string.save), save, isTelevision = true)
            if (!viewModel.isNew) {
                AppButton(stringResource(R.string.delete), { confirmDeletion = true }, isTelevision = true)
            }
        },
    ) {
        SourceFormFields(form, viewModel, isTelevision = true)
    }
    if (confirmDeletion) {
        AlertDialog(
            onDismissRequest = { confirmDeletion = false },
            title = { Text(stringResource(R.string.source_delete_confirm_title)) },
            text = { Text(form.label) },
            confirmButton = {
                TextButton(onClick = { confirmDeletion = false; viewModel.delete(undoable = false) }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = { TextButton(onClick = { confirmDeletion = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SourceFormFields(form: SourceForm, viewModel: SourceEditViewModel, isTelevision: Boolean) {
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::usePickedFile)
    }
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = form.label,
            onValueChange = { label -> viewModel.edit { it.copy(label = label) } },
            label = { Text(stringResource(R.string.source_label)) },
            singleLine = true,
            isError = SourceFieldError.LABEL_REQUIRED in form.errors,
            supportingText = {
                if (SourceFieldError.LABEL_REQUIRED in form.errors) Text(stringResource(R.string.source_label_required))
            },
            modifier = Modifier.fillMaxWidth(),
        )
        FieldTitle(R.string.source_format)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(
                stringResource(R.string.source_format_block),
                selected = form.format == SourceFormat.BLOCK,
                onClick = { viewModel.edit { it.copy(format = SourceFormat.BLOCK) } },
                isTelevision = isTelevision,
            )
            ChoiceChip(
                stringResource(R.string.source_format_allow),
                selected = form.format == SourceFormat.ALLOW,
                onClick = { viewModel.edit { it.copy(format = SourceFormat.ALLOW) } },
                isTelevision = isTelevision,
            )
        }
        FieldTitle(R.string.source_type)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceChip(
                stringResource(R.string.source_type_url),
                selected = !form.isFile,
                onClick = { if (form.isFile) viewModel.useUrl() },
                isTelevision = isTelevision,
            )
            ChoiceChip(
                stringResource(R.string.source_type_file),
                selected = form.isFile,
                onClick = { pickFile.launch(arrayOf("*/*")) },
                isTelevision = isTelevision,
            )
        }
        val locationError = when {
            SourceFieldError.LOCATION_REQUIRED in form.errors -> R.string.source_location_required
            SourceFieldError.LOCATION_INVALID in form.errors -> R.string.source_location_invalid
            SourceFieldError.LOCATION_DUPLICATE in form.errors -> R.string.source_location_duplicate
            else -> null
        }
        if (form.isFile) {
            Text(form.url, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
        } else {
            OutlinedTextField(
                value = form.url,
                onValueChange = { url -> viewModel.edit { it.copy(url = url) } },
                label = { Text(stringResource(R.string.source_location)) },
                singleLine = true,
                isError = locationError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        locationError?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        if (form.format == SourceFormat.BLOCK) {
            ChoiceChip(
                stringResource(R.string.source_redirects),
                selected = form.redirectsEnabled,
                onClick = { viewModel.edit { it.copy(redirectsEnabled = !it.redirectsEnabled) } },
                isTelevision = isTelevision,
            )
            Text(
                stringResource(R.string.source_redirects_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FieldTitle(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onBackground)
}
