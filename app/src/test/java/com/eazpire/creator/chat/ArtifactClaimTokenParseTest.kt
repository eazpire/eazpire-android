package com.eazpire.creator.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtifactClaimTokenParseTest {

    @Test
    fun parsesQueryAndPath() {
        assertEquals("tok_abc", ArtifactsJson.parseClaimToken("https://creator-engine.eazpire.workers.dev/artifacts/claim?t=tok_abc"))
        assertEquals("pathTok", ArtifactsJson.parseClaimToken("https://creator-engine.eazpire.workers.dev/q/pathTok"))
    }

    @Test
    fun rejectsMalformed() {
        assertNull(ArtifactsJson.parseClaimToken("https://example.com/artifacts/claim"))
        assertNull(ArtifactsJson.parseClaimToken("short"))
        assertNull(ArtifactsJson.parseClaimToken(""))
    }
}
