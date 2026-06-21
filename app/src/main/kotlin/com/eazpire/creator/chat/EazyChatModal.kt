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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.Work
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import com.eazpire.creator.ui.modal.EazBottomSheet
import com.eazpire.creator.ui.modal.EazModalFooterSurface
import com.eazpire.creator.ui.modal.EazModalSheetLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.eazpire.creator.ui.modal.EazInsetDialog
import kotlinx.coroutines.CoroutineScope
import coil.compose.AsyncImage
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.LocalTranslationStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

enum class EazySidebarTab { Chat, Notifications, Jobs, Settings, Games, Artifacts, Verify, Functions, Mascot }

enum class EazyChatContext { Shop, Creator }

private val EazyChatSidebarGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFFF97316), Color(0xFFEA580C))
)

private fun sidebarTabLabel(tab: EazySidebarTab, t: (String, String) -> String): String = when (tab) {
    EazySidebarTab.Chat -> t("eazy_chat.ui_chat_title", "eazy")
    EazySidebarTab.Notifications -> t("creator.notifications.notifications_tab", "Notifications")
    EazySidebarTab.Jobs -> t("creator.notifications.active_jobs", "Active Jobs")
    EazySidebarTab.Settings -> t("eazy_chat.ui_settings_tab", "Settings")
    EazySidebarTab.Games -> t("eazy_chat.ui_games_tab", "Games")
    EazySidebarTab.Artifacts -> t("eazy_chat.ui_artifacts_tab", "Artifacts")
    EazySidebarTab.Verify -> t("eazy_chat.ui_verify_tab", "Verify")
    EazySidebarTab.Functions -> t("eazy_chat.ui_functions_tab", "Functions")
    EazySidebarTab.Mascot -> t("eazy_chat.ui_mascot_tab", "Mascot")
}

private fun formatResetTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%d:%02d".format(m, s)
}

private data class EazyConvTabItem(
    val id: String,
    val preview: String?,
    val summary: String?,
    val messageCount: Int = 0,
    val mode: String = "ai",
    val supportStatus: String? = null,
)

private data class EazyNotifRow(
    val id: String,
    val title: String,
    val message: String,
    val isRead: Boolean,
    val createdAt: String?,
    val category: String?,
    val isSystem: Boolean = false,
    val systemAudience: String? = null,
    /** From notification `data` JSON: design/hero/product/job preview when available. */
    val previewImageUrl: String? = null,
    /** Creator Code invite: prefill redeem field when opening Creator Settings. */
    val creatorCodePrefill: String? = null,
    val opensCreatorCodes: Boolean = false,
)

private data class EazySystemJobRow(
    val sessionId: String,
    val title: String,
    val status: String,
    val message: String?,
    val jobKind: String,
    val subtitleDetail: String,
    val progress: Int,
    val thumbUrl: String?,
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

private fun String?.asHttpImageUrl(): String? {
    val s = this?.trim() ?: return null
    if (s.length < 8) return null
    return when {
        s.startsWith("http://", ignoreCase = true) || s.startsWith("https://", ignoreCase = true) -> s
        else -> null
    }
}

private fun JSONObject.firstHttpUrl(vararg keys: String): String? {
    for (k in keys) {
        optString(k, "").asHttpImageUrl()?.let { return it }
    }
    return null
}

/**
 * Reads `data` from user or system notification rows (string JSON or object).
 */
private fun extractNotificationDataObject(notification: JSONObject): JSONObject? {
    if (!notification.has("data") || notification.isNull("data")) return null
    return when (val d = notification.get("data")) {
        is JSONObject -> d
        is String -> try {
            JSONObject(d)
        } catch (_: Exception) {
            null
        }
        else -> null
    }
}

private fun isCreatorCodeNotificationCategory(category: String?): Boolean {
    val c = category?.lowercase()?.trim().orEmpty()
    return c.startsWith("creator_code")
}

/**
 * Reads `data` from user or system notification rows (string JSON or object).
 * Covers design/hero/video/product jobs: thumbnail_url, preview_url, result.*, product_image_url, etc.
 */
private fun extractNotificationPreviewUrl(notification: JSONObject): String? {
    val data = extractNotificationDataObject(notification) ?: return null

    data.firstHttpUrl(
        "thumbnail_url",
        "preview_url",
        "image_url",
        "product_image_url",
        "hero_image_url",
        "video_thumbnail_url",
        "user_image_url",
        "reference_image_url"
    )?.let { return it }

    data.optJSONObject("result")?.firstHttpUrl("preview_url", "image_url", "thumbnail_url", "url", "public_url", "file_url")
        ?.let { return it }

    data.optJSONArray("product_image_urls")?.takeIf { it.length() > 0 }?.optString(0)?.asHttpImageUrl()?.let { return it }
    data.optJSONArray("images")?.takeIf { it.length() > 0 }?.optString(0)?.asHttpImageUrl()?.let { return it }

    data.optJSONObject("metadata")?.firstHttpUrl("preview_url", "image_url", "thumbnail_url")?.let { return it }

    data.optJSONObject("result")?.optJSONObject("metadata")?.firstHttpUrl("preview_url", "image_url")
        ?.let { return it }

    return null
}

private fun extractJobIdFromNotificationId(notificationId: String): String? {
    val raw = notificationId.removePrefix("api-")
    if (raw.startsWith("generated-")) return raw.removePrefix("generated-")
    if (raw.startsWith("saved-")) return raw.removePrefix("saved-")
    return null
}

private fun pendingSaveJobIdsFromKv(jobsR: JSONObject): Set<String> {
    val pending = mutableSetOf<String>()
    if (!jobsR.optBoolean("ok", false)) return pending
    (jobsR.optJSONArray("items") ?: JSONArray()).let { arr ->
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val jid = o.optString("job_id", "").trim()
            if (jid.isBlank()) continue
            if (o.optBoolean("done", false) && !o.optBoolean("saved", false)) pending.add(jid)
        }
    }
    return pending
}

private fun shouldHideGeneratedNotification(row: EazyNotifRow, pendingSave: Set<String>): Boolean {
    val cat = row.category?.lowercase()?.trim().orEmpty()
    if (cat != "generated") return false
    val jid = extractJobIdFromNotificationId(row.id) ?: return false
    return pendingSave.contains(jid)
}

private fun parseNotifications(
    arr: JSONArray,
    isSystem: Boolean = false,
    systemAudience: String? = null
): List<EazyNotifRow> {
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val id = o.optString("notification_id", o.optString("id", "")).ifBlank { return@mapNotNull null }
        val cat = o.optString("category", o.optString("event_type", "")).takeIf { it.isNotBlank() }
        val isCreatorCode = isCreatorCodeNotificationCategory(cat)
        val dataObj = extractNotificationDataObject(o)
        val prefill = if (isCreatorCode) {
            dataObj?.optString("code", "")?.trim()?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        EazyNotifRow(
            id = id,
            title = o.optString("title", "").ifBlank { "Notification" },
            message = o.optString("message", ""),
            isRead = o.notificationIsRead(),
            createdAt = o.optString("created_at", "").takeIf { it.isNotBlank() },
            category = cat,
            isSystem = isSystem,
            systemAudience = systemAudience,
            previewImageUrl = extractNotificationPreviewUrl(o),
            creatorCodePrefill = prefill,
            opensCreatorCodes = isCreatorCode,
        )
    }
}

