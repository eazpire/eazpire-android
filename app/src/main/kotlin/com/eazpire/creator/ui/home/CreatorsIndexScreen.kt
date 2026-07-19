package com.eazpire.creator.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
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
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.formatCountLabel
import com.eazpire.creator.ui.components.EazLazyProductImage

private val CreatorsCardBorder = Color(0x73F97316)
private val CreatorsCardBg = Color.White
private val CreatorsPageBg = Color(0xFFF5F5F5)
private val CreatorsProductAreaBg = Color(0xFFFAFAFA)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CreatorsIndexScreen(
    creatorApi: CreatorApi,
    labelForKey: (String, String) -> String,
    onCreatorClick: (String) -> Unit,
    onProductClick: (String) -> Unit,
    customerId: String = "",
    modifier: Modifier = Modifier,
) {
    var sortTab by remember { mutableStateOf("recommend") }
    var creators by remember { mutableStateOf<List<ShopCreatorCard>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(sortTab, customerId) {
        loading = true
        creators = loadShopCreatorsForIndex(creatorApi, sortTab, limit = 48, customerId = customerId.ifBlank { null })
        loading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CreatorsPageBg),
    ) {
        CreatorsIndexHeader(
            sortTab = sortTab,
            onSortTabChange = { sortTab = it },
            labelForKey = labelForKey,
        )
        Divider(color = Color(0xFFE8E8E8))
        when {
            loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.size(32.dp))
                }
            }
            creators.isEmpty() -> {
                Text(
                    text = labelForKey(
                        if (sortTab == "subscribed") "eaz.creator_follow.empty_subscribed" else "eaz.home.no_recommended_products",
                        if (sortTab == "subscribed") "You are not following any creators yet." else "No creators to show right now.",
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = EazColors.TextSecondary,
                )
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    items(creators, key = { "${it.name}::${it.slug}" }) { creator ->
                        CreatorsIndexCard(
                            creator = creator,
                            reviewsLabel = formatCountLabel(
                                labelForKey("eaz.common.rating_reviews", "{{ count }} reviews"),
                                creator.ratingCount,
                            ),
                            productsLabel = formatCountLabel(
                                labelForKey("eaz.creator_profile.products_count", "{{ count }} products"),
                                creator.productCount,
                            ),
                            labelForKey = labelForKey,
                            onCreatorClick = { onCreatorClick(creator.name) },
                            onProductClick = onProductClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatorsIndexHeader(
    sortTab: String,
    onSortTabChange: (String) -> Unit,
    labelForKey: (String, String) -> String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = labelForKey("eaz.home.creators", "Creators"),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = EazColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0xFFF3F3F3))
                .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(999.dp))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                "recommend" to "eaz.home.recommended",
                "new" to "eaz.product_card.new",
                "subscribed" to "eaz.creator_follow.subscribed",
            ).forEach { (sort, key) ->
                val active = sortTab == sort
                val fallback = when (sort) {
                    "recommend" -> "Recommended"
                    "new" -> "New"
                    else -> "Subscribed"
                }
                Text(
                    text = labelForKey(key, fallback),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (active) EazColors.Orange else Color.Transparent)
                        .clickable { onSortTabChange(sort) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) Color.White else EazColors.TextSecondary,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

@Composable
private fun CreatorsIndexCard(
    creator: ShopCreatorCard,
    reviewsLabel: String,
    productsLabel: String,
    labelForKey: (String, String) -> String,
    onCreatorClick: () -> Unit,
    onProductClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CreatorsCardBg)
            .border(1.dp, CreatorsCardBorder, RoundedCornerShape(12.dp)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onCreatorClick)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(2.dp, CreatorsCardBorder, CircleShape),
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
                    )
                } else {
                    Text(
                        text = creator.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = EazColors.Orange,
                    )
                }
            }
            Text(
                text = creator.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = EazColors.TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
            CreatorRatingStars(rating = creator.ratingAvg, modifier = Modifier.padding(top = 6.dp))
            Text(
                text = reviewsLabel,
                fontSize = 11.sp,
                color = EazColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = productsLabel,
                fontSize = 11.sp,
                color = EazColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                text = formatCountLabel(
                    labelForKey("eaz.creator_follow.followers_count", "{{ count }} followers"),
                    creator.followerCount,
                ),
                fontSize = 11.sp,
                color = EazColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (creator.isFollowing) {
                Text(
                    text = labelForKey("eaz.creator_follow.subscribed_badge", "Subscribed"),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = EazColors.Orange,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (creator.products.isNotEmpty()) {
            Divider(color = Color(0xFFE8E8E8))
            CreatorProductCarousel(
                products = creator.products,
                onProductClick = onProductClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CreatorsProductAreaBg),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CreatorProductCarousel(
    products: List<ShopCreatorProductPreview>,
    onProductClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (products.size == 1) {
        CreatorProductSlide(
            product = products[0],
            onProductClick = onProductClick,
            modifier = modifier,
        )
        return
    }
    val pagerState = rememberPagerState(pageCount = { products.size })
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
    ) { page ->
        CreatorProductSlide(
            product = products[page],
            onProductClick = onProductClick,
        )
    }
}

@Composable
private fun CreatorProductSlide(
    product: ShopCreatorProductPreview,
    onProductClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onProductClick(product.handle) }
            .padding(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF0F0F0)),
            contentAlignment = Alignment.Center,
        ) {
            val img = product.imageUrl
            if (!img.isNullOrBlank()) {
                EazLazyProductImage(
                    url = img,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text("?", color = EazColors.TextSecondary, modifier = Modifier.padding(32.dp))
            }
        }
        Text(
            text = product.title,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = EazColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
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
                index < full -> EazColors.Orange
                index == full && hasHalf -> EazColors.Orange.copy(alpha = 0.55f)
                else -> Color(0xFFDDDDDD)
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
                color = EazColors.TextPrimary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
