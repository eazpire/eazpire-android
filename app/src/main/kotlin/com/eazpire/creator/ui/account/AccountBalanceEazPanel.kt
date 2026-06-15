package com.eazpire.creator.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.billing.EazCostCatalog
import com.eazpire.creator.billing.EazEarnedConvertBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class EazLogFilter(val key: String) {
    All("all"),
    Earned("earned"),
    Conversions("conversions"),
}

@Composable
fun AccountBalanceEazPanel(
    ownerId: String,
    api: CreatorApi,
    t: (String, String) -> String,
    modifier: Modifier = Modifier,
) {
    var innerTab by remember { mutableStateOf("balance") }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("balance" to "content.account_balance_eaz_balance_sub", "logs" to "content.account_balance_eaz_logs").forEach { (key, labelKey) ->
                val label = when (key) {
                    "balance" -> t("content.account_balance_eaz_balance_sub", "Balance")
                    else -> t("content.account_balance_eaz_logs", "Logs")
                }
                OutlinedButton(
                    onClick = { innerTab = key },
                    colors = if (innerTab == key) {
                        androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = EazColors.OrangeBg.copy(alpha = 0.5f)
                        )
                    } else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
                ) {
                    Text(
                        label,
                        color = if (innerTab == key) EazColors.Orange else EazColors.TextSecondary,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

        when (innerTab) {
            "balance" -> AccountEazBalanceSubPanel(ownerId, api, t)
            "logs" -> AccountEazLogsSubPanel(ownerId, api, t)
        }
    }
}

@Composable
private fun AccountEazBalanceSubPanel(
    ownerId: String,
    api: CreatorApi,
    t: (String, String) -> String,
) {
    var balance by remember { mutableStateOf<JSONObject?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }

    LaunchedEffect(ownerId, refresh) {
        if (ownerId.isBlank()) return@LaunchedEffect
        isLoading = true
        try {
            val r = withContext(Dispatchers.IO) { api.getEarnedBalance(ownerId) }
            if (r.optBoolean("ok", false)) balance = r
        } catch (_: Exception) {
            balance = null
        } finally {
            isLoading = false
        }
    }

    if (isLoading && balance == null) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(24.dp))
        }
        return
    }

    val data = balance ?: return
    val total = data.optDouble("balance_earned_total", 0.0)
    val avail = data.optDouble("balance_earned_available", 0.0)
    val locked = data.optDouble("balance_earned_locked", 0.0)
    val rate = data.optDouble("eaz_cents_per_eaz", 0.0)
    val minConvert = data.optDouble("min_convert_eaz", 50.0)
    val lockedEntries = data.optJSONArray("locked_entries") ?: JSONArray()

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        BalanceKpiCard(
            t("content.account_balance_eaz_total", "Total earned EAZ"),
            "${EazCostCatalog.fmtEaz(total)} EAZ",
            Modifier.weight(1f)
        )
        BalanceKpiCard(
            t("content.account_balance_eaz_available", "Available EAZ"),
            "${EazCostCatalog.fmtEaz(avail)} EAZ",
            Modifier.weight(1f)
        )
        BalanceKpiCard(
            t("content.account_balance_eaz_locked", "Locked EAZ"),
            "${EazCostCatalog.fmtEaz(locked)} EAZ",
            Modifier.weight(1f)
        )
    }

    if (rate > 0) {
        Text(
            "1 EAZ ≈ ${String.format(Locale.US, "%.2f", rate / 100.0)} (pack rate)",
            style = MaterialTheme.typography.labelSmall,
            color = EazColors.TextSecondary
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                t("content.account_balance_eaz_locked", "Locked EAZ"),
                style = MaterialTheme.typography.titleSmall,
                color = EazColors.TextPrimary
            )
            if (lockedEntries.length() == 0) {
                Text("—", style = MaterialTheme.typography.bodySmall, color = EazColors.TextSecondary)
            } else {
                for (i in 0 until lockedEntries.length()) {
                    val e = lockedEntries.optJSONObject(i) ?: continue
                    val amt = EazCostCatalog.fmtEaz(e.optDouble("amount_eaz", 0.0))
                    val unlockAt = e.optLong("unlock_at", 0L)
                    val dateStr = if (unlockAt > 0) formatUnlockDate(unlockAt) else "—"
                    val lbl = t("content.account_balance_eaz_locked_until", "Unlocks {{date}}")
                        .replace("{{date}}", dateStr)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("$amt EAZ", style = MaterialTheme.typography.bodySmall, color = EazColors.TextPrimary)
                        Text(lbl, style = MaterialTheme.typography.labelSmall, color = EazColors.TextSecondary)
                    }
                }
            }
        }
    }

    Text(
        t(
            "content.account_balance_eaz_convert_hint",
            "Converted fiat is added to your available balance. Use Request payout to withdraw."
        ),
        style = MaterialTheme.typography.bodySmall,
        color = EazColors.TextSecondary
    )

    if (avail > 0) {
        EazEarnedConvertBlock(
            ownerId = ownerId,
            api = api,
            earnedAvailable = avail,
            eazCentsPerEaz = rate,
            minConvertEaz = minConvert,
            translate = t,
            onConverted = { refresh++ }
        )
    }
}

