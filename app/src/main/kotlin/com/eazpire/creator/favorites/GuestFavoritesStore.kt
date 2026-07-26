package com.eazpire.creator.favorites

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Guest favorites via SharedPreferences — mirrors web `favorites.js` LocalStorage path
 * (`eaz_favorites` / product_id + optional variant_id).
 */
class GuestFavoritesStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun list(): List<GuestFavoriteItem> {
        val raw = prefs.getString(KEY_ITEMS, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val productId = o.optString("product_id", "").trim()
                if (productId.isEmpty()) return@mapNotNull null
                GuestFavoriteItem(
                    productId = productId,
                    variantId = o.optString("variant_id", "").trim().ifBlank { null },
                    productTitle = o.optString("product_title", "").trim().ifBlank { null },
                    productImage = o.optString("product_image", "").trim().ifBlank { null }
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun count(): Int = list().size

    fun has(productId: String, variantId: String? = null): Boolean {
        val pid = productId.trim()
        if (pid.isEmpty()) return false
        val vid = variantId?.trim()?.takeIf { it.isNotBlank() }
        return list().any {
            it.productId == pid && (vid == null || it.variantId == vid || it.variantId == null)
        }
    }

    fun add(
        productId: String,
        variantId: String? = null,
        productTitle: String? = null,
        productImage: String? = null
    ): Boolean {
        val pid = productId.trim()
        if (pid.isEmpty()) return false
        val items = list().toMutableList()
        if (items.any { it.productId == pid && it.variantId == variantId?.trim()?.takeIf { v -> v.isNotBlank() } }) {
            return true
        }
        items.add(
            0,
            GuestFavoriteItem(
                productId = pid,
                variantId = variantId?.trim()?.takeIf { it.isNotBlank() },
                productTitle = productTitle?.trim()?.takeIf { it.isNotBlank() },
                productImage = productImage?.trim()?.takeIf { it.isNotBlank() }
            )
        )
        persist(items)
        return true
    }

    fun remove(productId: String, variantId: String? = null): Boolean {
        val pid = productId.trim()
        if (pid.isEmpty()) return false
        val vid = variantId?.trim()?.takeIf { it.isNotBlank() }
        val before = list()
        val after = before.filterNot {
            it.productId == pid && (vid == null || it.variantId == vid || it.variantId == null)
        }
        if (after.size == before.size) return false
        persist(after)
        return true
    }

    private fun persist(items: List<GuestFavoriteItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("product_id", item.productId)
                    item.variantId?.let { put("variant_id", it) }
                    item.productTitle?.let { put("product_title", it) }
                    item.productImage?.let { put("product_image", it) }
                }
            )
        }
        prefs.edit()
            .putString(KEY_ITEMS, arr.toString())
            .putInt(KEY_COUNT, items.size)
            .apply()
    }

    data class GuestFavoriteItem(
        val productId: String,
        val variantId: String? = null,
        val productTitle: String? = null,
        val productImage: String? = null
    )

    companion object {
        private const val PREFS_NAME = "eazpire_guest_favorites"
        private const val KEY_ITEMS = "items"
        private const val KEY_COUNT = "count"
    }
}
