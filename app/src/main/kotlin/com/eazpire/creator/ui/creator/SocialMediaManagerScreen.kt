package com.eazpire.creator.ui.creator

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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

private data class SocialAssetItem(val id: String, val url: String?, val thumbnailUrl: String?)

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
                // Assumed: GET ?op=creator-social-channels-status&owner_id= -> { ok, channels:[{channel, connected}] }
                val resp = withContext(Dispatchers.IO) { api.creatorSocialChannelsStatus(ownerId) }
                if (resp.optBoolean("ok", false)) {
                    val arr = resp.optJSONArray("channels") ?: JSONArray()
                    val next = mutableSetOf<String>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        if (obj.optBoolean("connected", false)) {
                            obj.optString("channel", "").takeIf { it.isNotBlank() }?.let { next.add(it) }
                        }
                    }
                    connectedChannels = next
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
    translationStore: TranslationStore,
    onPosted: () -> Unit,
) {
    fun t(key: String, fallback: String): String = translationStore.t(key, fallback)
    val scope = rememberCoroutineScope()

    var assets by remember { mutableStateOf<List<SocialAssetItem>>(emptyList()) }
    var assetsLoading by remember { mutableStateOf(true) }
    var selectedAssetId by remember { mutableStateOf<String?>(null) }
    var caption by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var selectedChannels by remember { mutableStateOf<Set<String>>(emptySet()) }
    var scheduleEnabled by remember { mutableStateOf(false) }
    var scheduleDate by remember { mutableStateOf("") }
    var scheduleTime by remember { mutableStateOf("") }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var posting by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusError by remember { mutableStateOf(false) }

    val liveConnectedChannels = SOCIAL_CHANNELS.filter { it.live && connectedChannels.contains(it.key) }

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) {
            assetsLoading = false
            return@LaunchedEffect
        }
        assetsLoading = true
        try {
            // Assumed: GET ?op=composer-assets&owner_id= -> { ok, items:[{id,url,thumbnail_url}] }
            val resp = withContext(Dispatchers.IO) { api.composerAssets(ownerId) }
            if (resp.optBoolean("ok", false)) {
                val arr = resp.optJSONArray("items") ?: JSONArray()
                assets = (0 until arr.length()).mapNotNull { i ->
                    val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                    SocialAssetItem(
                        id = obj.optString("id", ""),
                        url = obj.optString("url", "").takeIf { it.isNotBlank() },
                        thumbnailUrl = obj.optString("thumbnail_url", "").takeIf { it.isNotBlank() }
                    )
                }
            }
        } catch (_: Exception) {}
        assetsLoading = false
    }

    fun submitPost() {
        if (ownerId.isBlank() || posting) return
        scope.launch {
            posting = true
            statusMessage = null
            statusError = false
            try {
                val body = JSONObject()
                    .put("caption", caption.trim())
                    .put("link", link.trim())
                    .put("channels", JSONArray(selectedChannels.toList()))
                selectedAssetId?.let { body.put("asset_id", it) }
                if (scheduleEnabled && scheduleDate.isNotBlank() && scheduleTime.isNotBlank()) {
                    body.put("scheduled_at", "${scheduleDate.trim()}T${scheduleTime.trim()}:00")
                }
                // Assumed: POST ?op=creator-social-posts-create – Body: owner_id + JSON above
                val resp = withContext(Dispatchers.IO) { api.creatorSocialPostsCreate(ownerId, body) }
                if (resp.optBoolean("ok", false)) {
                    caption = ""
                    link = ""
                    selectedChannels = emptySet()
                    selectedAssetId = null
                    scheduleEnabled = false
                    scheduleDate = ""
                    scheduleTime = ""
                    onPosted()
                } else {
                    statusMessage = resp.optString("error", "")
                        .ifBlank { t("creator.social_manager.post_failed", "Posting failed.") }
                    statusError = true
                }
            } catch (e: Exception) {
                statusMessage = e.message?.take(200) ?: t("creator.social_manager.network_error", "Network error.")
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
            text = t("creator.social_manager.new_post", "New Post"),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Spacer(Modifier.height(14.dp))

        Text(
            text = t("creator.social_manager.select_asset", "Select an asset"),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.75f)
        )
        Spacer(Modifier.height(8.dp))
        when {
            assetsLoading -> Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EazColors.Orange)
            }
            assets.isEmpty() -> Text(
                text = t("creator.social_manager.no_assets_yet", "No assets yet — generate hero images or videos first."),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f)
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.height(200.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(assets, key = { it.id }) { asset ->
                    val selected = selectedAssetId == asset.id
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                2.dp,
                                if (selected) EazColors.Orange else Color.White.copy(alpha = 0.15f),
                                RoundedCornerShape(8.dp)
                            )
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable { selectedAssetId = if (selected) null else asset.id }
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
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = caption,
            onValueChange = { caption = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(t("creator.social_manager.caption_placeholder", "Write a caption...")) },
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
        OutlinedTextField(
            value = link,
            onValueChange = { link = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(t("creator.social_manager.link_placeholder", "Link (optional)")) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = EazColors.Orange,
                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                focusedContainerColor = Color(0x99312937),
                unfocusedContainerColor = Color(0x99312937)
            )
        )

        Spacer(Modifier.height(14.dp))
        Text(
            text = t("creator.social_manager.channels", "Channels"),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.75f)
        )
        Spacer(Modifier.height(8.dp))
        if (liveConnectedChannels.isEmpty()) {
            Text(
                text = t("creator.social_manager.no_connected_channels", "Connect a channel first (see Connect tab)."),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.55f)
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                liveConnectedChannels.forEach { channel ->
                    val active = selectedChannels.contains(channel.key)
                    Text(
                        text = channel.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) Color.White else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .border(
                                1.dp,
                                if (active) EazColors.Orange else Color.White.copy(alpha = 0.2f),
                                RoundedCornerShape(999.dp)
                            )
                            .background(if (active) EazColors.Orange.copy(alpha = 0.25f) else Color.Transparent)
                            .clickable {
                                selectedChannels = if (active) {
                                    selectedChannels - channel.key
                                } else {
                                    selectedChannels + channel.key
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = t("creator.social_manager.schedule", "Schedule for later"),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Switch(
                checked = scheduleEnabled,
                onCheckedChange = { scheduleEnabled = it },
                colors = SwitchDefaults.colors(checkedTrackColor = EazColors.Orange)
            )
        }
        if (scheduleEnabled) {
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

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { showConfirmDialog = true },
            enabled = !posting && ownerId.isNotBlank() && selectedChannels.isNotEmpty() && caption.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange, contentColor = Color.White)
        ) {
            if (posting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(t("creator.social_manager.post", "Post"))
        }
        statusMessage?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = if (statusError) EazColors.Orange else Color.White.copy(alpha = 0.7f)
            )
        }
        Spacer(Modifier.height(20.dp))
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { if (!posting) showConfirmDialog = false },
            containerColor = Color(0xFF1F2937),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.88f),
            title = { Text(t("creator.social_manager.confirm_post_title", "Publish post?")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${t("creator.social_manager.channels", "Channels")}: ${selectedChannels.joinToString(", ")}")
                    if (scheduleEnabled && scheduleDate.isNotBlank() && scheduleTime.isNotBlank()) {
                        Text("${t("creator.social_manager.schedule", "Schedule for later")}: $scheduleDate $scheduleTime")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    submitPost()
                }) { Text(t("creator.common.confirm", "Confirm"), color = EazColors.Orange) }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(t("creator.common.cancel", "Cancel"), color = Color.White.copy(alpha = 0.85f))
                }
            }
        )
    }
}
