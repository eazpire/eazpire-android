package com.eazpire.creator.wear.handoff

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WearPlayerSessionHandoffGuardTest {

    @Test
    fun officialWearPlayerIsTrusted() {
        assertTrue(WearPlayerSessionHandoffGuard.isTrustedCaller("com.eazpire.wear"))
    }

    @Test
    fun foreignCallerIsRejected() {
        assertFalse(WearPlayerSessionHandoffGuard.isTrustedCaller("com.attacker.app"))
        assertFalse(WearPlayerSessionHandoffGuard.isTrustedCaller("com.eazpire.creator"))
        assertFalse(WearPlayerSessionHandoffGuard.isTrustedCaller(null))
        assertFalse(WearPlayerSessionHandoffGuard.isTrustedCaller(""))
    }
}
