package com.eazpire.creator.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.eazpire.creator.EazColors
import com.eazpire.creator.mockup.CustomerMockPreviewStore
import kotlinx.coroutines.delay

/** Per-card color start index (parity with web `eazHashRotationIndex`). */
fun plpRotationStartIndex(productId: String, count: Int): Int {
    if (count <= 1) return 0
    return (productId.hashCode() and Int.MAX_VALUE) % count
}

fun plpRotationJitterMs(productId: String): Long =
    (productId.hashCode() and 0x3FF).toLong()

private val PLP_FALLBACK_HEX = mapOf(
    "black" to Color(0xFF111111),
    "white" to Color(0xFFF5F5F5),
    "sport grey" to Color(0xFF9CA3AF),
    "sport gray" to Color(0xFF9CA3AF),
    "grey" to Color(0xFF9CA3AF),
    "gray" to Color(0xFF9CA3AF),
    "ash" to Color(0xFFC5C5C5),
    "heather" to Color(0xFF9CA3AF),
    "heather grey" to Color(0xFF9CA3AF),
    "heather gray" to Color(0xFF9CA3AF),
    "navy" to Color(0xFF1E3A5F),
    "navy blue" to Color(0xFF1E3A5F),
    "blue" to Color(0xFF2563EB),
    "royal blue" to Color(0xFF1D4ED8),
    "light blue" to Color(0xFF93C5FD),
    "carolina blue" to Color(0xFF7DD3FC),
    "red" to Color(0xFFDC2626),
    "cardinal" to Color(0xFF9B1C1C),
    "maroon" to Color(0xFF7F1D1D),
    "burgundy" to Color(0xFF7F1D1D),
    "orange" to Color(0xFFF97316),
    "safety orange" to Color(0xFFF97316),
    "yellow" to Color(0xFFFACC15),
    "gold" to Color(0xFFEAB308),
    "green" to Color(0xFF16A34A),
    "forest" to Color(0xFF166534),
    "forest green" to Color(0xFF166534),
    "kelly" to Color(0xFF22C55E),
    "pink" to Color(0xFFEC4899),
    "hot pink" to Color(0xFFDB2777),
    "purple" to Color(0xFF7C3AED),
    "violet" to Color(0xFF8B5CF6),
    "brown" to Color(0xFF92400E),
    "chocolate" to Color(0xFF78350F),
    "beige" to Color(0xFFD6C3A8),
    "sand" to Color(0xFFD6C3A8),
    "khaki" to Color(0xFFB8A077),
    "natural" to Color(0xFFE7E0D4),
    "cream" to Color(0xFFF5F0E6),
    "charcoal" to Color(0xFF374151),
    "silver" to Color(0xFFD1D5DB),
    "teal" to Color(0xFF0D9488),
    "coral" to Color(0xFFF87171),
)

private fun plpSwatchColor(name: String?): Color? {
    val key = name?.trim()?.lowercase()?.replace("\\s+".toRegex(), " ").orEmpty()
    if (key.isBlank()) return null
    PLP_FALLBACK_HEX[key]?.let { return it }
    PLP_FALLBACK_HEX[key.replace(" ", "")]?.let { return it }
    key.split(' ').asReversed().forEach { part ->
        PLP_FALLBACK_HEX[part]?.let { return it }
    }
    return null
}

/** Home / carousel: downscaled Coil request + placeholder (no decode full Shopify originals). */
@Composable
fun EazLazyProductImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    targetWidthPx: Int = 320,
    /** Skip Coil downscale — use for pre-rendered mock URLs (R2). */
    fullResolution: Boolean = false,
) {
    val context = LocalContext.current
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant
    SubcomposeAsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .apply {
                if (!fullResolution) size(Size(targetWidthPx, targetWidthPx))
            }
            .crossfade(200)
            .build(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier,
        loading = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(placeholderColor),
            )
        },
        error = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(placeholderColor),
            )
        },
    )
}

