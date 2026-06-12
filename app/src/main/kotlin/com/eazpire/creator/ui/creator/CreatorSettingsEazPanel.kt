package com.eazpire.creator.ui.creator

import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import androidx.compose.runtime.collectAsState
import com.eazpire.creator.billing.EazBalanceRefreshBus
import com.eazpire.creator.billing.EazCostCatalog
import com.eazpire.creator.billing.EazPackageCatalog
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private const val SETTINGS_TAB_CREATOR_CODES = 2
private const val DAY_MS = 86_400_000L

private enum class EazSettingsSubTab { Balance, Logs, Buy, Costs }

private enum class EazLogFilter(val key: String) {
    All("all"),
    Purchased("purchased"),
    Free("free"),
    Usage("usage"),
}

@Composable
fun CreatorSettingsEazPanel(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    onRequestSettingsTab: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ownerId = remember { tokenStore.getOwnerId().orEmpty() }
    val jwt = remember { tokenStore.getJwt() }
    val api = remember(jwt) { CreatorApi(jwt = jwt) }
    var subTab by remember { mutableIntStateOf(0) }
    var balanceData by remember { mutableStateOf<JSONObject?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var checkoutLoading by remember { mutableStateOf(false) }
    val balanceRefreshTick by EazBalanceRefreshBus.tick.collectAsState()

    val tabs = listOf(
        EazSettingsSubTab.Balance,
        EazSettingsSubTab.Logs,
        EazSettingsSubTab.Buy,
        EazSettingsSubTab.Costs
    )

    suspend fun reloadBalance() {
        if (ownerId.isBlank()) {
            balanceData = null
            isLoading = false
            return
        }
        isLoading = true
        try {
            balanceData = withContext(Dispatchers.IO) { api.getBalance(ownerId) }
        } catch (_: Exception) {
            balanceData = null
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(ownerId, balanceRefreshTick) {
        reloadBalance()
    }

    fun openStripeCheckout(eaz: Int) {
        if (ownerId.isBlank()) {
            Toast.makeText(
                context,
                translationStore.t("creator.settings.eaz_checkout_need_account", "Sign in to continue to checkout."),
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        scope.launch {
            checkoutLoading = true
            try {
                val resp = withContext(Dispatchers.IO) { api.eazStripeCheckout(ownerId, eaz) }
                val url = resp.optString("url", "")
                if (resp.optBoolean("ok", false) && url.isNotBlank()) {
                    CustomTabsIntent.Builder().setShowTitle(true).build()
                        .launchUrl(context, Uri.parse(url))
                } else {
                    Toast.makeText(
                        context,
                        translationStore.t("creator.settings.eaz_stripe_failed", "Checkout could not start. Please try again in a moment."),
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (_: Exception) {
                Toast.makeText(
                    context,
                    translationStore.t("creator.settings.eaz_stripe_failed", "Checkout could not start. Please try again in a moment."),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                checkoutLoading = false
            }
        }
    }

    val walletLocked = balanceData?.optBoolean("eaz_wallet_active", true) == false

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = translationStore.t("creator.settings.eaz_subtitle", "Free and purchased balance, activity, and packs."),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEachIndexed { index, tab ->
                val label = when (tab) {
                    EazSettingsSubTab.Balance -> translationStore.t("creator.settings.eaz_tab_balance", "Balance")
                    EazSettingsSubTab.Logs -> translationStore.t("creator.settings.eaz_tab_logs", "Logs")
                    EazSettingsSubTab.Buy -> translationStore.t("creator.settings.eaz_tab_buy", "Buy EAZ")
                    EazSettingsSubTab.Costs -> translationStore.t("creator.settings.eaz_tab_costs", "EAZ Costs")
                }
                OutlinedButton(
                    onClick = { subTab = index },
                    colors = if (subTab == index) {
                        ButtonDefaults.outlinedButtonColors(
                            containerColor = EazColors.Orange.copy(alpha = 0.2f)
                        )
                    } else {
                        ButtonDefaults.outlinedButtonColors()
                    }
                ) {
                    Text(
                        label,
                        color = if (subTab == index) EazColors.Orange else Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        when (tabs[subTab]) {
            EazSettingsSubTab.Balance -> EazBalanceSubPanel(
                balanceData = balanceData,
                isLoading = isLoading,
                translationStore = translationStore,
                walletLocked = walletLocked,
                onBuyClick = {
                    if (walletLocked) onRequestSettingsTab(SETTINGS_TAB_CREATOR_CODES)
                    else subTab = tabs.indexOf(EazSettingsSubTab.Buy)
                }
            )
            EazSettingsSubTab.Logs -> EazLogsSubPanel(
                ownerId = ownerId,
                api = api,
                translationStore = translationStore,
                walletLocked = walletLocked,
            )
            EazSettingsSubTab.Buy -> EazBuySubPanel(
                balanceData = balanceData,
                isLoading = isLoading || checkoutLoading,
                translationStore = translationStore,
                walletLocked = walletLocked,
                onBuyPack = { eaz ->
                    if (walletLocked) onRequestSettingsTab(SETTINGS_TAB_CREATOR_CODES)
                    else openStripeCheckout(eaz)
                }
            )
            EazSettingsSubTab.Costs -> EazCostsSubPanel(balanceData, isLoading, translationStore)
        }
    }
}

@Composable
private fun EazWalletLockBanner(translationStore: TranslationStore) {
    Text(
        text = translationStore.t(
            "creator.settings.eaz_wallet_locked_hint",
            "Redeem a Creator Code to unlock your EAZ wallet and purchases."
        ),
        style = MaterialTheme.typography.bodySmall,
        color = Color(0xFFFBBF24),
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFBBF24).copy(alpha = 0.12f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    )
}

@Composable
private fun EazBalanceSubPanel(
    balanceData: JSONObject?,
    isLoading: Boolean,
    translationStore: TranslationStore,
    walletLocked: Boolean,
    onBuyClick: () -> Unit,
) {
    if (walletLocked) {
        EazWalletLockBanner(translationStore)
    }

    if (isLoading && balanceData == null) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(24.dp))
        }
        return
    }

    val data = balanceData
    if (data == null || !data.optBoolean("ok", false)) {
        Text(
            translationStore.t("creator.settings.eaz_refill_loading", "Loading refill details…"),
            color = Color.White.copy(alpha = 0.65f),
            style = MaterialTheme.typography.bodySmall
        )
        return
    }

    if (walletLocked) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EazStatRow(
                translationStore.t("creator.settings.eaz_total_label", "Total balance"),
                "—"
            )
            EazStatRow(
                translationStore.t("creator.settings.eaz_free_label", "Free EAZ"),
                "— / 0"
            )
            EazStatRow(
                translationStore.t("creator.settings.eaz_purchased_label", "Purchased EAZ"),
                "—"
            )
        }
        Button(
            onClick = onBuyClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange)
        ) {
            Text(
                translationStore.t("creator.settings.eaz_enter_creator_code_cta", "Enter Creator Code"),
                color = Color.White
            )
        }
        return
    }

    val total = data.optDouble("balance_total", data.optDouble("balance_eaz", 0.0))
    val free = data.optDouble("balance_free", 0.0)
    val purchased = data.optDouble("balance_purchased", 0.0)
    val freeCap = data.optDouble("free_cap", 50.0)
    val nextAmt = data.optDouble("next_free_refill_amount", 0.0)
    val nextAt = data.optLong("next_free_refill_at", 0L).takeIf { it > 0L }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        EazStatRow(
            translationStore.t("creator.settings.eaz_total_label", "Total balance"),
            "${EazCostCatalog.fmtEaz(total)} EAZ"
        )
        EazStatRow(
            translationStore.t("creator.settings.eaz_free_label", "Free EAZ"),
            "${EazCostCatalog.fmtEaz(free)} / ${EazCostCatalog.fmtEaz(freeCap)}"
        )
        EazStatRow(
            translationStore.t("creator.settings.eaz_purchased_label", "Purchased EAZ"),
            "${EazCostCatalog.fmtEaz(purchased)} EAZ"
        )

        val refillPrefix = translationStore.t(
            "creator.settings.eaz_refill_prefix",
            "At the next daily reset, up to %N free EAZ can be added (until your free pool reaches the cap)."
        )
        Text(
            text = refillPrefix
                .replace("%N", EazCostCatalog.fmtEaz(nextAmt))
                .replace("%{N}", EazCostCatalog.fmtEaz(nextAmt)),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.65f)
        )

        if (nextAt != null) {
            EazResetCountdown(nextAt, translationStore)
        }

        Button(
            onClick = onBuyClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange)
        ) {
            Text(
                translationStore.t("creator.settings.eaz_buy_cta", "Buy EAZ"),
                color = Color.White
            )
        }
    }
}

@Composable
private fun EazResetCountdown(nextAtMs: Long, translationStore: TranslationStore) {
    var progress by remember(nextAtMs) { mutableStateOf(1f) }
    var countdownText by remember(nextAtMs) { mutableStateOf("") }

    LaunchedEffect(nextAtMs) {
        while (true) {
            val now = System.currentTimeMillis()
            val leftSec = ((nextAtMs - now) / 1000L).coerceAtLeast(0L)
            progress = ((nextAtMs - now).toFloat() / DAY_MS.toFloat()).coerceIn(0f, 1f)
            val h = leftSec / 3600
            val m = (leftSec % 3600) / 60
            val s = leftSec % 60
            val timeStr = "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
            val tpl = translationStore.t("creator.settings.eaz_until_reset", "Time until daily reset: {{time}}")
            countdownText = tpl.replace("{{time}}", timeStr)
            if (leftSec <= 0L) break
            delay(1000)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth(),
            color = EazColors.Orange,
            trackColor = Color.White.copy(alpha = 0.12f),
        )
        Text(
            text = countdownText,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.65f)
        )
    }
}

@Composable
private fun EazLogsSubPanel(
    ownerId: String,
    api: CreatorApi,
    translationStore: TranslationStore,
    walletLocked: Boolean,
) {
    var filter by remember { mutableStateOf(EazLogFilter.All) }
    var transactions by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) {
            transactions = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        try {
            val resp = withContext(Dispatchers.IO) { api.getTransactions(ownerId) }
            val arr = resp.optJSONArray("transactions") ?: JSONArray()
            transactions = buildList {
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { add(it) }
                }
            }
        } catch (_: Exception) {
            transactions = emptyList()
        } finally {
            loading = false
        }
    }

    if (walletLocked) {
        EazWalletLockBanner(translationStore)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EazLogFilter.entries.forEach { f ->
            val label = when (f) {
                EazLogFilter.All -> translationStore.t("creator.settings.eaz_logs_sub_all", "All")
                EazLogFilter.Purchased -> translationStore.t("creator.settings.eaz_logs_sub_purchased", "Purchased")
                EazLogFilter.Free -> translationStore.t("creator.settings.eaz_logs_sub_free", "Free")
                EazLogFilter.Usage -> translationStore.t("creator.settings.eaz_logs_sub_usage", "Usage")
            }
            OutlinedButton(
                onClick = { filter = f },
                colors = if (filter == f) {
                    ButtonDefaults.outlinedButtonColors(containerColor = EazColors.Orange.copy(alpha = 0.2f))
                } else {
                    ButtonDefaults.outlinedButtonColors()
                }
            ) {
                Text(
                    label,
                    color = if (filter == f) EazColors.Orange else Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }

    if (loading) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(16.dp))
        }
        return
    }

    val filtered = transactions.filter { eazLogMatchesFilter(filter, it) }
    if (transactions.isEmpty()) {
        Text(
            translationStore.t("creator.settings.eaz_logs_empty", "No transactions yet."),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 16.dp)
        )
        return
    }
    if (filtered.isEmpty()) {
        Text(
            translationStore.t("creator.settings.eaz_logs_empty_filtered", "No entries in this category."),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 16.dp)
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        filtered.forEach { row ->
            EazLogRow(row, translationStore)
        }
    }
}

