package com.eazpire.creator.ar.poster

import org.junit.Assert.assertEquals
import org.junit.Test

class PosterArDimensionsTest {

    @Test
    fun parseInches_verticalPoster() {
        val size = PosterArDimensions.parseMeters("11.7\" x 16.5\" (Vertical)")
        assertEquals(0.297f, size.widthM, 0.002f)
        assertEquals(0.419f, size.heightM, 0.002f)
    }

    @Test
    fun parseCm_dimensions() {
        val size = PosterArDimensions.parseMeters("29.7 cm x 42.0 cm")
        assertEquals(0.297f, size.widthM, 0.001f)
        assertEquals(0.42f, size.heightM, 0.001f)
    }
}
