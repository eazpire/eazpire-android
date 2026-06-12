package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import androidx.compose.runtime.collectAsState
import com.eazpire.creator.billing.EazBalanceRefreshBus
import com.eazpire.creator.billing.EazCostCatalog
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private enum class EazSettingsSubTab { Balance, Logs, Buy, Costs }

@Composable
fun CreatorSettingsEazPanel(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier
) {
    val ownerId = remember { tokenStore.getOwnerId().orEmpty() }
    val jwt = remember { tokenStore.getJwt() }
    val api = remember(jwt) { CreatorApi(jwt = jwt) }
    var subTab by remember { mutableIntStateOf(0) }
    var balanceData by remember { mutableStateOf<JSONObject?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val balanceRefreshTick by EazBalanceRefreshBus.tick.collectAsState()

    val tabs = listOf(
        EazSettingsSubTab.Balance,
        EazSettingsSubTab.Logs,
        EazSettingsSubTab.Buy,
        EazSettingsSubTab.Costs
    )

    LaunchedEffect(ownerId, balanceRefreshTick) {
        if (ownerId.isBlank()) {
            isLoading = false
            return@LaunchedEffect
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
                        androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = EazColors.Orange.copy(alpha = 0.2f)
                        )
                    } else {
                        androidx.compose.material3.ButtonDefaults.outlinedButtonColors()
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
            EazSettingsSubTab.Balance -> EazBalanceSubPanel(balanceData, isLoading, translationStore)
            EazSettingsSubTab.Logs -> EazComingSoonSubPanel(translationStore)
            EazSettingsSubTab.Buy -> EazComingSoonSubPanel(translationStore)
            EazSettingsSubTab.Costs -> EazCostsSubPanel(balanceData, isLoading, translationStore)
        }
    }
}

@Composable
private fun EazComingSoonSubPanel(translationStore: TranslationStore) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            translationStore.t("creator.common.coming_soon", "Coming soon"),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Text(
            translationStore.t("creator.settings.eaz_coming_soon", "EAZ features are coming soon"),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun EazBalanceSubPanel(
    balanceData: JSONObject?,
    isLoading: Boolean,
    translationStore: TranslationStore
) {
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

    val total = data.optDouble("balance_total", data.optDouble("balance_eaz", 0.0))
    val free = data.optDouble("balance_free", 0.0)
    val purchased = data.optDouble("balance_purchased", 0.0)
    val freeCap = data.optDouble("free_cap", 50.0)

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
        Text(value, style = MaterialTheme.typography.bodyMedium, color = EazColors.Orange, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
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
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                } else if (hasDiscount) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${EazCostCatalog.fmtEaz(baseCost)} EAZ",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.45f),
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                        )
                        Text(
                            "-${(discountPct * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4ADE80),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Text(
                            "${EazCostCatalog.fmtEaz(cost)} EAZ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EazColors.Orange,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                } else {
                    Text(
                        "${EazCostCatalog.fmtEaz(cost)} EAZ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = EazColors.Orange,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
