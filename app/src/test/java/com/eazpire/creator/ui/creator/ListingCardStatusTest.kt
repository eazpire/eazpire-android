package com.eazpire.creator.ui.creator

import com.eazpire.creator.util.isSampleProductId
import com.eazpire.creator.util.parseSamplePublishedId
import com.eazpire.creator.util.sampleHandle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListingCardStatusTest {
    @Test
    fun finishedSampleIsActiveSample() {
        val row = PublishedListingRow(
            productKey = "unisex-softstyle-cotton-tee",
            publishIntent = "sample_publish",
            completionStatus = "sample_publish",
        )
        assertTrue(isSampleListing(row))
        assertTrue(isOnlineListing(row))
        assertEquals(ListingCardKind.Sample, listingKind(row))
        assertEquals(ListingCardStatus.Active, listingCardStatus(checked = true, row = row))
    }

    @Test
    fun checkedWithoutRowIsQueueProduct() {
        assertEquals(ListingCardStatus.Queue, listingCardStatus(checked = true, row = null))
        assertEquals(ListingCardKind.Product, listingKind(null))
    }

    @Test
    fun shopifyCompleteIsActiveProduct() {
        val row = PublishedListingRow(
            productKey = "coffee-mug",
            publishIntent = "creator_publish",
            completionStatus = "complete",
        )
        assertFalse(isSampleListing(row))
        assertEquals(ListingCardStatus.Active, listingCardStatus(checked = true, row = row))
        assertEquals(ListingCardKind.Product, listingKind(row))
    }

    @Test
    fun sampleHandleHelpersMatchWeb() {
        assertEquals("sample-2752", sampleHandle(2752L))
        assertEquals(2752L, parseSamplePublishedId("sample-2752"))
        assertEquals(2752L, parseSamplePublishedId("SAMPLE-2752"))
        assertTrue(isSampleProductId("sample-2752"))
        assertNull(parseSamplePublishedId("softstyle-tee"))
        assertFalse(isSampleProductId("123456"))
    }
}
