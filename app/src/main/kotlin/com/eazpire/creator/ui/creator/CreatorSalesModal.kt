package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.RequestPage
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Sales Modal – fullscreen von unten, icons-only Sidebar (Overview, Earnings, Network, Payouts, Request) */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorSalesModal(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTab by remember { mutableIntStateOf(0) }
    var balanceText by remember { mutableStateOf("0.00") }
    var currencySymbol by remember { mutableStateOf("€") }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() ?: "" }
    val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val tabs = listOf(
        SalesTabItem(translationStore.t("creator.sales_modal.screen_overview", "Overview"), Icons.Default.MonetizationOn),
        SalesTabItem(translationStore.t("creator.sales_modal.screen_earnings", "Sales"), Icons.Default.ShoppingCart),
        SalesTabItem(translationStore.t("creator.sales_modal.screen_network", "Community"), Icons.Default.Groups),
        SalesTabItem(translationStore.t("creator.sales_modal.screen_payouts", "Payouts"), Icons.Default.Payments),
        SalesTabItem(translationStore.t("creator.sales_modal.screen_request", "Request payout"), Icons.Default.RequestPage)
    )

    LaunchedEffect(ownerId) {
        if (ownerId.isNotBlank()) {
            try {
                val payout = withContext(Dispatchers.IO) { api.getCreatorPayoutOverview(ownerId, 90) }
                if (payout.optBoolean("ok", false)) {
                    balanceText = "%.2f".format(payout.optDouble("availableAmount", 0.0))
                    currencySymbol = when (payout.optString("currency", "EUR").uppercase()) {
                        "USD" -> "$"
                        "GBP" -> "£"
                        else -> "€"
                    }
                }
            } catch (_: Exception) {}
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF070B14),
        modifier = modifier.fillMaxHeight(1f),
        dragHandle = null
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .width(56.dp)
                    .fillMaxHeight()
                    .background(Color(0xFF070B14))
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                tabs.forEachIndexed { i, tab ->
                    val isActive = i == currentTab
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        tint = if (isActive) EazColors.Orange else Color.White.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (isActive) EazColors.Orange.copy(alpha = 0.2f) else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { currentTab = i }
                            .padding(8.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF070B14))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tabs[currentTab].label,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFF0B1220))
                ) {
                when (currentTab) {
                    0 -> SlmOverviewScreen(
                        balanceText = balanceText,
                        currencySymbol = currencySymbol,
                        translationStore = translationStore
                    )
                    1 -> SlmEarningsScreen(translationStore = translationStore)
                    2 -> SlmNetworkScreen(translationStore = translationStore)
                    3 -> SlmPayoutsScreen(translationStore = translationStore)
                    4 -> SlmRequestScreen(
                        balanceText = balanceText,
                        currencySymbol = currencySymbol,
                        translationStore = translationStore
                    )
                }
                }
            }
        }
    }
}

private data class SalesTabItem(val label: String, val icon: ImageVector)

@Composable
private fun SlmOverviewScreen(
    balanceText: String,
    currencySymbol: String,
    translationStore: TranslationStore
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "$balanceText $currencySymbol",
            style = MaterialTheme.typography.headlineMedium,
            color = EazColors.Orange
        )
        Text(
            text = translationStore.t("creator.sales_modal.available", "Available"),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = translationStore.t("creator.sales_modal.breakdown_title", "Breakdown"),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(
            text = translationStore.t("creator.sales_modal.recent_activity", "Recent activity"),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = translationStore.t("creator.sales_modal.no_activity", "No activity yet"),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun SlmEarningsScreen(translationStore: TranslationStore) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = translationStore.t("creator.sales_modal.period", "Period"),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Text(
            text = translationStore.t("creator.sales_modal.last_90_days", "Last 90 days"),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = translationStore.t("creator.sales_modal.total_sales", "Total sales"),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(text = "–", color = EazColors.Orange, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SlmNetworkScreen(translationStore: TranslationStore) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = translationStore.t("creator.sales_modal.network_activity", "Network activity"),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Text(
            text = translationStore.t("creator.sales_modal.no_network_activity", "No network activity yet"),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun SlmPayoutsScreen(translationStore: TranslationStore) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = translationStore.t("creator.sales_modal.screen_payouts", "Payouts"),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White
        )
        Text(
            text = translationStore.t("creator.sales_modal.no_payouts", "No payouts yet"),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun SlmRequestScreen(
    balanceText: String,
    currencySymbol: String,
    translationStore: TranslationStore
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = translationStore.t("creator.sales_modal.available", "Available"),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.7f)
        )
        Text(
            text = "$balanceText $currencySymbol",
            style = MaterialTheme.typography.headlineMedium,
            color = EazColors.Orange,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = translationStore.t("creator.sales_modal.request_method_title", "Payout method"),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.padding(top = 24.dp)
        )
        Text(
            text = translationStore.t("creator.sales_modal.payout_shop_credit", "Shop credit"),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = translationStore.t("creator.sales_modal.cta_submit_request", "Submit request"),
            style = MaterialTheme.typography.labelLarge,
            color = EazColors.Orange,
            modifier = Modifier
                .padding(top = 24.dp)
                .background(EazColors.Orange.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                .padding(horizontal = 24.dp, vertical = 12.dp)
        )
    }
}
