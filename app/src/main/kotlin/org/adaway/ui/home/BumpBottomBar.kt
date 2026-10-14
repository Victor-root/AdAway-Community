package org.adaway.ui.home

import android.graphics.Paint
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import org.adaway.ui.common.DISABLED_ALPHA
import org.adaway.ui.theme.LocalAdAwayColors

private val BarHeight = 26.dp
private val BumpHeight = 10.dp
private val EdgeFlat = 12.dp
private val FabSize = 56.dp
private val TopPad = 6.dp
private val ShadowRadius = 8.dp
private val ShadowOffset = 4.dp
/** Ledge, crest, the upper half of the button and room for the shadow. */
private val TotalHeight = BarHeight + BumpHeight + FabSize / 2 + TopPad + ShadowRadius
/** S-curve softness of the swell, from 0 to 1. */
private const val CURVE = 0.5f
private val ShadowColor = Color(0x44000000)

/** Room the scrolling content keeps free at its end to clear the button crest. */
val BumpBottomBarReservedHeight = 70.dp

/**
 * The bottom bar: one flat ledge rising in a broad, gentle swell to a crest under the round
 * button, with a soft shadow along its outline and a fine stroke on its top edge. It extends under
 * the system navigation bar.
 */
@Composable
fun BumpBottomBar(icon: Painter, contentDescription: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalAdAwayColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        BumpBar {
            Box(
                modifier = Modifier
                    .size(FabSize)
                    .clip(CircleShape)
                    .background(colors.primary)
                    .clickable(enabled = enabled, onClick = onClick)
                    .alpha(if (enabled) 1f else DISABLED_ALPHA),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = contentDescription, tint = colors.onPrimary, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(modifier = Modifier.fillMaxWidth().windowInsetsBottomHeight(WindowInsets.navigationBars).background(colors.bumpBarBackground))
    }
}

/**
 * The swelling bar alone, with its round [badge] of 56 dp centered on the crest: the lower half
 * nestles in the bar, the upper half emerges above it. The TV home shows the blocking state so.
 */
@Composable
fun BumpBar(modifier: Modifier = Modifier, badge: @Composable () -> Unit) {
    val colors = LocalAdAwayColors.current
    Box(modifier = modifier.fillMaxWidth().height(TotalHeight)) {
        Canvas(modifier = Modifier.fillMaxWidth().height(TotalHeight)) {
            val ledgeY = size.height - BarHeight.toPx()
            val crestY = ledgeY - BumpHeight.toPx()
            val fill = topEdge(size, ledgeY, crestY).apply {
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawFillWithShadow(fill, colors.bumpBarBackground)
            drawPath(topEdge(size, ledgeY, crestY), colors.bumpBarStroke, style = Stroke(width = 1.dp.toPx()))
        }
        Box(modifier = Modifier.align(Alignment.TopCenter).offset(y = TotalHeight - BarHeight - BumpHeight - FabSize / 2)) {
            badge()
        }
    }
}

/** Flat left margin, a long swell up to a wide crest and back down, then flat right margin. */
private fun DrawScope.topEdge(size: Size, ledgeY: Float, crestY: Float) = Path().apply {
    val centerX = size.width / 2
    val leftStart = EdgeFlat.toPx()
    val rightEnd = size.width - leftStart
    val control = (centerX - leftStart) * CURVE
    moveTo(0f, ledgeY)
    lineTo(leftStart, ledgeY)
    cubicTo(leftStart + control, ledgeY, centerX - control, crestY, centerX, crestY)
    cubicTo(centerX + control, crestY, rightEnd - control, ledgeY, rightEnd, ledgeY)
    lineTo(size.width, ledgeY)
}

/** Android draws the shadow of any path with hardware rendering from Android 9 on, and none before. */
private fun DrawScope.drawFillWithShadow(path: Path, color: Color) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        this.color = color.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            setShadowLayer(ShadowRadius.toPx(), 0f, ShadowOffset.toPx(), ShadowColor.toArgb())
        }
    }
    drawIntoCanvas { it.nativeCanvas.drawPath(path.asAndroidPath(), paint) }
}
