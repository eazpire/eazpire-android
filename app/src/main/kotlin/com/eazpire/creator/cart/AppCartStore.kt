package com.eazpire.creator.cart

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * In-memory cart store for app. Holds item count.
 * Checkout still happens on web (Shopify).
 */
object AppCartStore {
    var itemCount by mutableIntStateOf(0)
        private set

    fun add(quantity: Int) {
        itemCount += quantity
    }

    fun clear() {
        itemCount = 0
    }
}