@Composable
fun EazProductCardRotatingImages(
    imageUrls: List<String>,
    productId: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    rotateIntervalMs: Long = 1800L,
    autoRotate: Boolean = true,
    /** Home carousels: load only the visible frame (+ optional next), not every variant URL. */
    lazyLoadImages: Boolean = false,
    targetWidthPx: Int = 320,
    fullResolution: Boolean = false,
    /** Controlled index (IDEA-065). When null, component owns index. */
    selectedIndex: Int? = null,
    onIndexChange: ((Int) -> Unit)? = null,
) {
    val urls = remember(imageUrls) { imageUrls.filter { it.isNotBlank() }.distinct() }
    var internalIndex by remember(productId, urls.size) {
        mutableIntStateOf(plpRotationStartIndex(productId, urls.size.coerceAtLeast(1)))
    }
    // Controlled mode: parent owns index + auto-tick. Uncontrolled: rotate internally.
    val currentIndex = when {
        selectedIndex != null && urls.isNotEmpty() -> selectedIndex.coerceIn(0, urls.lastIndex)
        else -> internalIndex
    }

    LaunchedEffect(productId, urls.size, autoRotate, selectedIndex != null) {
        if (!autoRotate || urls.size <= 1 || selectedIndex != null) return@LaunchedEffect
        delay(plpRotationJitterMs(productId))
        while (true) {
            delay(rotateIntervalMs)
            internalIndex = (internalIndex + 1) % urls.size
            onIndexChange?.invoke(internalIndex)
        }
    }

    Box(modifier = modifier) {
        if (urls.isEmpty()) return@Box
        if (lazyLoadImages) {
            val activeUrl = urls[currentIndex.coerceIn(0, urls.lastIndex)]
            val nextIndex = (currentIndex + 1) % urls.size
            val nextUrl = if (urls.size > 1) urls[nextIndex] else null
            EazLazyProductImage(
                url = activeUrl,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                targetWidthPx = targetWidthPx,
                fullResolution = fullResolution,
            )
            if (nextUrl != null && nextUrl != activeUrl) {
                val context = LocalContext.current
                LaunchedEffect(nextUrl) {
                    context.imageLoader.enqueue(
                        ImageRequest.Builder(context)
                            .data(nextUrl)
                            .apply {
                                if (!fullResolution) size(Size(targetWidthPx, targetWidthPx))
                            }
                            .build(),
                    )
                }
            }
            return@Box
        }
        urls.forEachIndexed { index, url ->
            val isActive = index == currentIndex
            val alpha by animateFloatAsState(
                targetValue = if (isActive) 1f else 0f,
                animationSpec = tween(
                    durationMillis = 1200,
                    easing = FastOutSlowInEasing
                ),
                label = "plpCardImageAlpha"
            )
            if (!isActive && alpha <= 0.01f) return@forEachIndexed
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = alpha)
                    .zIndex(if (isActive) 1f else 0f)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .apply {
                            if (!fullResolution) size(Size(targetWidthPx, targetWidthPx))
                        }
                        .crossfade(0)
                        .build(),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun EazProductCardMediaOverlays(
    modifier: Modifier = Modifier,
    showFavorite: Boolean = true,
    showCart: Boolean = true,
    showTryOn: Boolean = false,
    isTryOnActive: Boolean = false,
    tryOnLoading: Boolean = false,
    onFavoriteClick: () -> Unit = {},
    onCartClick: () -> Unit = {},
    onTryOnClick: () -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (showFavorite) {
            IconButton(
                onClick = onFavoriteClick,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.92f))
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = "Favorite",
                    tint = Color(0xFFBBBBBB),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (showCart) {
            IconButton(
                onClick = onCartClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.95f))
            ) {
                Icon(
                    Icons.Default.ShoppingBag,
                    contentDescription = "Add to cart",
                    tint = Color(0xFF6B7280),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        if (showTryOn) {
            IconButton(
                onClick = onTryOnClick,
                enabled = !tryOnLoading,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isTryOnActive) Color(0xFF111827) else Color.White.copy(alpha = 0.95f)
                    )
            ) {
                if (tryOnLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = if (isTryOnActive) Color.White else EazColors.Orange
                    )
                } else {
                    HangerIcon(
                        modifier = Modifier.size(20.dp),
                        color = if (isTryOnActive) Color.White else Color(0xFF1A1A1A)
                    )
                }
            }
        }
    }
}

/**
 * IDEA-065 / PARITY-PLP-CARD-001 — mock + left thumbs + variant dots under mock.
 * Manual thumb/dot selection pauses auto-rotation for this card.
 */
