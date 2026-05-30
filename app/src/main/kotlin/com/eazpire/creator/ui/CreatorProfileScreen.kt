package com.eazpire.creator.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import java.text.DateFormat
import java.util.Date
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.i18n.formatCountLabel
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyProductsApi
import android.util.Log
import com.eazpire.creator.i18n.LocalTranslationStore
import com.eazpire.creator.locale.LocaleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.Locale

data class CreatorProfilePreview(
    val name: String,
    val avatarUrl: String? = null,
    val coverUrl: String? = null,
    val ratingAvg: Double? = null,
    val ratingCount: Int? = null,
    val productCount: Int = 0
)

data class CreatorShopProduct(
    val handle: String,
    val title: String,
    val productName: String? = null,
    val shopifyProductId: String? = null,
    val imageUrl: String?,
    val price: String?,
    val priceAmount: Double? = null,
    val createdAtMs: Long? = null,
    val productType: String? = null,
    val contentType: String = "",
    val designType: String = "",
    val designStyle: List<String> = emptyList(),
    val ratio: String = "",
    val designLanguage: String = "",
)

data class CreatorReviewItem(
    val id: String,
    val rating: Double,
    val title: String,
    val body: String,
    val reviewerName: String,
    val createdAtMs: Long?,
    val shopifyProductId: String? = null,
    val productHandle: String?,
    val productTitle: String?,
    val productImage: String?
)

