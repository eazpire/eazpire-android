package com.eazpire.creator.billing

import java.text.NumberFormat
import java.util.Locale

/**
 * EAZ purchase packs — mirrors theme `creator-eaz-packages-json` and src/features/billing/eazStripePacks.js.
 */
object EazPackageCatalog {

    data class Pack(
        val eaz: Int,
        val priceUsd: Double,
        val recommended: Boolean,
        val label: String,
    )

    val packs: List<Pack> = listOf(
        Pack(eaz = 150, priceUsd = 19.99, recommended = false, label = "150 EAZ"),
        Pack(eaz = 410, priceUsd = 44.99, recommended = false, label = "410 EAZ"),
        Pack(eaz = 1050, priceUsd = 99.99, recommended = true, label = "1050 EAZ"),
    )

    fun fmtUsd(amount: Double): String {
        if (!amount.isFinite()) return "—"
        return NumberFormat.getCurrencyInstance(Locale.US).format(amount)
    }

    fun per10Usd(pack: Pack): Double? {
        if (pack.eaz <= 0) return null
        return (pack.priceUsd / pack.eaz) * 10.0
    }

    fun discountPctVsBaseline(pack: Pack, baseline: Pack?): Int? {
        val basePpu = baseline?.let { it.priceUsd / it.eaz } ?: return null
        val ppu = pack.priceUsd / pack.eaz
        if (ppu + 1e-9 >= basePpu) return null
        return ((1.0 - ppu / basePpu) * 100.0).toInt().coerceAtLeast(1)
    }
}
