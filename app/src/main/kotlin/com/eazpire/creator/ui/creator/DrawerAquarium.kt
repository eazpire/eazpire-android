package com.eazpire.creator.ui.creator

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.sin

/**
 * Aquarium-Partikel-Hintergrund wie Web drawer-aquarium.js.
 * Vereinfachte native Umsetzung: schwebende Partikel.
 */
@Composable
fun DrawerAquarium(modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    val transition = rememberInfiniteTransition(label = "aquarium")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val particleCount = 24
        val baseColor = Color(0xFFF97316)

        for (i in 0 until particleCount) {
            val seed = i * 0.137f
            val x = (w * (0.1f + (seed * 0.8f % 1f)))
            val yBase = h * (0.2f + (seed * 0.6f % 1f))
            val y = yBase + sin((phase * 2 * PI + seed * 2).toFloat()) * 12
            val radius = 2f + (seed % 3f)
            val alpha = 0.08f + (seed % 0.12f)

            drawCircle(
                color = baseColor.copy(alpha = alpha),
                radius = radius,
                center = Offset(x, y)
            )
        }
    }
}
