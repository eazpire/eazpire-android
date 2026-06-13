package com.eazpire.creator.chat

import android.content.Context

/** Persists "don't show connect tutorial again" (matches web localStorage key semantics). */
object EazyConnectTutorialPrefs {
    private const val PREFS = "eazy_connect_tutorial"
    private const val KEY_DISMISSED = "eazy_connect_tutorial_dismissed"

    fun isDismissed(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_DISMISSED, false)

    fun setDismissed(context: Context, dismissed: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DISMISSED, dismissed)
            .apply()
    }
}
