package com.eazpire.creator.chat

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/** Lightweight sine synth for Simon Says pad tones. */
class EazySimonSynth(
    padFreqs: List<Double>,
    private val instrument: String = "piano",
) {
    private val freqs = padFreqs.ifEmpty { DEFAULT_FREQS }

    fun playPad(index: Int) {
        val freq = freqs.getOrElse(index) { 440.0 }
        playTone(freq, if (instrument == "bells") 0.2 else 0.14)
    }

    fun playWrong() {
        playTone(140.0, 0.18)
    }

    fun playWin() {
        playTone(523.0, 0.1)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ playTone(784.0, 0.12) }, 90)
    }

    private fun playTone(freqHz: Double, durationSec: Double) {
        val sampleRate = 44100
        val numSamples = (sampleRate * durationSec).toInt().coerceAtLeast(1)
        val buf = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val env =
                when {
                    t < 0.015 -> t / 0.015
                    t > durationSec - 0.04 -> (durationSec - t) / 0.04
                    else -> 1.0
                }.coerceIn(0.0, 1.0)
            val wave =
                when (instrument) {
                    "marimba", "xylophone", "harp" ->
                        sin(2 * PI * freqHz * t) * 0.85 + sin(2 * PI * freqHz * 2 * t) * 0.15
                    "bells" -> sin(2 * PI * freqHz * t) + sin(2 * PI * freqHz * 2.5 * t) * 0.35
                    "organ", "synth" ->
                        sin(2 * PI * freqHz * t) + sin(2 * PI * freqHz * 2 * t) * 0.45
                    else -> sin(2 * PI * freqHz * t)
                }
            buf[i] = (wave * env * 8000).toInt().coerceIn(-32767, 32767).toShort()
        }
        val track =
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(buf.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        track.write(buf, 0, buf.size)
        track.play()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                track.stop()
                track.release()
            } catch (_: Exception) {
            }
        }, (durationSec * 1000).toLong() + 40)
    }

    companion object {
        private val DEFAULT_FREQS =
            listOf(262.0, 294.0, 330.0, 349.0, 392.0, 440.0, 494.0, 523.0, 587.0)

        fun fromGameMeta(meta: SimonGameMetaUi?): EazySimonSynth =
            EazySimonSynth(
                padFreqs = meta?.padFreqs ?: DEFAULT_FREQS,
                instrument = meta?.instrument ?: "piano",
            )
    }
}

data class SimonGameMetaUi(
    val melodyId: String,
    val instrument: String,
    val colors: List<String>,
    val padImages: List<String>,
    val padFreqs: List<Double>,
)
