package com.eazpire.creator.chat

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.json.JSONObject
import java.net.URLEncoder

data class PrizeInventoryItem(
    val id: Int,
    val type: String,
    val name: String,
    val category: String,
    val rarity: String,
    val slug: String,
    val fulfillmentMode: String?,
    val description: String?,
    val artworkR2Key: String?,
    val priceArtworkR2Key: String?,
    val metadata: JSONObject?,
    val cardDefinitionId: Int? = null,
    val fusionCount: Int = 4,
    val ownedCount: Int = 1,
    val fusionReady: Boolean = false,
    val instanceIds: List<Int> = emptyList(),
)

fun parseInventoryItems(arr: org.json.JSONArray): List<PrizeInventoryItem> =
    (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val instanceIdsArr = o.optJSONArray("instance_ids")
        val instanceIds = buildList {
            if (instanceIdsArr != null) {
                for (j in 0 until instanceIdsArr.length()) {
                    add(instanceIdsArr.optInt(j))
                }
            }
        }
        PrizeInventoryItem(
            id = o.optInt("id"),
            type = o.optString("type", "prize"),
            name = o.optString("name", o.optString("slug", "Item")),
            category = o.optString("category", ""),
            rarity = o.optString("rarity", "common"),
            slug = o.optString("slug", ""),
            fulfillmentMode = o.optString("fulfillment_mode", "").takeIf { it.isNotBlank() },
            description = o.optString("description", "").takeIf { it.isNotBlank() },
            artworkR2Key = o.optString("artwork_r2_key", "").takeIf { it.isNotBlank() },
            priceArtworkR2Key = o.optString("price_artwork_r2_key", "").takeIf { it.isNotBlank() },
            metadata = o.optJSONObject("metadata"),
            cardDefinitionId = o.optInt("card_definition_id").takeIf { it > 0 },
            fusionCount = o.optInt("fusion_count", 4).coerceAtLeast(1),
            ownedCount = o.optInt("owned_count", 1).coerceAtLeast(1),
            fusionReady = o.optBoolean("fusion_ready", false),
            instanceIds = instanceIds,
        )
    }

fun prizeArtworkUrl(apiBase: String, key: String?): String? {
    if (key.isNullOrBlank()) return null
    if (key.startsWith("http://", true) || key.startsWith("https://", true)) return key
    val base = apiBase.trimEnd('/')
    val path = key.trimStart('/')
    val encoded = path.split("/").joinToString("/") { segment ->
        URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
    }
    return "$base/apps/creator-dispatch/file/$encoded"
}

private data class RarityStyle(
    val color: Color,
    val tintTop: Float,
    val tintMid: Float,
    val tintBottom: Float,
)

private fun rarityStyle(rarity: String): RarityStyle = when (rarity.lowercase()) {
    "uncommon" -> RarityStyle(Color(0xFF22C55E), 0.16f, 0.11f, 0.40f)
    "rare" -> RarityStyle(Color(0xFF7C3AED), 0.20f, 0.14f, 0.44f)
    "epic" -> RarityStyle(Color(0xFFD946EF), 0.24f, 0.16f, 0.48f)
    "legendary" -> RarityStyle(Color(0xFFFFD700), 0.28f, 0.18f, 0.52f)
    else -> RarityStyle(Color(0xFF3B82F6), 0.14f, 0.10f, 0.38f)
}

private fun classIcon(category: String): String = when (category.lowercase()) {
    "shop" -> "🛍"
    "creator" -> "✦"
    "eazy" -> "⚡"
    "special" -> "★"
    else -> "★"
}

private fun giftCardCenterText(item: PrizeInventoryItem): String? {
    if (item.type != "prize") return null
    val mode = item.fulfillmentMode ?: return null
    if (mode != "instant_shopify_gc" && mode != "on_redeem_shopify_gc") return null
    val amount = item.metadata?.optString("amount")?.takeIf { it.isNotBlank() } ?: return null
    val num = amount.replace(Regex("\\.00$"), "")
    return "€$num\nGIFT CARD"
}

