package com.eazpire.creator.admin.cursoragent

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.eazpire.creator.auth.SecureTokenStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val TAG = "AdminCursorAgent"

class AdminCursorAgentViewModel(
    private val tokenStore: SecureTokenStore,
) : ViewModel() {

    var isAdmin by mutableStateOf(false)
        private set
    var cursorConfigured by mutableStateOf(false)
        private set
    var gateChecked by mutableStateOf(false)
        private set

    var panelOpen by mutableStateOf(false)
        private set
    var mode by mutableStateOf(AdminCursorMode.AGENT)
    var modelId by mutableStateOf(AdminCursorAgentDefaults.DEFAULT_MODEL)
    var models by mutableStateOf(listOf(AdminCursorAgentDefaults.DEFAULT_MODEL))
        private set

    var chats by mutableStateOf<List<AdminCursorChatSummary>>(emptyList())
        private set
    var chatId by mutableStateOf<String?>(null)
        private set
    var messages by mutableStateOf<List<AdminCursorMessage>>(emptyList())
        private set

    var promptText by mutableStateOf("")
    var includeScreenshot by mutableStateOf(false)
    var statusText by mutableStateOf("")
        private set
    var sending by mutableStateOf(false)
        private set
    var running by mutableStateOf(false)
        private set

    /** Null = use default bottom-right until layout known / prefs loaded. */
    var fabPos by mutableStateOf<AdminCursorFabPos?>(null)
        private set
    var fabDragging by mutableStateOf(false)

    var hideForScreenshot by mutableStateOf(false)
        private set

    private var pollJob: Job? = null
    private var saveFabJob: Job? = null

    private fun api(): AdminCursorAgentApi = AdminCursorAgentApi(jwt = tokenStore.getJwt())

    fun refreshAdminGate() {
        viewModelScope.launch {
            val jwt = tokenStore.getJwt()
            val ownerId = tokenStore.getOwnerId()
            if (jwt.isNullOrBlank()) {
                Log.i(TAG, "admin gate: no JWT — FAB hidden")
                isAdmin = false
                gateChecked = true
                return@launch
            }
            try {
                val me = api().me()
                val ok = me.optBoolean("ok")
                val admin = me.optBoolean("admin")
                isAdmin = ok && admin
                cursorConfigured = me.optBoolean("cursor_configured")
                // Security: never log tokens. Truncated owner id helps diagnose gate misses.
                // reason: no_auth | not_admin | forbidden — from admin-cursor-me soft deny.
                val ownerTail =
                    ownerId?.trim()?.takeIf { it.length >= 4 }?.takeLast(4) ?: "none"
                Log.i(
                    TAG,
                    "admin gate: ok=$ok admin=$admin isAdmin=$isAdmin " +
                        "cursorConfigured=$cursorConfigured ownerTail=…$ownerTail " +
                        "via=${me.optString("via", "")} reason=${me.optString("reason", "")} " +
                        "err=${me.optString("error", "")} " +
                        "actor=${me.optString("actor_id", "").takeLast(4)}",
                )
                if (isAdmin) {
                    loadFabPrefs()
                    loadModels()
                } else {
                    panelOpen = false
                }
            } catch (e: Exception) {
                Log.w(TAG, "admin gate failed — FAB hidden (non-admin or network): ${e.message}", e)
                isAdmin = false
                panelOpen = false
            } finally {
                gateChecked = true
            }
        }
    }

    private suspend fun loadFabPrefs() {
        try {
            val res = api().loadFabPrefs()
            if (res.optBoolean("ok")) {
                fabPos = AdminCursorAgentApi.parseFabPos(res, AdminCursorAgentDefaults.FAB_PREF_KEY)
            }
        } catch (_: Exception) {
            /* keep default */
        }
    }

    private suspend fun loadModels() {
        try {
            val res = api().listModels()
            if (res.optBoolean("ok")) {
                val arr = res.optJSONArray("items")
                val ids = mutableListOf<String>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i)
                        val id = o?.optString("id")?.trim().orEmpty()
                        if (id.isNotBlank()) ids.add(id)
                    }
                }
                if (ids.isNotEmpty()) {
                    models = ids
                    if (modelId !in ids) modelId = ids.first()
                }
            }
        } catch (_: Exception) {
            /* keep default */
        }
    }

    fun openPanel() {
        panelOpen = true
        statusText = if (cursorConfigured) "" else "CURSOR_API_KEY not configured on worker"
        refreshChats()
    }

    fun closePanel() {
        panelOpen = false
    }

    fun togglePanel() {
        if (panelOpen) closePanel() else openPanel()
    }

    fun refreshChats() {
        viewModelScope.launch {
            try {
                val res = api().listChats()
                if (res.optBoolean("ok")) {
                    chats = AdminCursorAgentApi.parseChats(res)
                }
            } catch (e: Exception) {
                statusText = e.message ?: "Failed to load chats"
            }
        }
    }

    fun selectChat(id: String) {
        chatId = id
        viewModelScope.launch { loadChatMessages(id) }
    }

    fun newChat() {
        chatId = null
        messages = emptyList()
        statusText = "New chat"
    }

    private suspend fun loadChatMessages(id: String) {
        try {
            val res = api().getChat(id)
            if (res.optBoolean("ok")) {
                messages = AdminCursorAgentApi.parseMessages(res)
                val chat = res.optJSONObject("chat")
                if (chat != null) {
                    mode = AdminCursorMode.fromApi(chat.optString("mode"))
                    val mid = chat.optString("model_id").trim()
                    if (mid.isNotBlank()) modelId = mid
                    val runId = chat.optString("active_run_id").takeIf { it.isNotBlank() }
                    val st = chat.optString("status")
                    if (st == "running" && runId != null) {
                        running = true
                        startPolling(id, runId)
                    }
                }
            }
        } catch (e: Exception) {
            statusText = e.message ?: "Failed to load chat"
        }
    }

    fun buildContextJson(): JSONObject =
        JSONObject()
            .put("portal", AdminCursorAgentDefaults.PORTAL)
            .put("href", AdminCursorAgentDefaults.HREF)
            .put("hostname", "android")
            .put("pathname", "/android-app")
            .put("title", "Eazpire Android")
            .put(
                "viewport",
                JSONObject().put("w", 0).put("h", 0),
            )
            .put("started_at", java.time.Instant.now().toString())
            .put("client", "android_native")

    fun send(activity: Activity) {
        if (sending || running) return
        val text = promptText.trim()
        if (text.isEmpty() && !includeScreenshot) {
            statusText = "Enter a prompt or enable screenshot"
            return
        }
        viewModelScope.launch {
            sending = true
            statusText = "Sending…"
            try {
                val images = mutableListOf<AdminCursorImageRef>()
                if (includeScreenshot) {
                    statusText = "Capturing screenshot…"
                    hideForScreenshot = true
                    delay(120)
                    val png = AdminCursorScreenshot.capturePng(activity)
                    hideForScreenshot = false
                    if (png != null) {
                        val up = api().uploadImage(png, "image/png")
                        if (up.optBoolean("ok")) {
                            val url = up.optString("url")
                            if (url.isNotBlank()) {
                                images.add(AdminCursorImageRef(url = url, mimeType = "image/png"))
                            } else {
                                statusText = "Screenshot upload missing url"
                            }
                        } else {
                            statusText = up.optString("error", "Screenshot upload failed")
                        }
                    } else {
                        statusText = "Screenshot capture failed"
                    }
                }

                val userText =
                    if (text.isBlank() && images.isNotEmpty()) {
                        "(screenshot attached — see image)"
                    } else {
                        text
                    }

                // Optimistic local user bubble
                if (userText.isNotBlank() || images.isNotEmpty()) {
                    messages = messages +
                        AdminCursorMessage(
                            id = "local_${System.currentTimeMillis()}",
                            role = "user",
                            content = userText,
                            imageUrls = images.map { it.url },
                        )
                }

                val res =
                    api().send(
                        chatId = chatId,
                        text = userText,
                        mode = mode.apiValue,
                        modelId = modelId,
                        context = buildContextJson(),
                        images = images,
                    )
                if (!res.optBoolean("ok")) {
                    statusText = res.optString("message").ifBlank {
                        res.optString("error", "Send failed")
                    }
                    sending = false
                    return@launch
                }
                val newChatId = res.optString("chat_id").takeIf { it.isNotBlank() }
                val runId = res.optString("run_id").takeIf { it.isNotBlank() }
                if (newChatId != null) {
                    chatId = newChatId
                    loadChatMessages(newChatId)
                }
                promptText = ""
                if (runId != null && newChatId != null) {
                    running = true
                    statusText = "Agent running…"
                    startPolling(newChatId, runId)
                } else {
                    statusText = "Sent"
                    refreshChats()
                }
            } catch (e: Exception) {
                hideForScreenshot = false
                statusText = e.message ?: "Send error"
            } finally {
                sending = false
            }
        }
    }

    private fun startPolling(id: String, runId: String) {
        pollJob?.cancel()
        pollJob =
            viewModelScope.launch {
                var attempts = 0
                while (attempts < 180) {
                    attempts++
                    delay(2000)
                    try {
                        val res = api().runGet(id, runId)
                        if (!res.optBoolean("ok")) continue
                        val status = res.optString("status", "UNKNOWN")
                        val terminal = res.optBoolean("terminal")
                        statusText = "Status: $status"
                        if (terminal) {
                            running = false
                            loadChatMessages(id)
                            refreshChats()
                            statusText =
                                if (status.equals("FINISHED", ignoreCase = true)) {
                                    "Synced to GitHub main (if deploy succeeded). On PC: git pull origin main"
                                } else {
                                    "Run ended: $status"
                                }
                            return@launch
                        }
                    } catch (_: Exception) {
                        /* retry */
                    }
                }
                running = false
                statusText = "Polling timed out — refresh chat"
            }
    }

    fun cancelRun() {
        val id = chatId ?: return
        viewModelScope.launch {
            try {
                api().cancel(id)
                pollJob?.cancel()
                running = false
                statusText = "Cancelled"
                loadChatMessages(id)
            } catch (e: Exception) {
                statusText = e.message ?: "Cancel failed"
            }
        }
    }

    fun onFabPosChanged(pos: AdminCursorFabPos, persist: Boolean) {
        fabPos = pos
        if (!persist) return
        saveFabJob?.cancel()
        saveFabJob =
            viewModelScope.launch {
                delay(280)
                try {
                    api().saveFabPref(
                        AdminCursorAgentDefaults.FAB_PREF_KEY,
                        pos.xPct,
                        pos.yPct,
                    )
                } catch (_: Exception) {
                    /* ignore */
                }
            }
    }

    fun resetFabPos() {
        fabPos = null
        viewModelScope.launch {
            try {
                api().clearFabPref(AdminCursorAgentDefaults.FAB_PREF_KEY)
            } catch (_: Exception) {
                /* ignore */
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        saveFabJob?.cancel()
        super.onCleared()
    }

    class Factory(private val tokenStore: SecureTokenStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AdminCursorAgentViewModel(tokenStore) as T
        }
    }
}
