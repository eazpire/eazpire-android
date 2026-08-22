package com.eazpire.creator.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PkceUtilsTest {

    @Test
    fun verifierAndStateAreUrlSafeAndUnique() {
        val a = PkceUtils.generateCodeVerifier()
        val b = PkceUtils.generateCodeVerifier()
        val state = PkceUtils.generateState()
        assertTrue(a.length >= 32)
        assertNotEquals(a, b)
        assertTrue(state.isNotBlank())
        assertEquals(false, a.contains("+") || a.contains("/"))
    }

    @Test
    fun challengeIsDeterministicSha256() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        val challenge = PkceUtils.generateCodeChallenge(verifier)
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", challenge)
    }
}
