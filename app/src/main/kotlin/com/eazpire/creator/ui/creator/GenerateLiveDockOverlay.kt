package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import kotlin.math.roundToInt

@Composable
fun GenerateLiveDockOverlay(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier,
) {
    val jobs by GenerateLiveDockStore.jobs.collectAsState()
    val minimized by GenerateLiveDockStore.minimized.collectAsState()
    if (jobs.isEmpty()) return

    var fabOffset by remember { mutableStateOf(Offset.Zero) }
    var dragged by remember { mutableStateOf(false) }
    val running = jobs.count { it.status == "generating" || it.status == "partial" }
    val ready = jobs.count { it.status == "ready" }
    val fabColor = when {
        running > 0 -> Color(0xFFF97316)
        ready > 0 -> Color(0xFF16A34A)
        else -> Color(0xFF111827)
    }

    Box(modifier = modifier.fillMaxSize().zIndex(40f)) {
        if (!minimized) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 160.dp)
                    .widthIn(max = 340.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF111827))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (running > 0) {
                            translationStore.t("creator.generator.live_gen_generating", "Generating…")
                        } else {
                            translationStore.t("creator.generator.live_gen_ready", "Generation complete")
                        },
                        color = Color.White,
                        fontSize = 14.sp
                    )
                    TextButton(onClick = { GenerateLiveDockStore.minimize() }) {
                        Text("─", color = Color.White)
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    jobs.forEach { job ->
                        Column(
                            modifier = Modifier
                                .widthIn(min = 220.dp, max = 280.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1F2937))
                        ) {
                            if (job.previewUrl.isNotBlank()) {
                                AsyncImage(
                                    model = job.previewUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(220.dp)
                                        .background(Color(0xFF111827)),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .background(Color(0xFF374151)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("…", color = Color.White)
                                }
                            }
                            Column(Modifier.padding(10.dp)) {
                                Text(
                                    when (job.status) {
                                        "ready" -> translationStore.t("creator.generator.live_gen_ready_short", "Done")
                                        "error" -> translationStore.t("creator.generator.live_gen_error", "Error")
                                        else -> translationStore.t("creator.generator.live_gen_generating_short", "Live")
                                    },
                                    color = EazColors.Orange,
                                    fontSize = 11.sp
                                )
                                Text(
                                    job.prompt.ifBlank {
                                        translationStore.t("creator.generator.history_empty_prompt", "No prompt")
                                    }.take(72),
                                    color = Color.White,
                                    fontSize = 13.sp
                                )
                                if (job.error.isNotBlank()) {
                                    Text(job.error, color = Color(0xFFFCA5A5), fontSize = 12.sp)
                                }
                                if (job.status == "ready") {
                                    Spacer(Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { GenerateLiveDockStore.save(tokenStore, job.jobId) },
                                            enabled = !job.busy,
                                            colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange)
                                        ) {
                                            Text(translationStore.t("creator.generator.live_gen_save", "Save"))
                                        }
                                        TextButton(
                                            onClick = { GenerateLiveDockStore.discard(tokenStore, job.jobId) },
                                            enabled = !job.busy
                                        ) {
                                            Text(
                                                translationStore.t("creator.generator.live_gen_discard", "Discard"),
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 88.dp)
                .offset { IntOffset(fabOffset.x.roundToInt(), fabOffset.y.roundToInt()) }
                .size(52.dp)
                .clip(CircleShape)
                .background(fabColor)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragged = false },
                        onDrag = { change, drag ->
                            change.consume()
                            if (kotlin.math.abs(drag.x) + kotlin.math.abs(drag.y) > 2f || dragged) {
                                dragged = true
                                fabOffset += drag
                            }
                        }
                    )
                }
                .clickable {
                    if (dragged) {
                        dragged = false
                    } else if (minimized) {
                        GenerateLiveDockStore.expand()
                    } else {
                        GenerateLiveDockStore.minimize()
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (running > 0) "…" else if (ready > 0) "✓" else "!",
                color = Color.White,
                fontSize = 18.sp
            )
            if (jobs.size > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(jobs.size.toString(), color = Color.Black, fontSize = 10.sp)
                }
            }
        }
    }
}
