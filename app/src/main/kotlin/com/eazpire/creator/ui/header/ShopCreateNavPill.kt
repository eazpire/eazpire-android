package com.eazpire.creator.ui.header

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors

/**
 * Charcoal square + scale pulse; label and background share the same transform (matches web).
 */
@Composable
fun ShopCreateNavPill(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "shopCreateNavPulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.11f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RectangleShape)
            .background(EazColors.ShopCreateNavGradient)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
