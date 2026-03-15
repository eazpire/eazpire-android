package com.eazpire.creator.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.api.ShopifyProductsApi
import kotlinx.coroutines.delay

private const val IMAGE_ROTATE_INTERVAL_MS = 1800L
private const val CAROUSEL_SCROLL_PX_PER_SEC = 48f

@Composable
fun ProductCarousel(
    title: String,
    products: List<ShopifyProductsApi.ProductItem>,
    modifier: Modifier = Modifier
) {
    if (products.isEmpty()) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(products) { product ->
                    ProductCard(
                        product = product,
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(product.url)))
                            } catch (_: Exception) {}
                        }
                    )
                }
            }
            LaunchedEffect(Unit) {
                val scrollPxPerTick = with(density) { (CAROUSEL_SCROLL_PX_PER_SEC * 0.1f).toDp().toPx() }
                while (true) {
                    delay(100)
                    val layoutInfo = listState.layoutInfo
                    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    val canScrollMore = lastVisible < products.lastIndex ||
                        listState.firstVisibleItemScrollOffset < (layoutInfo.visibleItemsInfo.lastOrNull()?.size ?: 0)
                    if (canScrollMore) {
                        listState.scroll { scrollBy(scrollPxPerTick) }
                    } else {
                        listState.animateScrollToItem(0)
                    }
                }
            }
        }
    }
}


@Composable
private fun ProductCard(
    product: ShopifyProductsApi.ProductItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val images = product.images
    var currentIndex by remember(product.id) { mutableStateOf(0) }

    LaunchedEffect(product.id, images.size) {
        if (images.size <= 1) return@LaunchedEffect
        while (true) {
            delay(IMAGE_ROTATE_INTERVAL_MS)
            currentIndex = (currentIndex + 1) % images.size
        }
    }

    Column(
        modifier = modifier
            .size(width = 140.dp, height = 200.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            if (images.isNotEmpty()) {
                val url = images.getOrNull(currentIndex) ?: images.first()
                AnimatedContent(
                    targetState = url,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "productImage"
                ) { imgUrl ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imgUrl)
                            .crossfade(300)
                            .build(),
                        contentDescription = product.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )
                }
            }
        }
        Text(
            text = product.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}