val CREATOR_PROFILE_SORT_OPTIONS = listOf(
    CollectionSortOption("date-desc", "Date, new to old"),
    CollectionSortOption("manual", "Featured"),
    CollectionSortOption("title-ascending", "Alphabetically, A–Z"),
    CollectionSortOption("title-descending", "Alphabetically, Z–A"),
    CollectionSortOption("price-ascending", "Price: Low to High"),
    CollectionSortOption("price-descending", "Price: High to Low"),
    CollectionSortOption("created-ascending", "Date, old to new"),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CreatorProfileScreen(
    creatorName: String,
    api: CreatorApi,
    viewerOwnerId: String = "",
    onBack: () -> Unit,
    onProductClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val store = LocalTranslationStore.current
    val tr = store?.translations?.collectAsState(initial = emptyMap())?.value
    val t = store?.let { { k: String, d: String -> it.t(k, d) } } ?: { _: String, d: String -> d }

    val context = LocalContext.current
    val localeStore = remember { LocaleStore(context) }
    val countryCode by localeStore.countryCode.collectAsState(initial = localeStore.getCountryCodeSync())
    val catalogRegion by localeStore.regionCode.collectAsState(initial = localeStore.getRegionCodeSync())

    var loading by remember(creatorName) { mutableStateOf(true) }
    var error by remember(creatorName) { mutableStateOf<String?>(null) }
    var profile by remember(creatorName) { mutableStateOf<CreatorProfilePreview?>(null) }
    var productsByPage by remember(creatorName) { mutableStateOf<Map<Int, List<CreatorShopProduct>>>(emptyMap()) }
    var filterCountProducts by remember(creatorName) { mutableStateOf<List<CreatorShopProduct>>(emptyList()) }
    var currentPage by remember(creatorName) { mutableStateOf(1) }
    var totalProductCount by remember(creatorName) { mutableStateOf(0) }
    var hasMoreProducts by remember(creatorName) { mutableStateOf(false) }
    var productsLoading by remember(creatorName) { mutableStateOf(false) }
    var profileReady by remember(creatorName) { mutableStateOf(false) }
    var sortBy by remember(creatorName) { mutableStateOf("date-desc") }
    var withinSearchQuery by remember(creatorName) { mutableStateOf("") }
    var productFilters by remember(creatorName) { mutableStateOf(ProductFilters()) }
    var showSortSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var showReviewsModal by remember { mutableStateOf(false) }
    var resolvedCreatorName by remember(creatorName) { mutableStateOf(creatorName) }
    var ownerId by remember(creatorName) { mutableStateOf<String?>(null) }

    LaunchedEffect(creatorName, countryCode, catalogRegion) {
        loading = true
        error = null
        profileReady = false
        productsByPage = emptyMap()
        filterCountProducts = emptyList()
        currentPage = 1
        totalProductCount = 0
        hasMoreProducts = false
        try {
            val profileJson = withContext(Dispatchers.IO) {
                api.getCreatorProfile(
                    creatorName = creatorName,
                    creatorSlug = creatorName,
                    region = catalogRegion
                )
            }
            if (!profileJson.optBoolean("ok", false)) {
                error = profileJson.optString("error", "profile_error")
                loading = false
                return@LaunchedEffect
            }
            val ratingObj = profileJson.optJSONObject("rating")
            val avatarObj = profileJson.optJSONObject("avatar")
            val coverObj = profileJson.optJSONObject("cover")
            val resolvedName = profileJson.optString("creator_name", creatorName).ifBlank { creatorName }
            resolvedCreatorName = resolvedName
            val resolvedOwnerId = profileJson.optString("owner_id", "").trim().ifBlank { null }
            ownerId = resolvedOwnerId
            val coverUrl = coverObj?.optString("image_url", "")?.trim()?.ifBlank { null }
                ?: coverObj?.optJSONArray("cover_rotation_slides")?.optJSONObject(0)
                    ?.optString("image_url", "")?.trim()?.ifBlank { null }

            profile = CreatorProfilePreview(
                name = resolvedName,
                avatarUrl = avatarObj?.optString("image_url", "")?.trim()?.ifBlank { null },
                coverUrl = coverUrl,
                ratingAvg = ratingObj?.optDouble("avg")?.takeIf { it > 0.0 }
                    ?: ratingObj?.optDouble("rating")?.takeIf { it > 0.0 },
                ratingCount = ratingObj?.optInt("count")?.takeIf { it > 0 }
                    ?: ratingObj?.optInt("rating_count")?.takeIf { it > 0 },
                productCount = 0
            )
            profileReady = true
        } catch (e: Exception) {
            error = e.message ?: "error"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(creatorName, currentPage, profileReady, resolvedCreatorName, ownerId, countryCode, catalogRegion) {
        if (!profileReady || error != null) return@LaunchedEffect
        if (productsByPage.containsKey(currentPage)) return@LaunchedEffect
        productsLoading = true
        try {
            val offset = (currentPage - 1) * CREATOR_PROFILE_PRODUCTS_PER_PAGE
            val productsJson = withContext(Dispatchers.IO) {
                api.getCreatorShopProducts(
                    creatorName = resolvedCreatorName,
                    creatorSlug = creatorName,
                    ownerId = ownerId,
                    country = countryCode,
                    region = catalogRegion,
                    limit = CREATOR_PROFILE_PRODUCTS_PER_PAGE,
                    offset = offset
                )
            }
            val list = mutableListOf<CreatorShopProduct>()
            if (productsJson.optBoolean("ok", false)) {
                val arr = productsJson.optJSONArray("products") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list.add(parseCreatorShopProduct(o))
                }
                val total = productsJson.optInt("total", -1)
                if (total >= 0) {
                    totalProductCount = total
                    profile = profile?.copy(productCount = total)
                } else {
                    totalProductCount = maxOf(totalProductCount, offset + list.size)
                    profile = profile?.copy(productCount = totalProductCount)
                }
                hasMoreProducts = productsJson.optBoolean("has_more", list.size >= CREATOR_PROFILE_PRODUCTS_PER_PAGE)
            }
            productsByPage = productsByPage + (currentPage to list)
        } catch (_: Exception) {
            /* keep prior pages */
        } finally {
            productsLoading = false
        }
    }

    LaunchedEffect(
        creatorName,
        showFilterSheet,
        productFilters,
        withinSearchQuery,
        profileReady,
        resolvedCreatorName,
        ownerId,
        countryCode,
        catalogRegion
    ) {
        if (!profileReady) return@LaunchedEffect
        val needAll = showFilterSheet || !productFilters.isEmpty() || withinSearchQuery.isNotBlank()
        if (!needAll || filterCountProducts.isNotEmpty()) return@LaunchedEffect
        try {
            val productsJson = withContext(Dispatchers.IO) {
                api.getCreatorShopProducts(
                    creatorName = resolvedCreatorName,
                    creatorSlug = creatorName,
                    ownerId = ownerId,
                    country = countryCode,
                    region = catalogRegion
                )
            }
            val list = mutableListOf<CreatorShopProduct>()
            if (productsJson.optBoolean("ok", false)) {
                val arr = productsJson.optJSONArray("products") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    list.add(parseCreatorShopProduct(o))
                }
            }
            filterCountProducts = list
        } catch (_: Exception) {
            /* ignore */
        }
    }

    val allLoadedProducts = remember(productsByPage, filterCountProducts) {
        if (filterCountProducts.isNotEmpty()) filterCountProducts
        else productsByPage.values.flatten()
    }
    val usesServerPaging = productFilters.isEmpty() && withinSearchQuery.isBlank()
    val pageProducts = productsByPage[currentPage] ?: emptyList()

    val filteredSortedProducts by remember(allLoadedProducts, pageProducts, sortBy, productFilters, withinSearchQuery, usesServerPaging, currentPage) {
        derivedStateOf {
            val source = if (usesServerPaging) pageProducts else allLoadedProducts
            val items = source.map { it.toFilterProductItem() }
            val filtered = applyCollectionProductFilters(items, productFilters)
            val searched = applyCollectionWithinSearchFilter(filtered, withinSearchQuery)
            val byHandle = source.associateBy { it.handle }
            val sorted = sortCreatorProducts(
                searched.mapNotNull { byHandle[it.handle] },
                sortBy
            )
            if (usesServerPaging) sorted
            else {
                val start = (currentPage - 1) * CREATOR_PROFILE_PRODUCTS_PER_PAGE
                sorted.drop(start).take(CREATOR_PROFILE_PRODUCTS_PER_PAGE)
            }
        }
    }

    val filteredTotalCount by remember(allLoadedProducts, productFilters, withinSearchQuery, usesServerPaging) {
        derivedStateOf {
            if (usesServerPaging) totalProductCount
            else {
                val items = allLoadedProducts.map { it.toFilterProductItem() }
                applyCollectionWithinSearchFilter(
                    applyCollectionProductFilters(items, productFilters),
                    withinSearchQuery
                ).size
            }
        }
    }

    val totalPages = remember(filteredTotalCount, hasMoreProducts, currentPage, usesServerPaging, allLoadedProducts, productFilters, withinSearchQuery) {
        if (!usesServerPaging) {
            maxOf(1, (filteredTotalCount + CREATOR_PROFILE_PRODUCTS_PER_PAGE - 1) / CREATOR_PROFILE_PRODUCTS_PER_PAGE)
        } else if (filteredTotalCount > 0) {
            maxOf(1, (filteredTotalCount + CREATOR_PROFILE_PRODUCTS_PER_PAGE - 1) / CREATOR_PROFILE_PRODUCTS_PER_PAGE)
        } else {
            maxOf(1, if (hasMoreProducts) currentPage + 1 else currentPage)
        }
    }

    val sortLabel = CREATOR_PROFILE_SORT_OPTIONS.find { it.value == sortBy }?.label ?: "Date, new to old"
    val density = LocalDensity.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
    ) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EazColors.Orange)
            }
            error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    t("eaz.creator_profile.error_profile", "Could not load this creator profile."),
                    color = MaterialTheme.colorScheme.error
                )
            }
            else -> {
                val p = profile
                Column(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(currentPage, totalPages) {
                                var totalDrag = 0f
                                val thresholdPx = with(density) { 60.dp.toPx() }
                                detectHorizontalDragGestures(
                                    onDragStart = { totalDrag = 0f },
                                    onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                                    onDragEnd = {
                                        when {
                                            totalDrag > thresholdPx && currentPage > 1 -> currentPage -= 1
                                            totalDrag < -thresholdPx && currentPage < totalPages -> currentPage += 1
                                        }
                                    }
                                )
                            },
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        item {
                            CreatorProfileHero(
                                name = p?.name ?: creatorName,
                                avatarUrl = p?.avatarUrl,
                                coverUrl = p?.coverUrl,
                                ratingAvg = p?.ratingAvg,
                                ratingCount = p?.ratingCount,
                                productCount = p?.productCount ?: filteredTotalCount,
                                t = t,
                                onRatingClick = {
                                    if ((p?.ratingCount ?: 0) > 0) showReviewsModal = true
                                }
                            )
                        }
                        stickyHeader {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White)
                            ) {
                                CollectionResultsBar(
                                    filteredCount = if (usesServerPaging) filteredTotalCount else filteredSortedProducts.size,
                                    totalCount = filteredTotalCount,
                                    sortBy = sortBy,
                                    sortLabel = sortLabel,
                                    t = t,
                                    onFilterClick = { showFilterSheet = true },
                                    onSortClick = { showSortSheet = true }
                                )
                            }
                        }
                        if (productsLoading && filteredSortedProducts.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = EazColors.Orange)
                                }
                            }
                        } else if (filteredSortedProducts.isEmpty()) {
                            item {
                                Text(
                                    t("eaz.creator_profile.empty_products", "No products from this creator yet."),
                                    modifier = Modifier.padding(16.dp),
                                    color = EazColors.TextSecondary
                                )
                            }
                        } else {
                            items(filteredSortedProducts.chunked(2)) { rowItems ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowItems.forEach { item ->
                                        CreatorShopProductCard(
                                            product = item,
                                            creatorLabel = p?.name ?: creatorName,
                                            viewerOwnerId = viewerOwnerId,
                                            creatorApi = api,
                                            modifier = Modifier.weight(1f),
                                            onClick = { onProductClick(item.handle) },
                                            onCartClick = { onProductClick(item.handle) }
                                        )
                                    }
                                    if (rowItems.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                    if (totalPages > 1) {
                        ProductPaginationDots(
                            totalPages = totalPages,
                            currentPage = currentPage,
                            onPageClick = { currentPage = it },
                            onSwipePrev = { if (currentPage > 1) currentPage -= 1 },
                            onSwipeNext = { if (currentPage < totalPages) currentPage += 1 },
                            style = PaginationDotsStyle.Light
                        )
                    }
                }
            }
        }
    }

    CollectionSortBottomSheet(
        visible = showSortSheet,
        sortBy = sortBy,
        sortOptions = CREATOR_PROFILE_SORT_OPTIONS,
        t = t,
        onDismiss = { showSortSheet = false },
        onSortSelected = { sortBy = it }
    )

    CreatorReviewsModal(
        visible = showReviewsModal,
        api = api,
        creatorName = resolvedCreatorName,
        creatorSlug = creatorName,
        ownerId = ownerId,
        ratingAvg = profile?.ratingAvg,
        ratingCount = profile?.ratingCount,
        shopProducts = allLoadedProducts,
        t = t,
        onDismiss = { showReviewsModal = false },
        onProductClick = { handle ->
            showReviewsModal = false
            if (handle.isNotBlank()) onProductClick(handle)
        }
    )

    if (showFilterSheet) {
        CollectionFilterDrawer(
            filters = productFilters,
            products = allLoadedProducts.map { it.toFilterProductItem() },
            withinSearchQuery = withinSearchQuery,
            onWithinSearchChange = { withinSearchQuery = it },
            onFiltersChange = { productFilters = it },
            onDismiss = { showFilterSheet = false },
            t = t
        )
    }
}

