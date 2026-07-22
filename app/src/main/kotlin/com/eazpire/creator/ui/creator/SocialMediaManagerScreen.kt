package com.eazpire.creator.ui.creator

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.AuthBrowserLauncher
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.modal.EazFullScreenDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Native Social Media Manager — parity shell for the marketing web "Social" module.
 * Sidebar nav: Overview | Connect | Manage Posts | New Post.
 *
 * NOTE: [CreatorApi.creatorSocialOAuthStart], [CreatorApi.creatorSocialDisconnect],
 * [CreatorApi.creatorSocialChannelsStatus], [CreatorApi.composerAssets],
 * [CreatorApi.creatorSocialPostsCreate] and [CreatorApi.creatorSocialPostsList] are expected
 * to be added to `CreatorApi` — see method assumptions documented at each call site below.
 */

private const val TAB_OVERVIEW = 0
private const val TAB_CONNECT = 1
private const val TAB_MANAGE_POSTS = 2
private const val TAB_NEW_POST = 3

private data class SocialChannelDef(val key: String, val label: String, val live: Boolean)

private val SOCIAL_CHANNELS = listOf(
    SocialChannelDef("facebook", "Facebook", live = true),
    SocialChannelDef("instagram", "Instagram", live = false),
    SocialChannelDef("threads", "Threads", live = false),
    SocialChannelDef("tiktok", "TikTok", live = true),
    SocialChannelDef("youtube", "YouTube", live = false),
    SocialChannelDef("snapchat", "Snapchat", live = false),
    SocialChannelDef("pinterest", "Pinterest", live = false),
    SocialChannelDef("tumblr", "Tumblr", live = false),
    SocialChannelDef("linkedin", "LinkedIn", live = false),
    SocialChannelDef("mastodon", "Mastodon", live = false),
    SocialChannelDef("bluesky", "Bluesky", live = false),
)

private data class SocialAssetItem(
    val id: String,
    val url: String?,
    val thumbnailUrl: String?,
    val kind: String = "image",
    val source: String? = null,
)

/** Matches `.smm-channel-card__logo--*` colors in creator-social-media-manager-modal.css */
private fun channelLogoColor(key: String): Color = when (key) {
    "facebook" -> Color(0xFF1877F2)
    "instagram" -> Color(0xFFDD2A7B)
    "threads" -> Color(0xFF111111)
    "tiktok" -> Color(0xFF010101)
    "youtube" -> Color(0xFFFF0000)
    "snapchat" -> Color(0xFFFFFC00)
    "pinterest" -> Color(0xFFE60023)
    "tumblr" -> Color(0xFF001935)
    "linkedin" -> Color(0xFF0A66C2)
    "mastodon" -> Color(0xFF6364FF)
    "bluesky" -> Color(0xFF1185FE)
    else -> EazColors.Orange
}

/** Matches web `CHANNELS[].short` in creator-social-media-manager-modal.js */
private fun channelShortLabel(key: String): String = when (key) {
    "facebook" -> "f"
    "instagram" -> "Ig"
    "threads" -> "@"
    "tiktok" -> "Tk"
    "youtube" -> "YT"
    "snapchat" -> "Sc"
    "pinterest" -> "P"
    "tumblr" -> "t"
    "linkedin" -> "in"
    "mastodon" -> "M"
    "bluesky" -> "bsky"
    else -> key.take(2).uppercase()
}

private fun Modifier.dashedBorder(
    color: Color,
    cornerRadius: Dp,
    strokeWidth: Dp = 2.dp,
): Modifier = this.drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
        style = Stroke(
            width = strokeWidth.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 10f), 0f)
        )
    )
}

private data class SocialPostItem(
    val id: String,
    val caption: String,
    val channels: List<String>,
    val status: String,
    val scheduledAt: String?
)

