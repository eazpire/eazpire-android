package com.eazpire.creator.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.AuthConfig
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private data class CollectionTokens(val rotate: Int = 0, val trade: Int = 0)

private data class MarketListing(
    val listingId: Int,
    val label: String,
    val item: PrizeInventoryItem,
)

private data class InviteFriend(
    val ownerId: String,
    val label: String,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EazyGamesCollectionPanel(
    api: CreatorApi,
    ownerId: String?,
    shop: String,
    t: (String, String) -> String,
    onNavigateExchange: () -> Unit,
    pendingTradeOfferId: Int? = null,
    onPendingTradeOfferConsumed: () -> Unit = {},
) {
    val palette = LocalEazyModalPalette.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<PrizeInventoryItem>>(emptyList()) }
    var tokens by remember { mutableStateOf(CollectionTokens()) }
    var filterCategory by remember { mutableStateOf("all") }
    var filterType by remember { mutableStateOf("card") }
    var refreshKey by remember { mutableIntStateOf(0) }
    var viewerItem by remember { mutableStateOf<PrizeInventoryItem?>(null) }
    var fusionConfirm by remember { mutableStateOf<PrizeInventoryItem?>(null) }
    var confirm by remember { mutableStateOf<Triple<String, Int, String>?>(null) }
    var exchangeSourceInstanceId by remember { mutableStateOf<Int?>(null) }
    var marketListings by remember { mutableStateOf<List<MarketListing>>(emptyList()) }
    var sendInstanceId by remember { mutableStateOf<Int?>(null) }
    var sendFriends by remember { mutableStateOf<List<InviteFriend>>(emptyList()) }
    var tradeReviewOfferId by remember { mutableStateOf<Int?>(null) }
    var tokenInfoKind by remember { mutableStateOf<String?>(null) }

    val categories = listOf(
        "all" to "⊞ ${t("eazy_chat.prizes_filter_all", "All")}",
        "shop" to "🛍 Shop",
        "creator" to "✦ Creator",
        "eazy" to "⚡ Eazy",
        "special" to "★ Special",
    )
    val types = listOf(
        "card" to "🃏 ${t("eazy_chat.prizes_filter_cards", "Cards")}",
        "prize" to "🏆 ${t("eazy_chat.prizes_filter_prizes", "Prizes")}",
    )

    fun refreshAll() {
        refreshKey++
    }

    LaunchedEffect(pendingTradeOfferId) {
        if (pendingTradeOfferId != null && pendingTradeOfferId > 0) {
            tradeReviewOfferId = pendingTradeOfferId
            onPendingTradeOfferConsumed()
        }
    }

    LaunchedEffect(ownerId, filterCategory, filterType, refreshKey) {
        if (ownerId.isNullOrBlank()) {
            loading = false
            items = emptyList()
            return@LaunchedEffect
        }
        loading = true
        try {
            val state = api.getPrizesInventoryState(ownerId, shop)
            if (state.optBoolean("ok", false)) {
                tokens = CollectionTokens(
                    rotate = state.optInt("rotate_tokens", state.optInt("reroll_tokens")),
                    trade = state.optInt("trade_tokens"),
                )
            }
            val j = api.getPrizesInventoryList(
                ownerId,
                shop,
                filterType,
                filterCategory,
                group = filterType == "card",
            )
            items = if (j.optBoolean("ok", false)) {
                parseInventoryItems(j.optJSONArray("items") ?: JSONArray())
            } else emptyList()
        } catch (_: Exception) {
            items = emptyList()
        }
        loading = false
    }

    confirm?.let { (action, id, itemType) ->
        val (title, message, confirmLabel) = when (action) {
            "redeem" -> Triple(
                t("eazy_chat.prizes_confirm_redeem_title", "Redeem this prize?"),
                t("eazy_chat.prizes_confirm_redeem_text", "This will fulfill the prize to your account. This cannot be undone."),
                t("eazy_chat.prizes_redeem", "Redeem"),
            )
            "rotate" -> Triple(
                t("eazy_chat.prizes_confirm_rotate_title", "Rotate this card?"),
                t("eazy_chat.prizes_confirm_rotate_text", "Uses 1 Rotate token. Your card is replaced by a new random card."),
                t("eazy_chat.prizes_rotate", "Rotate"),
            )
            "remove" -> Triple(
                t("eazy_chat.cards_confirm_remove_title", "Remove this card?"),
                t("eazy_chat.cards_confirm_remove_text", "This cannot be undone."),
                t("eazy_chat.cards_remove", "Remove"),
            )
            else -> Triple(
                t("eazy_chat.cards_confirm_list_title", "List this card?"),
                t("eazy_chat.cards_confirm_list_text", "Your card will appear on the Exchange market."),
                t("eazy_chat.exchange_list", "List on exchange"),
            )
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(title, color = palette.text) },
            text = { Text(message, color = palette.muted) },
            confirmButton = {
                TextButton(onClick = {
                    val oid = ownerId ?: return@TextButton
                    scope.launch {
                        try {
                            when (action) {
                                "redeem" -> api.postPrizesRedeem(oid, id, shop)
                                "rotate" -> api.postPrizesRotate(oid, id, shop)
                                "remove" -> api.postPrizesCardDiscard(oid, id, shop)
                                else -> api.postPrizesTradeListing(oid, itemType, id, shop)
                            }
                        } catch (_: Exception) {}
                        confirm = null
                        viewerItem = null
                        if (action == "list") onNavigateExchange()
                        refreshAll()
                    }
                }) { Text(confirmLabel, color = palette.accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) {
                    Text(t("eazy_chat.ui_close", "Close"), color = palette.muted)
                }
            },
            containerColor = palette.bg,
        )
    }

    fusionConfirm?.let { fuseItem ->
        AlertDialog(
            onDismissRequest = { fusionConfirm = null },
            title = { Text(t("eazy_chat.prizes_confirm_fusion_title", "Fuse these cards?"), color = palette.text) },
            text = {
                Text(
                    t(
                        "eazy_chat.prizes_confirm_fusion_text",
                        "Combine 4 matching cards into your prize. Fused cards can no longer be traded.",
                    ),
                    color = palette.muted,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val oid = ownerId ?: return@TextButton
                    val defId = fuseItem.cardDefinitionId ?: return@TextButton
                    scope.launch {
                        try {
                            api.postPrizesFuse(oid, defId, fuseItem.instanceIds, shop)
                            filterType = "prize"
                        } catch (_: Exception) {}
                        fusionConfirm = null
                        viewerItem = null
                        refreshAll()
                    }
                }) { Text(t("eazy_chat.prizes_fusion_confirm", "Fuse"), color = palette.accent) }
            },
            dismissButton = {
                TextButton(onClick = { fusionConfirm = null }) {
                    Text(t("eazy_chat.ui_close", "Close"), color = palette.muted)
                }
            },
            containerColor = palette.bg,
        )
    }

    tokenInfoKind?.let { kind ->
        val text = if (kind == "rotate") {
            t(
                "eazy_chat.cards_rotate_tokens_info",
                "Rotate tokens let you swap a card for a new random card (full daily win roll). Each rotate uses 1 token.",
            )
        } else {
            t(
                "eazy_chat.cards_trade_tokens_info",
                "Trade tokens are spent when an exchange is accepted (1 token per completed trade). You also receive bonus tokens from game wins and a weekly grant.",
            )
        }
        AlertDialog(
            onDismissRequest = { tokenInfoKind = null },
            title = { Text(t("eazy_chat.cards_token_info_title", "About tokens"), color = palette.text) },
            text = { Text(text, color = palette.muted) },
            confirmButton = {
                TextButton(onClick = { tokenInfoKind = null }) {
                    Text(t("eazy_chat.ui_close", "Close"), color = palette.accent)
                }
            },
            containerColor = palette.bg,
        )
    }

    viewerItem?.let { item ->
        val instanceId = item.instanceIds.firstOrNull() ?: item.id
        val locked = item.status == "listed" || item.status == "exchange_pending" || item.status == "gift_pending"
        AlertDialog(
            onDismissRequest = { viewerItem = null },
            title = { Text(t("eazy_chat.cards_viewer_title", "Card details"), color = palette.text) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        EazyPrizeCard(
                            item = item,
                            apiBase = AuthConfig.CREATOR_ENGINE_URL,
                            modifier = Modifier.width(180.dp),
                        )
                    }
                    Text(
                        item.unlockLore ?: t(
                            "eazy_chat.cards_lore_fallback",
                            "This card holds a mystery reward. Collect four alike to fuse a prize.",
                        ),
                        color = palette.muted,
                        fontSize = 13.sp,
                    )
                    if (item.status == "exchange_pending") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            t("eazy_chat.cards_exchange_pending", "Exchange pending"),
                            color = Color(0xFFF5C542),
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (item.fusionReady) {
                            EazyGamesActionButton(t("eazy_chat.prizes_fusion_confirm", "Fuse"), filled = true) {
                                fusionConfirm = item
                            }
                        }
                        if (!locked && !item.fusionReady) {
                            EazyGamesActionButton(t("eazy_chat.exchange_list", "List"), filled = false) {
                                confirm = Triple("list", instanceId, item.type)
                            }
                            EazyGamesActionButton(t("eazy_chat.cards_exchange", "Exchange"), filled = false) {
                                viewerItem = null
                                exchangeSourceInstanceId = instanceId
                                scope.launch {
                                    try {
                                        val j = api.getPrizesTradeListings(40, shop = shop)
                                        val arr = j.optJSONArray("listings") ?: JSONArray()
                                        marketListings = (0 until arr.length()).mapNotNull { i ->
                                            val o = arr.optJSONObject(i) ?: return@mapNotNull null
                                            val offered = o.optJSONObject("offered") ?: return@mapNotNull null
                                            val card = parseInventoryItems(JSONArray().put(offered)).firstOrNull()
                                                ?: return@mapNotNull null
                                            MarketListing(
                                                listingId = o.optInt("id"),
                                                label = card.name,
                                                item = card,
                                            )
                                        }
                                    } catch (_: Exception) {
                                        marketListings = emptyList()
                                    }
                                }
                            }
                            EazyGamesActionButton(t("eazy_chat.cards_send", "Send"), filled = false) {
                                viewerItem = null
                                sendInstanceId = instanceId
                                scope.launch {
                                    try {
                                        val j = api.listGamesInviteFriends(ownerId ?: return@launch, shop)
                                        val arr = j.optJSONArray("friends") ?: JSONArray()
                                        sendFriends = (0 until arr.length()).mapNotNull { i ->
                                            val o = arr.optJSONObject(i) ?: return@mapNotNull null
                                            val fid = o.optString("owner_id", o.optString("user_id", "")).trim()
                                            if (fid.isBlank()) return@mapNotNull null
                                            InviteFriend(
                                                ownerId = fid,
                                                label = o.optString("username", o.optString("display_name", fid)),
                                            )
                                        }
                                    } catch (_: Exception) {
                                        sendFriends = emptyList()
                                    }
                                }
                            }
                            EazyGamesActionButton(t("eazy_chat.prizes_rotate", "Rotate"), filled = true) {
                                confirm = Triple("rotate", instanceId, item.type)
                            }
                            EazyGamesActionButton(t("eazy_chat.cards_remove", "Remove"), filled = false) {
                                confirm = Triple("remove", instanceId, item.type)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewerItem = null }) {
                    Text(t("eazy_chat.ui_close", "Close"), color = palette.muted)
                }
            },
            containerColor = palette.bg,
        )
    }

    exchangeSourceInstanceId?.let { sourceInstanceId ->
        AlertDialog(
            onDismissRequest = { exchangeSourceInstanceId = null },
            title = { Text(t("eazy_chat.cards_exchange_market", "Card market"), color = palette.text) },
            text = {
                if (marketListings.isEmpty()) {
                    Text(t("eazy_chat.exchange_no_listings", "No listings yet."), color = palette.muted)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        marketListings.forEach { listing ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, palette.border, RoundedCornerShape(8.dp))
                                    .clickable {
                                        val oid = ownerId ?: return@clickable
                                        scope.launch {
                                            try {
                                                api.postPrizesTradeOfferCreate(oid, listing.listingId, sourceInstanceId, shop)
                                            } catch (_: Exception) {}
                                            exchangeSourceInstanceId = null
                                            refreshAll()
                                        }
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                EazyPrizeCard(
                                    item = listing.item,
                                    apiBase = AuthConfig.CREATOR_ENGINE_URL,
                                    modifier = Modifier.width(72.dp),
                                )
                                Text(listing.label, color = palette.text, fontSize = 13.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { exchangeSourceInstanceId = null }) {
                    Text(t("eazy_chat.ui_close", "Close"), color = palette.muted)
                }
            },
            containerColor = palette.bg,
        )
    }

    sendInstanceId?.let { instanceId ->
        AlertDialog(
            onDismissRequest = { sendInstanceId = null },
            title = { Text(t("eazy_chat.cards_send_title", "Send to friend"), color = palette.text) },
            text = {
                if (sendFriends.isEmpty()) {
                    Text(t("eazy_chat.invite_no_friends", "No friends yet."), color = palette.muted)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        sendFriends.forEach { friend ->
                            TextButton(onClick = {
                                val oid = ownerId ?: return@TextButton
                                scope.launch {
                                    try {
                                        api.postPrizesCardGift(
                                            ownerId = oid,
                                            action = "send",
                                            shop = shop,
                                            instanceId = instanceId,
                                            targetOwnerId = friend.ownerId,
                                        )
                                    } catch (_: Exception) {}
                                    sendInstanceId = null
                                    refreshAll()
                                }
                            }) {
                                Text(friend.label, color = palette.text)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { sendInstanceId = null }) {
                    Text(t("eazy_chat.ui_close", "Close"), color = palette.muted)
                }
            },
            containerColor = palette.bg,
        )
    }

    tradeReviewOfferId?.let { offerId ->
        var detail by remember(offerId) { mutableStateOf<JSONObject?>(null) }
        LaunchedEffect(offerId, ownerId) {
            val oid = ownerId ?: return@LaunchedEffect
            detail = try {
                api.getPrizesTradeOfferDetail(oid, offerId, shop)
            } catch (_: Exception) {
                null
            }
        }
        val offer = detail?.optJSONObject("offer")
        val canAccept = offer?.optBoolean("can_accept") == true
        val canDecline = offer?.optBoolean("can_decline") == true
        AlertDialog(
            onDismissRequest = { tradeReviewOfferId = null },
            title = { Text(t("eazy_chat.cards_trade_review_title", "Exchange review"), color = palette.text) },
            text = {
                if (detail == null) {
                    CircularProgressIndicator(color = palette.accent)
                } else if (detail?.optBoolean("ok") != true) {
                    Text(t("eazy_chat.exchange_no_trades", "No trade offers yet."), color = palette.muted)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        detail?.optJSONObject("requester_card")?.let { cardJson ->
                            parseInventoryItems(JSONArray().put(cardJson)).firstOrNull()?.let {
                                EazyPrizeCard(it, AuthConfig.CREATOR_ENGINE_URL, Modifier.width(120.dp))
                            }
                        }
                        detail?.optJSONObject("target_card")?.let { cardJson ->
                            parseInventoryItems(JSONArray().put(cardJson)).firstOrNull()?.let {
                                EazyPrizeCard(it, AuthConfig.CREATOR_ENGINE_URL, Modifier.width(120.dp))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (canAccept) {
                    TextButton(onClick = {
                        val oid = ownerId ?: return@TextButton
                        scope.launch {
                            try {
                                api.postPrizesTradeOffer(oid, "accept", offerId, shop)
                            } catch (_: Exception) {}
                            tradeReviewOfferId = null
                            refreshAll()
                        }
                    }) { Text(t("eazy_chat.cards_accept_exchange", "Accept"), color = palette.accent) }
                }
            },
            dismissButton = {
                Row {
                    if (canDecline) {
                        TextButton(onClick = {
                            val oid = ownerId ?: return@TextButton
                            scope.launch {
                                try {
                                    api.postPrizesTradeOffer(oid, "decline", offerId, shop)
                                } catch (_: Exception) {}
                                tradeReviewOfferId = null
                                refreshAll()
                            }
                        }) { Text(t("eazy_chat.cards_decline_exchange", "Decline"), color = palette.muted) }
                    }
                    TextButton(onClick = { tradeReviewOfferId = null }) {
                        Text(t("eazy_chat.ui_close", "Close"), color = palette.muted)
                    }
                }
            },
            containerColor = palette.bg,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        EazyGamesFilterChips(categories, types, filterCategory, filterType, { filterCategory = it }, { filterType = it })
        Spacer(modifier = Modifier.height(8.dp))
        when {
            ownerId.isNullOrBlank() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        t("eazy_chat.prizes_collection_login", "Sign in to view your collection."),
                        color = palette.muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = palette.accent)
                }
            }
            items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        t("eazy_chat.prizes_collection_empty", "No prizes or cards yet."),
                        color = palette.muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items, key = { "${it.type}-${it.cardDefinitionId ?: it.id}-${it.ownedCount}" }) { item ->
                        EazyPrizeCard(
                            item = item,
                            apiBase = AuthConfig.CREATOR_ENGINE_URL,
                            prizeView = item.type == "prize",
                            modifier = Modifier.clickable {
                                if (item.type == "card") viewerItem = item
                                else if (item.fusionReady) fusionConfirm = item
                            },
                            actions = {
                                if (item.type == "prize" && item.fulfillmentMode != "trade_token") {
                                    EazyGamesActionButton(
                                        t("eazy_chat.prizes_redeem", "Redeem"),
                                        filled = true,
                                        onClick = { confirm = Triple("redeem", item.id, item.type) },
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
        if (!ownerId.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CollectionTokenChip("↻", tokens.rotate) { tokenInfoKind = "rotate" }
                Spacer(modifier = Modifier.width(8.dp))
                CollectionTokenChip("⇄", tokens.trade) { tokenInfoKind = "trade" }
            }
        }
    }
}

@Composable
private fun CollectionTokenChip(icon: String, count: Int, onClick: () -> Unit) {
    val palette = LocalEazyModalPalette.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, palette.border, RoundedCornerShape(999.dp))
            .background(Color.Black.copy(alpha = 0.25f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(icon, color = palette.muted, fontSize = 14.sp)
        Text(count.toString(), color = palette.text, fontSize = 13.sp)
    }
}
