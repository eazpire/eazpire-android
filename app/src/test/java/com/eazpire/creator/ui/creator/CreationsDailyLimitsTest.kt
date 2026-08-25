package com.eazpire.creator.ui.creator

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreationsDailyLimitsTest {
    @Test
    fun parseDailyLimitsMapsUploadGeneratePublish() {
        val json = JSONObject(
            """
            {
              "ok": true,
              "creation_limits_effective": {
                "mode": "daily",
                "upload_used": 3,
                "upload_cap": 20,
                "generate_used": 2,
                "generate_cap": 10
              },
              "listing_limits_effective": {
                "channels": {
                  "shopify": {
                    "listings_used_today": 1,
                    "listings_per_day": 5,
                    "channel_unlocked": true
                  }
                }
              }
            }
            """.trimIndent()
        )
        val snap = parseDailyLimitsSnapshot(json)
        assertEquals(3, snap.upload.used)
        assertEquals(20, snap.upload.cap)
        assertFalse(snap.upload.locked)
        assertEquals(2, snap.generate.used)
        assertEquals(1, snap.publish.used)
        assertEquals(5, snap.publish.cap)
        assertTrue(snap.showCountdown)
    }

    @Test
    fun parseJourneySlotUsagePrefersListingCapOverProductTypes() {
        val json = JSONObject(
            """
            {
              "ok": true,
              "journey_limits": {
                "max_active_design_slots": 50,
                "max_products": 12,
                "active_designs_used": 12,
                "products_used": 12
              },
              "listing_limits_effective": {
                "channels": {
                  "shopify": {
                    "listings_used_total": 87,
                    "listings_cap": 210,
                    "channel_unlocked": true
                  }
                }
              }
            }
            """.trimIndent()
        )
        val usage = parseJourneySlotUsage(json)
        assertEquals(50, usage.maxActiveDesignSlots)
        assertEquals(210, usage.maxProducts)
        assertEquals(12, usage.activeDesignsUsed)
        assertEquals(87, usage.productsUsed)
    }

    @Test
    fun parseJourneySlotUsageReadsCapsAndUsed() {
        val json = JSONObject(
            """
            {
              "ok": true,
              "journey_limits": {
                "max_active_design_slots": 50,
                "max_products": 3,
                "active_designs_used": 12,
                "products_used": 2
              }
            }
            """.trimIndent()
        )
        val usage = parseJourneySlotUsage(json)
        assertEquals(50, usage.maxActiveDesignSlots)
        assertEquals(3, usage.maxProducts)
        assertEquals(12, usage.activeDesignsUsed)
        assertEquals(2, usage.productsUsed)
    }

    @Test
    fun slotFillPercentCapsAt100() {
        assertEquals(0, slotFillPercent(0, 25))
        assertEquals(48, slotFillPercent(12, 25))
        assertEquals(100, slotFillPercent(30, 25))
        assertEquals(0, slotFillPercent(4, 0))
    }

    @Test
    fun formatCountdownUsesHoursThenMinutes() {
        assertEquals("2h 3m", formatDailyResetCountdown((2 * 3600 + 3 * 60) * 1000L))
        assertEquals("4m 5s", formatDailyResetCountdown((4 * 60 + 5) * 1000L))
        assertEquals("9s", formatDailyResetCountdown(9000L))
    }

    @Test
    fun parseSavedDesignKeepsRowWithoutPreview() {
        val obj = JSONObject("""{"id":"77","library_status":"active","title":"No image yet"}""")
        val design = parseSavedCreationDesign(obj)
        assertEquals("77", design?.id)
        assertEquals("active", design?.effectiveLibraryStatus())
        assertEquals("", design?.imageUrl)
    }

    @Test
    fun jsonNextCursorTreatsJsonNullAsAbsent() {
        val withNull = JSONObject("""{"ok":true,"next_cursor":null}""")
        assertEquals(null, com.eazpire.creator.api.jsonNextCursor(withNull))
        val missing = JSONObject("""{"ok":true}""")
        assertEquals(null, com.eazpire.creator.api.jsonNextCursor(missing))
        val present = JSONObject("""{"ok":true,"next_cursor":"abc"}""")
        assertEquals("abc", com.eazpire.creator.api.jsonNextCursor(present))
        val fake = JSONObject("""{"ok":true,"next_cursor":"null"}""")
        assertEquals(null, com.eazpire.creator.api.jsonNextCursor(fake))
    }

    @Test
    fun idlePublishSessionIdDoesNotMarkDesignPublishing() {
        val obj = JSONObject(
            """
            {
              "id": "88",
              "library_status": "active",
              "title": "Idle",
              "preview_url": "https://cdn.example/d.webp",
              "publish_active": false,
              "publish_session_id": null
            }
            """.trimIndent()
        )
        val design = parseSavedCreationDesign(obj)
        assertFalse(isCreationPublishActive(obj))
        assertFalse(design!!.publishActive)
        assertEquals(null, design.publishSessionId)
    }

    @Test
    fun inactiveIdlePublishSessionIdDoesNotMarkDesignPublishing() {
        val obj = JSONObject(
            """
            {
              "id": "89",
              "library_status": "inactive",
              "title": "Idle inactive",
              "publish_active": false,
              "publish_session_id": null
            }
            """.trimIndent()
        )
        assertFalse(parseSavedCreationDesign(obj)!!.publishActive)
    }

    @Test
    fun livePublishSessionMarksDesignPublishing() {
        val obj = JSONObject(
            """
            {
              "id": "90",
              "library_status": "active",
              "publish_active": true,
              "publish_session_id": "sess-live"
            }
            """.trimIndent()
        )
        val design = parseSavedCreationDesign(obj)!!
        assertTrue(design.publishActive)
        assertEquals("sess-live", design.publishSessionId)
    }

    @Test
    fun productParseUsesMockupAndPrintifyWhenFeaturedIsNull() {
        val obj = JSONObject(
            """
            {
              "product_key": "printify:1",
              "product_name": "Softstyle",
              "shopify_product_id": 555,
              "shopify_handle": null,
              "featured_image": null,
              "image_url": null,
              "mockup_image": "https://cdn.example/catalog-blank.webp",
              "printify_images": ["https://images-api.printify.com/mock-with-design.jpg"],
              "mockups_by_view": {
                "front": { "White": "https://cdn.example/published-front.webp" }
              }
            }
            """.trimIndent()
        )
        val product = parseCreationProduct(obj, 0)
        assertEquals("555", product.id)
        assertEquals(null, product.shopifyHandle)
        assertTrue(product.imageUrls.contains("https://images-api.printify.com/mock-with-design.jpg"))
        assertTrue(product.imageUrls.contains("https://cdn.example/published-front.webp"))
        assertEquals("https://images-api.printify.com/mock-with-design.jpg", product.imageUrl)
    }

    @Test
    fun productParsePrefersShopifyFeaturedImage() {
        val obj = JSONObject(
            """
            {
              "product_key": "printify:2",
              "product_name": "Hoodie",
              "featured_image": "https://cdn.shopify.com/s/files/real-preview.jpg",
              "mockup_image": "https://cdn.example/catalog-blank.webp"
            }
            """.trimIndent()
        )
        val urls = parseCreationProductImageUrls(obj)
        assertEquals("https://cdn.shopify.com/s/files/real-preview.jpg", urls.first())
    }

    @Test
    fun usableJwtSkipsExpiredToken() {
        val payload = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("""{"exp":1}""".toByteArray())
        val expired = "hdr.$payload.sig"
        assertEquals(null, com.eazpire.creator.api.usableJwt(expired))
        assertEquals("live-token", com.eazpire.creator.api.usableJwt("Bearer live-token"))
    }

    @Test
    fun looksOwnerAuthFailureReadsWarnAndError() {
        val warn = JSONObject("""{"ok":true,"items":[],"warn":"missing_owner_id"}""")
        assertTrue(com.eazpire.creator.api.looksOwnerAuthFailure(warn))
        val err = JSONObject("""{"ok":false,"error":"missing_owner_id"}""")
        assertTrue(com.eazpire.creator.api.looksOwnerAuthFailure(err))
        val ok = JSONObject("""{"ok":true,"items":[{"id":"1"}]}""")
        assertFalse(com.eazpire.creator.api.looksOwnerAuthFailure(ok))
    }
}
