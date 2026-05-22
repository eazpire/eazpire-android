package com.eazpire.creator.auth

import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Speichert JWT und Shopify access_token sicher via EncryptedSharedPreferences.
 * Zusätzlich Spiegel in normalem SharedPreferences, damit Sessions App-Updates überleben,
 * wenn der Keystore-/Encrypted-Prefs-Zustand nach einem Update nicht mehr lesbar ist.
 */
class SecureTokenStore(context: Context) {
    private val appContext = context.applicationContext
    private val backupPrefs: SharedPreferences =
        appContext.getSharedPreferences(BACKUP_PREFS_NAME, Context.MODE_PRIVATE)
    private val prefs: SharedPreferences = openEncryptedPrefs(appContext).also { p ->
        if (p.getString(KEY_JWT, null).isNullOrBlank() && !backupPrefs.getString(KEY_JWT, null).isNullOrBlank()) {
            restoreFromBackup(p)
        }
    }

    private fun openEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            createEncryptedPrefs(context)
        } catch (_: Exception) {
            context.deleteSharedPreferences(ENCRYPTED_PREFS_NAME)
            val restored = createEncryptedPrefs(context)
            restoreFromBackup(restored)
            restored
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun restoreFromBackup(target: SharedPreferences) {
        val jwt = backupPrefs.getString(KEY_JWT, null)?.takeIf { it.isNotBlank() } ?: return
        val ownerId = backupPrefs.getString(KEY_OWNER_ID, null).orEmpty()
        val access = backupPrefs.getString(KEY_ACCESS_TOKEN, null)
        val refresh = backupPrefs.getString(KEY_REFRESH_TOKEN, null)
        val exp = backupPrefs.getLong(KEY_SHOPIFY_ACCESS_EXPIRES_AT, 0L)
        val ed = target.edit()
            .putString(KEY_JWT, jwt)
            .putString(KEY_OWNER_ID, ownerId)
            .putString(KEY_ACCESS_TOKEN, access ?: "")
        if (exp > 0L) ed.putLong(KEY_SHOPIFY_ACCESS_EXPIRES_AT, exp)
        if (!refresh.isNullOrBlank()) ed.putString(KEY_REFRESH_TOKEN, refresh)
        ed.commit()
    }

    private fun mirrorToBackup() {
        val jwt = prefs.getString(KEY_JWT, null) ?: return
        if (jwt.isBlank()) {
            backupPrefs.edit().clear().apply()
            return
        }
        val ed = backupPrefs.edit()
            .putString(KEY_JWT, jwt)
            .putString(KEY_OWNER_ID, prefs.getString(KEY_OWNER_ID, null).orEmpty())
            .putString(KEY_ACCESS_TOKEN, prefs.getString(KEY_ACCESS_TOKEN, null).orEmpty())
        val refresh = prefs.getString(KEY_REFRESH_TOKEN, null)
        if (!refresh.isNullOrBlank()) {
            ed.putString(KEY_REFRESH_TOKEN, refresh)
        } else {
            ed.remove(KEY_REFRESH_TOKEN)
        }
        val exp = prefs.getLong(KEY_SHOPIFY_ACCESS_EXPIRES_AT, 0L)
        if (exp > 0L) ed.putLong(KEY_SHOPIFY_ACCESS_EXPIRES_AT, exp)
        else ed.remove(KEY_SHOPIFY_ACCESS_EXPIRES_AT)
        ed.apply()
    }

    fun getJwt(): String? = prefs.getString(KEY_JWT, null)
        ?: backupPrefs.getString(KEY_JWT, null)

    fun getOwnerId(): String? = prefs.getString(KEY_OWNER_ID, null)
        ?: backupPrefs.getString(KEY_OWNER_ID, null)

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)
        ?: backupPrefs.getString(KEY_ACCESS_TOKEN, null)

    fun getRefreshToken(): String? =
        prefs.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }
            ?: backupPrefs.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun saveJwt(jwt: String, ownerId: String) {
        prefs.edit()
            .putString(KEY_JWT, jwt)
            .putString(KEY_OWNER_ID, ownerId)
            .apply()
        mirrorToBackup()
    }

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
        mirrorToBackup()
    }

    fun getShopifyAccessExpiresAtEpochMs(): Long {
        val exp = prefs.getLong(KEY_SHOPIFY_ACCESS_EXPIRES_AT, 0L)
        return if (exp > 0L) exp else backupPrefs.getLong(KEY_SHOPIFY_ACCESS_EXPIRES_AT, 0L)
    }

    fun setShopifyAccessExpiresAtEpochMs(epochMs: Long) {
        prefs.edit().putLong(KEY_SHOPIFY_ACCESS_EXPIRES_AT, epochMs).apply()
        mirrorToBackup()
    }

    fun clear() {
        prefs.edit().clear().commit()
        backupPrefs.edit().clear().apply()
    }

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
        private const val ENCRYPTED_PREFS_NAME = "eazpire_auth_prefs"
        private const val BACKUP_PREFS_NAME = "eazpire_auth_backup_v1"
        private const val KEY_JWT = "jwt"
        private const val KEY_OWNER_ID = "owner_id"
        private const val KEY_ACCESS_TOKEN = "shopify_access_token"
        private const val KEY_SHOPIFY_ACCESS_EXPIRES_AT = "shopify_access_expires_at"
        private const val KEY_REFRESH_TOKEN = "shopify_refresh_token"

        fun clearAuthCookies() {
            val cm = CookieManager.getInstance()
            cm.removeAllCookies(null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                cm.flush()
            }
        }
    }
}
