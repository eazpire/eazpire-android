package com.eazpire.creator.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.eazpire.creator.ui.modal.EazBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.LocalTranslationStore
import com.eazpire.creator.i18n.TranslationStore
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import com.eazpire.creator.ui.home.catalogAvailabilityFromJson
import com.eazpire.creator.ui.home.catalogPreviewUrlsFromJson
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Catalog card image rotation: matches web shop-create-product.js */
private const val CATALOG_ROTATION_MS = 1500L
private const val CATALOG_CROSSFADE_MS = 450

data class CatalogProduct(
    val productKey: String,
    val title: String,
    val mockUrls: List<String>,
    val catalogAvailability: String = "available",
    val categoryLeaf: String? = null,
    val categoryKey: String? = null,
    val categoryGroup: String? = null,
    val audience: List<String> = emptyList(),
    val visibleDesignTypes: List<String> = emptyList(),
    val productionType: String = "print",
    val providerKey: String? = null
)

private data class CatalogFacetTriSelection(
    val categoryLeaves: Map<String, FacetTriState> = emptyMap(),
    val audiences: Map<String, FacetTriState> = emptyMap(),
    val designTypes: Map<String, FacetTriState> = emptyMap(),
    val production: Map<String, FacetTriState> = emptyMap(),
    val providers: Map<String, FacetTriState> = emptyMap()
)

private fun triIncludes(states: Map<String, FacetTriState>): List<String> =
    states.filterValues { it == 1 }.keys.toList()

private fun triExcludes(states: Map<String, FacetTriState>): List<String> =
    states.filterValues { it == -1 }.keys.toList()

private fun matchesFacetGroup(
    includes: List<String>,
    excludes: List<String>,
    matches: (String) -> Boolean
): Boolean {
    for (ex in excludes) if (matches(ex)) return false
    if (includes.isNotEmpty()) return includes.any(matches)
    return true
}

private fun applyCatalogWithinSearchFilter(
    products: List<CatalogProduct>,
    queryRaw: String,
): List<CatalogProduct> {
    val q = queryRaw.trim().lowercase()
    if (q.isEmpty()) return products
    return products.filter { p ->
        val title = (p.title.ifBlank { p.productKey }).lowercase()
        title.contains(q)
    }
}

private fun filterCatalogProductsTri(
    products: List<CatalogProduct>,
    sel: CatalogFacetTriSelection
): List<CatalogProduct> {
    return products.filter { p ->
        val leaf = (p.categoryLeaf ?: p.categoryKey)?.lowercase()?.trim().orEmpty()
        if (!matchesFacetGroup(
                triIncludes(sel.categoryLeaves),
                triExcludes(sel.categoryLeaves)
            ) { v -> leaf.isNotEmpty() && leaf == v }
        ) return@filter false

        if (!matchesFacetGroup(
                triIncludes(sel.audiences),
                triExcludes(sel.audiences)
            ) { v -> audienceMatchesOne(p.audience, v) }
        ) return@filter false

        if (!matchesFacetGroup(
                triIncludes(sel.designTypes),
                triExcludes(sel.designTypes)
            ) { v -> designTypeMatches(p.visibleDesignTypes, setOf(v)) }
        ) return@filter false

        if (!matchesFacetGroup(
                triIncludes(sel.production),
                triExcludes(sel.production)
            ) { v -> p.productionType.lowercase() == v.lowercase() }
        ) return@filter false

        val pk = p.providerKey?.lowercase()?.trim().orEmpty()
        if (!matchesFacetGroup(
                triIncludes(sel.providers),
                triExcludes(sel.providers)
            ) { v -> pk.isNotEmpty() && pk == v.lowercase() }
        ) return@filter false

        true
    }
}

private fun audienceMatchesOne(audience: List<String>, filterVal: String): Boolean {
    val f = filterVal.trim().lowercase()
    if (f.isEmpty()) return true
    if (audience.any { it.equals("unisex", ignoreCase = true) }) return true
    return audience.any { it.equals(f, ignoreCase = true) }
}

private val CATALOG_LEAVES_PER_GROUP = mapOf(
    "clothing" to setOf("t-shirts", "hoodies", "sweatshirts", "tanks"),
    "accessories" to setOf("accessories", "plush"),
    "home_living" to setOf("mugs", "canvas_posters"),
    "other" to setOf("other")
)

