package com.eazpire.creator.ui.vouchers

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Matches web modal: Store Credit + four subtabs (own/sent gift cards, created/redeemed promos). */
private enum class VoucherSection {
    STORE_CREDIT,
    GC_OWN,
    GC_SENT,
    PROMO_CREATED,
    PROMO_REDEEMED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoucherModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore
) {
    if (!visible) return
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() ?: "" }
    val api = remember(tokenStore) { CreatorApi(jwt = tokenStore.getJwt()) }
    val t = remember(translationStore) { { k: String, d: String -> translationStore.t(k, d) } }

    var section by remember { mutableStateOf(VoucherSection.STORE_CREDIT) }

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

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val drawerItems = remember(t) {
        listOf(
            VoucherSection.STORE_CREDIT to t("creator.voucher_page.subtab_store_credit", "Store Credit"),
            VoucherSection.GC_OWN to t("creator.voucher_page.subtab_own_gift_cards", "My gift cards"),
            VoucherSection.GC_SENT to t("creator.voucher_page.subtab_sent_gift_cards", "Sent gift cards"),
            VoucherSection.PROMO_CREATED to t("creator.voucher_page.subtab_created_promos", "Created promos"),
            VoucherSection.PROMO_REDEEMED to t("creator.voucher_page.subtab_redeemed_promos", "Redeemed promos")
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
                    drawerItems.forEach { (dest, label) ->
                        val selected = section == dest
                        Text(
                            label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    section = dest
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
                                Text(t("eaz.sidebar.my_gift_cards", "My Gift Cards"))
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
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            when (section) {
                                VoucherSection.STORE_CREDIT ->
                                    StoreCreditPanel(payoutJson, currencyCode, loading, errorText, t)
                                VoucherSection.GC_OWN ->
                                    GiftCardsPanel(
                                        giftCards = giftCards,
                                        own = true,
                                        loading = loading,
                                        errorText = errorText,
                                        currencyFallback = currencyCode,
                                        t = t
                                    )
                                VoucherSection.GC_SENT ->
                                    GiftCardsPanel(
                                        giftCards = giftCards,
                                        own = false,
                                        loading = loading,
                                        errorText = errorText,
                                        currencyFallback = currencyCode,
                                        t = t
                                    )
                                VoucherSection.PROMO_CREATED ->
                                    PromoPanel(
                                        promoJson = promoJson,
                                        created = true,
                                        loading = loading,
                                        errorText = errorText,
                                        t = t
                                    )
                                VoucherSection.PROMO_REDEEMED ->
                                    PromoPanel(
                                        promoJson = promoJson,
                                        created = false,
                                        loading = loading,
                                        errorText = errorText,
                                        t = t
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
        columns = GridCells.Adaptive(220.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(rows) { row ->
            val amount = row.optDouble("amount", 0.0)
            val cur = row.optString("payoutCurrency", currencyFallback)
            val label = formatMoney(amount, cur)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(label, fontWeight = FontWeight.Bold)
                    Text(t("creator.voucher_page.subtab_store_credit", "Store Credit"), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun giftIsSent(gc: JSONObject): Boolean {
    return gc.optBoolean("is_buyer", false) &&
        gc.optJSONObject("email_template")?.optString("status") == "sent"
}

@Composable
private fun GiftCardsPanel(
    giftCards: List<JSONObject>,
    own: Boolean,
    loading: Boolean,
    errorText: String?,
    currencyFallback: String,
    t: (String, String) -> String
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
    val list = remember(giftCards, own) {
        giftCards.filter { gc -> if (own) !giftIsSent(gc) else giftIsSent(gc) }
    }
    if (list.isEmpty()) {
        Text(
            if (own) t("creator.gift_cards.no_gift_cards", "No gift cards")
            else t("creator.voucher_page.no_sent_gift_cards", "No sent gift cards"),
            modifier = Modifier.padding(16.dp)
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(220.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(list) { gc ->
            val bal = gc.optDouble("balance", 0.0)
            val cur = gc.optString("currency", currencyFallback)
            val masked = gc.optString("masked_code", gc.optString("last_characters", "••••"))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(masked, fontWeight = FontWeight.Medium)
                    Text(formatMoney(bal, cur), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun PromoPanel(
    promoJson: JSONObject?,
    created: Boolean,
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
        val count = (0 until active.length()).count()
        val available = (slotsTotal - count).coerceAtLeast(0)
        val slotItems = remember(active) {
            (0 until active.length()).map { active.getJSONObject(it) }
        }
        Column(Modifier.fillMaxSize()) {
            Text(
                "$available / $slotsTotal ${t("creator.voucher_page.loading_promos", "slots")}",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
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
                    val code = p.optString("discount_code", "")
                    val amt = p.optDouble("value_amount", 0.0)
                    val cur = p.optString("value_currency", "EUR")
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(code, fontWeight = FontWeight.Bold)
                            Text(formatMoney(amt, cur))
                        }
                    }
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
            columns = GridCells.Adaptive(220.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(all) { p ->
                val code = p.optString("discount_code", "")
                val amt = p.optDouble("value_amount", 0.0)
                val cur = p.optString("value_currency", "EUR")
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(code, fontWeight = FontWeight.Medium)
                            Text(formatMoney(amt, cur))
                        }
                    }
                }
            }
        }
    }
}

private fun formatMoney(amount: Double, currency: String): String {
    return try {
        NumberFormat.getCurrencyInstance(Locale.getDefault()).apply {
            this.currency = Currency.getInstance(currency)
        }.format(amount)
    } catch (_: Exception) {
        "%.2f %s".format(amount, currency)
    }
}
