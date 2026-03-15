package com.eazpire.creator.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eazpire.creator.api.ShopifyProductsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val CAROUSEL_CATEGORIES = listOf(
    "Women" to "women",
    "Men" to "men",
    "Kids" to "kids",
    "Toddler" to "toddler",
    "Home & Living" to "home-living",
)

@Composable
fun ProductCarouselSection(
    onCurrentPageChange: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val api = remember { ShopifyProductsApi() }
    var productsByCategory by remember { mutableStateOf<Map<String, List<ShopifyProductsApi.ProductItem>>>(emptyMap()) }

    LaunchedEffect(Unit) {
        productsByCategory = withContext(Dispatchers.IO) {
            api.getProductsByCategories(CAROUSEL_CATEGORIES, limitPerCategory = 12)
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(listState.firstVisibleItemIndex) {
        val idx = listState.firstVisibleItemIndex
        if (idx in CAROUSEL_CATEGORIES.indices) {
            val handle = CAROUSEL_CATEGORIES[idx].second
            onCurrentPageChange?.invoke("/collections/$handle")
        }
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        itemsIndexed(CAROUSEL_CATEGORIES) { _, (title, handle) ->
            val products = productsByCategory[handle].orEmpty()
            ProductCarousel(
                title = title,
                products = products,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}
