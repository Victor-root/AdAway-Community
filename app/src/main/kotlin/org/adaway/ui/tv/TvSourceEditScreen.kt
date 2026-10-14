package org.adaway.ui.tv

import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.adaway.R
import org.adaway.data.db.SourceFormat
import org.adaway.sources.SourceFieldError
import org.adaway.ui.common.AdAwayCheckboxRow
import org.adaway.ui.common.AdAwayTextField
import org.adaway.ui.common.AdAwayToggleGroup
import org.adaway.ui.sources.SourceEditViewModel
import org.adaway.ui.sources.SourceForm
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.LocalAdAwayColors

/** How long the redirection choice takes to fade in or out with the list format. */
private const val FADE_MILLIS = 300

/**
 * The TV source form: apply and delete sit in the header, apply focused first. Deleting takes no
 * confirmation and cannot be undone, as on 6.x.
 */
@Composable
fun TvSourceEditScreen(title: String, form: SourceForm, viewModel: SourceEditViewModel, save: () -> Unit) {
    val colors = LocalAdAwayColors.current
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::usePickedFile)
    }
    val openFile = {
        try {
            pickFile.launch(arrayOf("*/*"))
        } catch (_: ActivityNotFoundException) {
            // No document picker on this TV.
        }
    }
    val applyFocus = remember { FocusRequester() }
    TvScreen(
        header = {
            TvHeader(icon = painterResource(R.drawable.baseline_edit_24), title = title) {
                TvHeaderButton(
                    stringResource(R.string.apply),
                    painterResource(R.drawable.baseline_check_24),
                    onClick = save,
                    modifier = Modifier.focusRequester(applyFocus).padding(end = 10.dp),
                )
                if (!viewModel.isNew) {
                    TvHeaderButton(stringResource(R.string.delete), painterResource(R.drawable.outline_delete_24), onClick = { viewModel.delete(undoable = false) })
                }
            }
        },
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
            AdAwayTextField(
                value = form.label,
                onValueChange = { label -> viewModel.edit { it.copy(label = label) } },
                label = stringResource(R.string.source_label),
                error = if (SourceFieldError.LABEL_REQUIRED in form.errors) stringResource(R.string.source_label_required) else null,
            )
            FieldTitle(stringResource(R.string.source_format))
            AdAwayToggleGroup(
                options = listOf(
                    SourceFormat.BLOCK to stringResource(R.string.source_format_block),
                    SourceFormat.ALLOW to stringResource(R.string.source_format_allow),
                ),
                selected = form.format,
                onSelect = { format -> viewModel.edit { it.copy(format = format) } },
                modifier = Modifier.padding(top = 2.dp),
            )
            FieldTitle(stringResource(R.string.source_type))
            AdAwayToggleGroup(
                options = listOf(false to stringResource(R.string.source_type_url), true to stringResource(R.string.source_type_file)),
                selected = form.isFile,
                onSelect = { file -> if (file) openFile() else if (form.isFile) viewModel.useUrl() },
                modifier = Modifier.padding(top = 2.dp),
            )
            val locationError = when {
                SourceFieldError.LOCATION_REQUIRED in form.errors -> R.string.source_location_required
                SourceFieldError.LOCATION_INVALID in form.errors -> R.string.source_location_invalid
                SourceFieldError.LOCATION_DUPLICATE in form.errors -> R.string.source_location_duplicate
                else -> null
            }?.let { stringResource(it) }
            if (form.isFile) {
                Text(
                    form.url,
                    style = AdAwayType.body2,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(top = 20.dp).fillMaxWidth().clickable(onClick = openFile).padding(12.dp),
                )
                locationError?.let { Text(it, style = AdAwayType.caption, color = colors.primary, modifier = Modifier.padding(horizontal = 12.dp)) }
            } else {
                LocationField(form.url, locationError, onChange = { url -> viewModel.edit { it.copy(url = url) } })
            }
            // Hidden for an allow list, the room kept.
            val redirects = form.format == SourceFormat.BLOCK
            val redirectsAlpha by animateFloatAsState(if (redirects) 1f else 0f, tween(FADE_MILLIS), label = "redirects")
            AdAwayCheckboxRow(
                stringResource(R.string.source_redirects),
                checked = form.redirectsEnabled,
                onCheckedChange = { checked -> viewModel.edit { it.copy(redirectsEnabled = checked) } },
                modifier = Modifier.padding(top = 20.dp).alpha(redirectsAlpha).focusProperties { canFocus = redirects },
            )
            Text(
                stringResource(R.string.source_redirects_warning),
                style = AdAwayType.caption,
                color = colors.textSecondary,
                textAlign = TextAlign.Justify,
                modifier = Modifier.padding(top = 8.dp).alpha(redirectsAlpha),
            )
        }
    }
    LaunchedEffect(Unit) { applyFocus.requestFocus() }
}

@Composable
private fun FieldTitle(text: String) {
    Text(text, style = AdAwayType.caption, color = LocalAdAwayColors.current.textSecondary, modifier = Modifier.padding(top = 20.dp))
}

/** The address, its cursor put at the end each time the field takes the focus, ready to complete it. */
@Composable
private fun LocationField(url: String, error: String?, onChange: (String) -> Unit) {
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(url, TextRange(url.length))) }
    LaunchedEffect(url) {
        if (url != value.text) value = TextFieldValue(url, TextRange(url.length))
    }
    AdAwayTextField(
        value = value,
        onValueChange = {
            value = it
            onChange(it.text)
        },
        label = stringResource(R.string.source_location),
        error = error,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier
            .padding(top = 20.dp)
            .onFocusChanged { if (it.isFocused) value = value.copy(selection = TextRange(value.text.length)) },
    )
}
