package com.eazpire.creator.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.AuthConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

private enum class ArtifactsHubSection { Nfts, Outfit, Exchange, Marketplace }

private data class ArtifactSlotRow(val id: Int, val slotType: String, val serial: String)

@Composable
fun EazyArtifactsHubPanel(
    api: CreatorApi,
    ownerId: String?,
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit,
    t: (String, String) -> String,
) {
    var section by remember { mutableStateOf(ArtifactsHubSection.Nfts) }
    var loading by remember { mutableStateOf(false) }
    var slots by remember { mutableStateOf<List<ArtifactSlotRow>>(emptyList()) }
    var marketCount by remember { mutableStateOf(0) }
    val palette = LocalEazyModalPalette.current
    val shop = AuthConfig.SHOP_DOMAIN

    LaunchedEffect(section, ownerId, isLoggedIn) {
        if (!isLoggedIn || ownerId.isNullOrBlank()) return@LaunchedEffect
        loading = true
        try {
            when (section) {
                ArtifactsHubSection.Nfts, ArtifactsHubSection.Outfit -> {
                    val res = withContext(Dispatchers.IO) { api.getArtifactsInventoryList(ownerId, shop) }
                    val arr = res.optJSONArray("slots") ?: JSONArray()
                    slots = (0 until arr.length()).mapNotNull { i ->
                        val o = arr.optJSONObject(i) ?: return@mapNotNull null
                        ArtifactSlotRow(
                            id = o.optInt("id"),
                            slotType = o.optString("slot_type", ""),
                            serial = o.optString("serial", ""),
                        )
                    }
                }
                ArtifactsHubSection.Marketplace -> {
                    val res = withContext(Dispatchers.IO) { api.getArtifactsMarketList(shop) }
                    marketCount = res.optJSONArray("listings")?.length() ?: 0
                }
                ArtifactsHubSection.Exchange -> { /* listings via web parity later */ }
            }
        } catch (_: Exception) {
        } finally {
            loading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, palette.border)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                ArtifactsHubSection.Nfts to (Icons.Default.Collections to t("eazy_chat.artifacts_section_nfts", "NFTs")),
                ArtifactsHubSection.Outfit to (Icons.Default.Checkroom to t("eazy_chat.artifacts_section_outfit", "Outfit")),
                ArtifactsHubSection.Exchange to (Icons.Default.SwapHoriz to t("eazy_chat.artifacts_section_exchange", "Exchange")),
                ArtifactsHubSection.Marketplace to (Icons.Default.ShoppingBag to t("eazy_chat.artifacts_section_marketplace", "Marketplace")),
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
                ) {
                    Icon(iconLabel.first, null, tint = palette.accent, modifier = Modifier.size(16.dp))
                    Text(
                        iconLabel.second,
                        fontSize = 8.sp,
                        lineHeight = 9.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (active) palette.text else palette.muted,
                    )
                }
                if (sec != ArtifactsHubSection.Marketplace) Spacer(modifier = Modifier.width(4.dp))
            }
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

        if (loading) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = palette.accent)
            }
            return@Column
        }

        when (section) {
            ArtifactsHubSection.Nfts -> {
                if (slots.isEmpty()) {
                    Text(
                        t("eazy_chat.artifacts_collection_empty", "No slot NFTs yet."),
                        modifier = Modifier.padding(24.dp),
                        color = palette.muted,
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(100.dp),
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(slots) { slot ->
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                                    .padding(8.dp),
                            ) {
                                Text(slot.slotType, style = MaterialTheme.typography.labelSmall, color = palette.text)
                                Text(slot.serial, style = MaterialTheme.typography.bodySmall, color = palette.muted, maxLines = 1)
                            }
                        }
                    }
                }
            }
            ArtifactsHubSection.Outfit ->
                Text(
                    t("eazy_chat.artifacts_section_outfit", "Outfit") + " — ${slots.size} items",
                    modifier = Modifier.padding(16.dp),
                    color = palette.muted,
                )
            ArtifactsHubSection.Exchange ->
                Text(
                    t("eazy_chat.artifacts_exchange_empty", "No exchange listings yet."),
                    modifier = Modifier.padding(16.dp),
                    color = palette.muted,
                )
            ArtifactsHubSection.Marketplace ->
                Text(
                    if (marketCount == 0) t("eazy_chat.artifacts_market_empty", "No characters for sale.")
                    else "$marketCount listings",
                    modifier = Modifier.padding(16.dp),
                    color = palette.muted,
                )
        }
    }
}
