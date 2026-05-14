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

    /** OAuth refresh_token – verlängert die Session ohne erneuten Browser-Login. */
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun saveJwt(jwt: String, ownerId: String) {
        prefs.edit()
            .putString(KEY_JWT, jwt)
            .putString(KEY_OWNER_ID, ownerId)
            .apply()
    }

    /**
     * @param shopifyAccessExpiresAtEpochMs Absolutzeit (ms), zu der der Shopify OAuth access_token abläuft.
     * Aus [expires_in] der Token-Response: `System.currentTimeMillis() + expiresIn * 1000`.
     * @param sync Wenn true: [commit] statt [apply], damit [isLoggedIn] und UI im nächsten Frame korrekt sind.
     */
    fun saveTokens(
        jwt: String,
        ownerId: String,
        accessToken: String?,
        shopifyAccessExpiresAtEpochMs: Long? = null,
        refreshToken: String? = null,
        clearRefreshTokenIfNull: Boolean = false,
        sync: Boolean = false
    ) {
        val ed = prefs.edit()
            .putString(KEY_JWT, jwt)
            .putString(KEY_OWNER_ID, ownerId)
            .putString(KEY_ACCESS_TOKEN, accessToken ?: "")
        if (shopifyAccessExpiresAtEpochMs != null && shopifyAccessExpiresAtEpochMs > 0L) {
            ed.putLong(KEY_SHOPIFY_ACCESS_EXPIRES_AT, shopifyAccessExpiresAtEpochMs)
        } else {
            ed.remove(KEY_SHOPIFY_ACCESS_EXPIRES_AT)
        }
        when {
            refreshToken != null -> ed.putString(KEY_REFRESH_TOKEN, refreshToken)
            clearRefreshTokenIfNull -> ed.remove(KEY_REFRESH_TOKEN)
        }
        if (sync) ed.commit() else ed.apply()
    }

    /** 0 = nicht gesetzt (Legacy nach Update). */
    fun getShopifyAccessExpiresAtEpochMs(): Long =
        prefs.getLong(KEY_SHOPIFY_ACCESS_EXPIRES_AT, 0L)

    fun setShopifyAccessExpiresAtEpochMs(epochMs: Long) {
        prefs.edit().putLong(KEY_SHOPIFY_ACCESS_EXPIRES_AT, epochMs).apply()
    }

    /**
     * Löscht alle Tokens synchron (commit statt apply), damit der nächste
     * isLoggedIn()-Check sofort false liefert – kein automatisches Re-Login.
     */
    fun clear() {
        prefs.edit().clear().commit()
    }

    /**
     * Eingeloggt, wenn JWT da ist und die Session wiederherstellbar ist:
     * - Mit refresh_token gilt die Session auch bei fehlendem oder abgelaufenem Shopify access_token,
     *   bis [ShopSessionGuard.refreshAccessTokenIfNeeded] neue Tokens geschrieben hat (wichtig nach App-Updates / Storage-Glitches).
     * - Legacy ohne expires_at ohne refresh_token: access_token Pflicht wie bisher (validate auf dem Server).
     */
    fun isLoggedIn(): Boolean {
        val jwtOk = !getJwt().isNullOrBlank()
        if (!jwtOk) return false
        val refresh = getRefreshToken()
        if (!refresh.isNullOrBlank()) return true

        val access = getAccessToken()
        if (access.isNullOrBlank()) return false
        val exp = getShopifyAccessExpiresAtEpochMs()
        if (exp <= 0L) return true
        return System.currentTimeMillis() < exp
    }

    companion object {
        private const val KEY_JWT = "jwt"
        private const val KEY_OWNER_ID = "owner_id"
        private const val KEY_ACCESS_TOKEN = "shopify_access_token"
        private const val KEY_SHOPIFY_ACCESS_EXPIRES_AT = "shopify_access_expires_at"
        private const val KEY_REFRESH_TOKEN = "shopify_refresh_token"

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
