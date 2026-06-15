package com.eazpire.creator.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

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
    val gameMeta: SimonGameMetaUi? = null,
)

private fun parseGameMeta(obj: JSONObject?): SimonGameMetaUi? {
    if (obj == null) return null
    val colorsArr = obj.optJSONArray("colors")
    val patternsArr = obj.optJSONArray("pattern_ids")
    val freqsArr = obj.optJSONArray("pad_freqs")
    val colors =
        if (colorsArr != null) List(colorsArr.length()) { colorsArr.getString(it) } else emptyList()
    val patterns =
        if (patternsArr != null) List(patternsArr.length()) { patternsArr.getInt(it) } else emptyList()
    val freqs =
        if (freqsArr != null) List(freqsArr.length()) { freqsArr.getDouble(it) } else emptyList()
    return SimonGameMetaUi(
        melodyId = obj.optString("melody_id", ""),
        instrument = obj.optString("instrument", "piano"),
        colors = colors,
        patternIds = patterns,
        padFreqs = freqs,
    )
}

private fun parseHexOrHslColor(raw: String, fallback: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(raw))
    } catch (_: Exception) {
        fallback
    }
}

@Composable
private fun SimonPadPattern(patternId: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
        when (patternId % 9) {
            0 -> {
                var y = 0f
                while (y < size.height) {
                    drawLine(Color.White.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y + size.width * 0.4f), 2f)
                    y += 10f
                }
            }
            1 -> {
                var x = 0f
                while (x < size.width) {
                    drawLine(Color.Black.copy(alpha = 0.2f), Offset(x, 0f), Offset(x, size.height), 2f)
                    x += 8f
                }
            }
            2 -> {
                var y = 4f
                while (y < size.height) {
                    var x = 4f
                    while (x < size.width) {
                        drawCircle(Color.White.copy(alpha = 0.45f), 2f, Offset(x, y))
                        x += 10f
                    }
                    y += 10f
                }
            }
            3 -> {
                var y = 0f
                while (y < size.height) {
                    drawLine(Color.Black.copy(alpha = 0.22f), Offset(0f, y), Offset(size.width, y - size.width * 0.35f), 2f)
                    y += 9f
                }
            }
            4 -> {
                drawLine(Color.White.copy(alpha = 0.35f), Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 3f)
                drawLine(Color.White.copy(alpha = 0.35f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 3f)
            }
            5 -> {
                drawRect(Color.White.copy(alpha = 0.25f), style = stroke)
                drawLine(Color.Black.copy(alpha = 0.15f), Offset(0f, 0f), Offset(size.width, size.height), 2f)
            }
            6 -> drawRect(Color.White.copy(alpha = 0.4f), style = stroke)
            7 -> {
                var y = 0f
                while (y < size.height) {
                    var x = 0f
                    while (x < size.width) {
                        drawRect(Color.Black.copy(alpha = 0.12f), topLeft = Offset(x, y), size = androidx.compose.ui.geometry.Size(4f, 4f))
                        x += 8f
                    }
                    y += 8f
                }
            }
            else -> {
                var x = 0f
                while (x < size.width) {
                    drawLine(Color.White.copy(alpha = 0.28f), Offset(x, 0f), Offset(x + size.height * 0.5f, size.height), 2f)
                    x += 10f
                }
            }
        }
    }
}

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
    val synth = remember(session.gameMeta) { EazySimonSynth.fromGameMeta(session.gameMeta) }
    DisposableEffect(Unit) {
        onDispose { }
    }

    var timing by remember(session.timing) { mutableStateOf(session.timing) }
    val skewMs = timing.serverNowMs - System.currentTimeMillis()
    var deadline by remember { mutableLongStateOf(timing.deadlineMs + skewMs) }

    var round by remember(session.round) { mutableIntStateOf(session.round) }
    var targetRounds by remember(session.targetRounds) { mutableIntStateOf(session.targetRounds) }
    var playbackSteps by remember(session.playbackSteps) { mutableStateOf(session.playbackSteps) }
    var phase by remember(session.phase) { mutableStateOf(session.phase) }
    var gameMeta by remember(session.gameMeta) { mutableStateOf(session.gameMeta) }
    var litPad by remember { mutableIntStateOf(-1) }
    var lock by remember { mutableStateOf(true) }
    var submitted by remember { mutableStateOf(false) }
    var tapInFlight by remember { mutableStateOf(false) }
    var statusLine by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }

    fun applyTiming(t: SimonTimingUi) {
        timing = t
        deadline = t.deadlineMs + (t.serverNowMs - System.currentTimeMillis())
    }

    fun finishRound(forfeit: Boolean) {
        if (submitted || tapInFlight) return
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
            synth.playPad(idx)
            delay(timing.flashMs)
            litPad = -1
            delay(280)
        }
        val start = api.postDailyGameSimonStartInput(shop, ownerId)
        if (start.optBoolean("ok", false)) {
            round = start.optInt("simon_round", round)
            targetRounds = start.optInt("simon_target_rounds", targetRounds)
            playbackSteps = parseIntList(start.optJSONArray("simon_playback_steps"))
            phase = start.optString("simon_phase", "input")
            parseGameMeta(start.optJSONObject("simon_game"))?.let { gameMeta = it }
            start.optJSONObject("simon_timing")?.let { j ->
                applyTiming(
                    SimonTimingUi(
                        deadlineMs = j.optLong("deadline_ms"),
                        serverNowMs = j.optLong("server_now_ms"),
                        playMsPerRound = j.optLong("play_ms_per_round", timing.playMsPerRound),
                        flashMs = j.optLong("flash_ms", timing.flashMs),
                    ),
                )
            }
            lock = false
            statusLine = t("eazy_chat.games_simon_your_turn", "Your turn — repeat the sequence!")
        }
    }

    LaunchedEffect(session.playbackSteps, session.phase) {
        if (session.phase == "playback" && session.playbackSteps.isNotEmpty()) {
            runPlayback(session.playbackSteps)
        } else if (session.phase == "input") {
            applyTiming(session.timing)
            lock = false
            statusLine = t("eazy_chat.games_simon_your_turn", "Your turn — repeat the sequence!")
        }
    }

    LaunchedEffect(deadline, submitted, phase, tapInFlight) {
        while (!submitted && !tapInFlight && phase == "input" && deadline > 0L && System.currentTimeMillis() < deadline) {
            delay(400)
            tick++
        }
        if (!submitted && !tapInFlight && phase == "input" && deadline > 0L && System.currentTimeMillis() >= deadline) {
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
        if (statusLine.isNotBlank()) {
            Text(
                text = statusLine,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = LocalEazyModalPalette.current.accent,
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items((0 until 9).toList()) { idx ->
                val lit = litPad == idx
                val baseColor =
                    gameMeta?.colors?.getOrNull(idx)?.let { parseHexOrHslColor(it, Color.Gray) }
                        ?: Color(0xFFEA580C)
                Box(
                    modifier =
                        Modifier
                            .aspectRatio(1f)
                            .alpha(if (lit) 1f else 0.78f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(baseColor)
                            .clickable(enabled = !lock && !submitted && !tapInFlight && phase == "input") {
                                tapInFlight = true
                                litPad = idx
                                synth.playPad(idx)
                                scope.launch {
                                    delay(timing.flashMs)
                                    litPad = -1
                                    try {
                                        val j = api.postDailyGameSimonTap(shop, ownerId, idx)
                                        parseGameMeta(j.optJSONObject("simon_game"))?.let { gameMeta = it }
                                        j.optJSONObject("simon_timing")?.let { tj ->
                                            applyTiming(
                                                SimonTimingUi(
                                                    deadlineMs = tj.optLong("deadline_ms"),
                                                    serverNowMs = tj.optLong("server_now_ms"),
                                                    playMsPerRound = tj.optLong("play_ms_per_round", timing.playMsPerRound),
                                                    flashMs = tj.optLong("flash_ms", timing.flashMs),
                                                ),
                                            )
                                        }
                                        when {
                                            j.optString("outcome") == "win" -> {
                                                submitted = true
                                                synth.playWin()
                                                onRoundComplete()
                                            }
                                            j.optString("outcome") == "loss" -> {
                                                submitted = true
                                                synth.playWrong()
                                                onRoundComplete()
                                            }
                                            j.optString("simon_status") == "round_complete" -> {
                                                round = j.optInt("simon_round", round + 1)
                                                playbackSteps = parseIntList(j.optJSONArray("simon_playback_steps"))
                                                tapInFlight = false
                                                runPlayback(playbackSteps)
                                            }
                                            j.optBoolean("ok", false) -> {
                                                tapInFlight = false
                                                lock = false
                                            }
                                            else -> {
                                                tapInFlight = false
                                                lock = false
                                            }
                                        }
                                    } catch (_: Exception) {
                                        tapInFlight = false
                                        lock = false
                                    }
                                }
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    SimonPadPattern(
                        patternId = gameMeta?.patternIds?.getOrNull(idx) ?: idx,
                        modifier = Modifier.matchParentSize().padding(8.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (phase == "input" && deadline > 0L) {
                val secLeft = ((deadline - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L).toInt()
                Text(
                    text = "${t("eazy_chat.games_simon_timer", "Time left")}: ${secLeft}s",
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (secLeft <= 5) LocalEazyModalPalette.current.accent
                        else LocalEazyModalPalette.current.muted,
                    fontWeight = if (secLeft <= 5) FontWeight.Bold else FontWeight.Normal,
                )
            } else {
                Text(text = "", style = MaterialTheme.typography.bodySmall)
            }
            OutlinedButton(
                onClick = { finishRound(true) },
                enabled = !submitted && !tapInFlight,
            ) {
                Text(t("eazy_chat.games_simon_forfeit", "Give up"))
            }
        }
    }
}

private fun parseIntList(arr: JSONArray?): List<Int> {
    if (arr == null) return emptyList()
    return List(arr.length()) { arr.getInt(it) }
}

fun parseSimonSession(j: JSONObject): SimonSessionUi? {
    val timing = j.optJSONObject("simon_timing") ?: return null
    val stepsArr = j.optJSONArray("simon_playback_steps") ?: return null
    val steps = List(stepsArr.length()) { stepsArr.getInt(it) }
    return SimonSessionUi(
        timing =
            SimonTimingUi(
                deadlineMs = timing.optLong("deadline_ms"),
                serverNowMs = timing.optLong("server_now_ms"),
                playMsPerRound = timing.optLong("play_ms_per_round", 10_000L),
                flashMs = timing.optLong("flash_ms", 550L),
            ),
        targetRounds = j.optInt("simon_target_rounds", 7),
        round = j.optInt("simon_round", 0),
        playbackSteps = steps,
        phase = j.optString("simon_phase", "playback"),
        gameMeta = parseGameMeta(j.optJSONObject("simon_game")),
    )
}
