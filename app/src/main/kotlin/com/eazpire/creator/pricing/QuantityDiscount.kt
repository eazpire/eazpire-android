package com.eazpire.creator.pricing

/**
 * Volume discount tiers — keep in sync with theme/assets/quantity-discount-pricing.js
 * and eaz-quantity-discount-ts Shopify Function.
 */
object QuantityDiscount {

    data class Tier(val min: Int, val max: Int?, val percent: Int)

    data class LineEstimate(
        val listSubtotal: Double,
        val percent: Int,
        val discountAmount: Double,
        val afterDiscount: Double,
        val lineSavings: Double,
        val shippingSavings: Double,
        val totalSavingsEstimate: Double,
    )

    fun tierPercent(quantity: Int): Int {
        val q = quantity.coerceAtLeast(0)
        if (q < 5) return 0
        if (q < 10) return 4
        if (q < 25) return 7
        if (q < 50) return 10
        if (q < 100) return 12
        return 15
    }

    fun tierTable(): List<Tier> = listOf(
        Tier(1, 4, 0),
        Tier(5, 9, 4),
        Tier(10, 24, 7),
        Tier(25, 49, 10),
        Tier(50, 99, 12),
        Tier(100, null, 15),
    )

    fun estimateLineTotals(
        unitPrice: Double,
        quantity: Int,
        shippingThreshold: Double = 0.0,
        estimatedShipping: Double = 0.0,
    ): LineEstimate {
        val q = quantity.coerceAtLeast(0)
        val pct = tierPercent(q)
        val listSubtotal = unitPrice * q
        val discountAmount = listSubtotal * pct / 100.0
        val afterDiscount = listSubtotal - discountAmount
        var shippingSavings = 0.0
        if (shippingThreshold > 0 && afterDiscount >= shippingThreshold && estimatedShipping > 0) {
            shippingSavings = estimatedShipping
        }
        return LineEstimate(
            listSubtotal = listSubtotal,
            percent = pct,
            discountAmount = discountAmount,
            afterDiscount = afterDiscount,
            lineSavings = discountAmount,
            shippingSavings = shippingSavings,
            totalSavingsEstimate = discountAmount + shippingSavings,
        )
    }

    fun formatTierRange(tier: Tier): String =
        if (tier.max == null) "${tier.min}+" else "${tier.min}–${tier.max}"
}
