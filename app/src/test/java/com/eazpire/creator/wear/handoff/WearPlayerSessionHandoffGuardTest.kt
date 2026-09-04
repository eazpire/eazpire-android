package com.eazpire.creator.wear.handoff

import com.eazpire.shared.EazpireApps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearPlayerSessionHandoffGuardTest {

    @Test
    fun allowedPackageConstantMatchesShared() {
        assertEquals(EazpireApps.WEAR_PLAYER, WearPlayerSessionHandoffGuard.ALLOWED_PACKAGE)
    }

    @Suppress("DEPRECATION")
    @Test
    fun packageNameOnlyHelper_acceptsWearRejectsOthers() {
        assertTrue(WearPlayerSessionHandoffGuard.isTrustedCaller("com.eazpire.wear"))
        assertFalse(WearPlayerSessionHandoffGuard.isTrustedCaller("com.attacker.app"))
        assertFalse(WearPlayerSessionHandoffGuard.isTrustedCaller("com.eazpire.creator"))
        assertFalse(WearPlayerSessionHandoffGuard.isTrustedCaller(null))
        assertFalse(WearPlayerSessionHandoffGuard.isTrustedCaller(""))
    }
}
