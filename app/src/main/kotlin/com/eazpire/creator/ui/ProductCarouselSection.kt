package com.eazpire.creator.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyProductsApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.LocalTranslationStore
import com.eazpire.creator.locale.LocaleStore
import com.eazpire.creator.mockup.CustomerMockPreviewStore
import com.eazpire.creator.ui.home.HOME_PRODUCT_SECTIONS
import com.eazpire.creator.ui.home.HomeCategoryPools
import com.eazpire.creator.ui.home.HomeCategoryStrip
import com.eazpire.creator.ui.home.HomeCreatorsCarousel
import com.eazpire.creator.ui.home.loadHomeCategoryPoolsMissingChips
import com.eazpire.creator.ui.home.loadHomeSectionForChip
import com.eazpire.creator.ui.home.matchesHomeCategory
import com.eazpire.creator.ui.home.toHomeProductItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray

@Composable
fun ProductCarouselSection(
    onCurrentPageChange: ((String) -> Unit)? = null,
    onCategoryClick: ((title: String, handle: String) -> Unit)? = null,
    onProductClick: ((ProductClickWithCollection) -> Unit)? = null,
    onHotspotProductClick: ((String) -> Unit)? = null,
    onCreatorClick: ((String) -> Unit)? = null,
    onCreateScratchClick: ((CatalogProduct) -> Unit)? = null,
    productModalHandleState: MutableState<String?>? = null,
    scrollToTopTrigger: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = LocalTranslationStore.current
    val t = store?.let { { k: String, d: String -> it.t(k, d) } } ?: { _: String, d: String -> d }
    val api = remember { ShopifyProductsApi() }
    val tokenStore = remember { SecureTokenStore(context) }
    val ownerId = remember { tokenStore.getOwnerId().orEmpty() }
    val jwt = remember { runCatching { tokenStore.getJwt() }.getOrNull() }
    val creatorApi = remember(jwt) { CreatorApi(jwt = jwt) }
    val localeStore = remember { LocaleStore(context) }
    val region = remember { localeStore.getRegionCodeSync() }

    var mockPreviewRevision by remember { mutableIntStateOf(CustomerMockPreviewStore.revision) }
    LaunchedEffect(ownerId) {
        if (ownerId.isNotBlank()) {
            CustomerMockPreviewStore.loadMap(creatorApi, ownerId)
            mockPreviewRevision = CustomerMockPreviewStore.revision
        }
    }

    var selectedCategory by remember { mutableStateOf("all") }
    var promoProducts by remember { mutableStateOf<List<ShopifyProductsApi.ProductItem>>(emptyList()) }
    var sectionPools by remember { mutableStateOf<Map<String, HomeCategoryPools>>(emptyMap()) }
    var createScratchCatalog by remember { mutableStateOf<List<CatalogProduct>>(emptyList()) }
    /** Creators load only after promo + product carousels (top → bottom). */
    var loadCreatorsSection by remember { mutableStateOf(false) }

    LaunchedEffect(region) {
        loadCreatorsSection = false
        promoProducts = emptyList()
        sectionPools = emptyMap()
        createScratchCatalog = emptyList()

        val pools = mutableMapOf<String, HomeCategoryPools>()
        coroutineScope {
            val promoDeferred = async(Dispatchers.IO) {
                runCatching {
                    val j = creatorApi.listActiveShopPromotionProducts(localeStore.getCountryCodeSync())
                    ShopifyProductsApi.parseActivePromotionProductsResponse(j)
                }.getOrElse { emptyList() }
            }
            val sectionDefs = HOME_PRODUCT_SECTIONS
            val firstDef = sectionDefs.firstOrNull()
            val firstDeferred = firstDef?.let { def ->
                async(Dispatchers.IO) {
                    loadHomeSectionForChip(api, def.baseCollectionHandle, def.maxProducts, chipId = "all")
                }
            }
            promoProducts = promoDeferred.await()
            if (firstDef != null && firstDeferred != null) {
                pools[firstDef.id] = mapOf("all" to firstDeferred.await())
                sectionPools = pools.toMap()
            }
            for (def in sectionDefs.drop(1)) {
                val allProducts = withContext(Dispatchers.IO) {
                    loadHomeSectionForChip(api, def.baseCollectionHandle, def.maxProducts, chipId = "all")
                }
                pools[def.id] = mapOf("all" to allProducts)
                sectionPools = pools.toMap()
            }
        }

        createScratchCatalog = withContext(Dispatchers.IO) { loadCreateScratchCatalog(creatorApi, region) }

        loadCreatorsSection = true

        val fullPools = mutableMapOf<String, HomeCategoryPools>()
        for (def in HOME_PRODUCT_SECTIONS) {
            fullPools[def.id] = withContext(Dispatchers.IO) {
                loadHomeCategoryPoolsMissingChips(
                    api,
                    def.baseCollectionHandle,
                    def.maxProducts,
                    pools[def.id].orEmpty(),
                )
            }
            sectionPools = fullPools.toMap()
        }
    }

    LaunchedEffect(selectedCategory, sectionPools, region) {
        if (selectedCategory == "all") return@LaunchedEffect
        val needsLoad = HOME_PRODUCT_SECTIONS.any { def ->
            sectionPools[def.id]?.get(selectedCategory).isNullOrEmpty()
        }
        if (!needsLoad) return@LaunchedEffect
        val updated = sectionPools.toMutableMap()
        var changed = false
        for (def in HOME_PRODUCT_SECTIONS) {
            if (!updated[def.id]?.get(selectedCategory).isNullOrEmpty()) continue
            val products = withContext(Dispatchers.IO) {
                loadHomeSectionForChip(api, def.baseCollectionHandle, def.maxProducts, chipId = selectedCategory)
            }
            if (products.isEmpty()) continue
            val chipMap = updated[def.id].orEmpty().toMutableMap()
            chipMap[selectedCategory] = products
            updated[def.id] = chipMap
            changed = true
        }
        if (changed) sectionPools = updated
    }

    val listState = rememberLazyListState()
    val pinCategoryStrip by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) listState.animateScrollToItem(0)
    }

    val createScratchProducts = remember(createScratchCatalog, selectedCategory) {
        createScratchCatalog
            .filter { it.matchesHomeCategory(selectedCategory) }
            .take(16)
            .map { it.toHomeProductItem() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "hero") {
                HeroCarousel(
                    onProductClick = onProductClick?.let { callback ->
                        { handle -> callback(ProductClickWithCollection(handle, null, null)) }
                    },
                    onHotspotProductClick = onHotspotProductClick,
                    productModalHandleState = productModalHandleState,
                    fallbackProductHandle = sectionPools["new_arrivals"]?.get("all")?.firstOrNull()?.handle
                        ?: sectionPools["bestseller"]?.get("women")?.firstOrNull()?.handle,
                )
            }

            item(key = "category_strip") {
                HomeCategoryStrip(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it },
                    labelForKey = t,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

        if (promoProducts.isNotEmpty()) {
            item(key = "promotions") {
                val promoTitle = t("eaz.shop.promotions_title", "Promotions")
                ProductCarousel(
                    title = promoTitle,
                    products = promoProducts,
                    collectionHandle = EAZ_PROMOTIONS_COLLECTION_HANDLE,
                    onTitleClick = onCategoryClick?.let { cb -> { cb(promoTitle, EAZ_PROMOTIONS_COLLECTION_HANDLE) } },
                    onProductClick = onProductClick,
                    modifier = Modifier.padding(bottom = 6.dp),
                    promoProductLayout = true,
                    promoEndsPrefix = t("eaz.shop.promo_countdown_prefix", "Ends in"),
                    promoEndedLabel = t("eaz.shop.promo_countdown_ended", "Ended"),
                    promoNextDiscountPrefix = t("eaz.shop.promo_next_discount_prefix", "Discount in"),
                    promoNextPriceHintPrefix = t("eaz.shop.promo_next_price_hint_prefix", "Promo from"),
                    ownerId = ownerId,
                    creatorApi = creatorApi,
                    mockPreviewRevision = mockPreviewRevision,
                    lazyCardImages = true,
                )
            }
        }

        HOME_PRODUCT_SECTIONS.forEach { def ->
            val products = sectionPools[def.id]?.get(selectedCategory).orEmpty()
            if (products.isNotEmpty()) {
                item(key = "section_${def.id}") {
                    val displayTitle = t(def.titleKey, def.titleDefault)
                    ProductCarousel(
                        title = displayTitle,
                        products = products,
                        collectionHandle = def.viewAllHandle,
                        onTitleClick = def.viewAllHandle?.let { h ->
                            onCategoryClick?.let { cb -> { cb(displayTitle, h) } }
                        },
                        onProductClick = onProductClick,
                        modifier = Modifier.padding(bottom = 6.dp),
                        ownerId = ownerId,
                        creatorApi = creatorApi,
                        mockPreviewRevision = mockPreviewRevision,
                        lazyCardImages = true,
                    )
                }
            }
        }

        if (createScratchProducts.isNotEmpty()) {
            item(key = "create_scratch") {
                val title = t("eaz.home.create_from_scratch", "Create from Scratch")
                ProductCarousel(
                    title = title,
                    products = createScratchProducts,
                    collectionHandle = null,
                    onTitleClick = onCategoryClick?.let { cb ->
                        { cb(title, "shop-create-catalog") }
                    },
                    onProductClick = { click ->
                        val cat = createScratchCatalog.find { it.productKey == click.handle }
                        if (cat != null) {
                            onCreateScratchClick?.invoke(cat)
                        } else {
                            onProductClick?.invoke(click)
                        }
                    },
                    modifier = Modifier.padding(bottom = 6.dp),
                    ownerId = ownerId,
                    creatorApi = creatorApi,
                    mockPreviewRevision = mockPreviewRevision,
                    lazyCardImages = true,
                )
            }
        }

        if (loadCreatorsSection && onCreatorClick != null) {
            item(key = "creators") {
                HomeCreatorsCarousel(
                    creatorApi = creatorApi,
                    labelForKey = t,
                    onCreatorClick = onCreatorClick,
                )
            }
        }
        }

        if (pinCategoryStrip) {
            HomeCategoryStrip(
                selectedCategory = selectedCategory,
                onCategorySelected = { selectedCategory = it },
                labelForKey = t,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .zIndex(2f),
            )
        }
    }
}

