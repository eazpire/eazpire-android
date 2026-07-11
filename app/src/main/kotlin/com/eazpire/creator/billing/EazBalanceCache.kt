package com.eazpire.creator.billing

import java.util.Locale

/** In-memory EAZ balance for instant sidebar display while journey/balance APIs load. */
object EazBalanceCache {
    @Volatile
    var value: Double? = null

    fun read(): Double? = value

    fun write(balance: Double?) {
        value = balance?.takeIf { it.isFinite() }
    }

    fun formatSidebarBalance(): String? {
        val v = value ?: return null
        val rounded = (Math.round(v * 10.0) / 10.0)
        val label = if (kotlin.math.abs(rounded - rounded.toLong()) < 1e-9) {
            rounded.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", rounded)
        }
        return "$label EAZV"
    }
}
