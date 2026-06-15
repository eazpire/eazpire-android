package com.eazpire.creator.ui.home

import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyProductsApi
import com.eazpire.creator.ui.CatalogProduct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
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

/** Max products per home carousel row (matches web worker `list-home-carousel-products`). */
const val HOME_MAX_PRODUCTS = 100

/** Fast first paint on home: show this many products per carousel row, then fill in background. */
const val HOME_INITIAL_PRODUCTS = 24

/** Batch size when paginating home carousel products. */
const val HOME_LAZY_BATCH = 24

/** Creators carousel: first paint count before loading the full set. */
const val HOME_INITIAL_CREATORS = 7

fun homeCarouselWorkerSlot(sectionId: String): String = when (sectionId) {
    "new_arrivals" -> "new-arrivals"
    "bestseller" -> "bestseller"
    "personalizable" -> "personalizable"
    else -> sectionId
}

fun homeCarouselSectionIdFromWorkerSlot(slot: String): String? = when (slot.trim().lowercase()) {
    "new-arrivals" -> "new_arrivals"
    "bestseller" -> "bestseller"
    "personalizable" -> "personalizable"
    else -> null
}

/** Parse worker `list-home-carousel-bootstrap` pools → promos + section lists. */
fun parseHomeCarouselBootstrapResponse(
    json: JSONObject,
): Pair<List<ShopifyProductsApi.ProductItem>, Map<String, List<ShopifyProductsApi.ProductItem>>> {
    if (!json.optBoolean("ok", false)) return emptyList<ShopifyProductsApi.ProductItem>() to emptyMap()
    val pools = json.optJSONObject("pools") ?: return emptyList<ShopifyProductsApi.ProductItem>() to emptyMap()
    var promos = emptyList<ShopifyProductsApi.ProductItem>()
    val sections = mutableMapOf<String, List<ShopifyProductsApi.ProductItem>>()
    for (slot in pools.keys()) {
        val entry = pools.optJSONObject(slot) ?: continue
        val products = ShopifyProductsApi.parseHomeCarouselProductsResponse(
            JSONObject()
                .put("ok", entry.optBoolean("ok", true))
                .put("products", entry.optJSONArray("products") ?: JSONArray()),
        )
        when (slot.trim().lowercase()) {
            "promotions" -> promos = products
            else -> homeCarouselSectionIdFromWorkerSlot(slot)?.let { sections[it] = products }
        }
    }
    return promos to sections
}

suspend fun loadCreateScratchCatalogFromWorker(creatorApi: CreatorApi, region: String): List<CatalogProduct> =
    withContext(Dispatchers.IO) {
        runCatching {
            val data = creatorApi.getShopCreateProductCatalog(region)
            if (!data.optBoolean("ok", false)) return@runCatching emptyList()
            val arr = data.optJSONArray("products") ?: JSONArray()
            val list = mutableListOf<CatalogProduct>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val pk = o.optString("product_key", "").trim()
                if (pk.isEmpty()) continue
                val availability = catalogAvailabilityFromJson(o)
                if (availability != "available") continue
                list.add(
                    CatalogProduct(
                        productKey = pk,
                        title = o.optString("title", pk).ifBlank { pk },
                        mockUrls = catalogPreviewUrlsFromJson(o),
                        catalogAvailability = availability,
                        categoryLeaf = o.optString("category_leaf", "").trim().ifBlank { null },
                        categoryKey = o.optString("category_key", "").trim().ifBlank { null },
                        categoryGroup = o.optString("category_group", "").trim().ifBlank { null },
                        audience = parseCatalogAudience(o.optJSONArray("audience")),
                    ),
                )
            }
            list
        }.getOrElse { emptyList() }
    }

private fun parseCatalogAudience(arr: JSONArray?): List<String> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        arr.optString(i, "").trim().takeIf { it.isNotEmpty() }
    }
}

