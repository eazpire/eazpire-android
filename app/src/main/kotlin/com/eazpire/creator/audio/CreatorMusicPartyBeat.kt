package com.eazpire.creator.audio

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class MusicPartyVisuals(
    val smoothBass: Float = 0f,
    val intensity: Float = 0f,
    val titleScale: Float = 1f,
    val eazyScale: Float = 1f,
    val eazyRotateDeg: Float = 0f,
    val activeDotIndex: Int = 0,
    val dotEnergies: List<Float> = emptyList(),
)

/** Beat-synced music party visuals — mirrors theme/assets/creator-audio-party.js */
class CreatorMusicPartyBeat {

    private val _visuals = MutableStateFlow(MusicPartyVisuals())
    val visuals: StateFlow<MusicPartyVisuals> = _visuals.asStateFlow()

    private var smoothBass = 0f
    private var prevBass = 0f
    private var beatPulse = 0f
    private var lastBeatMs = 0L
    private var lastTiltMs = 0L
    private var rotateDir = 1
    private var eazyTilt = 0f
    private var eazyTiltTarget = 0f
    private var dotEnergies = mutableListOf<Float>()
    private var activeDotIndex = 0
    private var dotWalkDir = 1
    private var dotCount = 0

    fun setDotCount(count: Int) {
        dotCount = count.coerceAtLeast(0)
        while (dotEnergies.size < dotCount) dotEnergies.add(0f)
        if (dotEnergies.size > dotCount) {
            dotEnergies = dotEnergies.take(dotCount).toMutableList()
        }
        if (activeDotIndex >= dotCount) activeDotIndex = 0
    }

    fun onBass(bass: Float) {
        val now = SystemClock.elapsedRealtime()
        smoothBass = smoothBass * 0.8f + bass * 0.2f
        val rise = bass - prevBass
        prevBass = bass

        val beatHit = rise >= BEAT_RISE_MIN &&
            bass >= BEAT_LEVEL_MIN &&
            (bass >= smoothBass * 1.003f || rise >= BEAT_RISE_MIN * 2f) &&
            now - lastBeatMs >= BEAT_MIN_MS

        val softBeat = !beatHit &&
            smoothBass > 0.03f &&
            now - lastBeatMs >= SOFT_BEAT_MS

        if (beatHit || softBeat) {
            triggerBeat(if (softBeat) maxOf(bass, smoothBass, 0.07f) else bass, now)
        }

        beatPulse *= 0.9f

        if (abs(eazyTiltTarget) > 0.01f) {
            eazyTilt += (eazyTiltTarget - eazyTilt) * 0.24f
            if (abs(eazyTilt - eazyTiltTarget) < 0.35f) {
                eazyTiltTarget = 0f
            }
        } else {
            eazyTilt *= 0.8f
        }

        for (i in dotEnergies.indices) {
            dotEnergies[i] *= 0.86f
            if (dotEnergies[i] < 0.02f) dotEnergies[i] = 0f
        }

        val livePulse = max(beatPulse, smoothBass * 0.62f)
        publish(livePulse)
    }

    fun reset() {
        smoothBass = 0f
        prevBass = 0f
        beatPulse = 0f
        lastBeatMs = 0L
        lastTiltMs = 0L
        rotateDir = 1
        eazyTilt = 0f
        eazyTiltTarget = 0f
        dotEnergies.clear()
        activeDotIndex = 0
        dotWalkDir = 1
        _visuals.value = MusicPartyVisuals()
    }

    private fun triggerBeat(bass: Float, now: Long) {
        lastBeatMs = now
        beatPulse = 0.28f + min(1f, bass) * 0.72f

        if (dotCount > 0) {
            while (dotEnergies.size < dotCount) dotEnergies.add(0f)
            for (i in 0 until dotCount) dotEnergies[i] = 0f
            dotEnergies[activeDotIndex] = 1f
            advanceDotWalker(dotCount)
        }

        if (now - lastTiltMs >= TILT_MIN_MS) {
            lastTiltMs = now
            rotateDir *= -1
            val baseTilt = 5f + min(1f, bass / 0.28f) * 11f
            val extra = if (bass >= STRONG_BASS_MIN) {
                min(1f, (bass - STRONG_BASS_MIN) / 0.35f) * 14f
            } else 0f
            eazyTiltTarget = (baseTilt + extra) * rotateDir
        }
    }

    private fun advanceDotWalker(count: Int) {
        if (count <= 1) return
        var next = activeDotIndex + dotWalkDir
        if (next >= count) {
            next = count - 2
            dotWalkDir = -1
        } else if (next < 0) {
            next = 1
            dotWalkDir = 1
        }
        activeDotIndex = next.coerceIn(0, count - 1)
    }

    private fun publish(pulse: Float) {
        val intensity = min(1f, max(smoothBass * 0.38f, pulse * 0.72f + smoothBass * 0.62f))
        val titleScale = 1f + intensity * 0.1f
        val eazyScale = 1f + intensity * 0.09f
        val eazyRotate = eazyTilt + pulse * (1.8f + smoothBass * 4.5f) + smoothBass * 3.5f
        _visuals.value = MusicPartyVisuals(
            smoothBass = smoothBass,
            intensity = intensity,
            titleScale = titleScale,
            eazyScale = eazyScale,
            eazyRotateDeg = eazyRotate,
            activeDotIndex = activeDotIndex,
            dotEnergies = dotEnergies.toList(),
        )
    }

    companion object {
        private const val BEAT_MIN_MS = 120L
        private const val SOFT_BEAT_MS = 360L
        private const val TILT_MIN_MS = 220L
        private const val BEAT_RISE_MIN = 0.008f
        private const val BEAT_LEVEL_MIN = 0.04f
        private const val STRONG_BASS_MIN = 0.16f
    }
}
