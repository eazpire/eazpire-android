package com.eazpire.creator.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.LocalTranslationStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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

private data class EazyConvTabItem(
    val id: String,
    val preview: String?,
    val summary: String?,
    val messageCount: Int = 0
)

private data class EazyNotifRow(
    val id: String,
    val title: String,
    val message: String,
    val isRead: Boolean,
    val createdAt: String?,
    val category: String?,
    val isSystem: Boolean = false,
    val systemAudience: String? = null
)

private data class EazyKvJobRow(
    val id: String,
    val title: String,
    val progress: Int,
    val done: Boolean,
    val status: String?
)

private data class EazySystemJobRow(
    val sessionId: String,
    val title: String,
    val status: String,
    val message: String?
)

private fun parseMessagesArray(msgs: JSONArray): List<ChatMessage> {
    return (0 until msgs.length()).mapNotNull { i ->
        val m = msgs.optJSONObject(i) ?: return@mapNotNull null
        val content = m.optString("content", "")
        if (content.isBlank()) return@mapNotNull null
        ChatMessage(
            id = m.opt("id")?.toString() ?: "m$i",
            role = m.optString("role", "user"),
            content = content
        )
    }
}

private fun JSONObject.notificationIsRead(): Boolean {
    val v = opt("is_read") ?: opt("read")
    return when (v) {
        null -> false
        is Boolean -> v
        is Number -> v.toInt() == 1
        is String -> v == "1" || v.equals("true", true)
        else -> false
    }
}

private fun parseNotifications(
    arr: JSONArray,
    isSystem: Boolean = false,
    systemAudience: String? = null
): List<EazyNotifRow> {
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val id = o.optString("notification_id", o.optString("id", "")).ifBlank { return@mapNotNull null }
        EazyNotifRow(
            id = id,
            title = o.optString("title", "").ifBlank { "Notification" },
            message = o.optString("message", ""),
            isRead = o.notificationIsRead(),
            createdAt = o.optString("created_at", "").takeIf { it.isNotBlank() },
            category = o.optString("category", "").takeIf { it.isNotBlank() },
            isSystem = isSystem,
            systemAudience = systemAudience
        )
    }
}

private fun parseKvJobs(arr: JSONArray): List<EazyKvJobRow> {
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val id = o.optString("job_id", o.optString("id", "")).ifBlank { return@mapNotNull null }
        EazyKvJobRow(
            id = id,
            title = o.optString("prompt", o.optString("title", "")).ifBlank { id },
            progress = o.optInt("progress", 0).coerceIn(0, 100),
            done = o.optBoolean("done", false),
            status = o.optString("status", "").takeIf { it.isNotBlank() }
        )
    }
}

private fun parseSystemJobs(arr: JSONArray): List<EazySystemJobRow> {
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val sid = o.optString("session_id", "").ifBlank { return@mapNotNull null }
        val msg = o.optString("error_message", "").takeIf { it.isNotBlank() }
            ?: o.optString("summary", "").takeIf { it.isNotBlank() }
        EazySystemJobRow(
            sessionId = sid,
            title = o.optString("title", "").ifBlank { "System publish" },
            status = o.optString("status", ""),
            message = msg
        )
    }
}

/** System notifications: one combined list (creator + shop APIs), deduped by id. */
private fun mergeSystemNotificationRows(a: List<EazyNotifRow>, b: List<EazyNotifRow>): List<EazyNotifRow> {
    val merged = mutableMapOf<String, EazyNotifRow>()
    for (n in a + b) {
        val existing = merged[n.id]
        if (existing == null || (n.createdAt ?: "") >= (existing.createdAt ?: "")) {
            merged[n.id] = n
        }
    }
    return merged.values.sortedWith(compareByDescending { it.createdAt ?: "" })
}

/** System jobs: creator + shop lists merged, deduped by session id (no Creator/Shop tabs in UI). */
private fun mergeSystemJobRows(a: List<EazySystemJobRow>, b: List<EazySystemJobRow>): List<EazySystemJobRow> {
    val seen = mutableSetOf<String>()
    return (a + b).filter { seen.add(it.sessionId) }
}

private fun isUsableTabText(s: String?): Boolean {
    val t = s?.trim() ?: return false
    if (t.isEmpty()) return false
    if (t.equals("null", ignoreCase = true)) return false
    if (t.equals("undefined", ignoreCase = true)) return false
    return true
}

private fun sanitizeTabPreviewSummary(preview: String?, summary: String?): Pair<String?, String?> {
    val p = preview?.takeIf { isUsableTabText(it) }
    val s = summary?.takeIf { isUsableTabText(it) }
    return p to s
}

private fun tabStripLabel(preview: String?, summary: String?, newChatFallback: String): String {
    val p = preview?.takeIf { isUsableTabText(it) }
    val s = summary?.takeIf { isUsableTabText(it) }
    return p ?: s ?: newChatFallback
}

private fun parseConvTabs(arr: JSONArray): List<EazyConvTabItem> {
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val id = o.optString("id", "").ifBlank { return@mapNotNull null }
        val (pv, sm) = sanitizeTabPreviewSummary(
            o.optString("preview", "").takeIf { it.isNotBlank() },
            o.optString("summary", "").takeIf { it.isNotBlank() }
        )
        EazyConvTabItem(
            id = id,
            preview = pv,
            summary = sm,
            messageCount = o.optInt("message_count", 0)
        )
    }
}