/** Worker `list-home-carousel-products` — same sorting/dedupe as web home carousels. */
suspend fun loadHomeCarouselFromWorker(
    creatorApi: CreatorApi,
    sectionId: String,
    chipId: String = "all",
    limit: Int = HOME_MAX_PRODUCTS,
    personalizableMode: String = "shoppable",
    countryCode: String? = null,
): List<ShopifyProductsApi.ProductItem> = withContext(Dispatchers.IO) {
    runCatching {
        val slot = homeCarouselWorkerSlot(sectionId)
        val j = creatorApi.listHomeCarouselProducts(
            slot = slot,
            category = chipId,
            limit = limit.coerceIn(1, HOME_MAX_PRODUCTS),
            personalizableMode = personalizableMode,
            countryCode = countryCode,
        )
        ShopifyProductsApi.parseHomeCarouselProductsResponse(j)
    }.getOrElse { emptyList() }
}

fun resolveHomeSectionProducts(
    pools: HomeCategoryPools,
    chipId: String,
): List<ShopifyProductsApi.ProductItem> {
    val direct = pools[chipId].orEmpty()
    if (direct.isNotEmpty()) return direct
    if (chipId != "all") return emptyList()
    return pools.values.flatten().distinctBy { it.handle }
}

suspend fun loadHomeCarouselCategoryPools(
    creatorApi: CreatorApi,
    sectionId: String,
    limit: Int = HOME_MAX_PRODUCTS,
    personalizableMode: String = "shoppable",
    countryCode: String? = null,
): HomeCategoryPools = withContext(Dispatchers.IO) {
    coroutineScope {
        CHIP_COLLECTION_CANDIDATES.keys.map { chipId ->
            async {
                chipId to loadHomeCarouselFromWorker(
                    creatorApi,
                    sectionId,
                    chipId,
                    limit,
                    personalizableMode,
                    countryCode,
                )
            }
        }.awaitAll().toMap()
    }
}

suspend fun loadHomeCarouselCategoryPoolsMissingChips(
    creatorApi: CreatorApi,
    sectionId: String,
    limit: Int = HOME_MAX_PRODUCTS,
    existing: HomeCategoryPools,
    personalizableMode: String = "shoppable",
    countryCode: String? = null,
): HomeCategoryPools = withContext(Dispatchers.IO) {
    val out = existing.toMutableMap()
    val missing = CHIP_COLLECTION_CANDIDATES.keys.filter { !out.containsKey(it) }
    if (missing.isEmpty()) return@withContext out
    coroutineScope {
        missing.map { chipId ->
            async {
                chipId to loadHomeCarouselFromWorker(
                    creatorApi,
                    sectionId,
                    chipId,
                    limit,
                    personalizableMode,
                    countryCode,
                )
            }
        }.awaitAll().forEach { (chipId, products) -> out[chipId] = products }
    }
    out
}

suspend fun loadHomePromotionsFromWorker(
    creatorApi: CreatorApi,
    limit: Int = HOME_MAX_PRODUCTS,
    countryCode: String? = null,
): List<ShopifyProductsApi.ProductItem> = withContext(Dispatchers.IO) {
    runCatching {
        val j = creatorApi.listHomeCarouselProducts(
            slot = "promotions",
            limit = limit.coerceIn(1, HOME_MAX_PRODUCTS),
            countryCode = countryCode,
        )
        ShopifyProductsApi.parseHomeCarouselProductsResponse(j)
    }.getOrElse { emptyList() }
}

