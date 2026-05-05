package com.eazpire.creator.ui.vouchers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.AuthConfig
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Currency
import java.util.Locale

private enum class GiftCardSubTab {
    PURCHASED,
    REWARDS,
    SENT,
}

private enum class MainTab {
    STORE_CREDIT,
    GIFT_CARDS,
    PROMO_CODES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoucherModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore
) {
    val t = remember(translationStore) { { k: String, d: String -> translationStore.t(k, d) } }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() ?: "" }
    val api = remember(tokenStore) { CreatorApi(jwt = tokenStore.getJwt()) }
    var giftCardDetailId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(visible) {
        if (!visible) giftCardDetailId = null
    }
    GiftCardDetailNativeModal(
        giftCardId = giftCardDetailId,
        title = t("creator.gift_cards.gift_card_details", "Gift Card Details"),
        onDismiss = { giftCardDetailId = null },
        customerId = ownerId,
        shop = AuthConfig.SHOP_DOMAIN,
        api = api,
        t = t
    )
    if (!visible) return

    var mainTab by remember { mutableStateOf(MainTab.STORE_CREDIT) }
    var giftSubTab by remember { mutableStateOf(GiftCardSubTab.PURCHASED) }
    var promoSubCreated by remember { mutableStateOf(true) }

    var loading by remember { mutableStateOf(true) }
    var payoutJson by remember { mutableStateOf<JSONObject?>(null) }
    var giftCards by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var promoJson by remember { mutableStateOf<JSONObject?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val currencyCode = remember {
        try {
            Currency.getInstance(Locale.getDefault()).currencyCode
        } catch (_: Exception) {
            "EUR"
        }
    }

    LaunchedEffect(ownerId, visible) {
        if (ownerId.isBlank() || !visible) return@LaunchedEffect
        loading = true
        errorText = null
        try {
            val p = withContext(Dispatchers.IO) { api.getCreatorPayoutOverview(ownerId, 3650) }
            val g = withContext(Dispatchers.IO) {
                api.getCustomerGiftCards(ownerId, AuthConfig.SHOP_DOMAIN)
            }
            val pr = withContext(Dispatchers.IO) { api.getPromoSlots(ownerId) }
            payoutJson = if (p.optBoolean("ok", false)) p else null
            giftCards = if (g.optBoolean("ok", false)) {
                val arr = g.optJSONArray("gift_cards") ?: JSONArray()
                (0 until arr.length()).map { arr.getJSONObject(it) }
            } else emptyList()
            promoJson = if (pr.optBoolean("ok", false)) pr else null
        } catch (e: Exception) {
            errorText = e.message ?: "Error"
        } finally {
            loading = false
        }
    }

    val screenTitle = remember(mainTab, giftSubTab, promoSubCreated, translationStore) {
        when (mainTab) {
            MainTab.STORE_CREDIT -> t("creator.voucher_page.subtab_store_credit", "Store Credit")
            MainTab.GIFT_CARDS ->
                when (giftSubTab) {
                    GiftCardSubTab.PURCHASED -> t("creator.voucher_page.subtab_purchased_gift_cards", "Purchased")
                    GiftCardSubTab.REWARDS -> t("creator.voucher_page.subtab_reward_gift_cards", "Won")
                    GiftCardSubTab.SENT -> t("creator.voucher_page.subtab_sent_gift_cards", "Sent Gift Cards")
                }
            MainTab.PROMO_CODES ->
                if (promoSubCreated) t("creator.voucher_page.subtab_created_promos", "Created Promos")
                else t("creator.voucher_page.subtab_redeemed_promos", "Redeemed Promos")
        }
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    fun reloadPromos() {
        if (ownerId.isBlank()) return
        scope.launch {
            try {
                val pr = withContext(Dispatchers.IO) { api.getPromoSlots(ownerId) }
                promoJson = if (pr.optBoolean("ok", false)) pr else null
            } catch (_: Exception) { /* ignore */ }
        }
    }

    val mainDrawerItems = remember(t) {
        listOf(
            MainTab.STORE_CREDIT to t("creator.voucher_page.subtab_store_credit", "Store Credit"),
            MainTab.GIFT_CARDS to t("creator.voucher_page.tab_gift_cards", "Gift Cards"),
            MainTab.PROMO_CODES to t("creator.voucher_page.tab_promo_codes", "Promo Codes")
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    mainDrawerItems.forEach { (dest, label) ->
                        val selected = mainTab == dest
                        Text(
                            label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    mainTab = dest
                                    scope.launch { drawerState.close() }
                                }
                                .padding(16.dp),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            content = {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    screenTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = t("eaz.voucher.modal_menu", "Menu"))
                                }
                            },
                            actions = {
                                IconButton(onClick = onDismiss) {
                                    Icon(Icons.Default.Close, contentDescription = t("accessibility.close", "Close"))
                                }
                            }
                        )
                    }
                ) { padding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                    ) {
                        when (mainTab) {
                            MainTab.GIFT_CARDS -> VoucherGiftSubTabRow(
                                selected = giftSubTab,
                                onSelect = { giftSubTab = it },
                                t = t,
                            )
                            MainTab.PROMO_CODES -> VoucherSubTabRow(
                                leftLabel = t("creator.voucher_page.subtab_created_promos", "Created Promos"),
                                rightLabel = t("creator.voucher_page.subtab_redeemed_promos", "Redeemed Promos"),
                                selectedLeft = promoSubCreated,
                                onSelectLeft = { promoSubCreated = true },
                                onSelectRight = { promoSubCreated = false }
                            )
                            else -> Spacer(Modifier.height(0.dp))
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            when (mainTab) {
                                MainTab.STORE_CREDIT ->
                                    StoreCreditPanel(payoutJson, currencyCode, loading, errorText, t)
                                MainTab.GIFT_CARDS ->
                                    GiftCardsPanel(
                                        giftCards = giftCards,
                                        mode = giftSubTab,
                                        loading = loading,
                                        errorText = errorText,
                                        t = t,
                                        onGiftCardOpen = { giftCardDetailId = it }
                                    )
                                MainTab.PROMO_CODES ->
                                    PromoPanel(
                                        promoJson = promoJson,
                                        created = promoSubCreated,
                                        loading = loading,
                                        errorText = errorText,
                                        t = t,
                                        ownerId = ownerId,
                                        api = api,
                                        onReloadPromos = { reloadPromos() }
                                    )
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun VoucherGiftSubTabRow(
    selected: GiftCardSubTab,
    onSelect: (GiftCardSubTab) -> Unit,
    t: (String, String) -> String,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SubTabChip(
                t("creator.voucher_page.subtab_purchased_gift_cards", "Purchased"),
                selected == GiftCardSubTab.PURCHASED,
                Modifier.weight(1f),
            ) { onSelect(GiftCardSubTab.PURCHASED) }
            SubTabChip(
                t("creator.voucher_page.subtab_reward_gift_cards", "Won"),
                selected == GiftCardSubTab.REWARDS,
                Modifier.weight(1f),
            ) { onSelect(GiftCardSubTab.REWARDS) }
            SubTabChip(
                t("creator.voucher_page.subtab_sent_gift_cards", "Sent"),
                selected == GiftCardSubTab.SENT,
                Modifier.weight(1f),
            ) { onSelect(GiftCardSubTab.SENT) }
        }
        Divider(color = Color(0xFFE5E7EB))
    }
}

@Composable
private fun VoucherSubTabRow(
    leftLabel: String,
    rightLabel: String,
    selectedLeft: Boolean,
    onSelectLeft: () -> Unit,
    onSelectRight: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SubTabChip(leftLabel, selectedLeft, Modifier.weight(1f), onSelectLeft)
            SubTabChip(rightLabel, !selectedLeft, Modifier.weight(1f), onSelectRight)
        }
        Divider(color = Color(0xFFE5E7EB))
    }
}

