package com.eazpire.creator.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.AuthConfig
import kotlinx.coroutines.launch
import org.json.JSONObject

private fun parseMemorySession(j: JSONObject): Pair<MemoryDeckUi, MemoryTimingUi>? {
    val deck = j.optJSONObject("memory_deck") ?: return null
    val timing = j.optJSONObject("memory_timing") ?: return null
    val sk = deck.optJSONArray("slot_pair_keys") ?: return null
    val im = deck.optJSONArray("images") ?: return null
    val keys = List(sk.length()) { sk.getInt(it) }
    val imgs = List(im.length()) { im.getString(it) }
    val d = MemoryDeckUi(keys, imgs)
    val t =
        MemoryTimingUi(
            deadlineMs = timing.optLong("deadline_ms"),
            previewGraceMs = timing.optLong("preview_grace_ms"),
            matchFlipMs = timing.optLong("match_flip_ms", 850L),
            serverNowMs = timing.optLong("server_now_ms"),
        )
    return d to t
}

@Composable
fun EazyDailyGamePanel(
    api: CreatorApi,
    ownerId: String?,
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit,
    onDismiss: () -> Unit,
    t: (String, String) -> String,
) {
    val scope = rememberCoroutineScope()
    val shop = AuthConfig.SHOP_DOMAIN
    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var prizeLine by remember { mutableStateOf<String?>(null) }
    var playEnabled by remember { mutableStateOf(false) }
    var resumeMemory by remember { mutableStateOf(false) }
    var memorySession by remember { mutableStateOf<Pair<MemoryDeckUi, MemoryTimingUi>?>(null) }

    fun applyStateJson(j: JSONObject) {
        val prize = j.optString("prize_amount", "").trim()
        prizeLine =
            if (prize.isNotEmpty()) {
                "${t("eazy_chat.games_prize_label", "Prize value")}: $prize"
            } else {
                null
            }

        if (j.optBoolean("pending_memory", false)) {
            playEnabled = false
            status =
                t(
                    "eazy_chat.games_memory_resume",
                    "You have a game in progress — loading board…",
                )
            resumeMemory = true
            return
        }

        if (j.optBoolean("pending", false)) {
            playEnabled = false
            status = t("eazy_chat.games_pending", "Still processing — try again shortly.")
            return
        }

        val outcome = j.optString("outcome", "")
        when {
            j.optBoolean("already_played", false) && outcome == "win" -> {
                playEnabled = false
                status = t("eazy_chat.games_outcome_win", "You won a gift card!")
            }
            j.optBoolean("already_played", false) && outcome == "loss" -> {
                playEnabled = false
                status = t("eazy_chat.games_outcome_loss", "Not this time. Come back tomorrow.")
            }
            j.optBoolean("already_played", false) && outcome == "failed_issue" -> {
                playEnabled = false
                status = t("eazy_chat.games_outcome_failed", "We could not issue the prize.")
            }
            j.optBoolean("already_played", false) -> {
                playEnabled = false
                status = t("eazy_chat.games_already_played", "You already played today.")
            }
            else -> {
                playEnabled = true
                status = ""
            }
        }
    }

    LaunchedEffect(ownerId, isLoggedIn) {
        memorySession = null
        resumeMemory = false
        if (!isLoggedIn || ownerId.isNullOrBlank()) return@LaunchedEffect
        loading = true
        status = t("eazy_chat.games_loading", "Loading…")
        try {
            val j = api.getDailyGameState(shop)
            if (j.optBoolean("ok", false)) {
                applyStateJson(j)
            } else if (j.optString("error") == "unauthorized") {
                status = t("eazy_chat.games_login", "Sign in to play the daily game.")
                playEnabled = false
            } else {
                status = j.optString("message", t("eazy_chat.chat_error_unknown", "Something went wrong."))
                playEnabled = false
            }
        } catch (_: Exception) {
            status = t("eazy_chat.chat_error_unknown", "Something went wrong.")
            playEnabled = false
        } finally {
            loading = false
        }
    }

    LaunchedEffect(resumeMemory, ownerId, isLoggedIn) {
        val oid = ownerId ?: return@LaunchedEffect
        if (!isLoggedIn || !resumeMemory) return@LaunchedEffect
        resumeMemory = false
        loading = true
        try {
            val j = api.postDailyGameMemoryBegin(shop, oid)
            val parsed = parseMemorySession(j)
            if (j.optBoolean("ok", false) && parsed != null) {
                memorySession = parsed
                status = ""
            } else {
                status =
                    j.optString(
                        "message",
                        t("eazy_chat.games_memory_board_error", "Could not load the game board."),
                    )
                playEnabled = true
            }
        } catch (_: Exception) {
            status = t("eazy_chat.chat_error_unknown", "Something went wrong.")
            playEnabled = true
        } finally {
            loading = false
        }
    }

    if (!isLoggedIn) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = t("eazy_chat.login_required_text", "Sign in to use Eazy."),
                style = MaterialTheme.typography.bodyLarge,
                color = LocalEazyModalPalette.current.muted,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = {
                    onDismiss()
                    onLoginClick()
                },
                colors = ButtonDefaults.textButtonColors(contentColor = LocalEazyModalPalette.current.accent),
            ) {
                Text(t("eazy_chat.login_required_btn", "Sign in"))
            }
        }
        return
    }

    val sess = memorySession
    if (sess != null) {
        val deck = sess.first
        val timing = sess.second
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            prizeLine?.let { line ->
                Text(text = line, style = MaterialTheme.typography.bodySmall, color = LocalEazyModalPalette.current.muted)
            }
            EazyMemoryDailyBoard(
                deck = deck,
                timing = timing,
                onFinishRequest = fin@{ forfeit, flipLog ->
                    val oid = ownerId ?: return@fin
                    scope.launch {
                        busy = true
                        try {
                            val j = api.postDailyGameMemoryFinish(shop, oid, forfeit, flipLog)
                            memorySession = null
                            when {
                                j.optBoolean("ok", false) && j.optString("outcome") == "win" -> {
                                    status = t("eazy_chat.games_outcome_win", "You won a gift card!")
                                    playEnabled = false
                                }
                                j.optBoolean("ok", false) && j.optString("outcome") == "loss" -> {
                                    status = t("eazy_chat.games_outcome_loss", "Not this time. Come back tomorrow.")
                                    playEnabled = false
                                }
                                else -> {
                                    val st = api.getDailyGameState(shop)
                                    if (st.optBoolean("ok", false)) applyStateJson(st)
                                }
                            }
                        } catch (_: Exception) {
                            status = t("eazy_chat.chat_error_unknown", "Something went wrong.")
                            playEnabled = true
                        } finally {
                            busy = false
                        }
                    }
                },
                t = t,
            )
            if (busy) {
                CircularProgressIndicator(color = LocalEazyModalPalette.current.accent)
            }
        }
        return
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text =
                t(
                    "eazy_chat.games_memory_daily_intro",
                    "Flip two tiles at a time. Beat the countdown after the peek.",
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = LocalEazyModalPalette.current.text,
        )
        prizeLine?.let { line ->
            Text(text = line, style = MaterialTheme.typography.bodySmall, color = LocalEazyModalPalette.current.muted)
        }
        if (loading) {
            CircularProgressIndicator(color = LocalEazyModalPalette.current.accent)
            return@Column
        }
        if (status.isNotBlank()) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = LocalEazyModalPalette.current.text,
            )
        }
        Button(
            onClick = {
                val oid = ownerId ?: return@Button
                scope.launch {
                    busy = true
                    status = t("eazy_chat.games_loading", "Loading…")
                    try {
                        val j = api.postDailyGameMemoryBegin(shop, oid)
                        val parsed = parseMemorySession(j)
                        if (j.optBoolean("ok", false) && parsed != null) {
                            memorySession = parsed
                            status = ""
                        } else if (!j.optBoolean("ok", false)) {
                            if (j.optString("error") == "memory_pool_empty") {
                                status =
                                    t(
                                        "eazy_chat.games_memory_pool_empty",
                                        "Not enough designs available for today's puzzle.",
                                    )
                            } else {
                                status =
                                    j.optString(
                                        "message",
                                        t("eazy_chat.games_outcome_failed", "Could not complete play."),
                                    )
                            }
                            playEnabled = true
                        }
                    } catch (_: Exception) {
                        status = t("eazy_chat.chat_error_unknown", "Something went wrong.")
                        playEnabled = true
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = playEnabled && !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = LocalEazyModalPalette.current.accent),
        ) {
            Text(t("eazy_chat.games_play", "Start game"), color = Color.White)
        }
    }
}
