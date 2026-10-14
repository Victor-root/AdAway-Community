package org.adaway.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** The text sizes of the 6.x interface. */
object AdAwayType {
    val large = TextStyle(fontSize = 22.sp)
    val medium = TextStyle(fontSize = 18.sp)
    val small = TextStyle(fontSize = 14.sp)
    val headline6 = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.0125.em)
    val subtitle1 = TextStyle(fontSize = 16.sp, letterSpacing = 0.009375.em)
    val subtitle2 = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.0071429.em)
    val body2 = TextStyle(fontSize = 14.sp, letterSpacing = 0.0178571.em)
    val caption = TextStyle(fontSize = 12.sp, letterSpacing = 0.0333333.em)
    val button = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.0892857.em)
}