@Composable
fun SocialMediaManagerScreen(
    visible: Boolean,
    onDismiss: () -> Unit,
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    oauthRefreshNonce: Int = 0,
) {
    if (!visible) return

    fun t(key: String, fallback: String): String = translationStore.t(key, fallback)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() ?: "" }

    var currentTab by remember { mutableIntStateOf(TAB_OVERVIEW) }
    var connectedChannels by remember { mutableStateOf<Set<String>>(emptySet()) }
    var facebookSkillUnlocked by remember { mutableStateOf(false) }
    var channelsLoading by remember { mutableStateOf(false) }
    var channelsRefreshNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(oauthRefreshNonce) {
        if (oauthRefreshNonce > 0) channelsRefreshNonce++
    }

    fun loadChannelStatus() {
        if (ownerId.isBlank()) return
        scope.launch {
            channelsLoading = true
            try {
                val resp = withContext(Dispatchers.IO) { api.creatorSocialChannelsStatus(ownerId) }
                if (resp.optBoolean("ok", false)) {
                    val arr = resp.optJSONArray("channels") ?: JSONArray()
                    val next = mutableSetOf<String>()
                    var fbUnlocked = false
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val channel = obj.optString("channel", "").takeIf { it.isNotBlank() } ?: continue
                        if (obj.optBoolean("connected", false)) next.add(channel)
                        if (channel == "facebook" && obj.optBoolean("skill_unlocked", false)) {
                            fbUnlocked = true
                        }
                    }
                    connectedChannels = next
                    facebookSkillUnlocked = fbUnlocked
                }
            } catch (_: Exception) {}
            channelsLoading = false
        }
    }

    LaunchedEffect(visible, ownerId, channelsRefreshNonce) {
        if (visible && ownerId.isNotBlank()) loadChannelStatus()
    }

    EazFullScreenDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(Color(0xFF0B1220))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Text(
                    text = t("creator.social_manager.title", "Social Media Manager"),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                SocialManagerSidebar(
                    currentTab = currentTab,
                    onTabSelect = { currentTab = it },
                    translationStore = translationStore
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    when (currentTab) {
                        TAB_OVERVIEW -> SocialOverviewPanel(translationStore = translationStore)
                        TAB_CONNECT -> SocialConnectPanel(
                            connectedChannels = connectedChannels,
                            loading = channelsLoading,
                            translationStore = translationStore,
                            onConnect = { channel ->
                                if (ownerId.isBlank()) return@SocialConnectPanel
                                scope.launch {
                                    try {
                                        // Assumed: POST ?op=creator-social-oauth-start – Body: owner_id, channel, platform -> { ok, auth_url }
                                        val resp = withContext(Dispatchers.IO) {
                                            api.creatorSocialOAuthStart(ownerId, channel, "android")
                                        }
                                        val authUrl = resp.optString("auth_url", "")
                                        if (resp.optBoolean("ok", false) && authUrl.isNotBlank()) {
                                            AuthBrowserLauncher.launchOAuth(context, authUrl)
                                        }
                                    } catch (_: Exception) {}
                                }
                            },
                            onDisconnect = { channel ->
                                if (ownerId.isBlank()) return@SocialConnectPanel
                                scope.launch {
                                    try {
                                        // Assumed: POST ?op=creator-social-disconnect – Body: owner_id, channel
                                        withContext(Dispatchers.IO) { api.creatorSocialDisconnect(ownerId, channel) }
                                        channelsRefreshNonce++
                                    } catch (_: Exception) {}
                                }
                            }
                        )
                        TAB_MANAGE_POSTS -> SocialManagePostsPanel(
                            ownerId = ownerId,
                            api = api,
                            translationStore = translationStore
                        )
                        TAB_NEW_POST -> SocialNewPostPanel(
                            ownerId = ownerId,
                            api = api,
                            connectedChannels = connectedChannels,
                            facebookSkillUnlocked = facebookSkillUnlocked,
                            translationStore = translationStore,
                            onPosted = { currentTab = TAB_MANAGE_POSTS }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SocialManagerSidebar(
    currentTab: Int,
    onTabSelect: (Int) -> Unit,
    translationStore: TranslationStore
) {
    fun t(key: String, fallback: String): String = translationStore.t(key, fallback)
    val items = listOf(
        Triple(TAB_OVERVIEW, Icons.Default.Dashboard, t("creator.social_manager.overview", "Overview")),
        Triple(TAB_CONNECT, Icons.Default.Link, t("creator.social_manager.connect", "Connect")),
        Triple(TAB_MANAGE_POSTS, Icons.Default.List, t("creator.social_manager.manage_posts", "Manage Posts")),
        Triple(TAB_NEW_POST, Icons.Default.Edit, t("creator.social_manager.new_post", "New Post")),
    )
    Column(
        modifier = Modifier
            .width(76.dp)
            .fillMaxHeight()
            .background(Color(0xFF0F0C1C).copy(alpha = 0.9f))
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items.forEach { (index, icon, label) ->
            val active = index == currentTab
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (active) EazColors.Orange.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { onTabSelect(index) }
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (active) EazColors.Orange else Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) EazColors.Orange else Color.White.copy(alpha = 0.65f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun SocialOverviewPanel(translationStore: TranslationStore) {
    fun t(key: String, fallback: String): String = translationStore.t(key, fallback)
    val metrics = listOf(
        t("creator.social_manager.metric_followers", "Followers"),
        t("creator.social_manager.metric_engagement", "Engagement"),
        t("creator.social_manager.metric_posts", "Posts"),
        t("creator.social_manager.metric_reach", "Reach"),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = t("creator.social_manager.overview", "Overview"),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Spacer(Modifier.height(14.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.height(180.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(metrics) { label ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .background(Color(0x99111827))
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.BarChart, null, tint = EazColors.Orange.copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("—", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                    Spacer(Modifier.height(2.dp))
                    Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.65f))
                }
            }
        }
    }
}

@Composable
private fun SocialConnectPanel(
    connectedChannels: Set<String>,
    loading: Boolean,
    translationStore: TranslationStore,
    onConnect: (String) -> Unit,
    onDisconnect: (String) -> Unit,
) {
    fun t(key: String, fallback: String): String = translationStore.t(key, fallback)
    var confirmDisconnectChannel by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = t("creator.social_manager.connect", "Connect"),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = t("creator.social_manager.connect_subtitle", "Link your social channels to publish from eazpire."),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(14.dp))

        SOCIAL_CHANNELS.forEach { channel ->
            val connected = connectedChannels.contains(channel.key)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x99111827))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(channel.label, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                    Text(
                        text = when {
                            !channel.live -> t("creator.common.coming_soon", "Coming soon")
                            connected -> t("creator.social_manager.connected", "Connected")
                            else -> t("creator.social_manager.not_connected", "Not connected")
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            !channel.live -> EazColors.Orange.copy(alpha = 0.8f)
                            connected -> Color(0xFF34D399)
                            else -> Color.White.copy(alpha = 0.5f)
                        }
                    )
                }
                when {
                    !channel.live -> Text(
                        text = t("creator.common.coming_soon", "Coming soon"),
                        style = MaterialTheme.typography.labelSmall,
                        color = EazColors.Orange,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(EazColors.Orange.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                    connected -> OutlinedButton(onClick = { confirmDisconnectChannel = channel.key }) {
                        Text(t("creator.social_manager.disconnect", "Disconnect"), color = Color.White.copy(alpha = 0.85f))
                    }
                    else -> Button(
                        onClick = { onConnect(channel.key) },
                        enabled = !loading,
                        colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange, contentColor = Color.White)
                    ) {
                        Text(t("creator.social_manager.connect_action", "Connect"))
                    }
                }
            }
        }
    }

    confirmDisconnectChannel?.let { channelKey ->
        val channel = SOCIAL_CHANNELS.firstOrNull { it.key == channelKey }
        AlertDialog(
            onDismissRequest = { confirmDisconnectChannel = null },
            containerColor = Color(0xFF1F2937),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.88f),
            title = { Text(t("creator.social_manager.disconnect_confirm_title", "Disconnect channel?")) },
            text = { Text("${channel?.label ?: channelKey} — ${t("creator.social_manager.disconnect_confirm_body", "You can reconnect it any time.")}") },
            confirmButton = {
                TextButton(onClick = {
                    onDisconnect(channelKey)
                    confirmDisconnectChannel = null
                }) { Text(t("creator.common.confirm", "Confirm"), color = EazColors.Orange) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnectChannel = null }) {
                    Text(t("creator.common.cancel", "Cancel"), color = Color.White.copy(alpha = 0.85f))
                }
            }
        )
    }
}

@Composable
private fun SocialManagePostsPanel(
    ownerId: String,
    api: CreatorApi,
    translationStore: TranslationStore,
) {
    fun t(key: String, fallback: String): String = translationStore.t(key, fallback)
    val scope = rememberCoroutineScope()
    var posts by remember { mutableStateOf<List<SocialPostItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) {
            loading = false
            return@LaunchedEffect
        }
        loading = true
        try {
            // Assumed: GET ?op=creator-social-posts-list&owner_id=&limit= -> { ok, items:[{id,caption,channels,status,scheduled_at}] }
            val resp = withContext(Dispatchers.IO) { api.creatorSocialPostsList(ownerId, 50) }
            if (resp.optBoolean("ok", false)) {
                val arr = resp.optJSONArray("items") ?: JSONArray()
                posts = (0 until arr.length()).mapNotNull { i ->
                    val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                    val channelsArr = obj.optJSONArray("channels") ?: JSONArray()
                    SocialPostItem(
                        id = obj.optString("id", ""),
                        caption = obj.optString("caption", ""),
                        channels = (0 until channelsArr.length()).mapNotNull { j -> channelsArr.optString(j, "").takeIf { it.isNotBlank() } },
                        status = obj.optString("status", "draft"),
                        scheduledAt = obj.optString("scheduled_at", "").takeIf { it.isNotBlank() }
                    )
                }
            }
        } catch (_: Exception) {}
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = t("creator.social_manager.manage_posts", "Manage Posts"),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Spacer(Modifier.height(14.dp))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EazColors.Orange)
            }
            posts.isEmpty() -> Text(
                text = t("creator.social_manager.no_posts_yet", "No posts yet."),
                color = Color.White.copy(alpha = 0.55f),
                style = MaterialTheme.typography.bodySmall
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(posts, key = { it.id }) { post ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x99111827))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = post.caption.ifBlank { t("creator.social_manager.untitled_post", "Untitled post") },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            maxLines = 3
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = post.channels.joinToString(", ").ifBlank { "—" },
                            style = MaterialTheme.typography.labelSmall,
                            color = EazColors.Orange
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = buildString {
                                append(post.status.replaceFirstChar { it.uppercase() })
                                post.scheduledAt?.let { append(" · $it") }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SocialNewPostPanel(
    ownerId: String,
    api: CreatorApi,
    connectedChannels: Set<String>,
    facebookSkillUnlocked: Boolean,
    translationStore: TranslationStore,
    onPosted: () -> Unit,
) {
    fun t(key: String, fallback: String): String = translationStore.t(key, fallback)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedAsset by remember { mutableStateOf<SocialAssetItem?>(null) }

    var showSourceModal by remember { mutableStateOf(false) }
    var sourceTab by remember { mutableStateOf("library") }
    var libraryAssets by remember { mutableStateOf<List<SocialAssetItem>>(emptyList()) }
    var libraryLoading by remember { mutableStateOf(false) }
    var deviceUploading by remember { mutableStateOf(false) }
    var deviceError by remember { mutableStateOf<String?>(null) }
    var linkUrlInput by remember { mutableStateOf("") }
    var linkError by remember { mutableStateOf<String?>(null) }

    var caption by remember { mutableStateOf("") }
    var postLink by remember { mutableStateOf("") }

    var channelsExpanded by remember { mutableStateOf(true) }
    val channelEnabled = remember { mutableStateMapOf<String, Boolean>() }
    var expandedChannel by remember { mutableStateOf<String?>(null) }
    var fbPostType by remember { mutableStateOf("photo") }
    var fbDestination by remember { mutableStateOf("pages") }
    var tiktokPrivacy by remember { mutableStateOf("SELF_ONLY") }

    var scheduleEnabled by remember { mutableStateOf(false) }
    var scheduleExpanded by remember { mutableStateOf(false) }
    var scheduleDate by remember { mutableStateOf("") }
    var scheduleTime by remember { mutableStateOf("") }

    var showConfirmDialog by remember { mutableStateOf(false) }
    var posting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusError by remember { mutableStateOf(false) }

    val facebookPageConnected = connectedChannels.contains("facebook")
    val liveComposeChannels = SOCIAL_CHANNELS.filter {
        it.live && (
            connectedChannels.contains(it.key) ||
                (it.key == "facebook" && facebookSkillUnlocked)
            )
    }
    val enabledChannels = liveComposeChannels.filter { channelEnabled[it.key] != false }

    LaunchedEffect(liveComposeChannels.map { it.key }, facebookPageConnected) {
        liveComposeChannels.forEach { channel ->
            if (channel.key !in channelEnabled) channelEnabled[channel.key] = true
        }
        if (!facebookPageConnected && facebookSkillUnlocked && fbDestination == "pages") {
            fbDestination = "profile"
        }
    }

    LaunchedEffect(scheduleEnabled) {
        if (scheduleEnabled && fbDestination == "profile") {
            fbDestination = if (facebookPageConnected) "pages" else "pages"
        }
    }

    fun loadLibraryAssets() {
        if (ownerId.isBlank()) return
        scope.launch {
            libraryLoading = true
            try {
                // GET ?op=creator-social-compose-assets&owner_id= -> { ok, items:[{id,source,kind,url,thumb_url,label}] }
                val resp = withContext(Dispatchers.IO) { api.composerAssets(ownerId) }
                if (resp.optBoolean("ok", false)) {
                    val arr = resp.optJSONArray("items") ?: JSONArray()
                    libraryAssets = (0 until arr.length()).mapNotNull { i ->
                        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                        val kind = if (obj.optString("kind", "image") == "video") "video" else "image"
                        SocialAssetItem(
                            id = obj.optString("id", ""),
                            url = obj.optString("url", "").takeIf { it.isNotBlank() },
                            thumbnailUrl = obj.optString("thumb_url", obj.optString("thumbnail_url", ""))
                                .takeIf { it.isNotBlank() },
                            kind = kind,
                            source = obj.optString("source", "").takeIf { it.isNotBlank() }
                        )
                    }.filter { it.kind != "audio" }
                }
            } catch (_: Exception) {}
            libraryLoading = false
        }
    }

    LaunchedEffect(showSourceModal, sourceTab) {
        if (showSourceModal && sourceTab == "library") loadLibraryAssets()
    }

    val devicePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            deviceUploading = true
            deviceError = null
            try {
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val isVideo = mime.startsWith("video/")
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (bytes == null) {
                    deviceError = t("creator.social_media_manager.error_load_assets", "Could not load assets.")
                    return@launch
                }
                val resp = withContext(Dispatchers.IO) {
                    if (isVideo) {
                        api.uploadVideoMotionRef(ownerId, bytes, "smm_upload.mp4", mime)
                    } else {
                        api.uploadHeroImage(ownerId, "smm_compose", bytes, mime)
                    }
                }
                val url = resp.optString("url", "")
                    .ifBlank { resp.optString("video_url", "") }
                    .ifBlank { resp.optString("image_url", "") }
                    .ifBlank { resp.optString("public_url", "") }
                if (resp.optBoolean("ok", false) && url.isNotBlank()) {
                    selectedAsset = SocialAssetItem(
                        id = "upload_${System.currentTimeMillis()}",
                        url = url,
                        thumbnailUrl = url,
                        kind = if (isVideo) "video" else "image",
                        source = "upload"
                    )
                    showSourceModal = false
                } else {
                    deviceError = resp.optString("message", "").ifBlank { resp.optString("error", "") }
                        .ifBlank { t("creator.social_media_manager.error_load_assets", "Upload failed.") }
                }
            } catch (e: Exception) {
                deviceError = e.message?.take(200)
                    ?: t("creator.social_media_manager.error_network", "Network error. Please try again.")
            } finally {
                deviceUploading = false
            }
        }
    }

    fun applyLinkAsset() {
        val raw = linkUrlInput.trim()
        if (!raw.startsWith("http://", ignoreCase = true) && !raw.startsWith("https://", ignoreCase = true)) {
            linkError = t("creator.social_media_manager.error_invalid_url", "Enter a valid http(s) URL.")
            return
        }
        val lower = raw.lowercase()
        val isVideo = Regex("\\.(mp4|webm|mov)(\\?|$)").containsMatchIn(lower) || lower.contains("video")
        selectedAsset = SocialAssetItem(
            id = "link_${System.currentTimeMillis()}",
            url = raw,
            thumbnailUrl = raw,
            kind = if (isVideo) "video" else "image",
            source = "link"
        )
        linkUrlInput = ""
        linkError = null
        showSourceModal = false
    }

    fun submitPost() {
        if (ownerId.isBlank() || posting) return
        scope.launch {
            posting = true
            statusMessage = null
            statusError = false
            try {
                val enabledChannelKeys = enabledChannels.map { it.key }
                val body = JSONObject()
                    .put("caption", caption.trim())
                    .put("link", postLink.trim())
                    .put("link_url", postLink.trim())
                    .put("channels", JSONArray(enabledChannelKeys))
                selectedAsset?.let { asset ->
                    asset.id.takeIf { it.isNotBlank() }?.let { body.put("asset_id", it) }
                    asset.url?.let { body.put("media_url", it) }
                    body.put("media_kind", asset.kind)
                    asset.source?.let { body.put("asset_source", it) }
                }
                val channelSettings = JSONObject()
                if (enabledChannelKeys.contains("facebook")) {
                    val dest = when {
                        scheduleEnabled -> "pages"
                        else -> fbDestination
                    }
                    channelSettings.put(
                        "facebook",
                        JSONObject()
                            .put("post_type", fbPostType)
                            .put("destination", dest),
                    )
                }
                if (enabledChannelKeys.contains("tiktok")) {
                    channelSettings.put("tiktok", JSONObject().put("privacy_level", tiktokPrivacy))
                }
                if (channelSettings.length() > 0) body.put("channel_settings", channelSettings)
                if (scheduleEnabled && scheduleDate.isNotBlank() && scheduleTime.isNotBlank()) {
                    body.put("scheduled_at", "${scheduleDate.trim()}T${scheduleTime.trim()}:00")
                    body.put("mode", "schedule")
                } else {
                    body.put("mode", "post")
                }
                val resp = withContext(Dispatchers.IO) { api.creatorSocialPostsCreate(ownerId, body) }
                if (resp.optBoolean("ok", false)) {
                    val shareDialogs = resp.optJSONArray("share_dialogs")
                    if (shareDialogs != null && shareDialogs.length() > 0) {
                        for (i in 0 until shareDialogs.length()) {
                            val dialogUrl = shareDialogs.optJSONObject(i)
                                ?.optString("dialog_url", "")
                                ?.takeIf { it.isNotBlank() }
                                ?: continue
                            try {
                                CustomTabsIntent.Builder()
                                    .setShowTitle(true)
                                    .build()
                                    .launchUrl(context, Uri.parse(dialogUrl))
                            } catch (_: Exception) {
                                try {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            Uri.parse(dialogUrl),
                                        ),
                                    )
                                } catch (_: Exception) {
                                }
                            }
                        }
                    }
                    caption = ""
                    postLink = ""
                    selectedAsset = null
                    expandedChannel = null
                    scheduleEnabled = false
                    scheduleExpanded = false
                    scheduleDate = ""
                    scheduleTime = ""
                    statusMessage = resp.optString("message", "").ifBlank {
                        if (resp.optString("status", "") == "planned") {
                            t("creator.social_media_manager.scheduled_ok", "Post scheduled.")
                        } else {
                            t("creator.social_media_manager.posted_ok", "Post published.")
                        }
                    }
                    statusError = false
                    onPosted()
                } else {
                    statusMessage = resp.optString("message", "").ifBlank { resp.optString("error", "") }
                        .ifBlank { t("creator.social_media_manager.error_submit", "Could not submit post.") }
                    statusError = true
                }
            } catch (e: Exception) {
                statusMessage = e.message?.take(200)
                    ?: t("creator.social_media_manager.error_network", "Network error. Please try again.")
                statusError = true
            } finally {
                posting = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = t("creator.social_media_manager.new_post_title", "New Post"),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Spacer(Modifier.height(14.dp))

        // Viewer: large dashed upload area, or preview + change (×) button once an asset is picked.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            val asset = selectedAsset
            if (asset?.url.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .dashedBorder(color = EazColors.Orange.copy(alpha = 0.6f), cornerRadius = 16.dp)
                        .clickable { showSourceModal = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = t("creator.social_media_manager.choose_asset", "Choose asset"),
                        tint = EazColors.Orange,
                        modifier = Modifier.size(44.dp)
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                ) {
                    val thumb = asset?.thumbnailUrl ?: asset?.url
                    AsyncImage(
                        model = thumb,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    if (asset?.kind == "video") {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                    IconButton(
                        onClick = { selectedAsset = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = t("creator.social_media_manager.change_asset", "Change asset"),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = t("creator.social_media_manager.caption_label", "Caption"),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.75f)
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = caption,
            onValueChange = { caption = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(t("creator.social_media_manager.caption_placeholder", "Write your post caption…")) },
            minLines = 3,
            maxLines = 6,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = EazColors.Orange,
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedContainerColor = Color(0x99312937),
                unfocusedContainerColor = Color(0x99312937)
            )
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = t("creator.social_media_manager.link_label", "Link URL (optional)"),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.75f)
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = postLink,
            onValueChange = { postLink = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("https://…") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = EazColors.Orange,
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedContainerColor = Color(0x99312937),
                unfocusedContainerColor = Color(0x99312937)
            )
        )

        // Channels — collapsible section.
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { channelsExpanded = !channelsExpanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (channelsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = t("creator.social_media_manager.channels_label", "Channels"),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White
            )
        }
        if (channelsExpanded) {
            Spacer(Modifier.height(8.dp))
            if (liveComposeChannels.isEmpty()) {
                Text(
                    text = t(
                        "creator.social_media_manager.no_targets_with_hint",
                        "Connect a Facebook Page in Connect, or unlock Facebook in Creator Journey to share to your profile.",
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.55f)
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(liveComposeChannels, key = { it.key }) { channel ->
                        val enabled = channelEnabled[channel.key] != false
                        val expanded = enabled && expandedChannel == channel.key
                        SocialChannelCard(
                            channel = channel,
                            enabled = enabled,
                            expanded = expanded,
                            translationStore = translationStore,
                            onToggleEnabled = { checked ->
                                channelEnabled[channel.key] = checked
                                if (!checked && expandedChannel == channel.key) expandedChannel = null
                            },
                            onTapBody = {
                                if (!enabled) return@SocialChannelCard
                                expandedChannel = if (expandedChannel == channel.key) null else channel.key
                            }
                        )
                    }
                }
                expandedChannel?.let { chKey ->
                    if (channelEnabled[chKey] != false) {
                        Spacer(Modifier.height(12.dp))
                        SocialChannelSettingsPanel(
                            channelKey = chKey,
                            fbPostType = fbPostType,
                            onFbPostTypeChange = { fbPostType = it },
                            fbDestination = fbDestination,
                            onFbDestinationChange = { fbDestination = it },
                            facebookPageConnected = facebookPageConnected,
                            facebookProfileAvailable = facebookSkillUnlocked,
                            scheduleEnabled = scheduleEnabled,
                            tiktokPrivacy = tiktokPrivacy,
                            onTiktokPrivacyChange = { tiktokPrivacy = it },
                            mediaKind = selectedAsset?.kind ?: "image",
                            translationStore = translationStore
                        )
                    }
                }
            }
        }

        // Schedule — collapsible section with the enable switch on the right of the header.
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = scheduleEnabled) { scheduleExpanded = !scheduleExpanded }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (scheduleExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = t("creator.social_media_manager.schedule_label", "Schedule"),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White
                )
            }
            Switch(
                checked = scheduleEnabled,
                onCheckedChange = { checked ->
                    scheduleEnabled = checked
                    scheduleExpanded = checked
                },
                colors = SwitchDefaults.colors(checkedTrackColor = EazColors.Orange)
            )
        }
        if (scheduleEnabled && scheduleExpanded) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = scheduleDate,
                    onValueChange = { scheduleDate = it },
                    singleLine = true,
                    placeholder = { Text("YYYY-MM-DD") },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = EazColors.Orange,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                    )
                )
                OutlinedTextField(
                    value = scheduleTime,
                    onValueChange = { scheduleTime = it },
                    singleLine = true,
                    placeholder = { Text("HH:mm") },
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = EazColors.Orange,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                    )
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { showConfirmDialog = true },
            enabled = !posting && ownerId.isNotBlank() && !selectedAsset?.url.isNullOrBlank() && enabledChannels.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange, contentColor = Color.White)
        ) {
            if (posting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(t("creator.social_media_manager.btn_post", "Post"))
        }
        statusMessage?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = if (statusError) EazColors.Orange else Color(0xFF34D399)
            )
        }
        Spacer(Modifier.height(20.dp))
    }

    if (showSourceModal) {
        SocialAssetSourceDialog(
            sourceTab = sourceTab,
            onTabChange = {
                sourceTab = it
                deviceError = null
                linkError = null
            },
            libraryAssets = libraryAssets,
            libraryLoading = libraryLoading,
            onPickLibraryAsset = { asset ->
                selectedAsset = asset
                showSourceModal = false
            },
            onBrowseDevice = { devicePicker.launch("*/*") },
            deviceUploading = deviceUploading,
            deviceError = deviceError,
            linkUrlInput = linkUrlInput,
            onLinkUrlChange = { linkUrlInput = it },
            linkError = linkError,
            onApplyLink = { applyLinkAsset() },
            onDismiss = { showSourceModal = false },
            translationStore = translationStore
        )
    }

    if (showConfirmDialog) {
        val enabledChannelKeys = enabledChannels.map { it.key }
        AlertDialog(
            onDismissRequest = { if (!posting) showConfirmDialog = false },
            containerColor = Color(0xFF1F2937),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.88f),
            title = {
                Text(
                    if (scheduleEnabled) {
                        t("creator.social_media_manager.confirm_schedule_title", "Schedule post")
                    } else {
                        t("creator.social_media_manager.confirm_post_title", "Confirm post")
                    }
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${t("creator.social_media_manager.confirm_summary_caption", "Caption")}: ${caption.ifBlank { "—" }}")
                    Text("${t("creator.social_media_manager.confirm_summary_link", "Link")}: ${postLink.ifBlank { "—" }}")
                    val channelLines = enabledChannelKeys.joinToString(", ") { key ->
                        val name = SOCIAL_CHANNELS.firstOrNull { it.key == key }?.label ?: key
                        when (key) {
                            "facebook" -> {
                                val destLabel = when (fbDestination) {
                                    "profile" -> t(
                                        "creator.social_media_manager.facebook_destination_profile",
                                        "My profile (Share dialog)",
                                    )
                                    "both" -> t(
                                        "creator.social_media_manager.facebook_destination_both",
                                        "Pages + my profile",
                                    )
                                    else -> t(
                                        "creator.social_media_manager.facebook_destination_pages",
                                        "Connected pages",
                                    )
                                }
                                "$name ($destLabel)"
                            }
                            "tiktok" -> "$name (${tiktokPrivacy.replace("_", " ")})"
                            else -> name
                        }
                    }
                    Text("${t("creator.social_media_manager.confirm_summary_channels", "Channels")}: ${channelLines.ifBlank { "—" }}")
                    Text(
                        "${t("creator.social_media_manager.confirm_summary_schedule", "Schedule")}: " +
                            if (scheduleEnabled && scheduleDate.isNotBlank() && scheduleTime.isNotBlank()) {
                                "$scheduleDate $scheduleTime"
                            } else {
                                t("creator.social_media_manager.confirm_summary_immediate", "Post now")
                            }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    submitPost()
                }) {
                    Text(
                        if (scheduleEnabled) {
                            t("creator.social_media_manager.btn_confirm_schedule", "Confirm & schedule")
                        } else {
                            t("creator.social_media_manager.btn_confirm_post", "Confirm & post")
                        },
                        color = EazColors.Orange
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(t("creator.social_media_manager.btn_cancel", "Cancel"), color = Color.White.copy(alpha = 0.85f))
                }
            }
        )
    }
}

