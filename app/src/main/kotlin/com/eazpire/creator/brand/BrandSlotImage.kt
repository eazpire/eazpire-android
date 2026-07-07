package com.eazpire.creator.brand

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import coil.request.ImageRequest

@Composable
fun BrandSlotImage(
    slot: String,
    @DrawableRes fallbackResId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    /** Wide logo URL before manifest loads (matches web header asset). */
    fallbackUrl: String? = null,
) {
    val context = LocalContext.current
    val repo = remember { BrandAssetsRepository.get(context) }
    val urls by repo.urls.collectAsState()
    LaunchedEffect(slot) { repo.refreshIfStale() }
    val remoteUrl = urls[slot]?.takeIf { it.isNotBlank() } ?: fallbackUrl?.takeIf { it.isNotBlank() }
    if (!remoteUrl.isNullOrBlank()) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(remoteUrl).crossfade(true).build(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    } else {
        Image(
            painter = painterResource(fallbackResId),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}
