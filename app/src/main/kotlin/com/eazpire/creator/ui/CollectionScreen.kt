package com.eazpire.creator.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.IconButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.EazColors
import com.eazpire.creator.i18n.LocalTranslationStore
import com.eazpire.creator.locale.LocaleStore
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyProductsApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.favorites.FavoritesRefreshTrigger
import com.eazpire.creator.mockup.CustomerMockPreviewStore
import com.eazpire.creator.plp.PlpCardDisplay
import com.eazpire.creator.plp.PlpCardImageResolver
import com.eazpire.creator.ui.components.EazProductCardMediaOverlays
import com.eazpire.creator.ui.components.EazProductCardRotatingImages
import com.eazpire.creator.ui.components.togglePlpTryOnSession
import kotlinx.coroutines.launch
import com.eazpire.creator.api.hasPromoPricingUi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import java.text.Normalizer
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableIntStateOf

private const val IMAGE_ROTATE_INTERVAL_MS = 1800L

/** Special [collectionHandle] — load products from worker `list-active-shop-promotion-products`, not a Shopify collection. */
const val EAZ_PROMOTIONS_COLLECTION_HANDLE = "eaz-promotions"


private suspend fun fetchNextCollectionBatch(
    api: ShopifyProductsApi,
    creatorApi: CreatorApi,
    collectionHandle: String,
    countryCode: String,
    loadedProducts: List<ShopifyProductsApi.ProductItem>,
    nextCursor: String?,
): Triple<List<ShopifyProductsApi.ProductItem>, String?, Boolean> {
    if (collectionHandle == EAZ_PROMOTIONS_COLLECTION_HANDLE) {
        return Triple(loadedProducts, null, false)
    }
    val result = withContext(Dispatchers.IO) {
        var r = api.getProducts(
            collectionHandle = collectionHandle.ifBlank { null },
            limit = PRODUCT_LIST_BATCH,
            cursor = nextCursor,
        )
        if (r.products.isEmpty() && collectionHandle.isNotBlank() && nextCursor == null) {
            r = api.getProducts(limit = PRODUCT_LIST_BATCH, cursor = nextCursor)
        }
        val merged = api.mergeShopPromotionOverlay(r.products, countryCode, creatorApi)
        r.copy(products = merged)
    }
    if (result.products.isEmpty()) {
        return Triple(loadedProducts, nextCursor, false)
    }
    val mergedMap = linkedMapOf<Long, ShopifyProductsApi.ProductItem>()
    loadedProducts.forEach { mergedMap.putIfAbsent(it.id, it) }
    result.products.forEach { mergedMap.putIfAbsent(it.id, it) }
    val hasMore = result.hasNextPage && result.nextCursor != null
    return Triple(mergedMap.values.toList(), result.nextCursor, hasMore)
}

