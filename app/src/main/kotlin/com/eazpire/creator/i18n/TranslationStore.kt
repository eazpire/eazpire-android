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
    /** Alternative keys for API compatibility (API uses pdp.*, topbar.*, creator.*, eaz.* etc.) */
    private fun resolveKey(key: String): List<String> = buildList {
        add(if (key.startsWith("ui:")) key else "ui:$key")
        add(key)
        when (key) {
            "product.details" -> add("ui:pdp.product_details")
            "product.not_found" -> add("ui:collection.no_products")
            "product.buy_now" -> add("ui:eaz.pdp.buy_now")
            "cart.title" -> add("ui:topbar.cart")
            "cart.added" -> add("ui:pdp.add_to_cart")
            "common.close" -> add("ui:creator.common.close")
            "collection.min" -> { add("ui:creator.product_filters.min"); add("ui:eaz.collection.price_min") }
            "collection.max" -> { add("ui:creator.product_filters.max"); add("ui:eaz.collection.price_max") }
            "collection.price" -> add("ui:creator.product_filters.price")
            "collection.product_category" -> add("ui:creator.product_filters.category")
            "collection.products" -> add("ui:search.products")
            else -> {}
        }
    }

    fun t(key: String, default: String? = null): String {
        val map = _translations.value
        val enMap = _enFallback.value
        for (k in resolveKey(key)) {
            map[k]?.let { return it }
            enMap[k]?.let { return it }
        }
        val logical =
            when {
                key.startsWith("ui:creator.gift_cards.") -> key.removePrefix("ui:")
                key.startsWith("creator.gift_cards.") -> key
                else -> null
            }
        if (logical != null) {
            GiftCardUiFallback.en[logical]?.let { return it }
        }
        return default ?: key
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
