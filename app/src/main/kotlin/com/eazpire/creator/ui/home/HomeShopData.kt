package com.eazpire.creator.ui.home

import com.eazpire.creator.api.ShopifyProductsApi
import com.eazpire.creator.ui.CatalogProduct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Per global home category chip — product lists for one carousel row. */
typealias HomeCategoryPools = Map<String, List<ShopifyProductsApi.ProductItem>>

data class HomeCarouselSectionDef(
    val id: String,
    val titleKey: String,
    val titleDefault: String,
    val viewAllHandle: String?,
    val baseCollectionHandle: String?,
    val maxProducts: Int = 12,
)

val HOME_PRODUCT_SECTIONS: List<HomeCarouselSectionDef> = listOf(
    HomeCarouselSectionDef(
        id = "new_arrivals",
        titleKey = "eaz.nav.new-arrivals",
        titleDefault = "New Arrivals",
        viewAllHandle = "new-arrivals",
        baseCollectionHandle = null,
        maxProducts = 12,
    ),
    HomeCarouselSectionDef(
        id = "bestseller",
        titleKey = "eaz.nav.bestseller",
        titleDefault = "Bestseller",
        viewAllHandle = "bestsellers",
        baseCollectionHandle = "bestsellers",
        maxProducts = 12,
    ),
    HomeCarouselSectionDef(
        id = "personalizable",
        titleKey = "creator.filter_modal.personalizable",
        titleDefault = "Personalizable",
        viewAllHandle = null,
        baseCollectionHandle = null,
        maxProducts = 12,
    ),
)

private val CHIP_COLLECTION_CANDIDATES: Map<String, List<String?>> = mapOf(
    "all" to listOf(null),
    "women" to listOf("women"),
    "men" to listOf("men"),
    "kids" to listOf("kids"),
    "toddler" to listOf("toddler"),
    "accessories" to listOf("accessories", "accessoires"),
    "home-living" to listOf("home-living", "home-&-living"),
)

suspend fun loadHomeCategoryPools(
    api: ShopifyProductsApi,
    baseCollectionHandle: String?,
    maxProducts: Int,
): HomeCategoryPools = withContext(Dispatchers.IO) {
    coroutineScope {
        CHIP_COLLECTION_CANDIDATES.map { (chipId, handles) ->
            async {
                val products = loadProductsForChip(api, chipId, handles, baseCollectionHandle, maxProducts)
                chipId to products
            }
        }.awaitAll().toMap()
    }
}

private suspend fun loadProductsForChip(
    api: ShopifyProductsApi,
    chipId: String,
    handles: List<String?>,
    baseCollectionHandle: String?,
    maxProducts: Int,
): List<ShopifyProductsApi.ProductItem> {
    if (chipId == "all") {
        val handle = baseCollectionHandle
        val result = api.getProducts(collectionHandle = handle, limit = maxProducts * 3)
        return result.products
            .sortedByDescending { it.createdAt.ifBlank { "0" } }
            .take(maxProducts)
    }
    for (handle in handles) {
        if (handle.isNullOrBlank()) continue
        val result = api.getProducts(collectionHandle = handle, limit = maxProducts * 2)
        if (result.products.isNotEmpty()) {
            return result.products.take(maxProducts)
        }
    }
    return emptyList()
}

fun CatalogProduct.homeCategoryFilterKey(): String {
    val group = (categoryGroup ?: categoryKey ?: "")
        .trim()
        .lowercase()
        .replace("_", "-")
    return when (group) {
        "accessories" -> "accessories"
        "home-living", "home-&-living" -> "home-living"
        else -> "all"
    }
}

fun CatalogProduct.matchesHomeCategory(chipId: String): Boolean {
    if (chipId == "all") return true
    if (homeCategoryFilterKey() == chipId) return true
    val audiences = audience.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    if (audiences.contains("unisex")) return true
    return audiences.contains(chipId)
}

fun CatalogProduct.toHomeProductItem(): ShopifyProductsApi.ProductItem =
    ShopifyProductsApi.ProductItem(
        id = productKey.hashCode().toLong().let { if (it < 0) -it else it },
        title = title,
        handle = productKey,
        images = mockUrls,
        variantImages = mockUrls,
        url = "https://www.eazpire.com/pages/shop-create-catalog",
        metaProductKey = productKey,
    )
