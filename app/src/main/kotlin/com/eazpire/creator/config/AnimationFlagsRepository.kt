package com.eazpire.creator.config

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.flow.first
import org.json.JSONObject

private val Context.animationFlagsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "eaz_ui_animation_flags"
)

/**
 * Remote UI animation toggles from worker `get-ui-animation-flags`.
 * Android reads the `android` column per animation key.
 */
object AnimationFlagsRepository {

    private val KEY_JSON = stringPreferencesKey("flags_json")
    private val KEY_UPDATED_AT = longPreferencesKey("updated_at")

    private var memoryCache: JSONObject? = null

    suspend fun syncFromServer(context: Context, api: CreatorApi) {
        try {
            val o = api.getUiAnimationFlags()
            if (!o.optBoolean("ok", true) && o.has("error")) return
            context.animationFlagsDataStore.edit { prefs ->
                prefs[KEY_JSON] = o.toString()
                prefs[KEY_UPDATED_AT] = o.optLong("updated_at", System.currentTimeMillis())
            }
            memoryCache = o
        } catch (_: Exception) {
        }
    }

    suspend fun syncFromServerPublic(context: Context) {
        try {
            val o = CreatorApi().getUiAnimationFlags()
            if (!o.optBoolean("ok", true) && o.has("error")) return
            context.animationFlagsDataStore.edit { prefs ->
                prefs[KEY_JSON] = o.toString()
                prefs[KEY_UPDATED_AT] = o.optLong("updated_at", System.currentTimeMillis())
            }
            memoryCache = o
        } catch (_: Exception) {
        }
    }

    private suspend fun loadRoot(context: Context): JSONObject {
        memoryCache?.let { return it }
        val prefs = context.animationFlagsDataStore.data.first()
        val raw = prefs[KEY_JSON] ?: return JSONObject()
        return try {
            JSONObject(raw).also { memoryCache = it }
        } catch (_: Exception) {
            JSONObject()
        }
    }

    suspend fun isEnabled(context: Context, scope: String, key: String): Boolean {
        val root = loadRoot(context)
        val bucket = root.optJSONObject(scope) ?: return true
        val entry = bucket.optJSONObject(key) ?: return true
        return entry.optBoolean("android", true)
    }

    fun isEnabledCached(scope: String, key: String): Boolean {
        val root = memoryCache ?: return true
        val bucket = root.optJSONObject(scope) ?: return true
        val entry = bucket.optJSONObject(key) ?: return true
        return entry.optBoolean("android", true)
    }

    suspend fun refreshMemory(context: Context) {
        memoryCache = null
        loadRoot(context)
    }
}
