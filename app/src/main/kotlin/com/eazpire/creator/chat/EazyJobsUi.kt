package com.eazpire.creator.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** KV-backed active job row (user jobs feed). */
data class EazyKvJobRow(
    val id: String,
    val title: String,
    val progress: Int,
    val done: Boolean,
    val saving: Boolean,
    val saved: Boolean,
    val isWear: Boolean,
    val status: String?,
    val message: String?,
)

/**
 * Active job card — mirrors web creator-chat__job-item with circular progress + pulse (gen bar).
 */
@Composable
fun EazyActiveJobCard(
    title: String,
    subtitle: String?,
    statusLine: String?,
    progress: Int,
    kindIcon: ImageVector = Icons.Default.Bolt,
    modifier: Modifier = Modifier,
) {
    val palette = LocalEazyModalPalette.current
    val prog = progress.coerceIn(0, 100)
    val pulse by rememberInfiniteTransition(label = "job-pulse").animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "job-pulse-alpha",
    )
    val shimmerShift by rememberInfiniteTransition(label = "job-shimmer").animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "job-shimmer-shift",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.muted.copy(alpha = 0.06f))
            .border(1.dp, palette.accent.copy(alpha = 0.22f * pulse), RoundedCornerShape(12.dp))
            .drawBehind {
                val w = size.width
                val band = w * 0.35f
                val x = shimmerShift * w
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            palette.accent.copy(alpha = 0.07f),
                            Color.Transparent,
                        ),
                        start = Offset(x - band, 0f),
                        end = Offset(x + band, size.height),
                    ),
                )
            }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(44.dp)) {
            CircularJobProgress(progress = prog, accent = palette.accent)
            Icon(
                kindIcon,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = palette.text,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = palette.muted, maxLines = 2)
            }
            LinearProgressIndicator(
                progress = prog / 100f,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                color = palette.accent,
                trackColor = palette.muted.copy(alpha = 0.2f),
                strokeCap = StrokeCap.Round,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                statusLine?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = palette.muted, maxLines = 1)
                }
                Text(
                    "$prog%",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun CircularJobProgress(progress: Int, accent: Color) {
    val sweep = (progress.coerceIn(0, 100) / 100f) * 360f
    Box(
        modifier = Modifier
            .size(44.dp)
            .drawBehind {
                drawCircle(
                    color = accent.copy(alpha = 0.12f),
                    style = Stroke(width = 3.dp.toPx()),
                )
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            },
    )
}

@Composable
fun EazyActiveDesignJobCard(design: DesignJobState, t: (String, String) -> String) {
    EazyActiveJobCard(
        title = t("creator.generator_eazy.job_summary_title", "Design generation"),
        subtitle = design.summary,
        statusLine = design.message ?: t("creator.notifications.generate_design", "Generate Design"),
        progress = design.progress,
        kindIcon = Icons.Default.AutoAwesome,
    )
}

@Composable
fun EazyActiveHeroJobCard(hero: HeroJobState, t: (String, String) -> String) {
    EazyActiveJobCard(
        title = t("creator.hero_eazy.job_summary_title", "Hero image generation"),
        subtitle = hero.summary,
        statusLine = hero.message,
        progress = hero.progress,
        kindIcon = Icons.Default.Image,
    )
}

@Composable
fun EazyActiveVideoJobCard(video: VideoJobState, t: (String, String) -> String) {
    EazyActiveJobCard(
        title = t("creator.content_creation.videos.job_summary_title", "Video generation"),
        subtitle = video.summary,
        statusLine = video.message,
        progress = video.progress,
        kindIcon = Icons.Default.Videocam,
    )
}

@Composable
fun EazyKvJobCard(job: EazyKvJobRow, t: (String, String) -> String) {
    val statusLine = when {
        job.saving -> job.message?.ifBlank { null } ?: "Saving…"
        else -> job.message ?: job.status
    }
    EazyActiveJobCard(
        title = job.title,
        subtitle = null,
        statusLine = statusLine,
        progress = job.progress,
        kindIcon = Icons.Default.Bolt,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
    )
}
