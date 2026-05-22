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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
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
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Catalog card image rotation: matches web shop-create-product.js */
private const val CATALOG_ROTATION_MS = 1500L
private const val CATALOG_CROSSFADE_MS = 450

internal data class CatalogProduct(
    val productKey: String,
    val title: String,
    val mockUrls: List<String>,
    val categoryLeaf: String? = null,
    val categoryKey: String? = null,
    val audience: List<String> = emptyList(),
    val visibleDesignTypes: List<String> = emptyList(),
    val productionType: String = "print",
    val providerKey: String? = null
)

private data class CatalogFacetSelection(
    val groups: Set<String> = emptySet(),
    val audiences: Set<String> = emptySet(),
    val designTypes: Set<String> = emptySet(),
    val categoryLeaves: Set<String> = emptySet(),
    val production: Set<String> = emptySet()
)

private val CATALOG_LEAVES_PER_GROUP = mapOf(
    "clothing" to setOf("t-shirts", "hoodies", "sweatshirts", "tanks"),
    "accessories" to setOf("accessories", "plush"),
    "home_living" to setOf("mugs", "canvas_posters"),
    "other" to setOf("other")
)

private fun catalogProductGroup(p: CatalogProduct): String {
    val leaf = (p.categoryLeaf ?: p.categoryKey)?.lowercase()?.trim().orEmpty()
    if (leaf.isEmpty()) return "other"
    CATALOG_LEAVES_PER_GROUP.forEach { (group, leaves) ->
        if (leaf in leaves) return group
    }
    return "other"
}

private fun audienceMatchesAny(audience: List<String>, selected: Set<String>): Boolean {
    if (selected.isEmpty()) return true
    if (audience.isEmpty()) return true
    if (audience.any { it.equals("unisex", ignoreCase = true) }) return true
    return selected.any { f -> audience.any { it.equals(f, ignoreCase = true) } }
}

private fun designTypeMatches(types: List<String>, selected: Set<String>): Boolean {
    if (selected.isEmpty()) return true
    if (types.isEmpty()) return true
    return selected.any { f -> types.any { it.equals(f, ignoreCase = true) } }
}

private fun filterCatalogProducts(
    products: List<CatalogProduct>,
    sel: CatalogFacetSelection
): List<CatalogProduct> {
    return products.filter { p ->
        if (sel.groups.isNotEmpty() && catalogProductGroup(p) !in sel.groups) return@filter false
        if (sel.categoryLeaves.isNotEmpty()) {
            val lk = (p.categoryLeaf ?: p.categoryKey)?.lowercase()?.trim().orEmpty()
            if (lk.isNotEmpty() && lk !in sel.categoryLeaves) return@filter false
        }
        if (sel.production.isNotEmpty() && p.productionType.lowercase() !in sel.production) return@filter false
        if (!audienceMatchesAny(p.audience, sel.audiences)) return@filter false
        if (!designTypeMatches(p.visibleDesignTypes, sel.designTypes)) return@filter false
        true
    }
}

private fun parseJsonStringList(arr: JSONArray?): List<String> {
    if (arr == null) return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            arr.optString(i, "").trim().takeIf { it.isNotEmpty() }?.let { add(it.lowercase()) }
        }
    }
}

private sealed interface ShopCreateProductPhase {
    data object Closed : ShopCreateProductPhase
    data object Catalog : ShopCreateProductPhase
    data class Mode(val product: CatalogProduct) : ShopCreateProductPhase
    data class StudioGenerate(val product: CatalogProduct, val catalogProducts: List<CatalogProduct>) : ShopCreateProductPhase
    data class StudioUpload(val product: CatalogProduct, val imageUri: Uri) : ShopCreateProductPhase
    data class StudioCustomize(val product: CatalogProduct, val designUrl: String? = null) : ShopCreateProductPhase
}

