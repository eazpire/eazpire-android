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
}
