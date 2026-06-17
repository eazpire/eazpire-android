package com.eazpire.creator.audio

import android.content.Context
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
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
class CreatorAudioStore(context: Context) {

    private val autoplayPrefs = CreatorAudioAutoplayPrefs(context.applicationContext)

    @Volatile
    private var prefsOwnerId: String = ""

    /** For persisted “no autoplay after user pause”; call when owner/session is known. */
    fun bindOwner(ownerId: String) {
        prefsOwnerId = ownerId.ifBlank { "" }
    }

    val list = MutableStateFlow<List<CreatorAudioItem>>(emptyList())
    val selectedId = MutableStateFlow<String?>(null)
    val isPlaying = MutableStateFlow(false)
    val volume = MutableStateFlow(1f)
    val muted = MutableStateFlow(false)
    val currentPlaybackId = MutableStateFlow<String?>(null)
    /** Aktuell abgespieltes Item (auch wenn nicht in list, z.B. Auto-Play) */
    val currentPlaybackItem = MutableStateFlow<CreatorAudioItem?>(null)
    val currentPositionSec = MutableStateFlow(0)
    val isLoading = MutableStateFlow(false)
    val loadError = MutableStateFlow<String?>(null)

    /** Echte Audio-Levels vom Visualizer (16 Balken, 0f–1f), leer wenn nicht verfügbar */
    val visualizerLevels = MutableStateFlow<List<Float>>(emptyList())

    /** Bass-Energie 0f–1f für UI-Reaktionen */
    val bassLevel = MutableStateFlow(0f)

    private var mediaPlayer: MediaPlayer? = null
    private var visualizer: Visualizer? = null
    @Volatile
    private var appIsActive: Boolean = true

    fun getItem(id: String): CreatorAudioItem? = list.value.find { it.id == id }

    /** Aktives Track-Item: Playback, Auswahl oder Remote-Bootstrap. */
    fun resolvePlaybackItem(): CreatorAudioItem? {
        currentPlaybackItem.value?.takeIf { it.url.isNotBlank() }?.let { return it }
        currentPlaybackId.value?.let { getItem(it) }?.let { return it }
        selectedId.value?.let { getItem(it) }?.let { return it }
        return null
    }

    fun hasResolvableAudio(): Boolean = resolvePlaybackItem() != null

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

    fun setAppActive(active: Boolean) {
        appIsActive = active
        if (!active) {
            stop()
        }
    }

    /** Remote-track selection without opening MediaPlayer (next app launch after user paused). */
    fun armRemoteTrack(item: CreatorAudioItem) {
        stop()
        selectedId.value = item.id
        currentPlaybackItem.value = item
        currentPlaybackId.value = null
        isPlaying.value = false
        loadError.value = null
    }

    fun play(item: CreatorAudioItem, fromSessionBootstrap: Boolean = false) {
        if (!fromSessionBootstrap && prefsOwnerId.isNotBlank()) {
            autoplayPrefs.setSuppressed(prefsOwnerId, false)
        }
        if (!appIsActive) return
        stop()
        try {
            val mp = MediaPlayer().apply {
                setDataSource(item.url)
                setVolume(if (muted.value) 0f else volume.value, volume.value)
                isLooping = true
                setOnPreparedListener {
                    if (!appIsActive) {
                        try {
                            stop()
                            release()
                        } catch (_: Exception) {}
                        return@setOnPreparedListener
                    }
                    start()
                    this@CreatorAudioStore.currentPlaybackId.value = item.id
                    this@CreatorAudioStore.currentPlaybackItem.value = item
                    this@CreatorAudioStore.isPlaying.value = true
                    attachVisualizer(this)
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
        val hadPlayer = mediaPlayer != null
        mediaPlayer?.pause()
        isPlaying.value = false
        if (hadPlayer && prefsOwnerId.isNotBlank()) {
            autoplayPrefs.setSuppressed(prefsOwnerId, true)
        }
    }

    fun resume() {
        if (!appIsActive) return
        val mp = mediaPlayer ?: run {
            resolvePlaybackItem()?.let { play(it) }
            return
        }
        if (prefsOwnerId.isNotBlank()) {
            autoplayPrefs.setSuppressed(prefsOwnerId, false)
        }
        mp.start()
        isPlaying.value = true
    }

    fun togglePlay(item: CreatorAudioItem?) {
        if (item == null) return
        val mp = mediaPlayer
        if (currentPlaybackId.value == item.id && mp != null) {
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
        releaseVisualizer()
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null
        currentPlaybackId.value = null
        isPlaying.value = false
        currentPositionSec.value = 0
        visualizerLevels.value = emptyList()
        bassLevel.value = 0f
    }

    private fun attachVisualizer(mp: MediaPlayer) {
        releaseVisualizer()
        try {
            val sessionId = mp.audioSessionId
            if (sessionId == 0) return
            val range = Visualizer.getCaptureSizeRange()
            val captureSize = range[0]
            val store = this@CreatorAudioStore
            val viz = Visualizer(sessionId).apply {
                this.captureSize = captureSize
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        if (waveform != null) {
                            store.visualizerLevels.value = store.waveformToBars(waveform, 16)
                        }
                    }
                    override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft != null && fft.size >= 4) {
                            store.bassLevel.value = store.fftToBassLevel(fft)
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, true)
                enabled = true
            }
            visualizer = viz
        } catch (_: Exception) {
            visualizerLevels.value = emptyList()
            bassLevel.value = 0f
        }
    }

    private fun releaseVisualizer() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Exception) {}
        visualizer = null
    }

    private fun fftToBassLevel(fft: ByteArray): Float {
        var sum = 0f
        var count = 0
        val end = minOf(8, fft.size / 2)
        for (i in 0 until end) {
            val idx = i * 2
            if (idx + 1 >= fft.size) break
            val re = fft[idx].toInt()
            val im = fft[idx + 1].toInt()
            val mag = kotlin.math.sqrt((re * re + im * im).toFloat())
            sum += mag
            count++
        }
        if (count == 0) return 0f
        return (sum / count / 128f).coerceIn(0f, 1f)
    }

    private fun waveformToBars(waveform: ByteArray, barCount: Int): List<Float> {
        if (waveform.isEmpty()) return List(barCount) { 0.3f }
        val groupSize = waveform.size / barCount
        if (groupSize < 1) return List(barCount) { 0.3f }
        return (0 until barCount).map { i ->
            val start = i * groupSize
            val end = (start + groupSize).coerceAtMost(waveform.size)
            var sum = 0f
            for (j in start until end) {
                val b = waveform[j].toInt() and 0xFF
                sum += kotlin.math.abs(b - 128) / 128f
            }
            (sum / (end - start)).coerceIn(0.1f, 1f)
        }
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
