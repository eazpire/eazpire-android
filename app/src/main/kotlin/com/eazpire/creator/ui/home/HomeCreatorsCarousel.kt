package com.eazpire.creator.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.eazpire.creator.ui.components.EazLazyProductImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.formatCountLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ShopCreatorProductPreview(
    val handle: String,
    val title: String,
    val imageUrl: String?,
    val url: String,
)

data class ShopCreatorCard(
    val name: String,
    val slug: String,
    val profileImageUrl: String?,
    val ratingAvg: Double,
    val ratingCount: Int,
    val productCount: Int,
    val products: List<ShopCreatorProductPreview> = emptyList(),
)

internal val HomeCreatorsPanelGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xD134343C),
        Color(0xC744424E),
        Color(0xCC4E3E30),
    ),
)

suspend fun loadShopCreatorsForHome(
    creatorApi: CreatorApi,
    sortTab: String,
    limit: Int = 20,
): List<ShopCreatorCard> =
    withContext(Dispatchers.IO) {
        runCatching {
            parseShopCreators(creatorApi.listShopCreators(sort = sortTab, limit = limit))
        }.getOrElse { emptyList() }
    }

suspend fun loadShopCreatorsForIndex(
    creatorApi: CreatorApi,
    sortTab: String,
    limit: Int = 48,
): List<ShopCreatorCard> =
    withContext(Dispatchers.IO) {
        runCatching {
            parseShopCreators(
                creatorApi.listShopCreators(
                    sort = sortTab,
                    limit = limit,
                    includeProducts = true,
                    productsPerCreator = 12,
                ),
            )
        }.getOrElse { emptyList() }
    }

@Composable
fun HomeCreatorsCarousel(
    creators: List<ShopCreatorCard>,
    sortTab: String,
    loading: Boolean,
    onSortTabChange: (String) -> Unit,
    labelForKey: (String, String) -> String,
    onCreatorClick: (String) -> Unit,
    onCreatorsTitleClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(HomeCreatorsPanelGradient)
            .border(1.dp, Color(0x38F97316), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = labelForKey("eaz.home.creators", "Creators"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.then(
                    if (onCreatorsTitleClick != null) {
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onCreatorsTitleClick)
                    } else {
                        Modifier
                    },
                ),
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.1f))
                    .border(1.dp, Color(0x40F97316), RoundedCornerShape(999.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf("recommend" to "eaz.home.recommended", "new" to "eaz.product_card.new").forEach { (sort, key) ->
                    val active = sortTab == sort
                    Text(
                        text = labelForKey(key, if (sort == "recommend") "Recommended" else "New"),
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                if (active) {
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFFF97316), Color(0xFFFB923C)),
                                    )
                                } else {
                                    Brush.linearGradient(
                                        colors = listOf(Color.Transparent, Color.Transparent),
                                    )
                                },
                            )
                            .clickable { onSortTabChange(sort) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) Color.White else Color.White.copy(alpha = 0.75f),
                    )
                }
            }
        }

        when {
            loading && creators.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.size(28.dp))
                }
            }
            creators.isEmpty() -> {
                Text(
                    text = labelForKey("eaz.home.no_recommended_products", "No creators to show right now."),
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            else -> {
                val scroll = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scroll)
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    creators.forEach { c ->
                        CreatorHomeCard(
                            creator = c,
                            reviewsLabel = formatCountLabel(
                                labelForKey("eaz.common.rating_reviews", "{{ count }} reviews"),
                                c.ratingCount,
                            ),
                            productsLabel = formatCountLabel(
                                labelForKey("eaz.creator_profile.products_count", "{{ count }} products"),
                                c.productCount,
                            ),
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
            .width(148.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.92f))
                .border(2.dp, Color(0x73F97316), CircleShape),
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
            modifier = Modifier.padding(top = 10.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        CreatorRatingStars(
            rating = creator.ratingAvg,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = reviewsLabel,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
        )
        Text(
            text = productsLabel,
            fontSize = 11.sp,
            color = Color(0xD9FFEDD5),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun CreatorRatingStars(rating: Double, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val full = rating.toInt().coerceIn(0, 5)
        val hasHalf = rating - full >= 0.25 && full < 5
        repeat(5) { index ->
            val tint = when {
                index < full -> Color(0xFFFB923C)
                index == full && hasHalf -> Color(0xFFFB923C).copy(alpha = 0.55f)
                else -> Color.White.copy(alpha = 0.25f)
            }
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(12.dp),
            )
        }
        if (rating > 0) {
            Text(
                text = "%.1f".format(rating),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

private fun parseShopCreators(json: JSONObject): List<ShopCreatorCard> {
    if (!json.optBoolean("ok", false)) return emptyList()
    val arr = json.optJSONArray("creators") ?: return emptyList()
    val out = ArrayList<ShopCreatorCard>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val name = o.optString("creator_name", "").trim()
        if (name.isBlank()) continue
        val productsArr = o.optJSONArray("products")
        val products = if (productsArr != null) {
            buildList {
                for (j in 0 until productsArr.length()) {
                    val p = productsArr.optJSONObject(j) ?: continue
                    val handle = p.optString("handle", "").trim()
                    if (handle.isBlank()) continue
                    add(
                        ShopCreatorProductPreview(
                            handle = handle,
                            title = p.optString("title", handle).trim().ifBlank { handle },
                            imageUrl = p.optString("image", "").trim().ifBlank { null },
                            url = p.optString("url", "/products/$handle").trim().ifBlank { "/products/$handle" },
                        ),
                    )
                }
            }
        } else {
            emptyList()
        }
        out.add(
            ShopCreatorCard(
                name = name,
                slug = o.optString("slug", "").trim(),
                profileImageUrl = o.optString("profile_image_url", "").trim().ifBlank { null },
                ratingAvg = o.optDouble("rating_avg", 0.0),
                ratingCount = o.optInt("rating_count", 0),
                productCount = o.optInt("product_count", 0),
                products = products,
            ),
        )
    }
    return out
}
