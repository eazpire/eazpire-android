package com.eazpire.creator.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.util.DebugLog

/**
 * Community Tab – native Android UI.
 * Web reference: theme/sections/community-panel.liquid
 * Displays network stats and level-1 partners from list-community-network.
 */
data class CommunityNetwork(
    val me: MeStats,
    val stats: NetworkStats,
    val level1: List<PartnerItem>
)

data class MeStats(
    val name: String,
    val designs: Int,
    val products: Int,
    val sales: Int,
    val profit: String
)

data class NetworkStats(
    val partners: Int,
    val designs: Int,
    val products: Int,
    val sales: Int,
    val profit: String
)

data class PartnerItem(
    val id: String,
    val name: String,
    val country: String,
    val since: String,
    val designs: Int,
    val products: Int,
    val sales: Int
)

@Composable
fun AccountCommunityTab(
    tokenStore: SecureTokenStore,
    modifier: Modifier = Modifier
) {
    val jwt = remember { tokenStore.getJwt() }
    val ownerId = remember { tokenStore.getOwnerId() ?: "" }
    val api = remember(jwt) { com.eazpire.creator.api.CreatorApi(jwt = jwt) }

    var network by remember { mutableStateOf<CommunityNetwork?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) return@LaunchedEffect
        isLoading = true
        errorMessage = null
        try {
            val resp = api.listCommunityNetwork(ownerId)
            if (resp.optBoolean("ok", false)) {
                val net = resp.optJSONObject("network")
                network = net?.let { parseNetwork(it) }
            } else {
                errorMessage = resp.optString("error", "Failed to load community")
            }
        } catch (e: Exception) {
            DebugLog.click("Community load error: ${e.message}")
            errorMessage = "Failed to load community"
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Community",
            style = MaterialTheme.typography.titleMedium,
            color = EazColors.TextPrimary
        )
        Text(
            text = "Your network and community stats.",
            style = MaterialTheme.typography.bodySmall,
            color = EazColors.TextSecondary
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(24.dp))
            }
        } else {
            errorMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            network?.let { net ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = EazColors.OrangeBg.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "You (${net.me.name})",
                            style = MaterialTheme.typography.titleSmall,
                            color = EazColors.TextPrimary
                        )
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            statItem("Designs", net.me.designs.toString())
                            statItem("Products", net.me.products.toString())
                            statItem("Sales", net.me.sales.toString())
                            if (net.me.profit.isNotBlank()) statItem("Profit", net.me.profit)
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Network",
                            style = MaterialTheme.typography.titleSmall,
                            color = EazColors.TextPrimary
                        )
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            statItem("Partners", net.stats.partners.toString())
                            statItem("Designs", net.stats.designs.toString())
                            statItem("Products", net.stats.products.toString())
                            statItem("Sales", net.stats.sales.toString())
                        }
                    }
                }

                if (net.level1.isNotEmpty()) {
                    Text(
                        text = "Your Partners",
                        style = MaterialTheme.typography.labelLarge,
                        color = EazColors.TextSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    net.level1.forEach { partner ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(8.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = partner.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = EazColors.TextPrimary
                                    )
                                    Text(
                                        text = "${partner.country} • since ${partner.since}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = EazColors.TextSecondary
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = "D:${partner.designs}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EazColors.TextSecondary
                                    )
                                    Text(
                                        text = "P:${partner.products}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EazColors.TextSecondary
                                    )
                                    Text(
                                        text = "S:${partner.sales}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EazColors.TextSecondary
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp)
                            .background(EazColors.TopbarBorder.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Groups,
                                contentDescription = null,
                                modifier = Modifier.padding(bottom = 8.dp),
                                tint = EazColors.TextSecondary
                            )
                            Text(
                                text = "No partners yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = EazColors.TextSecondary
                            )
                            Text(
                                text = "Invite creators to grow your network",
                                style = MaterialTheme.typography.bodySmall,
                                color = EazColors.TextSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun statItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = EazColors.Orange
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = EazColors.TextSecondary
        )
    }
}

private fun parseNetwork(obj: org.json.JSONObject): CommunityNetwork? {
    val meObj = obj.optJSONObject("me") ?: return null
    val statsObj = obj.optJSONObject("stats") ?: return null
    val me = MeStats(
        name = meObj.optString("name", "You"),
        designs = meObj.optInt("designs", 0),
        products = meObj.optInt("products", 0),
        sales = meObj.optInt("sales", 0),
        profit = meObj.optString("profit", "")
    )
    val stats = NetworkStats(
        partners = statsObj.optInt("partners", 0),
        designs = statsObj.optInt("designs", 0),
        products = statsObj.optInt("products", 0),
        sales = statsObj.optInt("sales", 0),
        profit = statsObj.optString("profit", "")
    )
    val level1Arr = obj.optJSONArray("level1")
    val level1 = (0 until (level1Arr?.length() ?: 0)).mapNotNull { i ->
        val p = level1Arr?.optJSONObject(i) ?: return@mapNotNull null
        PartnerItem(
            id = p.optString("id", ""),
            name = p.optString("name", "–"),
            country = p.optString("country", ""),
            since = p.optString("since", ""),
            designs = p.optInt("designs", 0),
            products = p.optInt("products", 0),
            sales = p.optInt("sales", 0)
        )
    }
    return CommunityNetwork(me, stats, level1)
}
