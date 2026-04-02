package com.eazpire.creator.auth

import android.content.Context

/**
 * Persists PKCE [state] + [codeVerifier] while the user completes OAuth in a browser tab.
 * Survives process death; required so [shop.*://callback] can exchange the code after Custom Tab.
 */
object OAuthPkceStore {
    private const val PREFS = "oauth_pkce_pending"
    private const val KEY_STATE = "state"
    private const val KEY_VERIFIER = "code_verifier"

    fun save(context: Context, state: String, codeVerifier: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_STATE, state)
            .putString(KEY_VERIFIER, codeVerifier)
            .apply()
    }

    /**
     * Returns [codeVerifier] and clears prefs if [stateFromCallback] matches the stored state.
     */
    fun consume(context: Context, stateFromCallback: String): String? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_STATE, null) ?: return null
        val verifier = prefs.getString(KEY_VERIFIER, null) ?: return null
        if (stored != stateFromCallback) return null
        prefs.edit().clear().apply()
        return verifier
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
