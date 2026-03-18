package com.eazpire.creator.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.imageLoader
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.ShopifyProductsApi
import kotlinx.coroutines.delay

// Web-Parameter aus eaz-home-sections.liquid
private const val IMAGE_ROTATE_INTERVAL_MS = 1800L
private const val IMAGE_FADE_DURATION_MS = 1200
private const val IMAGE_FADE_CLEANUP_MS = 1250L
private const val CAROUSEL_SCROLL_PX_PER_SEC = 48f
private const val CAROUSEL_TICK_MS = 50L
private const val PAUSE_AFTER_MANUAL_SCROLL_MS = 1000L

/** Optional collection context for breadcrumb when navigating to product. */
data class ProductClickWithCollection(
    val handle: String,
    val collectionTitle: String?,
    val collectionHandle: String?
)

@Composable
fun ProductCarousel(
    title: String,
    products: List<ShopifyProductsApi.ProductItem>,
    collectionHandle: String? = null,
    onTitleClick: (() -> Unit)? = null,
    onProductClick: ((ProductClickWithCollection) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (products.isEmpty()) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()

    val infiniteProducts = remember(products) { products + products }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(EazColors.Orange.copy(alpha = 0.35f))
                .padding(vertical = 12.dp)
                .then(
                    if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(infiniteProducts, key = { index, p -> "${p.id}-$index" }) { index, product ->
                    ProductCard(
                        product = product,
                        onClick = {
                            if (onProductClick != null) {
                                onProductClick(
                                    ProductClickWithCollection(
                                        handle = product.handle,
                                        collectionTitle = title.takeIf { collectionHandle != null },
                                        collectionHandle = collectionHandle
                                    )
                                )
                            } else {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(product.url)))
                                } catch (_: Exception) {}
                            }
                        }
                    )
                }
            }
            var lastManualScrollEnd by remember { mutableLongStateOf(0L) }
            var wasUserScrolling by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                snapshotFlow { listState.isScrollInProgress }.collect { inProgress ->
                    if (wasUserScrolling && !inProgress) lastManualScrollEnd = System.currentTimeMillis()
                    wasUserScrolling = inProgress
                }
            }
            // Web-Logik: scrollBy mit kleinem Intervall (wie CSS animation linear infinite)
            // 48px/Sek, Tick alle 50ms → ~2.4px pro Tick
            LaunchedEffect(Unit) {
                val scrollPxPerTick = with(density) {
                    (CAROUSEL_SCROLL_PX_PER_SEC * CAROUSEL_TICK_MS / 1000f).toDp().toPx()
                }
                while (true) {
                    delay(CAROUSEL_TICK_MS)
                    val sinceLastManual = System.currentTimeMillis() - lastManualScrollEnd
                    if (sinceLastManual < PAUSE_AFTER_MANUAL_SCROLL_MS) continue
                    if (listState.isScrollInProgress) continue
                    val firstIndex = listState.firstVisibleItemIndex
                    val firstOffset = listState.firstVisibleItemScrollOffset
                    if (firstIndex >= products.size) {
                        listState.scrollToItem(0, firstOffset)
                        delay(50)
                        continue
                    }
                    val layoutInfo = listState.layoutInfo
                    val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    val canScrollMore = lastVisible < infiniteProducts.lastIndex ||
                        firstOffset < (layoutInfo.visibleItemsInfo.lastOrNull()?.size ?: 0)
                    if (canScrollMore) {
                        listState.scroll { scrollBy(scrollPxPerTick) }
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
    val images = (product.variantImages.ifEmpty { product.images })
    var displayIndex by remember(product.id) { mutableStateOf(0) }
    var isTransitioning by remember(product.id) { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(product.id, images) {
        images.forEach { url ->
            context.imageLoader.enqueue(
                ImageRequest.Builder(context).data(url).build()
            )
        }
    }
    LaunchedEffect(product.id, images.size, displayIndex, isTransitioning) {
        if (images.size <= 1) return@LaunchedEffect
        if (isTransitioning) return@LaunchedEffect
        delay(IMAGE_ROTATE_INTERVAL_MS)
        isTransitioning = true
    }

    val nextIndex = (displayIndex + 1) % images.size
    // Web: new slide fades IN (0->1) over 1.2s, old stays visible until cleanup
    val incomingAlpha by animateFloatAsState(
        targetValue = if (isTransitioning) 1f else 0f,
        animationSpec = tween(durationMillis = IMAGE_FADE_DURATION_MS),
        label = "variantIncoming"
    )
    LaunchedEffect(isTransitioning) {
        if (isTransitioning) {
            delay(IMAGE_FADE_CLEANUP_MS)
            displayIndex = nextIndex
            isTransitioning = false
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
                // Untere Ebene: aktuelles Bild (bleibt sichtbar während Transition, Web: old stays)
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(images.getOrNull(displayIndex) ?: images.first())
                        .crossfade(0)
                        .build(),
                    contentDescription = product.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )
                // Obere Ebene: nächstes Bild blendet ein (Web: new fades in 0->1 über 1.2s)
                key(displayIndex) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(images.getOrNull(nextIndex) ?: images.first())
                            .crossfade(0)
                            .build(),
                        contentDescription = product.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .graphicsLayer(alpha = incomingAlpha)
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