private fun parseKvJobs(arr: JSONArray): List<EazyKvJobRow> {
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val id = o.optString("job_id", o.optString("id", "")).ifBlank { return@mapNotNull null }
        val saving = o.optBoolean("saving", false)
        val done = o.optBoolean("done", false)
        val saved = o.optBoolean("saved", false)
        var progress = o.optInt("progress", 0).coerceIn(0, 100)
        if (saving) progress = maxOf(progress, 90).coerceIn(0, 99)
        else if (done && !saved && progress < 100) progress = maxOf(progress, 90)
        val clientDevice = o.optString("client_device", "")
            .ifBlank { o.optString("source", "") }
        val type = o.optString("type", o.optString("action", ""))
        val isWear = clientDevice.equals("wear", ignoreCase = true) ||
            type.contains("wear", ignoreCase = true)
        EazyKvJobRow(
            id = id,
            title = o.optString("prompt", o.optString("title", "")).ifBlank { id },
            progress = progress,
            done = done,
            saving = saving,
            saved = saved,
            isWear = isWear,
            status = o.optString("status", "").takeIf { it.isNotBlank() },
            message = o.optString("message", "").takeIf { it.isNotBlank() },
        )
    }.filter { !it.done || (it.saving && !it.saved) }
}

private fun parseSystemJobs(arr: JSONArray): List<EazySystemJobRow> {
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val sid = o.optString("session_id", "").ifBlank { return@mapNotNull null }
        val effMsg = sequenceOf(
            o.optString("effective_message", ""),
            o.optString("message", ""),
            o.optString("error_message", ""),
            o.optString("summary", ""),
        ).firstOrNull { it.isNotBlank() }
        val msg = effMsg?.takeIf { it.isNotBlank() }
        val thumbRaw = o.optString("effective_preview_url", "").asHttpImageUrl()
        val kind = o.optString("job_kind", "system_publish").ifBlank { "system_publish" }
        EazySystemJobRow(
            sessionId = sid,
            title = o.optString("title", "").ifBlank { "System publish" },
            status = o.optString("status", ""),
            message = msg,
            jobKind = kind,
            subtitleDetail = o.optString("subtitle_detail", ""),
            progress = o.optInt("effective_progress", 55).coerceIn(0, 100),
            thumbUrl = thumbRaw,
        )
    }
}

/** System notifications: one combined list (creator + shop APIs), deduped by id. */
private fun mergeSystemNotificationRows(a: List<EazyNotifRow>, b: List<EazyNotifRow>): List<EazyNotifRow> {
    val merged = mutableMapOf<String, EazyNotifRow>()
    for (n in a + b) {
        val existing = merged[n.id]
        if (existing == null) {
            merged[n.id] = n
        } else {
            val newer = if ((n.createdAt ?: "") >= (existing.createdAt ?: "")) n else existing
            val older = if (newer === n) existing else n
            merged[n.id] = newer.copy(previewImageUrl = newer.previewImageUrl ?: older.previewImageUrl)
        }
    }
    return merged.values.sortedWith(compareByDescending { it.createdAt ?: "" })
}

/** System jobs: creator + shop lists merged, deduped by session id (no Creator/Shop tabs in UI). */
private fun mergeSystemJobRows(a: List<EazySystemJobRow>, b: List<EazySystemJobRow>): List<EazySystemJobRow> {
    val seen = mutableSetOf<String>()
    return (a + b).filter { seen.add(it.sessionId) }
}

/** Avoid duplicate rows when the same job_id is shown in the local async overlay (hero/video/design). */
private fun filterKvJobsForLocalOverlay(
    jobs: List<EazyKvJobRow>,
    hero: HeroJobState?,
    video: VideoJobState?,
    design: DesignJobState?,
): List<EazyKvJobRow> {
    val exclude = buildSet {
        hero?.takeIf { it.isActive }?.jobId?.let { add(it) }
        video?.takeIf { it.isActive }?.jobId?.let { add(it) }
        design?.takeIf { it.isActive }?.jobId?.let { add(it) }
    }
    if (exclude.isEmpty()) return jobs
    return jobs.filter { it.id !in exclude }
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
            messageCount = o.optInt("message_count", 0),
            mode = o.optString("mode", "ai").ifBlank { "ai" },
            supportStatus = o.optString("support_status", "").takeIf { it.isNotBlank() },
        )
    }
}

/**
 * Merges server active list with local tabs so a newly created conversation stays in the strip
 * until the list endpoint includes it; current conversation is listed first.
 */
