package com.eazpire.creator.auth

import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.eazpire.creator.debug.AuthDebugLog
import com.eazpire.creator.perf.EazPerfTrace

/**
 * Speichert JWT und Shopify access_token sicher via EncryptedSharedPreferences.
 * Zusätzlich Spiegel in normalem SharedPreferences, damit Sessions App-Updates überleben,
 * wenn der Keystore-/Encrypted-Prefs-Zustand nach einem Update nicht mehr lesbar ist.
 */
class SecureTokenStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val backupPrefs: SharedPreferences =
        appContext.getSharedPreferences(BACKUP_PREFS_NAME, Context.MODE_PRIVATE)
    private val prefs: SharedPreferences = openEncryptedPrefs(appContext).also { p ->
        ensureSessionHydrated(p)
    }

    private val usingPlaintextFallback: Boolean
        get() = prefs === backupPrefs

    private fun openEncryptedPrefs(context: Context): SharedPreferences {
        return try {
            createEncryptedPrefs(context)
        } catch (e: Exception) {
            AuthDebugLog.w("[TOKEN] Encrypted prefs open failed; recreating and restoring backup", e)
            try {
                context.deleteSharedPreferences(ENCRYPTED_PREFS_NAME)
                val restored = createEncryptedPrefs(context)
                restoreFromBackup(restored)
                restored
            } catch (e2: Exception) {
                // Keystore can stay broken after OEM updates — never crash cold start.
                AuthDebugLog.w("[TOKEN] Encrypted prefs unavailable; using backup mirror only", e2)
                backupPrefs
            }
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences =
        EazPerfTrace.measureSection("SecureTokenStore.init") {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                ENCRYPTED_PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }

    /** After updates, encrypted prefs may be empty while the plain backup still holds the session. */
    private fun ensureSessionHydrated(encrypted: SharedPreferences) {
        val backupJwt = readNonBlank(backupPrefs, KEY_JWT)
        val encryptedJwt = readNonBlank(encrypted, KEY_JWT)
        when {
            backupJwt != null && encryptedJwt == null -> {
                AuthDebugLog.d("[TOKEN] Restoring session from backup mirror into encrypted prefs")
                restoreFromBackup(encrypted)
            }
            encryptedJwt != null && backupJwt == null -> {
                AuthDebugLog.d("[TOKEN] Backfilling backup mirror from encrypted prefs")
                mirrorToBackup()
            }
        }
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
        val jwt = readNonBlank(prefs, KEY_JWT)
        if (jwt == null) {
            // Never wipe a valid backup session because encrypted prefs are temporarily empty.
            if (readNonBlank(backupPrefs, KEY_JWT) == null) {
                backupPrefs.edit().clear().commit()
            }
            return
        }
        val ed = backupPrefs.edit()
            .putString(KEY_JWT, jwt)
            .putString(KEY_OWNER_ID, prefs.getString(KEY_OWNER_ID, null).orEmpty())
            .putString(KEY_ACCESS_TOKEN, prefs.getString(KEY_ACCESS_TOKEN, null).orEmpty())
        val refresh = readNonBlank(prefs, KEY_REFRESH_TOKEN)
        if (refresh != null) {
            ed.putString(KEY_REFRESH_TOKEN, refresh)
        } else {
            ed.remove(KEY_REFRESH_TOKEN)
        }
        val exp = prefs.getLong(KEY_SHOPIFY_ACCESS_EXPIRES_AT, 0L)
        if (exp > 0L) ed.putLong(KEY_SHOPIFY_ACCESS_EXPIRES_AT, exp)
        else ed.remove(KEY_SHOPIFY_ACCESS_EXPIRES_AT)
        ed.commit()
    }

    private fun mirrorToBackup(
        jwt: String,
        ownerId: String,
        accessToken: String?,
        shopifyAccessExpiresAtEpochMs: Long?,
        refreshToken: String?,
    ) {
        if (jwt.isBlank()) return
        val ed = backupPrefs.edit()
            .putString(KEY_JWT, jwt)
            .putString(KEY_OWNER_ID, ownerId)
            .putString(KEY_ACCESS_TOKEN, accessToken ?: "")
        if (!refreshToken.isNullOrBlank()) {
            ed.putString(KEY_REFRESH_TOKEN, refreshToken)
        } else {
            ed.remove(KEY_REFRESH_TOKEN)
        }
        if (shopifyAccessExpiresAtEpochMs != null && shopifyAccessExpiresAtEpochMs > 0L) {
            ed.putLong(KEY_SHOPIFY_ACCESS_EXPIRES_AT, shopifyAccessExpiresAtEpochMs)
        } else {
            ed.remove(KEY_SHOPIFY_ACCESS_EXPIRES_AT)
        }
        ed.commit()
    }

    private fun readNonBlank(store: SharedPreferences, key: String): String? =
        store.getString(key, null)?.takeIf { it.isNotBlank() }

    fun getJwt(): String? =
        readNonBlank(prefs, KEY_JWT) ?: readNonBlank(backupPrefs, KEY_JWT)

    fun getOwnerId(): String? =
        readNonBlank(prefs, KEY_OWNER_ID) ?: readNonBlank(backupPrefs, KEY_OWNER_ID)

    fun getAccessToken(): String? =
        readNonBlank(prefs, KEY_ACCESS_TOKEN) ?: readNonBlank(backupPrefs, KEY_ACCESS_TOKEN)

    fun getRefreshToken(): String? =
        readNonBlank(prefs, KEY_REFRESH_TOKEN) ?: readNonBlank(backupPrefs, KEY_REFRESH_TOKEN)

    fun saveJwt(jwt: String, ownerId: String) {
        prefs.edit()
            .putString(KEY_JWT, jwt)
            .putString(KEY_OWNER_ID, ownerId)
            .commit()
        mirrorToBackup(
            jwt = jwt,
            ownerId = ownerId,
            accessToken = readNonBlank(prefs, KEY_ACCESS_TOKEN),
            shopifyAccessExpiresAtEpochMs = prefs.getLong(KEY_SHOPIFY_ACCESS_EXPIRES_AT, 0L).takeIf { it > 0L },
            refreshToken = readNonBlank(prefs, KEY_REFRESH_TOKEN),
        )
    }

    fun saveTokens(
        jwt: String,
        ownerId: String,
        accessToken: String?,
        shopifyAccessExpiresAtEpochMs: Long? = null,
        refreshToken: String? = null,
        clearRefreshTokenIfNull: Boolean = false,
        sync: Boolean = false,
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
        ed.commit()
        val rt = when {
            refreshToken != null -> refreshToken
            clearRefreshTokenIfNull -> null
            else -> readNonBlank(prefs, KEY_REFRESH_TOKEN)
        }
        mirrorToBackup(jwt, ownerId, accessToken, shopifyAccessExpiresAtEpochMs, rt)
    }

    fun getShopifyAccessExpiresAtEpochMs(): Long {
        val exp = prefs.getLong(KEY_SHOPIFY_ACCESS_EXPIRES_AT, 0L)
        return if (exp > 0L) exp else backupPrefs.getLong(KEY_SHOPIFY_ACCESS_EXPIRES_AT, 0L)
    }

    fun setShopifyAccessExpiresAtEpochMs(epochMs: Long) {
        prefs.edit().putLong(KEY_SHOPIFY_ACCESS_EXPIRES_AT, epochMs).commit()
        mirrorToBackup()
    }

    /** Clears Shopify OAuth tokens only; keeps app JWT / owner id for creator API session. */
    fun clearShopifyOAuthTokens() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_SHOPIFY_ACCESS_EXPIRES_AT)
            .putString(KEY_ACCESS_TOKEN, "")
            .commit()
        backupPrefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_SHOPIFY_ACCESS_EXPIRES_AT)
            .putString(KEY_ACCESS_TOKEN, "")
            .commit()
    }

    fun clear() {
        prefs.edit().clear().commit()
        backupPrefs.edit().clear().commit()
    }

    fun isLoggedIn(): Boolean = !getJwt().isNullOrBlank()

    /** Debug helper after app start / update — no secrets logged. */
    fun sessionDebugSummary(): String {
        val encJwt = readNonBlank(prefs, KEY_JWT) != null
        val bakJwt = readNonBlank(backupPrefs, KEY_JWT) != null
        val hasRefresh = getRefreshToken() != null
        val hasAccess = !getAccessToken().isNullOrBlank()
        return "loggedIn=${isLoggedIn()} encJwt=$encJwt bakJwt=$bakJwt plaintextFallback=$usingPlaintextFallback hasRefresh=$hasRefresh hasAccess=$hasAccess ownerId=${!getOwnerId().isNullOrBlank()}"
    }

    companion object {
        @Volatile
        private var instance: SecureTokenStore? = null

        /** Process-wide singleton — Keystore init runs once per cold start. */
        fun get(context: Context): SecureTokenStore {
            val app = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: SecureTokenStore(app).also { instance = it }
            }
        }

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
