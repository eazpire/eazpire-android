package com.eazpire.creator.ui.eazc

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class EazcHeaderAmountTest {
    @Test
    fun formatsWholeNumbersWithoutDecimals() {
        assertEquals("12", formatHeaderEazcAmount(12.0))
    }

    @Test
    fun formatsFractionsWithTwoDecimals() {
        assertEquals("12.50", formatHeaderEazcAmount(12.5))
    }

    @Test
    fun sumsAvailableAndPending() {
        val data = JSONObject()
            .put("ok", true)
            .put("balance_eazc_available", 3.0)
            .put("balance_eazc_locked", 2.5)
        assertEquals(5.5, headerEazcFromBalance(data), 0.001)
        assertEquals("5.50", formatHeaderEazcAmount(headerEazcFromBalance(data)))
    }

    @Test
    fun prefersHeaderField() {
        val data = JSONObject()
            .put("ok", true)
            .put("balance_eazc_header", 9.0)
            .put("balance_eazc_available", 1.0)
            .put("balance_eazc_locked", 1.0)
        assertEquals(9.0, headerEazcFromBalance(data), 0.001)
    }
}
