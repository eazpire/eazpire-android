package com.eazpire.creator.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ShoppingBag
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.AuthConfig
import com.eazpire.creator.ui.creator.WearPairQrScannerOverlay
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ArtifactsHubSection { Nfts, Outfit, Exchange, Marketplace }
enum class ArtifactsExchangeSub { Market, MyListings }
enum class ArtifactsMarketSub { Buy, Sell }

@Composable
fun EazyArtifactsHubPanel(
    api: CreatorApi,
    ownerId: String?,
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit,
    pendingClaimToken: String? = null,
    onPendingClaimConsumed: () -> Unit = {},
    t: (String, String) -> String,
) {
    var section by remember { mutableStateOf(ArtifactsHubSection.Nfts) }
    var exchangeSub by remember { mutableStateOf(ArtifactsExchangeSub.Market) }
    var marketSub by remember { mutableStateOf(ArtifactsMarketSub.Buy) }
    var isArtifactsAdmin by remember { mutableStateOf(false) }
    var adminQrUrl by remember { mutableStateOf<String?>(null) }
    var slotFilter by remember { mutableStateOf("all") }
    var loading by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var showQrScanner by remember { mutableStateOf(false) }
    var claimBusy by remember { mutableStateOf(false) }
    var claimMessage by remember { mutableStateOf<String?>(null) }
    var inventory by remember { mutableStateOf<List<ArtifactSlot>>(emptyList()) }
    var loadout by remember {
        mutableStateOf(
            ArtifactLoadoutState(emptyMap(), emptyMap(), null, false, null),
        )
    }
    var tradeListings by remember { mutableStateOf<List<ArtifactTradeListing>>(emptyList()) }
    var marketListings by remember { mutableStateOf<List<ArtifactMarketListing>>(emptyList()) }
    var sellableCharacters by remember { mutableStateOf<List<ArtifactCharacter>>(emptyList()) }
    var tradeTokens by remember { mutableIntStateOf(0) }
    val palette = LocalEazyModalPalette.current
    val shop = AuthConfig.SHOP_DOMAIN
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        if (!isLoggedIn || ownerId.isNullOrBlank()) return
        loading = true
        try {
            when (section) {
                ArtifactsHubSection.Nfts -> {
                    val res = withContext(Dispatchers.IO) {
                        api.getArtifactsInventoryList(ownerId, shop, slotFilter.takeIf { it != "all" })
                    }
                    inventory = if (res.optBoolean("ok", false)) ArtifactsJson.parseSlots(res.optJSONArray("slots")) else emptyList()
                }
                ArtifactsHubSection.Outfit -> {
                    val inv = withContext(Dispatchers.IO) {
                        api.getArtifactsInventoryList(ownerId, shop)
                    }
                    inventory = if (inv.optBoolean("ok", false)) ArtifactsJson.parseSlots(inv.optJSONArray("slots")) else emptyList()
                    val lo = withContext(Dispatchers.IO) { api.getArtifactsLoadout(ownerId, shop) }
                    if (lo.optBoolean("ok", false)) loadout = ArtifactsJson.parseLoadoutResponse(lo)
                }
                ArtifactsHubSection.Exchange -> {
                    val state = withContext(Dispatchers.IO) { api.getArtifactsInventoryState(ownerId, shop) }
                    tradeTokens = state.optInt("trade_tokens", 0)
                    val inv = withContext(Dispatchers.IO) {
                        api.getArtifactsInventoryList(ownerId, shop)
                    }
                    inventory = if (inv.optBoolean("ok", false)) ArtifactsJson.parseSlots(inv.optJSONArray("slots")) else emptyList()
                    val tr = withContext(Dispatchers.IO) {
                        api.getArtifactsTradeListings(
                            shop,
                            scope = if (exchangeSub == ArtifactsExchangeSub.MyListings) "mine" else "market",
                        )
                    }
                    tradeListings = if (tr.optBoolean("ok", false)) {
                        ArtifactsJson.parseTradeListings(tr.optJSONArray("listings"))
                    } else {
                        emptyList()
                    }
                }
                ArtifactsHubSection.Marketplace -> {
                    val mk = withContext(Dispatchers.IO) {
                        api.getArtifactsMarketList(
                            shop,
                            scope = if (marketSub == ArtifactsMarketSub.Sell) "sell" else "buy",
                        )
                    }
                    marketListings = if (mk.optBoolean("ok", false)) {
                        ArtifactsJson.parseMarketListings(mk.optJSONArray("listings"))
                    } else {
                        emptyList()
                    }
                    sellableCharacters = if (marketSub == ArtifactsMarketSub.Sell && mk.optBoolean("ok", false)) {
                        ArtifactsJson.parseCharacters(mk.optJSONArray("sellable_characters"))
                    } else {
                        emptyList()
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            loading = false
        }
    }

    LaunchedEffect(section, ownerId, isLoggedIn, slotFilter, refreshKey, exchangeSub, marketSub) {
        reload()
    }

    LaunchedEffect(ownerId, isLoggedIn) {
        if (!isLoggedIn || ownerId.isNullOrBlank()) {
            isArtifactsAdmin = false
            return@LaunchedEffect
        }
        isArtifactsAdmin = try {
            withContext(Dispatchers.IO) { api.getAdminArtifactsOverview(shop).optBoolean("ok", false) }
        } catch (_: Exception) {
            false
        }
    }

    LaunchedEffect(inventory, section) {
        if (section != ArtifactsHubSection.Nfts) return@LaunchedEffect
        while (inventory.any { it.generationStatus == "generating" }) {
            delay(3500)
            refreshKey++
        }
    }

    suspend fun claimToken(token: String): Boolean {
        val oid = ownerId?.trim().orEmpty()
        if (!isLoggedIn || oid.isBlank()) return false
        claimBusy = true
        claimMessage = null
        return try {
            val res = withContext(Dispatchers.IO) {
                api.postArtifactsClaimQr(oid, shop, token.trim())
            }
            if (res.optBoolean("ok", false)) {
                claimMessage = t("eazy_chat.artifacts_claim_success", "Slot NFT claimed!")
                refreshKey++
                true
            } else {
                claimMessage = res.optString("error", t("eazy_chat.artifacts_claim_failed", "Claim failed"))
                false
            }
        } catch (e: Exception) {
            claimMessage = e.message ?: t("eazy_chat.artifacts_claim_failed", "Claim failed")
            false
        } finally {
            claimBusy = false
        }
    }

    LaunchedEffect(pendingClaimToken, ownerId, isLoggedIn) {
        val token = pendingClaimToken?.trim().orEmpty()
        if (token.isBlank() || !isLoggedIn || ownerId.isNullOrBlank()) return@LaunchedEffect
        section = ArtifactsHubSection.Nfts
        claimToken(token)
        onPendingClaimConsumed()
    }

    if (showQrScanner) {
        WearPairQrScannerOverlay(
            hint = t("eazy_chat.artifacts_scan_hint", "Scan product QR to claim"),
            parseToken = ArtifactsJson::parseClaimToken,
            onScanned = { token ->
                showQrScanner = false
                scope.launch { claimToken(token) }
            },
            onDismiss = { showQrScanner = false },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ArtifactsSubheader(section = section, onSection = { section = it }, t = t)

        if (section == ArtifactsHubSection.Exchange) {
            ArtifactsSectionSubnav(
                left = t("eazy_chat.artifacts_exchange_market", "Market") to (exchangeSub == ArtifactsExchangeSub.Market),
                right = t("eazy_chat.artifacts_exchange_my_listings", "My Listings") to (exchangeSub == ArtifactsExchangeSub.MyListings),
                onLeft = { exchangeSub = ArtifactsExchangeSub.Market },
                onRight = { exchangeSub = ArtifactsExchangeSub.MyListings },
            )
        }
        if (section == ArtifactsHubSection.Marketplace) {
            ArtifactsSectionSubnav(
                left = t("eazy_chat.artifacts_market_buy", "Buy") to (marketSub == ArtifactsMarketSub.Buy),
                right = t("eazy_chat.artifacts_market_sell", "Sell") to (marketSub == ArtifactsMarketSub.Sell),
                onLeft = { marketSub = ArtifactsMarketSub.Buy },
                onRight = { marketSub = ArtifactsMarketSub.Sell },
            )
        }

        if (section == ArtifactsHubSection.Nfts) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = { showQrScanner = true },
                    enabled = isLoggedIn && !claimBusy,
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t("eazy_chat.artifacts_scan_qr", "Scan QR"))
                }
            }
        }

        if (section == ArtifactsHubSection.Nfts || section == ArtifactsHubSection.Exchange) {
            EazyArtifactsSlotFilterRow(slotFilter = slotFilter, onFilter = { slotFilter = it }, t = t)
        }

        if (!isLoggedIn) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(t("eazy_chat.login_required_text", "Sign in to chat with eazy"), color = palette.muted)
                TextButton(onClick = onLoginClick) {
                    Text(t("eazy_chat.login_required_btn", "Sign in"))
                }
            }
            return@Column
        }

        if (claimBusy) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = palette.accent)
            }
            return@Column
        }

        claimMessage?.let { msg ->
            AlertDialog(
                onDismissRequest = { claimMessage = null },
                title = { Text(t("eazy_chat.ui_artifacts_tab", "Artifacts")) },
                text = { Text(msg) },
                confirmButton = {
                    TextButton(onClick = { claimMessage = null }) {
                        Text(t("eazy_chat.close", "Close"))
                    }
                },
            )
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = palette.accent)
            }
            return@Column
        }

        val oid = ownerId ?: return@Column
        when (section) {
            ArtifactsHubSection.Nfts -> EazyArtifactsNftsPanel(slots = inventory, t = t)
            ArtifactsHubSection.Outfit -> EazyArtifactsOutfitPanel(
                api = api,
                ownerId = oid,
                shop = shop,
                inventory = inventory,
                loadout = loadout,
                onLoadoutChanged = { refreshKey++ },
                t = t,
            )
            ArtifactsHubSection.Exchange -> EazyArtifactsExchangePanel(
                api = api,
                ownerId = oid,
                shop = shop,
                listings = tradeListings,
                tradeTokens = tradeTokens,
                inventory = inventory,
                slotFilter = slotFilter,
                isMyListings = exchangeSub == ArtifactsExchangeSub.MyListings,
                onRefresh = { refreshKey++ },
                t = t,
            )
            ArtifactsHubSection.Marketplace -> EazyArtifactsMarketplacePanel(
                api = api,
                ownerId = oid,
                shop = shop,
                listings = marketListings,
                sellableCharacters = sellableCharacters,
                isSellMode = marketSub == ArtifactsMarketSub.Sell,
                onRefresh = { refreshKey++ },
                t = t,
            )
        }

        if (isArtifactsAdmin) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                val res = withContext(Dispatchers.IO) { api.postAdminArtifactsGrantQr(shop) }
                                if (res.optBoolean("ok", false)) {
                                    adminQrUrl = res.optString("qr_url", res.optString("claim_url", ""))
                                    section = ArtifactsHubSection.Nfts
                                }
                            } catch (_: Exception) {
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t("eazy_chat.artifacts_admin_scan_qr", "Scan QR"))
                }
            }
        }
    }

    adminQrUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { adminQrUrl = null },
            title = { Text(t("eazy_chat.artifacts_admin_qr_title", "Test QR Claim")) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t("eazy_chat.artifacts_admin_qr_hint", "Scan this QR to claim a test slot NFT."))
                    Spacer(Modifier.size(8.dp))
                    Text(url, style = MaterialTheme.typography.bodySmall, color = palette.muted)
                }
            },
            confirmButton = {
                TextButton(onClick = { adminQrUrl = null }) {
                    Text(t("eazy_chat.close", "Close"))
                }
            },
        )
    }
}

