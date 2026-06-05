package com.eazpire.creator.ui.vouchers

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

/** Native LoyaliTee mark — tee + orange stamp badge (matches #eaz-icon-loyalitee). */
@Composable
fun LoyaliTeeLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sx = w / 24f
        val sy = h / 24f

        val teeFill = Color(0xFFE0F2FE)
        val teeStroke = Color(0xFF0EA5E9)
        val badge = Color(0xFFF97316)

        val collar = Path().apply {
            moveTo(7.5f * sx, 4f * sy)
            lineTo(16.5f * sx, 4f * sy)
            lineTo(18.25f * sx, 7.25f * sy)
            lineTo(5.75f * sx, 7.25f * sy)
            close()
        }
        drawPath(collar, teeFill)
        drawPath(collar, teeStroke, style = Stroke(width = 1.25f * sx))

        val body = Path().apply {
            moveTo(6.25f * sx, 7.25f * sy)
            lineTo(17.75f * sx, 7.25f * sy)
            lineTo(16.2f * sx, 19f * sy)
            lineTo(7.8f * sx, 19f * sy)
            close()
        }
        drawPath(body, Color(0xFFF0F9FF))
        drawPath(body, teeStroke, style = Stroke(width = 1.25f * sx))

        val badgeCenter = Offset(17.25f * sx, 17.25f * sy)
        val badgeR = 5.25f * sx
        drawCircle(color = badge, radius = badgeR, center = badgeCenter)
        drawCircle(color = Color.White, radius = badgeR, center = badgeCenter, style = Stroke(width = 1.35f * sx))
        drawLine(
            color = Color.White,
            start = Offset(17.25f * sx, 14.75f * sy),
            end = Offset(17.25f * sx, 19.75f * sy),
            strokeWidth = 1.35f * sx
        )
        drawLine(
            color = Color.White,
            start = Offset(14.75f * sx, 17.25f * sy),
            end = Offset(19.75f * sx, 17.25f * sy),
            strokeWidth = 1.35f * sx
        )
    }
}
