package com.eazpire.creator.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyProductsApi
import com.eazpire.creator.debug.debugLog
import com.eazpire.creator.locale.LocaleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TAG_PRODUCT_MODAL = "ProductModalDebug"
/** Zwei Bilder nebeneinander: jedes quadratisch, gleich groß */
private const val HERO_CELL_ASPECT_RATIO = 1f
private const val HERO_PAIR_ADVANCE_MS = 3500L
private const val HERO_SLIDE_DURATION_MS = 450
private val HERO_HOTSPOT_SIZE = 14.dp
private val HERO_HOTSPOT_TOUCH_TARGET = 48.dp
private val HERO_HOTSPOT_RING = 3.dp
private const val STORE_BASE_URL = "https://www.eazpire.com"

data class HeroHotspot(
    val x: Float,
    val y: Float,
    val url: String?,
    val title: String?,
    val productHandle: String? = null,
    val productGid: String? = null
)

data class HeroImage(
    val id: String,
    val imageUrl: String,
    val thumbnailUrl: String?,
    val title: String?,
    val link: String?,
    val hotspots: List<HeroHotspot>
)

private fun JSONObject.optNonBlank(vararg keys: String): String? {
    for (key in keys) {
        val value = optString(key, "").trim()
        if (value.isNotBlank() && value != "null") return value
    }
    return null
}

private fun productHandleFromUrl(rawUrl: String?): String? {
    val url = rawUrl?.trim()?.takeIf { it.isNotBlank() && it != "#" } ?: return null

    val marker = "/products/"
    val path = when {
        url.startsWith(marker) -> url.removePrefix(marker)
        url.contains(marker) -> url.substringAfter(marker)
        url.startsWith("products/") -> url.removePrefix("products/")
        else -> return null
    }

    return path
        .substringBefore("?")
        .substringBefore("#")
        .trimEnd('/')
        .takeIf { it.isNotBlank() }
}

private fun parseHotspotEntry(
    x: Float,
    y: Float,
    url: String?,
    title: String?,
    productHandle: String?,
    productGid: String?
): HeroHotspot? {
    val cleanUrl = url?.trim()?.takeIf { it.isNotBlank() && it != "#" }
    val cleanHandle = productHandle?.trim()?.takeIf { it.isNotBlank() }
        ?: productHandleFromUrl(cleanUrl)
    val cleanGid = productGid?.trim()?.takeIf { it.isNotBlank() }

    if (cleanHandle.isNullOrBlank() && cleanGid.isNullOrBlank() && cleanUrl.isNullOrBlank()) {
        Log.w(
            TAG_PRODUCT_MODAL,
            "[parseHotspots] Ignoring hotspot without handle/gid/url: x=$x y=$y title=$title"
        )
        return null
    }

    return HeroHotspot(
        x = x.coerceIn(0f, 1f),
        y = y.coerceIn(0f, 1f),
        url = cleanUrl ?: cleanHandle?.let { "/products/$it" },
        title = title,
        productHandle = cleanHandle,
        productGid = cleanGid
    )
}

private fun parseHotspotObject(
    h: JSONObject,
    itemHandle: String? = null,
    itemUrl: String? = null,
    itemTitle: String? = null,
    itemGid: String? = null
): HeroHotspot? {
    val x = h.optDouble("x", 0.5).toFloat()
    val y = h.optDouble("y", 0.5).toFloat()
    val url = h.optNonBlank("product_url", "productUrl", "url", "link", "href") ?: itemUrl
    val title = h.optNonBlank(
        "product_title",
        "productTitle",
        "product_name",
        "productName",
        "title"
    ) ?: itemTitle
    val handle = h.optNonBlank(
        "product_handle",
        "productHandle",
        "handle",
        "product_slug",
        "productSlug"
    ) ?: itemHandle
    val gid = h.optNonBlank("product_gid", "productGid", "product_id", "productId") ?: itemGid
    return parseHotspotEntry(x, y, url, title, handle, gid)
}