private fun eazLogMatchesFilter(filter: EazLogFilter, row: JSONObject): Boolean {
    if (filter == EazLogFilter.All) return true
    val bucket = row.optString("eaz_bucket", "")
    val type = row.optString("type", "")
    return when (filter) {
        EazLogFilter.Usage -> type == "debit"
        EazLogFilter.Purchased ->
            (type == "credit" || type == "refund" || type == "adjustment") && bucket == "purchased"
        EazLogFilter.Free ->
            (type == "credit" || type == "refund" || type == "adjustment") && bucket == "free"
        EazLogFilter.All -> true
    }
}

@Composable
private fun EazLogRow(row: JSONObject, translationStore: TranslationStore) {
    val amt = row.optDouble("amount_eaz", 0.0)
    val type = row.optString("type", "")
    val sign = if (type == "credit" || type == "refund") "+" else "−"
    val reason = formatLedgerReason(row, translationStore)
    val meta = row.optJSONObject("meta")
    val split = buildString {
        if (meta != null && (meta.has("debit_free") || meta.has("debit_purchased"))) {
            val tpl = translationStore.t(
                "creator.settings.eaz_log_debit_split",
                "Free {{free}} / Purchased {{purchased}}"
            )
            append(
                " (" + tpl
                    .replace("{{free}}", EazCostCatalog.fmtEaz(meta.optDouble("debit_free", 0.0)))
                    .replace("{{purchased}}", EazCostCatalog.fmtEaz(meta.optDouble("debit_purchased", 0.0))) + ")"
            )
        }
    }
    val createdAt = row.opt("created_at")
    val timeLabel = formatLogTime(createdAt)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "$sign${EazCostCatalog.fmtEaz(kotlin.math.abs(amt))} · $reason$split",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.9f),
            fontWeight = FontWeight.SemiBold
        )
        if (timeLabel.isNotBlank()) {
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

private fun formatLedgerReason(row: JSONObject, translationStore: TranslationStore): String {
    val reason = row.optString("reason", "")
    if (reason == "eaz_pack_dry_run") {
        return translationStore.t(
            "creator.settings.eaz_ledger_reason_eaz_pack_dry_run",
            "EAZ pack (test)"
        )
    }
    val raw = reason.ifBlank { row.optString("type", "") }
    return raw.take(96)
}

private fun formatLogTime(raw: Any?): String {
    if (raw == null) return ""
    val ms = try {
        when (raw) {
            is Number -> {
                val n = raw.toLong()
                if (n < 1_000_000_000_000L) n * 1000 else n
            }
            is String -> {
                raw.toLongOrNull()?.let { if (it < 1_000_000_000_000L) it * 1000 else it }
                    ?: java.time.Instant.parse(raw).toEpochMilli()
            }
            else -> return ""
        }
    } catch (_: Exception) {
        return ""
    }
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
        .format(Date(ms))
}

@Composable
private fun EazBuySubPanel(
    balanceData: JSONObject?,
    isLoading: Boolean,
    translationStore: TranslationStore,
    walletLocked: Boolean,
    onBuyPack: (Int) -> Unit,
) {
    if (walletLocked) {
        EazWalletLockBanner(translationStore)
    }

    Text(
        text = translationStore.t(
            "creator.settings.eaz_buy_intro",
            "Choose an EAZ pack. Checkout is handled securely via Stripe."
        ),
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.7f)
    )

    val genCost = EazCostCatalog.resolveCost(balanceData, "design_generate").takeIf { it > 0 } ?: 10.0
    val uploadCost = EazCostCatalog.resolveCost(balanceData, "design_upload").takeIf { it > 0 } ?: 1.0
    val freeCap = if (walletLocked) 50.0 else balanceData?.optDouble("free_cap", 50.0) ?: 50.0
    val freeUploads = if (uploadCost > 0) kotlin.math.floor(freeCap / uploadCost).toInt() else 0
    val baseline = EazPackageCatalog.packs.firstOrNull()
    val shopCta = if (walletLocked) {
        translationStore.t("creator.settings.eaz_enter_creator_code_cta", "Enter Creator Code")
    } else {
        translationStore.t("creator.settings.eaz_shop_cta", "Buy Now")
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EazPackageCatalog.packs.forEachIndexed { index, pack ->
            EazPackageCard(
                pack = pack,
                index = index,
                genCost = genCost,
                uploadCost = uploadCost,
                freeCap = freeCap,
                freeUploads = freeUploads,
                baseline = baseline,
                shopCta = shopCta,
                isLoading = isLoading,
                translationStore = translationStore,
                onBuy = { onBuyPack(pack.eaz) }
            )
        }
    }
}

