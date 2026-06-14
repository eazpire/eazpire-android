package com.eazpire.creator.ui.creator

import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.util.AttributeSet
import android.view.Surface
import android.view.TextureView
import android.widget.FrameLayout

/**
 * Full-bleed muted loop video — matches web `.creator-theme-bg-video { object-fit: cover }`.
 */
class CreatorThemeCoverVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val textureView = TextureView(context)
    private var mediaPlayer: MediaPlayer? = null
    private var videoUri: Uri? = null
    private var videoWidth = 0
    private var videoHeight = 0
    private var surfaceReady = false
    private var shouldPlay = true

    init {
        clipChildren = true
        clipToPadding = true
        addView(textureView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                surfaceReady = true
                bindPlayer(surface)
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                applyCoverTransform()
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                surfaceReady = false
                releasePlayer()
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }
    }

    fun setVideoUrl(url: String) {
        val uri = Uri.parse(url)
        if (videoUri == uri) return
        videoUri = uri
        videoWidth = 0
        videoHeight = 0
        releasePlayer()
        if (surfaceReady) {
            textureView.surfaceTexture?.let { bindPlayer(it) }
        }
    }

    fun setShouldPlay(play: Boolean) {
        shouldPlay = play
        if (!play) {
            try {
                mediaPlayer?.pause()
            } catch (_: Exception) {
            }
        } else {
            resumeIfReady()
        }
    }

    fun resumeIfReady() {
        if (!shouldPlay) return
        try {
            mediaPlayer?.let { mp ->
                if (!mp.isPlaying) mp.start()
            }
        } catch (_: Exception) {
        }
    }

    fun release() {
        shouldPlay = false
        releasePlayer()
        videoUri = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        applyCoverTransform()
    }

    private fun bindPlayer(surfaceTexture: SurfaceTexture) {
        val uri = videoUri ?: return
        releasePlayer()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(context, uri)
            setSurface(Surface(surfaceTexture))
            isLooping = true
            setVolume(0f, 0f)
            setOnVideoSizeChangedListener { _, w, h ->
                this@CreatorThemeCoverVideoView.videoWidth = w
                this@CreatorThemeCoverVideoView.videoHeight = h
                applyCoverTransform()
            }
            setOnPreparedListener { mp ->
                this@CreatorThemeCoverVideoView.videoWidth = mp.videoWidth
                this@CreatorThemeCoverVideoView.videoHeight = mp.videoHeight
                applyCoverTransform()
                if (shouldPlay) {
                    try {
                        mp.start()
                    } catch (_: Exception) {
                    }
                }
            }
            prepareAsync()
        }
    }

    /** object-fit: cover — same as web `object-fit: cover` / ScaleToFit.FILL */
    private fun applyCoverTransform() {
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        if (viewW <= 0f || viewH <= 0f || videoWidth <= 0 || videoHeight <= 0) return

        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewW, viewH)
        val videoRect = RectF(0f, 0f, videoWidth.toFloat(), videoHeight.toFloat())
        matrix.setRectToRect(videoRect, viewRect, Matrix.ScaleToFit.FILL)
        textureView.setTransform(matrix)
    }

    private fun releasePlayer() {
        try {
            mediaPlayer?.stop()
        } catch (_: Exception) {
        }
        try {
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
    }
}