private fun parseHotspots(obj: JSONObject): List<HeroHotspot> {
    val result = mutableListOf<HeroHotspot>()

    try {
        val hotspotsArr = obj.optJSONArray("hotspots")
        if (hotspotsArr != null) {
            for (j in 0 until hotspotsArr.length()) {
                val h = hotspotsArr.optJSONObject(j) ?: continue
                parseHotspotObject(h)?.let { result.add(it) }
            }
        }

        if (result.isEmpty()) {
            val raw = obj.opt("hotspots_json")
            val parsedAny = when (raw) {
                is JSONObject -> raw
                is String -> raw.takeIf { it.isNotBlank() }?.let {
                    try {
                        org.json.JSONTokener(it).nextValue()
                    } catch (_: Exception) {
                        null
                    }
                }
                else -> null
            }

            when (parsedAny) {
                is org.json.JSONArray -> {
                    for (i in 0 until parsedAny.length()) {
                        val h = parsedAny.optJSONObject(i) ?: continue
                        parseHotspotObject(h)?.let { result.add(it) }
                    }
                }

                is JSONObject -> {
                    val items = parsedAny.optJSONArray("items")
                    if (items != null) {
                        for (itemIndex in 0 until items.length()) {
                            val item = items.optJSONObject(itemIndex) ?: continue
                            val itemGid = item.optNonBlank(
                                "product_id",
                                "productId",
                                "product_gid",
                                "productGid"
                            )
                            val itemHandle = item.optNonBlank(
                                "product_handle",
                                "productHandle",
                                "handle",
                                "product_slug",
                                "productSlug"
                            )
                            val itemUrl = item.optNonBlank(
                                "product_url",
                                "productUrl",
                                "url",
                                "link",
                                "href"
                            )
                            val itemTitle = item.optNonBlank(
                                "product_title",
                                "productTitle",
                                "product_name",
                                "productName",
                                "title"
                            )
                            val itemHotspots = item.optJSONArray("hotspots")
                            if (itemHotspots != null) {
                                for (hIndex in 0 until itemHotspots.length()) {
                                    val h = itemHotspots.optJSONObject(hIndex) ?: continue
                                    parseHotspotObject(
                                        h = h,
                                        itemHandle = itemHandle,
                                        itemUrl = itemUrl,
                                        itemTitle = itemTitle,
                                        itemGid = itemGid
                                    )?.let { result.add(it) }
                                }
                            }
                        }
                    }

                    if (result.isEmpty()) {
                        val directHotspots = parsedAny.optJSONArray("hotspots")
                        if (directHotspots != null) {
                            for (i in 0 until directHotspots.length()) {
                                val h = directHotspots.optJSONObject(i) ?: continue
                                parseHotspotObject(h)?.let { result.add(it) }
                            }
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.w(TAG_PRODUCT_MODAL, "[parseHotspots] Failed to parse hotspots", e)
    }

    Log.d(TAG_PRODUCT_MODAL, "[parseHotspots] Parsed ${result.size} usable hotspots")
    return result
}

@Composable
fun HeroCarousel(
    onProductClick: ((String) -> Unit)? = null,
    onHotspotProductClick: ((String) -> Unit)? = null,
    productModalHandleState: MutableState<String?>? = null,
    fallbackProductHandle: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // #region agent log
    debugLog("HeroCarousel.kt:149", "HeroCarousel composed", mapOf("productModalHandleStateNotNull" to (productModalHandleState != null)), "H1")
    // #endregion
    Log.d(TAG_PRODUCT_MODAL, "[0] HeroCarousel composed: productModalHandleState=${productModalHandleState != null}")
    val api = remember { CreatorApi() }
    val productsApi = remember { ShopifyProductsApi() }
    val localeStore = remember { LocaleStore(context) }
    val heroRegion by localeStore.regionCode.collectAsState(initial = localeStore.getRegionCodeSync())
    var heroImages by remember { mutableStateOf<List<HeroImage>>(emptyList()) }

    LaunchedEffect(heroRegion, fallbackProductHandle) {
        heroImages = withContext(Dispatchers.IO) {
            try {
                val json = api.getHeroPublishedRandom(limit = 6, region = heroRegion)
                // #region agent log
                debugLog("HeroCarousel.kt:165", "Hero API response", mapOf(
                    "ok" to json.optBoolean("ok", false),
                    "heroRegion" to heroRegion,
                    "imagesCount" to (json.optJSONArray("images")?.length() ?: json.optJSONArray("items")?.length() ?: 0)
                ), "hero_load")
                // #endregion
                if (json.optBoolean("ok", false)) {
                    val arr = json.optJSONArray("images") ?: json.optJSONArray("items")
                    if (arr != null) {
                        (0 until arr.length()).map { i ->
                            val obj = arr.getJSONObject(i)
                            val hotspots = parseHotspots(obj)
                            HeroImage(
                                id = obj.optString("id", ""),
                                imageUrl = obj.optString("image_url", "").takeIf { it.isNotBlank() }
                                    ?: obj.optString("thumbnail_url", ""),
                                thumbnailUrl = obj.optString("thumbnail_url", "").takeIf { it.isNotBlank() },
                                title = obj.optString("title", "").takeIf { it.isNotBlank() },
                                link = null,
                                hotspots = hotspots
                            )
                        }.filter { it.imageUrl.isNotBlank() }
                    } else emptyList()
                } else emptyList()
            } catch (e: Exception) {
                // #region agent log
                debugLog("HeroCarousel.kt:188", "Hero API error", mapOf("error" to (e.message ?: "unknown")), "hero_load")
                // #endregion
                emptyList()
            }
        }
        val useFallback = heroImages.isEmpty() || (heroImages.size == 1 && heroImages[0].id == "fallback")
        if (useFallback) {
            val handle = fallbackProductHandle?.takeIf { it.isNotBlank() } ?: "gift-card"
            heroImages = listOf(
                HeroImage(
                    id = "fallback",
                    imageUrl = "https://picsum.photos/800/600",
                    thumbnailUrl = null,
                    title = "Test",
                    link = null,
                    hotspots = listOf(
                        HeroHotspot(0.5f, 0.5f, "/products/$handle", "Test Hotspot", handle)
                    )
                )
            )
        }
    }

    if (heroImages.isEmpty()) return

    val pairCount = (heroImages.size + 1) / 2
    var currentPairIndex by remember { mutableStateOf(0) }
    val slideProgress = remember { Animatable(1f) }
    var imageSizeBySlot by remember { mutableStateOf<Map<Int, Pair<Int, Int>>>(emptyMap()) }
    val isModalOpen = productModalHandleState?.value != null

    LaunchedEffect(heroImages.size, isModalOpen) {
        if (pairCount < 2 || isModalOpen) return@LaunchedEffect

        while (true) {
            delay(HERO_PAIR_ADVANCE_MS)
            if (productModalHandleState?.value != null) return@LaunchedEffect

            currentPairIndex = (currentPairIndex + 1) % pairCount
            slideProgress.snapTo(0f)
            slideProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(HERO_SLIDE_DURATION_MS, easing = FastOutSlowInEasing)
            )
        }
    }

    /** Unterer Teil mehr abgeschnitten als oberer → Alignment nach oben */
    fun hotspotAlignmentFromCentroid(hotspots: List<HeroHotspot>): Alignment =
        if (hotspots.isEmpty()) Alignment.TopCenter else {
            val avgX = hotspots.map { it.x }.average().toFloat()
            val avgY = hotspots.map { it.y }.average().toFloat()
            when {
                avgY < 0.33f -> when {
                    avgX < 0.33f -> Alignment.TopStart
                    avgX > 0.66f -> Alignment.TopEnd
                    else -> Alignment.TopCenter
                }
                avgY > 0.66f -> when {
                    avgX < 0.33f -> Alignment.TopStart
                    avgX > 0.66f -> Alignment.TopEnd
                    else -> Alignment.TopCenter
                }
                else -> when {
                    avgX < 0.33f -> Alignment.TopStart
                    avgX > 0.66f -> Alignment.TopEnd
                    else -> Alignment.TopCenter
                }
            }
        }

    fun handleHotspotClick(hotspot: HeroHotspot) {
        Log.d(
            TAG_PRODUCT_MODAL,
            "[3] onHotspotClick: handle=${hotspot.productHandle} gid=${hotspot.productGid} productModalState=${productModalHandleState != null} url=${hotspot.url}"
        )

        scope.launch {
            val handle = withContext(Dispatchers.IO) {
                productsApi.resolveProductHandle(
                    handle = hotspot.productHandle ?: productHandleFromUrl(hotspot.url),
                    gid = hotspot.productGid
                )
            }

            if (!handle.isNullOrBlank()) {
                val cleanHandle = handle.trim()

                Log.d(
                    TAG_PRODUCT_MODAL,
                    "[4] Hotspot resolved product handle=$cleanHandle; callback=${onHotspotProductClick != null}; state=${productModalHandleState != null}"
                )

                onHotspotProductClick?.invoke(cleanHandle)

                if (productModalHandleState != null) {
                    debugLog(
                        "HeroCarousel.kt",
                        "Setting productModalHandleState",
                        mapOf("handle" to cleanHandle),
                        "H1"
                    )
                    productModalHandleState.value = cleanHandle
                    return@launch
                }

                if (onHotspotProductClick == null) {
                    onProductClick?.invoke(cleanHandle)
                }
                return@launch
            }

            Log.w(
                TAG_PRODUCT_MODAL,
                "[3b] Hotspot clicked but no product handle found: url=${hotspot.url}, title=${hotspot.title}, gid=${hotspot.productGid}"
            )

            if (!hotspot.url.isNullOrBlank() && hotspot.url != "#") {
                val fullUrl = if (hotspot.url.startsWith("http")) {
                    hotspot.url
                } else {
                    "$STORE_BASE_URL${hotspot.url}"
                }

                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)))
                } catch (e: Exception) {
                    Log.w(TAG_PRODUCT_MODAL, "[3c] Failed to open hotspot URL: $fullUrl", e)
                }
            }
        }
    }

    val density = LocalDensity.current
    val basePairIndex = if (slideProgress.value < 1f) (currentPairIndex - 1 + pairCount) % pairCount else currentPairIndex
    val showOverlay = pairCount >= 2 && slideProgress.value < 1f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            listOf(0, 1).forEach { slot ->
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(HERO_CELL_ASPECT_RATIO)
                ) {
                    val cellHeightPx = with(density) { maxHeight.toPx() }
                    val leftOffsetY = (slideProgress.value - 1f) * cellHeightPx
                    val rightOffsetY = (1f - slideProgress.value) * cellHeightPx
                    val offsetY = if (slot == 0) leftOffsetY else rightOffsetY

                    val baseLeft = basePairIndex * 2
                    val baseRight = basePairIndex * 2 + 1
                    val baseHero = heroImages[(if (slot == 0) baseLeft else baseRight) % heroImages.size]
                    val baseHotspots = baseHero.hotspots
                    val baseImageSize = imageSizeBySlot[slot]
                    val baseAlignment = hotspotAlignmentFromCentroid(baseHotspots)

                    HeroCell(
                        hero = baseHero,
                        slotHotspots = baseHotspots,
                        slotImageSize = baseImageSize,
                        alignment = baseAlignment,
                        onHotspotClick = { handleHotspotClick(it) },
                        onImageSize = { w, h -> if (w > 0 && h > 0) imageSizeBySlot = imageSizeBySlot + (slot to (w to h)) },
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(0f)
                    )
                    if (showOverlay) {
                        val overlayLeft = currentPairIndex * 2
                        val overlayRight = currentPairIndex * 2 + 1
                        val overlayHero = heroImages[(if (slot == 0) overlayLeft else overlayRight) % heroImages.size]
                        val overlayHotspots = overlayHero.hotspots
                        val overlayAlignment = hotspotAlignmentFromCentroid(overlayHotspots)
                        HeroCell(
                            hero = overlayHero,
                            slotHotspots = overlayHotspots,
                            slotImageSize = null,
                            alignment = overlayAlignment,
                            onHotspotClick = { handleHotspotClick(it) },
                            onImageSize = { _, _ -> },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationY = offsetY }
                                .zIndex(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCell(
    hero: HeroImage,
    slotHotspots: List<HeroHotspot>,
    slotImageSize: Pair<Int, Int>?,
    alignment: Alignment,
    onHotspotClick: (HeroHotspot) -> Unit,
    onImageSize: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(hero.imageUrl)
                .crossfade(300)
                .build(),
            contentDescription = hero.title ?: "Hero image",
            contentScale = ContentScale.Crop,
            alignment = alignment,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { success ->
                val d = success.result.drawable
                val w = d.intrinsicWidth
                val h = d.intrinsicHeight
                onImageSize(w, h)
            }
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.4f)
                        ),
                        startY = 0f,
                        endY = 1000f
                    )
                )
        )
        if (slotHotspots.isNotEmpty()) {
            HeroHotspotsOverlay(
                modifier = Modifier.matchParentSize(),
                hotspots = slotHotspots,
                imageSize = slotImageSize,
                onHotspotClick = onHotspotClick
            )
        }
    }
}

