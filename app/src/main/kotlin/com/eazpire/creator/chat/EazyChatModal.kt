package com.eazpire.creator.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.LocalTranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class EazySidebarTab { Chat, Notifications, Jobs, Settings, Functions, Mascot }

enum class EazyChatContext { Shop, Creator }

private fun formatResetTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

private val ChatBg = Color(0xFF1F2937)
private val ChatHeader = Color(0xFF111827)
private val ChatAccent = Color(0xFFF97316)
private val ChatText = Color(0xFFE5E7EB)
private val ChatMuted = Color(0xFF9CA3AF)
private val UserBubble = Color(0xFF374151)
private val AssistantBubble = Color(0xFF4B5563)

@Composable
fun EazyChatModal(
    visible: Boolean,
    tokenStore: SecureTokenStore?,
    chatStore: EazyChatStore,
    onDismiss: () -> Unit,
    onLoginClick: () -> Unit,
    onResetMascot: () -> Unit = {},
    chatContext: EazyChatContext = EazyChatContext.Shop,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val context = LocalContext.current
    val store = LocalTranslationStore.current
    val t = store?.let { { k: String, d: String -> it.t(k, d) } } ?: { _: String, d: String -> d }
    val api = remember { CreatorApi(jwt = tokenStore?.getJwt()) }
    val scope = rememberCoroutineScope()

    val messages by chatStore.messages.collectAsState()
    val conversationId by chatStore.conversationId.collectAsState()
    val isLoading by chatStore.isLoading.collectAsState()
    val isTyping by chatStore.isTyping.collectAsState()
    val limitReached by chatStore.limitReached.collectAsState()
    val rateLimit by chatStore.rateLimit.collectAsState()
    val isLoggedIn = tokenStore?.isLoggedIn() == true
    val pagePath = if (chatContext == EazyChatContext.Creator) "/creator" else "/shop"

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        chatStore.setLoading(true)
        val customerId = tokenStore?.getOwnerId()
        val userId = chatStore.getUserId(customerId)
        try {
            val resp = withContext(Dispatchers.IO) { api.getEazyConversation(userId) }
            if (resp.optBoolean("ok", false)) {
                val conv = resp.optJSONObject("conversation")
                val msgs = resp.optJSONArray("messages") ?: org.json.JSONArray()
                conv?.optString("id")?.let { chatStore.setConversationId(it) }
                // rate_limit comes from chat-completion, not eazy-conv
                val list = (0 until msgs.length()).mapNotNull { i ->
                    val m = msgs.optJSONObject(i) ?: return@mapNotNull null
                    val content = m.optString("content", "")
                    if (content.isBlank()) return@mapNotNull null
                    ChatMessage(
                        id = m.opt("id")?.toString() ?: "m$i",
                        role = m.optString("role", "user"),
                        content = content
                    )
                }
                chatStore.setMessages(list)
            }
        } catch (_: Exception) {}
        chatStore.setLoading(false)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ChatBg)
            ) {
                var selectedTab by remember { mutableStateOf(EazySidebarTab.Chat) }
                var sidebarOpen by remember { mutableStateOf(true) }

                Row(modifier = Modifier.fillMaxSize()) {
                    // Sidebar
                    Column(
                        modifier = Modifier
                            .width(if (sidebarOpen) 56.dp else 0.dp)
                            .fillMaxSize()
                            .background(ChatHeader)
                            .then(if (sidebarOpen) Modifier.border(1.dp, ChatMuted.copy(alpha = 0.3f)) else Modifier)
                    ) {
                        if (sidebarOpen) {
                            listOf(
                                EazySidebarTab.Chat to Icons.Default.Chat,
                                EazySidebarTab.Notifications to Icons.Default.Notifications,
                                EazySidebarTab.Jobs to Icons.Default.Bolt,
                                EazySidebarTab.Settings to Icons.Default.Settings,
                                EazySidebarTab.Functions to Icons.Default.Build,
                                EazySidebarTab.Mascot to Icons.Default.Pets
                            ).forEach { (tab, icon) ->
                                IconButton(
                                    onClick = { selectedTab = tab },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .then(
                                            if (selectedTab == tab) Modifier.background(ChatAccent.copy(alpha = 0.2f))
                                            else Modifier
                                        )
                                ) {
                                    Icon(icon, contentDescription = tab.name, tint = if (selectedTab == tab) ChatAccent else ChatMuted)
                                }
                            }
                        }
                    }
                    // Content
                    Column(modifier = Modifier.weight(1f).fillMaxSize().background(ChatBg)) {
                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ChatHeader)
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { sidebarOpen = !sidebarOpen }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = if (sidebarOpen) t("eazy_chat.ui_close_sidebar", "Close sidebar") else t("eazy_chat.ui_open_sidebar", "Open sidebar"),
                                    tint = ChatText
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = when (selectedTab) {
                                    EazySidebarTab.Chat -> t("eazy_chat.ui_chat_title", "eazy")
                                    EazySidebarTab.Notifications -> t("creator.notifications.notifications_tab", "Notifications")
                                    EazySidebarTab.Jobs -> t("creator.notifications.active_jobs", "Active Jobs")
                                    EazySidebarTab.Settings -> t("eazy_chat.ui_settings_tab", "Settings")
                                    EazySidebarTab.Functions -> t("eazy_chat.ui_functions_tab", "Functions")
                                    EazySidebarTab.Mascot -> t("eazy_chat.ui_mascot_tab", "Mascot")
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = ChatText
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = t("eazy_chat.ui_close_chat", "Close"), tint = ChatText)
                            }
                        }

                when (selectedTab) {
                    EazySidebarTab.Chat -> {
                if (!isLoggedIn) {
                    // Login gate
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = t("eazy_chat.login_required_text", "Sign in to chat with eazy"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = ChatMuted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = {
                                onDismiss()
                                onLoginClick()
                            },
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = ChatAccent)
                        ) {
                            Text(t("eazy_chat.login_required_btn", "Sign in"))
                        }
                    }
                } else if (limitReached) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = t("eazy_chat.ui_limit_quota_used", "Your chat quota for this hour is used up."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ChatMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                // Rate limit bar (like web creator-chat__rate-limit)
                if (rateLimit != null) {
                    val rl = rateLimit!!
                    val rem = rl.remaining
                    val lim = rl.limit
                    val pct = if (lim > 0) (rem.toFloat() / lim * 100).toInt() else 100
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ChatHeader)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "$rem ${t("eazy_chat.ui_messages_of", "of")} $lim ${t("eazy_chat.ui_messages", "messages")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ChatMuted
                            )
                            if (rl.resetIn > 0) {
                                Text(
                                    text = "${t("eazy_chat.ui_reset_in", "Reset in")} ${formatResetTime(rl.resetIn)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ChatMuted
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(ChatMuted.copy(alpha = 0.2f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(pct / 100f)
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(if (pct <= 10) ChatMuted else ChatAccent)
                            )
                        }
                    }
                }
                // Messages
                val listState = rememberLazyListState()
                LaunchedEffect(messages.size) {
                    if (messages.isNotEmpty()) {
                        listState.animateScrollToItem(messages.size - 1)
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isLoading && messages.isEmpty()) {
                        item {
                            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = ChatAccent, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                    items(messages) { msg ->
                        ChatBubble(
                            message = msg,
                            isUser = msg.role == "user"
                        )
                    }
                    if (isTyping) {
                        item {
                            ChatBubble(
                                message = ChatMessage("typing", "assistant", "..."),
                                isUser = false,
                                isTyping = true
                            )
                        }
                    }
                }

                // Input
                var inputText by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    BasicTextField(
                        value = inputText,
                        onValueChange = { if (it.length <= 500) inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(UserBubble)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = ChatText),
                        cursorBrush = SolidColor(ChatAccent),
                        singleLine = false,
                        maxLines = 4,
                        decorationBox = { inner ->
                            Box(modifier = Modifier.padding(end = 8.dp)) {
                                if (inputText.isEmpty()) {
                                    Text(
                                        text = t("eazy_chat.ui_message_placeholder", "Type a message..."),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ChatMuted
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val text = inputText.trim()
                            if (text.isBlank() || isTyping) return@IconButton
                            inputText = ""
                            scope.launch {
                                chatStore.addMessage(ChatMessage("u${System.currentTimeMillis()}", "user", text))
                                chatStore.setTyping(true)
                                val customerId = tokenStore?.getOwnerId()
                                val userId = chatStore.getUserId(customerId)
                                val msgList = chatStore.messages.value.map { it.role to it.content }
                                try {
                                    val resp = withContext(Dispatchers.IO) {
                                        api.chatCompletion(
                                            userId = userId,
                                            messages = msgList,
                                            conversationId = conversationId,
                                            context = mapOf(
                                                "page" to pagePath,
                                                "locale" to java.util.Locale.getDefault().language
                                            )
                                        )
                                    }
                                    chatStore.setTyping(false)
                                    val rl = resp.optJSONObject("rate_limit")
                                    if (rl != null) {
                                        chatStore.setRateLimit(RateLimitState(
                                            remaining = rl.optInt("remaining", 30),
                                            limit = rl.optInt("limit", 30),
                                            resetAt = rl.optLong("reset_at", 0),
                                            resetIn = rl.optInt("reset_in", 0)
                                        ))
                                        if (rl.optInt("remaining", 30) <= 0) {
                                            chatStore.setLimitReached(true)
                                        }
                                    }
                                    if (resp.optBoolean("ok", false)) {
                                        val reply = resp.optString("text", "")
                                        if (reply.isNotBlank()) {
                                            chatStore.addMessage(ChatMessage("a${System.currentTimeMillis()}", "assistant", reply))
                                            resp.optString("conversation_id", "")?.takeIf { it.isNotBlank() }?.let { chatStore.setConversationId(it) }
                                        }
                                    }
                                } catch (_: Exception) {
                                    chatStore.setTyping(false)
                                    chatStore.addMessage(ChatMessage("err${System.currentTimeMillis()}", "assistant", t("eazy_chat.chat_network_error_retry", "Network error. Please try again.")))
                                }
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ChatAccent)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = t("eazy_chat.ui_send", "Send"),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                }
                    }
                    EazySidebarTab.Notifications -> EazyPlaceholderView(t("creator.notifications.notifications_tab", "Notifications"), t("creator.notifications.empty", "No notifications"))
                    EazySidebarTab.Jobs -> EazyPlaceholderView(t("creator.notifications.active_jobs", "Active Jobs"), t("creator.notifications.empty_jobs", "No active jobs"))
                    EazySidebarTab.Settings -> EazySettingsView(t, onDismiss, onResetMascot)
                    EazySidebarTab.Functions -> EazyPlaceholderView(t("eazy_chat.ui_functions_tab", "Functions"), t("eazy_chat.functions_hint", "Functions coming soon"))
                    EazySidebarTab.Mascot -> EazyMascotTabView(
                        ownerId = tokenStore?.getOwnerId(),
                        api = api,
                        t = t
                    )
                }
                    }
                }
            }
        }
    }
}