private val CATALOG_GROUP_OPTIONS = listOf(
    "clothing" to "group_clothing",
    "accessories" to "group_accessories",
    "home_living" to "group_home_living",
    "other" to "group_other"
)

private fun designTypeMatches(types: List<String>, selected: Set<String>): Boolean {
    if (selected.isEmpty()) return true
    if (types.isEmpty()) return true
    return selected.any { f -> types.any { it.equals(f, ignoreCase = true) } }
}

private fun leafLabelFor(products: List<CatalogProduct>, leaf: String): String {
    products.firstOrNull {
        (it.categoryLeaf ?: it.categoryKey)?.equals(leaf, ignoreCase = true) == true
    }?.title?.let { return leaf.replace('-', ' ').replaceFirstChar { c -> c.uppercase() } }
    return leaf.replace('-', ' ').replaceFirstChar { c -> c.uppercase() }
}

private fun facetCounts(products: List<CatalogProduct>): Map<String, Map<String, Int>> {
    val leaves = mutableMapOf<String, Int>()
    val audiences = mutableMapOf<String, Int>()
    val designTypes = mutableMapOf<String, Int>()
    val production = mutableMapOf<String, Int>()
    val providers = mutableMapOf<String, Int>()
    products.forEach { p ->
        val leaf = (p.categoryLeaf ?: p.categoryKey)?.lowercase()?.trim().orEmpty()
        if (leaf.isNotEmpty()) leaves[leaf] = (leaves[leaf] ?: 0) + 1
        p.audience.forEach { a ->
            val k = a.lowercase().trim()
            if (k.isNotEmpty()) audiences[k] = (audiences[k] ?: 0) + 1
        }
        p.visibleDesignTypes.forEach { dt ->
            val k = dt.lowercase().trim()
            if (k.isNotEmpty()) designTypes[k] = (designTypes[k] ?: 0) + 1
        }
        val pt = p.productionType.lowercase().trim()
        if (pt.isNotEmpty()) production[pt] = (production[pt] ?: 0) + 1
        val pk = p.providerKey?.lowercase()?.trim().orEmpty()
        if (pk.isNotEmpty()) providers[pk] = (providers[pk] ?: 0) + 1
    }
    return mapOf(
        "category_leaf" to leaves,
        "audience" to audiences,
        "design_type" to designTypes,
        "production" to production,
        "provider" to providers
    )
}

private fun parseJsonStringList(arr: JSONArray?): List<String> {
    if (arr == null) return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            arr.optString(i, "").trim().takeIf { it.isNotEmpty() }?.let { add(it.lowercase()) }
        }
    }
}

sealed interface ShopCreateProductPhase {
    data class Mode(val product: CatalogProduct) : ShopCreateProductPhase
    data class StudioGenerate(val product: CatalogProduct, val catalogProducts: List<CatalogProduct>) : ShopCreateProductPhase
    data class StudioUpload(val product: CatalogProduct, val imageUri: Uri) : ShopCreateProductPhase
    data class StudioCustomize(val product: CatalogProduct, val designUrl: String? = null) : ShopCreateProductPhase
}

private val SHOP_CREATE_SORT_OPTIONS = listOf(
    CollectionSortOption("manual", "Featured"),
    CollectionSortOption("title-ascending", "A–Z"),
    CollectionSortOption("title-descending", "Z–A"),
)

private fun sortCatalogProducts(products: List<CatalogProduct>, sortBy: String): List<CatalogProduct> = when (sortBy) {
    "title-ascending" -> products.sortedBy { it.title.lowercase() }
    "title-descending" -> products.sortedByDescending { it.title.lowercase() }
    else -> products
}

private data class PublicCatalogDesign(
    val id: String,
    val previewUrl: String,
    val designUrl: String
)

