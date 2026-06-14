package com.eazpire.creator.ui.creator

import android.net.Uri
import android.widget.VideoView
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
import androidx.compose.ui.platform.LocalContext
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
    resumeNonce: Int,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var videoView by remember { mutableStateOf<VideoView?>(null) }

    fun resumeVideo() {
        val vv = videoView ?: return
        try {
            if (!vv.isPlaying) vv.start()
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(resumeNonce, videoUrl) {
        resumeVideo()
    }

    DisposableEffect(lifecycleOwner, videoUrl) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> resumeVideo()
                Lifecycle.Event.ON_PAUSE -> {
                    try {
                        videoView?.pause()
                    } catch (_: Exception) {
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                videoView?.stopPlayback()
            } catch (_: Exception) {
            }
            videoView = null
        }
    }

    AndroidView(
        factory = { ctx ->
            VideoView(ctx).apply {
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    mp.setVolume(0f, 0f)
                    try {
                        start()
                    } catch (_: Exception) {
                    }
                }
                setVideoURI(Uri.parse(videoUrl))
                videoView = this
            }
        },
        update = { vv ->
            if (vv.tag != videoUrl) {
                vv.tag = videoUrl
                vv.setVideoURI(Uri.parse(videoUrl))
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