@Composable
private fun EazPackageCard(
    pack: EazPackageCatalog.Pack,
    index: Int,
    genCost: Double,
    uploadCost: Double,
    freeCap: Double,
    freeUploads: Int,
    baseline: EazPackageCatalog.Pack?,
    shopCta: String,
    isLoading: Boolean,
    translationStore: TranslationStore,
    onBuy: () -> Unit,
) {
    val gens = if (genCost > 0) kotlin.math.floor(pack.eaz / genCost).toInt() else 0
    val packUploads = if (uploadCost > 0) kotlin.math.floor(pack.eaz / uploadCost).toInt() else 0
    val ucStr = if (uploadCost % 1.0 == 0.0) uploadCost.toLong().toString() else EazCostCatalog.fmtEaz(uploadCost)
    val gensTpl = translationStore.t(
        "creator.settings.eaz_pkg_gens_tpl",
        "Up to {{count}} design generations ({{cost}} EAZ each)\nUp to {{upload_count}} uploads ({{upload_cost}} EAZ each)\nFree pool: up to {{free_uploads}} uploads ({{free_cap}} EAZ cap)"
    )
    val gensLine = gensTpl
        .replace("{{count}}", gens.toString())
        .replace("{{cost}}", EazCostCatalog.fmtEaz(genCost))
        .replace("{{upload_count}}", packUploads.toString())
        .replace("{{upload_cost}}", ucStr)
        .replace("{{free_cap}}", EazCostCatalog.fmtEaz(freeCap))
        .replace("{{free_uploads}}", freeUploads.toString())

    val priceTpl = translationStore.t("creator.settings.eaz_pkg_price_usd", "{{price}}")
    val priceStr = EazPackageCatalog.fmtUsd(pack.priceUsd)
    val per10Tpl = translationStore.t("creator.settings.eaz_pkg_per_10_tpl", "{{price}} per 10 EAZ")
    val per10 = EazPackageCatalog.per10Usd(pack)?.let { EazPackageCatalog.fmtUsd(it) }
    val per10Line = if (per10 != null) per10Tpl.replace("{{price}}", per10) else "—"

    val discountPct = if (index == 0) null else EazPackageCatalog.discountPctVsBaseline(pack, baseline)
    val discountLine = when {
        index == 0 -> translationStore.t(
            "creator.settings.eaz_pkg_baseline_comparison",
            "Compared to starter pack (reference tier)"
        )
        discountPct != null -> {
            val tpl = translationStore.t("creator.settings.eaz_pkg_discount", "{{pct}}% better value vs smallest pack")
            tpl.replace("{{pct}}", discountPct.toString())
        }
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (pack.recommended) EazColors.Orange.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.04f),
                RoundedCornerShape(12.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = pack.label,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            if (pack.recommended) {
                Text(
                    text = translationStore.t("creator.settings.eaz_pkg_recommended_badge", "Recommended"),
                    style = MaterialTheme.typography.labelSmall,
                    color = EazColors.Orange,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(EazColors.Orange.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Text(
            text = priceTpl.replace("{{price}}", priceStr),
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.9f),
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = per10Line,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.65f)
        )
        Text(
            text = gensLine,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f)
        )
        if (!discountLine.isNullOrBlank()) {
            Text(
                text = discountLine,
                style = MaterialTheme.typography.bodySmall,
                color = if (discountPct != null) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.5f)
            )
        }

        Button(
            onClick = onBuy,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = Color.White,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(shopCta, color = Color.White)
            }
        }
    }
}