val HOME_PRODUCT_SECTIONS: List<HomeCarouselSectionDef> = listOf(
    HomeCarouselSectionDef(
        id = "new_arrivals",
        titleKey = "eaz.nav.new-arrivals",
        titleDefault = "New Arrivals",
        viewAllHandle = "new-arrivals",
        baseCollectionHandle = "new-arrivals",
        maxProducts = HOME_MAX_PRODUCTS,
    ),
    HomeCarouselSectionDef(
        id = "bestseller",
        titleKey = "eaz.nav.bestseller",
        titleDefault = "Bestseller",
        viewAllHandle = "bestsellers",
        baseCollectionHandle = "bestsellers",
        maxProducts = HOME_MAX_PRODUCTS,
    ),
    HomeCarouselSectionDef(
        id = "personalizable",
        titleKey = "creator.filter_modal.personalizable",
        titleDefault = "Personalizable",
        viewAllHandle = null,
        baseCollectionHandle = null,
        maxProducts = HOME_MAX_PRODUCTS,
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

/** Fast path: products for one home chip ([initialOnly] = first batch only for quick paint). */
suspend fun loadHomeSectionForChip(
    api: ShopifyProductsApi,
    baseCollectionHandle: String?,
    maxProducts: Int,
    chipId: String = "all",
    initialOnly: Boolean = false,
): List<ShopifyProductsApi.ProductItem> = withContext(Dispatchers.IO) {
    val handles = CHIP_COLLECTION_CANDIDATES[chipId] ?: listOf(null)
    loadProductsForChip(api, chipId, handles, baseCollectionHandle, maxProducts, initialOnly)
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
    val missing = CHIP_COLLECTION_CANDIDATES.keys.filter { !out.containsKey(it) }
    if (missing.isEmpty()) return@withContext out
    coroutineScope {
        missing.map { chipId ->
            async {
                val handles = CHIP_COLLECTION_CANDIDATES[chipId] ?: listOf(null)
                chipId to loadProductsForChip(api, chipId, handles, baseCollectionHandle, maxProducts)
            }
        }.awaitAll().forEach { (chipId, products) -> out[chipId] = products }
    }
    out
}

private suspend fun loadProductsForChip(
    api: ShopifyProductsApi,
    chipId: String,
    handles: List<String?>,
    baseCollectionHandle: String?,
    maxProducts: Int,
    initialOnly: Boolean = false,
): List<ShopifyProductsApi.ProductItem> =
    loadProductsForChipUpTo(api, chipId, handles, baseCollectionHandle, maxProducts, initialOnly = initialOnly)

/** Loads up to [maxProducts], optionally stopping after the first batch for fast paint. */
suspend fun loadProductsForChipUpTo(
    api: ShopifyProductsApi,
    chipId: String,
    handles: List<String?>,
    baseCollectionHandle: String?,
    maxProducts: Int,
    initialOnly: Boolean = false,
): List<ShopifyProductsApi.ProductItem> {
    val cap = maxProducts.coerceIn(1, HOME_MAX_PRODUCTS)
    val firstBatch = if (initialOnly) HOME_INITIAL_PRODUCTS.coerceAtMost(cap) else cap
    val merged = linkedMapOf<Long, ShopifyProductsApi.ProductItem>()
    var cursor: String? = null
    while (merged.size < firstBatch) {
        val limit = HOME_LAZY_BATCH.coerceAtMost(firstBatch - merged.size)
        val page = fetchProductsPage(api, chipId, handles, baseCollectionHandle, limit, cursor)
        if (page.products.isEmpty()) break
        page.products.forEach { p -> merged.putIfAbsent(p.id, p) }
        if (!page.hasNextPage || page.nextCursor.isNullOrBlank()) break
        cursor = page.nextCursor
    }
    val sorted = if (chipId == "all") {
        merged.values.sortedByDescending { it.createdAt.ifBlank { "0" } }
    } else {
        merged.values.toList()
    }
    return sorted.take(firstBatch)
}

private suspend fun fetchProductsPage(
    api: ShopifyProductsApi,
    chipId: String,
    handles: List<String?>,
    baseCollectionHandle: String?,
    limit: Int,
    cursor: String?,
): ShopifyProductsApi.ProductsResult {
    if (chipId == "all") {
        return api.getProducts(collectionHandle = baseCollectionHandle, limit = limit, cursor = cursor)
    }
    for (handle in handles) {
        if (handle.isNullOrBlank()) continue
        val result = api.getProducts(collectionHandle = handle, limit = limit, cursor = cursor)
        if (result.products.isNotEmpty()) return result
    }
    return ShopifyProductsApi.ProductsResult(emptyList(), hasNextPage = false, nextCursor = null)
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
        url = "https://www.eazpire.com/pages/shop-create",
        metaProductKey = productKey,
    )
