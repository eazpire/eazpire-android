package com.eazpire.creator.ui.header

import com.eazpire.creator.api.CreatorApi

/** Edit an existing favorite (pool or list) from the favorites modal. */
data class FavoriteEditContext(
    val productHandle: String,
    val customerId: String,
    val api: CreatorApi,
    val productId: String,
    val initialVariantId: String?,
    val activeView: String,
    val itemId: Long = 0L,
    val onSaved: () -> Unit,
    val onDismiss: () -> Unit,
)
