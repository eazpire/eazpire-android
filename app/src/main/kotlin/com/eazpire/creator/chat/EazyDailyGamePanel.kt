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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.AuthConfig
import kotlinx.coroutines.delay
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
            maxWrongMoves = timing.optInt("max_wrong_moves", 5).coerceIn(1, 20),
            memoryWrongMoves = timing.optInt("memory_wrong_moves", 0).coerceAtLeast(0),
            playStarted = timing.optBoolean("play_started", false),
        )
    return d to t
}

private data class ConnectSessionUi(
    val board: List<List<Int>>,
    val timing: ConnectTimingUi,
)

private fun parseConnectSession(j: JSONObject): ConnectSessionUi? {
    val boardArr = j.optJSONArray("connect_board") ?: return null
    val timing = j.optJSONObject("connect_timing") ?: return null
    return ConnectSessionUi(
        board = parseConnectBoard(boardArr),
        timing =
            ConnectTimingUi(
                deadlineMs = timing.optLong("deadline_ms"),
                serverNowMs = timing.optLong("server_now_ms"),
                playMs = timing.optLong("play_ms", 240_000L),
                size = timing.optInt("size", 5),
            ),
    )
}

private fun formatCooldownLabel(sec: Int): String {
    val s = sec.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val r = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, r) else "%d:%02d".format(m, r)
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
    val canPlay = !ownerId.isNullOrBlank()
    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var prizeLine by remember { mutableStateOf<String?>(null) }
    var playEnabled by remember { mutableStateOf(false) }
    var selectedSlug by remember { mutableStateOf("memory_match") }
    var pickerItems by remember { mutableStateOf<List<DailyGamePickerItem>>(emptyList()) }
    var notifyPush by remember { mutableStateOf(true) }
    var notifyEmail by remember { mutableStateOf(false) }
    var resumeMemory by remember { mutableStateOf(false) }
    var resumeConnect by remember { mutableStateOf(false) }
    var resumeSimon by remember { mutableStateOf(false) }
    var memorySession by remember { mutableStateOf<Pair<MemoryDeckUi, MemoryTimingUi>?>(null) }
    var connectSession by remember { mutableStateOf<ConnectSessionUi?>(null) }
    var simonSession by remember { mutableStateOf<SimonSessionUi?>(null) }
    var showConnectTutorial by remember { mutableStateOf(false) }
    val appContext = LocalContext.current.applicationContext
    var lastStateJson by remember { mutableStateOf<JSONObject?>(null) }

    fun syncReminderFromState(j: JSONObject?) {
        val root = j ?: return
        val sched = root.optJSONObject("notify_schedule")
        val pushFlag = sched?.optBoolean("notify_push") ?: notifyPush
        val emailFlag = sched?.optBoolean("notify_email") ?: notifyEmail
        if (!pushFlag && !emailFlag) {
            DailyGameReminderScheduler.sync(null, false, false)
            return
        }
        val atMs =
            sched?.optLong("notify_at_ms")?.takeIf { it > 0 }
                ?: root.optLong("next_available_ms", 0L).takeIf { it > System.currentTimeMillis() }
        DailyGameReminderScheduler.sync(atMs, pushFlag, emailFlag)
    }

    fun beginConnectGame() {
        val oid = ownerId ?: return
        scope.launch {
            busy = true
            showConnectTutorial = false
            status = t("eazy_chat.games_loading", "Loading…")
            try {
                val j = api.postDailyGameConnectBegin(shop, oid, selectedSlug)
                val parsed = parseConnectSession(j)
                if (j.optBoolean("ok", false) && parsed != null) {
                    connectSession = parsed
                    status = ""
                } else {
                    status =
                        j.optString(
                            "message",
                            t("eazy_chat.games_outcome_failed", "Could not complete play."),
                        )
                    playEnabled = true
                }
            } catch (_: Exception) {
                status = t("eazy_chat.chat_error_unknown", "Something went wrong.")
                playEnabled = true
            } finally {
                busy = false
            }
        }
    }

    fun applyStateJson(j: JSONObject) {
        lastStateJson = j
        val gamesArr = j.optJSONArray("games")
        pickerItems = parseDailyGamePickerItems(gamesArr)
        if (pickerItems.isEmpty()) {
            pickerItems =
                listOf(
                    DailyGamePickerItem("memory_match", "Memory Match", true, "available", 0),
                    DailyGamePickerItem("connect_four_5x5", "Connect Four", true, "available", 0),
                    DailyGamePickerItem("simon_says", "Simon Says", true, "available", 0),
                )
        }
        selectedSlug =
            j.optString("selected_game_slug", j.optString("today_game_slug", j.optString("game_slug", "memory_match")))
        val notify = j.optJSONObject("notify")
        notifyPush = notify?.optBoolean("push_enabled", true) != false
        notifyEmail = notify?.optBoolean("email_enabled", false) == true

        val selectedState = pickerItems.find { it.slug == selectedSlug }
        val winProb = j.optDouble("win_probability", 0.25)
        prizeLine =
            "${(winProb * 100).toInt()}% ${t("eazy_chat.games_win_chance_suffix", "win chance")} · ${t("eazy_chat.games_random_prizes_hint", "random daily prizes")}"

        if (selectedState?.status == "cooldown") {
            playEnabled = false
            status =
                t("eazy_chat.games_cooldown_wait", "Available again in {{time}}").replace(
                    "{{time}}",
                    formatCooldownLabel(selectedState.cooldownRemainingSec),
                )
            syncReminderFromState(j)
            return
        }

        if (selectedState != null && !selectedState.available && selectedState.status == "locked") {
            playEnabled = false
            status = t("eazy_chat.games_other_in_progress", "Finish your current game first.")
            return
        }

        if (j.optBoolean("pending_memory", false)) {
            selectedSlug = "memory_match"
            playEnabled = false
            status =
                t(
                    "eazy_chat.games_memory_resume",
                    "You have a game in progress — loading board…",
                )
            resumeMemory = true
            return
        }

        if (j.optBoolean("pending_connect", false)) {
            selectedSlug = "connect_four_5x5"
            playEnabled = false
            status =
                t(
                    "eazy_chat.games_connect_resume",
                    "You have a game in progress — continuing.",
                )
            resumeConnect = true
            return
        }

        if (j.optBoolean("pending_simon", false)) {
            selectedSlug = "simon_says"
            playEnabled = false
            status =
                t(
                    "eazy_chat.games_simon_resume",
                    "You have a game in progress — continuing.",
                )
            resumeSimon = true
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
        syncReminderFromState(j)
    }

    fun applyMemoryOutcome(j: JSONObject) {
        memorySession = null
        val oid = ownerId ?: return
        scope.launch {
            try {
                val st = api.getDailyGameState(shop, oid)
                if (st.optBoolean("ok", false)) {
                    applyStateJson(st)
                    return@launch
                }
            } catch (_: Exception) {
            }
            when {
                j.optBoolean("ok", false) && j.optString("outcome") == "win" -> {
                    status = t("eazy_chat.games_outcome_win", "You won a gift card!")
                    playEnabled = false
                }
                j.optString("outcome") == "loss" || j.optBoolean("already_played", false) -> {
                    status = t("eazy_chat.games_outcome_loss", "Not this time. Come back tomorrow.")
                    playEnabled = false
                }
                else -> {
                    playEnabled = true
                }
            }
        }
    }

    LaunchedEffect(lastStateJson, selectedSlug, pickerItems) {
        val gs = pickerItems.find { it.slug == selectedSlug }
        val target = gs?.nextAvailableMs ?: lastStateJson?.optLong("next_available_ms", 0L)?.takeIf { it > 0L }
        if (gs?.status != "cooldown" || target == null) return@LaunchedEffect
        while (target > System.currentTimeMillis()) {
            val sec = ((target - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
            status =
                t("eazy_chat.games_cooldown_wait", "Available again in {{time}}").replace(
                    "{{time}}",
                    formatCooldownLabel(sec),
                )
            playEnabled = false
            delay(1000)
        }
    }

    LaunchedEffect(ownerId, canPlay, isLoggedIn) {
        memorySession = null
        connectSession = null
        simonSession = null
        showConnectTutorial = false
        resumeMemory = false
        resumeConnect = false
        resumeSimon = false
        if (!canPlay) return@LaunchedEffect
        loading = true
        status = t("eazy_chat.games_loading", "Loading…")
        try {
            val j = api.getDailyGameState(shop, ownerId)
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

    LaunchedEffect(resumeMemory, ownerId, canPlay) {
        val oid = ownerId ?: return@LaunchedEffect
        if (!canPlay || !resumeMemory) return@LaunchedEffect
        resumeMemory = false
        loading = true
        try {
            val j = api.postDailyGameMemoryBegin(shop, oid, selectedSlug)
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

    LaunchedEffect(resumeConnect, ownerId, canPlay) {
        val oid = ownerId ?: return@LaunchedEffect
        if (!canPlay || !resumeConnect) return@LaunchedEffect
        resumeConnect = false
        loading = true
        try {
            val j = api.postDailyGameConnectBegin(shop, oid)
            val parsed = parseConnectSession(j)
            if (j.optBoolean("ok", false) && parsed != null) {
                connectSession = parsed
                status = ""
            } else {
                status =
                    j.optString(
                        "message",
                        t("eazy_chat.games_connect_board_error", "Could not load the game board."),
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

    LaunchedEffect(resumeSimon, ownerId, canPlay) {
        val oid = ownerId ?: return@LaunchedEffect
        if (!canPlay || !resumeSimon) return@LaunchedEffect
        resumeSimon = false
        loading = true
        try {
            val j = api.postDailyGameSimonBegin(shop, oid)
            if (j.optBoolean("already_played", false)) {
                status =
                    j.optString(
                        "message",
                        t("eazy_chat.games_already_played", "You already played today."),
                    )
                playEnabled = false
            } else {
                val parsed = parseSimonSession(j)
                if (j.optBoolean("ok", false) && parsed != null) {
                    simonSession = parsed
                    status = ""
                } else {
                    status =
                        j.optString(
                            "message",
                            t("eazy_chat.games_simon_board_error", "Could not load the game board."),
                        )
                    playEnabled = true
                }
            }
        } catch (_: Exception) {
            status = t("eazy_chat.chat_error_unknown", "Something went wrong.")
            playEnabled = true
        } finally {
            loading = false
        }
    }

    if (!canPlay) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = t("eazy_chat.games_login", "Sign in to play the daily game."),
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

    val memSess = memorySession
    if (memSess != null) {
        val memOwnerId = ownerId ?: return
        val deck = memSess.first
        val timing = memSess.second
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
                api = api,
                shop = shop,
                ownerId = memOwnerId,
                onFinishRequest = fin@{ forfeit, flipLog ->
                    val owner = ownerId ?: return@fin
                    scope.launch {
                        busy = true
                        try {
                            val j = api.postDailyGameMemoryFinish(shop, owner, forfeit, flipLog)
                            applyMemoryOutcome(j)
                        } catch (_: Exception) {
                            status = t("eazy_chat.chat_error_unknown", "Something went wrong.")
                            playEnabled = true
                        } finally {
                            busy = false
                        }
                    }
                },
                onServerOutcome = { j -> applyMemoryOutcome(j) },
                t = t,
            )
            if (busy) {
                CircularProgressIndicator(color = LocalEazyModalPalette.current.accent)
            }
        }
        return
    }

    if (showConnectTutorial) {
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
            EazyConnectTutorial(
                onStartGame = { skipNext ->
                    if (skipNext) {
                        EazyConnectTutorialPrefs.setDismissed(appContext, true)
                    }
                    beginConnectGame()
                },
                t = t,
            )
            if (busy) {
                CircularProgressIndicator(color = LocalEazyModalPalette.current.accent)
            }
        }
        return
    }

    val connSess = connectSession
    if (connSess != null) {
        val oid = ownerId ?: return
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
            EazyConnectDailyBoard(
                api = api,
                shop = shop,
                ownerId = oid,
                initialBoard = connSess.board,
                timing = connSess.timing,
                onRoundComplete = {
                    scope.launch {
                        busy = true
                        connectSession = null
                        try {
                            val st = api.getDailyGameState(shop, ownerId)
                            if (st.optBoolean("ok", false)) applyStateJson(st)
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

    val simonSess = simonSession
    if (simonSess != null) {
        val oid = ownerId ?: return
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            EazySimonDailyBoard(
                api = api,
                shop = shop,
                ownerId = oid,
                session = simonSess,
                modifier = Modifier.weight(1f),
                onRoundComplete = {
                    scope.launch {
                        busy = true
                        simonSession = null
                        try {
                            val st = api.getDailyGameState(shop, ownerId)
                            if (st.optBoolean("ok", false)) applyStateJson(st)
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
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EazyGamesPickerCarousel(
            api = api,
            items = pickerItems,
            selectedSlug = selectedSlug,
            notifyPush = notifyPush,
            notifyEmail = notifyEmail,
            onSelect = { slug ->
                selectedSlug = slug
                val gs = pickerItems.find { it.slug == slug }
                if (gs?.status == "cooldown") {
                    playEnabled = false
                    status =
                        t("eazy_chat.games_cooldown_wait", "Available again in {{time}}").replace(
                            "{{time}}",
                            formatCooldownLabel(gs.cooldownRemainingSec),
                        )
                } else if (gs != null && gs.available) {
                    playEnabled = true
                    status = ""
                } else if (gs?.status == "locked") {
                    playEnabled = false
                    status = t("eazy_chat.games_other_in_progress", "Finish your current game first.")
                }
            },
            onNotifyChange = { push, email ->
                notifyPush = push
                notifyEmail = email
                syncReminderFromState(lastStateJson)
            },
            t = t,
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
                        if (selectedSlug == "connect_four_5x5") {
                            if (EazyConnectTutorialPrefs.isDismissed(appContext)) {
                                beginConnectGame()
                            } else {
                                busy = false
                                playEnabled = false
                                showConnectTutorial = true
                                status = ""
                            }
                        } else if (selectedSlug == "simon_says") {
                            val j = api.postDailyGameSimonBegin(shop, oid)
                            if (j.optBoolean("already_played", false)) {
                                status =
                                    j.optString(
                                        "message",
                                        t("eazy_chat.games_already_played", "You already played today."),
                                    )
                                playEnabled = false
                            } else {
                                val parsed = parseSimonSession(j)
                                if (j.optBoolean("ok", false) && parsed != null) {
                                    simonSession = parsed
                                    status = ""
                                } else {
                                    status =
                                        j.optString(
                                            "message",
                                            t("eazy_chat.games_simon_board_error", "Could not load the game board."),
                                        )
                                    playEnabled = true
                                }
                            }
                        } else {
                            val j = api.postDailyGameMemoryBegin(shop, oid, selectedSlug)
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
