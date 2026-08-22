package com.eazpire.creator.ui.creator

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.ShopifyProductsApi
import com.eazpire.creator.ui.CREATIONS_PRODUCTS_PER_PAGE
import com.eazpire.creator.ui.PaginationDotsStyle
import com.eazpire.creator.ui.ProductPaginationDots
import com.eazpire.creator.ui.ShopStyleProductImages
import com.eazpire.creator.auth.AuthConfig
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Creations Screen – 1:1 wie Web (creator-mobile-creations.liquid + creator-creations-screen.js)
 * Designs | Products tabs, search, filter, upload, grid/list view
 */
data class CreationDesign(
    val id: String?,
    val designId: String?,
    val jobId: String?,
    val imageUrl: String,
    val previewUrl: String,
    val originalUrl: String,
    val title: String,
    val prompt: String?,
    val designPrompt: String?,
    val createdAt: Long,
    val source: String, // generated, uploaded, saved
    val designSource: String,
    val creatorName: String?,
    val productsCount: Int,
    val ratio: String? = null,
    val designType: String? = null,
    val contentType: String? = null,
    val libraryStatus: String = "active",
    /** True while auto-save / save queue writes the inactive library row (show spinner overlay). */
    val savingToLibrary: Boolean = false,
    /** True while a publish session is in flight (show spinner; block open / bulk select). */
    val publishActive: Boolean = false,
    val publishSessionId: String? = null,
    val reviewStatus: String? = null,
    val qualityRating: String? = null,
    val remixCount: Int = 0,
    val favoriteCount: Int = 0,
) {
    val sortUpdatedAt: Long get() = createdAt
}

data class CreationProduct(
    val id: String,
    val title: String,
    val productName: String,
    val productKey: String,
    val imageUrl: String?,
    val storefrontUrl: String?,
    val shopifyHandle: String?,
    val publishedAt: Long?,
    val publishedCount: Int = 0,
    val isSample: Boolean = false,
    val publishedDesignId: String? = null,
    val designIds: List<String> = emptyList(),
    val updatedAt: Long? = null,
    val favoriteCount: Int = 0,
    val remixCount: Int = 0,
) {
    val sortUpdatedAt: Long get() = updatedAt ?: publishedAt ?: 0L
}

private val VIEW_MODES = listOf("grid2", "grid3", "grid4", "list")

private fun normalizeImageUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return if (url.startsWith("//")) "https:$url" else url
}

private fun extractShopifyHandle(product: CreationProduct): String? {
    product.shopifyHandle?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    val storefront = product.storefrontUrl?.takeIf { it.isNotBlank() } ?: return null
    return try {
        val segments = Uri.parse(storefront).pathSegments
        val productIndex = segments.indexOf("products")
        if (productIndex >= 0 && productIndex + 1 < segments.size) {
            segments[productIndex + 1].substringBefore("?").trim().takeIf { it.isNotBlank() }
        } else null
    } catch (_: Exception) {
        null
    }
}

