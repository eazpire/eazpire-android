package com.eazpire.creator.favorites

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** Increment to trigger MainHeader to refetch favorites count */
object FavoritesRefreshTrigger {
    var value by mutableIntStateOf(0)
        private set

    fun trigger() {
        value++
    }
}