/** Carousel + Functions tab: one icon per feature id (text via contentDescription only). */
private fun eazyFeatureIcon(featureId: String): ImageVector = when (featureId) {
    "interests" -> Icons.Default.Favorite
    "community" -> Icons.Default.Groups
    "generate-design" -> Icons.Default.AutoAwesome
    "my-creations" -> Icons.Default.Palette
    "publish" -> Icons.Default.Upload
    "my-products" -> Icons.Default.Inventory2
    "active-jobs" -> Icons.Default.Work
    "favorites" -> Icons.Default.Favorite
    "gift-cards" -> Icons.Default.CardGiftcard
    "promo-codes" -> Icons.Default.LocalOffer
    "size-ai" -> Icons.Default.Straighten
    "my-orders" -> Icons.Default.ReceiptLong
    "product-search" -> Icons.Default.Search
    "browse-shop" -> Icons.Default.Storefront
    "wardrobe" -> Icons.Default.Checkroom
    "my-mockups" -> Icons.Default.Collections
    "hero-images" -> Icons.Default.Image
    "creator-image" -> Icons.Default.Face
    "creator-settings" -> Icons.Default.Settings
    "balance" -> Icons.Default.AccountBalanceWallet
    "level" -> Icons.Default.TrendingUp
    "mentor-support" -> Icons.Default.VolunteerActivism
    else -> Icons.Default.Brush
}