/** Same URL list as shop cards: worker product-json → variantImages (or API fallback). */
private fun creationProductDisplayUrls(
    product: CreationProduct,
    overrides: Map<String, List<String>>
): List<String> {
    overrides[product.id]?.takeIf { it.isNotEmpty() }?.let { return it }
    return listOfNotNull(normalizeImageUrl(product.imageUrl)).filter { it.isNotBlank() }.distinct()
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun CreatorCreationsScreen(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    maxHeight: Dp = Dp.Infinity,
    modifier: Modifier = Modifier,
    onRequestGeneratorPrefill: (GeneratorPrefillRequest) -> Unit = {},
    onGlowUpJobStarted: (jobId: String, summary: String) -> Unit = { _, _ -> },
    onOpenEazyJobs: () -> Unit = {},
    initialDesignsActivityFilter: String? = null,
    onInitialDesignsActivityConsumed: () -> Unit = {},
    initialCreationsTab: String? = null,
    onInitialCreationsTabConsumed: () -> Unit = {},
) {
    val boundedHeight = if (maxHeight == Dp.Infinity) 4000.dp else maxHeight
    val context = LocalContext.current
    val jwt = remember { runCatching { tokenStore.getJwt() }.getOrNull() }
    val ownerId = remember { runCatching { tokenStore.getOwnerId() }.getOrNull() ?: "" }
    val api = remember(jwt) { com.eazpire.creator.api.CreatorApi(jwt = jwt) }
    val shopifyApi = remember { ShopifyProductsApi() }
    val shop = AuthConfig.SHOP_DOMAIN

    var currentTab by remember { mutableStateOf("designs") }
    var designsActivityFilter by remember { mutableStateOf("active") }
  /** Full merged design list (active + inactive); activity tab filters client-side only. */
    var designs by remember { mutableStateOf<List<CreationDesign>>(emptyList()) }
    var products by remember { mutableStateOf<List<CreationProduct>>(emptyList()) }
    var productBadgesByDesignId by remember { mutableStateOf<Map<String, CreationProductBadge>>(emptyMap()) }
    var designsLoadedOnce by remember { mutableStateOf(false) }
    var productsLoadedOnce by remember { mutableStateOf(false) }
    var designsLoading by remember { mutableStateOf(false) }
    var productsLoading by remember { mutableStateOf(false) }
    var designsSearch by remember { mutableStateOf(TextFieldValue("")) }
    var productsSearch by remember { mutableStateOf(TextFieldValue("")) }
    var viewMode by remember { mutableIntStateOf(0) } // grid2=0, grid3=1, grid4=2, list=3
    var filterModalVisible by remember { mutableStateOf(false) }
    var viewModeOverlayVisible by remember { mutableStateOf(false) }
    var designPreviewDesign by remember { mutableStateOf<CreationDesign?>(null) }
    var creationsFilter by remember { mutableStateOf(CreationsFilterState()) }
    var designsSort by remember { mutableStateOf(CreationsSortState()) }
    var productsSort by remember { mutableStateOf(CreationsSortState()) }
    var productsSortAvailability by remember { mutableStateOf(CreationsSortAvailability(favorites = true, remixes = true)) }
    var designsSortMenuOpen by remember { mutableStateOf(false) }
    var productsSortMenuOpen by remember { mutableStateOf(false) }
    var designsRefreshTrigger by remember { mutableIntStateOf(0) }
    var bulkSelectedKeys by remember { mutableStateOf(setOf<String>()) }
    var activateTargets by remember { mutableStateOf<List<CreationDesign>>(emptyList()) }
    var showActivateDialog by remember { mutableStateOf(false) }
    var deactivateTargets by remember { mutableStateOf<List<CreationDesign>>(emptyList()) }
    var showDeactivateDialog by remember { mutableStateOf(false) }
    var deleteTargets by remember { mutableStateOf<List<CreationDesign>>(emptyList()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var saveTargets by remember { mutableStateOf<List<CreationDesign>>(emptyList()) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var libraryActionBusy by remember { mutableStateOf(false) }
    var creatorNamesCache by remember { mutableStateOf<List<String>>(emptyList()) }
    var ratingTargets by remember { mutableStateOf<List<CreationDesign>>(emptyList()) }
    val ratingSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(designsActivityFilter, currentTab) {
        bulkSelectedKeys = emptySet()
    }

    LaunchedEffect(initialDesignsActivityFilter) {
        val f = initialDesignsActivityFilter?.trim()?.lowercase().orEmpty()
        if (f == "inactive" || f == "active") {
            currentTab = "designs"
            designsActivityFilter = f
            designsRefreshTrigger++
            onInitialDesignsActivityConsumed()
        }
    }

    LaunchedEffect(initialCreationsTab) {
        val tab = initialCreationsTab?.trim()?.lowercase().orEmpty()
        if (tab == "designs" || tab == "products") {
            currentTab = tab
            onInitialCreationsTabConsumed()
        }
    }

    var uploadInProgress by remember { mutableStateOf(false) }
    var uploadModalVisible by remember { mutableStateOf(false) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var productImageOverrides by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var productFallbackRequestedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val productIdsSignature = remember(products) { products.joinToString("\u0001") { it.id } }
    val designsListState = rememberLazyListState()
    val designsGridState = rememberLazyGridState()
    val productsListState = rememberLazyListState()
    val productsGridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()

    // When the published-products list changes, allow re-fetching shop-style URLs for the new rows.
    LaunchedEffect(productIdsSignature) {
        productFallbackRequestedIds = emptySet()
    }

    // Same as shop CollectionScreen: product-json → ProductItem.variantImages; parallel per handle.
    LaunchedEffect(productIdsSignature, currentTab) {
        if (currentTab != "products") return@LaunchedEffect
        val needFetch = products.mapNotNull { p ->
            if (productFallbackRequestedIds.contains(p.id)) return@mapNotNull null
            if (!productImageOverrides[p.id].isNullOrEmpty()) return@mapNotNull null
            val handle = extractShopifyHandle(p) ?: return@mapNotNull null
            p to handle
        }
        if (needFetch.isEmpty()) return@LaunchedEffect
        productFallbackRequestedIds = productFallbackRequestedIds + needFetch.map { it.first.id }
        val newOverrides = coroutineScope {
            val concurrency = Semaphore(12)
            needFetch.map { (p, handle) ->
                async(Dispatchers.IO) {
                    concurrency.withPermit {
                        val urls = runCatching { shopifyApi.getShopCardImageUrls(handle) }
                            .getOrNull()
                            .orEmpty()
                            .mapNotNull { normalizeImageUrl(it) }
                            .filter { it.isNotBlank() }
                            .distinct()
                        if (urls.isNotEmpty()) p.id to urls else null
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }
        if (newOverrides.isNotEmpty()) {
            productImageOverrides = productImageOverrides + newOverrides
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        selectedImageUri = uri
        uploadModalVisible = true
    }

    LaunchedEffect(ownerId) {
        designsLoadedOnce = false
        productsLoadedOnce = false
        designs = emptyList()
        products = emptyList()
        productBadgesByDesignId = emptyMap()
        designsLoading = false
        productsLoading = false
    }

    LaunchedEffect(ownerId, designsRefreshTrigger) {
        if (ownerId.isBlank()) {
            designsLoading = false
            return@LaunchedEffect
        }
        val showBlockingSpinner = !designsLoadedOnce
        if (showBlockingSpinner) designsLoading = true
        try {
            val list = withContext(Dispatchers.IO) {
                fetchCreationsDesignsMerged(api, ownerId)
            }
            designs = list
            designsLoadedOnce = true
        } catch (_: Exception) {
            if (!designsLoadedOnce) designs = emptyList()
        } finally {
            designsLoading = false
        }
    }

    LaunchedEffect(ownerId, designsLoadedOnce, designsRefreshTrigger) {
        if (ownerId.isBlank() || !designsLoadedOnce) return@LaunchedEffect
        val badges = withContext(Dispatchers.IO) {
            fetchCreationsProductBadgesMap(api, ownerId, shop)
        }
        if (badges.isNotEmpty()) {
            productBadgesByDesignId = badges
        }
    }

    LaunchedEffect(ownerId, currentTab) {
        if (ownerId.isBlank() || currentTab != "products") {
            if (ownerId.isBlank()) productsLoading = false
            return@LaunchedEffect
        }
        if (productsLoadedOnce) return@LaunchedEffect
        productsLoading = true
        try {
            val list = withContext(Dispatchers.IO) {
                fetchPublishedCreationsProducts(api, ownerId, shop)
            }
            products = list
            productsLoadedOnce = true
        } catch (_: Exception) {
            products = emptyList()
        } finally {
            productsLoading = false
        }
    }

    LaunchedEffect(designs.any { it.savingToLibrary }, designsActivityFilter, ownerId) {
        if (ownerId.isBlank() || designsActivityFilter != "inactive") return@LaunchedEffect
        if (!designs.any { it.savingToLibrary }) return@LaunchedEffect
        delay(3000)
        designsRefreshTrigger++
    }

    LaunchedEffect(designs.any { it.publishActive }, ownerId) {
        if (ownerId.isBlank()) return@LaunchedEffect
        if (!designs.any { it.publishActive }) return@LaunchedEffect
        delay(4000)
        designsRefreshTrigger++
    }

    val activityFilteredDesigns = remember(designs, designsActivityFilter) {
        designs
            .filter { d ->
                val ls = d.effectiveLibraryStatus()
                if (designsActivityFilter == "active") ls == "active" else ls == "inactive"
            }
    }

    val filteredDesigns = remember(activityFilteredDesigns, designsSearch.text, creationsFilter, designsSort, productBadgesByDesignId) {
        val q = designsSearch.text.trim().lowercase()
        var list = if (q.isBlank()) activityFilteredDesigns else activityFilteredDesigns.filter { d ->
            (d.title.lowercase().contains(q) || (d.prompt?.lowercase()?.contains(q) == true))
        }
        val f = creationsFilter
        if (!f.isEmpty()) {
            list = list.filter { d ->
                if (f.designArt.isNotEmpty()) {
                    val src = when (d.designSource.lowercase()) {
                        "generated" -> "generated"
                        "uploaded" -> "uploaded"
                        "saved" -> "personalized"
                        else -> d.designSource.lowercase()
                    }
                    if (src !in f.designArt.map { it.lowercase() }) return@filter false
                }
                if (f.ratio.isNotEmpty() && d.ratio != null) {
                    if (d.ratio !in f.ratio.map { it.lowercase() }) return@filter false
                }
                if (f.designType.isNotEmpty() && d.designType != null) {
                    if (d.designType !in f.designType.map { it.lowercase() }) return@filter false
                }
                if (f.contentType.isNotEmpty() && d.contentType != null) {
                    if (d.contentType !in f.contentType.map { it.lowercase() }) return@filter false
                }
                true
            }
        }
        sortCreationDesigns(list, designsSort) { d ->
            val id = d.id ?: d.designId ?: ""
            productBadgesByDesignId[id]?.published ?: d.productsCount
        }
    }

    val filteredProducts = remember(products, productsSearch.text, creationsFilter, productsSort) {
        val q = productsSearch.text.trim().lowercase()
        var list = if (q.isBlank()) products else products.filter { p ->
            p.title.lowercase().contains(q) || p.productName.lowercase().contains(q) || p.productKey.lowercase().contains(q)
        }
        val f = creationsFilter
        if (!f.isEmpty()) {
            list = list.filter { p ->
                if (f.sales.isNotEmpty()) {
                    val ok = f.sales.any { range ->
                        when (range) {
                            "0" -> p.publishedCount == 0
                            "1-10" -> p.publishedCount in 1..10
                            "11-50" -> p.publishedCount in 11..50
                            "51-100" -> p.publishedCount in 51..100
                            "100+" -> p.publishedCount >= 100
                            else -> false
                        }
                    }
                    if (!ok) return@filter false
                }
                true
            }
        }
        sortCreationProducts(list, productsSort)
    }

    var productsListPage by remember { mutableIntStateOf(1) }
    val filteredProductsPageKey = remember(filteredProducts) { filteredProducts.joinToString("\u0001") { it.id } }
    LaunchedEffect(filteredProductsPageKey, currentTab) {
        if (currentTab == "products") productsListPage = 1
    }
    val productsTotalPages = remember(filteredProducts.size) {
        maxOf(1, (filteredProducts.size + CREATIONS_PRODUCTS_PER_PAGE - 1) / CREATIONS_PRODUCTS_PER_PAGE)
    }
    LaunchedEffect(productsTotalPages) {
        if (productsListPage > productsTotalPages) productsListPage = productsTotalPages
    }
    val pagedProducts = remember(filteredProducts, productsListPage, productsTotalPages) {
        val idx = (productsListPage - 1).coerceIn(0, (productsTotalPages - 1).coerceAtLeast(0))
        val start = idx * CREATIONS_PRODUCTS_PER_PAGE
        filteredProducts.drop(start).take(CREATIONS_PRODUCTS_PER_PAGE)
    }

    val gridCols = when (VIEW_MODES[viewMode]) {
        "grid2" -> 2
        "grid3" -> 3
        "grid4" -> 4
        else -> 2
    }
    val isListMode = VIEW_MODES[viewMode] == "list"
    val bulkCohort = remember(bulkSelectedKeys, designsActivityFilter) {
        resolveBulkCohort(bulkSelectedKeys, designsActivityFilter)
    }
    val selectedDesignObjects = remember(filteredDesigns, bulkSelectedKeys) {
        designsFromSelectedKeys(filteredDesigns, bulkSelectedKeys)
    }

    LaunchedEffect(showActivateDialog, showSaveDialog) {
        if ((showActivateDialog || showSaveDialog) && ownerId.isNotBlank()) {
            creatorNamesCache = CreationsDesignLibraryActions.loadCreatorNames(api, ownerId)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .heightIn(max = boundedHeight)
    ) {
        // Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1C2434).copy(alpha = 0.75f))
                .padding(horizontal = 18.dp, vertical = 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f)) {
                val tabs = listOf(
                    "designs" to translationStore.t("creator.mobile.designs", "Designs"),
                    "products" to translationStore.t("creator.mobile.products", "Products")
                )
                for ((tab, label) in tabs) {
                    val active = currentTab == tab
                    Box(
                        modifier = Modifier
                            .clickable { currentTab = tab }
                            .padding(vertical = 12.dp, horizontal = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) EazColors.Orange else Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            IconButton(onClick = { viewModeOverlayVisible = true }) {
                Icon(Icons.Default.GridView, contentDescription = null, tint = Color.White)
            }
        }

        when (currentTab) {
            "designs" -> {
                CreationsDesignsToolbar(
                    designsSearch = designsSearch,
                    onDesignsSearchChange = { designsSearch = it },
                    onFilterClick = { filterModalVisible = true },
                    onUploadClick = {
                        if (!uploadInProgress) imagePicker.launch("image/*")
                    },
                    designsActivityFilter = designsActivityFilter,
                    onDesignsActivityChange = { key ->
                        if (designsActivityFilter != key) {
                            designsActivityFilter = key
                        }
                    },
                    designsCount = filteredDesigns.size,
                    translationStore = translationStore,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                ) {
                    val bulkBottomPad = if (bulkSelectedKeys.isNotEmpty()) 118.dp else 0.dp
                    val showDesignsSpinner = designsLoading && !designsLoadedOnce
                    when {
                        showDesignsSpinner -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = EazColors.Orange)
                            }
                        }
                        filteredDesigns.isEmpty() -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.White.copy(alpha = 0.5f))
                                    Spacer(Modifier.size(8.dp))
                                    Text(
                                        if (activityFilteredDesigns.isEmpty()) translationStore.t("creator.creations.no_designs", "No designs found.") else translationStore.t("creator.mobile.no_search_results", "No search results."),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                        isListMode -> {
                            LazyColumn(
                                state = designsListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .heightIn(max = boundedHeight),
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp + bulkBottomPad),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredDesigns, key = { d -> d.id ?: d.designId ?: d.jobId ?: d.imageUrl }) { design ->
                                    CreationDesignListItem(
                                        design = design,
                                        productBadgeText = formatDesignProductBadgeText(design, productBadgesByDesignId),
                                        translationStore = translationStore,
                                        bulkSelectable = isBulkSelectableDesign(design, designsActivityFilter),
                                        selected = bulkSelectedKeys.contains(design.bulkSelectionKey()),
                                        onSelectedChange = { on ->
                                            bulkSelectedKeys = setBulkSelectedKey(design, on, bulkSelectedKeys)
                                        },
                                        onLibraryAction = if (!design.id.isNullOrBlank()) {
                                            {
                                                if (design.effectiveLibraryStatus() == "inactive") {
                                                    activateTargets = listOf(design)
                                                    showActivateDialog = true
                                                } else {
                                                    deactivateTargets = listOf(design)
                                                    showDeactivateDialog = true
                                                }
                                            }
                                        } else null,
                                        onRateClick = if (design.canRate()) {
                                            { ratingTargets = listOf(design) }
                                        } else null,
                                        onClick = {
                                            if (!design.publishActive) designPreviewDesign = design
                                        }
                                    )
                                }
                            }
                        }
                        else -> {
                            LazyVerticalGrid(
                                state = designsGridState,
                                columns = GridCells.Fixed(gridCols),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .heightIn(max = boundedHeight),
                                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp + bulkBottomPad),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(filteredDesigns, key = { d -> d.id ?: d.designId ?: d.jobId ?: d.imageUrl }) { design ->
                                    Box(Modifier.aspectRatio(1f)) {
                                        CreationDesignGridCard(
                                            design = design,
                                            productBadgeText = formatDesignProductBadgeText(design, productBadgesByDesignId),
                                            bulkSelectable = isBulkSelectableDesign(design, designsActivityFilter),
                                            selected = bulkSelectedKeys.contains(design.bulkSelectionKey()),
                                            onSelectedChange = { on ->
                                                bulkSelectedKeys = setBulkSelectedKey(design, on, bulkSelectedKeys)
                                            },
                                            onLibraryAction = if (!design.id.isNullOrBlank() && !design.publishActive) {
                                                {
                                                    if (design.effectiveLibraryStatus() == "inactive") {
                                                        activateTargets = listOf(design)
                                                        showActivateDialog = true
                                                    } else {
                                                        deactivateTargets = listOf(design)
                                                        showDeactivateDialog = true
                                                    }
                                                }
                                            } else null,
                                            onRateClick = if (design.canRate()) {
                                                { ratingTargets = listOf(design) }
                                            } else null,
                                            onClick = {
                                                if (!design.publishActive) designPreviewDesign = design
                                            },
                                            activateLabel = translationStore.t("creator.creations.library_activate_btn", "Activate"),
                                            deactivateLabel = translationStore.t("creator.creations.library_deactivate_btn", "Deactivate"),
                                            shimmer = { GridImageShimmer(Modifier.fillMaxSize()) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    CreationsDesignBulkDock(
                        selectedCount = bulkSelectedKeys.size,
                        cohort = bulkCohort,
                        translationStore = translationStore,
                        onSelectAll = {
                            bulkSelectedKeys = selectAllPoolDesigns(filteredDesigns, designsActivityFilter, bulkSelectedKeys)
                                .mapNotNull { it.bulkSelectionKey().takeIf { k -> k.isNotBlank() } }
                                .toSet()
                        },
                        onDeselectAll = { bulkSelectedKeys = emptySet() },
                        onActivate = {
                            activateTargets = selectedDesignObjects
                            showActivateDialog = true
                        },
                        onDeactivate = {
                            deactivateTargets = selectedDesignObjects
                            showDeactivateDialog = true
                        },
                        onRate = {
                            ratingTargets = selectedDesignObjects.filter { it.canRate() }
                        },
                        onDelete = {
                            deleteTargets = selectedDesignObjects
                            showDeleteDialog = true
                        },
                        onSave = {
                            saveTargets = selectedDesignObjects.filter { it.id.isNullOrBlank() && !it.jobId.isNullOrBlank() }
                            showSaveDialog = true
                        },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
            "products" -> {
                if (productsLoading) {
                    Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = EazColors.Orange)
                    }
                } else if (filteredProducts.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.White.copy(alpha = 0.5f))
                            Spacer(Modifier.size(8.dp))
                            Text(
                                if (products.isEmpty()) translationStore.t("creator.creations.no_products", "No products found.") else translationStore.t("creator.mobile.no_search_results", "No search results."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else if (isListMode) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .heightIn(max = boundedHeight)
                    ) {
                        LazyColumn(
                            state = productsListState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF262930).copy(alpha = 0.68f))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    BasicTextField(
                                        value = productsSearch,
                                        onValueChange = { productsSearch = it },
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black.copy(alpha = 0.3f))
                                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                                        decorationBox = { inner ->
                                            if (productsSearch.text.isEmpty()) {
                                                Text(
                                                    translationStore.t("creator.common.search", "Search…"),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color.White.copy(alpha = 0.5f)
                                                )
                                            }
                                            inner()
                                        }
                                    )
                                    IconButton(
                                        onClick = { filterModalVisible = true },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                    ) {
                                        Icon(Icons.Default.FilterList, contentDescription = null, tint = Color.White)
                                    }
                                    Text(
                                        "${filteredProducts.size} ${translationStore.t("creator.mobile.products", "Products")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            items(pagedProducts, key = { it.id }) { product ->
                                CreationProductListItem(
                                    product = product,
                                    imageUrls = creationProductDisplayUrls(product, productImageOverrides),
                                    translationStore = translationStore,
                                    onClick = {
                                        product.storefrontUrl?.let { url ->
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    }
                                )
                            }
                        }
                        if (productsTotalPages > 1) {
                            ProductPaginationDots(
                                totalPages = productsTotalPages,
                                currentPage = productsListPage,
                                onPageClick = { productsListPage = it },
                                onSwipePrev = {
                                    if (productsListPage > 1) productsListPage = productsListPage - 1
                                },
                                onSwipeNext = {
                                    if (productsListPage < productsTotalPages) productsListPage = productsListPage + 1
                                },
                                style = PaginationDotsStyle.Dark,
                                swipeHint = translationStore.t(
                                    "creator.mobile.products_pagination_swipe_hint",
                                    "Swipe left / right on the dots"
                                )
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .heightIn(max = boundedHeight)
                    ) {
                        LazyVerticalGrid(
                            state = productsGridState,
                            columns = GridCells.Fixed(gridCols),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item(span = { GridItemSpan(gridCols) }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF262930).copy(alpha = 0.68f))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    BasicTextField(
                                        value = productsSearch,
                                        onValueChange = { productsSearch = it },
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black.copy(alpha = 0.3f))
                                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                            .padding(10.dp),
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                                        decorationBox = { inner ->
                                            if (productsSearch.text.isEmpty()) {
                                                Text(
                                                    translationStore.t("creator.common.search", "Search…"),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color.White.copy(alpha = 0.5f)
                                                )
                                            }
                                            inner()
                                        }
                                    )
                                    IconButton(
                                        onClick = { filterModalVisible = true },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                    ) {
                                        Icon(Icons.Default.FilterList, contentDescription = null, tint = Color.White)
                                    }
                                    Text(
                                        "${filteredProducts.size} ${translationStore.t("creator.mobile.products", "Products")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            items(pagedProducts, key = { it.id }) { product ->
                                CreationProductCard(
                                    product = product,
                                    imageUrls = creationProductDisplayUrls(product, productImageOverrides),
                                    onClick = {
                                        product.storefrontUrl?.let { url ->
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    }
                                )
                            }
                        }
                        if (productsTotalPages > 1) {
                            ProductPaginationDots(
                                totalPages = productsTotalPages,
                                currentPage = productsListPage,
                                onPageClick = { productsListPage = it },
                                onSwipePrev = {
                                    if (productsListPage > 1) productsListPage = productsListPage - 1
                                },
                                onSwipeNext = {
                                    if (productsListPage < productsTotalPages) productsListPage = productsListPage + 1
                                },
                                style = PaginationDotsStyle.Dark,
                                swipeHint = translationStore.t(
                                    "creator.mobile.products_pagination_swipe_hint",
                                    "Swipe left / right on the dots"
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    if (showActivateDialog) {
        CreationsActivateDesignDialog(
            targets = activateTargets,
            creatorNames = creatorNamesCache,
            translationStore = translationStore,
            api = api,
            ownerId = ownerId,
            shop = shop,
            busy = libraryActionBusy,
            onDismiss = {
                if (!libraryActionBusy) {
                    showActivateDialog = false
                    activateTargets = emptyList()
                }
            },
            onConfirm = { creatorName, visibilityPublic, activateWithout, publishExcluded ->
                scope.launch {
                    libraryActionBusy = true
                    try {
                        var ok = true
                        activateTargets.forEach { d ->
                            val id = d.id?.trim().orEmpty()
                            if (id.isBlank()) return@forEach
                            val excluded = if (activateTargets.size == 1) publishExcluded else null
                            ok = ok && CreationsDesignLibraryActions.activateDesign(
                                api,
                                id,
                                creatorName,
                                if (visibilityPublic) "public" else "private",
                                activateWithout,
                                excluded,
                            )
                        }
                        if (ok) {
                            bulkSelectedKeys = emptySet()
                            showActivateDialog = false
                            activateTargets = emptyList()
                            designsActivityFilter = "active"
                            designsRefreshTrigger++
                        }
                    } finally {
                        libraryActionBusy = false
                    }
                }
            },
        )
    }

    if (showSaveDialog) {
        CreationsActivateDesignDialog(
            targets = saveTargets,
            creatorNames = creatorNamesCache,
            translationStore = translationStore,
            api = api,
            ownerId = ownerId,
            shop = shop,
            busy = libraryActionBusy,
            onDismiss = {
                if (!libraryActionBusy) {
                    showSaveDialog = false
                    saveTargets = emptyList()
                }
            },
            onConfirm = { creatorName, visibilityPublic, activateWithout, _ ->
                scope.launch {
                    libraryActionBusy = true
                    try {
                        runBulkSaveWithThrottle(
                            saveTargets, ownerId, api, creatorName,
                            visibilityPublic,
                        )
                        bulkSelectedKeys = emptySet()
                        showSaveDialog = false
                        saveTargets = emptyList()
                        designsActivityFilter = "active"
                        designsRefreshTrigger++
                    } finally {
                        libraryActionBusy = false
                    }
                }
            },
        )
    }

    if (showDeactivateDialog) {
        CreationsConfirmActionDialog(
            title = if (deactivateTargets.size > 1) {
                translationStore.t("creator.creations.bulk_deactivate_title", "Deactivate designs")
            } else {
                translationStore.t("creator.creations.library_deactivate_title", "Deactivate design")
            },
            message = translationStore.t(
                "creator.creations.library_deactivate_simple_intro",
                "The design will be moved to Inactive. Published products can be unpublished."
            ),
            confirmLabel = translationStore.t("creator.creations.library_confirm_deactivate", "Deactivate"),
            busy = libraryActionBusy,
            onDismiss = {
                if (!libraryActionBusy) {
                    showDeactivateDialog = false
                    deactivateTargets = emptyList()
                }
            },
            onConfirm = {
                scope.launch {
                    libraryActionBusy = true
                    try {
                        var ok = true
                        deactivateTargets.forEach { d ->
                            val id = d.id?.trim().orEmpty()
                            if (id.isBlank()) return@forEach
                            ok = ok && CreationsDesignLibraryActions.deactivateDesign(api, ownerId, shop, id)
                        }
                        if (ok) {
                            bulkSelectedKeys = emptySet()
                            showDeactivateDialog = false
                            deactivateTargets = emptyList()
                            designsRefreshTrigger++
                        }
                    } finally {
                        libraryActionBusy = false
                    }
                }
            },
        )
    }

    if (showDeleteDialog) {
        CreationsConfirmActionDialog(
            title = translationStore.t("creator.creations.bulk_delete_title", "Delete designs"),
            message = if (deleteTargets.any { it.id.isNullOrBlank() }) {
                translationStore.t(
                    "creator.creations.bulk_delete_jobs_intro",
                    "These generated designs are not in your library yet. They will be removed permanently."
                )
            } else {
                translationStore.t("creator.creations.bulk_delete_intro", "Delete selected designs permanently?")
            },
            confirmLabel = translationStore.t("creator.creations.bulk_confirm_delete", "Delete"),
            busy = libraryActionBusy,
            danger = true,
            onDismiss = {
                if (!libraryActionBusy) {
                    showDeleteDialog = false
                    deleteTargets = emptyList()
                }
            },
            onConfirm = {
                scope.launch {
                    libraryActionBusy = true
                    try {
                        var ok = true
                        deleteTargets.forEach { d ->
                            val id = d.id?.trim().orEmpty()
                            val jid = d.jobId?.trim().orEmpty()
                            ok = ok && if (id.isNotBlank()) {
                                CreationsDesignLibraryActions.deleteSavedDesign(api, ownerId, id)
                            } else if (jid.isNotBlank()) {
                                CreationsDesignLibraryActions.deleteGeneratedJob(api, ownerId, jid)
                            } else {
                                false
                            }
                        }
                        if (ok) {
                            bulkSelectedKeys = emptySet()
                            showDeleteDialog = false
                            deleteTargets = emptyList()
                            designsRefreshTrigger++
                        }
                    } finally {
                        libraryActionBusy = false
                    }
                }
            },
        )
    }

    if (filterModalVisible) {
        CreatorFilterModal(
            onDismiss = { filterModalVisible = false },
            source = currentTab,
            translationStore = translationStore,
            initialFilter = creationsFilter,
            onApply = { creationsFilter = it },
            designs = designs,
            products = products
        )
    }

    if (uploadModalVisible) {
        CreatorDesignUploadModal(
            onDismiss = {
                uploadModalVisible = false
                selectedImageUri = null
            },
            selectedImageUri = selectedImageUri,
            onSelectImage = { imagePicker.launch("image/*") },
            onRemoveImage = { selectedImageUri = null },
            onUpload = { creatorName, visibility, imageBytes, mimeType ->
                if (ownerId.isBlank()) return@CreatorDesignUploadModal
                scope.launch {
                    uploadInProgress = true
                    try {
                        val name = selectedImageUri?.lastPathSegment ?: "upload.png"
                        val resp = api.uploadDesign(ownerId, imageBytes, mimeType, name, creatorName, visibility)
                        if (resp.optBoolean("ok", false)) {
                            designsRefreshTrigger++
                            uploadModalVisible = false
                            selectedImageUri = null
                        }
                    } finally {
                        uploadInProgress = false
                    }
                }
            },
            uploadInProgress = uploadInProgress,
            translationStore = translationStore,
            api = api,
            ownerId = ownerId
        )
    }

    if (viewModeOverlayVisible) {
        CreatorViewModeOverlay(
            currentMode = viewMode,
            onSelect = { viewMode = it; viewModeOverlayVisible = false },
            onDismiss = { viewModeOverlayVisible = false },
            translationStore = translationStore
        )
    }

    designPreviewDesign?.let { d ->
        DesignDetailSheet(
            design = d,
            onDismiss = { designPreviewDesign = null },
            translationStore = translationStore,
            tokenStore = tokenStore,
            onRequestGeneratorPrefill = onRequestGeneratorPrefill,
            onGlowUpJobStarted = onGlowUpJobStarted,
            onOpenEazyJobs = onOpenEazyJobs
        )
    }

    ratingTargets.takeIf { it.isNotEmpty() }?.let { targets ->
        val isBulk = targets.size > 1
        val targetKeys = targets.map { it.bulkSelectionKey() }.filter { it.isNotBlank() }.toSet()
        ModalBottomSheet(
            onDismissRequest = { ratingTargets = emptyList() },
            sheetState = ratingSheetState,
            containerColor = Color(0xFF1E293B),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (isBulk) {
                        translationStore.t("creator.creations.bulk_rate_title", "Rate selected designs")
                    } else {
                        translationStore.t("creator.creations.rating_aria", "Rate design")
                    },
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
                if (isBulk) {
                    val countTpl = translationStore.t("creator.creations.bulk_selected_count_tpl", "%n% selected")
                    Text(
                        countTpl.replace("%n%", targets.size.toString()),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                    )
                }
                listOf(
                    Triple("no_go", translationStore.t("creator.creations.rating_no_go", "No go"), Icons.Default.Close),
                    Triple("good", translationStore.t("creator.creations.rating_good", "Good"), Icons.Default.ThumbUp),
                    Triple("awesome", translationStore.t("creator.creations.rating_awesome", "Awesome"), Icons.Default.Star),
                ).forEach { (key, label, icon) ->
                    val selected = !isBulk && targets.first().qualityRating == key
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) Color.White.copy(alpha = 0.08f) else Color.Transparent
                            )
                            .clickable {
                                val prevByKey = targets.associate { it.bulkSelectionKey() to it.qualityRating }
                                designs = designs.map { d ->
                                    if (d.bulkSelectionKey() in targetKeys) d.copy(qualityRating = key) else d
                                }
                                ratingTargets = emptyList()
                                scope.launch {
                                    try {
                                        val resp = if (targets.size == 1) {
                                            val one = targets.first()
                                            api.rateDesign(ownerId, one.id, one.jobId, key)
                                        } else {
                                            api.rateDesigns(
                                                ownerId,
                                                targets.map { it.id to it.jobId },
                                                key,
                                            )
                                        }
                                        if (!resp.optBoolean("ok", false)) {
                                            designs = designs.map { d ->
                                                val k = d.bulkSelectionKey()
                                                if (k in prevByKey) d.copy(qualityRating = prevByKey[k]) else d
                                            }
                                        }
                                    } catch (_: Exception) {
                                        designs = designs.map { d ->
                                            val k = d.bulkSelectionKey()
                                            if (k in prevByKey) d.copy(qualityRating = prevByKey[k]) else d
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(icon, contentDescription = null, tint = qualityRatingTint(key))
                        Text(label, color = qualityRatingTint(key), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/** Parallel API fetch + merge (active + inactive); matches web creator-creations-screen.js. */
private suspend fun fetchCreationsDesignsMerged(
    api: com.eazpire.creator.api.CreatorApi,
    ownerId: String,
): List<CreationDesign> = coroutineScope {
    val listRes = async { api.listDesigns(ownerId, 200) }
    val genRes = async { api.listGenerated(ownerId, 100) }
    val jobsRes = async { api.listJobs(ownerId, 50) }

    val savedJobIds = mutableSetOf<String>()
    val savingJobIds = mutableSetOf<String>()
    val savingKvJobs = mutableListOf<JSONObject>()
    (jobsRes.await().optJSONArray("items") ?: JSONArray()).let { arr ->
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val jid = obj.optString("job_id", "").trim()
            if (jid.isBlank()) continue
            val saving = obj.optBoolean("saving", false)
            val saved = obj.optBoolean("saved", false)
            val done = obj.optBoolean("done", false)
            if (saving && !saved) {
                savingJobIds.add(jid)
                if (done) savingKvJobs.add(obj)
            }
        }
    }
    val merged = mutableListOf<CreationDesign>()

    (listRes.await().optJSONArray("items") ?: JSONArray()).let { arr ->
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            parseSavedCreationDesign(obj)?.let { d ->
                d.jobId?.let { savedJobIds.add(it) }
                merged.add(d)
            }
        }
    }

    (genRes.await().optJSONArray("items") ?: JSONArray()).let { arr ->
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val jid = obj.optString("job_id", "").trim()
            if (jid.isNotBlank() && savedJobIds.contains(jid)) continue
            parseGeneratedCreationDesign(obj, savingToLibrary = savingJobIds.contains(jid))?.let { merged.add(it) }
        }
    }

    savingKvJobs.forEach { obj ->
        val jid = obj.optString("job_id", "").trim()
        if (jid.isBlank() || savedJobIds.contains(jid)) return@forEach
        if (merged.any { it.jobId == jid }) return@forEach
        parseKvSavingCreationDesign(obj)?.let { merged.add(it) }
    }

    merged.sortedByDescending { it.createdAt }
}

private suspend fun fetchCreationsProductBadgesMap(
    api: com.eazpire.creator.api.CreatorApi,
    ownerId: String,
    shop: String,
): Map<String, CreationProductBadge> = runCatching {
    parseCreationProductBadges(api.getCreationsProductBadges(ownerId, "EU", shop))
}.getOrDefault(emptyMap())

private suspend fun fetchPublishedCreationsProducts(
    api: com.eazpire.creator.api.CreatorApi,
    ownerId: String,
    shop: String,
): List<CreationProduct> {
    val resp = api.getPublishedProducts(ownerId, shop)
    if (!resp.optBoolean("ok", false)) return emptyList()
    return (resp.optJSONArray("products") ?: JSONArray()).let { arr ->
        fun toImageStr(v: Any?): String? = when (v) {
            is String -> v.takeIf { it.isNotBlank() }
            is JSONObject -> (v.optString("src", "").takeIf { it.isNotBlank() }
                ?: v.optString("url", "").takeIf { it.isNotBlank() }
                ?: v.optString("image_url", "").takeIf { it.isNotBlank() }
                ?: v.optString("preview_url", "").takeIf { it.isNotBlank() })
            else -> null
        }
        fun resolveProductImageUrl(obj: JSONObject): String? {
            val fi = obj.opt("featured_image")
            val featuredStr = when (fi) {
                is JSONObject -> toImageStr(fi) ?: fi.optString("src", "").takeIf { it.isNotBlank() }
                else -> toImageStr(fi)
            }
            return toImageStr(obj.opt("image_url")) ?: featuredStr
                ?: toImageStr(obj.opt("preview_url")) ?: toImageStr(obj.opt("thumbnail_url"))
                ?: toImageStr(obj.opt("main_image")) ?: toImageStr(obj.opt("product_image"))
                ?: obj.optJSONArray("images")?.opt(0)?.let { toImageStr(it) }
                ?: obj.optJSONArray("variants")?.optJSONObject(0)?.opt("image")?.let { toImageStr(it) }
                ?: obj.optJSONArray("variants")?.optJSONObject(0)?.opt("image_url")?.let { toImageStr(it) }
        }
        (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val productKey = obj.optString("product_key", "")
            val storefront = obj.optString("storefront_url").takeIf { it.isNotBlank() }
            val img = normalizeImageUrl(resolveProductImageUrl(obj))
            val title = obj.optString("product_name", "")
                .ifBlank { obj.optString("title", "") }
                .ifBlank { productKey.ifBlank { "Product" } }
            val isSample = obj.optBoolean("is_sample", false) ||
                obj.optString("publish_intent") == "sample_publish"
            val sampleUrl = obj.optString("sample_url", "").takeIf { it.isNotBlank() }
            val designIds = buildList {
                val arr = obj.optJSONArray("design_ids")
                if (arr != null) {
                    for (j in 0 until arr.length()) {
                        arr.opt(j)?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }
            }
            CreationProduct(
                id = obj.optString("shopify_product_id", "")
                    .ifBlank { obj.optString("published_design_id", "") }
                    .ifBlank { obj.optString("product_key", "") + "-product" },
                title = title,
                productName = title,
                productKey = productKey,
                imageUrl = img,
                storefrontUrl = sampleUrl ?: storefront,
                shopifyHandle = obj.optString("shopify_handle").takeIf { it.isNotBlank() },
                publishedAt = (obj.opt("last_published_at") as? Number)?.toLong()
                    ?: (obj.optString("last_published_at").takeIf { it.isNotBlank() }?.let {
                        try {
                            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(it)?.time
                        } catch (_: Exception) {
                            null
                        }
                    }),
                publishedCount = obj.optInt("published_count", 0),
                isSample = isSample,
                publishedDesignId = obj.optString("published_design_id", "").takeIf { it.isNotBlank() },
                designIds = designIds,
            )
        }.sortedByDescending { it.publishedAt ?: 0L }
    }
}

@Composable
private fun CreationsDesignsActivityMeta(
    designsActivityFilter: String,
    onDesignsActivityChange: (String) -> Unit,
    designsCount: Int,
    designsLabel: String,
    activeLabel: String,
    inactiveLabel: String,
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(start = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.28f)),
        ) {
            listOf("active" to activeLabel, "inactive" to inactiveLabel).forEach { (key, label) ->
                val selected = designsActivityFilter == key
                Box(
                    modifier = Modifier
                        .clickable { onDesignsActivityChange(key) }
                        .background(
                            if (selected) EazColors.Orange.copy(alpha = 0.22f) else Color.Transparent
                        )
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.55f),
                    )
                }
            }
        }
        Text(
            text = "$designsCount $designsLabel",
            fontSize = 10.sp,
            lineHeight = 12.sp,
            color = Color.White.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun CreationsDesignsToolbar(
    designsSearch: TextFieldValue,
    onDesignsSearchChange: (TextFieldValue) -> Unit,
    onFilterClick: () -> Unit,
    onUploadClick: () -> Unit,
    designsActivityFilter: String,
    onDesignsActivityChange: (String) -> Unit,
    designsCount: Int,
    translationStore: TranslationStore,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF262930).copy(alpha = 0.68f))
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BasicTextField(
            value = designsSearch,
            onValueChange = onDesignsSearchChange,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.3f))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                .padding(10.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
            decorationBox = { inner ->
                if (designsSearch.text.isEmpty()) {
                    Text(
                        translationStore.t("creator.common.search", "Search…"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
                inner()
            },
        )
        IconButton(
            onClick = onFilterClick,
            modifier = Modifier
                .size(40.dp)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
        ) {
            Icon(Icons.Default.FilterList, contentDescription = null, tint = Color.White)
        }
        IconButton(
            onClick = onUploadClick,
            modifier = Modifier
                .size(40.dp)
                .background(EazColors.Orange.copy(alpha = 0.2f))
                .border(1.dp, EazColors.Orange, RoundedCornerShape(8.dp)),
        ) {
            Icon(Icons.Default.Upload, contentDescription = null, tint = Color.White)
        }
        CreationsDesignsActivityMeta(
            designsActivityFilter = designsActivityFilter,
            onDesignsActivityChange = onDesignsActivityChange,
            designsCount = designsCount,
            designsLabel = translationStore.t("creator.mobile.designs", "Designs"),
            activeLabel = translationStore.t("creator.creations.designs_tab_active", "Active"),
            inactiveLabel = translationStore.t("creator.creations.designs_tab_inactive", "Inactive"),
        )
    }
}

@Composable
private fun CreationDesignListItem(
    design: CreationDesign,
    productBadgeText: String,
    translationStore: TranslationStore,
    bulkSelectable: Boolean,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    onLibraryAction: (() -> Unit)?,
    onRateClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E293B))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (design.publishActive) Modifier else Modifier.clickable(onClick = onClick))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (bulkSelectable) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = onSelectedChange,
                    colors = CheckboxDefaults.colors(checkedColor = EazColors.Orange),
                )
            }
            SubcomposeAsyncImage(
                model = design.imageUrl,
                contentDescription = design.title,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                loading = { GridImageShimmer(Modifier.fillMaxSize()) }
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    design.title.ifBlank { "Design" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 2
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        design.designSource,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    if (productBadgeText.isNotBlank() && !design.publishActive) {
                        Text(
                            productBadgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = EazColors.Orange
                        )
                    }
                }
            }
            if (onRateClick != null && design.canRate()) {
                IconButton(onClick = onRateClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = translationStore.t("creator.creations.rating_aria", "Rate design"),
                        tint = qualityRatingTint(design.qualityRating),
                    )
                }
            }
            if (onLibraryAction != null && !design.publishActive) {
                TextButton(onClick = onLibraryAction) {
                    Text(
                        if (design.effectiveLibraryStatus() == "inactive") {
                            translationStore.t("creator.creations.library_activate_btn", "Activate")
                        } else {
                            translationStore.t("creator.creations.library_deactivate_btn", "Deactivate")
                        },
                        color = EazColors.Orange,
                        fontSize = 12.sp,
                    )
                }
            }
        }
        if (design.publishActive) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color(0xFF0F172A).copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = EazColors.Orange,
                        strokeWidth = 2.dp,
                    )
                    Text(
                        translationStore.t("creator.creations.publishing", "Publishing…"),
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun CreationProductCard(
    product: CreationProduct,
    imageUrls: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF353D4C)),
                contentAlignment = Alignment.Center
            ) {
                if (imageUrls.isNotEmpty()) {
                    ShopStyleProductImages(
                        imageUrls = imageUrls,
                        contentDescription = product.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        cornerRadius = 0.dp,
                        autoRotate = true
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                    }
                }
                if (product.isSample) {
                    Text(
                        "Sample",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                            .background(Color(0xFF0369A1), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                product.title.ifBlank { "Product" },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                modifier = Modifier.padding(8.dp),
                maxLines = 2
            )
        }
    }
}

@Composable
private fun CreationProductListItem(
    product: CreationProduct,
    imageUrls: List<String>,
    translationStore: TranslationStore,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1E293B))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF353D4C))
        ) {
            if (imageUrls.isNotEmpty()) {
                ShopStyleProductImages(
                    imageUrls = imageUrls,
                    contentDescription = product.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    cornerRadius = 8.dp,
                    autoRotate = true
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
                }
            }
        }
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                product.title.ifBlank { "Product" },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 2
            )
            product.publishedAt?.let { ts ->
                Text(
                    java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(java.util.Date(ts)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun GridImageShimmer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "grid-shimmer")
    val shift by transition.animateFloat(
        initialValue = -300f,
        targetValue = 900f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "grid-shimmer-shift"
    )
    Box(
        modifier = modifier
            .background(Color(0xFF2D3748))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.18f),
                            Color.Transparent
                        ),
                        start = Offset(shift - 220f, 0f),
                        end = Offset(shift, 320f)
                    )
                )
        )
    }
}
