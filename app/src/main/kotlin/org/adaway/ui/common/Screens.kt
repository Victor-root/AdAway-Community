package org.adaway.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.adaway.ui.theme.BrandRed
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Button as TvButton
import androidx.tv.material3.FilterChip as TvFilterChip
import androidx.tv.material3.Text as TvText

/**
 * TV screen: every control sits in the header, where the remote reaches it, and the Back button of
 * the remote leaves the screen.
 */
@Composable
fun TvScreen(
    title: String,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(BrandRed)
                .padding(horizontal = 48.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            actions()
        }
        Column(modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp), content = content)
    }
}

/** A button with the focus feedback the device needs. */
@Composable
fun AppButton(text: String, onClick: () -> Unit, isTelevision: Boolean, enabled: Boolean = true) {
    if (isTelevision) {
        TvButton(onClick = onClick, enabled = enabled) { TvText(text) }
    } else {
        Button(onClick = onClick, enabled = enabled) { Text(text) }
    }
}

/** A selectable choice, with the focus feedback the device needs. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, isTelevision: Boolean) {
    if (isTelevision) {
        TvFilterChip(selected = selected, onClick = onClick) { TvText(text) }
    } else {
        FilterChip(selected = selected, onClick = onClick, label = { Text(text) })
    }
}
