package com.eazpire.creator.ui.header

import org.junit.Assert.assertEquals
import org.junit.Test

class WalletLoadingLabelTest {
    @Test
    fun replacesMojibakeEllipsis() {
        assertEquals("...", sanitizeWalletLoadingLabel("â€¦"))
    }

    @Test
    fun keepsPlainAsciiEllipsis() {
        assertEquals("...", sanitizeWalletLoadingLabel("..."))
    }

    @Test
    fun blankFallsBackToAsciiEllipsis() {
        assertEquals("...", sanitizeWalletLoadingLabel("   "))
    }
}
