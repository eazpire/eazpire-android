package com.eazpire.creator.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WearPairTokenParseTest {

    @Test
    fun parsesCustomScheme() {
        assertEquals("PairTok_1", WearPairApi.parseTokenFromQrPayload("eazpire://wear-pair?t=PairTok_1"))
    }

    @Test
    fun rejectsOversizedAndUnicode() {
        assertNull(WearPairApi.parseTokenFromQrPayload("eazpire://wear-pair?t=" + "a".repeat(80)))
        assertNull(WearPairApi.parseTokenFromQrPayload("eazpire://wear-pair?t=bad token"))
        assertNull(WearPairApi.parseTokenFromQrPayload(""))
    }
}
