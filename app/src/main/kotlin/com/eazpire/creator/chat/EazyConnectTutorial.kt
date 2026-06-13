package com.eazpire.creator.chat

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private data class TutorialMove(val row: Int, val col: Int, val player: Int)

private data class TutorialStep(
    val captionKey: String,
    val captionFallback: String,
    val move: TutorialMove? = null,
    val win: Boolean = false,
)

private val TUTORIAL_STEPS =
    listOf(
        TutorialStep("eazy_chat.games_connect_tutorial_step1", "You play X and move first.", TutorialMove(2, 0, 1)),
        TutorialStep(
            "eazy_chat.games_connect_tutorial_step2",
            "Eazy responds with O after each of your moves.",
            TutorialMove(4, 0, 2),
        ),
        TutorialStep(
            "eazy_chat.games_connect_tutorial_step3",
            "Build four in a row — horizontal, vertical, or diagonal.",
            TutorialMove(2, 1, 1),
        ),
        TutorialStep("eazy_chat.games_connect_tutorial_step4", "Eazy tries to block you.", TutorialMove(4, 1, 2)),
        TutorialStep("eazy_chat.games_connect_tutorial_step5", "Three in a row — one more wins!", TutorialMove(2, 2, 1)),
        TutorialStep("eazy_chat.games_connect_tutorial_step6", "Keep an eye on Eazy's line too.", TutorialMove(4, 2, 2)),
        TutorialStep(
            "eazy_chat.games_connect_tutorial_step7",
            "Four X in a row wins the round!",
            TutorialMove(2, 3, 1),
            win = true,
        ),
    )

private val TUTORIAL_WIN_CELLS = setOf("2:0", "2:1", "2:2", "2:3")

@Composable
fun EazyConnectTutorial(
    onStartGame: (skipNextTime: Boolean) -> Unit,
    t: (String, String) -> String,
) {
    val size = 5
    val board =
        remember {
            mutableStateListOf<List<Int>>().apply {
                repeat(size) { add(List(size) { 0 }) }
            }
        }
    var caption by remember { mutableStateOf("") }
    var lastMove by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var winCells by remember { mutableStateOf<Set<String>>(emptySet()) }
    var finished by remember { mutableStateOf(false) }
    var skipNext by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(700)
        for (step in TUTORIAL_STEPS) {
            caption = t(step.captionKey, step.captionFallback)
            step.move?.let { mv ->
                val next = board.toMutableList()
                val row = next[mv.row].toMutableList()
                row[mv.col] = mv.player
                next[mv.row] = row
                for (i in next.indices) board[i] = next[i]
                lastMove = mv.row to mv.col
                if (step.win) winCells = TUTORIAL_WIN_CELLS
            }
            delay(if (step.win) 1400 else 850)
        }
        finished = true
        caption =
            t("eazy_chat.games_connect_tutorial_done", "Ready for today's puzzle? Start when you are.")
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = t("eazy_chat.games_connect_tutorial_badge", "Quick tutorial"),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF97316),
        )
        Text(
            text = caption,
            style = MaterialTheme.typography.bodyMedium,
            color = LocalEazyModalPalette.current.text,
        )

        val cells =
            remember(size) {
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
                val isWin = winCells.contains("$r:$c")
                val isLast = lastMove?.first == r && lastMove?.second == c
                val bg =
                    when (v) {
                        1 -> Color(0xFF38BDF8).copy(alpha = if (isWin) 0.28f else 0.18f)
                        2 -> Color(0xFFF97316).copy(alpha = 0.2f)
                        else -> Color(0xFF0F172A).copy(alpha = 0.55f)
                    }
                Box(
                    modifier =
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(bg),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontWeight = FontWeight.Bold,
                        color =
                            when (v) {
                                1 -> Color(0xFF38BDF8)
                                2 -> Color(0xFFF97316)
                                else -> Color.Transparent
                            },
                    )
                    if (isLast && !isWin) {
                        Box(
                            modifier =
                                Modifier
                                    .matchParentSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.06f)),
                        )
                    }
                }
            }
        }

        if (finished) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = skipNext, onCheckedChange = { skipNext = it })
                Text(
                    text = t("eazy_chat.games_connect_tutorial_skip", "Don't show this tutorial again"),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalEazyModalPalette.current.muted,
                )
            }
            Button(
                onClick = { onStartGame(skipNext) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = LocalEazyModalPalette.current.accent),
            ) {
                Text(
                    t("eazy_chat.games_connect_start_round", "Start game"),
                    color = Color.White,
                )
            }
        }
    }
}