@Composable
private fun CreatorProfileHero(
    name: String,
    avatarUrl: String?,
    coverUrl: String?,
    ratingAvg: Double?,
    ratingCount: Int?,
    productCount: Int,
    t: (String, String) -> String,
    onRatingClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(8f / 3f)
    ) {
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2A2A2A), Color(0xFF555555))
                        )
                    )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.15f), Color.Black.copy(alpha = 0.55f))
                    )
                )
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            CreatorAvatarLogo(
                name = name,
                avatarUrl = avatarUrl,
                size = 72.dp,
                cornerRadius = 12.dp,
                borderWidth = 3.dp,
                borderColor = Color.White
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (productCount > 0) {
                    Text(
                        text = formatCountLabel(
                            t("eaz.creator_profile.products_count", "{{ count }} products"),
                            productCount,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (ratingAvg != null && ratingCount != null) {
                    Spacer(Modifier.height(6.dp))
                    CreatorHeroRatingRow(
                        avg = ratingAvg,
                        count = ratingCount,
                        onClick = onRatingClick
                    )
                }
            }
        }
    }
}

@Composable
private fun CreatorHeroRatingRow(avg: Double, count: Int, onClick: () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        repeat(5) { index ->
            val filled = avg >= index + 1 - 0.25
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = if (filled) Color(0xFFFFD4A8) else Color.White.copy(alpha = 0.35f),
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = String.format("%.1f", avg),
            color = Color(0xFFFFD4A8),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 6.dp)
        )
        Text(
            text = "$count",
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}

