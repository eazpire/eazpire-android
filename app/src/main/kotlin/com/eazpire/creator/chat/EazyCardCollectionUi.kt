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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.AuthConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private data class PrizesInventoryStateUi(
    val tradeTokens: Int,
    val rerollTokens: Int,
    val activeListings: Int,
    val pendingOffers: Int,
)

private data class TradeOfferDetailUi(
    val offerId: Int,
    val offeredLabel: String,
    val listingLabel: String,
    val status: String,
)

private sealed interface CardCollectionConfirmAction {
    data class Redeem(val id: Int) : CardCollectionConfirmAction
    data class Rotate(val id: Int) : CardCollectionConfirmAction
    data class List(val id: Int, val type: String) : CardCollectionConfirmAction
    data class Fuse(val item: PrizeInventoryItem) : CardCollectionConfirmAction
    data class Discard(val id: Int) : CardCollectionConfirmAction
    data class AcceptOffer(val offerId: Int) : CardCollectionConfirmAction
    data class DeclineOffer(val offerId: Int) : CardCollectionConfirmAction
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EazyCardCollectionUi(
    api: CreatorApi,
    ownerId: String?,
    t: (String, String) -> String,
    onNavigateExchange: () -> Unit,
    initialType: String = "card",
    pendingTradeOfferId: Int? = null,
    onPendingTradeOfferConsumed: () -> Unit = {},
) {
    val palette = LocalEazyModalPalette.current
    val scope = rememberCoroutineScope()
    val shop = AuthConfig.SHOP_DOMAIN
    var loading by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<PrizeInventoryItem>>(emptyList()) }
    var inventoryState by remember { mutableStateOf<PrizesInventoryStateUi?>(null) }
    var filterCategory by remember { mutableStateOf("all") }
    var filterType by remember { mutableStateOf(if (initialType == "prize") "prize" else "card") }
    var refreshKey by remember { mutableIntStateOf(0) }
    var viewerItem by remember { mutableStateOf<PrizeInventoryItem?>(null) }
    var confirmAction by remember { mutableStateOf<CardCollectionConfirmAction?>(null) }
    var giftItemId by remember { mutableIntStateOf(0) }
    var giftOwnerId by remember { mutableStateOf("") }
    var giftError by remember { mutableStateOf<String?>(null) }
    var tradeOfferDetail by remember { mutableStateOf<TradeOfferDetailUi?>(null) }

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