@Composable
fun EazyChatModal(
    visible: Boolean,
    tokenStore: SecureTokenStore?,
    chatStore: EazyChatStore,
    onDismiss: () -> Unit,
    onLoginClick: () -> Unit,
    onResetMascot: () -> Unit = {},
    chatContext: EazyChatContext = EazyChatContext.Shop,
    startTab: EazySidebarTab = EazySidebarTab.Chat,
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
    val heroJob by chatStore.heroJobState.collectAsState()
    val videoJob by chatStore.videoJobState.collectAsState()
    val isLoggedIn = tokenStore?.isLoggedIn() == true
    val pagePath = if (chatContext == EazyChatContext.Creator) "/creator" else "/shop"
    val ownerId = tokenStore?.getOwnerId()

    var selectedTab by remember { mutableStateOf(EazySidebarTab.Chat) }
    var convTabs by remember { mutableStateOf<List<EazyConvTabItem>>(emptyList()) }
    var notifFilter by remember { mutableStateOf("unread") }
    var notifsUser by remember { mutableStateOf<List<EazyNotifRow>>(emptyList()) }
    var notifsSysCreator by remember { mutableStateOf<List<EazyNotifRow>>(emptyList()) }
    var notifsSysShop by remember { mutableStateOf<List<EazyNotifRow>>(emptyList()) }
    var notifFeedScope by remember { mutableStateOf("user") }
    var loadingNotifs by remember { mutableStateOf(false) }

    var jobsFeedScope by remember { mutableStateOf("user") }
    var userKvJobs by remember { mutableStateOf<List<EazyKvJobRow>>(emptyList()) }
    var systemJobs by remember { mutableStateOf<List<EazySystemJobRow>>(emptyList()) }
    var loadingJobs by remember { mutableStateOf(false) }

    val displayNotifications = remember(notifsUser, notifsSysCreator, notifsSysShop, notifFeedScope) {
        when (notifFeedScope) {
            "system" -> mergeSystemNotificationRows(notifsSysCreator, notifsSysShop)
            else -> notifsUser
        }
    }
    val totalUnreadNotifs = remember(notifsUser, notifsSysCreator, notifsSysShop) {
        notifsUser.count { !it.isRead } + notifsSysCreator.count { !it.isRead } + notifsSysShop.count { !it.isRead }
    }
    var historyOpen by remember { mutableStateOf(false) }
    var historyRows by remember { mutableStateOf<List<EazyConvTabItem>>(emptyList()) }
    var loadingHistory by remember { mutableStateOf(false) }
    var drawerExpanded by remember { mutableStateOf(false) }
    var showDeleteAllHistoryConfirm by remember { mutableStateOf(false) }
    var deleteHistoryTargetId by remember { mutableStateOf<String?>(null) }
    val tabListState = rememberLazyListState()
    val carouselScroll = rememberScrollState()

    LaunchedEffect(visible, startTab) {
        if (visible) selectedTab = startTab
    }

    LaunchedEffect(visible) {
        if (visible) chatStore.loadFnVisibilityFromStorage()
    }

    fun loadNotificationsList() {
        val oid = ownerId ?: return
        scope.launch {
            loadingNotifs = true
            try {
                val userR = withContext(Dispatchers.IO) { api.getNotifications(oid) }
                val crR = withContext(Dispatchers.IO) { api.getSystemNotifications(oid, "creator") }
                val shR = withContext(Dispatchers.IO) { api.getSystemNotifications(oid, "shop") }
                notifsUser = if (userR.optBoolean("ok", false)) {
                    parseNotifications(userR.optJSONArray("notifications") ?: JSONArray())
                } else emptyList()
                notifsSysCreator = if (crR.optBoolean("ok", false)) {
                    parseNotifications(crR.optJSONArray("notifications") ?: JSONArray(), true, "creator")
                } else emptyList()
                notifsSysShop = if (shR.optBoolean("ok", false)) {
                    parseNotifications(shR.optJSONArray("notifications") ?: JSONArray(), true, "shop")
                } else emptyList()
            } catch (_: Exception) {
                notifsUser = emptyList()
                notifsSysCreator = emptyList()
                notifsSysShop = emptyList()
            }
            loadingNotifs = false
        }
    }

    fun loadActiveTabs(uid: String) {
        scope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    api.getEazyConversation(uid, mapOf("list" to "1", "status" to "active"))
                }
                if (resp.optBoolean("ok", false)) {
                    val arr = resp.optJSONArray("conversations") ?: JSONArray()
                    convTabs = parseConvTabs(arr)
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(visible, selectedTab, ownerId) {
        if (visible && selectedTab == EazySidebarTab.Notifications && ownerId != null) {
            loadNotificationsList()
        }
    }

    LaunchedEffect(visible, selectedTab, ownerId, jobsFeedScope) {
        if (!visible || selectedTab != EazySidebarTab.Jobs || ownerId == null) return@LaunchedEffect
        val oid = ownerId ?: return@LaunchedEffect
        suspend fun loadOnce() {
            if (jobsFeedScope == "system") {
                val r1 = withContext(Dispatchers.IO) { api.listSystemJobs(oid, "creator", 50) }
                val r2 = withContext(Dispatchers.IO) { api.listSystemJobs(oid, "shop", 50) }
                val items1 = if (r1.optBoolean("ok", false)) parseSystemJobs(r1.optJSONArray("items") ?: JSONArray()) else emptyList()
                val items2 = if (r2.optBoolean("ok", false)) parseSystemJobs(r2.optJSONArray("items") ?: JSONArray()) else emptyList()
                systemJobs = mergeSystemJobRows(items1, items2)
                userKvJobs = emptyList()
            } else {
                val r = withContext(Dispatchers.IO) { api.listJobs(oid, 50) }
                if (r.optBoolean("ok", false)) {
                    userKvJobs = parseKvJobs(r.optJSONArray("items") ?: JSONArray())
                    systemJobs = emptyList()
                } else {
                    userKvJobs = emptyList()
                    systemJobs = emptyList()
                }
            }
        }
        loadingJobs = true
        try {
            try {
                loadOnce()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        } finally {
            loadingJobs = false
        }
        while (true) {
            delay(5000)
            try {
                loadOnce()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(visible, isLoggedIn, ownerId) {
        if (!visible || !isLoggedIn || ownerId == null) return@LaunchedEffect
        chatStore.setLoading(true)
        val userId = chatStore.getUserId(ownerId)
        try {
            val resp = withContext(Dispatchers.IO) {
                api.getEazyConversation(
                    userId,
                    mapOf("page" to pagePath, "auto_create" to "0")
                )
            }
            if (resp.optBoolean("ok", false)) {
                val conv = resp.optJSONObject("conversation")
                if (conv != null) {
                    val msgs = resp.optJSONArray("messages") ?: JSONArray()
                    conv.optString("id")?.let { chatStore.setConversationId(it) }
                    chatStore.setMessages(parseMessagesArray(msgs))
                } else {
                    val newR = withContext(Dispatchers.IO) { api.eazyConvNew(userId) }
                    if (newR.optBoolean("ok", false)) {
                        val c = newR.optJSONObject("conversation")
                        c?.optString("id")?.let { chatStore.setConversationId(it) }
                        chatStore.setMessages(emptyList())
                    }
                }
            }
        } catch (_: Exception) {}
        chatStore.setLoading(false)
        loadActiveTabs(userId)
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
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .width(56.dp)
                            .fillMaxSize()
                            .background(ChatHeader)
                            .border(1.dp, ChatMuted.copy(alpha = 0.3f))
                    ) {
                        listOf(
                            EazySidebarTab.Chat to Icons.Default.Chat,
                            EazySidebarTab.Notifications to Icons.Default.Notifications,
                            EazySidebarTab.Jobs to Icons.Default.Bolt,
                            EazySidebarTab.Settings to Icons.Default.Settings,
                            EazySidebarTab.Functions to Icons.Default.Build,
                            EazySidebarTab.Mascot to Icons.Default.Pets
                        ).forEach { (tab, icon) ->
                            val unreadCount = totalUnreadNotifs
                            Box {
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
                                if (tab == EazySidebarTab.Notifications && unreadCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(ChatAccent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (unreadCount > 99) "99+" else "$unreadCount",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f).fillMaxSize().background(ChatBg)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ChatHeader)
                                .padding(horizontal = 8.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectedTab == EazySidebarTab.Jobs) {
                                Box(
                                    modifier = Modifier.width(48.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Bolt,
                                        contentDescription = null,
                                        tint = ChatAccent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.width(48.dp))
                            }
                            Text(
                                text = when (selectedTab) {
                                    EazySidebarTab.Chat -> t("eazy_chat.ui_chat_title", "eazy")
                                    EazySidebarTab.Notifications -> t("creator.notifications.notifications_tab", "Notifications")
                                    EazySidebarTab.Jobs -> t("creator.notifications.active_jobs", "Active Jobs")
                                    EazySidebarTab.Settings -> t("eazy_chat.ui_settings_tab", "Settings")
                                    EazySidebarTab.Functions -> t("eazy_chat.ui_functions_tab", "Functions")
                                    EazySidebarTab.Mascot -> t("eazy_chat.ui_mascot_tab", "Mascot")
                                },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium,
                                color = ChatText,
                                textAlign = TextAlign.Center
                            )
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = t("eazy_chat.ui_close_chat", "Close"), tint = ChatText)
                            }
                        }

                        when (selectedTab) {
                            EazySidebarTab.Chat -> {
                                if (!isLoggedIn) {
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
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(ChatHeader.copy(alpha = 0.5f))
                                                .padding(horizontal = 4.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            LazyRow(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(44.dp),
                                                state = tabListState,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                items(convTabs, key = { it.id }) { tab ->
                                                    val newChatFb = t("eazy_chat.tab_new_chat", "Chat")
                                                    val label = tabStripLabel(tab.preview, tab.summary, newChatFb)
                                                    val active = tab.id == conversationId
                                                    Row(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(if (active) ChatAccent.copy(alpha = 0.25f) else Color.Transparent)
                                                            .clickable {
                                                                scope.launch {
                                                                    val u = chatStore.getUserId(ownerId)
                                                                    chatStore.setLoading(true)
                                                                    try {
                                                                        val resp = withContext(Dispatchers.IO) {
                                                                            api.getEazyConversation(u, mapOf("conv_id" to tab.id))
                                                                        }
                                                                        if (resp.optBoolean("ok", false)) {
                                                                            val conv = resp.optJSONObject("conversation")
                                                                            val msgs = resp.optJSONArray("messages") ?: JSONArray()
                                                                            conv?.optString("id")?.let { chatStore.setConversationId(it) }
                                                                            chatStore.setMessages(parseMessagesArray(msgs))
                                                                        }
                                                                    } catch (_: Exception) {}
                                                                    chatStore.setLoading(false)
                                                                }
                                                            }
                                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = label,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = if (active) ChatAccent else ChatText,
                                                            modifier = Modifier.widthIn(max = 120.dp)
                                                        )
                                                        IconButton(
                                                            onClick = {
                                                                scope.launch {
                                                                    val u = chatStore.getUserId(ownerId)
                                                                    withContext(Dispatchers.IO) {
                                                                        api.eazyConvClose(u, tab.id)
                                                                    }
                                                                    val remaining = convTabs.filter { it.id != tab.id }
                                                                    convTabs = remaining
                                                                    when {
                                                                        tab.id == conversationId && remaining.isNotEmpty() -> {
                                                                            val next = remaining.first().id
                                                                            val resp = withContext(Dispatchers.IO) {
                                                                                api.getEazyConversation(u, mapOf("conv_id" to next))
                                                                            }
                                                                            if (resp.optBoolean("ok", false)) {
                                                                                val conv = resp.optJSONObject("conversation")
                                                                                val msgs = resp.optJSONArray("messages") ?: JSONArray()
                                                                                conv?.optString("id")?.let { chatStore.setConversationId(it) }
                                                                                chatStore.setMessages(parseMessagesArray(msgs))
                                                                            }
                                                                        }
                                                                        remaining.isEmpty() -> {
                                                                            chatStore.clearMessages()
                                                                            val newR = withContext(Dispatchers.IO) { api.eazyConvNew(u) }
                                                                            if (newR.optBoolean("ok", false)) {
                                                                                val c = newR.optJSONObject("conversation")
                                                                                val nid = c?.optString("id")
                                                                                if (!nid.isNullOrBlank()) {
                                                                                    chatStore.setConversationId(nid)
                                                                                    chatStore.setMessages(emptyList())
                                                                                    convTabs = listOf(
                                                                                        EazyConvTabItem(id = nid, preview = null, summary = null)
                                                                                    )
                                                                                }
                                                                            }
                                                                            loadActiveTabs(u)
                                                                        }
                                                                    }
                                                                }
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(Icons.Default.Close, contentDescription = t("chatCloseTitle", "Close chat"), tint = ChatMuted, modifier = Modifier.size(14.dp))
                                                        }
                                                    }
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        val u = chatStore.getUserId(ownerId)
                                                        val newR = withContext(Dispatchers.IO) { api.eazyConvNew(u) }
                                                        if (newR.optBoolean("ok", false)) {
                                                            val c = newR.optJSONObject("conversation")
                                                            val id = c?.optString("id") ?: return@launch
                                                            chatStore.setConversationId(id)
                                                            chatStore.setMessages(emptyList())
                                                            convTabs = listOf(EazyConvTabItem(id, null, null)) + convTabs.filter { it.id != id }
                                                            loadActiveTabs(u)
                                                        }
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = t("eazy_chat.new_chat", "New chat"), tint = ChatAccent)
                                            }
                                            IconButton(onClick = {
                                                historyOpen = true
                                                scope.launch {
                                                    loadingHistory = true
                                                    val u = chatStore.getUserId(ownerId)
                                                    try {
                                                        val resp = withContext(Dispatchers.IO) {
                                                            api.getEazyConversation(u, mapOf("list" to "1", "status" to "closed"))
                                                        }
                                                        if (resp.optBoolean("ok", false)) {
                                                            val arr = resp.optJSONArray("conversations") ?: JSONArray()
                                                            historyRows = parseConvTabs(arr)
                                                        } else historyRows = emptyList()
                                                    } catch (_: Exception) {
                                                        historyRows = emptyList()
                                                    }
                                                    loadingHistory = false
                                                }
                                            }) {
                                                Icon(Icons.Default.History, contentDescription = t("eazy_chat.ui_chat_history", "Chat history"), tint = ChatAccent)
                                            }
                                        }

                                        AnimatedVisibility(drawerExpanded) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .background(ChatHeader)
                                                    .padding(8.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    IconButton(onClick = {
                                                        scope.launch {
                                                            carouselScroll.scrollTo((carouselScroll.value - 200).coerceAtLeast(0))
                                                        }
                                                    }) {
                                                        Text("\u2039", color = ChatText)
                                                    }
                                                    Row(
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .horizontalScroll(carouselScroll)
                                                    ) {
                                                        val defs = EazyChatFeatureCatalog.forContext(chatContext).filter { chatStore.isFeatureInCarousel(it.id) }
                                                        defs.forEach { def ->
                                                            val cd = t(def.labelKey, def.defaultLabel)
                                                            Box(
                                                                modifier = Modifier
                                                                    .padding(horizontal = 4.dp)
                                                                    .size(48.dp)
                                                                    .clip(RoundedCornerShape(10.dp))
                                                                    .border(1.dp, ChatMuted.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                                                    .clickable {
                                                                        drawerExpanded = false
                                                                        scope.launch {
                                                                            chatStore.setTyping(true)
                                                                            val u = chatStore.getUserId(ownerId)
                                                                            val msgList = chatStore.messages.value.map { it.role to it.content }
                                                                            try {
                                                                                val resp = withContext(Dispatchers.IO) {
                                                                                    api.chatCompletion(
                                                                                        userId = u,
                                                                                        messages = msgList,
                                                                                        conversationId = chatStore.conversationId.value,
                                                                                        context = mapOf(
                                                                                            "page" to pagePath,
                                                                                            "locale" to java.util.Locale.getDefault().language
                                                                                        ),
                                                                                        functionTrigger = def.id
                                                                                    )
                                                                                }
                                                                                chatStore.setTyping(false)
                                                                                val rl = resp.optJSONObject("rate_limit")
                                                                                if (rl != null) {
                                                                                    chatStore.setRateLimit(
                                                                                        RateLimitState(
                                                                                            remaining = rl.optInt("remaining", 30),
                                                                                            limit = rl.optInt("limit", 30),
                                                                                            resetAt = rl.optLong("reset_at", 0),
                                                                                            resetIn = rl.optInt("reset_in", 0)
                                                                                        )
                                                                                    )
                                                                                    if (rl.optInt("remaining", 30) <= 0) chatStore.setLimitReached(true)
                                                                                }
                                                                                if (resp.optBoolean("ok", false)) {
                                                                                    val reply = resp.optString("text", "")
                                                                                    if (reply.isNotBlank()) {
                                                                                        chatStore.addMessage(ChatMessage("a${System.currentTimeMillis()}", "assistant", reply))
                                                                                        resp.optString("conversation_id", "").takeIf { it.isNotBlank() }?.let { chatStore.setConversationId(it) }
                                                                                    }
                                                                                }
                                                                            } catch (_: Exception) {
                                                                                chatStore.setTyping(false)
                                                                            }
                                                                        }
                                                                    }
                                                                    .padding(8.dp),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                Icon(
                                                                    eazyFeatureIcon(def.id),
                                                                    contentDescription = cd,
                                                                    tint = ChatText,
                                                                    modifier = Modifier.size(26.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    IconButton(onClick = {
                                                        scope.launch {
                                                            carouselScroll.scrollTo(carouselScroll.value + 200)
                                                        }
                                                    }) {
                                                        Text("\u203A", color = ChatText)
                                                    }
                                                }
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { drawerExpanded = !drawerExpanded }
                                                .background(ChatHeader)
                                                .padding(vertical = 6.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(40.dp)
                                                        .height(4.dp)
                                                        .clip(RoundedCornerShape(2.dp))
                                                        .background(ChatMuted)
                                                )
                                                Icon(
                                                    imageVector = if (drawerExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = null,
                                                    tint = ChatMuted,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }

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
                                                ChatBubble(message = msg, isUser = msg.role == "user")
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

                                        var inputText by remember { mutableStateOf("") }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .navigationBarsPadding()
                                                .imePadding()
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
                                                        val u = chatStore.getUserId(ownerId)
                                                        val msgList = chatStore.messages.value.map { it.role to it.content }
                                                        try {
                                                            val resp = withContext(Dispatchers.IO) {
                                                                api.chatCompletion(
                                                                    userId = u,
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
                                                                chatStore.setRateLimit(
                                                                    RateLimitState(
                                                                        remaining = rl.optInt("remaining", 30),
                                                                        limit = rl.optInt("limit", 30),
                                                                        resetAt = rl.optLong("reset_at", 0),
                                                                        resetIn = rl.optInt("reset_in", 0)
                                                                    )
                                                                )
                                                                if (rl.optInt("remaining", 30) <= 0) chatStore.setLimitReached(true)
                                                            }
                                                            if (resp.optBoolean("ok", false)) {
                                                                val reply = resp.optString("text", "")
                                                                if (reply.isNotBlank()) {
                                                                    chatStore.addMessage(ChatMessage("a${System.currentTimeMillis()}", "assistant", reply))
                                                                    resp.optString("conversation_id", "").takeIf { it.isNotBlank() }?.let { chatStore.setConversationId(it) }
                                                                }
                                                            }
                                                            conversationId?.let { cid ->
                                                                val newChatFb = t("eazy_chat.tab_new_chat", "Chat")
                                                                convTabs = convTabs.map { tab ->
                                                                    val emptyPreview = tab.preview == null || !isUsableTabText(tab.preview)
                                                                    val wasPlaceholder = tab.preview?.trim() == newChatFb
                                                                    if (tab.id == cid && (emptyPreview || wasPlaceholder)) {
                                                                        tab.copy(preview = text.take(60))
                                                                    } else tab
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
                            }

                            EazySidebarTab.Notifications -> EazyNotificationsPanel(
                                loading = loadingNotifs,
                                notifFilter = notifFilter,
                                onFilterChange = { notifFilter = it },
                                notifications = displayNotifications,
                                notifFeedScope = notifFeedScope,
                                onNotifFeedScopeChange = { notifFeedScope = it },
                                t = t,
                                onMarkRead = { row ->
                                    val oid = ownerId ?: return@EazyNotificationsPanel
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            if (row.isSystem) api.markSystemNotificationRead(oid, row.id)
                                            else api.markNotificationRead(oid, row.id)
                                        }
                                        loadNotificationsList()
                                    }
                                }
                            )

                            EazySidebarTab.Jobs -> EazyJobsCombinedPanel(
                                hero = heroJob,
                                video = videoJob,
                                jobsFeedScope = jobsFeedScope,
                                onJobsFeedScopeChange = { jobsFeedScope = it },
                                userKvJobs = userKvJobs,
                                systemJobs = systemJobs,
                                loadingJobs = loadingJobs,
                                t = t
                            )
                            EazySidebarTab.Settings -> EazySettingsView(t, onDismiss, onResetMascot)
                            EazySidebarTab.Functions -> {
                                if (!isLoggedIn) {
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
                                } else {
                                    EazyFunctionsGrid(
                                        chatContext = chatContext,
                                        chatStore = chatStore,
                                        t = t,
                                        onToggle = { fid ->
                                            chatStore.toggleFeatureCarouselVisibility(fid)
                                            scope.launch { chatStore.persistFnVisibility() }
                                        },
                                        onCategoryToggle = { ids, vis ->
                                            chatStore.setCategoryCarouselVisibility(ids, vis)
                                            scope.launch { chatStore.persistFnVisibility() }
                                        },
                                        onRunFeature = { fid ->
                                            scope.launch {
                                                chatStore.setTyping(true)
                                                val u = chatStore.getUserId(ownerId)
                                                val msgList = chatStore.messages.value.map { it.role to it.content }
                                                try {
                                                    val resp = withContext(Dispatchers.IO) {
                                                        api.chatCompletion(
                                                            userId = u,
                                                            messages = msgList,
                                                            conversationId = chatStore.conversationId.value,
                                                            context = mapOf(
                                                                "page" to pagePath,
                                                                "locale" to java.util.Locale.getDefault().language
                                                            ),
                                                            functionTrigger = fid
                                                        )
                                                    }
                                                    chatStore.setTyping(false)
                                                    if (resp.optBoolean("ok", false)) {
                                                        val reply = resp.optString("text", "")
                                                        if (reply.isNotBlank()) {
                                                            chatStore.addMessage(ChatMessage("a${System.currentTimeMillis()}", "assistant", reply))
                                                            resp.optString("conversation_id", "").takeIf { it.isNotBlank() }?.let { chatStore.setConversationId(it) }
                                                        }
                                                    }
                                                } catch (_: Exception) {
                                                    chatStore.setTyping(false)
                                                }
                                            }
                                            selectedTab = EazySidebarTab.Chat
                                        }
                                    )
                                }
                            }

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

    if (historyOpen) {
        Dialog(onDismissRequest = { historyOpen = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ChatHeader)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(t("eazy_chat.ui_chat_history", "Chat history"), color = ChatText, style = MaterialTheme.typography.titleMedium)
                    Row {
                        TextButton(onClick = { showDeleteAllHistoryConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = ChatMuted, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { historyOpen = false }) {
                            Icon(Icons.Default.Close, contentDescription = t("eazy_chat.ui_close", "Close"), tint = ChatText)
                        }
                    }
                }
                Divider(color = ChatMuted.copy(alpha = 0.3f))
                if (loadingHistory) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = ChatAccent)
                    }
                } else if (historyRows.isEmpty()) {
                    Text(
                        t("eazy_chat.history_empty", "No past chats available."),
                        color = ChatMuted,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 400.dp)
                    ) {
                        items(historyRows, key = { it.id }) { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        scope.launch {
                                            val u = chatStore.getUserId(ownerId)
                                            val resp = withContext(Dispatchers.IO) {
                                                api.eazyConvReopen(u, row.id)
                                            }
                                            if (resp.optBoolean("ok", false)) {
                                                historyOpen = false
                                                val r2 = withContext(Dispatchers.IO) {
                                                    api.getEazyConversation(u, mapOf("conv_id" to row.id))
                                                }
                                                if (r2.optBoolean("ok", false)) {
                                                    val conv = r2.optJSONObject("conversation")
                                                    val msgs = r2.optJSONArray("messages") ?: JSONArray()
                                                    conv?.optString("id")?.let { chatStore.setConversationId(it) }
                                                    chatStore.setMessages(parseMessagesArray(msgs))
                                                }
                                                loadActiveTabs(u)
                                                selectedTab = EazySidebarTab.Chat
                                            }
                                        }
                                    }
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        tabStripLabel(row.preview, row.summary, "Chat"),
                                        color = ChatText,
                                        maxLines = 2
                                    )
                                    Text(
                                        "${row.messageCount} ${t("eazy_chat.ui_messages", "messages")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ChatMuted
                                    )
                                }
                                IconButton(onClick = { deleteHistoryTargetId = row.id }) {
                                    Icon(Icons.Default.Delete, contentDescription = t("chatDelete", "Delete"), tint = ChatMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteAllHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAllHistoryConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAllHistoryConfirm = false
                    scope.launch {
                        val u = chatStore.getUserId(ownerId)
                        withContext(Dispatchers.IO) { api.eazyConvDeleteHistory(u) }
                        historyRows = emptyList()
                    }
                }) { Text(t("eazy_chat.confirm_delete", "Delete")) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllHistoryConfirm = false }) { Text(t("eazy_chat.ui_close", "Close")) }
            },
            title = { Text(t("chatDeleteHistoryConfirm", "Delete complete chat history permanently? Open chats remain."), color = ChatText) }
        )
    }

    deleteHistoryTargetId?.let { targetId ->
        AlertDialog(
            onDismissRequest = { deleteHistoryTargetId = null },
            confirmButton = {
                TextButton(onClick = {
                    val id = deleteHistoryTargetId
                    deleteHistoryTargetId = null
                    if (id != null) {
                        scope.launch {
                            val u = chatStore.getUserId(ownerId)
                            withContext(Dispatchers.IO) { api.eazyConvDelete(u, id) }
                            historyRows = historyRows.filter { it.id != id }
                        }
                    }
                }) { Text(t("eazy_chat.confirm_delete", "Delete")) }
            },
            dismissButton = {
                TextButton(onClick = { deleteHistoryTargetId = null }) { Text(t("eazy_chat.ui_close", "Close")) }
            },
            title = { Text(t("chatDeleteChatConfirm", "Delete this chat permanently?"), color = ChatText) }
        )
    }
}

@Composable
private fun EazyNotificationsPanel(
    loading: Boolean,
    notifFilter: String,
    onFilterChange: (String) -> Unit,
    notifications: List<EazyNotifRow>,
    notifFeedScope: String,
    onNotifFeedScopeChange: (String) -> Unit,
    t: (String, String) -> String,
    onMarkRead: (EazyNotifRow) -> Unit
) {
    val unread = notifications.filter { !it.isRead }
    val read = notifications.filter { it.isRead }
    val shown = if (notifFilter == "unread") unread else read
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = { onNotifFeedScopeChange("user") },
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = if (notifFeedScope == "user") ChatAccent else ChatMuted
                )
            ) {
                Text(t("creator.notifications.feed_user", "User"))
            }
            TextButton(
                onClick = { onNotifFeedScopeChange("system") },
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = if (notifFeedScope == "system") ChatAccent else ChatMuted
                )
            ) {
                Text(t("creator.notifications.feed_system", "System"))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = { onFilterChange("unread") },
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = if (notifFilter == "unread") ChatAccent else ChatMuted
                )
            ) {
                Text(t("creator.notifications.unread", "Unread"))
            }
            TextButton(
                onClick = { onFilterChange("read") },
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = if (notifFilter == "read") ChatAccent else ChatMuted
                )
            ) {
                Text(t("creator.notifications.read", "Read"))
            }
        }
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ChatAccent)
            }
        } else if (shown.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = if (notifFilter == "unread") {
                        t("chat_notifications_none_unread", "No unread notifications")
                    } else {
                        t("chat_notifications_none_read", "No read notifications")
                    },
                    color = ChatMuted,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(shown, key = { it.id }) { n ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!n.isRead) ChatAccent.copy(alpha = 0.12f) else ChatMuted.copy(alpha = 0.08f))
                            .clickable {
                                if (!n.isRead) onMarkRead(n)
                            }
                            .padding(12.dp)
                    ) {
                        Text(n.title, style = MaterialTheme.typography.titleSmall, color = ChatText)
                        if (n.message.isNotBlank()) {
                            Text(n.message, style = MaterialTheme.typography.bodySmall, color = ChatMuted)
                        }
                        n.createdAt?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = ChatMuted.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EazyFunctionsGrid(
    chatContext: EazyChatContext,
    chatStore: EazyChatStore,
    t: (String, String) -> String,
    onToggle: (String) -> Unit,
    onCategoryToggle: (List<String>, Boolean) -> Unit,
    onRunFeature: (String) -> Unit
) {
    val categories = listOf(
        EazyFeatureCategory.Shared to EazyChatFeatureCatalog.forContext(chatContext).filter { it.category == EazyFeatureCategory.Shared },
        EazyFeatureCategory.Shop to EazyChatFeatureCatalog.forContext(EazyChatContext.Shop).filter { it.category == EazyFeatureCategory.Shop },
        EazyFeatureCategory.Creator to EazyChatFeatureCatalog.forContext(EazyChatContext.Creator).filter { it.category == EazyFeatureCategory.Creator }
    ).filter { it.second.isNotEmpty() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        categories.forEach { (cat, defs) ->
            val ids = defs.map { it.id }
            val allVis = ids.all { chatStore.isFeatureInCarousel(it) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    EazyChatFeatureCatalog.categoryLabel(cat, t),
                    style = MaterialTheme.typography.titleSmall,
                    color = ChatAccent
                )
                TextButton(onClick = { onCategoryToggle(ids, !allVis) }) {
                    Text(if (allVis) t("eazy_fn.hide_all", "Hide all") else t("eazy_fn.show_all", "Show all"), color = ChatMuted)
                }
            }
            defs.chunked(2).forEach { rowDefs ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowDefs.forEach { def ->
                        val vis = chatStore.isFeatureInCarousel(def.id)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 72.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, ChatMuted.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .clickable { onRunFeature(def.id) }
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = eazyFeatureIcon(def.id),
                                contentDescription = t(def.labelKey, def.defaultLabel),
                                tint = ChatText,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(36.dp)
                            )
                            IconButton(
                                onClick = { onToggle(def.id) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (vis) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = ChatMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    if (rowDefs.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
            Divider(color = ChatMuted.copy(alpha = 0.2f))
        }
        Text(
            t("eazy_fn.hint", "Eye: show or hide shortcuts in the chat carousel."),
            style = MaterialTheme.typography.labelSmall,
            color = ChatMuted
        )
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
private fun EazyJobsCombinedPanel(
    hero: HeroJobState?,
    video: VideoJobState?,
    jobsFeedScope: String,
    onJobsFeedScopeChange: (String) -> Unit,
    userKvJobs: List<EazyKvJobRow>,
    systemJobs: List<EazySystemJobRow>,
    loadingJobs: Boolean,
    t: (String, String) -> String
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(
                onClick = { onJobsFeedScopeChange("user") },
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = if (jobsFeedScope == "user") ChatAccent else ChatMuted
                )
            ) {
                Text(t("creator.notifications.feed_user", "User"))
            }
            TextButton(
                onClick = { onJobsFeedScopeChange("system") },
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = if (jobsFeedScope == "system") ChatAccent else ChatMuted
                )
            ) {
                Text(t("creator.notifications.feed_system", "System"))
            }
        }
        when {
            loadingJobs -> Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ChatAccent)
            }
            jobsFeedScope == "system" -> {
                if (systemJobs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(t("eazy_chat.chat_no_active_jobs", "No active jobs"), color = ChatMuted)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(systemJobs, key = { it.sessionId }) { j ->
                            val st = j.status.lowercase()
                            val prog = when {
                                st.contains("complete") -> 100
                                st.contains("fail") || st.contains("cancel") -> 0
                                else -> 50
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ChatMuted.copy(alpha = 0.08f))
                                    .padding(12.dp)
                            ) {
                                Text(j.title, style = MaterialTheme.typography.titleSmall, color = ChatText)
                                LinearProgressIndicator(
                                    progress = prog.coerceIn(0, 100) / 100f,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = ChatAccent,
                                    trackColor = ChatMuted.copy(alpha = 0.3f)
                                )
                                j.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ChatMuted) }
                            }
                        }
                    }
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    EazyHeroJobsPanel(hero, video, t)
                    if (userKvJobs.isNotEmpty()) {
                        Text(
                            t("creator.notifications.active_jobs", "Active Jobs"),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = ChatMuted
                        )
                        userKvJobs.forEach { j ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 6.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ChatMuted.copy(alpha = 0.08f))
                                    .padding(12.dp)
                            ) {
                                Text(j.title, style = MaterialTheme.typography.bodyMedium, color = ChatText)
                                LinearProgressIndicator(
                                    progress = (if (j.done) 100 else j.progress).coerceIn(0, 100) / 100f,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = ChatAccent,
                                    trackColor = ChatMuted.copy(alpha = 0.3f)
                                )
                                j.status?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = ChatMuted) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EazyHeroJobsPanel(
    hero: HeroJobState?,
    video: VideoJobState?,
    t: (String, String) -> String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val activeHero = hero?.takeIf { it.isActive }
        val activeVideo = video?.takeIf { it.isActive }
        if (activeHero != null) {
            Text(
                text = t("creator.hero_eazy.job_summary_title", "Hero image generation"),
                style = MaterialTheme.typography.labelMedium,
                color = ChatMuted
            )
            Text(
                text = activeHero.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = ChatText
            )
            LinearProgressIndicator(
                progress = activeHero.progress.coerceIn(0, 100) / 100f,
                modifier = Modifier.fillMaxWidth(),
                color = ChatAccent,
                trackColor = ChatMuted.copy(alpha = 0.3f)
            )
            activeHero.message?.takeIf { it.isNotBlank() }?.let { msg ->
                Text(text = msg, style = MaterialTheme.typography.bodySmall, color = ChatMuted)
            }
        }
        if (activeVideo != null) {
            Text(
                text = t("creator.content_creation.videos.job_summary_title", "Video generation"),
                style = MaterialTheme.typography.labelMedium,
                color = ChatMuted
            )
            Text(
                text = activeVideo.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = ChatText
            )
            LinearProgressIndicator(
                progress = activeVideo.progress.coerceIn(0, 100) / 100f,
                modifier = Modifier.fillMaxWidth(),
                color = ChatAccent,
                trackColor = ChatMuted.copy(alpha = 0.3f)
            )
            activeVideo.message?.takeIf { it.isNotBlank() }?.let { msg ->
                Text(text = msg, style = MaterialTheme.typography.bodySmall, color = ChatMuted)
            }
        }
        if (activeHero == null && activeVideo == null) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = null,
                    tint = ChatMuted,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "No active jobs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ChatMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
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
