package com.eazpire.creator.ui.account

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.util.DebugLog
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

/**
 * Balance & Payouts – matches web (theme/sections/account-balance-payouts.liquid).
 * No page title in content (header has it).
 * Subtabs: Overview, Payout Settings, Community, Payout History, Request Payout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountBalanceTab(
    tokenStore: com.eazpire.creator.auth.SecureTokenStore,
    modifier: Modifier = Modifier
) {
    val jwt = remember { tokenStore.getJwt() }
    val ownerId = remember { tokenStore.getOwnerId() ?: "" }
    val api = remember(jwt) { com.eazpire.creator.api.CreatorApi(jwt = jwt) }

    var activeSubtab by remember { mutableStateOf("overview") }
    val subtabs = listOf("overview", "payout-settings", "community", "payout-history", "request-payout")
    val subtabLabels = mapOf(
        "overview" to "Overview",
        "payout-settings" to "Payout Settings",
        "community" to "Community",
        "payout-history" to "Payout History",
        "request-payout" to "Request Payout"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            subtabs.forEach { tab ->
                OutlinedButton(
                    onClick = { activeSubtab = tab },
                    modifier = Modifier.padding(0.dp),
                    colors = if (activeSubtab == tab) {
                        androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = EazColors.OrangeBg.copy(alpha = 0.5f)
                        )
                    } else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                ) {
                    Text(
                        subtabLabels[tab] ?: tab,
                        color = if (activeSubtab == tab) EazColors.Orange else EazColors.TextSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        when (activeSubtab) {
            "overview" -> BalanceOverviewPanel(ownerId = ownerId, api = api)
            "payout-settings" -> PayoutSettingsPanel(ownerId = ownerId, api = api)
            "community" -> BalanceCommunityPanel(ownerId = ownerId, api = api)
            "payout-history" -> PayoutHistoryPanel(ownerId = ownerId, api = api)
            "request-payout" -> RequestPayoutPanel(ownerId = ownerId, api = api)
        }
    }
}

@Composable
private fun BalanceOverviewPanel(
    ownerId: String,
    api: com.eazpire.creator.api.CreatorApi
) {
    var days by remember { mutableStateOf(30) }
    var overview by remember { mutableStateOf<JSONObject?>(null) }
    var shopCredits by remember { mutableStateOf<JSONObject?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var refreshTrigger by remember { mutableStateOf(0) }

    LaunchedEffect(ownerId, days, refreshTrigger) {
        if (ownerId.isBlank()) return@LaunchedEffect
        isLoading = true
        try {
            coroutineScope {
                val ov = async { api.getCreatorPayoutOverview(ownerId, days, "community_only") }
                val sc = async { api.getShopCreditsSummary(ownerId) }
                val o = ov.await()
                val s = sc.await()
                if (o.optBoolean("ok", false)) overview = o
                if (s.optBoolean("ok", false)) shopCredits = s
            }
        } catch (e: Exception) {
            message = "Failed to load"
        } finally {
            isLoading = false
        }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(7, 30, 90, 0).forEach { d ->
            val label = if (d == 0) "All" else "${d}d"
            OutlinedButton(
                onClick = { days = d },
                modifier = Modifier.weight(1f),
                colors = if (days == d) {
                    androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = EazColors.OrangeBg.copy(alpha = 0.5f))
                } else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
            ) {
                Text(label, color = if (days == d) EazColors.Orange else EazColors.TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
        }
    }

    if (isLoading && overview == null) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(24.dp))
        }
    } else {
        message?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
        overview?.let { ov ->
            val currency = ov.optString("currency", "EUR")
            val available = ov.optDouble("availableAmount", 0.0)
            val network = ov.optDouble("networkAmount", 0.0)
            val paidOut = ov.optDouble("payoutsTotal", 0.0)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BalanceKpiCard("Available", fmtMoney(available, currency), Modifier.weight(1f))
                BalanceKpiCard("Community Sales", fmtMoney(network, currency), Modifier.weight(1f))
                BalanceKpiCard("Paid Out", fmtMoney(paidOut, currency), Modifier.weight(1f))
            }
        }
        shopCredits?.let { sc ->
            if (sc.optBoolean("ok", false)) {
                val total = sc.optDouble("total_amount", 0.0)
                val currency = sc.optString("currency", "EUR")
                val src = sc.optJSONObject("sources") ?: JSONObject()
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Shop Credits", style = androidx.compose.material3.MaterialTheme.typography.titleSmall, color = EazColors.TextPrimary)
                        Text("Total: ${fmtMoney(total, currency)}", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = EazColors.Orange, modifier = Modifier.padding(top = 4.dp))
                        listOf("store_credit", "gift_cards", "coupons_received", "community").forEach { key ->
                            val obj = src.optJSONObject(key)
                            val amt = obj?.optDouble("amount") ?: 0.0
                            if (amt > 0) {
                                val label = when (key) {
                                    "store_credit" -> "Store Credit"
                                    "gift_cards" -> "Gift Cards"
                                    "coupons_received" -> "Coupons"
                                    "community" -> "Community"
                                    else -> key
                                }
                                Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(label, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = EazColors.TextSecondary)
                                    Text(fmtMoney(amt, currency), style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = EazColors.TextPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
        overview?.let { ov ->
            val activity = ov.optJSONArray("activity") ?: org.json.JSONArray()
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent Activity", style = androidx.compose.material3.MaterialTheme.typography.titleSmall, color = EazColors.TextPrimary)
                        TextButton(onClick = { refreshTrigger++ }) { Text("Refresh", color = EazColors.Orange) }
                    }
                    if (activity.length() == 0) {
                        Text("No activity yet.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = EazColors.TextSecondary)
                    } else {
                        (0 until activity.length().coerceAtMost(12)).forEach { i ->
                            val it = activity.optJSONObject(i) ?: return@forEach
                            val type = it.optString("type", "")
                            val title = when (type) {
                                "payout" -> "Payout (${it.optString("payoutType", "payout")})"
                                "network" -> "Community L${it.optInt("networkLevel", 1)}"
                                else -> "Community"
                            }
                            val amt = it.optDouble("amount", 0.0)
                            val curr = it.optString("currency", ov.optString("currency", "EUR"))
                            val amtStr = if (type == "payout") "-${fmtMoney(kotlin.math.abs(amt), curr)}" else "+${fmtMoney(amt, curr)}"
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text(title, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = EazColors.TextPrimary)
                                    Text(it.optString("date", ""), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = EazColors.TextSecondary)
                                }
                                Text(amtStr, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = if (type == "payout") EazColors.TextSecondary else EazColors.Orange)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BalanceKpiCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = EazColors.TextSecondary)
            Text(value, style = androidx.compose.material3.MaterialTheme.typography.titleSmall, color = EazColors.Orange)
        }
    }
}

@Composable
private fun PayoutSettingsPanel(ownerId: String, api: com.eazpire.creator.api.CreatorApi) {
    var methods by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var autoEnabled by remember { mutableStateOf(false) }
    var autoMethod by remember { mutableStateOf("shop_credit") }
    var autoMinCents by remember { mutableStateOf(5000) }
    var autoDetailId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) return@LaunchedEffect
        isLoading = true
        try {
            val r = api.getCreatorPayoutDetails(ownerId)
            if (r.optBoolean("ok", false)) {
                val arr = r.optJSONArray("payoutMethods") ?: org.json.JSONArray()
                methods = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                autoEnabled = r.optBoolean("autoPayoutEnabled", false)
                autoMethod = r.optString("autoPayoutMethod", "shop_credit")
                autoMinCents = r.optInt("autoPayoutMinCents", 5000)
                autoDetailId = r.optString("autoPayoutDetailId", "").ifBlank { null }
            }
        } catch (e: Exception) {
            message = "Failed to load"
        } finally {
            isLoading = false
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(24.dp))
        }
    } else {
        Text("Payout methods and auto-payout. Add methods below.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = EazColors.TextSecondary)
        if (methods.isEmpty()) {
            Text("No payout methods yet.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = EazColors.TextSecondary)
        } else {
            methods.forEach { m ->
                val methodLabel = when (m.optString("method", "")) {
                    "wise" -> "Wise"
                    "paypal" -> "PayPal"
                    else -> m.optString("method", "")
                }
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("${m.optString("label", "Method")} · $methodLabel", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = EazColors.TextPrimary)
                            Text(m.optString("ibanMasked", m.optString("paypalEmailMasked", "")), style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = EazColors.TextSecondary)
                        }
                        TextButton(onClick = { /* delete - would need API call */ }) { Text("Remove", color = EazColors.Orange) }
                    }
                }
            }
        }
        Text("Auto-payout: Configure in web for full setup.", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = EazColors.TextSecondary, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun BalanceCommunityPanel(ownerId: String, api: com.eazpire.creator.api.CreatorApi) {
    var communityDays by remember { mutableStateOf(30) }
    var overview by remember { mutableStateOf<JSONObject?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(ownerId, communityDays) {
        if (ownerId.isBlank()) return@LaunchedEffect
        isLoading = true
        try {
            val r = api.getCreatorPayoutOverview(ownerId, communityDays, "community_only")
            if (r.optBoolean("ok", false)) overview = r
        } catch (_: Exception) {}
        finally { isLoading = false }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0, 7, 30, 90, 365).forEach { d ->
            val label = when (d) {
                0 -> "All"
                365 -> "1y"
                else -> "${d}d"
            }
            OutlinedButton(
                onClick = { communityDays = d },
                modifier = Modifier.weight(1f),
                colors = if (communityDays == d) {
                    androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = EazColors.OrangeBg.copy(alpha = 0.5f))
                } else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
            ) {
                Text(label, color = if (communityDays == d) EazColors.Orange else EazColors.TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
        }
    }

    if (isLoading && overview == null) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(24.dp))
        }
    } else {
        overview?.let { ov ->
            val count = ov.optInt("networkCount", 0)
            val profit = ov.optDouble("networkAmount", 0.0)
            val currency = ov.optString("currency", "EUR")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BalanceKpiCard("Sales Count", count.toString(), Modifier.weight(1f))
                BalanceKpiCard("Profit", fmtMoney(profit, currency), Modifier.weight(1f))
            }
            val activity = ov.optJSONArray("activity") ?: org.json.JSONArray()
            val networkActivity = (0 until activity.length()).mapNotNull { i ->
                val it = activity.optJSONObject(i)
                if (it?.optString("type") == "network") it else null
            }
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Network Activity", style = androidx.compose.material3.MaterialTheme.typography.titleSmall, color = EazColors.TextPrimary)
                    if (networkActivity.isEmpty()) {
                        Text("No network activity yet.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = EazColors.TextSecondary)
                    } else {
                        networkActivity.take(20).forEach { it ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(it.optString("productTitle", "#${it.optString("orderId", "—")}"), style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = EazColors.TextPrimary)
                                    Text(it.optString("date", ""), style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = EazColors.TextSecondary)
                                }
                                Text("+${fmtMoney(it.optDouble("amount", 0.0), currency)}", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = EazColors.Orange)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PayoutHistoryPanel(ownerId: String, api: com.eazpire.creator.api.CreatorApi) {
    var payouts by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var currency by remember { mutableStateOf("EUR") }

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) return@LaunchedEffect
        isLoading = true
        try {
            val r = api.getCreatorPayoutOverview(ownerId, 0, "community_only")
            if (r.optBoolean("ok", false)) {
                currency = r.optString("currency", "EUR")
                val arr = r.optJSONArray("payouts") ?: org.json.JSONArray()
                payouts = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            }
        } catch (_: Exception) {}
        finally { isLoading = false }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(24.dp))
        }
    } else {
        if (payouts.isEmpty()) {
            Text("No payouts yet.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = EazColors.TextSecondary)
        } else {
            payouts.forEach { p ->
                val amt = p.optDouble("amount", 0.0)
                val curr = p.optString("payoutCurrency", currency)
                val type = p.optString("payoutType", "payout").replace("_", " ")
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(type, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = EazColors.TextPrimary)
                            Text(p.optString("date", ""), style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = EazColors.TextSecondary)
                        }
                        Text(fmtMoney(amt, curr), style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = EazColors.Orange)
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestPayoutPanel(ownerId: String, api: com.eazpire.creator.api.CreatorApi) {
    var overview by remember { mutableStateOf<JSONObject?>(null) }
    var methods by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var selectedMethod by remember { mutableStateOf("shop_credit") }
    var amountStr by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) return@LaunchedEffect
        isLoading = true
        try {
            val ov = api.getCreatorPayoutOverview(ownerId, 30, "community_only")
            val det = api.getCreatorPayoutDetails(ownerId)
            if (ov.optBoolean("ok", false)) overview = ov
            if (det.optBoolean("ok", false)) {
                val arr = det.optJSONArray("payoutMethods") ?: org.json.JSONArray()
                methods = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            }
        } catch (_: Exception) {}
        finally { isLoading = false }
    }

    if (isLoading && overview == null) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(24.dp))
        }
    } else {
        val available = overview?.optDouble("availableAmount", 0.0) ?: 0.0
        val currency = overview?.optString("currency", "EUR") ?: "EUR"
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = EazColors.OrangeBg.copy(alpha = 0.3f)), shape = RoundedCornerShape(12.dp)) {
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Available", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = EazColors.TextSecondary)
                Text(fmtMoney(available, currency), style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = EazColors.Orange)
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { selectedMethod = "shop_credit" },
                modifier = Modifier.weight(1f),
                colors = if (selectedMethod == "shop_credit") {
                    androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = EazColors.OrangeBg.copy(alpha = 0.5f))
                } else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
            ) { Text("Shop Credit", color = if (selectedMethod == "shop_credit") EazColors.Orange else EazColors.TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall) }
            methods.filter { it.optString("method") in listOf("wise", "paypal") }.forEach { m ->
                val value = "${m.optString("method")}:${m.optString("id")}"
                OutlinedButton(
                    onClick = { selectedMethod = value },
                    modifier = Modifier.weight(1f),
                    colors = if (selectedMethod == value) {
                        androidx.compose.material3.ButtonDefaults.outlinedButtonColors(containerColor = EazColors.OrangeBg.copy(alpha = 0.5f))
                    } else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                ) {
                    Text(if (m.optString("method") == "wise") "Wise" else "PayPal", color = if (selectedMethod == value) EazColors.Orange else EazColors.TextSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                }
            }
        }
        message?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
        OutlinedTextField(
            value = amountStr,
            onValueChange = { amountStr = it },
            label = { Text("Amount") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
        OutlinedButton(
            onClick = {
                val amt = amountStr.toDoubleOrNull() ?: 0.0
                if (amt < 5) {
                    message = "Minimum 5"
                    return@OutlinedButton
                }
                isSubmitting = true
                message = null
                scope.launch {
                    try {
                        val amountCents = (amt * 100).toInt()
                        val body = mutableMapOf<String, Any?>(
                            "ownerId" to ownerId,
                            "amountCents" to amountCents,
                            "requestedCurrency" to currency,
                            "scope" to "community_only",
                            "payoutScope" to "community_only"
                        )
                        if (selectedMethod == "shop_credit") {
                            body["method"] = "shop_credit"
                            val r = api.convertToShopCredit(body)
                            if (r.optBoolean("ok", false)) {
                                message = "Request submitted."
                                amountStr = ""
                            } else message = r.optString("error", "Request failed")
                        } else {
                            val parts = selectedMethod.split(":")
                            val method = parts.getOrNull(0) ?: "wise"
                            body["payoutDetailId"] = parts.getOrNull(1)
                            val r = if (method == "paypal") api.requestPayPalPayout(body) else api.requestWisePayout(body)
                            if (r.optBoolean("ok", false)) {
                                message = "Request submitted."
                                amountStr = ""
                            } else message = r.optString("error", "Request failed")
                        }
                    } catch (e: Exception) {
                        message = "Request failed"
                    } finally {
                        isSubmitting = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSubmitting
        ) {
            Text("Submit")
        }
    }
}

private fun fmtMoney(amount: Double, currency: String): String {
    return try {
        NumberFormat.getCurrencyInstance(Locale.GERMANY).format(amount)
    } catch (_: Exception) {
        "%.2f $currency".format(amount)
    }
}
