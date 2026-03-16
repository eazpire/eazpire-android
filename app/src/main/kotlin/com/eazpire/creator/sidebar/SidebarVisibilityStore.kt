package com.eazpire.creator.sidebar

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists which sidebar sections are hidden (eye toggle).
 * Keys: gutscheine, audience, home-decor, lifestyle, tech, etc.
 */
class SidebarVisibilityStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isSectionVisible(containerId: String): Boolean {
        return prefs.getBoolean(key(containerId), true)
    }

    fun setSectionVisible(containerId: String, visible: Boolean) {
        prefs.edit().putBoolean(key(containerId), visible).apply()
    }

    private fun key(id: String) = "visible_$id"

    companion object {
        private const val PREFS_NAME = "eazpire_sidebar"
    }
}
