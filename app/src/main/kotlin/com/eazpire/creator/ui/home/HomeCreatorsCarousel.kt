package com.eazpire.creator.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.ui.components.EazLazyProductImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ShopCreatorCard(
    val name: String,
    val slug: String,
    val profileImageUrl: String?,
    val ratingAvg: Double,
    val ratingCount: Int,
    val productCount: Int,
)

private fun parseShopCreators(json: JSONObject): List<ShopCreatorCard> {
    if (!json.optBoolean("ok", false)) return emptyList()
    val arr = json.optJSONArray("creators") ?: return emptyList()
    val out = ArrayList<ShopCreatorCard>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val name = o.optString("creator_name", "").trim()
        if (name.isBlank()) continue
        out.add(
            ShopCreatorCard(
                name = name,
                slug = o.optString("slug", "").trim(),
                profileImageUrl = o.optString("profile_image_url", "").trim().ifBlank { null },
                ratingAvg = o.optDouble("rating_avg", 0.0),
                ratingCount = o.optInt("rating_count", 0),
                productCount = o.optInt("product_count", 0),
            ),
        )
    }
    return out
}

@Composable
fun HomeCreatorsCarousel(
    creatorApi: CreatorApi,
    labelForKey: (String, String) -> String,
    onCreatorClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sortTab by remember { mutableStateOf("recommend") }
    var loading by remember { mutableStateOf(true) }
    var creators by remember { mutableStateOf<List<ShopCreatorCard>>(emptyList()) }

    LaunchedEffect(sortTab) {
        loading = true
        creators = withContext(Dispatchers.IO) {
            runCatching {
                parseShopCreators(creatorApi.listShopCreators(sort = sortTab, limit = 20))
            }.getOrElse { emptyList() }
        }
        loading = false
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(EazColors.Orange.copy(alpha = 0.35f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = labelForKey("eaz.home.creators", "Creators"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("recommend" to "eaz.home.recommended", "new" to "eaz.product_card.new").forEach { (sort, key) ->
                    val active = sortTab == sort
                    Text(
                        text = labelForKey(key, if (sort == "recommend") "Recommended" else "New"),
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (active) Color.White else Color.Transparent)
                            .clickable { sortTab = sort }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 13.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) EazColors.Orange else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        when {
            loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.size(28.dp))
                }
            }
            creators.isEmpty() -> {
                Text(
                    text = labelForKey("eaz.home.no_recommended_products", "No creators to show right now."),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                val scroll = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scroll)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    creators.forEach { c ->
                        CreatorHomeCard(
                            creator = c,
                            reviewsLabel = labelForKey("eaz.common.rating_reviews", "__COUNT__ reviews")
                                .replace("__COUNT__", c.ratingCount.toString()),
                            productsLabel = labelForKey(
                                "content.search_results_resource_products_count.other",
                                "__COUNT__ products",
                            ).replace("__COUNT__", c.productCount.toString()),
                            onClick = { onCreatorClick(c.name) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatorHomeCard(
    creator: ShopCreatorCard,
    reviewsLabel: String,
    productsLabel: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFF5ED)),
            contentAlignment = Alignment.Center,
        ) {
            val img = creator.profileImageUrl
            if (!img.isNullOrBlank()) {
                EazLazyProductImage(
                    url = img,
                    contentDescription = null,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    targetWidthPx = 144,
                )
            } else {
                Text(
                    text = creator.name.firstOrNull()?.uppercase() ?: "?",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = EazColors.Orange,
                )
            }
        }
        Text(
            text = creator.name,
            modifier = Modifier.padding(top = 8.dp),
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 4.dp),
        ) {
            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(14.dp))
            Text(
                text = if (creator.ratingAvg > 0) "%.1f".format(creator.ratingAvg) else "—",
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Text(text = reviewsLabel, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = productsLabel, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
