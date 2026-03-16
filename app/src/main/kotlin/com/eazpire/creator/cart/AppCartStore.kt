package com.eazpire.creator.cart

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Cart store – syncs with Shopify cart via ShopifyCartApi.
 * Item count is updated when adding to cart; CartDrawer shows real cart in WebView.
 */
object AppCartStore {
    var itemCount by mutableIntStateOf(0)
        internal set

    fun setCount(count: Int) {
        itemCount = count.coerceAtLeast(0)
    }

    fun add(quantity: Int) {
        itemCount += quantity.coerceAtLeast(0)
    }

    fun clear() {
        itemCount = 0
    }
}
