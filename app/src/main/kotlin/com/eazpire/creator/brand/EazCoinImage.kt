package com.eazpire.creator.brand

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

private const val R2_COIN = "https://pub-2ffb11d4a361463498b9a842a87a870c.r2.dev/brand/coin"

@Composable
fun EazCoinImage(
    slot: String,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val repo = remember { BrandAssetsRepository.get(context) }
    val urls by repo.urls.collectAsState()
    LaunchedEffect(slot) { repo.refreshIfStale() }
    val fallback = when (slot) {
        BrandAssetSlots.EAZC_COIN_LOGO -> "$R2_COIN/eazc-coin-logo.png"
        else -> "$R2_COIN/eaz-coin-logo.png"
    }
    val url = urls[slot] ?: fallback
    AsyncImage(
        model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit,
    )
}
