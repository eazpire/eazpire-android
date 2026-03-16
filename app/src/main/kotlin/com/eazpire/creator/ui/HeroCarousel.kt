package com.eazpire.creator.ui

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import android.view.MotionEvent
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.debug.debugLog
import com.eazpire.creator.locale.LocaleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TAG_PRODUCT_MODAL = "ProductModalDebug"
private const val HERO_AUTO_ADVANCE_MS = 3500L
private const val HERO_ASPECT_RATIO = 2f / 3f
private val HERO_HOTSPOT_SIZE = 14.dp
private val HERO_HOTSPOT_TOUCH_TARGET = 48.dp
private val HERO_HOTSPOT_RING = 3.dp
private const val STORE_BASE_URL = "https://www.eazpire.com"

data class HeroHotspot(
    val x: Float,
    val y: Float,
    val url: String?,
    val title: String?,
    val productHandle: String? = null
)

data class HeroImage(
    val id: String,
    val imageUrl: String,
    val thumbnailUrl: String?,
    val title: String?,
    val link: String?,
    val hotspots: List<HeroHotspot>
)

private fun parseHotspots(obj: JSONObject): List<HeroHotspot> {
    val result = mutableListOf<HeroHotspot>()
    try {
        val hotspotsArr = obj.optJSONArray("hotspots")
        if (hotspotsArr != null) {
            for (j in 0 until hotspotsArr.length()) {
                val h = hotspotsArr.getJSONObject(j)
                val x = h.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f)
                val y = h.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f)
                val url = h.optString("url", "").takeIf { it.isNotBlank() }
                val title = h.optString("title", "").takeIf { it.isNotBlank() }
                val handle = h.optString("product_handle", "").takeIf { it.isNotBlank() }
                    ?: url?.takeIf { it.startsWith("/products/") }?.removePrefix("/products/")?.trimEnd('/')
                result.add(HeroHotspot(x = x, y = y, url = url, title = title, productHandle = handle))
            }
        }
        val hotspotsJson = obj.optString("hotspots_json", "").takeIf { it.isNotBlank() }
        if (result.isEmpty() && hotspotsJson != null) {
            val parsed = JSONObject(hotspotsJson)
            val items = parsed.optJSONArray("items")
            if (items != null) {
                for (k in 0 until items.length()) {
                    val item = items.getJSONObject(k)
                    val productHandle = item.optString("product_handle", "").takeIf { it.isNotBlank() }
                    val productTitle = item.optString("product_name", "")
                        .takeIf { it.isNotBlank() } ?: item.optString("product_title", "Produkt")
                    val itemHotspots = item.optJSONArray("hotspots")
                    if (itemHotspots != null) {
                        for (m in 0 until itemHotspots.length()) {
                            val h = itemHotspots.getJSONObject(m)
                            val x = h.optDouble("x", 0.5).toFloat().coerceIn(0f, 1f)
                            val y = h.optDouble("y", 0.5).toFloat().coerceIn(0f, 1f)
                            val url = if (productHandle != null) "/products/$productHandle" else null
                            result.add(HeroHotspot(x = x, y = y, url = url, title = productTitle, productHandle = productHandle))
                        }
                    }
                }
            }
        }
    } catch (_: Exception) { }
    return result
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun HeroCarousel(
    onProductClick: ((String) -> Unit)? = null,
    onHotspotProductClick: ((String) -> Unit)? = null,
    productModalHandleState: MutableState<String?>? = null,
    fallbackProductHandle: String? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // #region agent log
    debugLog("HeroCarousel.kt:149", "HeroCarousel composed", mapOf("productModalHandleStateNotNull" to (productModalHandleState != null)), "H1")
    // #endregion
    Log.d(TAG_PRODUCT_MODAL, "[0] HeroCarousel composed: productModalHandleState=${productModalHandleState != null}")
    val api = remember { CreatorApi() }
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

    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        pageCount = { heroImages.size },
        initialPage = 0
    )

    LaunchedEffect(heroImages.size, pagerState) {
        if (heroImages.size < 2) return@LaunchedEffect
        while (true) {
            delay(HERO_AUTO_ADVANCE_MS)
            val currentPage = snapshotFlow { pagerState.currentPage }.first()
            val next = (currentPage + 1) % heroImages.size
            pagerState.animateScrollToPage(next)
        }
    }

    var imageSizeByPage by remember { mutableStateOf<Map<Int, Pair<Int, Int>>>(emptyMap()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
                userScrollEnabled = heroImages.size > 1
            ) { page ->
                val hero = heroImages[page]
                val pageHotspots = when {
                    hero.hotspots.isNotEmpty() -> hero.hotspots
                    fallbackProductHandle != null -> listOf(
                        HeroHotspot(0.5f, 0.5f, "/products/$fallbackProductHandle", "Produkt", fallbackProductHandle)
                    )
                    else -> listOf(
                        HeroHotspot(0.5f, 0.5f, "/products/gift-card", "Produkt", "gift-card")
                    )
                }
                val pageImageSize = imageSizeByPage[page]
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(HERO_ASPECT_RATIO)
                        .clip(RoundedCornerShape(0.dp))
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(hero.imageUrl)
                            .crossfade(300)
                            .build(),
                        contentDescription = hero.title ?: "Hero image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(HERO_ASPECT_RATIO),
                        onSuccess = { success ->
                            val d = success.result.drawable
                            val w = d.intrinsicWidth
                            val h = d.intrinsicHeight
                            if (w > 0 && h > 0) {
                                imageSizeByPage = imageSizeByPage + (page to (w to h))
                            }
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
                    if (pageHotspots.isNotEmpty()) {
                        HeroHotspotsOverlay(
                            modifier = Modifier.matchParentSize(),
                            hotspots = pageHotspots,
                            imageSize = pageImageSize,
                            onHotspotClick = { hotspot ->
                        val handle = when {
                            hotspot.url != null && hotspot.url.startsWith("/products/") ->
                                hotspot.url.removePrefix("/products/").trimEnd('/')
                            hotspot.url != null && hotspot.url.contains("/products/") -> {
                                val idx = hotspot.url.indexOf("/products/") + "/products/".length
                                hotspot.url.substring(idx).trimEnd('/').substringBefore('?').substringBefore('#')
                            }
                            hotspot.productHandle != null -> hotspot.productHandle
                            else -> null
                        }
                        Log.d(TAG_PRODUCT_MODAL, "[3] onHotspotClick: handle=$handle productModalState=${productModalHandleState != null}")
                                if (handle != null && handle.isNotBlank()) {
                                    when {
                                        productModalHandleState != null -> {
                                            // #region agent log
                                            debugLog("HeroCarousel.kt:288", "Setting productModalHandleState", mapOf(
                                                "handle" to handle,
                                                "productModalHandleStateNotNull" to true
                                            ), "H1")
                                            // #endregion
                                            Log.d(TAG_PRODUCT_MODAL, "[4] Setting productModalHandleState.value = $handle")
                                            productModalHandleState.value = handle
                                            // #region agent log
                                            debugLog("HeroCarousel.kt:292", "AFTER productModalHandleState.value = handle", mapOf("handle" to handle), "H2")
                                            // #endregion
                                            onHotspotProductClick?.invoke(handle)
                                        }
                                        onHotspotProductClick != null -> {
                                            // #region agent log
                                            debugLog("HeroCarousel.kt:298", "Using onHotspotProductClick branch", mapOf("handle" to handle), "H4")
                                            // #endregion
                                            onHotspotProductClick(handle)
                                        }
                                        else -> onProductClick?.invoke(handle)
                                    }
                                } else if (hotspot.url != null && hotspot.url != "#" && hotspot.url.isNotBlank() && !hotspot.url.startsWith("/products/")) {
                            val fullUrl = if (hotspot.url.startsWith("http")) hotspot.url else "$STORE_BASE_URL${hotspot.url}"
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)))
                                    } catch (_: Exception) { }
                                } else {
                                    // #region agent log
                                    debugLog("HeroCarousel.kt:318", "handle null or blank", mapOf("handle" to handle), "H5")
                                    // #endregion
                                }
                            }
                        )
                    }
                }
            }
            if (heroImages.size > 1) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    heroImages.forEachIndexed { index, _ ->
                        val isActive = pagerState.currentPage == index
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(
                                    width = if (isActive) 20.dp else 8.dp,
                                    height = 8.dp
                                )
                                .clip(if (isActive) RoundedCornerShape(4.dp) else CircleShape)
                                .background(
                                    if (isActive) androidx.compose.ui.graphics.Color.White
                                    else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f)
                                )
                                .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                        )
                    }
                }
            }
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

