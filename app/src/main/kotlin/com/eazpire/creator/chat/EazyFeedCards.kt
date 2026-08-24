package com.eazpire.creator.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

data class EazyFeedChip(
    val label: String,
    val info: String,
    val state: String = "chip",
)

fun formatEazyClock(ts: Long): String {
    if (ts <= 0L) return ""
    return try {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ts))
    } catch (_: Exception) {
        ""
    }
}

fun formatEazyElapsed(ts: Long): String {
    if (ts <= 0L) return ""
    val elapsed = (System.currentTimeMillis() - ts).coerceAtLeast(0L)
    val mins = elapsed / 60_000L
    val hours = mins / 60L
    return when {
        hours >= 1 -> "${hours}h ${mins % 60}m"
        mins >= 1 -> "${mins}m"
        else -> "${(elapsed / 1000L).coerceAtLeast(1L)}s"
    }
}

fun eazyPhaseLabel(id: String, t: (String, String) -> String): String = when (id) {
    "queued" -> t("eazy_chat.chat_job_phase_queued", "Queued")
    "printify" -> t("eazy_chat.chat_job_phase_printify", "Printify")
    "shopify" -> t("eazy_chat.chat_job_phase_shopify", "Shop")
    "live" -> t("eazy_chat.chat_job_phase_live", "Live")
    "amazon" -> t("eazy_chat.chat_job_phase_amazon", "Amazon")
    "removed" -> t("eazy_chat.chat_job_phase_removed", "Removed")
    "create" -> t("eazy_chat.chat_job_phase_create", "Create")
    "online" -> t("eazy_chat.chat_job_phase_online", "Online")
    "generate" -> t("eazy_chat.chat_job_phase_generate", "Generate")
    "save" -> t("eazy_chat.chat_job_phase_save", "Save")
    else -> id
}

fun eazyPhaseInfo(id: String, t: (String, String) -> String): String = when (id) {
    "queued" -> t("eazy_chat.chat_stat_info_phase_queued", "Waiting to start.")
    "printify" -> t("eazy_chat.chat_stat_info_phase_printify", "The print partner is preparing the listing.")
    "shopify" -> t("eazy_chat.chat_stat_info_phase_shopify", "The shop listing is being created.")
    "live" -> t("eazy_chat.chat_stat_info_phase_live", "The product is live in the shop.")
    "amazon" -> t("eazy_chat.chat_stat_info_phase_amazon", "Amazon is processing this listing.")
    "removed" -> t("eazy_chat.chat_stat_info_phase_removed", "The listing is being taken down.")
    "create" -> t("eazy_chat.chat_stat_info_phase_create", "The image is being created.")
    "online" -> t("eazy_chat.chat_stat_info_phase_online", "The result is online.")
    "generate" -> t("eazy_chat.chat_stat_info_phase_generate", "The design is being generated.")
    "save" -> t("eazy_chat.chat_stat_info_phase_save", "The design is being saved.")
    else -> eazyPhaseLabel(id, t)
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalTextApi::class)
@Composable
fun EazyFeedCard(
    designTitle: String,
    productTitle: String? = null,
    thumbUrl: String? = null,
    chips: List<EazyFeedChip> = emptyList(),
    progress: Int? = null,
    errorText: String? = null,
    modifier: Modifier = Modifier,
    onCardClick: (() -> Unit)? = null,
) {
    val palette = LocalEazyModalPalette.current
    var info by remember { mutableStateOf<String?>(null) }
    var infoGen by remember { mutableIntStateOf(0) }
    val textMeasurer = rememberTextMeasurer()
    LaunchedEffect(infoGen) {
        val snapshot = infoGen
        val text = info ?: return@LaunchedEffect
        delay(2600)
        if (infoGen == snapshot && info == text) info = null
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.muted.copy(alpha = 0.08f))
            .border(1.dp, palette.muted.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
            .then(if (onCardClick != null) Modifier.clickable { onCardClick() } else Modifier)
            .heightIn(min = 88.dp)
            .drawWithContent {
                drawContent()
                val overlay = info
                if (overlay.isNullOrBlank()) return@drawWithContent
                drawRect(palette.bg.copy(alpha = 0.88f))
                val maxW = (size.width - 32.dp.toPx()).toInt().coerceAtLeast(40)
                val layout = textMeasurer.measure(
                    overlay,
                    style = TextStyle(
                        color = palette.text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    ),
                    constraints = Constraints(maxWidth = maxW),
                )
                drawText(
                    layout,
                    topLeft = Offset(
                        (size.width - layout.size.width) / 2f,
                        (size.height - layout.size.height) / 2f,
                    ),
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (!thumbUrl.isNullOrBlank()) {
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .width(88.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    designTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.text,
                    fontWeight = FontWeight.Normal,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                productTitle?.takeIf { it.isNotBlank() && !it.equals(designTitle, ignoreCase = true) }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (chips.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        chips.forEach { chip ->
                            val alpha = when (chip.state) {
                                "done" -> 0.55f
                                "todo" -> 0.38f
                                else -> 1f
                            }
                            val borderColor = if (chip.state == "current") palette.accent.copy(alpha = 0.55f)
                            else palette.muted.copy(alpha = 0.22f)
                            val textColor = if (chip.state == "current") palette.accent else palette.muted
                            Text(
                                chip.label,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .border(1.dp, borderColor, RoundedCornerShape(999.dp))
                                    .clickable {
                                        info = chip.info
                                        infoGen += 1
                                    }
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                                color = textColor.copy(alpha = alpha),
                                fontSize = 10.sp,
                                fontWeight = if (chip.state == "current") FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                progress?.let { pct ->
                    LinearProgressIndicator(
                        progress = pct.coerceIn(0, 100) / 100f,
                        modifier = Modifier.fillMaxWidth(),
                        color = palette.accent,
                        trackColor = palette.muted.copy(alpha = 0.2f),
                        strokeCap = StrokeCap.Round,
                    )
                }
                errorText?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = palette.muted, maxLines = 3)
                }
            }
        }
    }
}