@Composable
private fun HeroHotspotDot(
    contentDescription: String?,
    isJustClicked: Boolean = false,
    onClickAnimationDone: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val infiniteTransition = rememberInfiniteTransition(label = "hotspotPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )
    val clickScale by animateFloatAsState(
        targetValue = if (isJustClicked) 1.3f else 1f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "clickScale"
    )
    LaunchedEffect(isJustClicked) {
        if (isJustClicked) {
            delay(200)
            onClickAnimationDone()
        }
    }

    val ringPx = with(density) { HERO_HOTSPOT_RING.toPx() }
    val dotRadiusPx = with(density) { (HERO_HOTSPOT_SIZE / 2).toPx() }
    val baseRadiusPx = dotRadiusPx + ringPx
    val pulseRadiusPx = baseRadiusPx * pulseScale
    val pulseSizeDp = with(density) { (pulseRadiusPx * 2).toDp() }

    Box(
        modifier = modifier.scale(clickScale),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(pulseSizeDp)
                .drawBehind {
                    drawCircle(
                        color = EazColors.Orange.copy(alpha = 0.35f),
                        radius = pulseRadiusPx,
                        center = center
                    )
                }
        )
        Box(
            modifier = Modifier
                .size(HERO_HOTSPOT_SIZE)
                .drawBehind {
                    drawCircle(
                        color = EazColors.Orange.copy(alpha = 0.5f),
                        radius = baseRadiusPx,
                        center = center
                    )
                }
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.9f))
                .border(2.dp, Color.White, CircleShape)
        )
    }
}

