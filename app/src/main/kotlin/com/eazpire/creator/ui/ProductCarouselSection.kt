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
import com.eazpire.creator.ui.home.HOME_INITIAL_CREATORS
import com.eazpire.creator.ui.home.HOME_INITIAL_PRODUCTS
import com.eazpire.creator.ui.home.HOME_MAX_PRODUCTS
import com.eazpire.creator.ui.home.HOME_PRODUCT_SECTIONS
import com.eazpire.creator.ui.home.HomeCategoryPools
import com.eazpire.creator.ui.home.HomeCategoryStrip
import com.eazpire.creator.ui.home.HomeCarouselFilterModal
import com.eazpire.creator.ui.home.HomeCreateScratchCarousel
import com.eazpire.creator.ui.HeroImage
import com.eazpire.creator.ui.fetchHeroImagesForHome
import com.eazpire.creator.ui.home.HomeCreatorsCarousel
import com.eazpire.creator.ui.home.ShopCreatorCard
import com.eazpire.creator.ui.home.catalogAvailabilityFromJson
import com.eazpire.creator.ui.home.loadShopCreatorsForHome
import com.eazpire.creator.ui.home.catalogPreviewUrlsFromJson
import com.eazpire.creator.ui.home.loadHomeCarouselFromWorker
import com.eazpire.creator.ui.home.loadHomePromotionsFromWorker
import com.eazpire.creator.ui.home.matchesHomeCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
    reloadTrigger: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val store = LocalTranslationStore.current
    val t = store?.let { { k: String, d: String -> it.t(k, d) } } ?: { _: String, d: String -> d }
    val tokenStore = remember { SecureTokenStore(context) }
    val ownerId = remember { tokenStore.getOwnerId().orEmpty() }
    val jwt = remember { runCatching { tokenStore.getJwt() }.getOrNull() }
    val creatorApi = remember(jwt) { CreatorApi(jwt = jwt) }
    val localeStore = remember { LocaleStore(context) }
    val region = remember { localeStore.getRegionCodeSync() }

    var mockPreviewRevision by remember { mutableIntStateOf(CustomerMockPreviewStore.revision) }
    LaunchedEffect(ownerId, reloadTrigger) {
        if (ownerId.isNotBlank()) {
            CustomerMockPreviewStore.loadMap(creatorApi, ownerId, force = reloadTrigger > 0)
            mockPreviewRevision = CustomerMockPreviewStore.revision
        }
    }

    var selectedCategory by remember { mutableStateOf("all") }
    var filterModalVisible by remember { mutableStateOf(false) }
    var productFilters by remember { mutableStateOf(PlpTriFilterSelection()) }
    var withinSearchQuery by remember { mutableStateOf("") }
    var loadingCategories by remember { mutableStateOf(setOf<String>()) }

    fun filterCarouselProducts(list: List<ShopifyProductsApi.ProductItem>): List<ShopifyProductsApi.ProductItem> {
        return applyCollectionWithinSearchFilter(
            applyCollectionProductFilters(list, productFilters),
            withinSearchQuery,
        )
    }

    var promoProducts by remember { mutableStateOf<List<ShopifyProductsApi.ProductItem>>(emptyList()) }
    var sectionPools by remember { mutableStateOf<Map<String, HomeCategoryPools>>(emptyMap()) }
    var createScratchCatalog by remember { mutableStateOf<List<CatalogProduct>>(emptyList()) }
    var homePoolsBootstrapping by remember { mutableStateOf(true) }

    val homeFilterPool = remember(selectedCategory, sectionPools, promoProducts) {
        buildList {
            addAll(promoProducts)
            HOME_PRODUCT_SECTIONS.forEach { def ->
                addAll(sectionPools[def.id]?.get(selectedCategory).orEmpty())
            }
        }.distinctBy { it.handle }
    }

    val filterActive = !productFilters.isEmpty() || withinSearchQuery.isNotBlank()
    /** Creators load only after promo + product carousels (top → bottom). */
    var loadCreatorsSection by remember { mutableStateOf(false) }

    /** Cached in parent so LazyColumn scroll-off does not re-fetch hero / creators. */
    var homeHeroImages by remember { mutableStateOf<List<HeroImage>>(emptyList()) }
    var homeCreators by remember { mutableStateOf<List<ShopCreatorCard>>(emptyList()) }
    var homeCreatorsSort by remember { mutableStateOf("recommend") }
    var homeCreatorsLoading by remember { mutableStateOf(false) }

    val heroFallbackHandle =
        remember(sectionPools) {
            sectionPools["new_arrivals"]?.get("all")?.firstOrNull()?.handle
                ?: sectionPools["bestseller"]?.get("all")?.firstOrNull()?.handle
        }

    LaunchedEffect(loadCreatorsSection, homeCreatorsSort, reloadTrigger) {
        if (!loadCreatorsSection) return@LaunchedEffect
        homeCreatorsLoading = homeCreators.isEmpty()
        val initial = loadShopCreatorsForHome(creatorApi, homeCreatorsSort, HOME_INITIAL_CREATORS)
        homeCreators = initial
        homeCreatorsLoading = false
        if (initial.size < 20) {
            val full = loadShopCreatorsForHome(creatorApi, homeCreatorsSort, 20)
            if (full.size > initial.size) homeCreators = full
        }
    }

    LaunchedEffect(region, reloadTrigger) {
        loadCreatorsSection = false
        promoProducts = emptyList()
        sectionPools = emptyMap()
        createScratchCatalog = emptyList()
        homePoolsBootstrapping = true
        homeHeroImages = emptyList()
        homeCreators = emptyList()

        val countryCode = localeStore.getCountryCodeSync()
        val pools = mutableMapOf<String, HomeCategoryPools>()
        coroutineScope {
            val heroDeferred = async(Dispatchers.IO) {
                fetchHeroImagesForHome(CreatorApi(), region, heroFallbackHandle)
            }
            val promoDeferred = async(Dispatchers.IO) {
                loadHomePromotionsFromWorker(creatorApi, HOME_INITIAL_PRODUCTS, countryCode)
            }
            val scratchDeferred = async(Dispatchers.IO) {
                loadCreateScratchCatalog(creatorApi, region)
            }
            val sectionDeferreds = HOME_PRODUCT_SECTIONS.map { def ->
                async(Dispatchers.IO) {
                    def.id to loadHomeCarouselFromWorker(
                        creatorApi,
                        def.id,
                        chipId = "all",
                        limit = HOME_INITIAL_PRODUCTS.coerceAtMost(HOME_MAX_PRODUCTS),
                        countryCode = countryCode,
                    )
                }
            }
            homeHeroImages = heroDeferred.await()
            promoProducts = promoDeferred.await()
            createScratchCatalog = scratchDeferred.await()
            loadCreatorsSection = true
            sectionDeferreds.forEach { deferred ->
                val (id, products) = deferred.await()
                pools[id] = mapOf("all" to products)
                sectionPools = pools.toMap()
            }
            homePoolsBootstrapping = false

            launch(Dispatchers.IO) {
                val updated = sectionPools.toMutableMap()
                coroutineScope {
                    HOME_PRODUCT_SECTIONS.map { def ->
                        async {
                            val fullProducts = loadHomeCarouselFromWorker(
                                creatorApi,
                                def.id,
                                chipId = "all",
                                limit = HOME_MAX_PRODUCTS,
                                countryCode = countryCode,
                            )
                            def.id to fullProducts
                        }
                    }.forEach { deferred ->
                        val (id, fullProducts) = deferred.await()
                        updated[id] = mapOf("all" to fullProducts)
                        sectionPools = updated.toMap()
                    }
                }
            }
            launch(Dispatchers.IO) {
                val promos = loadHomePromotionsFromWorker(creatorApi, HOME_MAX_PRODUCTS, countryCode)
                if (promos.isNotEmpty()) promoProducts = promos
            }
        }
    }

    LaunchedEffect(selectedCategory, sectionPools, region) {
        if (selectedCategory == "all") return@LaunchedEffect
        val chip = selectedCategory
        val defsToLoad = HOME_PRODUCT_SECTIONS.filter { def ->
            !sectionPools[def.id].orEmpty().containsKey(chip)
        }
        if (defsToLoad.isEmpty()) return@LaunchedEffect
        val countryCode = localeStore.getCountryCodeSync()
        loadingCategories = loadingCategories + chip
        try {
            val updated = sectionPools.toMutableMap()
            coroutineScope {
                defsToLoad.map { def ->
                    async(Dispatchers.IO) {
                        val products = loadHomeCarouselFromWorker(
                            creatorApi,
                            def.id,
                            chipId = chip,
                            limit = HOME_INITIAL_PRODUCTS.coerceAtMost(HOME_MAX_PRODUCTS),
                            countryCode = countryCode,
                        )
                        def.id to products
                    }
                }.forEach { deferred ->
                    val (id, products) = deferred.await()
                    val chipMap = updated[id].orEmpty().toMutableMap()
                    chipMap[chip] = products
                    updated[id] = chipMap
                    sectionPools = updated.toMap()
                }
            }
            loadingCategories = loadingCategories - chip
            coroutineScope {
                defsToLoad.map { def ->
                    async(Dispatchers.IO) {
                        val products = loadHomeCarouselFromWorker(
                            creatorApi,
                            def.id,
                            chipId = chip,
                            limit = HOME_MAX_PRODUCTS,
                            countryCode = countryCode,
                        )
                        def.id to products
                    }
                }.forEach { deferred ->
                    val (id, products) = deferred.await()
                    val chipMap = updated[id].orEmpty().toMutableMap()
                    chipMap[chip] = products
                    updated[id] = chipMap
                    sectionPools = updated.toMap()
                }
            }
        } finally {
            loadingCategories = loadingCategories - chip
        }
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
                    fallbackProductHandle = heroFallbackHandle,
                    heroImages = homeHeroImages.takeIf { it.isNotEmpty() },
                )
            }

            item(key = "category_strip") {
                HomeCategoryStrip(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { selectedCategory = it },
                    labelForKey = t,
                    onFilterClick = { filterModalVisible = true },
                    filterActive = filterActive,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

        if (promoProducts.isNotEmpty()) {
            item(key = "promotions") {
                val promoTitle = t("eaz.shop.promotions_title", "Promotions")
                val visiblePromos = filterCarouselProducts(promoProducts)
                if (visiblePromos.isNotEmpty()) {
                ProductCarousel(
                    title = promoTitle,
                    products = visiblePromos,
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
                    onCartClick = { params ->
                        productModalHandleState?.value = params.handle
                    },
                )
                }
            }
        }

        HOME_PRODUCT_SECTIONS.forEach { def ->
            val products = sectionPools[def.id]?.get(selectedCategory).orEmpty()
            val visibleProducts = filterCarouselProducts(products)
            val sectionLoading =
                (homePoolsBootstrapping && !sectionPools.containsKey(def.id)) ||
                    (selectedCategory != "all" &&
                        loadingCategories.contains(selectedCategory) &&
                        !sectionPools[def.id].orEmpty().containsKey(selectedCategory))
            item(key = "section_${def.id}") {
                val displayTitle = t(def.titleKey, def.titleDefault)
                ProductCarousel(
                    title = displayTitle,
                    products = visibleProducts,
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
                    productsLoading = sectionLoading,
                    alwaysShowTitleRow = true,
                    onCartClick = { params ->
                        productModalHandleState?.value = params.handle
                    },
                )
            }
        }

        if (createScratchProducts.isNotEmpty()) {
            item(key = "create_scratch") {
                val title = t("eaz.home.create_from_scratch", "Create from Scratch")
                HomeCreateScratchCarousel(
                    title = title,
                    products = createScratchProducts,
                    onTitleClick = onCategoryClick?.let { cb ->
                        { cb(title, "shop-create") }
                    },
                    onProductClick = { cat -> onCreateScratchClick?.invoke(cat) },
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }

        if (loadCreatorsSection && onCreatorClick != null) {
            item(key = "creators") {
                HomeCreatorsCarousel(
                    creators = homeCreators,
                    sortTab = homeCreatorsSort,
                    loading = homeCreatorsLoading,
                    onSortTabChange = { homeCreatorsSort = it },
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
                onFilterClick = { filterModalVisible = true },
                filterActive = filterActive,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .zIndex(2f),
            )
        }

        if (filterModalVisible) {
            HomeCarouselFilterModal(
                products = homeFilterPool,
                filters = productFilters,
                withinSearchQuery = withinSearchQuery,
                onWithinSearchChange = { withinSearchQuery = it },
                onFiltersChange = { productFilters = it },
                onDismiss = { filterModalVisible = false },
                labelForKey = t,
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

private fun parseCatalogAudience(arr: JSONArray?): List<String> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        arr.optString(i, "").trim().takeIf { it.isNotEmpty() }
    }
}
