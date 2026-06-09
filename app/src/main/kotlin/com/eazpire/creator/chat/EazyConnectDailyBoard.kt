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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray

data class ConnectTimingUi(
    val deadlineMs: Long,
    val serverNowMs: Long,
    val playMs: Long,
    val size: Int,
)

fun parseConnectBoard(arr: JSONArray): List<List<Int>> =
    List(arr.length()) { r ->
        val row = arr.getJSONArray(r)
        List(row.length()) { c -> row.getInt(c) }
    }

@Composable
fun EazyConnectDailyBoard(
    api: CreatorApi,
    shop: String,
    ownerId: String,
    initialBoard: List<List<Int>>,
    timing: ConnectTimingUi,
    onRoundComplete: () -> Unit,
    t: (String, String) -> String,
) {
    val scope = rememberCoroutineScope()
    val skewMs = remember(timing.serverNowMs) { timing.serverNowMs - System.currentTimeMillis() }
    val deadline = timing.deadlineMs + skewMs
    val size = timing.size.coerceAtLeast(3)

    var board by remember(initialBoard) { mutableStateOf(initialBoard) }
    var lock by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var statusLine by remember { mutableStateOf(t("eazy_chat.games_connect_your_turn", "Your turn")) }
    var tick by remember { mutableIntStateOf(0) }

    fun finishRound(forfeit: Boolean) {
        if (submitted) return
        submitted = true
        scope.launch {
            try {
                if (forfeit) {
                    api.postDailyGameConnectForfeit(shop, ownerId)
                } else {
                    api.postDailyGameConnectFinish(shop, ownerId, false)
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
            text = "${t("eazy_chat.games_connect_timer", "Time left")}: ${secLeft}s",
            style = MaterialTheme.typography.bodySmall,
            color = LocalEazyModalPalette.current.muted,
        )
        Text(
            text = statusLine,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalEazyModalPalette.current.text,
        )

        val cells = remember(board, size) {
            buildList {
                for (r in 0 until size) {
                    for (c in 0 until size) {
                        add(r to c)
                    }
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(size),
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(cells) { (r, c) ->
                val v = board.getOrNull(r)?.getOrNull(c) ?: 0
                val label =
                    when (v) {
                        1 -> "X"
                        2 -> "O"
                        else -> ""
                    }
                val bg =
                    when (v) {
                        1 -> Color(0xFF38BDF8).copy(alpha = 0.18f)
                        2 -> Color(0xFFF97316).copy(alpha = 0.2f)
                        else -> Color(0xFF0F172A).copy(alpha = 0.55f)
                    }
                Box(
                    modifier =
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(bg)
                            .clickable(enabled = !lock && !submitted && v == 0) {
                                lock = true
                                statusLine = t("eazy_chat.games_connect_eazy_turn", "Eazy is thinking…")
                                scope.launch {
                                    try {
                                        val j = api.postDailyGameConnectMove(shop, ownerId, r, c)
                                        if (j.has("connect_board")) {
                                            board = parseConnectBoard(j.getJSONArray("connect_board"))
                                        }
                                        when {
                                            j.optString("outcome") == "loss" -> {
                                                submitted = true
                                                onRoundComplete()
                                            }
                                            j.optString("connect_status") == "player_won" -> {
                                                val fin = api.postDailyGameConnectFinish(shop, ownerId, false)
                                                submitted = true
                                                fin.optString("outcome")
                                                onRoundComplete()
                                            }
                                            else -> {
                                                statusLine = t("eazy_chat.games_connect_your_turn", "Your turn")
                                                lock = false
                                            }
                                        }
                                    } catch (_: Exception) {
                                        statusLine = t("eazy_chat.chat_error_unknown", "Something went wrong.")
                                        lock = false
                                    }
                                }
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontWeight = FontWeight.Bold,
                        color =
                            when (v) {
                                1 -> Color(0xFF38BDF8)
                                2 -> Color(0xFFF97316)
                                else -> LocalEazyModalPalette.current.text
                            },
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { finishRound(true) },
            enabled = !submitted,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(t("eazy_chat.games_connect_forfeit", "Give up"))
        }
    }
}
