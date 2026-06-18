package com.eazpire.creator.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyProductsApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.LocalTranslationStore
import com.eazpire.creator.locale.LocaleStore
import com.eazpire.creator.mockup.CustomerMockPreviewStore
import com.eazpire.creator.ui.home.HOME_PRODUCT_SECTIONS
import com.eazpire.creator.ui.home.HomeCategoryStrip
import com.eazpire.creator.ui.home.HomeCarouselFilterModal
import com.eazpire.creator.ui.home.HomeCreateScratchCarousel
import com.eazpire.creator.ui.home.HomeCreatorsCarousel
import com.eazpire.creator.ui.home.HomeViewModel
import com.eazpire.creator.ui.home.HomeViewModelFactory
import com.eazpire.creator.ui.home.matchesHomeCategory
import com.eazpire.creator.ui.home.resolveHomeSectionProducts

@Composable
fun ProductCarouselSection(
    tokenStore: SecureTokenStore,
    onCurrentPageChange: ((String) -> Unit)? = null,
    onCategoryClick: ((title: String, handle: String) -> Unit)? = null,
    onProductClick: ((ProductClickWithCollection) -> Unit)? = null,
    onHotspotProductClick: ((String) -> Unit)? = null,
    onCreatorClick: ((String) -> Unit)? = null,
    onCreatorsTitleClick: (() -> Unit)? = null,
    onCreateScratchClick: ((CatalogProduct) -> Unit)? = null,
    productModalHandleState: MutableState<String?>? = null,
    scrollToTopTrigger: Int = 0,
    reloadTrigger: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val homeViewModel: HomeViewModel = viewModel(
        factory = remember(tokenStore) { HomeViewModelFactory(tokenStore, context.applicationContext) },
    )
    val homeState by homeViewModel.state.collectAsState()
    val store = LocalTranslationStore.current
    val t = store?.let { { k: String, d: String -> it.t(k, d) } } ?: { _: String, d: String -> d }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId().orEmpty() }
    val jwt = remember(tokenStore) { runCatching { tokenStore.getJwt() }.getOrNull() }
    val creatorApi = remember(jwt) { CreatorApi(jwt = jwt) }
    val localeStore = remember { LocaleStore(context) }
    val countryCode by localeStore.countryCode.collectAsState(initial = localeStore.getCountryCodeSync())
    val catalogRegion by localeStore.regionCode.collectAsState(initial = localeStore.getRegionCodeSync())

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

    fun filterCarouselProducts(list: List<ShopifyProductsApi.ProductItem>): List<ShopifyProductsApi.ProductItem> {
        return applyCollectionWithinSearchFilter(
            applyCollectionProductFilters(list, productFilters),
            withinSearchQuery,
        )
    }

    val homeFilterPool = remember(selectedCategory, homeState.sectionPools, homeState.promoProducts) {
        buildList {
            addAll(homeState.promoProducts)
            HOME_PRODUCT_SECTIONS.forEach { def ->
                addAll(resolveHomeSectionProducts(homeState.sectionPools[def.id].orEmpty(), selectedCategory))
            }
        }.distinctBy { it.handle }
    }

    val filterActive = !productFilters.isEmpty() || withinSearchQuery.isNotBlank()

    LaunchedEffect(countryCode, catalogRegion, reloadTrigger) {
        homeViewModel.ensureBootstrap(countryCode, catalogRegion, reloadTrigger, activity)
    }

    LaunchedEffect(homeState.loadCreatorsSection, homeState.homeCreatorsSort, reloadTrigger) {
        homeViewModel.loadCreators(reloadTrigger)
    }

    LaunchedEffect(selectedCategory, countryCode) {
        if (selectedCategory != "all") {
            homeViewModel.loadCategoryChip(countryCode, selectedCategory)
        }
    }

    val listState = rememberLazyListState()
    val pinCategoryStrip by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 }
    }
    LaunchedEffect(scrollToTopTrigger) {
        if (scrollToTopTrigger > 0) listState.animateScrollToItem(0)
    }

    val createScratchProducts = remember(homeState.createScratchCatalog, selectedCategory) {
        homeState.createScratchCatalog
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
                    heroImages = homeState.heroImages.takeIf { it.isNotEmpty() },
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

        if (homeState.bootstrapInProgress || homeState.promoProducts.isNotEmpty()) {
            item(key = "promotions") {
                val promoTitle = t("eaz.shop.promotions_title", "Promotions")
                val visiblePromos = filterCarouselProducts(homeState.promoProducts)
                val promoLoading = homeState.bootstrapInProgress && homeState.promoProducts.isEmpty()
                if (promoLoading || visiblePromos.isNotEmpty()) {
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
                    productsLoading = promoLoading,
                    alwaysShowTitleRow = promoLoading,
                    onCartClick = { params ->
                        productModalHandleState?.value = params.handle
                    },
                )
                }
            }
        }

        HOME_PRODUCT_SECTIONS.forEach { def ->
            val products = resolveHomeSectionProducts(homeState.sectionPools[def.id].orEmpty(), selectedCategory)
            val visibleProducts = filterCarouselProducts(products)
            val sectionLoading =
                !homeState.sectionPools.containsKey(def.id) ||
                    (selectedCategory != "all" &&
                        homeState.loadingCategories.contains(selectedCategory) &&
                        !homeState.sectionPools[def.id].orEmpty().containsKey(selectedCategory))
            if (sectionLoading || visibleProducts.isNotEmpty()) {
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
                        onCartClick = { params ->
                            productModalHandleState?.value = params.handle
                        },
                    )
                }
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

        if (homeState.loadCreatorsSection && onCreatorClick != null) {
            item(key = "creators") {
                HomeCreatorsCarousel(
                    creators = homeState.homeCreators,
                    sortTab = homeState.homeCreatorsSort,
                    loading = homeState.homeCreatorsLoading,
                    onSortTabChange = { homeViewModel.setCreatorsSort(it) },
                    labelForKey = t,
                    onCreatorClick = onCreatorClick,
                    onCreatorsTitleClick = onCreatorsTitleClick,
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
