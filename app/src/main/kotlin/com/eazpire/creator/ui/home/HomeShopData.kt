package com.eazpire.creator.ui.home

import com.eazpire.creator.api.ShopifyProductsApi
import com.eazpire.creator.ui.CatalogProduct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject

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

/** Fast first paint on home: show this many products per carousel row, then fill in background. */
const val HOME_INITIAL_PRODUCTS = 10

/** Creators carousel: first paint count before loading the full set. */
const val HOME_INITIAL_CREATORS = 7

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

/** Fast path: products for the default home chip only (shown first). */
suspend fun loadHomeSectionForChip(
    api: ShopifyProductsApi,
    baseCollectionHandle: String?,
    maxProducts: Int,
    chipId: String = "all",
): List<ShopifyProductsApi.ProductItem> = withContext(Dispatchers.IO) {
    val handles = CHIP_COLLECTION_CANDIDATES[chipId] ?: listOf(null)
    loadProductsForChip(api, chipId, handles, baseCollectionHandle, maxProducts)
}

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

/** Fills chip pools not yet loaded (e.g. after initial "all"-only pass). */
suspend fun loadHomeCategoryPoolsMissingChips(
    api: ShopifyProductsApi,
    baseCollectionHandle: String?,
    maxProducts: Int,
    existing: HomeCategoryPools,
): HomeCategoryPools = withContext(Dispatchers.IO) {
    val out = existing.toMutableMap()
    CHIP_COLLECTION_CANDIDATES.keys.forEach { chipId ->
        if (!out[chipId].isNullOrEmpty()) return@forEach
        val handles = CHIP_COLLECTION_CANDIDATES[chipId] ?: listOf(null)
        out[chipId] = loadProductsForChip(api, chipId, handles, baseCollectionHandle, maxProducts)
    }
    out
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
        val fetchLimit = (maxProducts * 3).coerceAtMost(36)
        val result = api.getProducts(collectionHandle = handle, limit = fetchLimit)
        return result.products
            .sortedByDescending { it.createdAt.ifBlank { "0" } }
            .take(maxProducts)
    }
    for (handle in handles) {
        if (handle.isNullOrBlank()) continue
        val fetchLimit = (maxProducts * 2).coerceAtMost(24)
        val result = api.getProducts(collectionHandle = handle, limit = fetchLimit)
        if (result.products.isNotEmpty()) {
            return result.products.take(maxProducts)
        }
    }
    return emptyList()
}

fun catalogPreviewUrlsFromJson(o: JSONObject): List<String> {
    val imagesArr = o.optJSONArray("images")
    if (imagesArr != null && imagesArr.length() > 0) {
        val first = imagesArr.optString(0, "").trim()
        if (first.isNotEmpty()) return listOf(first)
    }
    val preview = o.optString("preview_image_url", "").trim()
    if (preview.isNotEmpty()) return listOf(preview)
    val mocks = o.optJSONArray("mock_urls")
    if (mocks != null && mocks.length() > 0) {
        val first = mocks.optString(0, "").trim()
        if (first.isNotEmpty()) return listOf(first)
    }
    return emptyList()
}

/** Matches web `catalogAvailabilityOf` in eaz-shop-create-catalog-page.js */
fun catalogAvailabilityFromJson(o: JSONObject): String {
    if (o.optString("catalog_availability", "").trim().equals("coming_soon", ignoreCase = true)) {
        return "coming_soon"
    }
    if (o.optInt("catalog_is_active", 0) == 1) return "coming_soon"
    return "available"
}

fun CatalogProduct.isCatalogAvailable(): Boolean = catalogAvailability != "coming_soon"

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
