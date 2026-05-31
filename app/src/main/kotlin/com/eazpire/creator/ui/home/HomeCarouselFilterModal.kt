package com.eazpire.creator.ui.home

import androidx.compose.runtime.Composable
import com.eazpire.creator.api.ShopifyProductsApi
import com.eazpire.creator.ui.CollectionFilterDrawer
import com.eazpire.creator.ui.ProductFilters

@Composable
internal fun HomeCarouselFilterModal(
    products: List<ShopifyProductsApi.ProductItem>,
    filters: ProductFilters,
    withinSearchQuery: String,
    onFiltersChange: (ProductFilters) -> Unit,
    onWithinSearchChange: (String) -> Unit,
    onDismiss: () -> Unit,
    labelForKey: (String, String) -> String,
) {
    CollectionFilterDrawer(
        filters = filters,
        products = products,
        withinSearchQuery = withinSearchQuery,
        onWithinSearchChange = onWithinSearchChange,
        onFiltersChange = onFiltersChange,
        onDismiss = onDismiss,
        t = labelForKey,
    )
}
