package com.eazpire.creator.chat

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val Context.eazySettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "eazy_settings")

/**
 * Mirrors web [eazy-settings.js]: localStorage `eazy_settings_v1` + POST `eazy-memory` with `preferences.eazy_settings`.
 */
class EazySettingsStore(private val context: Context) {

    private val _settings = MutableStateFlow(defaultSettings())
    val settings: StateFlow<JSONObject> = _settings.asStateFlow()

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var syncJob: Job? = null

    suspend fun loadFromDisk() {
        val raw = context.eazySettingsDataStore.data.map { it[PREFS_KEY] }.first()
        if (raw.isNullOrBlank()) {
            _settings.value = defaultSettings()
            return
        }
        try {
            val o = JSONObject(raw)
            mergeMissingDefaults(o)
            _settings.value = o
        } catch (_: Exception) {
            _settings.value = defaultSettings()
        }
    }

    /** If local file empty, pull `eazy_settings` from server (same idea as web loadFromServer). */
    suspend fun tryMergeFromServer(api: CreatorApi, userId: String) {
        val hasLocal = context.eazySettingsDataStore.data.map { it[PREFS_KEY] }.first() != null
        if (hasLocal) return
        try {
            val r = withContext(Dispatchers.IO) { api.getEazyMemory(userId) }
            if (!r.optBoolean("ok", false)) return
            val mem = r.optJSONObject("memory") ?: return
            val prefsRaw = mem.opt("preferences") ?: return
            val prefs = when (prefsRaw) {
                is String -> JSONObject(prefsRaw)
                is JSONObject -> prefsRaw
                else -> return
            }
            val ez = prefs.optJSONObject("eazy_settings") ?: return
            mergeMissingDefaults(ez)
            _settings.value = ez
            persistToDisk()
        } catch (_: Exception) {
        }
    }

    fun getBoolean(key: String, default: Boolean): Boolean =
        _settings.value.optBoolean(key, default)

    fun getInt(key: String, default: Int): Int =
        _settings.value.optInt(key, default)

    fun getString(key: String, default: String): String =
        _settings.value.optString(key, default).ifBlank { default }

    fun getMoodTags(): Set<String> {
        val arr = _settings.value.optJSONArray("mood_tags") ?: return defaultMoodTagSet()
        val out = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            arr.optString(i).takeIf { it.isNotBlank() }?.let { out.add(it) }
        }
        return out.ifEmpty { defaultMoodTagSet() }
    }

    fun setBoolean(key: String, value: Boolean) {
        mutate { put(key, value) }
    }

    fun setInt(key: String, value: Int) {
        mutate { put(key, value) }
    }

    fun setString(key: String, value: String) {
        mutate { put(key, value) }
    }

    fun toggleMoodTag(tag: String) {
        mutate {
            val cur = optJSONArray("mood_tags") ?: JSONArray(defaultMoodTagList())
            val list = mutableListOf<String>()
            for (i in 0 until cur.length()) list.add(cur.optString(i))
            if (tag in list) list.remove(tag) else list.add(tag)
            val na = JSONArray()
            list.forEach { na.put(it) }
            put("mood_tags", na)
        }
    }

    fun resetToDefaults() {
        _settings.value = defaultSettings()
        ioScope.launch {
            persistToDisk()
        }
    }

    private fun mutate(block: JSONObject.() -> Unit) {
        val o = JSONObject(_settings.value.toString())
        o.apply(block)
        _settings.value = o
        ioScope.launch {
            persistToDisk()
        }
    }

    private suspend fun persistToDisk() {
        context.eazySettingsDataStore.edit { prefs ->
            prefs[PREFS_KEY] = _settings.value.toString()
        }
    }

    fun scheduleSyncToServer(api: CreatorApi, userId: String) {
        syncJob?.cancel()
        syncJob = ioScope.launch {
            delay(SYNC_DEBOUNCE_MS)
            withContext(Dispatchers.IO) {
                try {
                    val payload = JSONObject().put("eazy_settings", JSONObject(_settings.value.toString()))
                    api.postEazyMemory(userId, payload)
                } catch (_: Exception) {
                }
            }
        }
    }

    companion object {
        private val PREFS_KEY = stringPreferencesKey("eazy_settings_v1")
        private const val SYNC_DEBOUNCE_MS = 3000L

        private fun defaultMoodTagList() = listOf(
            "lustig", "inspirierend", "weisheit", "informativ", "frech", "flirty"
        )

        private fun defaultMoodTagSet(): Set<String> = defaultMoodTagList().toSet()

        private fun defaultSettings(): JSONObject = JSONObject().apply {
            put("audio_enabled", true)
            put("audio_volume", 75)
            put("audio_autoplay", false)
            put("messages_enabled", true)
            put("messages_mascot_bubbles", true)
            put("messages_dream_bubbles", true)
            put("messages_chat_greeting", true)
            put("messages_idle_tips_chat", true)
            put("messages_idle_tips_page", true)
            put("messages_function_tips", true)
            put("messages_job_updates", true)
            put("frequency_mascot_bubbles", 1)
            put("frequency_dream_bubbles", 1)
            put("frequency_idle_tips_chat", 1)
            put("frequency_idle_tips_page", 1)
            put("mood_tags", JSONArray(defaultMoodTagList()))
            put("placement", "page")
        }

        private fun mergeMissingDefaults(o: JSONObject) {
            val d = defaultSettings()
            val it = d.keys()
            while (it.hasNext()) {
                val k = it.next()
                if (!o.has(k)) {
                    o.put(k, d.get(k))
                }
            }
        }
    }
}
