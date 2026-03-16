package com.eazpire.creator.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val HERO_AUTO_ADVANCE_MS = 3500L
private const val HERO_ASPECT_RATIO = 2f / 3f

data class HeroImage(
    val id: String,
    val imageUrl: String,
    val thumbnailUrl: String?,
    val title: String?,
    val link: String?
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroCarousel(
    modifier: Modifier = Modifier
) {
    val api = remember { CreatorApi() }
    var heroImages by remember { mutableStateOf<List<HeroImage>>(emptyList()) }

    LaunchedEffect(Unit) {
        heroImages = withContext(Dispatchers.IO) {
            try {
                val json = api.getHeroPublishedRandom(limit = 6)
                if (json.optBoolean("ok", false)) {
                    val arr = json.optJSONArray("images") ?: json.optJSONArray("items")
                    if (arr != null) {
                        (0 until arr.length()).map { i ->
                            val obj = arr.getJSONObject(i)
                            HeroImage(
                                id = obj.optString("id", ""),
                                imageUrl = obj.optString("image_url", "").takeIf { it.isNotBlank() }
                                    ?: obj.optString("thumbnail_url", ""),
                                thumbnailUrl = obj.optString("thumbnail_url", "").takeIf { it.isNotBlank() },
                                title = obj.optString("title", "").takeIf { it.isNotBlank() },
                                link = null
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

    LaunchedEffect(heroImages.size) {
        if (heroImages.size < 2) return@LaunchedEffect
        while (true) {
            delay(HERO_AUTO_ADVANCE_MS)
            val next = (pagerState.currentPage + 1) % heroImages.size
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
                                        androidx.compose.ui.graphics.Color.Transparent,
                                        androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.4f)
                                    ),
                                    startY = 0f,
                                    endY = 1000f
                                )
                            )
                    )
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
