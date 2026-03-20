package com.eazpire.creator.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.delay

/** Matches CollectionScreen / ProductCarousel: preload, rotate variant URLs, AsyncImage + ImageRequest.crossfade(0). */
private const val SHOP_STYLE_IMAGE_ROTATE_MS = 1800L

@Composable
fun ShopStyleProductImages(
    imageUrls: List<String>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    cornerRadius: Dp = 0.dp,
    rotateIntervalMs: Long = SHOP_STYLE_IMAGE_ROTATE_MS
) {
    val context = LocalContext.current
    val urls = remember(imageUrls) {
        imageUrls.map { it.trim() }.filter { it.isNotBlank() }.distinct()
    }
    var currentIndex by remember(urls) { mutableStateOf(0) }

    LaunchedEffect(urls) {
        urls.forEach { u ->
            context.imageLoader.enqueue(ImageRequest.Builder(context).data(u).build())
        }
    }

    LaunchedEffect(urls.size, currentIndex) {
        if (urls.size <= 1) return@LaunchedEffect
        while (true) {
            delay(rotateIntervalMs)
            currentIndex = (currentIndex + 1) % urls.size
        }
    }

    if (urls.isEmpty()) {
        Box(modifier = modifier.fillMaxSize())
        return
    }

    val clipped = (if (cornerRadius > 0.dp) modifier.clip(RoundedCornerShape(cornerRadius)) else modifier)
        .fillMaxSize()
    Box(modifier = clipped) {
        urls.forEachIndexed { index, url ->
            val isActive = index == currentIndex
            val alpha by animateFloatAsState(
                targetValue = if (isActive) 1f else 0f,
                animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
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
    }
}