@Composable
private fun EazySettingsView(
    t: (String, String) -> String,
    onDismiss: () -> Unit,
    onResetMascot: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Placement (like web eazy-settings__section)
        Text(
            text = t("eazy_chat.settings_placement", "Placement"),
            style = MaterialTheme.typography.titleSmall,
            color = ChatMuted
        )
        androidx.compose.material3.TextButton(
            onClick = {
                onResetMascot()
                onDismiss()
            },
            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = ChatAccent)
        ) {
            Text(t("eazy_chat.settings_reset_mascot", "Reset mascot position"))
        }

        // Privacy (like web)
        Text(
            text = t("eazy_chat.settings_privacy", "Privacy"),
            style = MaterialTheme.typography.titleSmall,
            color = ChatMuted
        )
        Text(
            text = t("eazy_chat.settings_clear_history_hint", "Clear chat history and memory. Coming soon."),
            style = MaterialTheme.typography.bodySmall,
            color = ChatMuted
        )
    }
}

@Composable
private fun EazyPlaceholderView(title: String, hint: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = hint, style = MaterialTheme.typography.bodyMedium, color = ChatMuted)
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    isUser: Boolean,
    isTyping: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (isUser) ChatAccent else AssistantBubble)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .widthIn(max = 280.dp)
        ) {
            if (isTyping) {
                CircularProgressIndicator(color = ChatText, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White
                )
            }
        }
    }
}
