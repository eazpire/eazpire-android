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
 * Stores JWT and Shopify tokens in EncryptedSharedPreferences only.
 * A leftover plaintext mirror (`eazpire_auth_backup_v1`) is wiped on init.
 * If the Keystore is unusable the session is treated as logged out.
 */
class SecureTokenStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private var keystoreUnavailable: Boolean = false
    private val prefs: SharedPreferences = openEncryptedPrefs(appContext)

    private fun openEncryptedPrefs(context: Context): SharedPreferences {
        wipeLegacyPlaintextBackup(context)
        return try {
            createEncryptedPrefs(context)
        } catch (e: Exception) {
            AuthDebugLog.w("[TOKEN] Encrypted prefs open failed; recreating", e)
            try {
                context.deleteSharedPreferences(ENCRYPTED_PREFS_NAME)
                createEncryptedPrefs(context)
            } catch (e2: Exception) {
                AuthDebugLog.w("[TOKEN] Encrypted prefs unavailable; session cleared", e2)
                keystoreUnavailable = true
                EmptyPrefs
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

    private fun readNonBlank(store: SharedPreferences, key: String): String? =
        store.getString(key, null)?.takeIf { it.isNotBlank() }

    fun getJwt(): String? =
        if (keystoreUnavailable) null else readNonBlank(prefs, KEY_JWT)

    fun getOwnerId(): String? =
        if (keystoreUnavailable) null else readNonBlank(prefs, KEY_OWNER_ID)

    fun getAccessToken(): String? =
        if (keystoreUnavailable) null else readNonBlank(prefs, KEY_ACCESS_TOKEN)

    fun getRefreshToken(): String? =
        if (keystoreUnavailable) null else readNonBlank(prefs, KEY_REFRESH_TOKEN)

    fun saveJwt(jwt: String, ownerId: String) {
        if (keystoreUnavailable) return
        prefs.edit()
            .putString(KEY_JWT, jwt)
            .putString(KEY_OWNER_ID, ownerId)
            .commit()
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
        if (keystoreUnavailable) return
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
    }

    fun getShopifyAccessExpiresAtEpochMs(): Long {
        if (keystoreUnavailable) return 0L
        return prefs.getLong(KEY_SHOPIFY_ACCESS_EXPIRES_AT, 0L)
    }

    fun setShopifyAccessExpiresAtEpochMs(epochMs: Long) {
        if (keystoreUnavailable) return
        prefs.edit().putLong(KEY_SHOPIFY_ACCESS_EXPIRES_AT, epochMs).commit()
    }

    /** Clears Shopify OAuth tokens only; keeps app JWT / owner id for creator API session. */
    fun clearShopifyOAuthTokens() {
        if (keystoreUnavailable) return
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_SHOPIFY_ACCESS_EXPIRES_AT)
            .putString(KEY_ACCESS_TOKEN, "")
            .commit()
    }

    fun clear() {
        if (!keystoreUnavailable) {
            prefs.edit().clear().commit()
        }
        wipeLegacyPlaintextBackup(appContext)
    }

    fun isLoggedIn(): Boolean = !getJwt().isNullOrBlank()

    /** Debug helper after app start / update — no secrets logged. */
    fun sessionDebugSummary(): String {
        val encJwt = !keystoreUnavailable && readNonBlank(prefs, KEY_JWT) != null
        val hasRefresh = getRefreshToken() != null
        val hasAccess = !getAccessToken().isNullOrBlank()
        return "loggedIn=${isLoggedIn()} encJwt=$encJwt bakJwt=false plaintextFallback=$keystoreUnavailable hasRefresh=$hasRefresh hasAccess=$hasAccess ownerId=${!getOwnerId().isNullOrBlank()}"
    }

    companion object {
        @Volatile
        private var instance: SecureTokenStore? = null

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

        fun wipeLegacyPlaintextBackup(context: Context) {
            runCatching {
                context.applicationContext.getSharedPreferences(BACKUP_PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .commit()
                context.applicationContext.deleteSharedPreferences(BACKUP_PREFS_NAME)
            }
        }

        fun clearAuthCookies() {
            val cm = CookieManager.getInstance()
            cm.removeAllCookies(null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                cm.flush()
            }
        }
    }
}

/** In-memory no-op prefs when Keystore cannot be opened. Nothing is persisted. */
private object EmptyPrefs : SharedPreferences {
    override fun getAll(): MutableMap<String, *> = mutableMapOf<String, Any>()
    override fun getString(key: String?, defValue: String?): String? = defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String?, defValue: Int): Int = defValue
    override fun getLong(key: String?, defValue: Long): Long = defValue
    override fun getFloat(key: String?, defValue: Float): Float = defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
    override fun contains(key: String?): Boolean = false
    override fun edit(): SharedPreferences.Editor = EmptyEditor
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}

private object EmptyEditor : SharedPreferences.Editor {
    override fun putString(key: String?, value: String?) = this
    override fun putStringSet(key: String?, values: MutableSet<String>?) = this
    override fun putInt(key: String?, value: Int) = this
    override fun putLong(key: String?, value: Long) = this
    override fun putFloat(key: String?, value: Float) = this
    override fun putBoolean(key: String?, value: Boolean) = this
    override fun remove(key: String?) = this
    override fun clear() = this
    override fun commit(): Boolean = true
    override fun apply() = Unit
}
