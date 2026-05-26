package com.eazpire.creator.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
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
import coil.request.ImageRequest
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

@Composable
fun EazProductCardRotatingImages(
    imageUrls: List<String>,
    productId: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    rotateIntervalMs: Long = 1800L,
    autoRotate: Boolean = true,
) {
    val urls = remember(imageUrls) { imageUrls.filter { it.isNotBlank() }.distinct() }
    var currentIndex by remember(productId, urls.size) {
        mutableIntStateOf(plpRotationStartIndex(productId, urls.size.coerceAtLeast(1)))
    }

    LaunchedEffect(productId, urls.size, autoRotate) {
        if (!autoRotate || urls.size <= 1) return@LaunchedEffect
        delay(plpRotationJitterMs(productId))
        while (true) {
            delay(rotateIntervalMs)
            currentIndex = (currentIndex + 1) % urls.size
        }
    }

    Box(modifier = modifier) {
        if (urls.isEmpty()) return@Box
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(alpha = alpha)
                    .zIndex(if (isActive) 1f else 0f)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
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
                        tint = if (isTryOnActive) Color.White else Color(0xFF1A1A1A)
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
