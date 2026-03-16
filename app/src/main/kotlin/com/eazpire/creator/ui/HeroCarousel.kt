package com.eazpire.creator.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.locale.LocaleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val HERO_AUTO_ADVANCE_MS = 3500L
private const val HERO_ASPECT_RATIO = 2f / 3f
private val HERO_HOTSPOT_SIZE = 14.dp
private val HERO_HOTSPOT_RING = 3.dp
private const val STORE_BASE_URL = "https://www.eazpire.com"

data class HeroHotspot(
    val x: Float,
    val y: Float,
    val url: String?,
    val title: String?
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
                result.add(HeroHotspot(x = x, y = y, url = url, title = title))
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
                            result.add(HeroHotspot(x = x, y = y, url = url, title = productTitle))
                        }
                    }
                }
            }
        }
    } catch (_: Exception) { }
    return result
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroCarousel(
    onProductClick: ((String) -> Unit)? = null,
    onHotspotProductClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val api = remember { CreatorApi() }
    val localeStore = remember { LocaleStore(context) }
    val heroRegion by localeStore.regionCode.collectAsState(initial = localeStore.getRegionCodeSync())
    var heroImages by remember { mutableStateOf<List<HeroImage>>(emptyList()) }

    LaunchedEffect(heroRegion) {
        heroImages = withContext(Dispatchers.IO) {
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
                                hotspots = hotspots
                            )
                        }.filter { it.imageUrl.isNotBlank() }
                    } else emptyList()
                } else emptyList()
            } catch (_: Exception) {
                emptyList()
            }
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(HERO_ASPECT_RATIO)
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
                    if (hero.hotspots.isNotEmpty()) {
                        HeroHotspotsOverlay(
                            hotspots = hero.hotspots,
                            onHotspotClick = { url ->
                                if (url != null) {
                                    val handle = when {
                                        url.startsWith("/products/") -> url.removePrefix("/products/").trimEnd('/')
                                        url.contains("/products/") -> {
                                            val idx = url.indexOf("/products/") + "/products/".length
                                            url.substring(idx).trimEnd('/').substringBefore('?').substringBefore('#')
                                        }
                                        else -> null
                                    }
                                    if (handle != null && handle.isNotBlank()) {
                                        when {
                                            onHotspotProductClick != null -> onHotspotProductClick(handle)
                                            else -> onProductClick?.invoke(handle)
                                        }
                                    } else {
                                        val fullUrl = if (url.startsWith("http")) url else "$STORE_BASE_URL$url"
                                        try {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fullUrl)))
                                        } catch (_: Exception) { }
                                    }
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
private fun HeroHotspotsOverlay(
    hotspots: List<HeroHotspot>,
    onHotspotClick: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val halfSize = HERO_HOTSPOT_SIZE.value / 2f
        hotspots.forEach { hotspot ->
            val xDp = (hotspot.x * maxWidth.value - halfSize).dp
            val yDp = (hotspot.y * maxHeight.value - halfSize).dp

            HeroHotspotDot(
                onClick = { onHotspotClick(hotspot.url) },
                contentDescription = hotspot.title,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = xDp, y = yDp)
                    .size(HERO_HOTSPOT_SIZE)
            )
        }
    }
}

@Composable
private fun HeroHotspotDot(
    onClick: () -> Unit,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "hotspotPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    Box(
        modifier = modifier
            .drawBehind {
                val ringPx = with(density) { HERO_HOTSPOT_RING.toPx() }
                drawCircle(
                    color = EazColors.Orange.copy(alpha = 0.4f),
                    radius = size.minDimension / 2 + ringPx,
                    center = center
                )
            }
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.9f))
            .border(2.dp, Color.White, CircleShape)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
                .drawBehind {
                    drawCircle(
                        color = Color.White.copy(alpha = pulseAlpha * 0.5f),
                        radius = size.minDimension / 2,
                        center = center
                    )
                }
        )
    }
}
