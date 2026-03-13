package com.eazpire.creator.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Speichert JWT sicher via EncryptedSharedPreferences.
 */
class SecureTokenStore(context: Context) {
    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "eazpire_auth_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun getJwt(): String? = prefs.getString(KEY_JWT, null)
    fun getOwnerId(): String? = prefs.getString(KEY_OWNER_ID, null)

    fun saveJwt(jwt: String, ownerId: String) {
        prefs.edit()
            .putString(KEY_JWT, jwt)
            .putString(KEY_OWNER_ID, ownerId)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun isLoggedIn(): Boolean = !getJwt().isNullOrBlank()

    companion object {
        private const val KEY_JWT = "jwt"
        private const val KEY_OWNER_ID = "owner_id"
    }
}
