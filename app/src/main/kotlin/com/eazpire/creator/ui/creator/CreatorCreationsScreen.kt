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
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    val contentType: String? = null
)

data class CreationProduct(
    val id: String,
    val title: String,
    val productName: String,
    val productKey: String,
    val imageUrl: String?,
    val storefrontUrl: String?,
    val shopifyHandle: String?,
    val publishedAt: Long?,
    val publishedCount: Int = 0
)

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CreatorCreationsScreen(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    maxHeight: Dp = Dp.Infinity,
    modifier: Modifier = Modifier,
    onRequestGeneratorPrefill: (GeneratorPrefillRequest) -> Unit = {}
) {
    val boundedHeight = if (maxHeight == Dp.Infinity) 4000.dp else maxHeight
    val context = LocalContext.current
    val jwt = remember { runCatching { tokenStore.getJwt() }.getOrNull() }
    val ownerId = remember { runCatching { tokenStore.getOwnerId() }.getOrNull() ?: "" }
    val api = remember(jwt) { com.eazpire.creator.api.CreatorApi(jwt = jwt) }
    val shopifyApi = remember { ShopifyProductsApi() }
    val shop = AuthConfig.SHOP_DOMAIN

    var currentTab by remember { mutableStateOf("designs") }
    var designs by remember { mutableStateOf<List<CreationDesign>>(emptyList()) }
    var products by remember { mutableStateOf<List<CreationProduct>>(emptyList()) }
    var productsCountByDesignId by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var designsLoading by remember { mutableStateOf(false) }
    var productsLoading by remember { mutableStateOf(false) }
    var designsSearch by remember { mutableStateOf(TextFieldValue("")) }
    var productsSearch by remember { mutableStateOf(TextFieldValue("")) }
    var viewMode by remember { mutableIntStateOf(0) } // grid2=0, grid3=1, grid4=2, list=3
    var filterModalVisible by remember { mutableStateOf(false) }
    var viewModeOverlayVisible by remember { mutableStateOf(false) }
    var designPreviewDesign by remember { mutableStateOf<CreationDesign?>(null) }
    var creationsFilter by remember { mutableStateOf(CreationsFilterState()) }
    var designsRefreshTrigger by remember { mutableIntStateOf(0) }
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

    LaunchedEffect(ownerId, currentTab, designsRefreshTrigger) {
        if (ownerId.isBlank()) {
            designsLoading = false
            productsLoading = false
            return@LaunchedEffect
        }
        try {
        when (currentTab) {
            "designs" -> {
                designsLoading = true
                try {
                    val (designsList, summary) = withContext(Dispatchers.IO) {
                        // Nur gespeicherte/uploaded Designs laden (wie Web My Creations)
                        val listRes = api.listDesigns(ownerId, 100)
                        val summaryRes = api.getPublishedSummary(ownerId, shop)

                        val savedItems = (listRes.optJSONArray("items") ?: JSONArray()).let { arr ->
                            (0 until arr.length()).mapNotNull { i ->
                                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                                val meta = try {
                                    val m = obj.opt("metadata")
                                    when (m) {
                                        is String -> JSONObject(m.ifBlank { "{}" })
                                        is JSONObject -> m
                                        else -> JSONObject()
                                    }
                                } catch (_: Exception) { JSONObject() }
                                val userImg = meta.optString("user_image_url", "").ifBlank { obj.optString("user_image_url", "") }
                                val designPrompt = meta.optString("design_prompt", "").ifBlank { obj.optString("design_prompt", "") }
                                val isUploaded = userImg.isNotBlank() && designPrompt.isBlank()
                                val src = when {
                                    isUploaded -> "uploaded"
                                    else -> (obj.optString("design_source", obj.optString("source", "saved"))).lowercase()
                                }
                                val preview = obj.optString("preview_url", "").ifBlank { obj.optString("original_url", "") }
                                if (preview.isBlank()) return@mapNotNull null
                                val ct = meta.optString("content_type", "").let { c ->
                                    when (c) {
                                        "Design + Text" -> "design_text"
                                        "Text Only" -> "text_only"
                                        "Design Only" -> "design_only"
                                        else -> c.ifBlank { null }
                                    }
                                }
                                CreationDesign(
                                    id = obj.optString("id", "").takeIf { it.isNotBlank() },
                                    designId = obj.optString("id", "").takeIf { it.isNotBlank() },
                                    jobId = obj.optString("job_id", "").takeIf { it.isNotBlank() },
                                    imageUrl = preview,
                                    previewUrl = obj.optString("preview_url", "").ifBlank { preview },
                                    originalUrl = obj.optString("original_url", "").ifBlank { preview },
                                    title = obj.optString("title", obj.optString("prompt", "Design")).take(80),
                                    prompt = obj.optString("prompt").takeIf { it.isNotBlank() },
                                    designPrompt = obj.optString("design_prompt").takeIf { it.isNotBlank() },
                                    createdAt = (obj.opt("updated_at") as? Number)?.toLong() ?: (obj.opt("created_at") as? Number)?.toLong() ?: 0L,
                                    source = src,
                                    designSource = when (src) {
                                        "generated" -> "Generated"
                                        "uploaded" -> "Uploaded"
                                        else -> "Saved"
                                    },
                                    creatorName = meta.optString("creator_name", "").takeIf { it.isNotBlank() }
                                        ?: obj.optString("creator_name", "").takeIf { it.isNotBlank() },
                                    productsCount = 0,
                                    ratio = meta.optString("ratio", "").takeIf { it.isNotBlank() }?.lowercase(),
                                    designType = meta.optString("design_type", "").takeIf { it.isNotBlank() }?.lowercase(),
                                    contentType = ct
                                )
                            }
                        }
                        val merged = savedItems.sortedByDescending { it.createdAt }
                        val summaryMap = mutableMapOf<String, Int>()
                        if (summaryRes.optBoolean("ok", false)) {
                            (summaryRes.optJSONArray("designs") ?: JSONArray()).let { arr ->
                                for (i in 0 until arr.length()) {
                                    val d = arr.optJSONObject(i) ?: continue
                                    val did = d.optString("design_id", "").takeIf { it.isNotBlank() } ?: continue
                                    summaryMap[did] = d.optInt("products_count", 0)
                                }
                            }
                        }
                        merged.map { d ->
                            d.copy(productsCount = (d.id ?: d.designId)?.let { summaryMap[it] ?: 0 } ?: 0)
                        } to summaryMap
                    }
                    designs = designsList
                    productsCountByDesignId = summary
                } catch (_: Exception) {
                    designs = emptyList()
                } finally {
                    designsLoading = false
                }
            }
            "products" -> {
                productsLoading = true
                try {
                    val resp = withContext(Dispatchers.IO) {
                        api.getPublishedProducts(ownerId, shop)
                    }
                    products = if (resp.optBoolean("ok", false)) {
                        (resp.optJSONArray("products") ?: JSONArray()).let { arr ->
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
                                CreationProduct(
                                    id = obj.optString("shopify_product_id", "")
                                        .ifBlank { obj.optString("product_key", "") + "-product" },
                                    title = title,
                                    productName = title,
                                    productKey = productKey,
                                    imageUrl = img,
                                    storefrontUrl = storefront,
                                    shopifyHandle = obj.optString("shopify_handle").takeIf { it.isNotBlank() },
                                    publishedAt = (obj.opt("last_published_at") as? Number)?.toLong()
                                        ?: (obj.optString("last_published_at").takeIf { it.isNotBlank() }?.let { try { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(it)?.time } catch (_: Exception) { null } }),
                                    publishedCount = obj.optInt("published_count", 0)
                                )
                            }.sortedByDescending { it.publishedAt ?: 0L }
                        }
                    } else emptyList()
                } catch (_: Exception) {
                    products = emptyList()
                } finally {
                    productsLoading = false
                }
            }
        }
        } catch (e: Exception) {
            designs = emptyList()
            products = emptyList()
            designsLoading = false
            productsLoading = false
        }
    }

    val filteredDesigns = remember(designs, designsSearch.text, productsCountByDesignId, creationsFilter) {
        val q = designsSearch.text.trim().lowercase()
        val withCount = designs.map { d ->
            d.copy(productsCount = (d.id ?: d.designId)?.let { productsCountByDesignId[it] ?: 0 } ?: 0)
        }
        var list = if (q.isBlank()) withCount else withCount.filter { d ->
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
        list
    }

    val filteredProducts = remember(products, productsSearch.text, creationsFilter) {
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
        list
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
                if (designsLoading) {
                    Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = EazColors.Orange)
                    }
                } else if (filteredDesigns.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color.White.copy(alpha = 0.5f))
                            Spacer(Modifier.size(8.dp))
                            Text(
                                if (designs.isEmpty()) translationStore.t("creator.creations.no_designs", "No designs found.") else translationStore.t("creator.mobile.no_search_results", "No search results."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else if (isListMode) {
                    LazyColumn(
                        state = designsListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .heightIn(max = boundedHeight),
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
                                    value = designsSearch,
                                    onValueChange = { designsSearch = it },
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
                                IconButton(
                                    onClick = {
                                        if (!uploadInProgress) imagePicker.launch("image/*")
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(EazColors.Orange.copy(alpha = 0.2f))
                                        .border(1.dp, EazColors.Orange, RoundedCornerShape(8.dp))
                                ) {
                                    Icon(Icons.Default.Upload, contentDescription = null, tint = Color.White)
                                }
                                Text(
                                    "${filteredDesigns.size} ${translationStore.t("creator.mobile.designs", "Designs")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                        items(filteredDesigns, key = { d -> d.id ?: d.designId ?: d.jobId ?: d.imageUrl }) { design ->
                            CreationDesignListItem(
                                design = design,
                                translationStore = translationStore,
                                onClick = { designPreviewDesign = design }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        state = designsGridState,
                        columns = GridCells.Fixed(gridCols),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .heightIn(max = boundedHeight),
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
                                    value = designsSearch,
                                    onValueChange = { designsSearch = it },
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
                                IconButton(
                                    onClick = {
                                        if (!uploadInProgress) imagePicker.launch("image/*")
                                    },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(EazColors.Orange.copy(alpha = 0.2f))
                                        .border(1.dp, EazColors.Orange, RoundedCornerShape(8.dp))
                                ) {
                                    Icon(Icons.Default.Upload, contentDescription = null, tint = Color.White)
                                }
                                Text(
                                    "${filteredDesigns.size} ${translationStore.t("creator.mobile.designs", "Designs")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                        items(filteredDesigns, key = { d -> d.id ?: d.designId ?: d.jobId ?: d.imageUrl }) { design ->
                            CreationDesignCard(
                                design = design,
                                onClick = { designPreviewDesign = design }
                            )
                        }
                    }
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
            onRequestGeneratorPrefill = onRequestGeneratorPrefill
        )
    }
}

@Composable
private fun CreationDesignCard(
    design: CreationDesign,
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF353D4C)),
            contentAlignment = Alignment.Center
        ) {
            SubcomposeAsyncImage(
                model = design.imageUrl,
                contentDescription = design.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center,
                loading = { GridImageShimmer(Modifier.fillMaxSize()) }
            )
            if (design.productsCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(EazColors.Orange, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "${design.productsCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun CreationDesignListItem(
    design: CreationDesign,
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
                if (design.productsCount > 0) {
                    Text(
                        "${design.productsCount} products",
                        style = MaterialTheme.typography.labelSmall,
                        color = EazColors.Orange
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