/**
 * Converts image coordinates (0-1) to container coordinates (0-1).
 * Mirrors hero-dynamic.js imageCoordsToContainerPercent for object-fit: cover + center.
 */
private fun imageCoordsToContainer(
    containerW: Float,
    containerH: Float,
    imageW: Int,
    imageH: Int,
    xImg: Float,
    yImg: Float,
    posX: Float = 0.5f,
    posY: Float = 0.5f
): Pair<Float, Float>? {
    if (containerW <= 0f || containerH <= 0f || imageW <= 0 || imageH <= 0) return null
    val scale = maxOf(containerW / imageW, containerH / imageH)
    val scaledW = imageW * scale
    val scaledH = imageH * scale
    val offsetX = containerW * posX - scaledW * posX
    val offsetY = containerH * posY - scaledH * posY
    val px = offsetX + xImg * scaledW
    val py = offsetY + yImg * scaledH
    return (px / containerW) to (py / containerH)
}

@Composable
private fun HeroHotspotsOverlay(
    hotspots: List<HeroHotspot>,
    imageSize: Pair<Int, Int>?,
    onHotspotClick: (HeroHotspot) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var clickedHotspot by remember(hotspots) { mutableStateOf<HeroHotspot?>(null) }
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val maxW = maxWidth
        val maxH = maxHeight
        val halfTouch = HERO_HOTSPOT_TOUCH_TARGET.value / 2f
        hotspots.forEach { hotspot ->
            val (cx, cy) = when (val sz = imageSize) {
                null -> hotspot.x to hotspot.y
                else -> imageCoordsToContainer(
                    widthPx, heightPx, sz.first, sz.second,
                    hotspot.x, hotspot.y
                ) ?: (hotspot.x to hotspot.y)
            }
            val xDp = (cx * maxW.value - halfTouch).dp
            val yDp = (cy * maxH.value - halfTouch).dp
            val clickInteraction = remember(hotspot.x, hotspot.y, hotspot.productHandle, hotspot.productGid, hotspot.url) {
                MutableInteractionSource()
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = xDp, y = yDp)
                    .size(HERO_HOTSPOT_TOUCH_TARGET)
                    .clickable(
                        interactionSource = clickInteraction,
                        indication = null,
                    ) {
                        clickedHotspot = hotspot
                        onHotspotClick(hotspot)
                    },
                contentAlignment = Alignment.Center
            ) {
                HeroHotspotDot(
                    contentDescription = hotspot.title,
                    isJustClicked = clickedHotspot == hotspot,
                    onClickAnimationDone = { if (clickedHotspot == hotspot) clickedHotspot = null },
                    modifier = Modifier,
                )
            }
        }
    }
}
