package com.eazpire.creator.cart

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists Storefront cart ID across app restarts.
 * Cart survives logout – guest cart until buyer identity is updated on login.
 */
class StorefrontCartStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var cartId: String?
        get() = prefs.getString(KEY_CART_ID, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit()
                .putString(KEY_CART_ID, value ?: "")
                .apply()
        }

    fun clear() {
        prefs.edit().remove(KEY_CART_ID).apply()
    }

    companion object {
        private const val PREFS_NAME = "eazpire_storefront_cart"
        private const val KEY_CART_ID = "cart_id"
    }
}
