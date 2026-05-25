package com.eazpire.creator.i18n

import java.util.Locale

/**
 * Client fallbacks for Creator Wear settings until D1 has `creator.settings.wear_*` rows.
 * Matches theme/locales en.default.json and de.json.
 */
object WearSettingsUiFallback {
    private val en: Map<String, String> = mapOf(
        "creator.settings.wear_phone_logged_in" to "This device: logged in",
        "creator.settings.wear_phone_logged_out" to "This device: not logged in",
        "creator.settings.wear_not_connected" to "No watch connected to your account.",
        "creator.settings.wear_connected_watch" to "Connected watch: {{name}}",
        "creator.settings.wear_connected_at" to "Connected: {{when}}",
        "creator.settings.wear_play_store" to "Get app on Google Play",
        "creator.settings.wear_connect" to "Connect",
        "creator.settings.wear_disconnect" to "Disconnect watch",
        "creator.settings.wear_status_title" to "Status",
        "creator.settings.wear_title" to "Creator Wear",
        "creator.settings.wear_subtitle" to "Connect your Wear OS watch to your creator account",
        "creator.settings.wear_intro" to "Install Eazpire Creator on your Wear OS watch. After the logo, scan the QR on the watch with Connect below (or use the Eazpire Android app).",
    )

    private val de: Map<String, String> = mapOf(
        "creator.settings.wear_phone_logged_in" to "Dieses Gerät: angemeldet",
        "creator.settings.wear_phone_logged_out" to "Dieses Gerät: nicht angemeldet",
        "creator.settings.wear_not_connected" to "Keine Uhr mit deinem Konto verbunden.",
        "creator.settings.wear_connected_watch" to "Verbundene Uhr: {{name}}",
        "creator.settings.wear_connected_at" to "Verbunden: {{when}}",
        "creator.settings.wear_play_store" to "App bei Google Play herunterladen",
        "creator.settings.wear_connect" to "Verbinden",
        "creator.settings.wear_disconnect" to "Uhr trennen",
        "creator.settings.wear_status_title" to "Status",
        "creator.settings.wear_title" to "Creator Wear",
        "creator.settings.wear_subtitle" to "Verbinde deine Wear OS-Uhr mit deinem Creator-Konto",
        "creator.settings.wear_intro" to "Installiere Eazpire Creator auf deiner Wear OS-Uhr. Nach dem Logo scanne den QR-Code auf der Uhr mit „Verbinden“ unten (oder nutze die Eazpire-Android-App).",
    )

    fun get(key: String, lang: String): String? {
        val logical = key.removePrefix("ui:")
        if (!logical.startsWith("creator.settings.wear_")) return null
        val base = lang.trim().lowercase(Locale.ROOT).take(2)
        return if (base == "de") de[logical] else en[logical]
    }
}
