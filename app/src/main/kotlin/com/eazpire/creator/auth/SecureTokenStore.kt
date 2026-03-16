package com.eazpire.creator.auth

import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Speichert JWT und Shopify access_token sicher via EncryptedSharedPreferences.
 * access_token wird für direkte Customer Account API GraphQL-Aufrufe benötigt
 * (Adressen, Payment Methods, Checkout-Prefill).
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

    /** Shopify Customer Account API access_token – für GraphQL (Adressen, Payment Methods). */
    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun saveJwt(jwt: String, ownerId: String) {
        prefs.edit()
            .putString(KEY_JWT, jwt)
            .putString(KEY_OWNER_ID, ownerId)
            .apply()
    }

    fun saveTokens(jwt: String, ownerId: String, accessToken: String?) {
        prefs.edit()
            .putString(KEY_JWT, jwt)
            .putString(KEY_OWNER_ID, ownerId)
            .putString(KEY_ACCESS_TOKEN, accessToken ?: "")
            .apply()
    }

    /**
     * Löscht alle Tokens synchron (commit statt apply), damit der nächste
     * isLoggedIn()-Check sofort false liefert – kein automatisches Re-Login.
     */
    fun clear() {
        prefs.edit().clear().commit()
    }

    fun isLoggedIn(): Boolean = !getJwt().isNullOrBlank()

    companion object {
        private const val KEY_JWT = "jwt"
        private const val KEY_OWNER_ID = "owner_id"
        private const val KEY_ACCESS_TOKEN = "shopify_access_token"

        /**
         * Löscht alle WebView-Cookies (Shopify OAuth, Cart, Checkout).
         * Nach Logout aufrufen, damit der nächste Login einen frischen OAuth-Flow startet.
         */
        fun clearAuthCookies() {
            val cm = CookieManager.getInstance()
            cm.removeAllCookies(null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                cm.flush()
            }
        }
    }
}
