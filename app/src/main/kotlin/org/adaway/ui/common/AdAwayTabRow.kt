package org.adaway.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.LocalAdAwayColors

/**
 * Fixed tabs of equal width, the selected one in red over a red line. [tabModifier] gives each tab
 * its own behavior, such as how the TV remote reaches it.
 */
@Composable
fun AdAwayTabRow(titles: List<String>, selected: Int, onSelect: (Int) -> Unit, tabModifier: (Int) -> Modifier = { Modifier }) {
    val colors = LocalAdAwayColors.current
    Row(modifier = Modifier.fillMaxWidth().background(colors.surface)) {
        titles.forEachIndexed { index, title ->
            val current = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .then(tabModifier(index))
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    title.uppercase(),
                    style = AdAwayType.button,
                    color = if (current) colors.primary else colors.textSecondary,
                    maxLines = 1,
                )
                if (current) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(colors.primary))
                }
            }
        }
    }
}