private suspend fun loadCreateScratchCatalog(creatorApi: CreatorApi, region: String): List<CatalogProduct> =
    runCatching {
        val data = creatorApi.getShopCreateProductCatalog(region)
        if (!data.optBoolean("ok", false)) return@runCatching emptyList()
        val arr = data.optJSONArray("products") ?: JSONArray()
        val list = mutableListOf<CatalogProduct>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val pk = o.optString("product_key", "").trim()
            if (pk.isEmpty()) continue
            val availability = o.optString("catalog_availability", "").trim()
            val active = o.optInt("catalog_is_active", 0)
            val online =
                availability == "available" || active == 2 ||
                    (availability.isBlank() && active != 1)
            if (!online) continue
            val urls = mutableListOf<String>()
            val mu = o.optJSONArray("mock_urls")
            if (mu != null) {
                for (j in 0 until mu.length()) {
                    val u = mu.optString(j, "").trim()
                    if (u.isNotEmpty()) urls.add(u)
                }
            }
            if (urls.isEmpty()) {
                o.optString("preview_image_url", "").trim().takeIf { it.isNotEmpty() }?.let { urls.add(it) }
            }
            list.add(
                CatalogProduct(
                    productKey = pk,
                    title = o.optString("title", pk).ifBlank { pk },
                    mockUrls = urls,
                    categoryLeaf = o.optString("category_leaf", "").trim().ifBlank { null },
                    categoryKey = o.optString("category_key", "").trim().ifBlank { null },
                    categoryGroup = o.optString("category_group", "").trim().ifBlank { null },
                    audience = parseCatalogAudience(o.optJSONArray("audience")),
                ),
            )
        }
        list
    }.getOrElse { emptyList() }

private fun parseCatalogAudience(arr: JSONArray?): List<String> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        arr.optString(i, "").trim().takeIf { it.isNotEmpty() }
    }
}
