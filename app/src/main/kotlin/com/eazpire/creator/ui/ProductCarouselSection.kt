package com.eazpire.creator.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

private sealed class HomeListItem {
    data class Section(val section: HomeSectionData) : HomeListItem()
    data class Category(val title: String, val handle: String) : HomeListItem()
}

/** Web-ähnliche Home-Sektionen: Newcomer, Neuheiten, Coming Soon (Karussell) */
private data class HomeSectionData(
    val id: String,
    val titleKey: String,
    val collectionHandle: String,
    val maxProducts: Int = 10
)

private val HOME_SECTION_DEFAULTS = mapOf(
    "newcomer" to "Newcomer",
    "neuheiten" to "Neuheiten",
    "coming_soon" to "Coming Soon"
)
private val HOME_SECTIONS = listOf(
    HomeSectionData("newcomer", "home.newcomer", "bestseller", 10),
    HomeSectionData("neuheiten", "home.neuheiten", "all", 10),
    HomeSectionData("coming_soon", "home.coming_soon", "coming-soon", 10)
)

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
    val store = LocalTranslationStore.current
    val t = store?.let { { k: String, d: String -> it.t(k, d) } } ?: { _: String, d: String -> d }
    val api = remember { ShopifyProductsApi() }
    var productsByHomeSection by remember { mutableStateOf<Map<String, List<ShopifyProductsApi.ProductItem>>>(emptyMap()) }
    var productsByCategory by remember { mutableStateOf<Map<String, List<ShopifyProductsApi.ProductItem>>>(emptyMap()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            coroutineScope {
                val homeDeferred = async {
                    HOME_SECTIONS.associate { section ->
                        val handle = section.collectionHandle.ifBlank { "all" }
                        val result = api.getProducts(
                            collectionHandle = if (handle == "all") null else handle,
                            limit = section.maxProducts
                        )
                        val fallback = if (result.products.isEmpty()) {
                            api.getProducts(limit = section.maxProducts).products
                        } else result.products
                        section.id to fallback
                    }
                }
                val catDeferred = async {
                    api.getProductsByCategories(CAROUSEL_CATEGORIES, limitPerCategory = 12)
                }
                productsByHomeSection = homeDeferred.await()
                productsByCategory = catDeferred.await()
            }
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) listState.animateScrollToItem(0)
    }
    val categoryStartIndex = HOME_SECTIONS.size
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        val idx = listState.firstVisibleItemIndex
        when {
            idx < categoryStartIndex -> onCurrentPageChange?.invoke("/")
            idx >= categoryStartIndex -> {
                val catIdx = idx - categoryStartIndex
                if (catIdx in CAROUSEL_CATEGORIES.indices) {
                    onCurrentPageChange?.invoke("/collections/${CAROUSEL_CATEGORIES[catIdx].second}")
                } else {
                    onCurrentPageChange?.invoke("/")
                }
            }
            else -> onCurrentPageChange?.invoke("/")
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        HeroCarousel(
            onProductClick = onProductClick?.let { callback ->
                { handle -> callback(ProductClickWithCollection(handle, null, null)) }
            },
            onHotspotProductClick = onHotspotProductClick,
            productModalHandleState = productModalHandleState,
            fallbackProductHandle = productsByHomeSection["newcomer"]?.firstOrNull()?.handle
                ?: productsByCategory["women"]?.firstOrNull()?.handle
        )
        val allItems = remember(HOME_SECTIONS, CAROUSEL_CATEGORIES) {
            HOME_SECTIONS.map { HomeListItem.Section(it) } +
                CAROUSEL_CATEGORIES.map { (title, handle) -> HomeListItem.Category(title, handle) }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 0.dp, bottom = 0.dp)
        ) {
            itemsIndexed(
                items = allItems,
                key = { _, item ->
                    when (item) {
                        is HomeListItem.Section -> "home-${item.section.id}"
                        is HomeListItem.Category -> "cat-${item.handle}"
                    }
                }
            ) { index, item ->
                when (item) {
                    is HomeListItem.Section -> {
                        val s = item.section
                        val products = productsByHomeSection[s.id].orEmpty()
                        val displayTitle = t(s.titleKey, HOME_SECTION_DEFAULTS[s.id] ?: s.id)
                        ProductCarousel(
                            title = displayTitle,
                            products = products,
                            collectionHandle = s.collectionHandle,
                            onTitleClick = null,
                            onProductClick = onProductClick,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                    is HomeListItem.Category -> {
                        val products = productsByCategory[item.handle].orEmpty()
                        val isLast = index == allItems.lastIndex
                        val displayTitle = t(CAROUSEL_TITLE_KEYS[item.title] ?: item.title, item.title)
                        ProductCarousel(
                            title = displayTitle,
                            products = products,
                            collectionHandle = item.handle,
                            onTitleClick = onCategoryClick?.let { { it(item.title, item.handle) } },
                            onProductClick = onProductClick,
                            modifier = Modifier.padding(bottom = if (isLast) 0.dp else 16.dp)
                        )
                    }
                }
            }
        }
    }
}
