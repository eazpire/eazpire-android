package com.eazpire.creator.i18n

import android.content.Context
import com.eazpire.creator.debug.langDebug
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Locale

private val Context.translationDataStore: DataStore<Preferences> by preferencesDataStore(name = "translation_cache")

/**
 * Translation store – loads from DB API with fallback chain (lang → base → en).
 * For dialect/script: fallback to main language, then English.
 * Uses cached translations; refreshes when language changes.
 */
class TranslationStore(
    private val context: Context,
    private val api: CreatorApi = CreatorApi()
) {
    private val _translations = MutableStateFlow<Map<String, String>>(emptyMap())
    val translations: StateFlow<Map<String, String>> = _translations.asStateFlow()

    private val _enFallback = MutableStateFlow<Map<String, String>>(emptyMap())
    val enFallback: StateFlow<Map<String, String>> = _enFallback.asStateFlow()

    /**
     * Get translated string. Lookup order: ui:key, key.
     * Fallback: English map (loaded when lang != en), then default.
     */
    fun t(key: String, default: String? = null): String {
        val map = _translations.value
        val uiKey = if (key.startsWith("ui:")) key else "ui:$key"
        val result = map[uiKey]
            ?: map[key]
            ?: _enFallback.value[uiKey]
            ?: _enFallback.value[key]
            ?: default
            ?: key
        // #region agent log
        if (key == "topbar.cart" || key == "eaz.sidebar.nav_home") {
            langDebug("TranslationStore.kt:t", "Lookup", mapOf("key" to key, "result" to result, "mapSize" to map.size), "H6")
        }
        // #endregion
        return result
    }

    /**
     * Load translations for language. Server applies fallback: lang → base → en.
     * We also load en separately for client-side fallback when key is missing.
     */
    suspend fun load(lang: String) = withContext(Dispatchers.IO) {
        val normalizedLang = lang.trim().lowercase()
        // #region agent log
        langDebug("TranslationStore.kt:load", "Entry", mapOf("lang" to normalizedLang), "H4")
        // #endregion
        try {
            val main = api.getTranslations(normalizedLang, "ui")
            // #region agent log
            langDebug("TranslationStore.kt:load", "API success", mapOf("count" to main.size, "sampleKeys" to main.keys.take(5).toString()), "H4")
            // #endregion
            _translations.value = main

            if (normalizedLang != "en") {
                val en = api.getTranslations("en", "ui")
                _enFallback.value = en
            } else {
                _enFallback.value = main
            }
        } catch (e: Exception) {
            // #region agent log
            langDebug("TranslationStore.kt:load", "API error", mapOf("error" to (e.message ?: "unknown")), "H4")
            // #endregion
            if (_translations.value.isEmpty()) {
                try {
                    val en = api.getTranslations("en", "ui")
                    _translations.value = en
                    _enFallback.value = en
                } catch (_: Exception) {}
            }
        }
    }

    fun getTranslationsSync(): Map<String, String> = _translations.value
    fun getEnFallbackSync(): Map<String, String> = _enFallback.value
}