@Composable
private fun ArtifactsSectionSubnav(
    left: Pair<String, Boolean>,
    right: Pair<String, Boolean>,
    onLeft: () -> Unit,
    onRight: () -> Unit,
) {
    val palette = LocalEazyModalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ArtifactsSubnavChip(left.first, left.second, palette, Modifier.weight(1f), onLeft)
        ArtifactsSubnavChip(right.first, right.second, palette, Modifier.weight(1f), onRight)
    }
}

@Composable
private fun ArtifactsSubnavChip(
    label: String,
    active: Boolean,
    palette: EazyModalPalette,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Text(
        label,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (active) palette.accent.copy(alpha = 0.15f) else palette.muted.copy(alpha = 0.08f))
            .border(1.dp, if (active) palette.accent else palette.border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 10.dp),
        color = if (active) Color.White else palette.text.copy(alpha = 0.8f),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun ArtifactsSubheader(
    section: ArtifactsHubSection,
    onSection: (ArtifactsHubSection) -> Unit,
    t: (String, String) -> String,
) {
    val palette = LocalEazyModalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.headerSecondary)
            .border(width = 1.dp, color = palette.border.copy(alpha = 0.35f))
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ArtifactsTabButton(
                active = section == ArtifactsHubSection.Nfts,
                imageVector = Icons.Default.Collections,
                label = t("eazy_chat.artifacts_section_nfts", "NFTs"),
                showLabel = true,
                onClick = { onSection(ArtifactsHubSection.Nfts) },
            )
            ArtifactsTabButton(
                active = section == ArtifactsHubSection.Outfit,
                imageVector = Icons.Default.Checkroom,
                label = t("eazy_chat.artifacts_section_outfit", "Outfit"),
                showLabel = true,
                onClick = { onSection(ArtifactsHubSection.Outfit) },
            )
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ArtifactsTabButton(
                active = section == ArtifactsHubSection.Exchange,
                imageVector = Icons.Default.SwapHoriz,
                label = t("eazy_chat.artifacts_section_exchange", "Exchange"),
                showLabel = false,
                onClick = { onSection(ArtifactsHubSection.Exchange) },
            )
            ArtifactsTabButton(
                active = section == ArtifactsHubSection.Marketplace,
                imageVector = Icons.Default.ShoppingBag,
                label = t("eazy_chat.artifacts_section_marketplace", "Marketplace"),
                showLabel = false,
                onClick = { onSection(ArtifactsHubSection.Marketplace) },
            )
        }
    }
}

@Composable
private fun ArtifactsTabButton(
    active: Boolean,
    imageVector: ImageVector,
    label: String,
    showLabel: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalEazyModalPalette.current
    val bg = if (active) palette.accent else palette.muted.copy(alpha = 0.08f)
    val border = if (active) palette.accent else palette.border
    val contentColor = if (active) Color.White else palette.text.copy(alpha = 0.82f)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = if (showLabel) 14.dp else 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(imageVector, contentDescription = label, tint = contentColor, modifier = Modifier.size(18.dp))
        if (showLabel) {
            Text(
                label,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
