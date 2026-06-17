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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
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

/** Load hero slides once; parent caches so LazyColumn scroll does not re-fetch. */
suspend fun fetchHeroImagesForHome(
    api: CreatorApi,
    heroRegion: String,
): List<HeroImage> = withContext(Dispatchers.IO) {
    try {
        val json = api.getHeroPublishedRandom(limit = 6, region = heroRegion)
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
                        hotspots = hotspots,
                    )
                }.filter { it.imageUrl.isNotBlank() }
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        debugLog(
            "HeroCarousel.kt:fetch",
            "Hero API error",
            mapOf("error" to (e.message ?: "unknown")),
            "hero_load",
        )
        emptyList()
    }
}

@Composable
fun HeroCarousel(
    onProductClick: ((String) -> Unit)? = null,
    onHotspotProductClick: ((String) -> Unit)? = null,
    productModalHandleState: MutableState<String?>? = null,
    /** When set, skips internal API load (parent caches across LazyColumn dispose). */
    heroImages: List<HeroImage>? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Log.d(TAG_PRODUCT_MODAL, "[0] HeroCarousel composed: productModalHandleState=${productModalHandleState != null}")
    val api = remember { CreatorApi() }
    val productsApi = remember { ShopifyProductsApi() }
    val localeStore = remember { LocaleStore(context) }
    val heroRegion by localeStore.regionCode.collectAsState(initial = localeStore.getRegionCodeSync())
    var internalHeroImages by remember { mutableStateOf<List<HeroImage>>(emptyList()) }

    LaunchedEffect(heroRegion, heroImages) {
        if (heroImages != null) return@LaunchedEffect
        internalHeroImages = fetchHeroImagesForHome(api, heroRegion)
    }

    val displayImages = heroImages ?: internalHeroImages

    if (displayImages.isEmpty()) return

    val pairCount = (displayImages.size + 1) / 2
    var currentPairIndex by remember { mutableStateOf(0) }
    val slideProgress = remember { Animatable(1f) }
    var imageSizeByHeroId by remember { mutableStateOf<Map<String, Pair<Int, Int>>>(emptyMap()) }
    var carouselPaused by remember { mutableStateOf(false) }
    var hotspotLoading by remember { mutableStateOf(false) }
    val isModalOpen = productModalHandleState?.value != null

    LaunchedEffect(isModalOpen) {
        if (!isModalOpen && carouselPaused) {
            carouselPaused = false
        }
    }

    LaunchedEffect(displayImages.size, carouselPaused, isModalOpen) {
        if (pairCount < 2 || carouselPaused || isModalOpen) return@LaunchedEffect

        while (true) {
            delay(HERO_PAIR_ADVANCE_MS)
            if (carouselPaused || productModalHandleState?.value != null) return@LaunchedEffect

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
        carouselPaused = true
        hotspotLoading = true
        Log.d(
            TAG_PRODUCT_MODAL,
            "[3] onHotspotClick: handle=${hotspot.productHandle} gid=${hotspot.productGid} productModalState=${productModalHandleState != null} url=${hotspot.url}"
        )

        scope.launch {
            try {
                val directHandle = hotspot.productHandle?.trim()?.takeIf { it.isNotBlank() }
                    ?: productHandleFromUrl(hotspot.url)
                val handle = if (!directHandle.isNullOrBlank()) {
                    directHandle
                } else {
                    withContext(Dispatchers.IO) {
                        productsApi.resolveProductHandle(
                            handle = null,
                            gid = hotspot.productGid
                        )
                    }
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
                    carouselPaused = false
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
                carouselPaused = false
            } finally {
                hotspotLoading = false
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
        Box(modifier = Modifier.fillMaxWidth()) {
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
                    val baseHero = displayImages[(if (slot == 0) baseLeft else baseRight) % displayImages.size]
                    val baseHotspots = baseHero.hotspots
                    val baseImageSize = imageSizeByHeroId[baseHero.id]
                    val baseAlignment = hotspotAlignmentFromCentroid(baseHotspots)

                    HeroCell(
                        hero = baseHero,
                        slotHotspots = baseHotspots,
                        slotImageSize = baseImageSize,
                        alignment = baseAlignment,
                        onHotspotClick = { handleHotspotClick(it) },
                        onImageSize = { w, h ->
                            if (w > 0 && h > 0 && baseHero.id.isNotBlank()) {
                                val existing = imageSizeByHeroId[baseHero.id]
                                if (existing?.first != w || existing?.second != h) {
                                    imageSizeByHeroId = imageSizeByHeroId + (baseHero.id to (w to h))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(0f)
                    )
                    if (showOverlay) {
                        val overlayLeft = currentPairIndex * 2
                        val overlayRight = currentPairIndex * 2 + 1
                        val overlayHero = displayImages[(if (slot == 0) overlayLeft else overlayRight) % displayImages.size]
                        val overlayHotspots = overlayHero.hotspots
                        val overlayImageSize = imageSizeByHeroId[overlayHero.id]
                        val overlayAlignment = hotspotAlignmentFromCentroid(overlayHotspots)
                        HeroCell(
                            hero = overlayHero,
                            slotHotspots = overlayHotspots,
                            slotImageSize = overlayImageSize,
                            alignment = overlayAlignment,
                            onHotspotClick = { handleHotspotClick(it) },
                            onImageSize = { w, h ->
                                if (w > 0 && h > 0 && overlayHero.id.isNotBlank()) {
                                    val existing = imageSizeByHeroId[overlayHero.id]
                                    if (existing?.first != w || existing?.second != h) {
                                        imageSizeByHeroId = imageSizeByHeroId + (overlayHero.id to (w to h))
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer { translationY = offsetY }
                                .zIndex(1f)
                        )
                    }
                }
            }
        }
            if (hotspotLoading) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.White.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        color = EazColors.Orange,
                        strokeWidth = 3.dp
                    )
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
                .crossfade(150)
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
                imageAlignment = alignment,
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
    val pulseRingBaseDp = with(density) { (baseRadiusPx * 2f).toDp() }

    Box(
        modifier = modifier.scale(clickScale),
        contentAlignment = Alignment.Center
    ) {
        // Fixed layout size; pulse only via graphicsLayer so position stays pixel-stable.
        Box(
            modifier = Modifier
                .size(pulseRingBaseDp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
                .drawBehind {
                    drawCircle(
                        color = EazColors.Orange.copy(alpha = 0.35f),
                        radius = baseRadiusPx,
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

/** Maps ContentScale.Crop alignment to object-position fractions (0–1). */
private fun cropObjectPosition(alignment: Alignment): Pair<Float, Float> =
    when (alignment) {
        Alignment.TopStart -> 0f to 0f
        Alignment.TopCenter -> 0.5f to 0f
        Alignment.TopEnd -> 1f to 0f
        else -> 0.5f to 0.5f
    }

/**
 * Converts image coordinates (0-1) to container coordinates (0-1).
 * Mirrors hero-dynamic.js imageCoordsToContainerPercent for object-fit: cover.
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
    imageAlignment: Alignment,
    onHotspotClick: (HeroHotspot) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    var clickedHotspot by remember(hotspots) { mutableStateOf<HeroHotspot?>(null) }
    val (posX, posY) = cropObjectPosition(imageAlignment)
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().alpha(if (imageSize != null || hotspots.isEmpty()) 1f else 0f)
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val halfTouchPx = with(density) { (HERO_HOTSPOT_TOUCH_TARGET / 2).toPx() }
        hotspots.forEach { hotspot ->
            val (cx, cy) = when (val sz = imageSize) {
                null -> hotspot.x to hotspot.y
                else -> imageCoordsToContainer(
                    widthPx, heightPx, sz.first, sz.second,
                    hotspot.x, hotspot.y,
                    posX = posX,
                    posY = posY,
                ) ?: (hotspot.x to hotspot.y)
            }
            val centerXPx = cx * widthPx
            val centerYPx = cy * heightPx
            val clickInteraction = remember(hotspot.x, hotspot.y, hotspot.productHandle, hotspot.productGid, hotspot.url) {
                MutableInteractionSource()
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            (centerXPx - halfTouchPx).roundToInt(),
                            (centerYPx - halfTouchPx).roundToInt(),
                        )
                    }
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
