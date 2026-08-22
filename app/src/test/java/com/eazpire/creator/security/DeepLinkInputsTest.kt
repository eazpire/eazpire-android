package com.eazpire.creator.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkInputsTest {

    @Test
    fun pairToken_acceptsAlphanumeric() {
        assertEquals("Ab12_-", DeepLinkInputs.pairToken("Ab12_-"))
    }

    @Test
    fun pairToken_rejectsEmptyAndUnicode() {
        assertNull(DeepLinkInputs.pairToken(""))
        assertNull(DeepLinkInputs.pairToken("   "))
        assertNull(DeepLinkInputs.pairToken("tok€n"))
        assertNull(DeepLinkInputs.pairToken("a".repeat(DeepLinkInputs.MAX_PAIR_TOKEN_LEN + 1)))
    }

    @Test
    fun sessionId_rejectsOversized() {
        assertNull(DeepLinkInputs.sessionId("x".repeat(DeepLinkInputs.MAX_SESSION_ID_LEN + 1)))
        assertEquals("sess_1", DeepLinkInputs.sessionId("sess_1"))
    }

    @Test
    fun artifactToken_rejectsSpaces() {
        assertNull(DeepLinkInputs.artifactToken("not a token"))
        assertEquals("artifact_token_ok", DeepLinkInputs.artifactToken("artifact_token_ok"))
    }

    @Test
    fun redactUriForLog_stripsSecrets() {
        val raw = "shop.73952035098.eazpire://callback?code=SECRETCODE&state=STATE123"
        val redacted = DeepLinkInputs.redactUriForLog(raw)
        assertEquals(true, redacted.contains("code=REDACTED"))
        assertEquals(true, redacted.contains("state=REDACTED"))
        assertEquals(false, redacted.contains("SECRETCODE"))
    }
}
