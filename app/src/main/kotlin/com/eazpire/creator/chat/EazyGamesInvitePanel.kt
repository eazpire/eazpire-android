package com.eazpire.creator.chat

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.ui.share.getActiveRefUrl
import kotlinx.coroutines.launch
import org.json.JSONObject

private enum class InviteTab { Friends, Requests, Invites }

private data class InviteFriendItem(
    val userId: String,
    val username: String,
    val avatarUrl: String?,
    val badge: String,
    val gamesPlayed: Int,
    val gamesWon: Int,
    val canRequest: Boolean,
    val pendingRequest: Boolean,
    val canSendLife: Boolean = true,
    val pendingSentLife: Boolean = false,
)

private data class InviteRequestItem(
    val id: Int,
    val requesterId: String,
    val username: String,
    val avatarUrl: String?,
)

private data class LifeInviteItem(
    val id: Int,
    val senderUsername: String,
    val avatarUrl: String?,
    val gameSlug: String?,
)

@Composable
fun EazyGamesInvitePanel(
    api: CreatorApi,
    ownerId: String?,
    shop: String,
    t: (String, String) -> String,
    initialTab: String = "friends",
    selectedGameSlug: String? = null,
    onLifeAccepted: (String?) -> Unit = {},
) {
    val palette = LocalEazyModalPalette.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resolvedInitialTab = when (initialTab.lowercase()) {
        "requests" -> InviteTab.Requests
        "invites" -> InviteTab.Invites
        else -> InviteTab.Friends
    }
    var tab by remember { mutableStateOf(resolvedInitialTab) }
    var loading by remember { mutableStateOf(true) }
    var friends by remember { mutableStateOf<List<InviteFriendItem>>(emptyList()) }
    var requests by remember { mutableStateOf<List<InviteRequestItem>>(emptyList()) }
    var invites by remember { mutableStateOf<List<LifeInviteItem>>(emptyList()) }
    var refreshKey by remember { mutableStateOf(0) }

    LaunchedEffect(initialTab) {
        tab = when (initialTab.lowercase()) {
            "requests" -> InviteTab.Requests
            "invites" -> InviteTab.Invites
            else -> InviteTab.Friends
        }
    }

    fun badgeLabel(badge: String): String = when (badge) {
        "creator" -> t("eazy_chat.invite_badge_creator", "Creator")
        "community" -> t("eazy_chat.invite_badge_community", "Community")
        else -> t("eazy_chat.invite_badge_invited", "Invited")
    }

    LaunchedEffect(ownerId, tab, refreshKey) {
        val oid = ownerId?.trim().orEmpty()
        if (oid.isBlank()) {
            loading = false
            friends = emptyList()
            requests = emptyList()
            invites = emptyList()
            return@LaunchedEffect
        }
        loading = true
        try {
            when (tab) {
                InviteTab.Friends -> {
                    val res = api.listGamesInviteFriends(oid, shop)
                    val arr = res.optJSONArray("friends") ?: org.json.JSONArray()
                    friends = (0 until arr.length()).mapNotNull { i ->
                        val o = arr.optJSONObject(i) ?: return@mapNotNull null
                        InviteFriendItem(
                            userId = o.optString("user_id", ""),
                            username = o.optString("username", ""),
                            avatarUrl = o.optString("profile_picture_url", "").trim().ifBlank { null },
                            badge = o.optString("invite_badge", "invited"),
                            gamesPlayed = o.optInt("games_played", 0),
                            gamesWon = o.optInt("games_won", 0),
                            canRequest = o.optBoolean("can_request_life", o.optBoolean("can_request_game", true)),
                            pendingRequest = o.optBoolean("pending_life_request", o.optBoolean("pending_request", false)),
                            canSendLife = o.optBoolean("can_send_life", true),
                            pendingSentLife = o.optBoolean("pending_sent_life", false),
                        )
                    }
                }
                InviteTab.Requests -> {
                    val res = api.listGamesInviteRequests(oid)
                    val arr = res.optJSONArray("requests") ?: org.json.JSONArray()
                    requests = (0 until arr.length()).mapNotNull { i ->
                        val o = arr.optJSONObject(i) ?: return@mapNotNull null
                        InviteRequestItem(
                            id = o.optInt("id", 0),
                            requesterId = o.optString("requester_id", ""),
                            username = o.optString("requester_username", ""),
                            avatarUrl = o.optString("requester_profile_picture_url", "").trim().ifBlank { null },
                        )
                    }
                }
                InviteTab.Invites -> {
                    val res = api.listGamesLifeInvites(oid)
                    val arr = res.optJSONArray("invites") ?: org.json.JSONArray()
                    invites = (0 until arr.length()).mapNotNull { i ->
                        val o = arr.optJSONObject(i) ?: return@mapNotNull null
                        LifeInviteItem(
                            id = o.optInt("id", 0),
                            senderUsername = o.optString("sender_username", ""),
                            avatarUrl = o.optString("sender_profile_picture_url", "").trim().ifBlank { null },
                            gameSlug = o.optString("game_slug", "").trim().ifBlank { null },
                        )
                    }
                }
            }
        } catch (_: Exception) {
            friends = emptyList()
            requests = emptyList()
            invites = emptyList()
        }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                InviteTab.Friends to t("eazy_chat.invite_friends_tab", "Friends"),
                InviteTab.Requests to t("eazy_chat.invite_requests_tab", "Requests"),
                InviteTab.Invites to t("eazy_chat.invite_invites_tab", "Invites"),
            ).forEach { (key, label) ->
                EazyGamesChip(label, tab == key, palette) { tab = key }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))

        when {
            ownerId.isNullOrBlank() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        t("eazy_chat.games_login", "Sign in to play the daily game."),
                        color = palette.muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = palette.accent, modifier = Modifier.size(28.dp))
                }
            }
            tab == InviteTab.Friends -> {
                if (friends.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            t("eazy_chat.invite_friends_empty", "No friends yet. Share your invite link to get started."),
                            color = palette.muted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(140.dp),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(friends, key = { it.userId }) { friend ->
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                                    .background(palette.bg.copy(alpha = 0.35f))
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (!friend.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(friend.avatarUrl).crossfade(true).build(),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(palette.border.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text("?", color = palette.muted)
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    friend.username,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = palette.text,
                                )
                                Text(
                                    badgeLabel(friend.badge),
                                    fontSize = 10.sp,
                                    color = palette.accent,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                                Text(
                                    "${t("eazy_chat.invite_games_played", "Played")}: ${friend.gamesPlayed} · " +
                                        "${t("eazy_chat.invite_games_won", "Won")}: ${friend.gamesWon}",
                                    fontSize = 10.sp,
                                    color = palette.muted,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                val reqLabel = if (friend.pendingRequest) {
                                    t("eazy_chat.invite_request_pending", "Pending")
                                } else {
                                    t("eazy_chat.invite_request_game", "Request Life")
                                }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                api.createGamesPlayRequest(ownerId, friend.userId, shop)
                                                refreshKey++
                                            } catch (_: Exception) {
                                            }
                                        }
                                    },
                                    enabled = friend.canRequest && !friend.pendingRequest,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text(reqLabel, fontSize = 11.sp)
                                }
                                val sendLabel = if (friend.pendingSentLife) {
                                    "Sent"
                                } else {
                                    t("eazy_chat.invite_send_life", "Send Life")
                                }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                api.sendGamesLife(ownerId, friend.userId, shop, selectedGameSlug)
                                                refreshKey++
                                            } catch (_: Exception) {
                                            }
                                        }
                                    },
                                    enabled = friend.canSendLife && !friend.pendingSentLife,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text(sendLabel, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
            tab == InviteTab.Invites -> {
                if (invites.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            t("eazy_chat.invite_invites_empty", "No pending life invites."),
                            color = palette.muted,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        invites.forEach { inv ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(inv.senderUsername, fontWeight = FontWeight.SemiBold, color = palette.text)
                                    Text(
                                        t("eazy_chat.invite_send_life", "Send Life"),
                                        fontSize = 11.sp,
                                        color = palette.muted,
                                    )
                                }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            try {
                                                val res = api.acceptGamesLifeInvite(ownerId!!, inv.id)
                                                if (res.optBoolean("ok", false)) {
                                                    val slug = res.optString("game_slug", inv.gameSlug).ifBlank { inv.gameSlug }
                                                    onLifeAccepted(slug)
                                                }
                                                refreshKey++
                                            } catch (_: Exception) {
                                            }
                                        }
                                    },
                                ) {
                                    Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.size(4.dp))
                                    Text(t("eazy_chat.invite_request_accept", "Accept"), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                if (requests.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            t("eazy_chat.invite_requests_empty", "No pending requests."),
                            color = palette.muted,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        requests.forEach { req ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(req.username, fontWeight = FontWeight.SemiBold, color = palette.text)
                                    Text(
                                        "Requested a life",
                                        fontSize = 11.sp,
                                        color = palette.muted,
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            api.respondGamesPlayRequest(ownerId!!, req.id, "reject")
                                            refreshKey++
                                        }
                                    },
                                ) {
                                    Text(t("eazy_chat.invite_request_reject", "Reject"), fontSize = 11.sp)
                                }
                                Button(
                                    onClick = {
                                        scope.launch {
                                            api.respondGamesPlayRequest(ownerId!!, req.id, "accept")
                                            refreshKey++
                                        }
                                    },
                                ) {
                                    Text(t("eazy_chat.invite_request_accept", "Accept"), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                scope.launch {
                    val url = getActiveRefUrl(api, ownerId.orEmpty())
                    if (!url.isNullOrBlank()) {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, url)
                        }
                        context.startActivity(Intent.createChooser(intent, t("eazy_chat.invite_footer_button", "Invite")))
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !ownerId.isNullOrBlank(),
        ) {
            Text(t("eazy_chat.invite_footer_button", "Invite"))
        }
    }
}
