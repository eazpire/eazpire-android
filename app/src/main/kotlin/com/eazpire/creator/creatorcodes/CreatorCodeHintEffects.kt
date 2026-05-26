package com.eazpire.creator.creatorcodes

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors

/** Orange glow pulse for profile / settings hint targets. */
fun Modifier.creatorCodeHintPulse(active: Boolean, cornerRadiusDp: Float = 10f): Modifier = composed {
    if (!active) return@composed this
    val transition = rememberInfiniteTransition(label = "creatorCodeHintPulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "creatorCodeHintPulseValue",
    )
    scale(1f + pulse * 0.06f)
        .border(
            width = (1.5f + pulse).dp,
            color = EazColors.Orange.copy(alpha = 0.35f + pulse * 0.45f),
            shape = RoundedCornerShape(cornerRadiusDp.dp),
        )
}
