package com.eazpire.creator.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eazpire.creator.api.ShopifyProductsApi
import com.eazpire.creator.i18n.LocalTranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val CAROUSEL_CATEGORIES = listOf(
    "Women" to "women",
    "Men" to "men",
    "Kids" to "kids",
    "Toddler" to "toddler",
    "Home & Living" to "home-living",
)
private val CAROUSEL_TITLE_KEYS = mapOf(
    "Women" to "sidebar.women", "Men" to "sidebar.men", "Kids" to "sidebar.kids",
    "Toddler" to "eaz.header.toddler", "Home & Living" to "menu.home-living"
)

@Composable
fun ProductCarouselSection(
    onCurrentPageChange: ((String) -> Unit)? = null,
    onCategoryClick: ((title: String, handle: String) -> Unit)? = null,
    onProductClick: ((ProductClickWithCollection) -> Unit)? = null,
    onHotspotProductClick: ((String) -> Unit)? = null,
    productModalHandleState: MutableState<String?>? = null,
    scrollToTopTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    val t = LocalTranslationStore.current?.let { { k: String, d: String -> it.t(k, d) } } ?: { _: String, d: String -> d }
    val api = remember { ShopifyProductsApi() }
    var productsByCategory by remember { mutableStateOf<Map<String, List<ShopifyProductsApi.ProductItem>>>(emptyMap()) }

    LaunchedEffect(Unit) {
        productsByCategory = withContext(Dispatchers.IO) {
            api.getProductsByCategories(CAROUSEL_CATEGORIES, limitPerCategory = 12)
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) listState.animateScrollToItem(0)
    }
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        val idx = listState.firstVisibleItemIndex
        val offset = listState.firstVisibleItemScrollOffset
        if (idx == 0 && offset == 0) {
            onCurrentPageChange?.invoke("/")
        } else if (idx in CAROUSEL_CATEGORIES.indices) {
            val handle = CAROUSEL_CATEGORIES[idx].second
            onCurrentPageChange?.invoke("/collections/$handle")
        } else {
            onCurrentPageChange?.invoke("/")
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        HeroCarousel(
            onProductClick = onProductClick?.let { callback ->
                { handle -> callback(ProductClickWithCollection(handle, null, null)) }
            },
            onHotspotProductClick = onHotspotProductClick,
            productModalHandleState = productModalHandleState,
            fallbackProductHandle = productsByCategory["women"]?.firstOrNull()?.handle
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 0.dp, bottom = 0.dp)
        ) {
        itemsIndexed(CAROUSEL_CATEGORIES) { index, (title, handle) ->
            val products = productsByCategory[handle].orEmpty()
            val isLast = index == CAROUSEL_CATEGORIES.lastIndex
            val displayTitle = t(CAROUSEL_TITLE_KEYS[title] ?: title, title)
            ProductCarousel(
                title = displayTitle,
                products = products,
                collectionHandle = handle,
                onTitleClick = onCategoryClick?.let { { it(title, handle) } },
                onProductClick = onProductClick,
                modifier = Modifier.padding(bottom = if (isLast) 0.dp else 16.dp)
            )
        }
        }
    }
}
