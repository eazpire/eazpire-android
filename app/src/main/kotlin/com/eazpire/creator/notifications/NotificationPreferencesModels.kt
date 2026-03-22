package com.eazpire.creator.notifications

import org.json.JSONObject

data class NotificationPrefs(
    val shopMaster: Boolean = true,
    val creatorMaster: Boolean = true,
    val shop: Map<String, Boolean> = defaultShop(),
    val creator: Map<String, Boolean> = defaultCreator()
) {
    companion object {
        fun defaultShop(): Map<String, Boolean> = mapOf(
            "cart_reminder" to true,
            "orders" to true,
            "promotions" to true,
            "promotions_new" to true,
            "promotions_ending_soon" to true
        )

        fun defaultCreator(): Map<String, Boolean> = mapOf(
            "generations" to true,
            "design_saved" to true,
            "product_published" to true,
            "community" to true,
            "other" to true
        )

        fun fromJson(o: JSONObject): NotificationPrefs {
            val shopObj = o.optJSONObject("shop")
            val creatorObj = o.optJSONObject("creator")
            return NotificationPrefs(
                shopMaster = o.optBoolean("shop_master", true),
                creatorMaster = o.optBoolean("creator_master", true),
                shop = parseBoolMap(shopObj, defaultShop()),
                creator = parseBoolMap(creatorObj, defaultCreator())
            )
        }

        private fun parseBoolMap(src: JSONObject?, defaults: Map<String, Boolean>): Map<String, Boolean> {
            val out = defaults.toMutableMap()
            if (src == null) return out
            for (k in defaults.keys) {
                if (src.has(k)) out[k] = src.optBoolean(k, defaults[k] ?: true)
            }
            return out
        }
    }

    fun toJson(): JSONObject {
        val s = JSONObject()
        shop.forEach { (k, v) -> s.put(k, v) }
        val c = JSONObject()
        creator.forEach { (k, v) -> c.put(k, v) }
        return JSONObject()
            .put("shop_master", shopMaster)
            .put("creator_master", creatorMaster)
            .put("shop", s)
            .put("creator", c)
    }
}

object NotificationCategoryMapping {
    /** Align with worker categoryToShopPreferenceKey. */
    fun categoryToShopKey(category: String?): String {
        val c = category?.lowercase() ?: return "promotions_new"
        if (c.contains("ending") || c.contains("ends_soon") || c.contains("ending_soon")) {
            return "promotions_ending_soon"
        }
        if (c.contains("cart") || c.contains("abandon")) return "cart_reminder"
        if (c.contains("shop_order")) return "orders"
        return "promotions_new"
    }

    /** Align with worker categoryToCreatorPreferenceKey. */
    fun categoryToCreatorKey(category: String?): String {
        val c = category?.lowercase() ?: return "other"
        if (c.contains("saved") || c.contains("uploaded")) return "design_saved"
        if (c.contains("published") || c.contains("removed_product") || c.contains("product")) return "product_published"
        if (c.contains("video") || c.contains("hero") || c.contains("job") ||
            c.contains("generat") || c.contains("mockup")
        ) {
            return "generations"
        }
        if (c.contains("community") || c.contains("mentor") || c.contains("referral") ||
            c.contains("gift") || c.contains("organic")
        ) {
            return "community"
        }
        return "other"
    }
}
