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

    fun applyStateJson(j: org.json.JSONObject) {
        val prize = j.optString("prize_amount", "").trim()
        prizeLine =
            if (prize.isNotEmpty()) {
                "${t("eazy_chat.games_prize_label", "Prize value")}: $prize"
            } else {
                null
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

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = t("eazy_chat.games_daily_intro", "Play once per day for a chance to win a gift card."),
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
                        val j = api.postDailyGamePlay(shop, oid)
                        if (!j.optBoolean("ok", false)) {
                            if (j.optString("error") == "play_in_progress") {
                                status = t("eazy_chat.games_pending", "Still processing — try again shortly.")
                            } else {
                                status = j.optString("message", t("eazy_chat.games_outcome_failed", "Could not complete play."))
                                playEnabled = true
                            }
                            busy = false
                            return@launch
                        }
                        when (j.optString("outcome")) {
                            "win" -> {
                                playEnabled = false
                                status = t("eazy_chat.games_outcome_win", "You won a gift card!")
                            }
                            "loss" -> {
                                playEnabled = false
                                status = t("eazy_chat.games_outcome_loss", "Not this time. Come back tomorrow.")
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
            enabled = playEnabled && !busy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = LocalEazyModalPalette.current.accent),
        ) {
            Text(t("eazy_chat.games_play", "Play today"), color = Color.White)
        }
    }
}