@Composable
private fun AccountEazLogsSubPanel(
    ownerId: String,
    api: CreatorApi,
    t: (String, String) -> String,
) {
    var filter by remember { mutableStateOf(EazLogFilter.All) }
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(ownerId, filter) {
        if (ownerId.isBlank()) return@LaunchedEffect
        isLoading = true
        try {
            val r = withContext(Dispatchers.IO) {
                api.getEarnedTransactions(ownerId, limit = 100, filter = filter.key)
            }
            if (r.optBoolean("ok", false)) {
                val arr = r.optJSONArray("transactions") ?: JSONArray()
                rows = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            } else {
                rows = emptyList()
            }
        } catch (_: Exception) {
            rows = emptyList()
        } finally {
            isLoading = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EazLogFilter.entries.forEach { f ->
            val label = when (f) {
                EazLogFilter.All -> "All"
                EazLogFilter.Earned -> t("content.account_balance_eaz_log_filter_earned", "Earned")
                EazLogFilter.Conversions -> t("content.account_balance_eaz_log_filter_conversions", "Conversions")
            }
            OutlinedButton(
                onClick = { filter = f },
                colors = if (filter == f) {
                    androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        containerColor = EazColors.OrangeBg.copy(alpha = 0.5f)
                    )
                } else androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
            ) {
                Text(
                    label,
                    color = if (filter == f) EazColors.Orange else EazColors.TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(24.dp))
        }
    } else if (rows.isEmpty()) {
        Text("No entries yet.", style = MaterialTheme.typography.bodySmall, color = EazColors.TextSecondary)
    } else {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            rows.forEach { row ->
                val typ = row.optString("type", "")
                val amt = row.optDouble("amount_eaz", 0.0)
                val sign = if (typ == "debit") "−" else "+"
                val status = row.optString("status", "")
                val unlockAt = row.optLong("unlock_at", 0L)
                val reason = row.optString("reason", "")
                val created = row.optLong("created_at", 0L)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(reason.ifBlank { typ }, style = MaterialTheme.typography.bodySmall, color = EazColors.TextPrimary)
                            Text(
                                "$sign${EazCostCatalog.fmtEaz(amt)} EAZ",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (typ == "debit") EazColors.TextSecondary else EazColors.Orange
                            )
                        }
                        Text(
                            formatUnlockDate(created),
                            style = MaterialTheme.typography.labelSmall,
                            color = EazColors.TextSecondary
                        )
                        if (status == "locked" && unlockAt > 0) {
                            Text(
                                t("content.account_balance_eaz_locked_until", "Unlocks {{date}}")
                                    .replace("{{date}}", formatUnlockDate(unlockAt)),
                                style = MaterialTheme.typography.labelSmall,
                                color = EazColors.Orange
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountEazOverviewTeaser(
    ownerId: String,
    api: CreatorApi,
    t: (String, String) -> String,
    onOpenEazTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var teaser by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) return@LaunchedEffect
        try {
            val r = withContext(Dispatchers.IO) { api.getEarnedBalance(ownerId) }
            if (!r.optBoolean("ok", false)) return@LaunchedEffect
            val total = r.optDouble("balance_earned_total", 0.0)
            if (total <= 0) return@LaunchedEffect
            val avail = EazCostCatalog.fmtEaz(r.optDouble("balance_earned_available", 0.0))
            val totalStr = EazCostCatalog.fmtEaz(total)
            teaser = t(
                "content.account_balance_eaz_overview_teaser",
                "Earned EAZ: {{total}} ({{available}} available) — open EAZ tab"
            )
                .replace("{{total}}", totalStr)
                .replace("{{available}}", avail)
        } catch (_: Exception) {
            teaser = null
        }
    }

    teaser?.let { text ->
        Text(
            text = text,
            modifier = modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenEazTab)
                .padding(vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = EazColors.Orange,
            textDecoration = TextDecoration.Underline
        )
    }
}

private fun formatUnlockDate(ms: Long): String {
    if (ms <= 0) return "—"
    return try {
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(ms))
    } catch (_: Exception) {
        "—"
    }
}