@Composable
fun EazProductCardPlpMediaStack(
    imageUrls: List<String>,
    productId: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    colorNames: List<String> = emptyList(),
    viewsByColor: Map<String, List<String>> = emptyMap(),
    rotateIntervalMs: Long = 1800L,
    autoRotate: Boolean = true,
    fullResolution: Boolean = false,
    /** Controlled expand (tap). Null = manage internally. */
    expanded: Boolean? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    onDetailClick: () -> Unit = {},
    showFavorite: Boolean = true,
    showCart: Boolean = true,
    showTryOn: Boolean = false,
    isTryOnActive: Boolean = false,
    tryOnLoading: Boolean = false,
    onFavoriteClick: () -> Unit = {},
    onCartClick: () -> Unit = {},
    onTryOnClick: () -> Unit = {},
) {
    val urls = remember(imageUrls) { imageUrls.filter { it.isNotBlank() }.distinct() }
    var currentIndex by remember(productId, urls.size) {
        mutableIntStateOf(plpRotationStartIndex(productId, urls.size.coerceAtLeast(1)))
    }
    var paused by remember(productId) { mutableStateOf(false) }
    var overrideUrl by remember(productId) { mutableStateOf<String?>(null) }
    var internalExpanded by remember(productId) { mutableStateOf(false) }
    val isExpanded = expanded ?: internalExpanded
    fun setExpanded(value: Boolean) {
        if (expanded == null) internalExpanded = value
        onExpandedChange?.invoke(value)
    }
    val scrollState = rememberScrollState()

    val activeColor = colorNames.getOrNull(currentIndex)?.trim()?.lowercase().orEmpty()
    val mainUrl = overrideUrl ?: urls.getOrNull(currentIndex).orEmpty()
    val otherViewUrls = remember(activeColor, viewsByColor, mainUrl) {
        val fromColor = if (activeColor.isNotBlank()) viewsByColor[activeColor].orEmpty() else emptyList()
        val mainBase = mainUrl.substringBefore('?')
        fromColor
            .filter { it.isNotBlank() && it.substringBefore('?') != mainBase }
            .distinct()
            .take(4)
    }

    fun selectIndex(index: Int, pause: Boolean = true) {
        if (urls.isEmpty()) return
        val idx = ((index % urls.size) + urls.size) % urls.size
        currentIndex = idx
        overrideUrl = null
        if (pause) paused = true
    }

    LaunchedEffect(productId, urls.size, autoRotate, paused, overrideUrl) {
        if (!autoRotate || paused || overrideUrl != null || urls.size <= 1) return@LaunchedEffect
        delay(plpRotationJitterMs(productId))
        while (true) {
            delay(rotateIntervalMs)
            currentIndex = (currentIndex + 1) % urls.size
        }
    }

    LaunchedEffect(currentIndex, urls.size, isExpanded) {
        if (!isExpanded || urls.size <= 1) return@LaunchedEffect
        val approxDot = 28
        val target = (currentIndex * approxDot - 40).coerceAtLeast(0)
        scrollState.scrollTo(target)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 5f)
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    if (!isExpanded) setExpanded(true)
                }
        ) {
            val displayUrls = if (overrideUrl != null && currentIndex in urls.indices) {
                urls.toMutableList().also { it[currentIndex] = overrideUrl!! }
            } else {
                urls
            }

            if (displayUrls.isNotEmpty()) {
                EazProductCardRotatingImages(
                    imageUrls = displayUrls,
                    productId = productId,
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.22f
                            scaleY = 1.22f
                        },
                    rotateIntervalMs = rotateIntervalMs,
                    autoRotate = false,
                    fullResolution = fullResolution,
                    selectedIndex = currentIndex,
                )
            }

            if (isExpanded && otherViewUrls.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.92f))
                        .zIndex(4f)
                ) {
                    val cells = otherViewUrls
                    Column(
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(cells.take(2), cells.drop(2).take(2)).forEach { row ->
                            if (row.isEmpty()) return@forEach
                            Row(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                row.forEach { url ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(6.dp))
                                            .border(1.dp, Color(0x14000000), RoundedCornerShape(6.dp))
                                            .background(Color.White)
                                            .clickable {
                                                paused = true
                                                overrideUrl = url
                                            }
                                    ) {
                                        AsyncImage(
                                            model = url,
                                            contentDescription = null,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                                if (row.size == 1) {
                                    Box(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            if (isExpanded) {
                IconButton(
                    onClick = onDetailClick,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .zIndex(6f)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.96f))
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "View product details",
                        tint = Color(0xFF1A1A1A),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isExpanded) {
                EazProductCardMediaOverlays(
                    showFavorite = showFavorite,
                    showCart = showCart,
                    showTryOn = showTryOn,
                    isTryOnActive = isTryOnActive,
                    tryOnLoading = tryOnLoading,
                    onFavoriteClick = onFavoriteClick,
                    onCartClick = onCartClick,
                    onTryOnClick = onTryOnClick,
                )
            }
        }

        if (isExpanded && urls.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .padding(horizontal = 2.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { selectIndex(currentIndex - 1, pause = true) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowLeft,
                        contentDescription = "Previous variant",
                        tint = Color(0xFF6B7280),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    urls.forEachIndexed { index, url ->
                        val colorName = colorNames.getOrNull(index)
                        val swatch = plpSwatchColor(colorName)
                        val active = index == currentIndex
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(swatch ?: Color(0xFFD1D5DB))
                                .then(
                                    if (swatch == null) {
                                        Modifier.border(1.dp, Color(0x33000000), CircleShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .border(
                                    width = if (active) 2.dp else 0.dp,
                                    color = if (active) EazColors.Orange else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectIndex(index, pause = true) }
                        ) {
                            if (swatch == null) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = colorName ?: "Variant ${index + 1}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape)
                                )
                            }
                        }
                    }
                }
                IconButton(
                    onClick = { selectIndex(currentIndex + 1, pause = true) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowRight,
                        contentDescription = "Next variant",
                        tint = Color(0xFF6B7280),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/** Toggle try-on session so [CustomerMockPreviewStore.resolveCardImages] picks mock vs shop URLs. */
fun togglePlpTryOnSession(context: android.content.Context, handle: String, active: Boolean) {
    CustomerMockPreviewStore.setTryOnSessionActive(context, handle, active)
}
