package com.eazpire.creator.shop.sidebar

import org.json.JSONArray
import org.json.JSONObject

/** Web parity: [theme/assets/eaz-product-catalog-visibility.js] + CUSTOMER_DB product_catalog_v1 */
data class ProductCatalogPreferences(
    val excluded: Set<String>,
    val included: Set<String>,
    val sizeFilterActive: Boolean,
    val sizeVisibility: Map<String, List<String>>,
) {
    fun isPathVisible(pathId: String): Boolean {
        val id = pathId.trim()
        if (id.isEmpty()) return true
        if (id in included) return true
        if (id in excluded) return false
        val parts = id.split("--")
        for (i in 1 until parts.size) {
            val prefix = parts.subList(0, i + 1).joinToString("--")
            if (prefix in included) return true
            if (prefix in excluded) return false
        }
        return true
    }

    fun allowedSizes(productKey: String): List<String>? {
        if (!sizeFilterActive) return null
        return sizeVisibility[productKey.trim().lowercase()]
    }

    companion object {
        fun fromJson(obj: JSONObject?): ProductCatalogPreferences? {
            if (obj == null) return null
            return ProductCatalogPreferences(
                excluded = readStringSet(obj.optJSONArray("excluded")),
                included = readStringSet(obj.optJSONArray("included")),
                sizeFilterActive = obj.optBoolean("size_filter_active", false),
                sizeVisibility = readSizeMap(obj.optJSONObject("size_visibility")),
            )
        }

        private fun readStringSet(arr: JSONArray?): Set<String> {
            if (arr == null) return emptySet()
            return buildSet {
                for (i in 0 until arr.length()) {
                    val v = arr.optString(i, "").trim()
                    if (v.isNotEmpty()) add(v)
                }
            }
        }

        private fun readSizeMap(obj: JSONObject?): Map<String, List<String>> {
            if (obj == null) return emptyMap()
            val out = mutableMapOf<String, List<String>>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val arr = obj.optJSONArray(k) ?: continue
                val list = buildList {
                    for (i in 0 until arr.length()) {
                        val s = arr.optString(i, "").trim()
                        if (s.isNotEmpty()) add(s)
                    }
                }
                if (list.isNotEmpty()) out[k.lowercase()] = list
            }
            return out
        }
    }
}