private fun sortProducts(
    products: List<ShopifyProductsApi.ProductItem>,
    sortBy: String
): List<ShopifyProductsApi.ProductItem> = when (sortBy) {
    "created-descending" -> products.sortedByDescending { it.createdAt }
    "created-ascending" -> products.sortedBy { it.createdAt }
    "price-ascending" -> products.sortedBy { it.price }
    "price-descending" -> products.sortedByDescending { it.price }
    "title-ascending" -> products.sortedBy { it.title.lowercase() }
    "title-descending" -> products.sortedByDescending { it.title.lowercase() }
    else -> products
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    title: String,
    collectionHandle: String,
    initialProductType: String? = null,
    onBack: () -> Unit,
    onProductClick: (ShopifyProductsApi.ProductItem) -> Unit,
    onCartClick: (ShopifyProductsApi.ProductItem) -> Unit = onProductClick,
    reloadTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    val store = LocalTranslationStore.current
    val tr = store?.translations?.collectAsState(initial = emptyMap())?.value
    val t = store?.let { { k: String, d: String -> it.t(k, d) } } ?: { _: String, d: String -> d }
    val context = LocalContext.current
    val api = remember { ShopifyProductsApi() }
    val tokenStore = remember { SecureTokenStore(context) }
    val ownerId = remember { tokenStore.getOwnerId().orEmpty() }
    val jwt = remember { runCatching { tokenStore.getJwt() }.getOrNull() }
    val creatorApi = remember(jwt) { CreatorApi(jwt = jwt) }
    var mockPreviewRevision by remember { mutableIntStateOf(CustomerMockPreviewStore.revision) }
    LaunchedEffect(ownerId, reloadTrigger) {
        if (ownerId.isNotBlank()) {
            CustomerMockPreviewStore.loadMap(creatorApi, ownerId, force = reloadTrigger > 0)
            mockPreviewRevision = CustomerMockPreviewStore.revision
        }
    }
    var loadedProducts by remember { mutableStateOf<List<ShopifyProductsApi.ProductItem>>(emptyList()) }
    var nextCursor by remember { mutableStateOf<String?>(null) }
    var hasMoreFromApi by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var autoLoadPaused by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("manual") }
    var sortSheetVisible by remember { mutableStateOf(false) }
    var filterDrawerVisible by remember { mutableStateOf(false) }
    var productFilters by remember { mutableStateOf(PlpTriFilterSelection()) }
    var withinSearchQuery by remember { mutableStateOf("") }
    var filterCountProducts by remember { mutableStateOf<List<ShopifyProductsApi.ProductItem>>(emptyList()) }
    val localeStore = remember { LocaleStore(context) }
    val countryCode by localeStore.countryCode.collectAsState(initial = localeStore.getCountryCodeSync())
    val density = LocalDensity.current

    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val nearEnd = rememberProductListNearEnd(gridState)
    val usesInfiniteScroll = productFilters.isEmpty() && withinSearchQuery.isBlank()

    fun loadMore(forceAfterCap: Boolean = false) {
        if (isLoadingMore) return
        if (autoLoadPaused && !forceAfterCap) return
        if (!forceAfterCap && loadedProducts.size >= PRODUCT_LIST_MAX_AUTO) {
            autoLoadPaused = true
            return
        }
        scope.launch {
            isLoadingMore = true
            try {
                val country = countryCode
                val (nextList, cursor, hasMore) = fetchNextCollectionBatch(
                    api = api,
                    creatorApi = creatorApi,
                    collectionHandle = collectionHandle,
                    countryCode = country,
                    loadedProducts = loadedProducts,
                    nextCursor = nextCursor,
                )
                loadedProducts = nextList
                nextCursor = cursor
                hasMoreFromApi = hasMore
                if (loadedProducts.size >= PRODUCT_LIST_MAX_AUTO && !forceAfterCap) {
                    autoLoadPaused = true
                }
            } finally {
                isLoadingMore = false
            }
        }
    }

    LaunchedEffect(collectionHandle, initialProductType, reloadTrigger, countryCode) {
        loadedProducts = emptyList()
        nextCursor = null
        hasMoreFromApi = false
        autoLoadPaused = false
        filterCountProducts = emptyList()
        withinSearchQuery = ""
        productFilters = if (initialProductType != null) {
            PlpTriFilterSelection(productTypes = mapOf(normalizePlpValue(initialProductType) to 1))
        } else {
            PlpTriFilterSelection()
        }
    }

    LaunchedEffect(collectionHandle, reloadTrigger, countryCode) {
        isLoading = true
        loadedProducts = emptyList()
        nextCursor = null
        hasMoreFromApi = false
        autoLoadPaused = false
        if (collectionHandle == EAZ_PROMOTIONS_COLLECTION_HANDLE) {
            val list = withContext(Dispatchers.IO) {
                try {
                    val j = creatorApi.listActiveShopPromotionProducts(countryCode)
                    ShopifyProductsApi.parseActivePromotionProductsResponse(j)
                } catch (_: Exception) {
                    emptyList()
                }
            }
            loadedProducts = list
            filterCountProducts = list
            hasMoreFromApi = false
            isLoading = false
            return@LaunchedEffect
        }
        val country = countryCode
        val (nextList, cursor, hasMore) = fetchNextCollectionBatch(
            api = api,
            creatorApi = creatorApi,
            collectionHandle = collectionHandle,
            countryCode = country,
            loadedProducts = emptyList(),
            nextCursor = null,
        )
        loadedProducts = nextList
        nextCursor = cursor
        hasMoreFromApi = hasMore
        if (loadedProducts.size >= PRODUCT_LIST_MAX_AUTO) {
            autoLoadPaused = true
        }
        isLoading = false
    }

    LaunchedEffect(collectionHandle, filterDrawerVisible, productFilters.isEmpty(), loadedProducts.isEmpty(), countryCode) {
        if (collectionHandle == EAZ_PROMOTIONS_COLLECTION_HANDLE) {
            if (filterCountProducts.isEmpty() && loadedProducts.isNotEmpty()) {
                filterCountProducts = loadedProducts
            }
            return@LaunchedEffect
        }
        val needFilterProducts = filterDrawerVisible || !productFilters.isEmpty() || loadedProducts.isNotEmpty()
        if (needFilterProducts && filterCountProducts.isEmpty()) {
            filterCountProducts = withContext(Dispatchers.IO) {
                var r = api.getProducts(
                    collectionHandle = collectionHandle.ifBlank { null },
                    limit = 250,
                    cursor = null,
                )
                if (r.products.isEmpty() && collectionHandle.isNotBlank()) {
                    r = api.getProducts(limit = 250, cursor = null)
                }
                api.mergeShopPromotionOverlay(r.products, countryCode, creatorApi)
            }
        }
    }

    val productsToFilter = when {
        productFilters.isEmpty() && withinSearchQuery.isBlank() -> loadedProducts
        filterCountProducts.isNotEmpty() -> filterCountProducts
        else -> loadedProducts
    }
    val sortedProducts = remember(productsToFilter, sortBy) { sortProducts(productsToFilter, sortBy) }
    val filteredProducts = remember(sortedProducts, productFilters) {
        applyCollectionProductFilters(sortedProducts, productFilters)
    }
    val displayProducts = remember(filteredProducts, withinSearchQuery) {
        applyCollectionWithinSearchFilter(filteredProducts, withinSearchQuery)
    }
    val currentSortLabel = COLLECTION_SORT_OPTIONS.find { it.value == sortBy }?.label?.let { t("collection.sort_$sortBy", it) } ?: t("collection.sort_by", "Sort by")
    val loadMoreLabel = t("eaz.product_list.load_more", "Load more")

    ProductListAutoLoadEffect(
        enabled = usesInfiniteScroll && !isLoading,
        nearEnd = nearEnd,
        autoLoadPaused = autoLoadPaused,
        hasMore = hasMoreFromApi,
        loading = isLoadingMore,
        onLoadMore = { loadMore() },
    )

    Column(modifier = modifier.fillMaxSize()) {
        if (isLoading && loadedProducts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = EazColors.Orange)
            }
        } else if (loadedProducts.isEmpty()) {
            CollectionComingSoon(title = title, onBrowseAll = onBack)
        } else {
            CollectionResultsBar(
                filteredCount = displayProducts.size,
                totalCount = if (filterCountProducts.isNotEmpty()) filterCountProducts.size else productsToFilter.size,
                sortBy = sortBy,
                sortLabel = currentSortLabel,
                t = t,
                onFilterClick = { filterDrawerVisible = true },
                onSortClick = { sortSheetVisible = true }
            )
            LazyVerticalGrid(
                state = gridState,
                modifier = Modifier.weight(1f),
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(displayProducts, key = { it.id }) { product ->
                    val showPromoUi = product.hasPromoPricingUi()
                    CollectionProductCard(
                        product = product,
                        ownerId = ownerId,
                        creatorApi = creatorApi,
                        mockPreviewRevision = mockPreviewRevision,
                        showPromoUi = showPromoUi,
                        promoEndsPrefix = t("eaz.shop.promo_countdown_prefix", "Ends in"),
                        promoEndedLabel = t("eaz.shop.promo_countdown_ended", "Ended"),
                        promoNextDiscountPrefix = t("eaz.shop.promo_next_discount_prefix", "Discount in"),
                        promoNextPriceHintPrefix = t("eaz.shop.promo_next_price_hint_prefix", "Promo from"),
                        promoStartsPrefix = t("eaz.shop.promo_starts_prefix", "Starts in"),
                        onClick = { onProductClick(product) },
                        onCartClick = { onCartClick(product) }
                    )
                }
                if (usesInfiniteScroll) {
                    item(key = "infinite-footer") {
                        ProductListInfiniteFooter(
                            visible = isLoadingMore && !autoLoadPaused,
                            loading = isLoadingMore,
                            showLoadMore = autoLoadPaused && hasMoreFromApi,
                            loadMoreLabel = loadMoreLabel,
                            onLoadMore = { loadMore(forceAfterCap = true) },
                        )
                    }
                }
            }
        }
    }

    if (filterDrawerVisible) {
        val filterSource = if (filterCountProducts.isNotEmpty()) filterCountProducts else loadedProducts
        val productsForCounts = filterSource
        CollectionFilterDrawer(
            filters = productFilters,
            products = productsForCounts,
            withinSearchQuery = withinSearchQuery,
            onWithinSearchChange = { withinSearchQuery = it },
            onFiltersChange = { productFilters = it },
            onDismiss = { filterDrawerVisible = false },
            t = t
        )
    }

    CollectionSortBottomSheet(
        visible = sortSheetVisible,
        sortBy = sortBy,
        t = t,
        onDismiss = { sortSheetVisible = false },
        onSortSelected = { sortBy = it }
    )
}

