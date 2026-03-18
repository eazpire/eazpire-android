package com.eazpire.creator.ui.creator

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors

/** Compact audio widget for Creator header – visualizer + back/play/fwd, wie Web */
@Composable
fun CreatorAudioWidget(
    isPlaying: Boolean,
    hasAudio: Boolean,
    onOpenModal: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "audio-vis")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Column(
        modifier = modifier
            .clickable(
                indication = null,
                interactionSource = androidx.compose.runtime.remember { MutableInteractionSource() }
            ) { onOpenModal() }
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(
            modifier = Modifier.size(72.dp, 22.dp),
            onDraw = {
                val w = size.width
                val h = size.height
                val barCount = 16
                val barW = ((w - 6) / barCount - 1).coerceAtLeast(2f)
                val centerY = h / 2
                val staticHeights = listOf(0.15f, 0.35f, 0.5f, 0.4f, 0.6f, 0.3f, 0.45f, 0.55f, 0.25f, 0.4f, 0.5f, 0.35f, 0.45f, 0.3f, 0.4f, 0.25f)
                for (i in 0 until barCount) {
                    val amp = if (isPlaying) {
                        val t = (phase + i * 0.08f) % 1f
                        (staticHeights.getOrElse(i) { 0.3f } * (0.6f + 0.4f * kotlin.math.sin(t * 2 * kotlin.math.PI.toFloat())))
                    } else {
                        staticHeights.getOrElse(i) { 0.3f }
                    }
                    val barH = (amp * h * 0.5f).coerceAtLeast(3f)
                    val x = 3 + (i.toFloat() / barCount) * (w - 6)
                    val t = i.toFloat() / barCount
                    val r = (93 + (100 - 93) * t).toInt()
                    val g = (72 + (200 - 72) * t).toInt()
                    val b = (198 + (255 - 198) * t).toInt()
                    drawRect(
                        color = Color(r, g, b).copy(alpha = 0.6f),
                        topLeft = Offset(x, centerY - barH / 2),
                        size = androidx.compose.ui.geometry.Size(barW, barH)
                    )
                }
            }
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { if (hasAudio) onSeekBack() },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Replay,
                    contentDescription = "Back 10s",
                    tint = if (hasAudio) Color.White else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(14.dp)
                )
            }
            IconButton(
                onClick = { if (hasAudio) onPlayPause() else onOpenModal() },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = if (hasAudio) EazColors.Orange else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(16.dp)
                )
            }
            IconButton(
                onClick = { if (hasAudio) onSeekForward() },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.FastForward,
                    contentDescription = "Forward 10s",
                    tint = if (hasAudio) Color.White else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
