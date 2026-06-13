package com.eazpire.creator.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Brush
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio

@Composable
fun CreatorsIndexScreen(
    creatorApi: CreatorApi,
    labelForKey: (String, String) -> String,
    onCreatorClick: (String) -> Unit,
    onProductClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sortTab by remember { mutableStateOf("recommend") }
    var creators by remember { mutableStateOf<List<ShopCreatorCard>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(sortTab) {
        loading = true
        creators = loadShopCreatorsForIndex(creatorApi, sortTab, limit = 48)
        loading = false
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(HomeCreatorsPanelGradient)
                .border(1.dp, Color(0x38F97316), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 18.dp),
        ) {
            Column {
                RowHeader(sortTab = sortTab, onSortTabChange = { sortTab = it }, labelForKey = labelForKey)
                when {
                    loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.size(32.dp))
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
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 168.dp),
                            contentPadding = PaddingValues(top = 14.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth(),
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
                                    onCreatorClick = { onCreatorClick(creator.name) },
                                    onProductClick = onProductClick,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowHeader(
    sortTab: String,
    onSortTabChange: (String) -> Unit,
    labelForKey: (String, String) -> String,
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
                                Brush.linearGradient(colors = listOf(Color.Transparent, Color.Transparent))
                            },
                        )
                        .clickable { onSortTabChange(sort) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) Color.White else Color.White.copy(alpha = 0.75f),
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
    onCreatorClick: () -> Unit,
    onProductClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
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
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
            CreatorRatingStars(rating = creator.ratingAvg, modifier = Modifier.padding(top = 6.dp))
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
        if (creator.products.isNotEmpty()) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.14f))
                    .border(
                        width = 0.dp,
                        color = Color.Transparent,
                    ),
            ) {
                val slideWidth = maxWidth
                val scroll = rememberScrollState()
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scroll),
                ) {
                    creator.products.forEach { product ->
                        Column(
                            modifier = Modifier
                                .width(slideWidth)
                                .clickable { onProductClick(product.handle) }
                                .padding(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.08f)),
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
                                    Text(
                                        "?",
                                        color = Color.White.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(32.dp),
                                    )
                                }
                            }
                            Text(
                                text = product.title,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = Color.White.copy(alpha = 0.92f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
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
