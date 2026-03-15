package com.eazpire.creator.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

private val CAROUSEL_CATEGORIES = listOf(
    "Women" to "women",
    "Men" to "men",
    "Kids" to "kids",
    "Toddler" to "toddler",
    "Home & Living" to "home-living",
)

@Composable
fun ProductCarouselSection(modifier: Modifier = Modifier) {
    val api = remember { ShopifyProductsApi() }
    var productsByCategory by remember { mutableStateOf<Map<String, List<ShopifyProductsApi.ProductItem>>>(emptyMap()) }

    LaunchedEffect(Unit) {
        productsByCategory = withContext(Dispatchers.IO) {
            api.getProductsByCategories(CAROUSEL_CATEGORIES, limitPerCategory = 12)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp)
    ) {
        CAROUSEL_CATEGORIES.forEach { (title, handle) ->
            val products = productsByCategory[handle].orEmpty()
            ProductCarousel(
                title = title,
                products = products,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }
}