@Composable
private fun SubTabChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Text(
        label,
        modifier = modifier
            .clickable(onClick = onClick)
            .background(
                if (selected) Color(0x14000000) else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .padding(vertical = 10.dp, horizontal = 8.dp),
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        color = if (selected) EazColors.Orange else Color(0xFF6B7280),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun StoreCreditPanel(
    payoutJson: JSONObject?,
    currencyFallback: String,
    loading: Boolean,
    errorText: String?,
    t: (String, String) -> String
) {
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (errorText != null) {
        Text(errorText, modifier = Modifier.padding(16.dp), color = EazColors.TextSecondary)
        return
    }
    val payouts = payoutJson?.optJSONArray("payouts") ?: JSONArray()
    val rows = remember(payoutJson) {
        (0 until payouts.length()).map { payouts.getJSONObject(it) }
            .filter { it.optString("payoutType", "").equals("shop_credit", ignoreCase = true) }
    }
    if (rows.isEmpty()) {
        Text(
            t("creator.voucher_page.no_store_credits", "No store credit yet"),
            modifier = Modifier.padding(16.dp)
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(rows) { row ->
            WebStyleStoreCreditCard(row, currencyFallback, t)
        }
    }
}

private fun giftIsReward(gc: JSONObject): Boolean =
    gc.optString("gift_card_origin", "") == "reward"

private fun giftIsSent(gc: JSONObject): Boolean {
    return gc.optBoolean("is_buyer", false) &&
        gc.optJSONObject("email_template")?.optString("status") == "sent"
}

@Composable
private fun GiftCardsPanel(
    giftCards: List<JSONObject>,
    mode: GiftCardSubTab,
    loading: Boolean,
    errorText: String?,
    t: (String, String) -> String,
    onGiftCardOpen: (String) -> Unit
) {
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (errorText != null) {
        Text(errorText, modifier = Modifier.padding(16.dp))
        return
    }
    val list = remember(giftCards, mode) {
        giftCards.filter { gc ->
            when (mode) {
                GiftCardSubTab.SENT -> giftIsSent(gc)
                GiftCardSubTab.REWARDS -> !giftIsSent(gc) && giftIsReward(gc)
                GiftCardSubTab.PURCHASED -> !giftIsSent(gc) && !giftIsReward(gc)
            }
        }
    }
    if (list.isEmpty()) {
        val emptyMsg =
            when (mode) {
                GiftCardSubTab.SENT -> t("creator.voucher_page.no_sent_gift_cards", "No sent gift cards")
                GiftCardSubTab.REWARDS -> t("creator.gift_cards.no_reward_gift_cards", "No prizes yet")
                GiftCardSubTab.PURCHASED -> t("creator.gift_cards.no_gift_cards", "No gift cards")
            }
        Text(emptyMsg, modifier = Modifier.padding(16.dp))
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(list) { gc ->
            WebStyleGiftCard(gc, t, onOpenDetail = onGiftCardOpen)
        }
    }
}

@Composable
private fun PromoPanel(
    promoJson: JSONObject?,
    created: Boolean,
    loading: Boolean,
    errorText: String?,
    t: (String, String) -> String,
    ownerId: String,
    api: CreatorApi,
    onReloadPromos: () -> Unit
) {
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (errorText != null) {
        Text(errorText, modifier = Modifier.padding(16.dp))
        return
    }
    if (promoJson == null) {
        Text(t("creator.voucher_page.error_loading", "Could not load"), modifier = Modifier.padding(16.dp))
        return
    }
    if (created) {
        val active = promoJson.optJSONArray("active") ?: JSONArray()
        val slotsTotal = promoJson.optInt("slots_total", 5)
        val activeCount = (0 until active.length()).count()
        val emptySlots = (slotsTotal - activeCount).coerceAtLeast(0)
        val slotItems = remember(active) {
            (0 until active.length()).map { active.getJSONObject(it) }
        }
        val counterLine = t("creator.voucher_page.promo_slots_counter", "{available} / {total} slots available")
            .replace("{available}", emptySlots.toString())
            .replace("{total}", slotsTotal.toString())
        Column(Modifier.fillMaxSize()) {
            Text(
                counterLine,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                fontSize = 13.sp,
                color = Color(0xFF6B7280)
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(220.dp),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(slotItems) { p ->
                    PromoSlotCardWeb(
                        promo = p,
                        t = t,
                        ownerId = ownerId,
                        api = api,
                        onReload = onReloadPromos
                    )
                }
                items(emptySlots) {
                    PromoEmptySlotWeb(t = t)
                }
            }
        }
    } else {
        val redeemed = promoJson.optJSONArray("redeemed") ?: JSONArray()
        val received = promoJson.optJSONArray("received") ?: JSONArray()
        val all = remember(redeemed, received) {
            buildList {
                for (i in 0 until redeemed.length()) add(redeemed.getJSONObject(i))
                for (i in 0 until received.length()) add(received.getJSONObject(i))
            }
        }
        if (all.isEmpty()) {
            Text(
                t("creator.voucher_page.no_redeemed_promos", "No redeemed promos"),
                modifier = Modifier.padding(16.dp)
            )
            return
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(280.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(all) { p ->
                PromoRedeemedRowWeb(p = p, t = t)
            }
        }
    }
}