@Composable
private fun CreatorShopProductCard(
    product: CreatorShopProduct,
    creatorLabel: String,
    viewerOwnerId: String = "",
    creatorApi: CreatorApi? = null,
    mockPreviewRevision: Int = 0,
    onClick: () -> Unit,
    onCartClick: () -> Unit = onClick,
    modifier: Modifier = Modifier
) {
    val (designTitle, productLabel) = splitCreatorProductTitle(product.title, product.productName)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val imageUrls = remember(product.imageUrl) {
        listOfNotNull(product.imageUrl?.takeIf { it.isNotBlank() })
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFEFEFEF))
        ) {
            if (imageUrls.isNotEmpty()) {
                com.eazpire.creator.ui.components.EazProductCardRotatingImages(
                    imageUrls = imageUrls,
                    productId = product.shopifyProductId ?: product.handle,
                    contentDescription = product.title,
                    modifier = Modifier.fillMaxSize(),
                    autoRotate = imageUrls.size > 1
                )
            }
            com.eazpire.creator.ui.components.EazProductCardMediaOverlays(
                showTryOn = false,
                onFavoriteClick = {
                    val oid = viewerOwnerId
                    val api = creatorApi
                    val pid = product.shopifyProductId
                    if (oid.isBlank() || api == null || pid.isNullOrBlank()) return@EazProductCardMediaOverlays
                    scope.launch {
                        runCatching {
                            api.addFavorite(
                                customerId = oid,
                                productId = pid,
                                variantId = null,
                                productTitle = product.title,
                                productImage = product.imageUrl
                            )
                            com.eazpire.creator.favorites.FavoritesRefreshTrigger.trigger()
                        }
                    }
                },
                onCartClick = onCartClick
            )
        }
        Text(
            text = creatorLabel.uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelSmall,
            color = EazColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = designTitle,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = EazColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
        if (productLabel.isNotBlank()) {
            Text(
                text = productLabel,
                style = MaterialTheme.typography.labelSmall,
                color = EazColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (!product.price.isNullOrBlank()) {
            Text(
                text = product.price,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = EazColors.TextPrimary,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

@Composable
fun CreatorAvatarLogo(
    name: String,
    avatarUrl: String?,
    size: androidx.compose.ui.unit.Dp,
    cornerRadius: androidx.compose.ui.unit.Dp = 9.dp,
    borderWidth: androidx.compose.ui.unit.Dp = 1.dp,
    borderColor: Color = Color(0xFFE8E8E8),
    backgroundColor: Color = Color(0xFFF0F0F0),
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(cornerRadius)
    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .border(borderWidth, borderColor, shape)
            .background(backgroundColor)
            .then(clickMod),
        contentAlignment = Alignment.Center
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = name.take(1).uppercase(Locale.ROOT),
                color = EazColors.Orange,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
fun CreatorAvatarCircle(
    name: String,
    avatarUrl: String?,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    CreatorAvatarLogo(
        name = name,
        avatarUrl = avatarUrl,
        size = size,
        cornerRadius = size * 0.21f,
        modifier = modifier,
        onClick = onClick
    )
}

@Composable
fun CreatorRatingRow(
    avg: Double,
    count: Int,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Star, contentDescription = null, tint = EazColors.Orange, modifier = Modifier.size(16.dp))
        Text(
            text = String.format("%.1f", avg),
            fontWeight = FontWeight.SemiBold,
            color = EazColors.TextPrimary,
            modifier = Modifier.padding(start = 4.dp)
        )
        Text(
            text = "($count)",
            color = EazColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

private fun parseCreatorShopProduct(o: JSONObject): CreatorShopProduct {
    val handle = o.optString("handle", "").trim()
    val title = o.optString("title", handle)
    val priceRaw = o.optString("price", "").trim()
    val priceAmount = when {
        o.has("price_amount") && !o.isNull("price_amount") -> o.optDouble("price_amount").takeIf { it > 0.0 }
        else -> priceRaw.replace(",", ".").replace(Regex("[^0-9.]"), "").toDoubleOrNull()
    }
    val createdAtMs = parseCreatedAtMs(o.optString("created_at", ""))
    val designStyleRaw = o.optString("design_style", "").ifBlank { o.optString("designStyle", "") }
    val designStyles = when {
        o.has("design_style") && o.opt("design_style") is JSONArray -> {
            val arr = o.optJSONArray("design_style") ?: JSONArray()
            (0 until arr.length()).mapNotNull { arr.optString(it).trim().ifBlank { null } }
        }
        designStyleRaw.isNotBlank() -> designStyleRaw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        else -> emptyList()
    }
    return CreatorShopProduct(
        handle = handle,
        title = title,
        productName = o.optString("product_name", "").trim().ifBlank { null },
        shopifyProductId = o.optString("id", "").trim().ifBlank { null },
        imageUrl = run {
            val preview = o.optString("preview_image_url", "").trim()
            if (preview.isNotBlank()) preview
            else o.optJSONArray("images")?.optJSONObject(0)?.optString("src", "")?.trim()?.ifBlank { null }
                ?: o.optString("image", "").trim().ifBlank { null }
        },
        price = priceRaw.ifBlank { null },
        priceAmount = priceAmount,
        createdAtMs = createdAtMs,
        productType = o.optString("product_type", "").trim().ifBlank { null },
        contentType = o.optString("content_type", "").ifBlank { o.optString("contentType", "") },
        designType = o.optString("design_type", "").ifBlank { o.optString("designType", "") },
        designStyle = designStyles,
        ratio = o.optString("design_ratio", "").ifBlank { o.optString("ratio", "") },
        designLanguage = o.optString("design_language", "").ifBlank { o.optString("designLanguage", "") },
    )
}

private fun CreatorShopProduct.toFilterProductItem(): ShopifyProductsApi.ProductItem {
    val idNum = shopifyProductId?.filter { it.isDigit() }?.toLongOrNull()
        ?: handle.hashCode().toLong().let { if (it < 0) -it else it }
    return ShopifyProductsApi.ProductItem(
        id = idNum,
        title = title,
        handle = handle,
        images = listOfNotNull(imageUrl),
        url = "https://www.eazpire.com/products/$handle",
        price = priceAmount ?: 0.0,
        createdAt = createdAtMs?.toString().orEmpty(),
        productType = productType.orEmpty(),
        contentType = contentType,
        designType = designType,
        designStyle = designStyle,
        ratio = ratio,
        designLanguage = designLanguage,
        patProductName = productName.orEmpty(),
    )
}

private fun parseCreatedAtMs(raw: String): Long? {
    if (raw.isBlank()) return null
    raw.toLongOrNull()?.let { return it }
    return try {
        Instant.parse(raw).toEpochMilli()
    } catch (_: Exception) {
        null
    }
}

private fun splitCreatorProductTitle(title: String, productName: String?): Pair<String, String> {
    val normalized = title
        .replace(" — ", " | ")
        .replace(" – ", " | ")
        .replace(" - ", " | ")
    val parts = normalized.split(" | ").map { it.trim() }.filter { it.isNotBlank() }
    val design = parts.firstOrNull()?.ifBlank { title } ?: title
    val label = when {
        parts.size > 1 -> parts.drop(1).joinToString(" - ")
        !productName.isNullOrBlank() -> productName.trim()
        else -> ""
    }
    return design to label
}

private fun parseReviewCreatedAtMs(raw: Long): Long? {
    if (raw <= 0L) return null
    return if (raw < 1_000_000_000_000L) raw * 1000L else raw
}

private fun enrichCreatorReview(
    review: CreatorReviewItem,
    products: List<CreatorShopProduct>
): CreatorReviewItem {
    var handle = review.productHandle
    var title = review.productTitle
    var image = review.productImage
    val pid = review.shopifyProductId?.filter { it.isDigit() }.orEmpty()
    if (pid.isNotBlank()) {
        val match = products.firstOrNull { p ->
            p.shopifyProductId?.filter { it.isDigit() } == pid
        }
        if (match != null) {
            if (handle.isNullOrBlank()) handle = match.handle
            if (title.isNullOrBlank()) title = match.title
            if (image.isNullOrBlank()) image = match.imageUrl
        }
    }
    return review.copy(
        productHandle = handle,
        productTitle = title,
        productImage = image
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatorReviewsModal(
    visible: Boolean,
    api: CreatorApi,
    creatorName: String,
    creatorSlug: String,
    ownerId: String?,
    ratingAvg: Double?,
    ratingCount: Int?,
    shopProducts: List<CreatorShopProduct>,
    t: (String, String) -> String,
    onDismiss: () -> Unit,
    onProductClick: (String) -> Unit
) {
    if (!visible) return

    val modalTitle = remember(creatorName) {
        t("eaz.creator_profile.reviews_modal_title_creator", "{{ name }} reviews")
            .replace("{{ name }}", creatorName.ifBlank { "Creator" })
    }

    var loading by remember(visible, creatorName, ownerId) { mutableStateOf(true) }
    var reviews by remember(visible, creatorName) { mutableStateOf<List<CreatorReviewItem>>(emptyList()) }
    var summaryAvg by remember(visible, creatorName) { mutableStateOf(ratingAvg) }
    var summaryCount by remember(visible, creatorName) { mutableStateOf(ratingCount) }
    var emptyMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(visible, creatorName, creatorSlug, ownerId) {
        if (!visible) return@LaunchedEffect
        loading = true
        emptyMessage = null
        reviews = emptyList()
        try {
            val data = withContext(Dispatchers.IO) {
                api.getCreatorReviews(
                    creatorName = creatorName.takeIf { it.isNotBlank() },
                    creatorSlug = creatorSlug.takeIf { it.isNotBlank() },
                    ownerId = ownerId
                )
            }
            Log.d(
                "CreatorReviews",
                "get-creator-reviews ok=${data.optBoolean("ok")} source=${data.optString("reviews_source")} count=${data.optJSONArray("reviews")?.length() ?: 0}"
            )
            if (!data.optBoolean("ok", false)) {
                emptyMessage = data.optString("error", "").ifBlank {
                    t("eaz.creator_profile.reviews_empty", "No reviews for this creator yet.")
                }
                return@LaunchedEffect
            }
            val ratingObj = data.optJSONObject("rating")
            summaryAvg = ratingObj?.optDouble("avg")?.takeIf { it > 0.0 }
                ?: ratingObj?.optDouble("rating")?.takeIf { it > 0.0 }
                ?: ratingAvg
            summaryCount = ratingObj?.optInt("count")?.takeIf { it > 0 }
                ?: ratingObj?.optInt("rating_count")?.takeIf { it > 0 }
                ?: ratingCount
            val arr = data.optJSONArray("reviews") ?: JSONArray()
            val list = mutableListOf<CreatorReviewItem>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val raw = enrichCreatorReview(
                    CreatorReviewItem(
                        id = o.optString("id", i.toString()),
                        rating = o.optDouble("rating", 0.0),
                        title = o.optString("title", "").trim(),
                        body = o.optString("body", "").trim(),
                        reviewerName = o.optString("reviewer_name", "").trim(),
                        createdAtMs = parseReviewCreatedAtMs(o.optLong("created_at", 0L)),
                        shopifyProductId = o.optString("shopify_product_id", "").trim().ifBlank { null },
                        productHandle = o.optString("product_handle", "").trim().ifBlank { null },
                        productTitle = o.optString("product_title", "").trim().ifBlank { null },
                        productImage = o.optString("product_image", "").trim().ifBlank { null }
                    ),
                    shopProducts
                )
                list.add(raw)
            }
            reviews = list
            if (list.isEmpty()) {
                emptyMessage = t("eaz.creator_profile.reviews_empty", "No reviews for this creator yet.")
            }
        } catch (e: Exception) {
            Log.w("CreatorReviews", "get-creator-reviews failed", e)
            emptyMessage = t("eaz.creator_profile.reviews_empty", "No reviews for this creator yet.")
        } finally {
            loading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .heightIn(min = 360.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    modalTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = t("eaz.creator_profile.close", "Close"),
                        tint = EazColors.TextSecondary
                    )
                }
            }
            if (summaryAvg != null && summaryCount != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(5) { index ->
                        val filled = summaryAvg!! >= index + 1 - 0.25
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = if (filled) EazColors.Orange else Color(0xFFDDDDDD),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = String.format("%.1f", summaryAvg),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                    Text(
                        text = "($summaryCount)",
                        color = EazColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
            when {
                loading -> Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = EazColors.Orange)
                }
                !emptyMessage.isNullOrBlank() -> Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(emptyMessage!!, color = EazColors.TextSecondary)
                }
                else -> LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(reviews, key = { it.id }) { review ->
                        CreatorReviewListItem(
                            review = review,
                            t = t,
                            onProductClick = onProductClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatorReviewListItem(
    review: CreatorReviewItem,
    t: (String, String) -> String,
    onProductClick: (String) -> Unit
) {
    val author = review.reviewerName.ifBlank { t("eaz.creator_profile.review_anonymous", "Anonymous") }
    val dateLabel = review.createdAtMs?.let {
        DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(it))
    }.orEmpty()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val handle = review.productHandle
        if (!review.productImage.isNullOrBlank() || !handle.isNullOrBlank()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(80.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEFEFEF))
                        .then(
                            if (!handle.isNullOrBlank()) Modifier.clickable { onProductClick(handle) }
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (!review.productImage.isNullOrBlank()) {
                        AsyncImage(
                            model = review.productImage,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                if (!review.productTitle.isNullOrBlank()) {
                    Text(
                        text = review.productTitle,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                repeat(5) { index ->
                    val filled = review.rating >= index + 1 - 0.25
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = if (filled) EazColors.Orange else Color(0xFFDDDDDD),
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = String.format("%.1f", review.rating),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 6.dp)
                )
                if (dateLabel.isNotBlank()) {
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = EazColors.TextSecondary,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            Text(
                text = author,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (review.title.isNotBlank()) {
                Text(
                    text = review.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            if (review.body.isNotBlank()) {
                Text(
                    text = review.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = EazColors.TextSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

private fun sortCreatorProducts(list: List<CreatorShopProduct>, sortBy: String): List<CreatorShopProduct> {
    val copy = list.toMutableList()
    when (sortBy) {
        "title-ascending" -> copy.sortBy { it.title.lowercase(Locale.ROOT) }
        "title-descending" -> copy.sortByDescending { it.title.lowercase(Locale.ROOT) }
        "price-ascending" -> copy.sortWith(compareBy({ it.priceAmount ?: Double.MAX_VALUE }, { it.title }))
        "price-descending" -> copy.sortWith(compareByDescending<CreatorShopProduct> { it.priceAmount ?: 0.0 }.thenBy { it.title })
        "created-ascending", "date-asc" -> copy.sortBy { it.createdAtMs ?: 0L }
        "created-descending", "date-desc" -> copy.sortByDescending { it.createdAtMs ?: 0L }
        else -> copy.sortByDescending { it.createdAtMs ?: 0L }
    }
    return copy
}