@Composable
private fun CollectionComingSoon(
    title: String,
    onBrowseAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("🕐", style = MaterialTheme.typography.displayMedium)
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = EazColors.TextPrimary,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            "We are working on something special for this collection. New designs are dropping soon — stay tuned!",
            style = MaterialTheme.typography.bodyMedium,
            color = EazColors.TextSecondary,
            modifier = Modifier.padding(top = 8.dp)
        )
        Row(
            modifier = Modifier.padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(EazColors.TextSecondary.copy(alpha = 0.4f))
                )
            }
        }
        Text(
            "Browse all collections",
            style = MaterialTheme.typography.labelLarge,
            color = EazColors.Orange,
            modifier = Modifier
                .padding(top = 24.dp)
                .clickable(onClick = onBrowseAll)
        )
    }
}

/** Split product title into design title + product type (like web eaz-product-card-redesign.liquid). */
private fun formatShopMoneyCard(value: Double): String =
    try {
        NumberFormat.getCurrencyInstance(Locale.GERMANY).format(value)
    } catch (_: Exception) {
        "%.2f €".format(value)
    }

private fun formatPromoDurationMs(ms: Long): String {
    if (ms <= 0L) return ""
    var s = ms / 1000L
    val d = s / 86400L
    s %= 86400L
    val h = s / 3600L
    s %= 3600L
    val m = s / 60L
    val sec = s % 60L
    return when {
        d > 0L -> "${d}d ${h}h"
        h > 0L -> "${h}h ${m}m ${sec}s"
        else -> "${m}m ${sec}s"
    }
}

