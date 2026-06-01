package com.eazpire.creator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyProductsApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.LocalTranslationStore
import com.eazpire.creator.mockup.CustomerMockPreviewStore
import com.eazpire.creator.plp.PlpCardDisplay
import com.eazpire.creator.plp.PlpCardImageResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SEARCH_INITIAL_BATCH = PRODUCT_LIST_BATCH

@Composable
fun ShopSearchScreen(
    searchQuery: String,
    onBack: () -> Unit,
    onProductClick: (ShopifyProductsApi.ProductItem) -> Unit,
    onCartClick: (ShopifyProductsApi.ProductItem) -> Unit = onProductClick,
    reloadTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    val store = LocalTranslationStore.current
    val titleTemplate = store?.t("eaz.search.results_title", "Search: %s") ?: "Search: %s"
    val screenTitle = try {
        String.format(titleTemplate, searchQuery)
    } catch (_: Exception) {
        "Search: $searchQuery"
    }
    val emptyText = store?.t("eaz.search.no_results", "No results") ?: "No results"

    val api = remember { ShopifyProductsApi() }
    val context = LocalContext.current
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
    var products by remember(searchQuery) { mutableStateOf<List<ShopifyProductsApi.ProductItem>>(emptyList()) }
    var nextCursor by remember(searchQuery) { mutableStateOf<String?>(null) }
    var hasMore by remember(searchQuery) { mutableStateOf(false) }
    var loading by remember(searchQuery) { mutableStateOf(true) }
    var loadingMore by remember(searchQuery) { mutableStateOf(false) }
    var autoLoadPaused by remember(searchQuery) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val nearEnd = rememberProductListNearEnd(gridState)
    val loadMoreLabel = store?.t("eaz.product_list.load_more", "Load more") ?: "Load more"

    fun loadMore(forceAfterCap: Boolean = false) {
        if (loadingMore || searchQuery.isBlank()) return
        if (autoLoadPaused && !forceAfterCap) return
        if (!forceAfterCap && products.size >= PRODUCT_LIST_MAX_AUTO) {
            autoLoadPaused = true
            return
        }
        scope.launch {
            loadingMore = true
            try {
                val r = withContext(Dispatchers.IO) {
                    api.getProducts(searchQuery = searchQuery.trim(), limit = SEARCH_INITIAL_BATCH, cursor = nextCursor)
                }
                if (r.products.isEmpty()) {
                    hasMore = false
                    return@launch
                }
                val merged = linkedMapOf<Long, ShopifyProductsApi.ProductItem>()
                products.forEach { merged.putIfAbsent(it.id, it) }
                r.products.forEach { merged.putIfAbsent(it.id, it) }
                products = merged.values.toList()
                nextCursor = r.nextCursor
                hasMore = r.hasNextPage && r.nextCursor != null
                if (products.size >= PRODUCT_LIST_MAX_AUTO && !forceAfterCap) {
                    autoLoadPaused = true
                }
            } finally {
                loadingMore = false
            }
        }
    }

    LaunchedEffect(searchQuery, reloadTrigger) {
        loading = true
        products = emptyList()
        nextCursor = null
        hasMore = false
        autoLoadPaused = false
        val r = withContext(Dispatchers.IO) {
            api.getProducts(searchQuery = searchQuery.trim(), limit = SEARCH_INITIAL_BATCH, cursor = null)
        }
        products = r.products
        nextCursor = r.nextCursor
        hasMore = r.hasNextPage && r.nextCursor != null
        if (products.size >= PRODUCT_LIST_MAX_AUTO) autoLoadPaused = true
        loading = false
    }

    ProductListAutoLoadEffect(
        enabled = !loading && products.isNotEmpty(),
        nearEnd = nearEnd,
        autoLoadPaused = autoLoadPaused,
        hasMore = hasMore,
        loading = loadingMore,
        onLoadMore = { loadMore() },
    )

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(EazColors.Orange)
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }
            Text(
                text = screenTitle,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        when {
            loading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = EazColors.Orange)
                }
            }
            products.isEmpty() -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emptyText, color = EazColors.TextSecondary)
                }
            }
            else -> {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(products, key = { it.id }) { p ->
                        ShopSearchProductCard(
                            product = p,
                            ownerId = ownerId,
                            creatorApi = creatorApi,
                            mockPreviewRevision = mockPreviewRevision,
                            onClick = { onProductClick(p) },
                            onCartClick = { onCartClick(p) }
                        )
                    }
                    item(key = "search-footer") {
                        ProductListInfiniteFooter(
                            visible = loadingMore && !autoLoadPaused,
                            loading = loadingMore,
                            showLoadMore = autoLoadPaused && hasMore,
                            loadMoreLabel = loadMoreLabel,
                            onLoadMore = { loadMore(forceAfterCap = true) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopSearchProductCard(
    product: ShopifyProductsApi.ProductItem,
    ownerId: String = "",
    creatorApi: CreatorApi? = null,
    mockPreviewRevision: Int = 0,
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
        tryOnActive = sessionActive || autoActive
        showManualTryOn = CustomerMockPreviewStore.shouldShowManualTryOnButton(
            map,
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
                .background(Color(0xFFF5F5F5))
        ) {
            if (display.urls.isNotEmpty()) {
                com.eazpire.creator.ui.components.EazProductCardRotatingImages(
                    imageUrls = display.urls,
                    productId = product.id.toString(),
                    contentDescription = product.title,
                    modifier = Modifier.fillMaxSize(),
                    autoRotate = display.autoRotate,
                    fullResolution = display.isPersonalizedMock,
                )
            }
            com.eazpire.creator.ui.components.EazProductCardMediaOverlays(
                showTryOn = showManualTryOn,
                isTryOnActive = tryOnActive,
                onTryOnClick = {
                    val next = !tryOnActive
                    com.eazpire.creator.ui.components.togglePlpTryOnSession(ctx, product.handle, next)
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
                            com.eazpire.creator.favorites.FavoritesRefreshTrigger.trigger()
                        }
                    }
                },
                onCartClick = onCartClick,
            )
        }
        Text(
            text = product.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (product.price > 0) {
            Text(
                text = "CHF %.2f".format(product.price),
                style = MaterialTheme.typography.labelSmall,
                color = EazColors.TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