    LaunchedEffect(ownerId, filterCategory, filterType, refreshKey) {
        if (ownerId.isNullOrBlank()) {
            loading = false
            items = emptyList()
            return@LaunchedEffect
        }
        loading = true
        try {
            val listRes = withContext(Dispatchers.IO) {
                api.getPrizesInventoryList(
                    ownerId = ownerId,
                    shop = shop,
                    type = filterType,
                    category = filterCategory,
                    group = filterType == "card",
                )
            }
            items = if (listRes.optBoolean("ok", false)) {
                parseInventoryItems(listRes.optJSONArray("items") ?: JSONArray())
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            items = emptyList()
        }
        loading = false
    }

    LaunchedEffect(ownerId, refreshKey) {
        if (ownerId.isNullOrBlank()) {
            inventoryState = null
            return@LaunchedEffect
        }
        inventoryState = try {
            val stateRes = withContext(Dispatchers.IO) { api.getPrizesInventoryState(ownerId, shop) }
            if (stateRes.optBoolean("ok", false)) {
                PrizesInventoryStateUi(
                    tradeTokens = stateRes.optInt("trade_tokens", 0),
                    rerollTokens = stateRes.optInt("reroll_tokens", 0),
                    activeListings = stateRes.optInt("active_listings", 0),
                    pendingOffers = stateRes.optInt("pending_offers", 0),
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    LaunchedEffect(pendingTradeOfferId, ownerId) {
        val offerId = pendingTradeOfferId ?: return@LaunchedEffect
        if (offerId <= 0 || ownerId.isNullOrBlank()) return@LaunchedEffect
        tradeOfferDetail = try {
            parseTradeOfferDetail(
                withContext(Dispatchers.IO) {
                    api.getPrizesTradeOfferDetail(ownerId, offerId, shop)
                },
                offerId,
            )
        } catch (_: Exception) {
            null
        }
        onPendingTradeOfferConsumed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        CardCollectionFilterChips(
            categories = categories,
            types = types,
            selectedCategory = filterCategory,
            selectedType = filterType,
            onCategory = { filterCategory = it },
            onType = { filterType = it },
        )
        Spacer(modifier = Modifier.height(8.dp))

        when {
            ownerId.isNullOrBlank() -> {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        t("eazy_chat.prizes_collection_login", "Sign in to view your collection."),
                        color = palette.muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            loading -> {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = palette.accent)
                }
            }
            items.isEmpty() -> {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
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
                            modifier = Modifier.clickable { viewerItem = item },
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        CardCollectionFooter(
            state = inventoryState,
            t = t,
            onGoExchange = onNavigateExchange,
        )
    }

    viewerItem?.let { item ->
        AlertDialog(
            onDismissRequest = { viewerItem = null },
            title = { Text(item.name, color = palette.text) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EazyPrizeCard(
                        item = item,
                        apiBase = AuthConfig.CREATOR_ENGINE_URL,
                        prizeView = item.type == "prize",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (item.type == "prize" && item.fulfillmentMode != "trade_token") {
                            CardCollectionActionButton(
                                label = t("eazy_chat.prizes_redeem", "Redeem"),
                                filled = true,
                                onClick = { confirmAction = CardCollectionConfirmAction.Redeem(item.id) },
                            )
                        }
                        if (item.type == "prize") {
                            CardCollectionActionButton(
                                label = t("eazy_chat.prizes_rotate", "Rotate"),
                                filled = true,
                                onClick = { confirmAction = CardCollectionConfirmAction.Rotate(item.id) },
                            )
                        }
                        if (item.type == "card" && item.fusionReady) {
                            CardCollectionActionButton(
                                label = t("eazy_chat.prizes_fusion_confirm", "Fuse"),
                                filled = true,
                                onClick = { confirmAction = CardCollectionConfirmAction.Fuse(item) },
                            )
                        }
                        if (item.type == "card" && !item.fusionReady) {
                            val listId = item.instanceIds.firstOrNull() ?: item.id
                            CardCollectionActionButton(
                                label = t("eazy_chat.exchange_list", "List"),
                                filled = false,
                                onClick = { confirmAction = CardCollectionConfirmAction.List(listId, item.type) },
                            )
                            CardCollectionActionButton(
                                label = t("eazy_chat.send", "Send"),
                                filled = false,
                                onClick = {
                                    giftItemId = listId
                                    giftOwnerId = ""
                                    giftError = null
                                },
                            )
                            CardCollectionActionButton(
                                label = t("eazy_chat.discard", "Discard"),
                                filled = false,
                                onClick = { confirmAction = CardCollectionConfirmAction.Discard(listId) },
                            )
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

    if (giftItemId > 0) {
        AlertDialog(
            onDismissRequest = { giftItemId = 0 },
            title = { Text(t("eazy_chat.send_card_title", "Send card"), color = palette.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        t("eazy_chat.send_card_hint", "Enter recipient owner ID."),
                        color = palette.muted,
                    )
                    OutlinedTextField(
                        value = giftOwnerId,
                        onValueChange = { giftOwnerId = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    giftError?.let { Text(it, color = Color(0xFFEF4444), style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val oid = ownerId ?: return@TextButton
                        val target = giftOwnerId.trim()
                        if (target.isBlank()) {
                            giftError = t("eazy_chat.send_card_error_target", "Recipient is required.")
                            return@TextButton
                        }
                        scope.launch {
                            val res = withContext(Dispatchers.IO) {
                                api.postPrizesCardGift(oid, giftItemId, target, shop)
                            }
                            if (res.optBoolean("ok", false)) {
                                giftItemId = 0
                                viewerItem = null
                                refreshKey++
                            } else {
                                giftError = res.optString("error", t("eazy_chat.send_card_error", "Send failed"))
                            }
                        }
                    },
                ) { Text(t("eazy_chat.send", "Send"), color = palette.accent) }
            },
            dismissButton = {
                TextButton(onClick = { giftItemId = 0 }) {
                    Text(t("eazy_chat.cancel", "Cancel"), color = palette.muted)
                }
            },
            containerColor = palette.bg,
        )
    }

    confirmAction?.let { action ->
        val title: String
        val body: String
        val label: String
        when (action) {
            is CardCollectionConfirmAction.Redeem -> {
                title = t("eazy_chat.prizes_confirm_redeem_title", "Redeem this prize?")
                body = t("eazy_chat.prizes_confirm_redeem_text", "This will fulfill the prize to your account. This cannot be undone.")
                label = t("eazy_chat.prizes_redeem", "Redeem")
            }
            is CardCollectionConfirmAction.Rotate -> {
                title = t("eazy_chat.prizes_confirm_rotate_title", "Rotate this prize?")
                body = t("eazy_chat.prizes_confirm_rotate_text", "Your current prize will be replaced with a new random item of the same category and rarity.")
                label = t("eazy_chat.prizes_rotate", "Rotate")
            }
            is CardCollectionConfirmAction.List -> {
                title = t("eazy_chat.prizes_confirm_list_title", "List on the exchange?")
                body = t("eazy_chat.prizes_confirm_list_text", "Your item will be listed on the marketplace for other players to trade.")
                label = t("eazy_chat.exchange_list", "List on exchange")
            }
            is CardCollectionConfirmAction.Fuse -> {
                title = t("eazy_chat.prizes_confirm_fusion_title", "Fuse these cards?")
                body = t("eazy_chat.prizes_confirm_fusion_text", "Combine 4 matching cards into your prize. Fused cards can no longer be traded.")
                label = t("eazy_chat.prizes_fusion_confirm", "Fuse")
            }
            is CardCollectionConfirmAction.Discard -> {
                title = t("eazy_chat.discard_card_title", "Discard this card?")
                body = t("eazy_chat.discard_card_text", "This card will be permanently removed.")
                label = t("eazy_chat.discard", "Discard")
            }
            is CardCollectionConfirmAction.AcceptOffer -> {
                title = t("eazy_chat.exchange_accept", "Accept")
                body = t("eazy_chat.exchange_offer_accept_text", "Accept this trade offer?")
                label = t("eazy_chat.exchange_accept", "Accept")
            }
            is CardCollectionConfirmAction.DeclineOffer -> {
                title = t("eazy_chat.exchange_decline", "Decline")
                body = t("eazy_chat.exchange_offer_decline_text", "Decline this trade offer?")
                label = t("eazy_chat.exchange_decline", "Decline")
            }
        }
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(title, color = palette.text) },
            text = { Text(body, color = palette.muted) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val oid = ownerId ?: return@TextButton
                        scope.launch {
                            try {
                                when (action) {
                                    is CardCollectionConfirmAction.Redeem ->
                                        api.postPrizesRedeem(oid, action.id, shop)
                                    is CardCollectionConfirmAction.Rotate ->
                                        api.postPrizesRotate(oid, action.id, shop)
                                    is CardCollectionConfirmAction.List ->
                                        api.postPrizesTradeListing(oid, action.type, action.id, shop)
                                    is CardCollectionConfirmAction.Fuse -> {
                                        val defId = action.item.cardDefinitionId ?: 0
                                        if (defId > 0) api.postPrizesFuse(oid, defId, action.item.instanceIds, shop)
                                        filterType = "prize"
                                    }
                                    is CardCollectionConfirmAction.Discard ->
                                        api.postPrizesCardDiscard(oid, action.id, shop)
                                    is CardCollectionConfirmAction.AcceptOffer ->
                                        api.postPrizesTradeOffer(oid, "accept", action.offerId, shop)
                                    is CardCollectionConfirmAction.DeclineOffer ->
                                        api.postPrizesTradeOffer(oid, "decline", action.offerId, shop)
                                }
                            } catch (_: Exception) {
                            }
                            if (action is CardCollectionConfirmAction.List) onNavigateExchange()
                            if (action is CardCollectionConfirmAction.AcceptOffer || action is CardCollectionConfirmAction.DeclineOffer) {
                                tradeOfferDetail = null
                            }
                            viewerItem = null
                            confirmAction = null
                            refreshKey++
                        }
                    },
                ) { Text(label, color = palette.accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) {
                    Text(t("eazy_chat.cancel", "Cancel"), color = palette.muted)
                }
            },
            containerColor = palette.bg,
        )
    }

    tradeOfferDetail?.let { detail ->
        AlertDialog(
            onDismissRequest = { tradeOfferDetail = null },
            title = { Text(t("eazy_chat.exchange_trade_offer_for", "Offer for your listing"), color = palette.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${t("eazy_chat.exchange_listing", "Listing")}: ${detail.listingLabel}", color = palette.text)
                    Text("${t("eazy_chat.exchange_offer", "Offer")}: ${detail.offeredLabel}", color = palette.text)
                    Text("${t("eazy_chat.status", "Status")}: ${detail.status}", color = palette.muted)
                }
            },
            confirmButton = {
                TextButton(onClick = { confirmAction = CardCollectionConfirmAction.AcceptOffer(detail.offerId) }) {
                    Text(t("eazy_chat.exchange_accept", "Accept"), color = palette.accent)
                }
            },
            dismissButton = {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { confirmAction = CardCollectionConfirmAction.DeclineOffer(detail.offerId) }) {
                        Text(t("eazy_chat.exchange_decline", "Decline"), color = palette.muted)
                    }
                    TextButton(onClick = { tradeOfferDetail = null }) {
                        Text(t("eazy_chat.ui_close", "Close"), color = palette.muted)
                    }
                }
            },
            containerColor = palette.bg,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardCollectionFilterChips(
    categories: List<Pair<String, String>>,
    types: List<Pair<String, String>>,
    selectedCategory: String,
    selectedType: String,
    onCategory: (String) -> Unit,
    onType: (String) -> Unit,
) {
    val palette = LocalEazyModalPalette.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            categories.forEach { (key, label) ->
                CardCollectionChip(label, selectedCategory == key, palette) { onCategory(key) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            types.forEach { (key, label) ->
                CardCollectionChip(label, selectedType == key, palette) { onType(key) }
            }
        }
    }
}

@Composable
private fun CardCollectionChip(label: String, active: Boolean, palette: EazyModalPalette, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(1.dp, if (active) palette.accent else palette.border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(label, color = if (active) palette.accent else palette.muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CardCollectionActionButton(
    label: String,
    filled: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalEazyModalPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (filled) Modifier.background(palette.accent)
                else Modifier.border(1.dp, palette.border, RoundedCornerShape(8.dp)),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(label, color = if (filled) Color.White else palette.muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CardCollectionFooter(
    state: PrizesInventoryStateUi?,
    t: (String, String) -> String,
    onGoExchange: () -> Unit,
) {
    val palette = LocalEazyModalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(palette.muted.copy(alpha = 0.08f))
            .border(1.dp, palette.border, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val tradeTokens = state?.tradeTokens ?: 0
        val rerollTokens = state?.rerollTokens ?: 0
        val pendingOffers = state?.pendingOffers ?: 0
        val activeListings = state?.activeListings ?: 0
        Text(
            "${t("eazy_chat.exchange_token_balance", "Trade tokens")}: $tradeTokens · ${t("eazy_chat.reroll_tokens", "Reroll")}: $rerollTokens",
            modifier = Modifier.weight(1f),
            color = palette.text,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            "${t("eazy_chat.exchange_listings", "Listings")}: $activeListings · ${t("eazy_chat.exchange_trades", "Trades")}: $pendingOffers",
            color = palette.muted,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(modifier = Modifier.width(4.dp))
        CardCollectionActionButton(
            label = t("eazy_chat.games_section_exchange", "Exchange"),
            filled = false,
            onClick = onGoExchange,
        )
    }
}

private fun parseTradeOfferDetail(json: JSONObject, offerId: Int): TradeOfferDetailUi? {
    if (!json.optBoolean("ok", false)) return null
    val o = json.optJSONObject("offer") ?: json
    val offeredLabel = o.optString("offered_label").takeIf { it.isNotBlank() }
        ?: "Item #${o.optInt("offered_instance_id", offerId)}"
    val listingLabel = o.optString("listing_label").takeIf { it.isNotBlank() }
        ?: o.optJSONObject("listing")?.optJSONObject("offered")?.optString("name").orEmpty().ifBlank { "Listing #${o.optInt("listing_id", 0)}" }
    return TradeOfferDetailUi(
        offerId = o.optInt("id", offerId).takeIf { it > 0 } ?: offerId,
        offeredLabel = offeredLabel,
        listingLabel = listingLabel,
        status = o.optString("status", "pending"),
    )
}