@Composable
private fun CollectionPromoCountdownChip(endsAtMs: Long, endsPrefix: String, endedLabel: String) {
    var now by remember(endsAtMs) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAtMs) {
        while (now < endsAtMs) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val left = endsAtMs - now
    val label = if (left <= 0L) endedLabel else "$endsPrefix ${formatPromoDurationMs(left)}"
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = EazColors.Orange,
        modifier = Modifier.padding(top = 4.dp)
    )
}

private fun splitProductTitleForCard(title: String, productType: String): Pair<String, String> {
    val normalized = title
        .replace(" — ", " | ")
        .replace(" – ", " | ")
        .replace(" - ", " | ")
    val parts = normalized.split(" | ")
    val designTitle = parts.firstOrNull()?.trim()?.ifBlank { title } ?: title
    val productTypeTitle = when {
        parts.size > 1 -> parts.drop(1).joinToString(" - ").trim()
        productType.isNotBlank() -> productType
        else -> ""
    }
    return designTitle to productTypeTitle
}

@Composable
private fun CollectionProductCard(
    product: ShopifyProductsApi.ProductItem,
    ownerId: String = "",
    creatorApi: CreatorApi? = null,
    mockPreviewRevision: Int = 0,
    showPromoUi: Boolean = false,
    promoEndsPrefix: String = "",
    promoEndedLabel: String = "",
    promoNextDiscountPrefix: String = "",
    promoNextPriceHintPrefix: String = "",
    promoStartsPrefix: String = "",
    onClick: () -> Unit,
    onCartClick: () -> Unit = onClick,
    modifier: Modifier = Modifier
) {
    val shopImages = PlpCardImageResolver.shopRotationUrls(product)
    var display by remember(product.id, mockPreviewRevision) {
        mutableStateOf(PlpCardDisplay(shopImages, isPersonalizedMock = false))
    }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var imageReload by remember(product.id) { mutableIntStateOf(0) }
    var tryOnActive by remember(product.handle) {
        mutableStateOf(CustomerMockPreviewStore.isTryOnSessionActive(ctx, product.handle))
    }
    var showManualTryOn by remember(product.handle) { mutableStateOf(false) }
    LaunchedEffect(product.id, shopImages, ownerId, mockPreviewRevision, imageReload, tryOnActive) {
        if (ownerId.isBlank() || creatorApi == null) {
            display = PlpCardDisplay(shopImages, isPersonalizedMock = false)
            return@LaunchedEffect
        }
        val map = CustomerMockPreviewStore.peekMap(ownerId)
            ?: CustomerMockPreviewStore.loadMap(creatorApi, ownerId)
        val sessionActive = CustomerMockPreviewStore.isTryOnSessionActive(ctx, product.handle)
        val autoActive = CustomerMockPreviewStore.shouldAutoShowMockOnCard(
            map,
            product.handle,
            product.metaProductKey,
            product.designId
        )
        tryOnActive = CustomerMockPreviewStore.isTryOnDisplayActive(ctx, product.handle, autoActive)
        showManualTryOn = CustomerMockPreviewStore.shouldShowTryOnButton(
            map,
            ctx,
            product.handle,
            product.metaProductKey,
            product.designId
        )
        display = PlpCardImageResolver.resolve(
            context = ctx,
            creatorApi = creatorApi,
            ownerId = ownerId,
            product = product,
            mockMap = map,
            sessionTryOnActive = sessionActive,
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 5f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            if (display.urls.isNotEmpty()) {
                EazProductCardRotatingImages(
                    imageUrls = display.urls,
                    productId = product.id.toString(),
                    contentDescription = product.title,
                    modifier = Modifier.fillMaxSize(),
                    rotateIntervalMs = IMAGE_ROTATE_INTERVAL_MS,
                    autoRotate = display.autoRotate,
                    fullResolution = display.isPersonalizedMock,
                )
            }
            EazProductCardMediaOverlays(
                showTryOn = showManualTryOn,
                isTryOnActive = tryOnActive,
                onTryOnClick = {
                    val next = !tryOnActive
                    togglePlpTryOnSession(ctx, product.handle, next)
                    tryOnActive = next
                    imageReload++
                },
                onFavoriteClick = {
                    if (ownerId.isBlank() || creatorApi == null) return@EazProductCardMediaOverlays
                    scope.launch {
                        runCatching {
                            creatorApi.addFavorite(
                                customerId = ownerId,
                                productId = product.id.toString(),
                                variantId = null,
                                productTitle = product.title,
                                productImage = display.urls.firstOrNull()
                            )
                            FavoritesRefreshTrigger.trigger()
                        }
                    }
                },
                onCartClick = onCartClick,
            )
        }
        val (designTitle, productTypeTitle) = remember(product.title, product.productType) {
            splitProductTitleForCard(product.title, product.productType)
        }
        Column(modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = designTitle,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (productTypeTitle.isNotBlank()) {
                Text(
                    text = productTypeTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = EazColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (product.price > 0) {
            if (showPromoUi) {
                val nextHint = promoNextPriceHintPrefix.ifBlank { "Promo from" }
                val nextDisc = promoNextDiscountPrefix.ifBlank { "Discount in" }
                val startsDisc = promoStartsPrefix.ifBlank { "Starts in" }
                if (product.promoOutsideSlot || product.promoPrelaunch) {
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            text = formatShopMoneyCard(product.price),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val preview = product.promoPreviewPrice
                        if (preview != null && preview < product.price - 1e-6) {
                            Text(
                                text = "$nextHint ${formatShopMoneyCard(preview)}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = EazColors.Orange,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        val nextAt = product.promoCampaignStartsAtMs ?: product.promoNextWindowStartsAtMs
                        val countPrefix = if (product.promoPrelaunch) startsDisc else nextDisc
                        if (nextAt != null && nextAt > 0L) {
                            CollectionPromoCountdownChip(
                                endsAtMs = nextAt,
                                endsPrefix = countPrefix,
                                endedLabel = promoEndedLabel.ifBlank { "Ended" }
                            )
                        }
                    }
                } else {
                    val before = product.promoBeforePrice
                        ?: product.compareAtPrice?.takeIf { it > product.price + 1e-6 }
                    val strikePrice = before?.takeIf { it > product.price + 1e-6 }
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = formatShopMoneyCard(product.price),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = EazColors.Orange
                        )
                        if (strikePrice != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formatShopMoneyCard(strikePrice),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                                textDecoration = TextDecoration.LineThrough
                            )
                        }
                    }
                    val ends = product.promotionEndsAtMs
                    if (ends != null && ends > 0L) {
                        CollectionPromoCountdownChip(
                            endsAtMs = ends,
                            endsPrefix = promoEndsPrefix.ifBlank { "Ends in" },
                            endedLabel = promoEndedLabel.ifBlank { "Ended" }
                        )
                    }
                }
            } else {
                Text(
                    text = formatShopMoneyCard(product.price),
                    style = MaterialTheme.typography.labelSmall,
                    color = EazColors.TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
