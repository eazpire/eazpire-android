package com.eazpire.creator.audio

import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class CreatorAudioItem(
    val id: String,
    val title: String,
    val url: String,
    val durationSec: Int,
    val ownerId: String?,
    val coverUrl: String? = null
)

/** State store for Creator Audio – list, playback, volume, selection */
class CreatorAudioStore {
    val list = MutableStateFlow<List<CreatorAudioItem>>(emptyList())
    val selectedId = MutableStateFlow<String?>(null)
    val isPlaying = MutableStateFlow(false)
    val volume = MutableStateFlow(1f)
    val muted = MutableStateFlow(false)
    val currentPlaybackId = MutableStateFlow<String?>(null)
    val currentPositionSec = MutableStateFlow(0)
    val isLoading = MutableStateFlow(false)
    val loadError = MutableStateFlow<String?>(null)

    private var mediaPlayer: MediaPlayer? = null

    fun getItem(id: String): CreatorAudioItem? = list.value.find { it.id == id }

    fun select(id: String?) {
        selectedId.value = id
    }

    fun setVolume(v: Float) {
        volume.value = v.coerceIn(0f, 1f)
        mediaPlayer?.setVolume(if (muted.value) 0f else volume.value, volume.value)
    }

    fun setMuted(m: Boolean) {
        muted.value = m
        mediaPlayer?.setVolume(if (m) 0f else volume.value, volume.value)
    }

    fun toggleMute() = setMuted(!muted.value)

    fun play(item: CreatorAudioItem) {
        stop()
        try {
            val mp = MediaPlayer().apply {
                setDataSource(item.url)
                setVolume(if (muted.value) 0f else volume.value, volume.value)
                isLooping = true
                setOnPreparedListener {
                    start()
                    this@CreatorAudioStore.currentPlaybackId.value = item.id
                    this@CreatorAudioStore.isPlaying.value = true
                }
                setOnCompletionListener { }
                prepareAsync()
            }
            mediaPlayer = mp
        } catch (_: Exception) {
            loadError.value = "Playback failed"
        }
    }

    fun pause() {
        mediaPlayer?.pause()
        isPlaying.value = false
    }

    fun resume() {
        mediaPlayer?.start()
        isPlaying.value = true
    }

    fun togglePlay(item: CreatorAudioItem) {
        if (currentPlaybackId.value == item.id) {
            if (isPlaying.value) pause() else resume()
        } else {
            play(item)
        }
    }

    fun seekBack() {
        val mp = mediaPlayer ?: return
        val pos = mp.currentPosition - 10000
        mp.seekTo((pos).coerceAtLeast(0))
    }

    fun seekForward() {
        val mp = mediaPlayer ?: return
        val pos = mp.currentPosition + 10000
        val dur = mp.duration
        mp.seekTo(if (dur > 0) pos.coerceAtMost(dur) else pos)
    }

    fun stop() {
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null
        currentPlaybackId.value = null
        isPlaying.value = false
        currentPositionSec.value = 0
    }

    fun release() = stop()

    companion object {
        fun parseItem(obj: JSONObject): CreatorAudioItem? {
            val id = obj.optString("id", "").ifBlank { return null }
            val url = obj.optString("url", "").ifBlank { return null }
            return CreatorAudioItem(
                id = id,
                title = obj.optString("title", "Untitled"),
                url = url,
                durationSec = obj.optInt("duration_sec", 0),
                ownerId = obj.optString("owner_id", "").takeIf { it.isNotBlank() },
                coverUrl = obj.optString("cover_url", "").takeIf { it.isNotBlank() }
            )
        }
    }
}
