package com.eazpire.creator.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class MemoryDeckUi(
    val slotPairKeys: List<Int>,
    val images: List<String>,
)

data class MemoryTimingUi(
    val deadlineMs: Long,
    val previewGraceMs: Long,
    val matchFlipMs: Long,
    val serverNowMs: Long,
    val maxWrongMoves: Int = 5,
    val memoryWrongMoves: Int = 0,
)

@Composable
fun EazyMemoryDailyBoard(
    deck: MemoryDeckUi,
    timing: MemoryTimingUi,
    api: CreatorApi,
    shop: String,
    ownerId: String,
    onFinishRequest: (forfeit: Boolean, flipLog: List<Int>) -> Unit,
    onServerOutcome: (JSONObject) -> Unit,
    t: (String, String) -> String,
) {
    val scope = rememberCoroutineScope()
    val n = deck.slotPairKeys.size
    val skewMs = remember(timing.serverNowMs) { timing.serverNowMs - System.currentTimeMillis() }
    val deadline = timing.deadlineMs + skewMs
    val maxWrongMoves = timing.maxWrongMoves.coerceIn(1, 20)

    var preview by remember { mutableStateOf(timing.previewGraceMs > 0) }
    var tick by remember { mutableIntStateOf(0) }
    var submitted by remember { mutableStateOf(false) }
    var wrongMoves by remember {
        mutableIntStateOf(timing.memoryWrongMoves.coerceIn(0, maxWrongMoves))
    }

    fun submit(forfeit: Boolean, log: List<Int>) {
        if (submitted) return
        submitted = true
        onFinishRequest(forfeit, log)
    }

    fun handleServerJson(j: JSONObject) {
        if (submitted) return
        val outcome = j.optString("outcome", "")
        if (outcome == "loss" || j.optBoolean("already_played", false)) {
            submitted = true
            onServerOutcome(j)
            return
        }
        if (j.optBoolean("ok", false) && j.has("memory_wrong_moves")) {
            wrongMoves =
                j.optInt("memory_wrong_moves", wrongMoves).coerceIn(0, maxWrongMoves)
        }
    }

    fun syncFlipAfterMismatch(log: List<Int>) {
        scope.launch {
            try {
                val j =
                    withContext(Dispatchers.IO) {
                        api.postDailyGameMemorySyncFlip(shop, ownerId, log)
                    }
                handleServerJson(j)
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(preview, timing.previewGraceMs) {
        if (preview && timing.previewGraceMs > 0) {
            delay(timing.previewGraceMs)
            preview = false
        }
    }

    LaunchedEffect(preview, deadline, submitted) {
        while (!preview && !submitted && System.currentTimeMillis() < deadline) {
            delay(400)
            tick++
        }
        if (!preview && !submitted && System.currentTimeMillis() >= deadline) {
            submit(true, emptyList())
        }
    }

    val matched = remember { mutableStateMapOf<Int, Boolean>() }
    var firstPick by remember { mutableStateOf<Int?>(null) }
    var lock by remember { mutableStateOf(false) }
    var openMismatch by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val flipLog = remember { mutableListOf<Int>() }

    fun imgForSlot(i: Int): String {
        val pk = deck.slotPairKeys.getOrNull(i) ?: return ""
        return deck.images.getOrNull(pk) ?: ""
    }

    fun isFaceUp(slot: Int): Boolean =
        preview ||
            matched[slot] == true ||
            firstPick == slot ||
            (openMismatch != null && (slot == openMismatch!!.first || slot == openMismatch!!.second))

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val secLeft =
            if (preview) {
                null
            } else {
                tick
                ((deadline - System.currentTimeMillis()).coerceAtLeast(0L) / 1000L).toInt()
            }
        Text(
            text =
                if (preview) {
                    t("eazy_chat.games_memory_preview_timer_frozen", "Timer starts after peek")
                } else if (secLeft != null && secLeft <= 0) {
                    t("eazy_chat.games_memory_time_up", "Time's up!")
                } else {
                    "${t("eazy_chat.games_memory_timer", "Time left")}: ${secLeft ?: 0}s"
                },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = LocalEazyModalPalette.current.text,
        )
        if (preview) {
            Text(
                text = t("eazy_chat.games_memory_preview", "Peek — cards hide when the countdown ends."),
                style = MaterialTheme.typography.bodySmall,
                color = LocalEazyModalPalette.current.muted,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = t("eazy_chat.games_memory_strikes_aria", "Wrong guesses remaining"),
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalEazyModalPalette.current.muted,
                    modifier = Modifier.weight(1f),
                )
                repeat(maxWrongMoves) { index ->
                    val spent = index < wrongMoves
                    Box(
                        modifier =
                            Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .then(
                                    if (spent) {
                                        Modifier.background(Color(0xFF374151))
                                    } else {
                                        Modifier.background(
                                            Brush.radialGradient(
                                                colors = listOf(Color(0xFFFBBF24), Color(0xFFEA580C)),
                                            ),
                                        )
                                    },
                                ),
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { submit(true, emptyList()) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !submitted,
        ) {
            Text(t("eazy_chat.games_memory_forfeit", "Give up"))
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(n) { slot ->
                val done = matched[slot] == true
                val faceUp = isFaceUp(slot)
                Box(
                    modifier =
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .clickable(
                                enabled =
                                    !submitted &&
                                        !preview &&
                                        !lock &&
                                        !done,
                            ) {
                                if (done || preview || lock || submitted) return@clickable
                                if (firstPick == slot) {
                                    firstPick = null
                                    return@clickable
                                }
                                if (firstPick == null && !faceUp) {
                                    firstPick = slot
                                    return@clickable
                                }
                                if (firstPick != null && !faceUp) {
                                    val a = firstPick!!
                                    val b = slot
                                    if (a == b) return@clickable
                                    firstPick = null
                                    flipLog.add(a)
                                    flipLog.add(b)
                                    if (deck.slotPairKeys[a] == deck.slotPairKeys[b]) {
                                        matched[a] = true
                                        matched[b] = true
                                        openMismatch = null
                                        if (matched.size >= n) {
                                            submit(false, flipLog.toList())
                                        }
                                    } else {
                                        openMismatch = a to b
                                        lock = true
                                        val logSnapshot = flipLog.toList()
                                        scope.launch {
                                            delay(timing.matchFlipMs.coerceIn(400L, 2500L))
                                            openMismatch = null
                                            lock = false
                                        }
                                        syncFlipAfterMismatch(logSnapshot)
                                    }
                                    return@clickable
                                }
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        done || faceUp ->
                            AsyncImage(
                                model = imgForSlot(slot),
                                contentDescription = null,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                contentScale = ContentScale.Fit,
                            )

                        else ->
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .background(
                                            brush =
                                                Brush.linearGradient(
                                                    listOf(Color(0xFFFF9A2A), Color(0xFFEA580C)),
                                                ),
                                        ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "?",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White.copy(alpha = 0.95f),
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                    }
                }
            }
        }
    }
}
