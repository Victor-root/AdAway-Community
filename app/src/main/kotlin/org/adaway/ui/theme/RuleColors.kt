package org.adaway.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.adaway.data.db.RuleType

/** Color of each kind of rule, the same on every screen. */
@Composable
fun ruleTypeColor(type: RuleType): Color {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return when (type) {
        RuleType.BLOCKED -> if (dark) Color(0xFFFF8A80) else Color(0xFFC62828)
        RuleType.ALLOWED -> if (dark) Color(0xFF81C784) else Color(0xFF2E7D32)
        RuleType.REDIRECTED -> if (dark) Color(0xFF9FA8DA) else Color(0xFF3949AB)
    }
}
