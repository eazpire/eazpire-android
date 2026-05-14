package com.eazpire.creator.shop.sidebar

import org.junit.Assert.assertEquals
import org.junit.Test

class NavHandleCanonicalTest {

    @Test
    fun mapsGermanSynonyms() {
        assertEquals("eaz.nav.women", navUiTranslationKey("frauen"))
        assertEquals("eaz.nav.men", navUiTranslationKey("manner"))
        assertEquals("eaz.nav.jewelry", navUiTranslationKey("schmuck"))
    }

    @Test
    fun mapsHyphenatedHandles() {
        assertEquals("eaz.nav.wall_art", navUiTranslationKey("wall-art"))
        assertEquals("eaz.nav.home_living", navUiTranslationKey("home-living"))
    }

    @Test
    fun passthroughUnknownHandle() {
        assertEquals("eaz.nav.custom-thing", navUiTranslationKey("custom-thing"))
    }
}
