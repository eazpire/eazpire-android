package com.eazpire.creator.locale

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.localeDataStore: DataStore<Preferences> by preferencesDataStore(name = "locale_prefs")

/**
 * Country code (ISO 3166-1 alpha-2) to region code mapping.
 * Aligned with [src/features/admin/catalogConstants.js] COUNTRY_TO_REGION.
 */
private val COUNTRY_TO_REGION = mapOf(
    "DE" to "EU", "AT" to "EU", "CH" to "EU", "FR" to "EU", "NL" to "EU", "IT" to "EU",
    "ES" to "EU", "PL" to "EU", "CZ" to "EU", "LV" to "EU", "SI" to "EU", "BE" to "EU",
    "LU" to "EU", "PT" to "EU", "IE" to "EU", "DK" to "EU", "SE" to "EU", "FI" to "EU",
    "NO" to "EU", "EE" to "EU", "LT" to "EU", "SK" to "EU", "HU" to "EU", "RO" to "EU",
    "BG" to "EU", "HR" to "EU", "GR" to "EU", "CY" to "EU", "MT" to "EU",
    "GB" to "GB",
    "US" to "US",
    "CA" to "CA",
    "AU" to "AU",
    "NZ" to "AU",
    "CN" to "CN",
)

/** Language code to flag country code (for display). Aligned with API /api/languages. */
private val LANG_TO_FLAG_COUNTRY = mapOf(
    "de" to "DE", "en" to "GB", "fr" to "FR", "es" to "ES", "it" to "IT", "pt" to "PT",
    "nl" to "NL", "pl" to "PL", "cs" to "CZ", "da" to "DK", "sv" to "SE", "nb" to "NO",
    "nn" to "NO", "no" to "NO", "fi" to "FI", "hu" to "HU", "ro" to "RO", "bg" to "BG",
    "hr" to "HR", "sk" to "SK", "sl" to "SI", "et" to "EE", "lv" to "LV", "lt" to "LT",
    "el" to "GR", "ru" to "RU", "uk" to "UA", "tr" to "TR", "ar" to "SA", "he" to "IL",
    "ja" to "JP", "ko" to "KR", "zh" to "CN", "zh-cn" to "CN", "zh-tw" to "TW",
    "zh-hans" to "CN", "zh-hant" to "TW", "pt-br" to "BR", "pt-pt" to "PT",
    "th" to "TH", "vi" to "VN", "id" to "ID", "ms" to "MY", "fa" to "IR", "bn" to "BD",
    "ta" to "IN", "te" to "IN", "ka" to "GE", "hy" to "AM", "sw" to "KE", "af" to "ZA",
    "ca" to "ES", "eu" to "ES", "gl" to "ES", "sr" to "RS", "bs" to "BA", "mk" to "MK",
    "sq" to "AL", "mn" to "MN", "ne" to "NP", "si" to "LK", "km" to "KH", "lo" to "LA",
    "my" to "MM", "cy" to "GB", "ga" to "IE", "is" to "IS", "mt" to "MT", "lb" to "LU",
    "fil" to "PH", "tl" to "PH",
    "es-mx" to "MX", "es-ar" to "AR", "fr-ca" to "CA", "fr-be" to "BE",
    "en-us" to "US", "en-gb" to "GB", "en-au" to "AU",
    "de-ch" to "CH", "de-at" to "AT", "de-bay" to "DE", "de-koeln" to "DE",
    "de-berlin" to "DE", "de-platt" to "DE", "nl-be" to "BE", "fr-ch" to "CH",
    "it-ch" to "CH", "no-nn" to "NO", "en-scots" to "GB", "es-anda" to "ES",
    "sr-latn" to "RS", "sr-cyrl" to "RS", "zh-latn" to "CN", "ja-romaji" to "JP",
    "ko-romaji" to "KR", "ar-latn" to "SA", "ru-latn" to "RU", "hi-latn" to "IN",
    "pt-br-d" to "BR",
)

private val KEY_REGION_OVERRIDE = stringPreferencesKey("user_region_override")
private val KEY_LANGUAGE_OVERRIDE = stringPreferencesKey("user_language_override")

/**
 * Store for region and language with automatic device detection.
 * No user selection on first launch – uses device locale.
 * Manual overrides persisted in DataStore.
 */
class LocaleStore(context: Context) {
    private val dataStore = context.localeDataStore

    /** Region code for API (EU, US, GB, etc.). */
    val regionCode: Flow<String> = dataStore.data.map { prefs ->
        val country = prefs[KEY_REGION_OVERRIDE] ?: detectCountryFromDevice()
        mapCountryToRegion(country)
    }

    val languageCode: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_LANGUAGE_OVERRIDE] ?: detectLanguageFromDevice()
    }

    /** Country code for display (DE, CH, US, etc.). */
    val countryCode: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_REGION_OVERRIDE] ?: detectCountryFromDevice()
    }

    suspend fun setRegionOverride(countryCode: String) {
        dataStore.edit { it[KEY_REGION_OVERRIDE] = countryCode.uppercase().take(2) }
    }

    suspend fun setLanguageOverride(langCode: String) {
        dataStore.edit { it[KEY_LANGUAGE_OVERRIDE] = langCode.lowercase().take(12) }
    }

    suspend fun clearOverrides() {
        dataStore.edit {
            it.remove(KEY_REGION_OVERRIDE)
            it.remove(KEY_LANGUAGE_OVERRIDE)
        }
    }

    fun getRegionCodeSync(): String = mapCountryToRegion(detectCountryFromDevice())

    fun getLanguageCodeSync(): String = detectLanguageFromDevice()

    fun getCountryCodeSync(): String = detectCountryFromDevice()

    fun getFlagCountryForLanguage(lang: String): String {
        val key = lang.lowercase()
        return LANG_TO_FLAG_COUNTRY[key]
            ?: LANG_TO_FLAG_COUNTRY[key.take(5)]
            ?: LANG_TO_FLAG_COUNTRY[key.take(2)]
            ?: "US"
    }

    fun mapCountryToRegion(countryCode: String): String =
        COUNTRY_TO_REGION[countryCode.uppercase().take(2)] ?: "OTHER"

    private fun detectCountryFromDevice(): String {
        val locale = Locale.getDefault()
        val country = locale.country
        return if (country.isNotBlank()) country.uppercase() else "DE"
    }

    private fun detectLanguageFromDevice(): String {
        return Locale.getDefault().language.lowercase()
    }

    private fun detectRegionFromDevice(): String {
        val country = detectCountryFromDevice()
        return mapCountryToRegion(country)
    }
}
