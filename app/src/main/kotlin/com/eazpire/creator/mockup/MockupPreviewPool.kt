package com.eazpire.creator.mockup

import org.json.JSONArray
import org.json.JSONObject

/** Multi-mock shop preview pool (parity with eaz-mockup-preview-pool.js). */
object MockupPreviewPool {
    const val MAX_PREVIEW_MOCKS_PER_PRODUCT = 5
    /** Auto wearing mocks on PLP/carousels — disabled until shop preview ships. */
    const val SHOP_PREVIEW_AUTO_DISPLAY_ENABLED = false

    fun hashString(seed: String): Int {
        var h = 0
        for (c in seed) {
            h = ((h shl 5) - h + c.code) or 0
        }
        return kotlin.math.abs(h)
    }

    fun getPreviewIds(meta: JSONObject?): List<Long> {
        if (meta == null) return emptyList()
        val arr = meta.optJSONArray("preview_mockup_ids")
        if (arr != null && arr.length() > 0) {
            return (0 until arr.length()).mapNotNull { i ->
                arr.optLong(i, -1L).takeIf { it > 0 }
            }
        }
        val single = meta.optLong("mockup_id", -1L).takeIf { it > 0 }
            ?: meta.optString("mockup_id").toLongOrNull()?.takeIf { it > 0 }
        return if (single != null) listOf(single) else emptyList()
    }

    fun hasPreviewPool(meta: JSONObject?): Boolean = getPreviewIds(meta).isNotEmpty()

    fun isShopPreviewActive(meta: JSONObject?): Boolean {
        if (!SHOP_PREVIEW_AUTO_DISPLAY_ENABLED) return false
        if (meta == null) return false
        if (meta.has("shop_preview_enabled") && !meta.optBoolean("shop_preview_enabled", true)) {
            return false
        }
        return hasPreviewPool(meta)
    }

    fun pickMockupId(meta: JSONObject?, handle: String, colorIndex: Int): Long? {
        val ids = getPreviewIds(meta)
        if (ids.isEmpty() || !isShopPreviewActive(meta)) return null
        val seed = "$handle:$colorIndex"
        return ids[hashString(seed) % ids.size]
    }
}
