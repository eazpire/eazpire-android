package com.eazpire.creator.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.delay

/** Matches CollectionScreen / ProductCarousel: preload, optional auto-rotate, AsyncImage + ImageRequest.crossfade(0). */
private const val SHOP_STYLE_IMAGE_ROTATE_MS = 1800L

/** From this width (dp) show left/right arrows instead of swipe for manual variant navigation. */
private const val VARIANT_NAV_ARROWS_MIN_WIDTH_DP = 840

@Composable
fun ShopStyleProductImages(
    imageUrls: List<String>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    cornerRadius: Dp = 0.dp,
    rotateIntervalMs: Long = SHOP_STYLE_IMAGE_ROTATE_MS,
    /** When true, cycles through URLs on a timer (ignored if [manualVariantNavigation] is true). */
    autoRotate: Boolean = false,
    /**
     * When true, no auto-rotation: narrow screens use horizontal swipe; wide screens use arrow buttons.
     * Use in hero/video product picker and selected-product previews.
     */
    manualVariantNavigation: Boolean = false
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val urls = remember(imageUrls) {
        imageUrls.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }
    var currentIndex by remember { mutableStateOf(0) }

    LaunchedEffect(urls) {
        currentIndex = 0
        urls.forEach { u ->
            context.imageLoader.enqueue(ImageRequest.Builder(context).data(u).build())
        }
    }

    val useAutoRotate = autoRotate && !manualVariantNavigation && urls.size > 1
    LaunchedEffect(useAutoRotate, urls.size, rotateIntervalMs) {
        if (!useAutoRotate) return@LaunchedEffect
        while (true) {
            delay(rotateIntervalMs)
            currentIndex = (currentIndex + 1) % urls.size
        }
    }

    val showArrows =
        manualVariantNavigation && urls.size > 1 && screenWidthDp >= VARIANT_NAV_ARROWS_MIN_WIDTH_DP
    val useSwipe =
        manualVariantNavigation && urls.size > 1 && screenWidthDp < VARIANT_NAV_ARROWS_MIN_WIDTH_DP

    val fadeMs = if (manualVariantNavigation) 350 else 1200

    if (urls.isEmpty()) {
        Box(modifier = modifier.fillMaxSize())
        return
    }

    val baseMod = (if (cornerRadius > 0.dp) modifier.clip(RoundedCornerShape(cornerRadius)) else modifier)
        .fillMaxSize()

    val swipeMod = if (useSwipe) {
        Modifier.pointerInput(urls.size) {
            var totalDrag = 0f
            val thresholdPx = 48f * density.density
            detectHorizontalDragGestures(
                onDragStart = { totalDrag = 0f },
                onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                onDragEnd = {
                    when {
                        totalDrag > thresholdPx && currentIndex > 0 -> currentIndex--
                        totalDrag < -thresholdPx && currentIndex < urls.size - 1 -> currentIndex++
                    }
                }
            )
        }
    } else {
        Modifier
    }

    Box(modifier = baseMod.then(swipeMod)) {
        urls.forEachIndexed { index, url ->
            val isActive = index == currentIndex
            val alpha by animateFloatAsState(
                targetValue = if (isActive) 1f else 0f,
                animationSpec = tween(durationMillis = fadeMs, easing = FastOutSlowInEasing),
                label = "shopStyleImg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = alpha)
                    .zIndex(if (isActive) 1f else 0f)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(url)
                        .crossfade(0)
                        .build(),
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = contentScale
                )
            }
        }

        if (showArrows) {
            IconButton(
                onClick = { if (currentIndex > 0) currentIndex-- },
                enabled = currentIndex > 0,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .zIndex(4f)
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(
                    Icons.Default.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(
                onClick = { if (currentIndex < urls.size - 1) currentIndex++ },
                enabled = currentIndex < urls.size - 1,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .zIndex(4f)
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
