package com.eazpire.creator.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray

data class NumberRushTimingUi(
    val deadlineMs: Long,
    val serverNowMs: Long,
    val playMs: Long,
    val cols: Int,
)

data class NumberRushSessionUi(
    val grid: List<Int>,
    val timing: NumberRushTimingUi,
    val nextExpected: Int,
    val tapped: Set<Int>,
)

fun parseNumberRushGrid(arr: JSONArray): List<Int> =
    List(arr.length()) { arr.getInt(it) }

@Composable
fun EazyNumberRushDailyBoard(
    api: CreatorApi,
    shop: String,
    ownerId: String,
    session: NumberRushSessionUi,
    onRoundComplete: () -> Unit,
    t: (String, String) -> String,
) {
    val scope = rememberCoroutineScope()
    val skewMs = remember(session.timing.serverNowMs) { session.timing.serverNowMs - System.currentTimeMillis() }
    val deadline = session.timing.deadlineMs + skewMs
    val cols = session.timing.cols.coerceAtLeast(3)

    var nextExpected by remember(session.nextExpected) { mutableIntStateOf(session.nextExpected.coerceAtLeast(1)) }
    var tapped by remember(session.tapped) { mutableStateOf(session.tapped) }
    var lock by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }

    fun finishRound(forfeit: Boolean) {
        if (submitted) return
        submitted = true
        scope.launch {
            try {
                if (forfeit) {
                    api.postDailyGameNumberRushForfeit(shop, ownerId)
                }
            } catch (_: Exception) {
            } finally {
                onRoundComplete()
            }
        }
    }

    LaunchedEffect(deadline, submitted) {
        while (!submitted && System.currentTimeMillis() < deadline) {
            delay(400)
            tick++
        }
        if (!submitted && System.currentTimeMillis() >= deadline) {
            finishRound(true)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        tick
        val secLeft = ((deadline - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L).toInt()
        Text(
            text = "${t("eazy_chat.games_nr_timer", "Time left")}: ${secLeft}s",
            style = MaterialTheme.typography.bodySmall,
            color = LocalEazyModalPalette.current.muted,
        )
        Text(
            text = t("eazy_chat.games_nr_next_label", "Next: {{n}}").replace("{{n}}", nextExpected.toString()),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = LocalEazyModalPalette.current.accent,
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(session.grid) { index, value ->
                val done = tapped.contains(index)
                val isNext = value == nextExpected && !done
                Box(
                    modifier =
                        Modifier
                            .aspectRatio(1f)
                            .alpha(if (done) 0.35f else 1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    done -> Color(0xFF22C55E).copy(alpha = 0.15f)
                                    isNext -> Color(0xFFF97316).copy(alpha = 0.18f)
                                    else -> Color(0xFF0F172A).copy(alpha = 0.55f)
                                },
                            )
                            .clickable(enabled = !lock && !submitted && !done) {
                                lock = true
                                scope.launch {
                                    try {
                                        val j = api.postDailyGameNumberRushTap(shop, ownerId, index)
                                        when {
                                            j.optString("outcome") == "win" -> {
                                                tapped = tapped + index
                                                submitted = true
                                                onRoundComplete()
                                            }
                                            j.optString("outcome") == "loss" -> {
                                                submitted = true
                                                onRoundComplete()
                                            }
                                            j.optBoolean("ok", false) -> {
                                                tapped = tapped + index
                                                nextExpected = j.optInt("number_rush_next", nextExpected + 1)
                                                lock = false
                                            }
                                            else -> lock = false
                                        }
                                    } catch (_: Exception) {
                                        lock = false
                                    }
                                }
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = value.toString(),
                        fontWeight = FontWeight.Bold,
                        color =
                            if (isNext) LocalEazyModalPalette.current.accent
                            else LocalEazyModalPalette.current.text,
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { finishRound(true) },
            enabled = !submitted,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(t("eazy_chat.games_nr_forfeit", "Give up"))
        }
    }
}