/**
 * Shop Create Product: catalog → mode → native generate/upload (customer-design API, no WebView).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopCreateProductFlow(
    visible: Boolean,
    onDismiss: () -> Unit,
    api: CreatorApi,
    tokenStore: SecureTokenStore,
    region: String,
    translationStore: TranslationStore,
    translation: (String, String) -> String,
    onRequireLogin: () -> Unit = {}
) {
    var phase by remember(visible) {
        mutableStateOf(
            if (visible) ShopCreateProductPhase.Catalog else ShopCreateProductPhase.Closed
        )
    }
    var pendingUploadProduct by remember { mutableStateOf<CatalogProduct?>(null) }
    val uploadPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val p = pendingUploadProduct
        pendingUploadProduct = null
        if (p != null && uri != null) {
            phase = ShopCreateProductPhase.StudioUpload(p, uri)
        } else if (p != null) {
            phase = ShopCreateProductPhase.Mode(p)
        }
    }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var products by remember { mutableStateOf<List<CatalogProduct>>(emptyList()) }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() }

    LaunchedEffect(visible, region) {
        if (!visible) return@LaunchedEffect
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
        }
    }

    if (!visible) return

    when (val current = phase) {
        ShopCreateProductPhase.Closed -> {}
        ShopCreateProductPhase.Catalog -> {
            ShopCreateCatalogScreen(
                loading = loading,
                error = error,
                products = products,
                translation = translation,
                translationStore = translationStore,
                onDismiss = onDismiss,
                onProductClick = { p -> phase = ShopCreateProductPhase.StudioCustomize(p) }
            )
        }
        is ShopCreateProductPhase.Mode -> {
            val p = current.product
            ShopModeBottomSheet(
                productTitle = p.title,
                translation = translation,
                onDismissRequest = { phase = ShopCreateProductPhase.Catalog },
                onGenerate = {
                    phase = ShopCreateProductPhase.StudioGenerate(p, products)
                },
                onUpload = {
                    pendingUploadProduct = p
                    uploadPicker.launch("image/*")
                },
                onCustomize = {
                    phase = ShopCreateProductPhase.StudioCustomize(p)
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
                onDismiss = { phase = ShopCreateProductPhase.Mode(p) },
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
                onDismiss = { phase = ShopCreateProductPhase.Catalog },
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
                onDismiss = { phase = ShopCreateProductPhase.Mode(p) },
                onRequireLogin = onRequireLogin
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShopCreateCatalogScreen(
    loading: Boolean,
    error: String?,
    products: List<CatalogProduct>,
    translation: (String, String) -> String,
    translationStore: TranslationStore,
    onDismiss: () -> Unit,
    onProductClick: (CatalogProduct) -> Unit
) {
    BackHandler(onBack = onDismiss)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ShopLightSheetTheme {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = translation("creator.common.close", "Close"))
                    }
                    Text(
                        text = translationStore.t(
                            "creator.shop_create_product.catalog_page_title",
                            "Create a product"
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.width(48.dp))
                }
                Text(
                    text = translationStore.t(
                        "creator.shop_create_product.catalog_page_subtitle",
                        "Pick a product to customize in the design studio."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                CatalogStep(
                    loading = loading,
                    error = error,
                    products = products,
                    translation = translation,
                    translationStore = translationStore,
                    onProductClick = onProductClick,
                    modifier = Modifier.weight(1f)
                )
            }
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
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color.White,
        modifier = Modifier.fillMaxHeight(0.92f),
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
                    .navigationBarsPadding()
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
private fun CatalogStep(
    loading: Boolean,
    error: String?,
    products: List<CatalogProduct>,
    translation: (String, String) -> String,
    translationStore: TranslationStore,
    onProductClick: (CatalogProduct) -> Unit,
    modifier: Modifier = Modifier
) {
    var facetSel by remember { mutableStateOf(CatalogFacetSelection()) }
    var filterSheetOpen by remember { mutableStateOf(false) }
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val filtered = remember(products, facetSel) { filterCatalogProducts(products, facetSel) }
    val groupOptions = listOf(
        "clothing" to translationStore.t("creator.shop_create_product.group_clothing", "Clothing"),
        "accessories" to translationStore.t("creator.shop_create_product.group_accessories", "Accessories"),
        "home_living" to translationStore.t("creator.shop_create_product.group_home_living", "Home & Living"),
        "other" to translationStore.t("creator.shop_create_product.group_other", "Other")
    )
    val audienceOptions = listOf(
        "women" to translationStore.t("creator.shop_create_product.audience_women", "Women"),
        "men" to translationStore.t("creator.shop_create_product.audience_men", "Men"),
        "kids" to translationStore.t("creator.shop_create_product.audience_kids", "Kids"),
        "toddler" to translationStore.t("creator.shop_create_product.audience_toddler", "Toddler")
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            translation("creator.shop_create_product.private_hint", "Shop designs stay private."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${filtered.size} / ${products.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ShopSheetPrimaryButton(onClick = { filterSheetOpen = true }) {
                Text(translationStore.t("creator.product_filters.filters", "Filters"))
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            groupOptions.forEach { (value, label) ->
                val selected = value in facetSel.groups
                FilterChip(
                    selected = selected,
                    onClick = {
                        facetSel = facetSel.copy(
                            groups = if (selected) facetSel.groups - value else facetSel.groups + value
                        )
                    },
                    label = { Text(label, maxLines = 1) }
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            audienceOptions.forEach { (value, label) ->
                val selected = value in facetSel.audiences
                FilterChip(
                    selected = selected,
                    onClick = {
                        facetSel = facetSel.copy(
                            audiences = if (selected) facetSel.audiences - value else facetSel.audiences + value
                        )
                    },
                    label = { Text(label, maxLines = 1) }
                )
            }
        }
        when {
            loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            error != null -> Text(error, color = MaterialTheme.colorScheme.error)
            filtered.isEmpty() -> Text(translation("creator.shop_create_product.empty", "No products"))
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(4.dp),
                userScrollEnabled = true
            ) {
                items(filtered, key = { it.productKey }) { p ->
                    CatalogProductCard(product = p) { onProductClick(p) }
                }
            }
        }
    }

    if (filterSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { filterSheetOpen = false },
            sheetState = filterSheetState,
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    translationStore.t("creator.product_filters.filters", "Filters"),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                CatalogFacetCheckboxGroup(
                    title = translationStore.t("creator.shop_create_product.catalog_filter_design_type", "Design type"),
                    options = listOf(
                        "classic" to translationStore.t("creator.shop_create_product.dt_classic", "Classic"),
                        "pattern" to translationStore.t("creator.shop_create_product.dt_pattern", "Pattern"),
                        "all-over" to translationStore.t("creator.shop_create_product.dt_all_over", "All-Over"),
                        "full-coverage" to translationStore.t("creator.shop_create_product.dt_full_coverage", "Full-Coverage"),
                        "panorama" to translationStore.t("creator.shop_create_product.dt_panorama", "Panorama")
                    ),
                    selected = facetSel.designTypes,
                    onToggle = { v, on ->
                        facetSel = facetSel.copy(
                            designTypes = if (on) facetSel.designTypes + v else facetSel.designTypes - v
                        )
                    }
                )
                CatalogFacetCheckboxGroup(
                    title = translationStore.t("creator.shop_create_product.catalog_filter_production", "Production"),
                    options = listOf(
                        "print" to translationStore.t("creator.shop_create_product.production_print", "Print"),
                        "embroidery" to translationStore.t("creator.shop_create_product.production_embroidery", "Embroidery"),
                        "3d_print" to translationStore.t("creator.shop_create_product.production_3d", "3D print")
                    ),
                    selected = facetSel.production,
                    onToggle = { v, on ->
                        facetSel = facetSel.copy(
                            production = if (on) facetSel.production + v else facetSel.production - v
                        )
                    }
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CatalogFacetCheckboxGroup(
    title: String,
    options: List<Pair<String, String>>,
    selected: Set<String>,
    onToggle: (String, Boolean) -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))
    options.forEach { (value, label) ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(value, value !in selected) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = value in selected, onCheckedChange = { onToggle(value, it) })
            Text(label, modifier = Modifier.padding(start = 4.dp))
        }
    }
    Spacer(Modifier.height(12.dp))
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
private fun CatalogProductCard(
    product: CatalogProduct,
    onClick: () -> Unit
) {
    var urlIndex by remember(product.productKey) { mutableIntStateOf(0) }
    val urls = product.mockUrls

    LaunchedEffect(product.productKey, urls.size) {
        if (urls.size < 2) return@LaunchedEffect
        while (isActive) {
            delay(CATALOG_ROTATION_MS)
            urlIndex = (urlIndex + 1) % urls.size
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            Crossfade(
                targetState = urlIndex,
                animationSpec = tween(
                    durationMillis = CATALOG_CROSSFADE_MS,
                    easing = FastOutSlowInEasing
                ),
                label = "catalog_thumb"
            ) { idx ->
                val u = urls.getOrNull(idx % urls.size.coerceAtLeast(1)).orEmpty()
                if (u.isNotEmpty()) {
                    AsyncImage(
                        model = u,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Text(
            product.title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