private fun statEntries(metadata: JSONObject?): List<Pair<String, String>> {
    val stats = metadata?.optJSONObject("stats") ?: return emptyList()
    val labels = mapOf(
        "utility" to "UTL",
        "luck" to "LCK",
        "craft" to "CRF",
        "charm" to "CHM",
        "power" to "PWR",
    )
    return labels.mapNotNull { (key, label) ->
        val v = stats.optInt(key, 0)
        if (v > 0) label to v.toString() else null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EazyPrizeCard(
    item: PrizeInventoryItem,
    apiBase: String,
    modifier: Modifier = Modifier,
    prizeView: Boolean = item.type == "prize",
    actions: @Composable () -> Unit = {},
) {
    val style = remember(item.rarity) { rarityStyle(item.rarity) }
    val shape = CutCornerShape(10.dp)
    val artKey = if (prizeView) item.priceArtworkR2Key ?: item.artworkR2Key else item.artworkR2Key
    val imageUrl = remember(artKey, apiBase) { prizeArtworkUrl(apiBase, artKey) }
    val centerValue = remember(item) { giftCardCenterText(item) }
    val stats = remember(item.metadata) { statEntries(item.metadata) }
    val isLegendary = item.rarity.equals("legendary", ignoreCase = true)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(shape)
            .border(
                width = if (item.fusionReady) 3.dp else 2.dp,
                color = if (item.fusionReady) style.color else style.color.copy(alpha = 0.88f),
                shape = shape,
            )
            .background(Color(0xFF0A0F18)),
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                style.color.copy(alpha = 0.32f),
                                Color(0xFF0A0F18),
                                Color(0xFF060A12),
                            ),
                        ),
                    ),
            )
            Text(
                text = "IMAGE COMING SOON",
                modifier = Modifier.align(Alignment.Center).padding(12.dp),
                color = Color(0xFF64748B),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            style.color.copy(alpha = style.tintTop),
                            style.color.copy(alpha = style.tintMid),
                            style.color.copy(alpha = style.tintBottom),
                            Color.Black.copy(alpha = 0.88f),
                        ),
                    ),
                ),
        )

        centerValue?.let { text ->
            Text(
                text = text,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
            )
        }

        if (isLegendary) {
            val transition = rememberInfiniteTransition(label = "prize-shimmer")
            val offsetX by transition.animateFloat(
                initialValue = -0.5f,
                targetValue = 1.5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(3400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "shimmer-x",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .width(120.dp)
                        .offset(x = (offsetX * 180).dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = 0.04f),
                                    Color(0xFFFFD700).copy(alpha = 0.22f),
                                    Color(0xFFFFF8C8).copy(alpha = 0.42f),
                                    Color(0xFFFFD700).copy(alpha = 0.22f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = classIcon(item.category),
                    modifier = Modifier
                        .background(Color(0xE00A0F18), CutCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    fontSize = 14.sp,
                )
                Text(
                    text = item.rarity.uppercase(),
                    modifier = Modifier
                        .background(
                            if (isLegendary) Color(0xEB4A3A0C) else style.color.copy(alpha = 0.38f),
                            CutCornerShape(4.dp),
                        )
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    color = if (isLegendary) Color(0xFFFFF9DB) else Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                if (stats.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 6.dp),
                    ) {
                        stats.forEach { (label, value) ->
                            Text(
                                text = "$label $value",
                                modifier = Modifier
                                    .background(Color(0xD10A0F18), CutCornerShape(3.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                color = Color(0xFFE2E8F0),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Text(
                    text = item.name,
                    color = Color(0xFFF8FAFC),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.description?.let { desc ->
                    Text(
                        text = desc,
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text(
                    text = when {
                        item.type == "card" && item.ownedCount > 1 ->
                            "Collectible ${item.ownedCount}/${item.fusionCount}"
                        item.type == "card" -> "Collectible"
                        else -> item.fulfillmentMode ?: "Prize"
                    },
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .background(Color(0xD10A0F18), CutCornerShape(3.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    color = Color(0xFFCBD5E1),
                    fontSize = 9.sp,
                )
                Box(modifier = Modifier.padding(top = 8.dp)) {
                    actions()
                }
            }
        }
    }
}
