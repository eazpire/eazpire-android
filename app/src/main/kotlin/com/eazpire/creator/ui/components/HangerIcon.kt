package com.eazpire.creator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Hanger icon matching web `#eaz-icon-hanger` / my-mockups try-on button.
 */
@Composable
fun HangerIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.Black,
    size: Dp = 20.dp
) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val sx = w / 24f
        val sy = h / 24f
        fun p(x: Float, y: Float) = Offset(x * sx, y * sy)

        val path = Path().apply {
            moveTo(p(12f, 2f).x, p(12f, 2f).y)
            // head circle approximation via cubic arcs is heavy; use simplified hanger stroke path
            moveTo(p(11f, 4.73f).x, p(11f, 4.73f).y)
            lineTo(p(11f, 7f).x, p(11f, 7f).y)
            lineTo(p(4f, 12.2f).x, p(4f, 12.2f).y)
            cubicTo(
                p(4.82f, 14.6f).x, p(4.82f, 14.6f).y,
                p(5.18f, 15.8f).x, p(5.18f, 15.8f).y,
                p(5.18f, 15.8f).x, p(5.18f, 15.8f).y
            )
            lineTo(p(18.82f, 15.8f).x, p(18.82f, 15.8f).y)
            lineTo(p(18f, 12.2f).x, p(18f, 12.2f).y)
            lineTo(p(13f, 7f).x, p(13f, 7f).y)
            lineTo(p(13f, 4.73f).x, p(13f, 4.73f).y)
        }

        drawCircle(
            color = color,
            radius = 2f * sx,
            center = p(12f, 4f),
            style = Stroke(width = 1.5f * sx, cap = StrokeCap.Round)
        )
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = 1.5f * sx,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
