package com.eazpire.creator.sidebar

import android.content.Context
import android.content.SharedPreferences

enum class SidebarViewMode { Grid, List }

/**
 * Persists sidebar view mode (grid vs list).
 * Analog to localStorage.eaz_sidebar_view in web.
 */
class SidebarViewStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getViewMode(): SidebarViewMode {
        val value = prefs.getString(KEY_VIEW, "grid") ?: "grid"
        return when (value) {
            "list" -> SidebarViewMode.List
            else -> SidebarViewMode.Grid
        }
    }

    fun setViewMode(mode: SidebarViewMode) {
        prefs.edit().putString(KEY_VIEW, mode.name.lowercase()).apply()
    }

    companion object {
        private const val PREFS_NAME = "eazpire_sidebar"
        private const val KEY_VIEW = "view_mode"
    }
}