private fun mergeActiveConversations(
    server: List<EazyConvTabItem>,
    previous: List<EazyConvTabItem>,
    currentConvId: String?
): List<EazyConvTabItem> {
    val byId = LinkedHashMap<String, EazyConvTabItem>()
    server.forEach { byId[it.id] = it }
    previous.forEach { prev ->
        val existing = byId[prev.id]
        if (existing == null) {
            byId[prev.id] = prev
        } else {
            byId[prev.id] = existing.copy(
                preview = prev.preview?.takeIf { isUsableTabText(it) } ?: existing.preview,
                summary = prev.summary ?: existing.summary,
                messageCount = kotlin.math.max(existing.messageCount, prev.messageCount)
            )
        }
    }
    currentConvId?.let { cid ->
        val entry = byId.remove(cid)
            ?: previous.find { it.id == cid }
            ?: EazyConvTabItem(cid, null, null)
        val rest = byId.values.filter { it.id != cid }
        return listOf(entry) + rest
    }
    return byId.values.toList()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EazyChatModal(
    visible: Boolean,
    tokenStore: SecureTokenStore?,
    chatStore: EazyChatStore,
    eazySettingsStore: EazySettingsStore,
    onDismiss: () -> Unit,
    onLoginClick: () -> Unit,
    onResetMascot: () -> Unit = {},
    chatContext: EazyChatContext = EazyChatContext.Shop,
    startTab: EazySidebarTab = EazySidebarTab.Chat,
    pendingArtifactClaimToken: String? = null,
    onPendingArtifactClaimConsumed: () -> Unit = {},
    onOpenCreatorCodes: (prefillCode: String?) -> Unit = {},
    pendingGamesSection: String? = null,
    pendingTradeOfferId: Int? = null,
    onPendingGamesNavConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val context = LocalContext.current
    val store = LocalTranslationStore.current
    val t = store?.let { { k: String, d: String -> it.t(k, d) } } ?: { _: String, d: String -> d }
    val jwt = tokenStore?.getJwt()
    val ownerId = tokenStore?.getOwnerId()?.trim()?.takeIf { it.isNotBlank() }
    val api = remember(jwt, ownerId) { CreatorApi(jwt = jwt) }
    val scope = rememberCoroutineScope()

    val messages by chatStore.messages.collectAsState()
    val fnVisibility by chatStore.fnVisibility.collectAsState()
    val conversationId by chatStore.conversationId.collectAsState()
    val isLoading by chatStore.isLoading.collectAsState()
    val isTyping by chatStore.isTyping.collectAsState()
    val limitReached by chatStore.limitReached.collectAsState()
    val rateLimit by chatStore.rateLimit.collectAsState()
    val heroJob by chatStore.heroJobState.collectAsState()
    val videoJob by chatStore.videoJobState.collectAsState()
    val designJob by chatStore.designJobState.collectAsState()
    val isLoggedIn = tokenStore?.isLoggedIn() == true && !ownerId.isNullOrBlank()
    val pagePath = if (chatContext == EazyChatContext.Creator) "/creator" else "/shop"

    val sidebarTabs = remember {
        listOf(
            EazySidebarTab.Chat to Icons.Default.Chat,
            EazySidebarTab.Notifications to Icons.Default.Notifications,
            EazySidebarTab.Jobs to Icons.Default.Bolt,
            EazySidebarTab.Settings to Icons.Default.Settings,
            EazySidebarTab.Games to Icons.Default.SportsEsports,
            EazySidebarTab.Artifacts to Icons.Default.Star,
            EazySidebarTab.Verify to Icons.Default.Verified,
            EazySidebarTab.Functions to Icons.Default.Build,
            EazySidebarTab.Mascot to Icons.Default.Pets
        )
    }

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
    // ── Support Mode state (mirrors web supportMode / survey flow) ──
    var supportMode by remember { mutableStateOf(false) }
    var supportAgentOnline by remember { mutableStateOf(false) }
    var convSupportMeta by remember { mutableStateOf(EazyConvMeta()) }
    var supportSurveyStep by remember { mutableStateOf<SupportSurveyStep?>(null) }
    var supportSurveyData by remember { mutableStateOf(SupportSurveyData()) }
    val supportSurveyActive = supportSurveyStep != null
    val isLiveSupportActive = supportMode &&
        convSupportMeta.supportStatus != "resolved" &&
        convSupportMeta.supportStatus != "closed"

    // ── Support polling (every 5s when live support is active) ──
    LaunchedEffect(visible, supportMode, conversationId, ownerId) {
        if (!visible || !supportMode || conversationId == null || ownerId == null) return@LaunchedEffect
        val u = chatStore.getUserId(ownerId)
        while (true) {
            delay(5_000)
            if (!supportMode) break
            try {
                val afterId = maxNumericMessageId(chatStore.messages.value)
                val (meta, newMsgs) = pollSupportReplies(api, u, conversationId!!, afterId)
                convSupportMeta = meta
                if (meta.supportFirstReplyAt != null) supportAgentOnline = true
                newMsgs.forEach { chatStore.addMessage(it) }
                if (meta.supportStatus == "resolved" && !supportSurveyActive) {
                    supportMode = false
                    supportAgentOnline = false
                    supportSurveyStep = SupportSurveyStep.SOLVED
                    supportSurveyData = SupportSurveyData()
                }
            } catch (_: Exception) {}
        }
    }

    var historyOpen by remember { mutableStateOf(false) }
    var historyRows by remember { mutableStateOf<List<EazyConvTabItem>>(emptyList()) }
    var loadingHistory by remember { mutableStateOf(false) }
    var drawerExpanded by remember { mutableStateOf(false) }
    var showDeleteAllHistoryConfirm by remember { mutableStateOf(false) }
    var deleteHistoryTargetId by remember { mutableStateOf<String?>(null) }
    val tabListState = rememberLazyListState()
    val chatListState = rememberLazyListState()
    val carouselScroll = rememberScrollState()
    var inputText by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            chatListState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(visible, startTab) {
        if (visible) {
            selectedTab = startTab
        }
    }

    LaunchedEffect(visible) {
        if (visible) chatStore.loadFnVisibilityFromStorage()
    }

    LaunchedEffect(visible) {
        if (visible) {
            eazySettingsStore.loadFromDisk()
        }
    }

    LaunchedEffect(visible, ownerId, isLoggedIn) {
        if (visible && isLoggedIn && ownerId != null) {
            val u = chatStore.getUserId(ownerId)
            eazySettingsStore.tryMergeFromServer(api, u)
        }
    }

    fun loadNotificationsList() {
        val oid = ownerId ?: return
        scope.launch {
            loadingNotifs = true
            try {
                val userR = withContext(Dispatchers.IO) { api.getNotifications(oid) }
                val crR = withContext(Dispatchers.IO) { api.getSystemNotifications(oid, "creator") }
                val shR = withContext(Dispatchers.IO) { api.getSystemNotifications(oid, "shop") }
                val jobsR = withContext(Dispatchers.IO) { api.listJobs(oid, 50) }
                val pendingSave = pendingSaveJobIdsFromKv(jobsR)
                notifsUser = if (userR.optBoolean("ok", false)) {
                    parseNotifications(userR.optJSONArray("notifications") ?: JSONArray())
                        .filter { !shouldHideGeneratedNotification(it, pendingSave) }
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
                val prevSnapshot = convTabs
                val cur = chatStore.conversationId.value
                val resp = withContext(Dispatchers.IO) {
                    api.getEazyConversation(uid, mapOf("list" to "1", "status" to "active"))
                }
                if (resp.optBoolean("ok", false)) {
                    val arr = resp.optJSONArray("conversations") ?: JSONArray()
                    val parsed = parseConvTabs(arr)
                    convTabs = mergeActiveConversations(parsed, prevSnapshot, cur)
                }
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(visible, selectedTab, ownerId) {
        if (visible && selectedTab == EazySidebarTab.Notifications && ownerId != null) {
            loadNotificationsList()
        }
    }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        chatStore.designSaveComplete.collect {
            selectedTab = EazySidebarTab.Notifications
            notifFilter = "unread"
            notifFeedScope = "user"
            delay(2000)
            loadNotificationsList()
        }
    }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        chatStore.designJobComplete.collect {
            selectedTab = EazySidebarTab.Notifications
            notifFilter = "unread"
            notifFeedScope = "user"
            delay(800)
            loadNotificationsList()
        }
    }

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        chatStore.asyncJobComplete.collect {
            delay(600)
            loadNotificationsList()
        }
    }

    LaunchedEffect(visible, ownerId, selectedTab, jobsFeedScope) {
        if (!visible || ownerId == null) return@LaunchedEffect
        val oid = ownerId ?: return@LaunchedEffect
        suspend fun loadOnce() {
            if (selectedTab == EazySidebarTab.Jobs && jobsFeedScope == "system") {
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
                } else {
                    userKvJobs = emptyList()
                }
                systemJobs = emptyList()
            }
        }
        loadingJobs = selectedTab == EazySidebarTab.Jobs
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

    val userKvJobsForDisplay = remember(userKvJobs, heroJob, videoJob, designJob) {
        filterKvJobsForLocalOverlay(userKvJobs, heroJob, videoJob, designJob)
    }

    var lastActiveJobCount by remember { mutableStateOf(0) }
    val currentActiveJobCount = remember(userKvJobsForDisplay, heroJob, videoJob, designJob) {
        val heroActive = if (heroJob?.isActive == true) 1 else 0
        val videoActive = if (videoJob?.isActive == true) 1 else 0
        val designActive = if (designJob?.isActive == true) 1 else 0
        userKvJobsForDisplay.size + heroActive + videoActive + designActive
    }
    LaunchedEffect(currentActiveJobCount, visible, selectedTab) {
        if (!visible || selectedTab != EazySidebarTab.Jobs) {
            lastActiveJobCount = currentActiveJobCount
            return@LaunchedEffect
        }
        if (lastActiveJobCount > 0 && currentActiveJobCount == 0) {
            selectedTab = EazySidebarTab.Notifications
            notifFilter = "unread"
            notifFeedScope = "user"
            delay(400)
            loadNotificationsList()
        }
        lastActiveJobCount = currentActiveJobCount
    }

    val jobsBadgeCount = remember(userKvJobsForDisplay, systemJobs, heroJob, videoJob, designJob, selectedTab, jobsFeedScope) {
        val heroActive = if (heroJob?.isActive == true) 1 else 0
        val videoActive = if (videoJob?.isActive == true) 1 else 0
        val designActive = if (designJob?.isActive == true) 1 else 0
        if (selectedTab == EazySidebarTab.Jobs && jobsFeedScope == "system") {
            systemJobs.size
        } else {
            userKvJobsForDisplay.size + heroActive + videoActive + designActive
        }
    }

    LaunchedEffect(visible, isLoggedIn, ownerId) {
        if (!visible || !isLoggedIn || ownerId == null) return@LaunchedEffect
        chatStore.setLoading(true)
        val userId = chatStore.getUserId(ownerId)
        try {
            val resp = withContext(Dispatchers.IO) {
                withTimeout(25_000) {
                    api.getEazyConversation(
                        userId,
                        mapOf("page" to pagePath, "auto_create" to "0")
                    )
                }
            }
            if (resp.optBoolean("ok", false)) {
                val conv = resp.optJSONObject("conversation")
                if (conv != null) {
                    val msgs = resp.optJSONArray("messages") ?: JSONArray()
                    conv.optString("id")?.let { cid ->
                        chatStore.setConversationId(cid)
                        val meta = parseEazyConvMeta(conv)
                        convTabs =
                            listOf(
                                EazyConvTabItem(
                                    id = cid,
                                    preview = conv.optString("preview", "").trim().ifBlank { null },
                                    summary = conv.optString("summary", "").trim().ifBlank { null },
                                    mode = meta.mode,
                                    supportStatus = meta.supportStatus,
                                ),
                            )
                        // Restore support state when conversation was in support mode
                        if (isLiveSupportMeta(meta)) {
                            convSupportMeta = meta
                            supportMode = true
                            if (meta.supportFirstReplyAt != null) supportAgentOnline = true
                        } else if (meta.supportStatus == "resolved") {
                            supportSurveyStep = SupportSurveyStep.SOLVED
                            supportSurveyData = SupportSurveyData()
                        }
                    }
                    chatStore.setMessages(parseMessagesArray(msgs))
                } else {
                    val newR = withContext(Dispatchers.IO) {
                        withTimeout(25_000) { api.eazyConvNew(userId) }
                    }
                    if (newR.optBoolean("ok", false)) {
                        val c = newR.optJSONObject("conversation")
                        c?.optString("id")?.let { cid ->
                            chatStore.setConversationId(cid)
                            convTabs = listOf(EazyConvTabItem(cid, null, null))
                        }
                        chatStore.setMessages(emptyList())
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            chatStore.setLoading(false)
            loadActiveTabs(userId)
        }
    }

    val eazyPalette = remember(chatContext) { eazyPaletteFor(chatContext) }
    CompositionLocalProvider(LocalEazyModalPalette provides eazyPalette) {
    EazBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = eazyPalette.bg,
        fullscreen = true,
        dragHandle = null,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            val isWideLayout = maxWidth > 600.dp
            var sidebarOpen by remember(isWideLayout) { mutableStateOf(isWideLayout) }
            LaunchedEffect(isWideLayout) {
                sidebarOpen = isWideLayout
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .background(LocalEazyModalPalette.current.bg)
            ) {
                EazModalSheetLayout(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    header = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(EazyOrangeHeaderGradient)
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!sidebarOpen) {
                                    IconButton(
                                        onClick = { sidebarOpen = true },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Menu,
                                            contentDescription = t("eazy_chat.ui_open_sidebar", "Open menu"),
                                            tint = Color.White.copy(alpha = 0.8f),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.width(36.dp))
                                }
                                Text(
                                    text = sidebarTabLabel(selectedTab, t),
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = t("eazy_chat.ui_close_chat", "Close"),
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            if (selectedTab == EazySidebarTab.Chat && isLoggedIn && !limitReached) {
                                EazyChatConvTabsHeader(
                                    convTabs = convTabs,
                                    onConvTabsChange = { convTabs = it },
                                    tabListState = tabListState,
                                    conversationId = conversationId,
                                    supportMode = supportMode,
                                    convSupportMeta = convSupportMeta,
                                    drawerExpanded = drawerExpanded,
                                    onDrawerExpandedChange = { drawerExpanded = it },
                                    carouselScroll = carouselScroll,
                                    chatContext = chatContext,
                                    fnVisibility = fnVisibility,
                                    chatStore = chatStore,
                                    api = api,
                                    ownerId = ownerId,
                                    scope = scope,
                                    pagePath = pagePath,
                                    t = t,
                                    onHistoryClick = {
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
                                                    val curId = conversationId
                                                    historyRows = parseConvTabs(arr).filter { row ->
                                                        curId == null || row.id != curId
                                                    }
                                                } else historyRows = emptyList()
                                            } catch (_: Exception) {
                                                historyRows = emptyList()
                                            }
                                            loadingHistory = false
                                        }
                                    },
                                    loadActiveTabs = { loadActiveTabs(it) },
                                )
                            }
                        }
                    },
                    footer = {
                        if (selectedTab == EazySidebarTab.Chat && isLoggedIn && !limitReached) {
                            EazyChatComposerFooter(
                                rateLimit = rateLimit,
                                inputText = inputText,
                                onInputTextChange = { inputText = it },
                                isTyping = isTyping,
                                supportSurveyStep = supportSurveyStep,
                                supportSurveyData = supportSurveyData,
                                onSupportSurveyDataChange = { supportSurveyData = it },
                                onSupportSurveyStepChange = { supportSurveyStep = it },
                                convSupportMeta = convSupportMeta,
                                onConvSupportMetaChange = { convSupportMeta = it },
                                supportMode = supportMode,
                                onSupportModeChange = { supportMode = it },
                                onSupportAgentOnlineChange = { supportAgentOnline = it },
                                isLiveSupportActive = isLiveSupportActive,
                                conversationId = conversationId,
                                ownerId = ownerId,
                                api = api,
                                chatStore = chatStore,
                                scope = scope,
                                pagePath = pagePath,
                                convTabs = convTabs,
                                onConvTabsChange = { convTabs = it },
                                t = t,
                            )
                        }
                    },
                    body = {
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
                                            color = LocalEazyModalPalette.current.muted,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        TextButton(
                                            onClick = {
                                                onDismiss()
                                                onLoginClick()
                                            },
                                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = LocalEazyModalPalette.current.accent)
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
                                            color = LocalEazyModalPalette.current.muted,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        // Online float badge (mirrors web #creator-chat-support-float)
                                        EazySupportOnlineFloat(
                                            visible = supportAgentOnline && isLiveSupportActive,
                                            agentName = EAZY_SUPPORT_AGENT_NAME,
                                            t = t,
                                            modifier = Modifier.fillMaxWidth(),
                                        )

                                        // Support survey panel
                                        if (supportSurveyStep != null) {
                                            EazySupportSurveyPanel(
                                                step = supportSurveyStep!!,
                                                survey = supportSurveyData,
                                                t = t,
                                                onSolved = { solved ->
                                                    supportSurveyData = supportSurveyData.copy(solved = solved)
                                                    supportSurveyStep = SupportSurveyStep.RATING
                                                    chatStore.addMessage(ChatMessage("sv_sol${System.currentTimeMillis()}", "user", if (solved) t("support_yes", "Yes") else t("support_no", "No")))
                                                },
                                                onRating = { stars ->
                                                    supportSurveyData = supportSurveyData.copy(rating = stars)
                                                    supportSurveyStep = SupportSurveyStep.FEEDBACK_ASK
                                                    chatStore.addMessage(ChatMessage("sv_rat${System.currentTimeMillis()}", "user", "$stars / 5"))
                                                },
                                                onFeedbackChoice = { wantsFeedback ->
                                                    if (wantsFeedback) {
                                                        supportSurveyStep = SupportSurveyStep.FEEDBACK_TEXT
                                                    } else {
                                                        val snap = supportSurveyData
                                                        scope.launch {
                                                            val u = chatStore.getUserId(ownerId)
                                                            val cid = conversationId
                                                            if (cid != null) {
                                                                try { submitSupportSurveyOnServer(api, u, cid, snap) } catch (_: Exception) {}
                                                            }
                                                        }
                                                        supportSurveyStep = null
                                                        supportMode = false
                                                        supportAgentOnline = false
                                                        convSupportMeta = convSupportMeta.copy(mode = "ai", supportStatus = "closed")
                                                        chatStore.addMessage(ChatMessage("sv_ty${System.currentTimeMillis()}", "assistant", t("support_survey_thanks", "Thank you for your feedback! You can chat with Eazy again now.")))
                                                    }
                                                },
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                            )
                                        }

                                        LazyColumn(
                                            modifier = Modifier.fillMaxSize(),
                                            state = chatListState,
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            if (isLoading && messages.isEmpty()) {
                                                item {
                                                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                        CircularProgressIndicator(color = LocalEazyModalPalette.current.accent, modifier = Modifier.size(32.dp))
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
                                },
                                onOpenCreatorCodes = onOpenCreatorCodes,
                            )

                            EazySidebarTab.Jobs -> EazyJobsCombinedPanel(
                                hero = heroJob,
                                video = videoJob,
                                design = designJob,
                                jobsFeedScope = jobsFeedScope,
                                onJobsFeedScopeChange = { jobsFeedScope = it },
                                userKvJobs = userKvJobsForDisplay,
                                systemJobs = systemJobs,
                                loadingJobs = loadingJobs,
                                t = t
                            )
                            EazySidebarTab.Settings -> EazySettingsTabView(
                                eazySettingsStore = eazySettingsStore,
                                api = api,
                                chatStore = chatStore,
                                ownerId = ownerId,
                                scope = scope,
                                t = t,
                                onResetMascot = onResetMascot,
                                onOpenFunctions = { selectedTab = EazySidebarTab.Functions },
                                onChatHistoryCleared = {
                                    scope.launch {
                                        val u = chatStore.getUserId(ownerId)
                                        chatStore.clearMessages()
                                        convTabs = emptyList()
                                        loadActiveTabs(u)
                                    }
                                }
                            )
                            EazySidebarTab.Games -> EazyGamesHubPanel(
                                api = api,
                                ownerId = ownerId,
                                isLoggedIn = isLoggedIn,
                                onLoginClick = onLoginClick,
                                onDismiss = onDismiss,
                                t = t,
                                initialSection = pendingGamesSection,
                                pendingTradeOfferId = pendingTradeOfferId,
                                onPendingGamesNavConsumed = onPendingGamesNavConsumed,
                            )
                            EazySidebarTab.Artifacts -> EazyArtifactsHubPanel(
                                api = api,
                                ownerId = ownerId,
                                isLoggedIn = isLoggedIn,
                                onLoginClick = onLoginClick,
                                pendingClaimToken = pendingArtifactClaimToken,
                                onPendingClaimConsumed = onPendingArtifactClaimConsumed,
                                t = t,
                            )
                            EazySidebarTab.Verify -> EazyVerifyPanel(
                                ownerId = ownerId,
                                api = api,
                                t = t,
                                modifier = Modifier.fillMaxSize(),
                            )
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
                                            color = LocalEazyModalPalette.current.muted,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        TextButton(
                                            onClick = {
                                                onDismiss()
                                                onLoginClick()
                                            },
                                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = LocalEazyModalPalette.current.accent)
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
                )

                if (sidebarOpen) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { sidebarOpen = false }
                            .zIndex(19f)
                    )
                    EazyChatSidebarOverlay(
                        tabs = sidebarTabs,
                        selectedTab = selectedTab,
                        isWideLayout = isWideLayout,
                        unreadCount = totalUnreadNotifs,
                        jobsBadgeCount = jobsBadgeCount,
                        tabLabel = { tab -> sidebarTabLabel(tab, t) },
                        onTabSelected = { tab ->
                            selectedTab = tab
                            if (!isWideLayout) sidebarOpen = false
                        },
                        onClose = { sidebarOpen = false },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .zIndex(20f)
                    )
                }
            }
        }
    }

    if (historyOpen) {
        EazInsetDialog(onDismissRequest = { historyOpen = false }) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(LocalEazyModalPalette.current.header)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(t("eazy_chat.ui_chat_history", "Chat history"), color = LocalEazyModalPalette.current.text, style = MaterialTheme.typography.titleMedium)
                    Row {
                        TextButton(onClick = { showDeleteAllHistoryConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = LocalEazyModalPalette.current.muted, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { historyOpen = false }) {
                            Icon(Icons.Default.Close, contentDescription = t("eazy_chat.ui_close", "Close"), tint = LocalEazyModalPalette.current.text)
                        }
                    }
                }
                Divider(color = LocalEazyModalPalette.current.muted.copy(alpha = 0.3f))
                if (loadingHistory) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = LocalEazyModalPalette.current.accent)
                    }
                } else if (historyRows.isEmpty()) {
                    Text(
                        t("eazy_chat.history_empty", "No past chats available."),
                        color = LocalEazyModalPalette.current.muted,
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
                                        color = LocalEazyModalPalette.current.text,
                                        maxLines = 2
                                    )
                                    Text(
                                        "${row.messageCount} ${t("eazy_chat.ui_messages", "messages")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = LocalEazyModalPalette.current.muted
                                    )
                                }
                                IconButton(onClick = { deleteHistoryTargetId = row.id }) {
                                    Icon(Icons.Default.Delete, contentDescription = t("chatDelete", "Delete"), tint = LocalEazyModalPalette.current.muted)
                                }
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
            title = { Text(t("chatDeleteHistoryConfirm", "Delete complete chat history permanently? Open chats remain."), color = LocalEazyModalPalette.current.text) }
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
            title = { Text(t("chatDeleteChatConfirm", "Delete this chat permanently?"), color = LocalEazyModalPalette.current.text) }
        )
    }
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
    onMarkRead: (EazyNotifRow) -> Unit,
    onOpenCreatorCodes: (prefillCode: String?) -> Unit = {},
) {
    val unread = notifications.filter { !it.isRead }
    val read = notifications.filter { it.isRead }
    val shown = if (notifFilter == "unread") unread else read
    Column(modifier = Modifier.fillMaxSize()) {
        EazyFeedScopeTabRow(
            tabs = listOf(
                "user" to t("creator.notifications.feed_user", "User"),
                "system" to t("creator.notifications.feed_system", "System"),
            ),
            activeKey = notifFeedScope,
            onSelect = onNotifFeedScopeChange,
        )
        EazyUnderlineTabRow(
            tabs = listOf(
                "unread" to t("creator.notifications.unread", "Unread"),
                "read" to t("creator.notifications.read", "Read"),
            ),
            activeKey = notifFilter,
            onSelect = onFilterChange,
        )
        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = LocalEazyModalPalette.current.accent)
            }
        } else if (shown.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = if (notifFilter == "unread") {
                        t("chat_notifications_none_unread", "No unread notifications")
                    } else {
                        t("chat_notifications_none_read", "No read notifications")
                    },
                    color = LocalEazyModalPalette.current.muted,
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!n.isRead) LocalEazyModalPalette.current.accent.copy(alpha = 0.12f) else LocalEazyModalPalette.current.muted.copy(alpha = 0.08f))
                            .clickable {
                                if (!n.isRead) onMarkRead(n)
                                if (n.opensCreatorCodes) {
                                    onOpenCreatorCodes(n.creatorCodePrefill)
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        n.previewImageUrl?.let { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(n.title, style = MaterialTheme.typography.titleSmall, color = LocalEazyModalPalette.current.text)
                            if (n.message.isNotBlank()) {
                                Text(n.message, style = MaterialTheme.typography.bodySmall, color = LocalEazyModalPalette.current.muted)
                            }
                            n.createdAt?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = LocalEazyModalPalette.current.muted.copy(alpha = 0.7f))
                            }
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
    val fnVisibility by chatStore.fnVisibility.collectAsState()
    fun isInCarousel(id: String) = fnVisibility[id] != false

    val categories = listOf(
        EazyFeatureCategory.Shared to EazyChatFeatureCatalog.forCategory(EazyFeatureCategory.Shared),
        EazyFeatureCategory.Shop to EazyChatFeatureCatalog.forCategory(EazyFeatureCategory.Shop),
        EazyFeatureCategory.Creator to EazyChatFeatureCatalog.forCategory(EazyFeatureCategory.Creator),
    ).filter { it.second.isNotEmpty() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        categories.forEach { (cat, defs) ->
            val ids = defs.map { it.id }
            val allVis = ids.all { isInCarousel(it) }
            val catAccent = eazyCategoryAccent(cat)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(eazyCategoryTint(cat))
                    .border(1.dp, catAccent.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    EazyChatFeatureCatalog.categoryLabel(cat, t),
                    style = MaterialTheme.typography.titleSmall,
                    color = catAccent
                )
                TextButton(onClick = { onCategoryToggle(ids, !allVis) }) {
                    Text(if (allVis) t("eazy_fn.hide_all", "Hide all") else t("eazy_fn.show_all", "Show all"), color = LocalEazyModalPalette.current.muted)
                }
            }
            defs.chunked(2).forEach { rowDefs ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowDefs.forEach { def ->
                        val vis = isInCarousel(def.id)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 72.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, LocalEazyModalPalette.current.muted.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { onRunFeature(def.id) }
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = eazyFeatureIcon(def.id),
                                    contentDescription = t(def.labelKey, def.defaultLabel),
                                    tint = LocalEazyModalPalette.current.text,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            IconButton(
                                onClick = { onToggle(def.id) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(36.dp)
                            ) {
                                Icon(
                                    imageVector = if (vis) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = LocalEazyModalPalette.current.muted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    if (rowDefs.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
            }
            Divider(color = LocalEazyModalPalette.current.muted.copy(alpha = 0.15f))
        }
        Text(
            t("eazy_fn.hint", "Eye: show or hide shortcuts in the chat carousel."),
            style = MaterialTheme.typography.labelSmall,
            color = LocalEazyModalPalette.current.muted
        )
    }
}

@Composable
private fun EazyChatSidebarOverlay(
    tabs: List<Pair<EazySidebarTab, ImageVector>>,
    selectedTab: EazySidebarTab,
    isWideLayout: Boolean,
    unreadCount: Int,
    jobsBadgeCount: Int,
    tabLabel: (EazySidebarTab) -> String,
    onTabSelected: (EazySidebarTab) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sidebarWidth = if (isWideLayout) 48.dp else 200.dp
    Column(
        modifier = modifier
            .width(sidebarWidth)
            .fillMaxHeight()
            .background(EazyChatSidebarGradient)
            .border(1.dp, Color.White.copy(alpha = 0.15f))
            .padding(vertical = if (isWideLayout) 8.dp else 12.dp, horizontal = if (isWideLayout) 0.dp else 8.dp),
        horizontalAlignment = if (isWideLayout) Alignment.CenterHorizontally else Alignment.Start
    ) {
        if (!isWideLayout) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.End)
                    .size(36.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        tabs.forEach { (tab, icon) ->
            val active = selectedTab == tab
            val inactiveTint = Color.White.copy(alpha = 0.7f)
            val activeTint = Color.White
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (isWideLayout) Modifier.height(40.dp) else Modifier)
                        .clip(
                            if (isWideLayout) {
                                RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
                            } else {
                                RoundedCornerShape(8.dp)
                            }
                        )
                        .then(
                            if (active) Modifier.background(Color.White.copy(alpha = 0.2f)) else Modifier
                        )
                        .clickable { onTabSelected(tab) }
                        .padding(
                            horizontal = if (isWideLayout) 0.dp else 12.dp,
                            vertical = if (isWideLayout) 0.dp else 10.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (isWideLayout) Arrangement.Center else Arrangement.Start
                ) {
                    if (isWideLayout && active) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(Color.White)
                        )
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.name,
                        tint = if (active) activeTint else inactiveTint,
                        modifier = Modifier
                            .size(if (isWideLayout) 22.dp else 20.dp)
                            .then(if (!isWideLayout) Modifier.padding(end = 10.dp) else Modifier)
                    )
                    if (!isWideLayout) {
                        Text(
                            text = tabLabel(tab),
                            color = if (active) activeTint else inactiveTint.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (tab == EazySidebarTab.Notifications && unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = if (isWideLayout) 2.dp else 8.dp)
                            .height(16.dp)
                            .widthIn(min = 16.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (unreadCount > 99) "99+" else "$unreadCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalEazyModalPalette.current.accent
                        )
                    }
                }
                if (tab == EazySidebarTab.Jobs && jobsBadgeCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = if (isWideLayout) 2.dp else 8.dp)
                            .height(16.dp)
                            .widthIn(min = 16.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (jobsBadgeCount > 99) "99+" else "$jobsBadgeCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalEazyModalPalette.current.accent
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
        }
    }
}

private fun systemJobKindUiLabel(jobKind: String, t: (String, String) -> String): String =
    when (jobKind.trim()) {
        "automation_design" -> t("eazy_chat.chat_job_kind_automation_design", "Scheduled design automation")
        else -> t("eazy_chat.chat_job_kind_system_publish", "Automatic publishing")
    }

@Composable
private fun EazyJobsCombinedPanel(
    hero: HeroJobState?,
    video: VideoJobState?,
    design: DesignJobState?,
    jobsFeedScope: String,
    onJobsFeedScopeChange: (String) -> Unit,
    userKvJobs: List<EazyKvJobRow>,
    systemJobs: List<EazySystemJobRow>,
    loadingJobs: Boolean,
    t: (String, String) -> String
) {
    Column(modifier = Modifier.fillMaxSize()) {
        EazyFeedScopeTabRow(
            tabs = listOf(
                "user" to t("creator.notifications.feed_user", "User"),
                "system" to t("creator.notifications.feed_system", "System"),
            ),
            activeKey = jobsFeedScope,
            onSelect = onJobsFeedScopeChange,
        )
        when {
            loadingJobs -> Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = LocalEazyModalPalette.current.accent)
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
                        Text(t("eazy_chat.chat_no_active_jobs", "No active jobs"), color = LocalEazyModalPalette.current.muted)
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
                            val kindLbl = systemJobKindUiLabel(j.jobKind, t)
                            val prog = j.progress.coerceIn(0, 100)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(LocalEazyModalPalette.current.muted.copy(alpha = 0.08f))
                                    .padding(12.dp)
                            ) {
                                Text(j.title, style = MaterialTheme.typography.titleSmall, color = LocalEazyModalPalette.current.text)
                                Text(kindLbl, style = MaterialTheme.typography.labelSmall, color = LocalEazyModalPalette.current.muted)
                                if (j.subtitleDetail.isNotBlank()) {
                                    Text(
                                        j.subtitleDetail.take(140),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalEazyModalPalette.current.muted
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = prog.coerceIn(0, 100) / 100f,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = LocalEazyModalPalette.current.accent,
                                    trackColor = LocalEazyModalPalette.current.muted.copy(alpha = 0.3f)
                                )
                                j.message?.takeIf { it.isNotBlank() }?.let { m ->
                                    Text(m, style = MaterialTheme.typography.bodySmall, color = LocalEazyModalPalette.current.muted)
                                }
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
                    EazyLocalAsyncJobsPanel(
                        hero = hero,
                        video = video,
                        design = design,
                        hasKvJobs = userKvJobs.isNotEmpty(),
                        t = t
                    )
                    if (userKvJobs.isNotEmpty()) {
                        Text(
                            t("creator.notifications.active_jobs", "Active Jobs"),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = LocalEazyModalPalette.current.muted
                        )
                        userKvJobs.forEach { j ->
                            EazyKvJobCard(j, t)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EazyLocalAsyncJobsPanel(
    hero: HeroJobState?,
    video: VideoJobState?,
    design: DesignJobState?,
    hasKvJobs: Boolean = false,
    t: (String, String) -> String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val activeDesign = design?.takeIf { it.isActive }
        val activeHero = hero?.takeIf { it.isActive }
        val activeVideo = video?.takeIf { it.isActive }
        activeDesign?.let { EazyActiveDesignJobCard(it, t) }
        activeHero?.let { EazyActiveHeroJobCard(it, t) }
        activeVideo?.let { EazyActiveVideoJobCard(it, t) }
        if (activeDesign == null && activeHero == null && activeVideo == null && !hasKvJobs) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Bolt,
                    contentDescription = null,
                    tint = LocalEazyModalPalette.current.muted,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = t("eazy_chat.chat_no_active_jobs", "No active jobs"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = LocalEazyModalPalette.current.muted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}


@Composable
private fun EazyChatConvTabsHeader(
    convTabs: List<EazyConvTabItem>,
    onConvTabsChange: (List<EazyConvTabItem>) -> Unit,
    tabListState: LazyListState,
    conversationId: String?,
    supportMode: Boolean,
    @Suppress("UNUSED_PARAMETER") convSupportMeta: EazyConvMeta,
    drawerExpanded: Boolean,
    onDrawerExpandedChange: (Boolean) -> Unit,
    carouselScroll: ScrollState,
    chatContext: EazyChatContext,
    fnVisibility: Map<String, Boolean>,
    chatStore: EazyChatStore,
    api: CreatorApi,
    ownerId: String?,
    scope: CoroutineScope,
    pagePath: String,
    t: (String, String) -> String,
    onHistoryClick: () -> Unit,
    loadActiveTabs: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalEazyModalPalette.current.header)
            .border(
                1.dp,
                LocalEazyModalPalette.current.border.copy(
                    alpha = if (LocalEazyModalPalette.current.bg == Color.White) 1f else 0.35f,
                ),
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
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
                val active = tab.id == conversationId
                val isSupportTab = (active && supportMode) ||
                    (tab.mode == "support" && tab.supportStatus != "closed" && tab.supportStatus != "resolved")
                val livePrefix = t("chat_live_support_tab", "Live Support:")
                val baseLabel = tabStripLabel(tab.preview, tab.summary, newChatFb)
                val label = if (isSupportTab) supportTabPrefixLabel(livePrefix, baseLabel) else baseLabel
                val tabBg = when {
                    active && isSupportTab -> EazySupportRed.copy(alpha = 0.25f)
                    active -> LocalEazyModalPalette.current.accent.copy(alpha = 0.25f)
                    isSupportTab -> EazySupportRed.copy(alpha = 0.12f)
                    else -> Color.Transparent
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(tabBg)
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
                        color = when {
                            isSupportTab -> EazySupportRedActive
                            active -> LocalEazyModalPalette.current.accent
                            else -> LocalEazyModalPalette.current.text
                        },
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
                                onConvTabsChange(remaining)
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
                                                onConvTabsChange(
                                                    listOf(EazyConvTabItem(id = nid, preview = null, summary = null))
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
                        Icon(
                            Icons.Default.Close,
                            contentDescription = t("chatCloseTitle", "Close chat"),
                            tint = LocalEazyModalPalette.current.muted,
                            modifier = Modifier.size(14.dp)
                        )
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
                        onConvTabsChange(listOf(EazyConvTabItem(id, null, null)) + convTabs.filter { it.id != id })
                        loadActiveTabs(u)
                    }
                }
            }
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = t("eazy_chat.new_chat", "New chat"),
                tint = LocalEazyModalPalette.current.accent
            )
        }
        IconButton(onClick = onHistoryClick) {
            Icon(
                Icons.Default.History,
                contentDescription = t("eazy_chat.ui_chat_history", "Chat history"),
                tint = LocalEazyModalPalette.current.accent
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalEazyModalPalette.current.header)
    ) {
        AnimatedVisibility(drawerExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
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
                        Text("\u2039", color = LocalEazyModalPalette.current.text)
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(carouselScroll)
                    ) {
                        val defs = EazyChatFeatureCatalog.all().filter { fnVisibility[it.id] != false }
                        defs.forEach { def ->
                            val cd = t(def.labelKey, def.defaultLabel)
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, LocalEazyModalPalette.current.muted.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .clickable {
                                        onDrawerExpandedChange(false)
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
                                    tint = LocalEazyModalPalette.current.text,
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
                        Text("\u203A", color = LocalEazyModalPalette.current.text)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onDrawerExpandedChange(!drawerExpanded) }
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(LocalEazyModalPalette.current.muted)
                )
                Icon(
                    imageVector = if (drawerExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = LocalEazyModalPalette.current.muted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun EazyChatComposerFooter(
    rateLimit: RateLimitState?,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    isTyping: Boolean,
    supportSurveyStep: SupportSurveyStep?,
    supportSurveyData: SupportSurveyData,
    onSupportSurveyDataChange: (SupportSurveyData) -> Unit,
    onSupportSurveyStepChange: (SupportSurveyStep?) -> Unit,
    convSupportMeta: EazyConvMeta,
    onConvSupportMetaChange: (EazyConvMeta) -> Unit,
    supportMode: Boolean,
    onSupportModeChange: (Boolean) -> Unit,
    onSupportAgentOnlineChange: (Boolean) -> Unit,
    isLiveSupportActive: Boolean,
    conversationId: String?,
    ownerId: String?,
    api: CreatorApi,
    chatStore: EazyChatStore,
    scope: CoroutineScope,
    pagePath: String,
    convTabs: List<EazyConvTabItem>,
    onConvTabsChange: (List<EazyConvTabItem>) -> Unit,
    t: (String, String) -> String,
) {
    EazModalFooterSurface(
        color = LocalEazyModalPalette.current.header,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (rateLimit != null) {
                val rl = rateLimit
                val rem = rl.remaining
                val lim = rl.limit
                val pct = if (lim > 0) (rem.toFloat() / lim * 100).toInt() else 100
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$rem ${t("eazy_chat.ui_messages_of", "of")} $lim ${t("eazy_chat.ui_messages", "messages")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalEazyModalPalette.current.muted
                        )
                        if (rl.resetIn > 0) {
                            Text(
                                text = "${t("eazy_chat.ui_reset_in", "Reset in")} ${formatResetTime(rl.resetIn)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalEazyModalPalette.current.muted
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(LocalEazyModalPalette.current.muted.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(pct / 100f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (pct <= 10) LocalEazyModalPalette.current.muted else LocalEazyModalPalette.current.accent)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = { if (it.length <= 500) onInputTextChange(it) },
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(LocalEazyModalPalette.current.userBubble)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = LocalEazyModalPalette.current.text),
                    cursorBrush = SolidColor(LocalEazyModalPalette.current.accent),
                    singleLine = false,
                    maxLines = 4,
                    decorationBox = { inner ->
                        Box(modifier = Modifier.padding(end = 8.dp)) {
                            if (inputText.isEmpty()) {
                                Text(
                                    text = t("eazy_chat.ui_message_placeholder", "Type a message..."),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = LocalEazyModalPalette.current.muted
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
                        onInputTextChange("")
                        if (supportSurveyStep == SupportSurveyStep.FEEDBACK_TEXT) {
                            onSupportSurveyDataChange(supportSurveyData.copy(feedback = text))
                            scope.launch {
                                chatStore.addMessage(ChatMessage("u${System.currentTimeMillis()}", "user", text))
                                val u = chatStore.getUserId(ownerId)
                                val cid = conversationId
                                if (cid != null) {
                                    try { submitSupportSurveyOnServer(api, u, cid, supportSurveyData.copy(feedback = text)) } catch (_: Exception) {}
                                }
                                onSupportSurveyStepChange(null)
                                onSupportModeChange(false)
                                onSupportAgentOnlineChange(false)
                                onConvSupportMetaChange(convSupportMeta.copy(mode = "ai", supportStatus = "closed"))
                                chatStore.addMessage(ChatMessage("sv${System.currentTimeMillis()}", "assistant", t("support_survey_thanks", "Thank you for your feedback! You can chat with Eazy again now.")))
                            }
                            return@IconButton
                        }
                        scope.launch {
                            chatStore.addMessage(ChatMessage("u${System.currentTimeMillis()}", "user", text))
                            if (isLiveSupportActive) {
                                val u = chatStore.getUserId(ownerId)
                                val cid = conversationId ?: return@launch
                                try {
                                    sendSupportMessageOnServer(api, u, cid, text)
                                } catch (_: Exception) {}
                                return@launch
                            }
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
                                    val action = resp.optJSONObject("action")
                                    if (action?.optString("action") == "connect_support") {
                                        val reason = action.optString("params", "")
                                        onConvSupportMetaChange(EazyConvMeta(mode = "support", supportStatus = "open"))
                                        onSupportModeChange(true)
                                        onSupportAgentOnlineChange(false)
                                        conversationId?.let { cid ->
                                            onConvTabsChange(
                                                convTabs.map { tab ->
                                                    if (tab.id == cid) tab.copy(mode = "support", supportStatus = "open") else tab
                                                }
                                            )
                                        }
                                        val cid = conversationId
                                        if (cid != null) {
                                            try {
                                                activateSupportOnServer(api, u, cid, reason, t)
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }
                                conversationId?.let { cid ->
                                    val newChatFb = t("eazy_chat.tab_new_chat", "Chat")
                                    onConvTabsChange(
                                        convTabs.map { tab ->
                                            val emptyPreview = tab.preview == null || !isUsableTabText(tab.preview)
                                            val wasPlaceholder = tab.preview?.trim() == newChatFb
                                            if (tab.id == cid && (emptyPreview || wasPlaceholder)) {
                                                tab.copy(preview = text.take(60))
                                            } else tab
                                        }
                                    )
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
                        .background(LocalEazyModalPalette.current.accent)
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
                .then(
                    if (isUser) Modifier.background(LocalEazyModalPalette.current.userBubble)
                    else Modifier
                        .background(LocalEazyModalPalette.current.assistantBubble)
                        .border(1.dp, LocalEazyModalPalette.current.border, RoundedCornerShape(16.dp))
                )
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .widthIn(max = 280.dp)
        ) {
            if (isTyping) {
                CircularProgressIndicator(color = LocalEazyModalPalette.current.text, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) Color.White else LocalEazyModalPalette.current.text
                )
            }
        }
    }
}
