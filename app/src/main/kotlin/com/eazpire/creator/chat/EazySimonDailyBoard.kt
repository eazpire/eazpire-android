package com.eazpire.creator.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.async
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
    val imagesArr = obj.optJSONArray("pad_images")
    val freqsArr = obj.optJSONArray("pad_freqs")
    val colors =
        if (colorsArr != null) List(colorsArr.length()) { colorsArr.getString(it) } else emptyList()
    val images =
        if (imagesArr != null) List(imagesArr.length()) { imagesArr.getString(it) } else emptyList()
    val freqs =
        if (freqsArr != null) List(freqsArr.length()) { freqsArr.getDouble(it) } else emptyList()
    return SimonGameMetaUi(
        melodyId = obj.optString("melody_id", ""),
        instrument = obj.optString("instrument", "piano"),
        colors = colors,
        padImages = images,
        padFreqs = freqs,
    )
}

private fun parseHexColor(raw: String, fallback: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(raw))
    } catch (_: Exception) {
        fallback
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
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val synth = remember(session.gameMeta) { EazySimonSynth.fromGameMeta(session.gameMeta) }

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

    fun applyTiming(timingUi: SimonTimingUi) {
        timing = timingUi
        deadline = timingUi.deadlineMs + (timingUi.serverNowMs - System.currentTimeMillis())
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
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tick
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items((0 until 9).toList()) { idx ->
                val lit = litPad == idx
                val baseColor =
                    gameMeta?.colors?.getOrNull(idx)?.let { parseHexColor(it, Color(0xFFEA580C)) }
                        ?: Color(0xFFEA580C)
                val imageUrl = gameMeta?.padImages?.getOrNull(idx).orEmpty()
                val canTap = !lock && !submitted && !tapInFlight && phase == "input"
                Box(
                    modifier =
                        Modifier
                            .aspectRatio(1f)
                            .alpha(if (lit) 1f else 0.78f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(baseColor)
                            .clickable(enabled = canTap) {
                                if (tapInFlight) return@clickable
                                tapInFlight = true
                                litPad = idx
                                synth.playPad(idx)
                                scope.launch {
                                    val flashJob = async {
                                        delay(timing.flashMs)
                                        if (litPad == idx) litPad = -1
                                    }
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
                                    } finally {
                                        flashJob.await()
                                    }
                                }
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    if (imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(10.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
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
}

private fun parseIntList(arr: JSONArray?): List<Int> {
    if (arr == null) return emptyList()
    return List(arr.length()) { arr.getInt(it) }
}

fun parseSimonSession(j: JSONObject): SimonSessionUi? {
    val timing = j.optJSONObject("simon_timing") ?: return null
    val stepsArr = j.optJSONArray("simon_playback_steps") ?: return null
    if (stepsArr.length() < 1) return null
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
