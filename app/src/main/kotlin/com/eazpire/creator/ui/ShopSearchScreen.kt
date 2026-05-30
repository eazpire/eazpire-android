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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PAGE_SIZE = 48

@Composable
fun ShopSearchScreen(
    searchQuery: String,
    onBack: () -> Unit,
    onProductClick: (ShopifyProductsApi.ProductItem) -> Unit,
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
    LaunchedEffect(ownerId) {
        if (ownerId.isNotBlank()) {
            CustomerMockPreviewStore.loadMap(creatorApi, ownerId)
            mockPreviewRevision = CustomerMockPreviewStore.revision
        }
    }
    var products by remember(searchQuery) { mutableStateOf<List<ShopifyProductsApi.ProductItem>>(emptyList()) }
    var loading by remember(searchQuery) { mutableStateOf(true) }

    LaunchedEffect(searchQuery) {
        loading = true
        products = emptyList()
        val r = withContext(Dispatchers.IO) {
            api.getProducts(searchQuery = searchQuery.trim(), limit = PAGE_SIZE, cursor = null)
        }
        products = r.products
        loading = false
    }

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
                            onClick = { onProductClick(p) }
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
    val shopImages = product.variantImages.ifEmpty { product.images }
    var images by remember(product.id, mockPreviewRevision) { mutableStateOf(shopImages) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var imageReload by remember(product.id) { mutableIntStateOf(0) }
    var mockDisplayLocked by remember(product.handle) { mutableStateOf(false) }
    var tryOnActive by remember(product.handle) {
        mutableStateOf(CustomerMockPreviewStore.isTryOnSessionActive(ctx, product.handle))
    }
    LaunchedEffect(product.id, shopImages, ownerId, mockPreviewRevision, imageReload) {
        if (ownerId.isBlank() || creatorApi == null) {
            if (!mockDisplayLocked) images = shopImages
            return@LaunchedEffect
        }
        val map = CustomerMockPreviewStore.peekMap(ownerId)
            ?: CustomerMockPreviewStore.loadMap(creatorApi, ownerId)
        val info = CustomerMockPreviewStore.tryOnInfo(
            map,
            product.handle,
            product.metaProductKey,
            product.designId
        )
        val autoActive = CustomerMockPreviewStore.shouldAutoShowMockOnCard(
            map,
            product.handle,
            product.metaProductKey,
            product.designId
        )
        val sessionActive = CustomerMockPreviewStore.isTryOnSessionActive(ctx, product.handle)
        tryOnActive = sessionActive || autoActive
        val useMock = tryOnActive || autoActive
        if (!useMock && info == null) {
            if (!mockDisplayLocked) images = shopImages
            return@LaunchedEffect
        }
        val resolved = withContext(Dispatchers.IO) {
            CustomerMockPreviewStore.resolveCardImages(ctx, creatorApi, ownerId, product, map)
        }
        val shopFirst = shopImages.firstOrNull()
        val mockFirst = resolved.firstOrNull()
        val hasMock = mockFirst != null && mockFirst != shopFirst
        if (useMock && hasMock) {
            mockDisplayLocked = true
            images = listOf(mockFirst!!)
            return@LaunchedEffect
        }
        if (useMock && resolved.isNotEmpty()) {
            mockDisplayLocked = true
            images = resolved.take(4)
            return@LaunchedEffect
        }
        if (!mockDisplayLocked) {
            images = if (resolved.isNotEmpty()) resolved else shopImages
        }
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
            if (images.isNotEmpty()) {
                com.eazpire.creator.ui.components.EazProductCardRotatingImages(
                    imageUrls = images,
                    productId = product.id.toString(),
                    contentDescription = product.title,
                    modifier = Modifier.fillMaxSize(),
                    autoRotate = !tryOnActive && images.size > 1
                )
            }
            com.eazpire.creator.ui.components.EazProductCardMediaOverlays(
                showTryOn = false,
                isTryOnActive = tryOnActive,
                onFavoriteClick = {
                    if (ownerId.isBlank() || creatorApi == null) return@EazProductCardMediaOverlays
                    scope.launch {
                        runCatching {
                            creatorApi.addFavorite(
                                customerId = ownerId,
                                productId = product.id.toString(),
                                variantId = null,
                                productTitle = product.title,
                                productImage = images.firstOrNull()
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