/** Asset picker modal: Library (composerAssets) / Device (upload) / Link (paste URL) tabs — matches web "smm-asset-picker". */
@Composable
private fun SocialAssetSourceDialog(
    sourceTab: String,
    onTabChange: (String) -> Unit,
    libraryAssets: List<SocialAssetItem>,
    libraryLoading: Boolean,
    onPickLibraryAsset: (SocialAssetItem) -> Unit,
    onBrowseDevice: () -> Unit,
    deviceUploading: Boolean,
    deviceError: String?,
    linkUrlInput: String,
    onLinkUrlChange: (String) -> Unit,
    linkError: String?,
    onApplyLink: () -> Unit,
    onDismiss: () -> Unit,
    translationStore: TranslationStore,
) {
    fun t(key: String, fallback: String): String = translationStore.t(key, fallback)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1F2937))
                .padding(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = t("creator.social_media_manager.asset_picker_title", "Choose asset"),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = t("creator.social_media_manager.close", "Close"), tint = Color.White)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "library" to t("creator.social_media_manager.source_library", "Library"),
                    "device" to t("creator.social_media_manager.source_device", "Device"),
                    "link" to t("creator.social_media_manager.source_link", "Link"),
                ).forEach { (key, label) ->
                    val active = sourceTab == key
                    Text(
                        text = label,
                        color = if (active) Color.White else Color.White.copy(alpha = 0.65f),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (active) EazColors.Orange else Color.White.copy(alpha = 0.08f))
                            .clickable { onTabChange(key) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            when (sourceTab) {
                "device" -> {
                    Text(
                        text = t("creator.social_media_manager.source_device_hint", "Pick an image or video from this device."),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onBrowseDevice,
                        enabled = !deviceUploading,
                        colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange, contentColor = Color.White)
                    ) {
                        if (deviceUploading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(t("creator.social_media_manager.source_device_browse", "Browse files"))
                    }
                    deviceError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = EazColors.Orange)
                    }
                }
                "link" -> {
                    Text(
                        text = t("creator.social_media_manager.source_link_hint", "Paste a direct image or video URL."),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = t("creator.social_media_manager.link_label", "Link URL (optional)"),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = linkUrlInput,
                        onValueChange = onLinkUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("https://…") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = EazColors.Orange,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f)
                        )
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onApplyLink,
                        colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange, contentColor = Color.White)
                    ) {
                        Text(t("creator.social_media_manager.source_link_apply", "Use link"))
                    }
                    linkError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = EazColors.Orange)
                    }
                }
                else -> {
                    Text(
                        text = t("creator.social_media_manager.asset_picker_hint", "Images and videos from your library. Audio is excluded."),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(10.dp))
                    when {
                        libraryLoading -> Box(
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = EazColors.Orange)
                        }
                        libraryAssets.isEmpty() -> Text(
                            text = t(
                                "creator.social_media_manager.asset_picker_empty",
                                "No assets yet. Upload in Video Studio or generate images first."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                        else -> LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.heightIn(max = 340.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(libraryAssets, key = { it.id }) { asset ->
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.06f))
                                        .clickable { onPickLibraryAsset(asset) }
                                ) {
                                    val thumb = asset.thumbnailUrl ?: asset.url
                                    if (!thumb.isNullOrBlank()) {
                                        AsyncImage(
                                            model = thumb,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    if (asset.kind == "video") {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.align(Alignment.Center).size(22.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Compact channel card used in the Channels carousel — colored logo letter + name + status + enable switch. */
@Composable
private fun SocialChannelCard(
    channel: SocialChannelDef,
    enabled: Boolean,
    expanded: Boolean,
    translationStore: TranslationStore,
    onToggleEnabled: (Boolean) -> Unit,
    onTapBody: () -> Unit,
) {
    fun t(key: String, fallback: String): String = translationStore.t(key, fallback)
    Column(
        modifier = Modifier
            .width(134.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x99111827))
            .border(
                1.dp,
                if (expanded) EazColors.Orange else Color.White.copy(alpha = if (enabled) 0.18f else 0.08f),
                RoundedCornerShape(12.dp)
            )
            .clickable(enabled = enabled) { onTapBody() }
            .padding(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(channelLogoColor(channel.key)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = channelShortLabel(channel.key),
                    color = if (channel.key == "snapchat") Color(0xFF111111) else Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Spacer(Modifier.weight(1f))
            Switch(
                checked = enabled,
                onCheckedChange = onToggleEnabled,
                colors = SwitchDefaults.colors(checkedTrackColor = EazColors.Orange)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = t("creator.social_media_manager.channel_${channel.key}", channel.label),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (enabled) {
                t("creator.social_media_manager.channel_enabled", "Enabled")
            } else {
                t("creator.social_media_manager.channel_disabled", "Disabled")
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) Color(0xFF34D399) else Color.White.copy(alpha = 0.45f)
        )
    }
}

/** Per-channel settings shown below the carousel when a channel is enabled + expanded. */
@Composable
private fun SocialChannelSettingsPanel(
    channelKey: String,
    fbPostType: String,
    onFbPostTypeChange: (String) -> Unit,
    fbDestination: String,
    onFbDestinationChange: (String) -> Unit,
    facebookPageConnected: Boolean,
    facebookProfileAvailable: Boolean,
    scheduleEnabled: Boolean,
    tiktokPrivacy: String,
    onTiktokPrivacyChange: (String) -> Unit,
    mediaKind: String,
    translationStore: TranslationStore,
) {
    fun t(key: String, fallback: String): String = translationStore.t(key, fallback)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x99111827))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(
            text = SOCIAL_CHANNELS.firstOrNull { it.key == channelKey }?.label ?: channelKey,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White
        )
        Spacer(Modifier.height(10.dp))
        when (channelKey) {
            "facebook" -> {
                val destOptions = buildList {
                    if (facebookPageConnected) {
                        add(
                            "pages" to t(
                                "creator.social_media_manager.facebook_destination_pages",
                                "Connected pages",
                            ),
                        )
                    }
                    if (facebookProfileAvailable && !scheduleEnabled) {
                        add(
                            "profile" to t(
                                "creator.social_media_manager.facebook_destination_profile",
                                "My profile (Share dialog)",
                            ),
                        )
                    }
                    if (facebookPageConnected && facebookProfileAvailable && !scheduleEnabled) {
                        add(
                            "both" to t(
                                "creator.social_media_manager.facebook_destination_both",
                                "Pages + my profile",
                            ),
                        )
                    }
                }
                if (destOptions.isNotEmpty()) {
                    SocialDropdownField(
                        label = t("creator.social_media_manager.facebook_destination", "Post to"),
                        selected = fbDestination,
                        options = destOptions,
                        onSelect = onFbDestinationChange,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (fbDestination == "profile" || fbDestination == "both") {
                            t(
                                "creator.social_media_manager.facebook_profile_share_note",
                                "Profile posts open Facebook so you can confirm. Add a link for the best preview. Scheduling is for pages only.",
                            )
                        } else {
                            t(
                                "creator.social_media_manager.facebook_pages_note",
                                "Posts to your connected Facebook Pages automatically.",
                            )
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (fbDestination == "pages" || fbDestination == "both") {
                    SocialDropdownField(
                        label = t("creator.social_media_manager.facebook_post_type", "Post type"),
                        selected = fbPostType,
                        options = listOf(
                            "photo" to t("creator.social_media_manager.facebook_post_photo", "Photo post"),
                            "link" to t("creator.social_media_manager.facebook_post_link", "Link post"),
                        ),
                        onSelect = onFbPostTypeChange,
                    )
                }
            }
            "tiktok" -> {
                if (mediaKind != "video") {
                    Text(
                        text = t("creator.social_media_manager.tiktok_needs_video", "TikTok posts require a video asset."),
                        style = MaterialTheme.typography.bodySmall,
                        color = EazColors.Orange.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(8.dp))
                }
                SocialDropdownField(
                    label = t("creator.social_media_manager.tiktok_privacy", "Privacy"),
                    selected = tiktokPrivacy,
                    options = listOf(
                        "SELF_ONLY" to "SELF ONLY",
                        "MUTUAL_FOLLOW_FRIENDS" to "MUTUAL FOLLOW FRIENDS",
                        "PUBLIC_TO_EVERYONE" to "PUBLIC TO EVERYONE",
                    ),
                    onSelect = onTiktokPrivacyChange
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = t(
                        "creator.social_media_manager.tiktok_sandbox_note",
                        "Sandbox apps may publish private posts until TikTok app review."
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            else -> Text(
                text = t("creator.social_media_manager.channel_publish_soon", "Publishing to this channel is coming soon."),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

/** Minimal dropdown field (label + tappable value + menu) used for Facebook post type / TikTok privacy. */
@Composable
private fun SocialDropdownField(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: selected
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(4.dp))
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selectedLabel, style = MaterialTheme.typography.bodySmall, color = Color.White, modifier = Modifier.weight(1f))
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, optionLabel) ->
                    DropdownMenuItem(
                        text = { Text(optionLabel) },
                        onClick = {
                            onSelect(value)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
