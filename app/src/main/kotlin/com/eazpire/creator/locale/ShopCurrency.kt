package com.eazpire.creator.locale

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Locale-aware shop money helpers for Android PDP / cart UI.
 * Maps device/store country → currency (Shopify market approximation).
 */
object ShopCurrency {
    fun currencyCodeForCountry(countryCode: String): String {
        return when (countryCode.uppercase().take(2)) {
            "CH", "LI" -> "CHF"
            "GB" -> "GBP"
            "US" -> "USD"
            "CA" -> "CAD"
            "AU", "NZ" -> "AUD"
            "JP" -> "JPY"
            "SE" -> "SEK"
            "NO" -> "NOK"
            "DK" -> "DKK"
            "PL" -> "PLN"
            "CZ" -> "CZK"
            "HU" -> "HUF"
            "RO" -> "RON"
            "TR" -> "TRY"
            "AE", "SA", "QA", "KW", "BH", "OM" -> "AED"
            else -> "EUR"
        }
    }

    fun format(amount: Double, currencyCode: String, locale: Locale = Locale.getDefault()): String {
        return try {
            val nf = NumberFormat.getCurrencyInstance(locale)
            nf.currency = Currency.getInstance(currencyCode)
            nf.maximumFractionDigits = 2
            nf.format(amount)
        } catch (_: Exception) {
            String.format(locale, "%.2f %s", amount, currencyCode)
        }
    }
}
