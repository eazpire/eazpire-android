package com.eazpire.creator.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.AuthConfig
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

private enum class GamesHubSection { Play, Collection, Exchange }

private enum class ExchangeTab { Market, MyListings, Trades }

private data class TradeListingItem(
    val id: Int,
    val title: String,
    val sellerId: String?,
)

private data class TradeOfferItem(
    val id: Int,
    val label: String,
)

@Composable
fun EazyGamesHubPanel(
    api: CreatorApi,
    ownerId: String?,
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit,
    onDismiss: () -> Unit,
    t: (String, String) -> String,
) {
    var section by remember { mutableStateOf(GamesHubSection.Play) }
    val palette = LocalEazyModalPalette.current
    val shop = AuthConfig.SHOP_DOMAIN

    Column(modifier = Modifier.fillMaxSize()) {
        // Zentrierte Subnav wie Web (.eazy-games-carousel)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(width = 1.dp, color = palette.border)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                GamesHubSection.Play to (Icons.Default.SportsEsports to t("eazy_chat.games_section_play", "Play")),
                GamesHubSection.Collection to (Icons.Default.EmojiEvents to t("eazy_chat.games_section_collection", "Collection")),
                GamesHubSection.Exchange to (Icons.Default.SwapHoriz to t("eazy_chat.games_section_exchange", "Exchange")),
            ).forEach { (sec, iconLabel) ->
                val active = section == sec
                Column(
                    modifier = Modifier
                        .width(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) palette.accent.copy(alpha = 0.1f) else Color.Transparent)
                        .border(
                            1.dp,
                            if (active) palette.accent.copy(alpha = 0.35f) else Color.Transparent,
                            RoundedCornerShape(8.dp),
                        )
                        .clickable { section = sec }
                        .padding(vertical = 3.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Icon(
                        iconLabel.first,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        iconLabel.second,
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (active) palette.text else palette.muted,
                    )
                }
                if (sec != GamesHubSection.Exchange) Spacer(modifier = Modifier.width(4.dp))
            }
        }

        when (section) {
            GamesHubSection.Play ->
                EazyDailyGamePanel(
                    api = api,
                    ownerId = ownerId,
                    isLoggedIn = isLoggedIn,
                    onLoginClick = onLoginClick,
                    onDismiss = onDismiss,
                    t = t,
                )
            GamesHubSection.Collection ->
                EazyGamesCollectionPanel(
                    api = api,
                    ownerId = ownerId,
                    shop = shop,
                    t = t,
                    onNavigateExchange = { section = GamesHubSection.Exchange },
                )
            GamesHubSection.Exchange ->
                EazyGamesExchangePanel(
                    api = api,
                    ownerId = ownerId,
                    shop = shop,
                    t = t,
                )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EazyGamesFilterChips(
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
                EazyGamesChip(label, selectedCategory == key, palette) { onCategory(key) }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            types.forEach { (key, label) ->
                EazyGamesChip(label, selectedType == key, palette) { onType(key) }
            }
        }
    }
}

@Composable
private fun EazyGamesChip(label: String, active: Boolean, palette: EazyModalPalette, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .border(
                1.dp,
                if (active) palette.accent else palette.border,
                RoundedCornerShape(999.dp),
            )
            .background(Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = if (active) palette.accent else palette.muted,
        )
    }
}