@Composable
private fun EazStatRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.82f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = EazColors.Orange, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EazCostsSubPanel(
    balanceData: JSONObject?,
    isLoading: Boolean,
    translationStore: TranslationStore
) {
    Text(
        translationStore.t(
            "creator.settings.eaz_costs_intro",
            "Your effective EAZ price per feature (includes mascot discounts when active)."
        ),
        style = MaterialTheme.typography.bodySmall,
        color = Color.White.copy(alpha = 0.7f)
    )

    if (isLoading && balanceData == null) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(16.dp))
        }
        return
    }

    val freeLbl = translationStore.t("creator.settings.eaz_cost_free", "Free")
    val discountPct = EazCostCatalog.mascotDiscountPct(balanceData)

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EazCostCatalog.items.forEach { item ->
            val cost = EazCostCatalog.resolveCost(balanceData, item.feature)
            val baseCost = EazCostCatalog.resolveBaseCost(balanceData, item.feature)
            val active = EazCostCatalog.isFeatureActive(balanceData, item.feature)
            val isFree = !active || cost <= 0
            val hasDiscount = !isFree && discountPct > 0 && baseCost > cost + 1e-9
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    EazCostCatalog.label(item, translationStore),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.88f),
                    modifier = Modifier.weight(1f)
                )
                if (isFree) {
                    Text(
                        freeLbl,
                        style = MaterialTheme.typography.bodyMedium,
                        color = EazColors.Orange,
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (hasDiscount) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${EazCostCatalog.fmtEaz(baseCost)} EAZ",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.45f),
                            textDecoration = TextDecoration.LineThrough
                        )
                        Text(
                            "-${(discountPct * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4ADE80),
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${EazCostCatalog.fmtEaz(cost)} EAZ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EazColors.Orange,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        "${EazCostCatalog.fmtEaz(cost)} EAZ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = EazColors.Orange,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
