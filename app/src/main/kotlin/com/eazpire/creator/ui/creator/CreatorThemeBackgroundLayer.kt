package com.eazpire.creator.ui.creator

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import com.eazpire.creator.R
import com.eazpire.creator.config.CreatorAreaBackground

private val ThemeBgOverlayGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0x660A0514),
        Color(0x9905020F),
    ),
)

private val ThemeBgBaseGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF0A0514),
        Color(0xFF05020F),
    ),
)

@Composable
fun CreatorThemeBackgroundLayer(
    background: CreatorAreaBackground?,
    videoEnabled: Boolean,
    resumeNonce: Int = 0,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when {
            background != null && background.isVideo && videoEnabled -> {
                CreatorThemeVideoBackground(
                    videoUrl = background.url!!,
                    posterUrl = background.posterUrl,
                    resumeNonce = resumeNonce,
                )
            }
            background != null && !background.imageUrl.isNullOrBlank() -> {
                AsyncImage(
                    model = background.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.85f,
                )
            }
            else -> {
                DefaultGalaxyNebulaBackground()
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ThemeBgOverlayGradient),
        )
    }
}

@Composable
private fun DefaultGalaxyNebulaBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ThemeBgBaseGradient),
    ) {
        Image(
            painter = painterResource(R.drawable.galaxy_nebula_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.85f,
        )
    }
}

@Composable
private fun CreatorThemeVideoBackground(
    videoUrl: String,
    posterUrl: String?,
    resumeNonce: Int,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var coverView by remember(videoUrl) { mutableStateOf<CreatorThemeCoverVideoView?>(null) }
    var videoFailed by remember(videoUrl) { mutableStateOf(false) }

    LaunchedEffect(resumeNonce, videoUrl) {
        coverView?.setShouldPlay(true)
        coverView?.resumeIfReady()
    }

    DisposableEffect(lifecycleOwner, videoUrl) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    coverView?.setShouldPlay(true)
                    coverView?.resumeIfReady()
                }
                Lifecycle.Event.ON_PAUSE -> coverView?.setShouldPlay(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            coverView?.release()
            coverView = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!posterUrl.isNullOrBlank()) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.85f,
            )
        }
        if (!videoFailed) {
            AndroidView(
                factory = { ctx ->
                    CreatorThemeCoverVideoView(ctx).also { view ->
                        coverView = view
                        view.onPlaybackError = { videoFailed = true }
                        view.setVideoUrl(videoUrl)
                        view.setShouldPlay(true)
                    }
                },
                update = { view ->
                    coverView = view
                    view.onPlaybackError = { videoFailed = true }
                    view.setVideoUrl(videoUrl)
                    view.setShouldPlay(true)
                },
                onRelease = { view ->
                    view.onPlaybackError = null
                    view.release()
                    if (coverView === view) coverView = null
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
