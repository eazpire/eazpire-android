package com.eazpire.creator.brand

import android.content.Context
import com.eazpire.creator.auth.AuthConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object BrandAssetSlots {
    const val CREATOR_APP_HEADER_LOGO = "creator_app_header_logo"
    const val CREATOR_WEAR_PROMO_LOGO = "creator_wear_promo_logo"
    const val WEAR_LOGO = "wear_logo"
    const val CREATOR_WATCH_LOGO = "creator_watch_logo"
    const val EAZ_APP_FAVICON = "eaz_app_favicon"
    const val EAZV_COIN_LOGO = "eazv_coin_logo"
    const val EAZC_COIN_LOGO = "eazc_coin_logo"
}

/**
 * Caches platform-asset-manifest from creator-engine for runtime brand logos (Coil URLs).
 * Falls back to embedded drawables when offline or manifest unavailable.
 */
class BrandAssetsRepository(
    private val context: Context,
    private val baseUrl: String = AuthConfig.CREATOR_ENGINE_URL,
) {
    companion object {
        private const val PREFS = "brand_assets_manifest"
        private const val KEY_JSON = "manifest_json"
        private const val KEY_FETCHED_AT = "fetched_at_ms"
        /** In-session TTL after the mandatory cold-start manifest fetch. */
        private const val SESSION_STALE_MS = 2 * 60 * 60 * 1000L

        @Volatile
        private var shared: BrandAssetsRepository? = null

        fun get(context: Context): BrandAssetsRepository {
            return shared ?: synchronized(this) {
                shared ?: BrandAssetsRepository(context.applicationContext).also { shared = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Process-local: cleared on app kill — first fetch after restart is always fresh. */
    private var coldStartRefreshPending = true

    private val _urls = MutableStateFlow(loadCachedUrls())
    val urls: StateFlow<Map<String, String>> = _urls.asStateFlow()

    fun urlFor(slot: String): String? = _urls.value[slot]

    private fun loadCachedUrls(): Map<String, String> {
        val raw = prefs.getString(KEY_JSON, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val assets = obj.optJSONObject("assets") ?: return emptyMap()
            val out = mutableMapOf<String, String>()
            assets.keys().forEach { key ->
                val url = assets.optString(key, "").trim()
                if (url.isNotBlank()) out[key] = url
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun refreshIfStale(force: Boolean = false) = withContext(Dispatchers.IO) {
        if (force) {
            refresh(force = true)
            return@withContext
        }
        if (coldStartRefreshPending) {
            coldStartRefreshPending = false
            refresh(force = true)
            return@withContext
        }
        val last = prefs.getLong(KEY_FETCHED_AT, 0L)
        if (System.currentTimeMillis() - last < SESSION_STALE_MS && _urls.value.isNotEmpty()) {
            return@withContext
        }
        refresh(force = true)
    }

    suspend fun refresh(force: Boolean = false) = withContext(Dispatchers.IO) {
        if (!force && _urls.value.isNotEmpty()) {
            val last = prefs.getLong(KEY_FETCHED_AT, 0L)
            if (System.currentTimeMillis() - last < SESSION_STALE_MS) return@withContext
        }
        try {
            val url = "$baseUrl/apps/creator-dispatch?op=platform-asset-manifest&_t=${System.currentTimeMillis()}"
            val req = Request.Builder()
                .url(url)
                .header("Cache-Control", "no-cache")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext
                val text = resp.body?.string().orEmpty()
                if (text.isBlank()) return@withContext
                val obj = JSONObject(text)
                if (!obj.optBoolean("ok", false)) return@withContext
                prefs.edit()
                    .putString(KEY_JSON, text)
                    .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
                    .apply()
                _urls.value = loadCachedUrls()
            }
        } catch (_: Exception) {
            // Keep cached values — UI uses drawable fallback.
        }
    }
}
