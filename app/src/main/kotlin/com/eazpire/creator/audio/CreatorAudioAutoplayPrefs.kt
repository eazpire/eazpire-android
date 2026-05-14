package com.eazpire.creator.audio

import android.content.Context

/** Remember per owner that Creator audio must not autoplay until the user taps play again. */
class CreatorAudioAutoplayPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isAutoplaySuppressed(ownerId: String): Boolean =
        ownerId.isNotBlank() && prefs.getBoolean(key(ownerId), false)

    fun setSuppressed(ownerId: String, suppressed: Boolean) {
        if (ownerId.isBlank()) return
        prefs.edit().putBoolean(key(ownerId), suppressed).apply()
    }

    private fun key(ownerId: String): String = "suppress_autoplay_$ownerId"

    companion object {
        private const val PREFS = "eaz_creator_audio"
    }
}