/**
 * Inline Create catalog PLP — Products | Designs tabs; design pick then product → studio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopCreateCollectionScreen(
    api: CreatorApi,
    region: String,
    modifier: Modifier = Modifier,
    ownerId: String? = null,
    onProductClick: (CatalogProduct, designUrl: String?) -> Unit,
    onProductsLoaded: (List<CatalogProduct>) -> Unit = {}
) {
    val store = LocalTranslationStore.current
    val t = store?.let { { k: String, d: String -> it.t(k, d) } } ?: { _: String, d: String -> d }

    var catalogTab by remember { mutableStateOf("products") }
    var pendingDesignUrl by remember { mutableStateOf<String?>(null) }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var products by remember { mutableStateOf<List<CatalogProduct>>(emptyList()) }

    var designs by remember { mutableStateOf<List<PublicCatalogDesign>>(emptyList()) }
    var designCursor by remember { mutableStateOf<String?>(null) }
    var designsLoading by remember { mutableStateOf(false) }
    var designsLoaded by remember { mutableStateOf(false) }
    var designSearch by remember { mutableStateOf("") }
    var designFilterRatio by remember { mutableStateOf<String?>(null) }
    var designFilterContent by remember { mutableStateOf<String?>(null) }
    var designFilterType by remember { mutableStateOf<String?>(null) }
    var designFilterDrawer by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(region) {
        loading = true
        error = null
        try {
            val data = withContext(Dispatchers.IO) {
                api.getShopCreateProductCatalog(region)
            }
            if (!data.optBoolean("ok", false)) {
                error = data.optString("error", "catalog_error")
                products = emptyList()
            } else {
                val arr = data.optJSONArray("products") ?: JSONArray()
                val list = mutableListOf<CatalogProduct>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val pk = o.optString("product_key", "").trim()
                    if (pk.isEmpty()) continue
                    list.add(
                        CatalogProduct(
                            productKey = pk,
                            title = o.optString("title", pk).ifBlank { pk },
                            mockUrls = catalogPreviewUrlsFromJson(o),
                            catalogAvailability = catalogAvailabilityFromJson(o),
                            categoryLeaf = o.optString("category_leaf", "").trim().ifBlank { null },
                            categoryKey = o.optString("category_key", "").trim().ifBlank { null },
                            categoryGroup = o.optString("category_group", "").trim().ifBlank { null },
                            audience = parseJsonStringList(o.optJSONArray("audience")),
                            visibleDesignTypes = parseJsonStringList(o.optJSONArray("visible_design_types")),
                            productionType = o.optString("production_type", "print").ifBlank { "print" },
                            providerKey = o.optString("provider_key", "").trim().ifBlank { null }
                        )
                    )
                }
                products = list
            }
        } catch (e: Exception) {
            error = e.message ?: "error"
            products = emptyList()
        } finally {
            loading = false
            onProductsLoaded(products)
        }
    }

    suspend fun loadDesigns(reset: Boolean) {
        if (designsLoading) return
        if (!reset && designCursor == null && designsLoaded) return
        designsLoading = true
        try {
            val filters = buildMap {
                designFilterRatio?.let { put("filter_ratio", it) }
                designFilterContent?.let { put("filter_content_type", it) }
                designFilterType?.let { put("filter_design_type", it) }
            }
            val data = withContext(Dispatchers.IO) {
                api.listPublic(
                    limit = 48,
                    search = designSearch.takeIf { it.isNotBlank() },
                    cursor = if (reset) null else designCursor,
                    filterParams = filters,
                    activePublicOnly = true,
                    excludeOwnerId = ownerId
                )
            }
            if (!data.optBoolean("ok", false)) {
                if (reset) designs = emptyList()
            } else {
                val arr = data.optJSONArray("items") ?: JSONArray()
                val page = mutableListOf<PublicCatalogDesign>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optString("id", "").trim()
                    val preview = o.optString("preview_url", "").trim()
                    val original = o.optString("original_url", "").trim()
                    val url = original.ifBlank { preview }
                    if (id.isEmpty() || url.isEmpty()) continue
                    page.add(PublicCatalogDesign(id = id, previewUrl = preview.ifBlank { url }, designUrl = url))
                }
                designs = if (reset) page else designs + page
                designCursor = data.optString("next_cursor", "").trim().ifBlank { null }
            }
            designsLoaded = true
        } catch (_: Exception) {
            if (reset) designs = emptyList()
            designsLoaded = true
        } finally {
            designsLoading = false
        }
    }

    LaunchedEffect(catalogTab, designSearch, designFilterRatio, designFilterContent, designFilterType) {
        if (catalogTab != "designs") return@LaunchedEffect
        delay(180)
        loadDesigns(reset = true)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "products" to t("creator.shop_create_product.catalog_tab_products", "Products"),
                "designs" to t("creator.shop_create_product.catalog_tab_designs", "Designs")
            ).forEach { (key, label) ->
                val active = catalogTab == key
                Text(
                    text = label,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) EazColors.Orange.copy(alpha = 0.15f) else Color(0xFFF3F3F3))
                        .clickable { catalogTab = key }
                        .padding(vertical = 10.dp),
                    textAlign = TextAlign.Center,
                    color = if (active) EazColors.Orange else Color(0xFF444444),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        if (!pendingDesignUrl.isNullOrBlank() && catalogTab == "products") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(EazColors.Orange.copy(alpha = 0.12f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    t("creator.shop_create_product.catalog_select_product_for_design", "Select a product for this design"),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    t("creator.shop_create_product.catalog_clear_pending_design", "Clear"),
                    modifier = Modifier.clickable { pendingDesignUrl = null },
                    color = EazColors.Orange,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(Modifier = Modifier.height(6.dp))
        }

        when (catalogTab) {
            "designs" -> {
                ShopCreateDesignsGrid(
                    loading = designsLoading && designs.isEmpty(),
                    designs = designs,
                    hasMore = designCursor != null,
                    search = designSearch,
                    t = t,
                    onSearchChange = { designSearch = it },
                    onOpenFilters = { designFilterDrawer = true },
                    onLoadMore = {
                        if (!designsLoading) scope.launch { loadDesigns(false) }
                    },
                    onDesignClick = { d ->
                        pendingDesignUrl = d.designUrl
                        catalogTab = "products"
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> ShopCreateCatalogGrid(
                loading = loading,
                error = error,
                products = products,
                t = t,
                onProductClick = { p ->
                    val url = pendingDesignUrl
                    pendingDesignUrl = null
                    onProductClick(p, url)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    if (designFilterDrawer) {
        ShopCreateDesignFilterSheet(
            ratio = designFilterRatio,
            contentType = designFilterContent,
            designType = designFilterType,
            t = t,
            onDismiss = { designFilterDrawer = false },
            onApply = { r, c, dt ->
                designFilterRatio = r
                designFilterContent = c
                designFilterType = dt
                designFilterDrawer = false
            }
        )
    }
}

/**
 * Studio overlays after a product is chosen from [ShopCreateCollectionScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopCreateProductFlow(
    phase: ShopCreateProductPhase?,
    catalogProducts: List<CatalogProduct>,
    onCloseStudio: () -> Unit,
    onPhaseChange: (ShopCreateProductPhase?) -> Unit,
    api: CreatorApi,
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    translation: (String, String) -> String,
    onRequireLogin: () -> Unit = {}
) {
    val current = phase ?: return
    var pendingUploadProduct by remember { mutableStateOf<CatalogProduct?>(null) }
    val uploadPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val p = pendingUploadProduct
        pendingUploadProduct = null
        if (p != null && uri != null) {
            onPhaseChange(ShopCreateProductPhase.StudioUpload(p, uri))
        } else if (p != null) {
            onPhaseChange(ShopCreateProductPhase.Mode(p))
        }
    }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() }

    when (current) {
        is ShopCreateProductPhase.Mode -> {
            val p = current.product
            ShopModeBottomSheet(
                productTitle = p.title,
                translation = translation,
                onDismissRequest = onCloseStudio,
                onGenerate = {
                    onPhaseChange(ShopCreateProductPhase.StudioGenerate(p, catalogProducts))
                },
                onUpload = {
                    pendingUploadProduct = p
                    uploadPicker.launch("image/*")
                },
                onCustomize = {
                    onPhaseChange(ShopCreateProductPhase.StudioCustomize(p))
                }
            )
        }
        is ShopCreateProductPhase.StudioGenerate -> {
            val p = current.product
            ShopDesignStudioGenerateSheet(
                product = p,
                catalogProducts = current.catalogProducts,
                api = api,
                ownerId = ownerId,
                translationStore = translationStore,
                translation = translation,
                onDismiss = { onPhaseChange(ShopCreateProductPhase.Mode(p)) },
                onRequireLogin = onRequireLogin
            )
        }
        is ShopCreateProductPhase.StudioCustomize -> {
            val p = current.product
            ShopPrintifyDesignStudioScreen(
                product = p,
                initialDesignUrl = current.designUrl,
                api = api,
                ownerId = ownerId,
                translationStore = translationStore,
                translation = translation,
                onDismiss = onCloseStudio,
                onRequireLogin = onRequireLogin
            )
        }
        is ShopCreateProductPhase.StudioUpload -> {
            val p = current.product
            ShopUploadNativeSheet(
                product = p,
                imageUri = current.imageUri,
                api = api,
                ownerId = ownerId,
                translationStore = translationStore,
                translation = translation,
                onDismiss = { onPhaseChange(ShopCreateProductPhase.Mode(p)) },
                onRequireLogin = onRequireLogin
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShopModeBottomSheet(
    productTitle: String,
    translation: (String, String) -> String,
    onDismissRequest: () -> Unit,
    onGenerate: () -> Unit,
    onUpload: () -> Unit,
    onCustomize: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    BackHandler(onBack = onDismissRequest)
    EazBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color.White,
        maxHeightFraction = 0.92f,
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                )
            }
        }
    ) {
        ShopLightSheetTheme {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                ModeSheetHeader(
                    translation = translation,
                    onBack = onDismissRequest
                )
                ModeStep(
                    productTitle = productTitle,
                    translation = translation,
                    onGenerate = onGenerate,
                    onUpload = onUpload,
                    onCustomize = onCustomize
                )
            }
        }
    }
}

@Composable
private fun CatalogSheetHeader(
    translation: (String, String) -> String,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = translation("creator.common.close", "Close")
            )
        }
        Text(
            text = translation("creator.shop_create_product.modal1_title", "Choose a catalog product"),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.width(48.dp))
    }
}

@Composable
private fun ModeSheetHeader(
    translation: (String, String) -> String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = translation("creator.common.back", "Back")
            )
        }
        Text(
            text = translation("creator.shop_create_product.modal2_title", "How do you want to create?"),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.width(48.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShopCreateDesignsGrid(
    loading: Boolean,
    designs: List<PublicCatalogDesign>,
    hasMore: Boolean,
    search: String,
    t: (String, String) -> String,
    onSearchChange: (String) -> Unit,
    onOpenFilters: () -> Unit,
    onLoadMore: () -> Unit,
    onDesignClick: (PublicCatalogDesign) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = search,
                onValueChange = onSearchChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = {
                    Text(t("creator.shop_create_product.catalog_search_placeholder", "Search by title"))
                }
            )
            IconButton(onClick = onOpenFilters) {
                Icon(Icons.Filled.FilterList, contentDescription = t("eaz.collection.filter", "Filter"))
            }
        }
        when {
            loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = EazColors.Orange)
            }
            designs.isEmpty() -> Text(
                t("creator.shop_create_product.catalog_designs_empty", "No public designs found."),
                modifier = Modifier.padding(16.dp)
            )
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(designs.chunked(2), key = { row -> row.joinToString("-") { it.id } }) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { d ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFFF0F0F0))
                                    .clickable { onDesignClick(d) }
                            ) {
                                AsyncImage(
                                    model = d.previewUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
                if (hasMore) {
                    item(key = "designs_load_more") {
                        LaunchedEffect(Unit) { onLoadMore() }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = EazColors.Orange,
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShopCreateDesignFilterSheet(
    ratio: String?,
    contentType: String?,
    designType: String?,
    t: (String, String) -> String,
    onDismiss: () -> Unit,
    onApply: (ratio: String?, contentType: String?, designType: String?) -> Unit
) {
    var localRatio by remember { mutableStateOf(ratio) }
    var localContent by remember { mutableStateOf(contentType) }
    var localType by remember { mutableStateOf(designType) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    EazBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        maxHeightFraction = 0.85f
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(t("creator.filter_modal.ratio", "Ratio"), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("portrait", "landscape", "square").forEach { v ->
                    val on = localRatio == v
                    Text(
                        v.replaceFirstChar { it.uppercase() },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (on) EazColors.Orange else Color(0xFFEEEEEE))
                            .clickable { localRatio = if (on) null else v }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        color = if (on) Color.White else Color.Black
                    )
                }
            }
            Text(t("creator.filter_modal.content_type", "Content Type"), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("design_text", "design_only", "text_only").forEach { v ->
                    val on = localContent == v
                    Text(
                        v,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (on) EazColors.Orange else Color(0xFFEEEEEE))
                            .clickable { localContent = if (on) null else v }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        color = if (on) Color.White else Color.Black
                    )
                }
            }
            Text(t("creator.filter_modal.design_type", "Design Type"), style = MaterialTheme.typography.titleSmall)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("classic", "pattern", "all_over", "full_surface", "panorama").forEach { v ->
                    val on = localType == v
                    Text(
                        v,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (on) EazColors.Orange else Color(0xFFEEEEEE))
                            .clickable { localType = if (on) null else v }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        color = if (on) Color.White else Color.Black
                    )
                }
            }
            Text(
                t("creator.shop_create_product.catalog_reset_filters", "Reset filters"),
                modifier = Modifier
                    .clickable {
                        localRatio = null
                        localContent = null
                        localType = null
                    }
                    .padding(vertical = 8.dp),
                color = EazColors.Orange
            )
            Text(
                t("creator.common.apply", "Apply"),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(EazColors.Orange)
                    .clickable { onApply(localRatio, localContent, localType) }
                    .padding(vertical = 12.dp),
                textAlign = TextAlign.Center,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShopCreateCatalogGrid(
    loading: Boolean,
    error: String?,
    products: List<CatalogProduct>,
    t: (String, String) -> String,
    onProductClick: (CatalogProduct) -> Unit,
    modifier: Modifier = Modifier
) {
    var facetSel by remember { mutableStateOf(CatalogFacetTriSelection()) }
    var withinSearchQuery by remember { mutableStateOf("") }
    var filterDrawerVisible by remember { mutableStateOf(false) }
    var sortBy by remember { mutableStateOf("manual") }
    var sortSheetVisible by remember { mutableStateOf(false) }
    val filtered = remember(products, facetSel, withinSearchQuery) {
        applyCatalogWithinSearchFilter(filterCatalogProductsTri(products, facetSel), withinSearchQuery)
    }
    val sorted = remember(filtered, sortBy) { sortCatalogProducts(filtered, sortBy) }
    val availableProducts = remember(sorted) { sorted.filter { it.catalogAvailability != "coming_soon" } }
    val comingSoonProducts = remember(sorted) { sorted.filter { it.catalogAvailability == "coming_soon" } }
    var availableExpanded by remember { mutableStateOf(true) }
    var comingSoonExpanded by remember(availableProducts.size, comingSoonProducts.size) {
        mutableStateOf(availableProducts.isEmpty() && comingSoonProducts.isNotEmpty())
    }
    val currentSortLabel = SHOP_CREATE_SORT_OPTIONS.find { it.value == sortBy }?.label?.let {
        t("collection.sort_$sortBy", it)
    } ?: t("collection.sort_by", "Sort by")

    Column(modifier = modifier.fillMaxSize()) {
        when {
            loading && products.isEmpty() -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = EazColors.Orange)
            }
            error != null && products.isEmpty() -> Text(
                error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
            sorted.isEmpty() -> Text(
                t("creator.shop_create_product.empty", "No products"),
                modifier = Modifier.padding(16.dp)
            )
            else -> {
                CollectionResultsBar(
                    filteredCount = sorted.size,
                    totalCount = products.size,
                    sortBy = sortBy,
                    sortLabel = currentSortLabel,
                    t = t,
                    onFilterClick = { filterDrawerVisible = true },
                    onSortClick = { sortSheetVisible = true }
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    if (availableProducts.isNotEmpty()) {
                        item(key = "section_available_header") {
                            ShopCreateAvailabilityHeader(
                                title = t(
                                    "creator.shop_create_product.catalog_section_available",
                                    "Available",
                                ),
                                count = availableProducts.size,
                                expanded = availableExpanded,
                                onToggle = { availableExpanded = !availableExpanded },
                            )
                        }
                        if (availableExpanded) {
                            items(
                                availableProducts.chunked(2),
                                key = { row -> row.joinToString("-") { it.productKey } },
                            ) { row ->
                                ShopCreateCatalogProductRow(
                                    row = row,
                                    t = t,
                                    onProductClick = onProductClick,
                                )
                            }
                        }
                    }
                    if (comingSoonProducts.isNotEmpty()) {
                        item(key = "section_coming_soon_header") {
                            ShopCreateAvailabilityHeader(
                                title = t(
                                    "creator.shop_create_product.catalog_section_coming_soon",
                                    "Coming soon",
                                ),
                                count = comingSoonProducts.size,
                                expanded = comingSoonExpanded,
                                onToggle = { comingSoonExpanded = !comingSoonExpanded },
                            )
                        }
                        if (comingSoonExpanded) {
                            items(
                                comingSoonProducts.chunked(2),
                                key = { row -> row.joinToString("-") { it.productKey } },
                            ) { row ->
                                ShopCreateCatalogProductRow(
                                    row = row,
                                    t = t,
                                    onProductClick = onProductClick,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (filterDrawerVisible) {
        ShopCreateFilterDrawer(
            products = products,
            facetSel = facetSel,
            withinSearchQuery = withinSearchQuery,
            onWithinSearchChange = { withinSearchQuery = it },
            onFacetChange = { facetSel = it },
            t = t,
            onDismiss = { filterDrawerVisible = false },
        )
    }

    CollectionSortBottomSheet(
        visible = sortSheetVisible,
        sortBy = sortBy,
        sortOptions = SHOP_CREATE_SORT_OPTIONS,
        t = t,
        onDismiss = { sortSheetVisible = false },
        onSortSelected = { sortBy = it }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShopCreateFilterDrawer(
    products: List<CatalogProduct>,
    facetSel: CatalogFacetTriSelection,
    withinSearchQuery: String,
    onWithinSearchChange: (String) -> Unit,
    onFacetChange: (CatalogFacetTriSelection) -> Unit,
    t: (String, String) -> String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val counts = remember(products) { facetCounts(products) }

    fun dismissAnimated() {
        scope.launch {
            sheetState.hide()
        }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    EazBottomSheet(
        onDismissRequest = { dismissAnimated() },
        sheetState = sheetState,
        maxHeightFraction = 0.9f,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 450.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = { dismissAnimated() }) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = t("collection.filter", "Filters"),
                        tint = EazColors.Orange,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                TextField(
                    value = withinSearchQuery,
                    onValueChange = onWithinSearchChange,
                    placeholder = {
                        Text(
                            t(
                                "creator.shop_create_product.catalog_search_placeholder",
                                "Search products…",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .heightIn(min = 44.dp),
                    singleLine = true,
                )
                Text(
                    t("creator.shop_create_product.catalog_filter_category", "Category"),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                CATALOG_GROUP_OPTIONS.forEach { (groupKey, labelKey) ->
                    val leaves = CATALOG_LEAVES_PER_GROUP[groupKey].orEmpty()
                    val groupBody = leaves.mapNotNull { leaf ->
                        val n = counts["category_leaf"]?.get(leaf) ?: 0
                        if (n <= 0) return@mapNotNull null
                        leaf to n
                    }
                    if (groupBody.isEmpty()) return@forEach
                    Text(
                        t("creator.shop_create_product.$labelKey", groupKey),
                        style = MaterialTheme.typography.labelMedium,
                        color = EazColors.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                    groupBody.forEach { (leaf, n) ->
                        FacetTriSwitchRow(
                            label = leafLabelFor(products, leaf),
                            count = n,
                            state = facetSel.categoryLeaves[leaf] ?: 0,
                            onStateChange = { st ->
                                onFacetChange(facetSel.copy(categoryLeaves = facetSel.categoryLeaves + (leaf to st)))
                            }
                        )
                    }
                }
                CatalogTriFacetGroup(
                    title = t("creator.shop_create_product.catalog_filter_audience", "Audience"),
                    options = listOf(
                        "women" to t("creator.shop_create_product.audience_women", "Women"),
                        "men" to t("creator.shop_create_product.audience_men", "Men"),
                        "kids" to t("creator.shop_create_product.audience_kids", "Kids"),
                        "toddler" to t("creator.shop_create_product.audience_toddler", "Toddler")
                    ),
                    counts = counts["audience"].orEmpty(),
                    states = facetSel.audiences,
                    onStateChange = { v, st -> onFacetChange(facetSel.copy(audiences = facetSel.audiences + (v to st))) }
                )
                CatalogTriFacetGroup(
                    title = t("creator.shop_create_product.catalog_filter_design_type", "Design type"),
                    options = listOf(
                        "classic" to t("creator.shop_create_product.dt_classic", "Classic"),
                        "pattern" to t("creator.shop_create_product.dt_pattern", "Pattern"),
                        "all-over" to t("creator.shop_create_product.dt_all_over", "All-Over"),
                        "full-coverage" to t("creator.shop_create_product.dt_full_coverage", "Full-Coverage"),
                        "panorama" to t("creator.shop_create_product.dt_panorama", "Panorama")
                    ),
                    counts = counts["design_type"].orEmpty(),
                    states = facetSel.designTypes,
                    onStateChange = { v, st -> onFacetChange(facetSel.copy(designTypes = facetSel.designTypes + (v to st))) }
                )
                CatalogTriFacetGroup(
                    title = t("creator.shop_create_product.catalog_filter_production", "Production"),
                    options = listOf(
                        "print" to t("creator.shop_create_product.production_print", "Print"),
                        "embroidery" to t("creator.shop_create_product.production_embroidery", "Embroidery"),
                        "3d_print" to t("creator.shop_create_product.production_3d", "3D print")
                    ),
                    counts = counts["production"].orEmpty(),
                    states = facetSel.production,
                    onStateChange = { v, st -> onFacetChange(facetSel.copy(production = facetSel.production + (v to st))) }
                )
                val providerCounts = counts["provider"].orEmpty()
                if (providerCounts.isNotEmpty()) {
                    CatalogTriFacetGroup(
                        title = t("creator.shop_create_product.catalog_filter_provider", "Provider"),
                        options = providerCounts.keys.sorted().map { it to it.replaceFirstChar { c -> c.uppercase() } },
                        counts = providerCounts,
                        states = facetSel.providers,
                        onStateChange = { v, st -> onFacetChange(facetSel.copy(providers = facetSel.providers + (v to st))) }
                    )
                }
                Spacer(Modifier.height(24.dp))
            }

            // Footer (fixed) — reset filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .clickable {
                            onFacetChange(CatalogFacetTriSelection())
                            onWithinSearchChange("")
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        t("creator.shop_create_product.catalog_reset_filters", "Reset filters"),
                        style = MaterialTheme.typography.labelLarge,
                        color = EazColors.Orange
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(EazColors.Orange)
                        .clickable { dismissAnimated() }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        t("collection.apply", "Apply"),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogTriFacetGroup(
    title: String,
    options: List<Pair<String, String>>,
    counts: Map<String, Int>,
    states: Map<String, FacetTriState>,
    onStateChange: (String, FacetTriState) -> Unit
) {
    val visible = options.filter { (value, _) -> (counts[value] ?: 0) > 0 }
    if (visible.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp))
    visible.forEach { (value, label) ->
        FacetTriSwitchRow(
            label = label,
            count = counts[value],
            state = states[value] ?: 0,
            onStateChange = { onStateChange(value, it) }
        )
    }
}

@Composable
private fun ModeStep(
    productTitle: String,
    translation: (String, String) -> String,
    onGenerate: () -> Unit,
    onUpload: () -> Unit,
    onCustomize: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = productTitle,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShopSheetPrimaryButton(
                onClick = onGenerate,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
            ) {
                Text(
                    translation("creator.shop_create_product.generate", "Generate"),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Text(
                text = translation(
                    "creator.shop_create_product.generate_desc",
                    "Create an all-new design with AI from scratch."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShopSheetPrimaryButton(
                onClick = onUpload,
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                Text(
                    translation("creator.shop_create_product.upload", "Upload"),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Text(
                text = translation(
                    "creator.shop_create_product.upload_desc",
                    "Upload a finished design or image as it will appear on the product."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.92f)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShopSheetPrimaryButton(
                onClick = onCustomize,
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                Text(
                    translation("design_studio.shop.customize_product", "Customize on product"),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Text(
                text = translation(
                    "creator.shop_create_product.customize_desc",
                    "Place and adjust your design on the product mockup."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.92f)
            )
        }
    }
}

@Composable
private fun ShopCreateAvailabilityHeader(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "$title ($count)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun ShopCreateCatalogProductRow(
    row: List<CatalogProduct>,
    t: (String, String) -> String,
    onProductClick: (CatalogProduct) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        row.forEach { product ->
            CatalogProductCard(
                product = product,
                t = t,
                onClick = { onProductClick(product) },
                modifier = Modifier.weight(1f),
            )
        }
        if (row.size == 1) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CatalogProductCard(
    product: CatalogProduct,
    t: (String, String) -> String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previewUrl = product.mockUrls.firstOrNull().orEmpty()
    val isComingSoon = product.catalogAvailability == "coming_soon"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isComingSoon) Modifier
                else Modifier.clickable(onClick = onClick),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 5f)
                .clip(RoundedCornerShape(8.dp))
                .then(
                    if (isComingSoon) {
                        Modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (previewUrl.isNotEmpty()) {
                AsyncImage(
                    model = previewUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (isComingSoon) Modifier else Modifier),
                    alpha = if (isComingSoon) 0.72f else 1f,
                )
            }
            if (isComingSoon) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF6B7280))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = t(
                            "creator.shop_create_product.catalog_badge_coming_soon",
                            "Coming soon",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
        }
        Text(
            product.title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (isComingSoon) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
