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
import androidx.compose.foundation.lazy.grid.items
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

data class SimonTimingUi(
    val deadlineMs: Long,
    val serverNowMs: Long,
    val playMsPerRound: Long,
    val flashMs: Long,
)

data class SimonSessionUi(
    val timing: SimonTimingUi,
    val targetRounds: Int,
    val round: Int,
    val playbackSteps: List<Int>,
    val phase: String,
)

private val SIMON_COLORS =
    listOf(
        Color(0xFFEA580C),
        Color(0xFF0284C7),
        Color(0xFF16A34A),
        Color(0xFF7C3AED),
    )

@Composable
fun EazySimonDailyBoard(
    api: CreatorApi,
    shop: String,
    ownerId: String,
    session: SimonSessionUi,
    onRoundComplete: () -> Unit,
    t: (String, String) -> String,
) {
    val scope = rememberCoroutineScope()
    val skewMs = remember(session.timing.serverNowMs) { session.timing.serverNowMs - System.currentTimeMillis() }
    val deadline = session.timing.deadlineMs + skewMs

    var round by remember(session.round) { mutableIntStateOf(session.round) }
    var targetRounds by remember(session.targetRounds) { mutableIntStateOf(session.targetRounds) }
    var playbackSteps by remember(session.playbackSteps) { mutableStateOf(session.playbackSteps) }
    var phase by remember(session.phase) { mutableStateOf(session.phase) }
    var litPad by remember { mutableIntStateOf(-1) }
    var lock by remember { mutableStateOf(true) }
    var submitted by remember { mutableStateOf(false) }
    var statusLine by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }

    fun finishRound(forfeit: Boolean) {
        if (submitted) return
        submitted = true
        scope.launch {
            try {
                if (forfeit) api.postDailyGameSimonForfeit(shop, ownerId)
            } catch (_: Exception) {
            } finally {
                onRoundComplete()
            }
        }
    }

  suspend fun runPlayback(steps: List<Int>) {
        lock = true
        phase = "playback"
        statusLine = t("eazy_chat.games_simon_watch", "Watch Eazy's sequence…")
        for (idx in steps) {
            if (submitted) return
            litPad = idx
            delay(session.timing.flashMs)
            litPad = -1
            delay(280)
        }
        val start = api.postDailyGameSimonStartInput(shop, ownerId)
        if (start.optBoolean("ok", false)) {
            round = start.optInt("simon_round", round)
            targetRounds = start.optInt("simon_target_rounds", targetRounds)
            playbackSteps = parseIntList(start.optJSONArray("simon_playback_steps"))
            phase = start.optString("simon_phase", "input")
            lock = false
            statusLine = t("eazy_chat.games_simon_your_turn", "Your turn — repeat the sequence!")
        }
    }

    LaunchedEffect(session.playbackSteps, session.phase) {
        if (session.phase == "playback" && session.playbackSteps.isNotEmpty()) {
            runPlayback(session.playbackSteps)
        } else if (session.phase == "input") {
            lock = false
            statusLine = t("eazy_chat.games_simon_your_turn", "Your turn — repeat the sequence!")
        }
    }

    LaunchedEffect(deadline, submitted, phase) {
        while (!submitted && phase == "input" && deadline > 0L && System.currentTimeMillis() < deadline) {
            delay(400)
            tick++
        }
        if (!submitted && phase == "input" && deadline > 0L && System.currentTimeMillis() >= deadline) {
            finishRound(true)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        tick
        Text(
            text =
                t("eazy_chat.games_simon_round_label", "Round {{r}} of {{t}}")
                    .replace("{{r}}", (round + 1).toString())
                    .replace("{{t}}", targetRounds.toString()),
            style = MaterialTheme.typography.bodySmall,
            color = LocalEazyModalPalette.current.muted,
        )
        if (phase == "input" && deadline > 0L) {
            val secLeft = ((deadline - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L).toInt()
            Text(
                text = "${t("eazy_chat.games_simon_timer", "Time left")}: ${secLeft}s",
                style = MaterialTheme.typography.bodySmall,
                color = LocalEazyModalPalette.current.muted,
            )
        }
        if (statusLine.isNotBlank()) {
            Text(
                text = statusLine,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = LocalEazyModalPalette.current.accent,
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(SIMON_COLORS.indices.toList()) { idx ->
                val lit = litPad == idx
                Box(
                    modifier =
                        Modifier
                            .aspectRatio(1f)
                            .alpha(if (lit) 1f else 0.72f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(SIMON_COLORS[idx])
                            .clickable(enabled = !lock && !submitted && phase == "input") {
                                lock = true
                                litPad = idx
                                scope.launch {
                                    delay(session.timing.flashMs)
                                    litPad = -1
                                    try {
                                        val j = api.postDailyGameSimonTap(shop, ownerId, idx)
                                        when {
                                            j.optString("outcome") == "win" ||
                                                j.optString("outcome") == "loss" -> {
                                                submitted = true
                                                onRoundComplete()
                                            }
                                            j.optString("simon_status") == "round_complete" -> {
                                                round = j.optInt("simon_round", round + 1)
                                                playbackSteps = parseIntList(j.optJSONArray("simon_playback_steps"))
                                                runPlayback(playbackSteps)
                                            }
                                            j.optBoolean("ok", false) -> lock = false
                                            else -> lock = false
                                        }
                                    } catch (_: Exception) {
                                        lock = false
                                    }
                                }
                            },
                    contentAlignment = Alignment.Center,
                ) {}
            }
        }

        OutlinedButton(
            onClick = { finishRound(true) },
            enabled = !submitted,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(t("eazy_chat.games_simon_forfeit", "Give up"))
        }
    }
}

private fun parseIntList(arr: JSONArray?): List<Int> {
    if (arr == null) return emptyList()
    return List(arr.length()) { arr.getInt(it) }
}