@Composable
private fun EazyGamesCollectionPanel(
    api: CreatorApi,
    ownerId: String?,
    shop: String,
    t: (String, String) -> String,
    onNavigateExchange: () -> Unit,
) {
    val palette = LocalEazyModalPalette.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<PrizeInventoryItem>>(emptyList()) }
    var filterCategory by remember { mutableStateOf("all") }
    var filterType by remember { mutableStateOf("all") }
    var refreshKey by remember { mutableIntStateOf(0) }
    var confirm by remember { mutableStateOf<Triple<String, Int, String>?>(null) } // action, id, type

    val categories = listOf(
        "all" to t("eazy_chat.prizes_filter_all", "All"),
        "shop" to "shop",
        "creator" to "creator",
        "eazy" to "eazy",
        "special" to "special",
    )
    val types = listOf(
        "all" to t("eazy_chat.prizes_filter_all_types", "All types"),
        "prize" to t("eazy_chat.prizes_filter_prizes", "Prizes"),
        "card" to t("eazy_chat.prizes_filter_cards", "Cards"),
    )

    LaunchedEffect(ownerId, filterCategory, filterType, refreshKey) {
        if (ownerId.isNullOrBlank()) {
            loading = false
            items = emptyList()
            return@LaunchedEffect
        }
        loading = true
        try {
            val j = api.getPrizesInventoryList(ownerId, shop, filterType, filterCategory)
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
                t("eazy_chat.prizes_confirm_rotate_title", "Rotate this prize?"),
                t("eazy_chat.prizes_confirm_rotate_text", "Your current prize will be replaced with a new random item of the same category and rarity."),
                t("eazy_chat.prizes_rotate", "Rotate"),
            )
            else -> Triple(
                t("eazy_chat.prizes_confirm_list_title", "List on the exchange?"),
                t("eazy_chat.prizes_confirm_list_text", "Your item will be listed on the marketplace for other players to trade."),
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
                                else -> api.postPrizesTradeListing(oid, itemType, id, shop)
                            }
                        } catch (_: Exception) {}
                        confirm = null
                        if (action == "list") onNavigateExchange()
                        refreshKey++
                    }
                }) { Text(confirmLabel, color = palette.accent) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text(t("eazy_chat.ui_close", "Close"), color = palette.muted) }
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
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items, key = { "${it.type}-${it.id}" }) { item ->
                        EazyPrizeCard(
                            item = item,
                            apiBase = AuthConfig.CREATOR_ENGINE_URL,
                            actions = {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    if (item.type == "prize" && item.fulfillmentMode != "trade_token") {
                                        EazyGamesActionButton(
                                            t("eazy_chat.prizes_redeem", "Redeem"),
                                            filled = true,
                                            onClick = { confirm = Triple("redeem", item.id, item.type) },
                                        )
                                    }
                                    if (item.type == "prize") {
                                        EazyGamesActionButton(
                                            t("eazy_chat.prizes_rotate", "Rotate"),
                                            filled = true,
                                            onClick = { confirm = Triple("rotate", item.id, item.type) },
                                        )
                                    }
                                    EazyGamesActionButton(
                                        t("eazy_chat.exchange_list", "List"),
                                        filled = false,
                                        onClick = { confirm = Triple("list", item.id, item.type) },
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EazyGamesActionButton(label: String, filled: Boolean, onClick: () -> Unit) {
    val palette = LocalEazyModalPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (filled) Modifier.background(palette.accent)
                else Modifier.border(1.dp, palette.border, RoundedCornerShape(8.dp))
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            label,
            fontSize = 11.sp,
            color = if (filled) Color.White else palette.muted,
        )
    }
}

@Composable
private fun EazyGamesExchangePanel(
    api: CreatorApi,
    ownerId: String?,
    shop: String,
    t: (String, String) -> String,
) {
    val palette = LocalEazyModalPalette.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(ExchangeTab.Market) }
    var tokenBalance by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var listings by remember { mutableStateOf<List<TradeListingItem>>(emptyList()) }
    var offers by remember { mutableStateOf<List<TradeOfferItem>>(emptyList()) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(ownerId, tab, refreshKey) {
        if (ownerId.isNullOrBlank()) {
            loading = false
            listings = emptyList()
            offers = emptyList()
            return@LaunchedEffect
        }
        loading = true
        try {
            val tok = api.getPrizesTradeTokens(ownerId, shop)
            if (tok.optBoolean("ok", false)) tokenBalance = tok.optInt("balance", 0)
            when (tab) {
                ExchangeTab.Market -> {
                    val j = api.getPrizesTradeListings(limit = 30, shop = shop)
                    listings = if (j.optBoolean("ok", false)) parseListings(j.optJSONArray("listings") ?: JSONArray()) else emptyList()
                    offers = emptyList()
                }
                ExchangeTab.MyListings -> {
                    val j = api.getPrizesTradeListings(limit = 50, sellerId = ownerId, shop = shop)
                    listings = if (j.optBoolean("ok", false)) parseListings(j.optJSONArray("listings") ?: JSONArray()) else emptyList()
                    offers = emptyList()
                }
                ExchangeTab.Trades -> {
                    listings = emptyList()
                    val j = api.getPrizesTradeMyOffers(ownerId, shop)
                    offers = if (j.optBoolean("ok", false)) {
                        (j.optJSONArray("incoming") ?: JSONArray()).let { arr ->
                            (0 until arr.length()).mapNotNull { i ->
                                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                                TradeOfferItem(
                                    id = o.optInt("id"),
                                    label = o.optString("offered_label", "Item #${o.optInt("offered_instance_id")}"),
                                )
                            }
                        }
                    } else emptyList()
                }
            }
        } catch (_: Exception) {
            listings = emptyList()
            offers = emptyList()
        }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            "${t("eazy_chat.exchange_token_balance", "Trade tokens")}: $tokenBalance",
            style = MaterialTheme.typography.titleSmall,
            color = palette.text,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                ExchangeTab.Market to t("eazy_chat.exchange_market", "Market"),
                ExchangeTab.MyListings to t("eazy_chat.exchange_my_listings", "My listings"),
                ExchangeTab.Trades to t("eazy_chat.exchange_trades", "Trades"),
            ).forEach { (key, label) ->
                EazyGamesChip(label, tab == key, palette) { tab = key }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        when {
            ownerId.isNullOrBlank() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(t("eazy_chat.prizes_collection_login", "Sign in to view your collection."), color = palette.muted)
                }
            }
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = palette.accent)
                }
            }
            tab == ExchangeTab.Trades -> {
                if (offers.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(t("eazy_chat.exchange_no_trades", "No trade offers yet."), color = palette.muted)
                    }
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        offers.forEach { offer ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.muted.copy(alpha = 0.06f))
                                    .padding(12.dp),
                            ) {
                                Text(
                                    "${t("eazy_chat.exchange_trade_offer_for", "Offer for your listing")}: ${offer.label}",
                                    color = palette.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                    EazyGamesActionButton(t("eazy_chat.exchange_accept", "Accept"), filled = true) {
                                        val oid = ownerId ?: return@EazyGamesActionButton
                                        scope.launch {
                                            api.postPrizesTradeOffer(oid, "accept", offer.id, shop)
                                            refreshKey++
                                        }
                                    }
                                    EazyGamesActionButton(t("eazy_chat.exchange_decline", "Decline"), filled = false) {
                                        val oid = ownerId ?: return@EazyGamesActionButton
                                        scope.launch {
                                            api.postPrizesTradeOffer(oid, "decline", offer.id, shop)
                                            refreshKey++
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            listings.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (tab == ExchangeTab.MyListings) {
                            t("eazy_chat.exchange_no_my_listings", "You have no active listings.")
                        } else {
                            t("eazy_chat.exchange_no_listings", "No listings yet.")
                        },
                        color = palette.muted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listings.forEach { listing ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(palette.muted.copy(alpha = 0.06f))
                                .padding(12.dp),
                        ) {
                            Text(listing.title, color = palette.text, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            if (tab == ExchangeTab.MyListings) {
                                EazyGamesActionButton(t("eazy_chat.exchange_listing_cancel", "Remove listing"), filled = false) {
                                    val oid = ownerId ?: return@EazyGamesActionButton
                                    scope.launch {
                                        api.deletePrizesTradeListing(oid, listing.id, shop)
                                        refreshKey++
                                    }
                                }
                            } else {
                                EazyGamesActionButton(t("eazy_chat.exchange_make_offer", "Make offer"), filled = true) {
                                    // Offer-Dialog wie Web folgt; Button bleibt sichtbar
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseListings(arr: JSONArray): List<TradeListingItem> {
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val offered = o.optJSONObject("offered")
        val name = offered?.optString("name") ?: "Item #${o.optInt("offered_instance_id")}"
        TradeListingItem(
            id = o.optInt("id"),
            title = name,
            sellerId = o.optString("seller_id", "").takeIf { it.isNotBlank() },
        )
    }
}
