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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
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
import com.eazpire.creator.api.hasPromoPricingUi
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.Locale

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
    modifier: Modifier = Modifier,
    /** If set, show title row + this message when [products] is empty (e.g. Promotions). */
    emptyStateMessage: String? = null,
    /** Promotions carousel: before/after price + countdown. */
    promoProductLayout: Boolean = false,
    promoEndsPrefix: String = "",
    promoEndedLabel: String = "",
    promoNextDiscountPrefix: String = "",
    promoNextPriceHintPrefix: String = ""
) {
    if (products.isEmpty()) {
        if (emptyStateMessage == null) return
        Column(modifier = modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(EazColors.Orange.copy(alpha = 0.35f))
                    .padding(vertical = 6.dp)
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
            Text(
                text = emptyStateMessage,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
            )
        }
        return
    }
    val context = LocalContext.current
    val density = LocalDensity.current
    val listState = rememberLazyListState()

    val infiniteProducts = remember(products) { products + products }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(EazColors.Orange.copy(alpha = 0.35f))
                .padding(vertical = 6.dp)
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
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(infiniteProducts, key = { index, p -> "${p.id}-$index" }) { index, product ->
                    ProductCard(
                        product = product,
                        promoStyle = promoProductLayout || product.hasPromoPricingUi(),
                        promoEndsPrefix = promoEndsPrefix,
                        promoEndedLabel = promoEndedLabel,
                        promoNextDiscountPrefix = promoNextDiscountPrefix,
                        promoNextPriceHintPrefix = promoNextPriceHintPrefix,
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

private fun formatShopMoney(value: Double): String =
    try {
        NumberFormat.getCurrencyInstance(Locale.GERMANY).format(value)
    } catch (_: Exception) {
        "%.2f €".format(value)
    }

private fun formatPromoDuration(ms: Long): String {
    if (ms <= 0L) return ""
    var s = ms / 1000L
    val d = s / 86400L
    s %= 86400L
    val h = s / 3600L
    s %= 3600L
    val m = s / 60L
    val sec = s % 60L
    return when {
        d > 0L -> "${d}d ${h}h"
        h > 0L -> "${h}h ${m}m ${sec}s"
        else -> "${m}m ${sec}s"
    }
}

/** Same split as CollectionScreen / web eaz-product-card-redesign (design line + product type line). */
private fun splitProductTitleForCard(title: String, productType: String): Pair<String, String> {
    val normalized = title
        .replace(" — ", " | ")
        .replace(" – ", " | ")
        .replace(" - ", " | ")
    val parts = normalized.split(" | ")
    val designTitle = parts.firstOrNull()?.trim()?.ifBlank { title } ?: title
    val productTypeTitle = when {
        parts.size > 1 -> parts.drop(1).joinToString(" - ").trim()
        productType.isNotBlank() -> productType
        else -> ""
    }
    return designTitle to productTypeTitle
}

@Composable
private fun PromoCountdownChip(endsAtMs: Long, endsPrefix: String, endedLabel: String) {
    var now by remember(endsAtMs) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAtMs) {
        while (now < endsAtMs) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val left = endsAtMs - now
    val label = if (left <= 0L) endedLabel else "$endsPrefix ${formatPromoDuration(left)}"
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = EazColors.Orange,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun ProductCard(
    product: ShopifyProductsApi.ProductItem,
    promoStyle: Boolean = false,
    promoEndsPrefix: String = "",
    promoEndedLabel: String = "",
    promoNextDiscountPrefix: String = "",
    promoNextPriceHintPrefix: String = "",
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

    val nextIndex =
        if (images.isEmpty()) 0 else (displayIndex + 1) % images.size
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
            .width(140.dp)
            .heightIn(min = 220.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (images.isEmpty()) Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                    else Modifier
                )
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
        val (designTitle, productTypeTitle) = remember(product.title, product.productType) {
            splitProductTitleForCard(product.title, product.productType)
        }
        Column(modifier = Modifier.padding(top = 6.dp)) {
            Text(
                text = designTitle,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (productTypeTitle.isNotBlank()) {
                Text(
                    text = productTypeTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = EazColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (promoStyle && product.price > 0) {
            val waiting = product.promoOutsideSlot
            val nextHint = promoNextPriceHintPrefix.ifBlank { "Promo from" }
            val nextDisc = promoNextDiscountPrefix.ifBlank { "Discount in" }
            if (waiting) {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    Text(
                        text = formatShopMoney(product.price),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val preview = product.promoPreviewPrice
                    if (preview != null && preview < product.price - 1e-6) {
                        Text(
                            text = "$nextHint ${formatShopMoney(preview)}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = EazColors.Orange,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    val nextAt = product.promoNextWindowStartsAtMs
                    if (nextAt != null && nextAt > 0L) {
                        PromoCountdownChip(
                            endsAtMs = nextAt,
                            endsPrefix = nextDisc,
                            endedLabel = promoEndedLabel.ifBlank { "Ended" }
                        )
                    }
                }
            } else {
                val before = product.promoBeforePrice
                    ?: product.compareAtPrice?.takeIf { it > product.price + 1e-6 }
                val strikePrice = before?.takeIf { it > product.price + 1e-6 }
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = formatShopMoney(product.price),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = EazColors.Orange
                        )
                        if (strikePrice != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formatShopMoney(strikePrice),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                textDecoration = TextDecoration.LineThrough
                            )
                        }
                    }
                    val ends = product.promotionEndsAtMs
                    if (ends != null && ends > 0L) {
                        PromoCountdownChip(
                            endsAtMs = ends,
                            endsPrefix = promoEndsPrefix.ifBlank { "Ends in" },
                            endedLabel = promoEndedLabel.ifBlank { "Ended" }
                        )
                    }
                }
            }
        } else if (!promoStyle && product.price > 0) {
            Text(
                text = formatShopMoney(product.price),
                style = MaterialTheme.typography.labelSmall,
                color = EazColors.TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
