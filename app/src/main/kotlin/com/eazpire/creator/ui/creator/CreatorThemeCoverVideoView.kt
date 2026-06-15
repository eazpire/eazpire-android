package com.eazpire.creator.ui.creator

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Full-bleed muted loop video — matches web `.creator-theme-bg-video { object-fit: cover }`.
 * Uses ExoPlayer (Media3) for reliable remote MP4 playback in Compose.
 */
class CreatorThemeCoverVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {

    private val playerView = PlayerView(context).apply {
        useController = false
        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
        setKeepContentOnPlayerReset(true)
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
    }

    private var player: ExoPlayer? = null
    private var currentUrl: String? = null
    private var shouldPlay = true

    var onPlaybackError: (() -> Unit)? = null
    var onPlaybackReady: (() -> Unit)? = null

    init {
        clipChildren = true
        clipToPadding = true
        addView(playerView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setVideoUrl(url: String) {
        if (currentUrl == url && player != null) return
        currentUrl = url
        releasePlayerInternal()
        val exo = ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = shouldPlay
            setMediaItem(MediaItem.fromUri(url))
            addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            onPlaybackReady?.invoke()
                            if (shouldPlay && !isPlaying) {
                                play()
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        onPlaybackError?.invoke()
                    }
                },
            )
            prepare()
        }
        player = exo
        playerView.player = exo
    }

    fun setShouldPlay(play: Boolean) {
        shouldPlay = play
        player?.playWhenReady = play
    }

    fun resumeIfReady() {
        if (!shouldPlay) return
        player?.playWhenReady = true
    }

    fun release() {
        shouldPlay = false
        releasePlayerInternal()
        currentUrl = null
    }

    private fun releasePlayerInternal() {
        playerView.player = null
        player?.release()
        player = null
    }
}
