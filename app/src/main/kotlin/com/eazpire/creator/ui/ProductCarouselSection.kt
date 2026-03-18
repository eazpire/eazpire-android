package com.eazpire.creator.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** Home-Sektion: New Arrivals (neueste Produkte). Kategorien: zufällig gemischt. */
private data class HomeSectionData(
    val id: String,
    val titleKey: String,
    val collectionHandle: String?,
    val maxProducts: Int = 10
)

private val HOME_SECTION_DEFAULTS = mapOf(
    "newcomer" to "New Arrivals"
)
private val HOME_SECTIONS = listOf(
    HomeSectionData("newcomer", "home.newcomer", null, 10)
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
                        val result = api.getProducts(
                            collectionHandle = section.collectionHandle,
                            limit = section.maxProducts * 2
                        )
                        val list = if (result.products.isEmpty()) {
                            api.getProducts(limit = section.maxProducts * 2).products
                        } else result.products
                        val sorted = list
                            .sortedByDescending { it.createdAt.ifBlank { "0" } }
                            .take(section.maxProducts)
                        section.id to sorted
                    }
                }
                val catDeferred = async {
                    val raw = api.getProductsByCategories(CAROUSEL_CATEGORIES, limitPerCategory = 12)
                    raw.mapValues { (_, products) ->
                        products.shuffled(Random.Default)
                    }
                }
                productsByHomeSection = homeDeferred.await()
                productsByCategory = catDeferred.await()
            }
        }
    }

    val scrollState = rememberScrollState()
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) scrollState.animateScrollTo(0)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
    ) {
        HeroCarousel(
            onProductClick = onProductClick?.let { callback ->
                { handle -> callback(ProductClickWithCollection(handle, null, null)) }
            },
            onHotspotProductClick = onHotspotProductClick,
            productModalHandleState = productModalHandleState,
            fallbackProductHandle = productsByHomeSection["newcomer"]?.firstOrNull()?.handle
                ?: productsByCategory["women"]?.firstOrNull()?.handle
        )
        HOME_SECTIONS.forEach { section ->
            val products = productsByHomeSection[section.id].orEmpty()
            val displayTitle = t(section.titleKey, HOME_SECTION_DEFAULTS[section.id] ?: section.id)
            ProductCarousel(
                title = displayTitle,
                products = products,
                collectionHandle = section.collectionHandle,
                onTitleClick = null,
                onProductClick = onProductClick,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
        CAROUSEL_CATEGORIES.forEachIndexed { index, (title, handle) ->
            val products = productsByCategory[handle].orEmpty()
            val isLast = index == CAROUSEL_CATEGORIES.lastIndex
            val displayTitle = t(CAROUSEL_TITLE_KEYS[title] ?: title, title)
            ProductCarousel(
                title = displayTitle,
                products = products,
                collectionHandle = handle,
                onTitleClick = onCategoryClick?.let { { it(title, handle) } },
                onProductClick = onProductClick,
                modifier = Modifier.padding(bottom = if (isLast) 24.dp else 16.dp)
            )
        }
    }
}
