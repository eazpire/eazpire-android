package com.eazpire.creator.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    val gapMs: Long = 240L,
    val introMs: Long = 1400L,
    val roundBreakMs: Long = 1200L,
    val preloadMinMs: Long = 900L,
)

data class SimonSessionUi(
    val timing: SimonTimingUi,
    val targetRounds: Int,
    val round: Int,
    val playbackSteps: List<Int>,
    val phase: String,
    val gameMeta: SimonGameMetaUi? = null,
    val lootBoostPct: Int = 0,
    val canContinue: Boolean = true,
)

private fun parseGameMeta(obj: JSONObject?): SimonGameMetaUi? {
    if (obj == null) return null
    val colorsArr = obj.optJSONArray("colors")
    val imagesArr = obj.optJSONArray("pad_images")
    val freqsArr = obj.optJSONArray("pad_freqs")
    val colors =
        if (colorsArr != null) {
            buildList {
                for (i in 0 until colorsArr.length()) {
                    colorsArr.optString(i, "").takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        } else {
            emptyList()
        }
    val images =
        if (imagesArr != null) {
            buildList {
                for (i in 0 until imagesArr.length()) {
                    imagesArr.optString(i, "").takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        } else {
            emptyList()
        }
    val freqs =
        if (freqsArr != null) {
            buildList {
                for (i in 0 until freqsArr.length()) {
                    val v = freqsArr.optDouble(i, Double.NaN)
                    if (!v.isNaN()) add(v)
                }
            }
        } else {
            emptyList()
        }
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
    var pressedPad by remember { mutableIntStateOf(-1) }
    var boardReady by remember { mutableStateOf(session.phase == "input") }
    var lock by remember { mutableStateOf(true) }
    var submitted by remember { mutableStateOf(false) }
    var tapProcessing by remember { mutableStateOf(false) }
    val tapQueue = remember { mutableStateListOf<Int>() }
    var statusLine by remember { mutableStateOf("") }
    var tick by remember { mutableIntStateOf(0) }
    var atOffer by remember { mutableStateOf(session.phase == "offer") }
    var lootBoostPct by remember(session.lootBoostPct) { mutableIntStateOf(session.lootBoostPct) }
    var canContinue by remember(session.canContinue) { mutableStateOf(session.canContinue) }

    fun applyTiming(timingUi: SimonTimingUi) {
        timing = timingUi
        deadline = timingUi.deadlineMs + (timingUi.serverNowMs - System.currentTimeMillis())
    }

    fun finishRound(forfeit: Boolean) {
        if (submitted || atOffer || tapProcessing) return
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

    fun showOffer(nextBoost: Int) {
        atOffer = true
        lock = true
        phase = "offer"
        statusLine =
            t(
                "eazy_chat.games_simon_win_offer",
                "You won! Keep playing for +{{pct}}% better loot — or take your prize now.",
            ).replace("{{pct}}", nextBoost.toString())
    }

    suspend fun flashPad(idx: Int, userPress: Boolean = false) {
        if (userPress) pressedPad = idx else litPad = idx
        synth.playPad(idx)
        delay(timing.flashMs)
        if (userPress) {
            if (pressedPad == idx) pressedPad = -1
        } else if (litPad == idx) {
            litPad = -1
        }
    }

    suspend fun runPlayback(steps: List<Int>) {
        lock = true
        phase = "playback"
        boardReady = true
        statusLine = t("eazy_chat.games_simon_watch", "Watch Eazy's sequence…")
        delay(timing.gapMs)
        for (idx in steps) {
            if (submitted) return
            flashPad(idx, userPress = false)
            delay(timing.gapMs)
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
                        gapMs = j.optLong("gap_ms", timing.gapMs),
                        introMs = j.optLong("intro_ms", timing.introMs),
                        roundBreakMs = j.optLong("round_break_ms", timing.roundBreakMs),
                        preloadMinMs = j.optLong("preload_min_ms", timing.preloadMinMs),
                    ),
                )
            }
            lock = false
            statusLine = t("eazy_chat.games_simon_your_turn", "Your turn — repeat the sequence!")
        }
    }

    suspend fun prepareBoardThenPlayback(steps: List<Int>) {
        boardReady = false
        lock = true
        statusLine = t("eazy_chat.games_simon_get_ready", "Get ready…")
        delay(timing.preloadMinMs)
        if (submitted) return
        boardReady = true
        delay(timing.introMs)
        if (submitted) return
        runPlayback(steps)
    }

    suspend fun processTapQueue() {
        if (tapProcessing || tapQueue.isEmpty() || submitted || atOffer) return
        tapProcessing = true
        val idx = tapQueue.removeAt(0)
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
                        gapMs = tj.optLong("gap_ms", timing.gapMs),
                        introMs = tj.optLong("intro_ms", timing.introMs),
                        roundBreakMs = tj.optLong("round_break_ms", timing.roundBreakMs),
                        preloadMinMs = tj.optLong("preload_min_ms", timing.preloadMinMs),
                    ),
                )
            }
            lootBoostPct = j.optInt("simon_loot_boost_pct", lootBoostPct)
            canContinue = j.optBoolean("simon_can_continue", canContinue)
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
                j.optString("simon_status") == "offer_continue" -> {
                    targetRounds = j.optInt("simon_target_rounds", targetRounds)
                    round = j.optInt("simon_round", round)
                    showOffer(if (canContinue) lootBoostPct + 5 else lootBoostPct)
                }
                j.optString("simon_status") == "round_complete" -> {
                    round = j.optInt("simon_round", round + 1)
                    playbackSteps = parseIntList(j.optJSONArray("simon_playback_steps"))
                    statusLine = t("eazy_chat.games_simon_round_done", "Nice! Next round…")
                    delay(timing.roundBreakMs)
                    runPlayback(playbackSteps)
                }
                j.optBoolean("ok", false) -> lock = false
                else -> lock = false
            }
        } catch (_: Exception) {
            lock = false
        } finally {
            tapProcessing = false
            if (!submitted && !atOffer && phase == "input") lock = false
            processTapQueue()
        }
    }

    LaunchedEffect(session.playbackSteps, session.phase) {
        if (session.phase == "playback" && session.playbackSteps.isNotEmpty()) {
            prepareBoardThenPlayback(session.playbackSteps)
        } else if (session.phase == "input") {
            applyTiming(session.timing)
            boardReady = true
            lock = false
            statusLine = t("eazy_chat.games_simon_your_turn", "Your turn — repeat the sequence!")
        }
    }

    LaunchedEffect(deadline, submitted, phase, tapProcessing, atOffer) {
        while (!submitted && !tapProcessing && !atOffer && phase == "input" && deadline > 0L && System.currentTimeMillis() < deadline) {
            delay(200)
            tick++
        }
        if (!submitted && !tapProcessing && !atOffer && phase == "input" && deadline > 0L && System.currentTimeMillis() >= deadline && tapQueue.isEmpty()) {
            finishRound(true)
        }
    }

    LaunchedEffect(session.phase) {
        if (session.phase == "offer") {
            atOffer = true
            showOffer(if (session.canContinue) session.lootBoostPct + 5 else session.lootBoostPct)
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
                val pressed = pressedPad == idx
                val baseColor =
                    gameMeta?.colors?.getOrNull(idx)?.let { parseHexColor(it, Color(0xFFEA580C)) }
                        ?: Color(0xFFEA580C)
                val imageUrl = gameMeta?.padImages?.getOrNull(idx).orEmpty()
                val canTap = boardReady && !submitted && phase == "input" && !atOffer
                EazySimonPad(
                    baseColor = baseColor,
                    imageUrl = imageUrl.takeIf { it.isNotBlank() },
                    lit = lit,
                    pressed = pressed,
                    enabled = canTap,
                    boardReady = boardReady,
                    onClick = {
                        if (!canTap || submitted || atOffer) return@EazySimonPad
                        scope.launch {
                            async { flashPad(idx, userPress = true) }
                            tapQueue.add(idx)
                            processTapQueue()
                        }
                    },
                    modifier = Modifier.aspectRatio(1f),
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (!atOffer) {
                Text(
                    text =
                        t("eazy_chat.games_simon_round_label", "Round {{r}} of {{t}}")
                            .replace("{{r}}", (round + 1).toString())
                            .replace("{{t}}", targetRounds.toString()),
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
            if (atOffer && !submitted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            submitted = true
                            scope.launch {
                                try {
                                    val j = api.postDailyGameSimonClaimWin(shop, ownerId)
                                    if (j.optString("outcome") == "win") synth.playWin()
                                } catch (_: Exception) {
                                } finally {
                                    onRoundComplete()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    ) {
                        Text(t("eazy_chat.games_simon_take_win", "Take prize"))
                    }
                    Button(
                        onClick = {
                            atOffer = false
                            lock = true
                            scope.launch {
                                try {
                                    val j = api.postDailyGameSimonContinueGamble(shop, ownerId)
                                    round = j.optInt("simon_round", 0)
                                    targetRounds = j.optInt("simon_target_rounds", targetRounds)
                                    playbackSteps = parseIntList(j.optJSONArray("simon_playback_steps"))
                                    phase = "playback"
                                    statusLine =
                                        t("eazy_chat.games_simon_extension_start", "Bonus round — watch closely!")
                                    delay(minOf(timing.roundBreakMs, 700L))
                                    runPlayback(playbackSteps)
                                } catch (_: Exception) {
                                    atOffer = true
                                }
                            }
                        },
                        enabled = canContinue,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEA580C)),
                    ) {
                        Text(t("eazy_chat.games_simon_continue", "Keep playing"))
                    }
                }
            } else {
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
                        enabled = !submitted && !tapProcessing && !atOffer,
                    ) {
                        Text(t("eazy_chat.games_simon_forfeit", "Give up"))
                    }
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
    val steps = buildList {
        for (i in 0 until stepsArr.length()) {
            val step = stepsArr.optInt(i, -1)
            if (step >= 0) add(step)
        }
    }
    if (steps.isEmpty()) return null
    return SimonSessionUi(
        timing =
            SimonTimingUi(
                deadlineMs = timing.optLong("deadline_ms"),
                serverNowMs = timing.optLong("server_now_ms"),
                playMsPerRound = timing.optLong("play_ms_per_round", 10_000L),
                flashMs = timing.optLong("flash_ms", 310L),
                gapMs = timing.optLong("gap_ms", 240L),
                introMs = timing.optLong("intro_ms", 1400L),
                roundBreakMs = timing.optLong("round_break_ms", 1200L),
                preloadMinMs = timing.optLong("preload_min_ms", 900L),
            ),
        targetRounds = j.optInt("simon_target_rounds", 7),
        round = j.optInt("simon_round", 0),
        playbackSteps = steps,
        phase = j.optString("simon_phase", "playback"),
        gameMeta = parseGameMeta(j.optJSONObject("simon_game")),
        lootBoostPct = j.optInt("simon_loot_boost_pct", 0),
        canContinue = j.optBoolean("simon_can_continue", true),
    )
}