private fun findHotspotAt(
    x: Float, y: Float, widthPx: Float, heightPx: Float,
    hotspots: List<HeroHotspot>, imageSize: Pair<Int, Int>?,
    touchRadiusPx: Float
): HeroHotspot? {
    if (widthPx <= 0f || heightPx <= 0f) return null
    val hit = hotspots.minByOrNull { hotspot ->
        val (cx, cy) = when (val sz = imageSize) {
            null -> hotspot.x to hotspot.y
            else -> imageCoordsToContainer(
                widthPx, heightPx, sz.first, sz.second,
                hotspot.x, hotspot.y
            ) ?: (hotspot.x to hotspot.y)
        }
        val hx = cx * widthPx
        val hy = cy * heightPx
        kotlin.math.sqrt((x - hx) * (x - hx) + (y - hy) * (y - hy))
    } ?: return null
    val (cx, cy) = when (val sz = imageSize) {
        null -> hit.x to hit.y
        else -> imageCoordsToContainer(
            widthPx, heightPx, sz.first, sz.second,
            hit.x, hit.y
        ) ?: (hit.x to hit.y)
    }
    val hx = cx * widthPx
    val hy = cy * heightPx
    val dist = kotlin.math.sqrt((x - hx) * (x - hx) + (y - hy) * (y - hy))
    return if (dist <= touchRadiusPx) hit else null
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun HeroHotspotsOverlay(
    hotspots: List<HeroHotspot>,
    imageSize: Pair<Int, Int>?,
    onHotspotClick: (HeroHotspot) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val touchRadiusPx = with(density) { (HERO_HOTSPOT_TOUCH_TARGET / 2).toPx() }
    var clickedHotspot by remember { mutableStateOf<HeroHotspot?>(null) }
    var consumedDownOnHotspot by remember { mutableStateOf<HeroHotspot?>(null) }
    var sizePx by remember { mutableStateOf(0f to 0f) }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { sizePx = it.width.toFloat() to it.height.toFloat() }
            .pointerInteropFilter { event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        val (w, h) = sizePx
                        val hit = findHotspotAt(event.x, event.y, w, h, hotspots, imageSize, touchRadiusPx)
                        if (hit != null) {
                            consumedDownOnHotspot = hit
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val was = consumedDownOnHotspot
                        consumedDownOnHotspot = null
                        if (was != null && event.action == MotionEvent.ACTION_UP) {
                            clickedHotspot = was
                            onHotspotClick(was)
                        }
                        was != null
                    }
                    else -> consumedDownOnHotspot != null
                }
            }
    ) {
        SideEffect {
            val w = with(density) { maxWidth.toPx() }
            val h = with(density) { maxHeight.toPx() }
            if (w > 0f && h > 0f) sizePx = w to h
        }
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

            HeroHotspotDot(
                contentDescription = hotspot.title,
                isJustClicked = clickedHotspot == hotspot,
                onClickAnimationDone = { clickedHotspot = null },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = xDp, y = yDp)
                    .size(HERO_HOTSPOT_TOUCH_TARGET)
            )
        }
    }
}
