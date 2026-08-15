package com.eazpire.creator.ui.creator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
