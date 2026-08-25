package com.eazpire.creator.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreationsProductsPagingTest {
    @Test
    fun firstBatchIsCappedToTotal() {
        assertEquals(0, initialCreationsVisibleCount(0))
        assertEquals(7, initialCreationsVisibleCount(7))
        assertEquals(CREATIONS_PRODUCTS_PER_PAGE, initialCreationsVisibleCount(80))
    }

    @Test
    fun nextBatchGrowsUntilTotal() {
        assertEquals(24, nextCreationsVisibleCount(0, 80))
        assertEquals(48, nextCreationsVisibleCount(24, 80))
        assertEquals(80, nextCreationsVisibleCount(72, 80))
        assertEquals(80, nextCreationsVisibleCount(80, 80))
        assertEquals(0, nextCreationsVisibleCount(0, 0))
    }

    @Test
    fun appendWhenNearEndOfWindow() {
        assertTrue(shouldAppendCreationsBatch(20, 24, 80))
        assertFalse(shouldAppendCreationsBatch(10, 24, 80))
        assertFalse(shouldAppendCreationsBatch(80, 80, 80))
        assertFalse(shouldAppendCreationsBatch(0, 0, 80))
    }
}
