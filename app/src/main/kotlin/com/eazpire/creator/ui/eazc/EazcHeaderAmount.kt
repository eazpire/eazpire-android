package com.eazpire.creator.ui.eazc

import java.util.Locale
import org.json.JSONObject

fun formatHeaderEazcAmount(amount: Double): String {
    val v = if (amount.isFinite() && amount >= 0.0) amount else 0.0
    return if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.2f", v)
}

fun headerEazcFromBalance(data: JSONObject?): Double {
    if (data == null || !data.optBoolean("ok", true)) return 0.0
    if (data.has("balance_eazc_header") && !data.isNull("balance_eazc_header")) {
        val header = data.optDouble("balance_eazc_header", Double.NaN)
        if (header.isFinite()) return header.coerceAtLeast(0.0)
    }
    val avail = data.optDouble(
        "balance_eazc_available",
        data.optDouble("balance_earned_available", 0.0)
    )
    val locked = data.optDouble(
        "balance_eazc_locked",
        data.optDouble("balance_earned_locked", 0.0)
    )
    val a = if (avail.isFinite()) avail else 0.0
    val l = if (locked.isFinite()) locked else 0.0
    return (a + l).coerceAtLeast(0.0)
}
