package com.eazpire.creator.ui.creator

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.auth.AuthConfig
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
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
    val productsCount: Int
)

data class CreationProduct(
    val id: String,
    val title: String,
    val productName: String,
    val productKey: String,
    val imageUrl: String?,
    val storefrontUrl: String?,
    val shopifyHandle: String?,
    val publishedAt: Long?
)

private val VIEW_MODES = listOf("grid2", "grid3", "grid4", "list")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CreatorCreationsScreen(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val jwt = remember { runCatching { tokenStore.getJwt() }.getOrNull() }
    val ownerId = remember { runCatching { tokenStore.getOwnerId() }.getOrNull() ?: "" }
    val api = remember(jwt) { com.eazpire.creator.api.CreatorApi(jwt = jwt) }

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

    val shop = AuthConfig.SHOP_DOMAIN

    LaunchedEffect(ownerId, currentTab) {
        if (ownerId.isBlank()) return@LaunchedEffect
        when (currentTab) {
            "designs" -> {
                designsLoading = true
                try {
                    val (designsList, summary) = withContext(Dispatchers.IO) {
                        val listRes = api.listDesigns(ownerId, 100)
                        val genRes = api.listGenerated(ownerId, 200)
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
                                    productsCount = 0
                                )
                            }
                        }
                        val savedJobIds = savedItems.mapNotNull { it.jobId }.toSet()
                        val genItems = (genRes.optJSONArray("items") ?: JSONArray()).let { arr ->
                            (0 until arr.length()).mapNotNull { g ->
                                val obj = arr.optJSONObject(g) ?: return@mapNotNull null
                                val jid = obj.optString("job_id", "").takeIf { it.isNotBlank() }
                                if (jid != null && savedJobIds.contains(jid)) return@mapNotNull null
                                val img = obj.optString("image_url", "").ifBlank { obj.optString("preview_url", "") }
                                if (img.isBlank()) return@mapNotNull null
                                CreationDesign(
                                    id = null,
                                    designId = null,
                                    jobId = jid,
                                    imageUrl = img,
                                    previewUrl = img,
                                    originalUrl = img,
                                    title = obj.optString("prompt", obj.optString("design_prompt", "Design")).take(80),
                                    prompt = obj.optString("prompt").takeIf { it.isNotBlank() },
                                    designPrompt = obj.optString("design_prompt").takeIf { it.isNotBlank() },
                                    createdAt = (obj.opt("created_at") as? Number)?.toLong() ?: (obj.opt("finished") as? Number)?.toLong() ?: 0L,
                                    source = "generated",
                                    designSource = "Generated",
                                    creatorName = obj.optString("creator_name").takeIf { it.isNotBlank() },
                                    productsCount = 0
                                )
                            }
                        }
                        val merged = (savedItems + genItems).sortedByDescending { it.createdAt }
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
                            (0 until arr.length()).mapNotNull { i ->
                                val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                                fun toStr(v: Any?): String? = when (v) {
                                    is String -> v.takeIf { it.isNotBlank() }
                                    else -> null
                                }
                                val img = toStr(obj.opt("image_url")) ?: toStr(obj.opt("featured_image")) ?: toStr(obj.opt("preview_url"))
                                    ?: (obj.optJSONArray("images")?.opt(0)?.let { toStr(it) })
                                    ?: (obj.optJSONArray("variants")?.optJSONObject(0)?.opt("image")?.let { toStr(it) })
                                CreationProduct(
                                    id = obj.optString("shopify_product_id", obj.optString("product_key", "") + "-product"),
                                    title = obj.optString("product_name", obj.optString("product_key", "Product")),
                                    productName = obj.optString("product_name", obj.optString("product_key", "Product")),
                                    productKey = obj.optString("product_key", ""),
                                    imageUrl = img,
                                    storefrontUrl = obj.optString("storefront_url").takeIf { it.isNotBlank() },
                                    shopifyHandle = obj.optString("shopify_handle").takeIf { it.isNotBlank() },
                                    publishedAt = (obj.opt("last_published_at") as? Number)?.toLong()
                                        ?: (obj.optString("last_published_at").takeIf { it.isNotBlank() }?.let { try { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(it)?.time } catch (_: Exception) { null } })
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
    }

    val filteredDesigns = remember(designs, designsSearch.text, productsCountByDesignId) {
        val q = designsSearch.text.trim().lowercase()
        val withCount = designs.map { d ->
            d.copy(productsCount = (d.id ?: d.designId)?.let { productsCountByDesignId[it] ?: 0 } ?: 0)
        }
        if (q.isBlank()) withCount
        else withCount.filter { d ->
            (d.title.lowercase().contains(q) || (d.prompt?.lowercase()?.contains(q) == true))
        }
    }

    val filteredProducts = remember(products, productsSearch.text) {
        val q = productsSearch.text.trim().lowercase()
        if (q.isBlank()) products
        else products.filter { p ->
            p.title.lowercase().contains(q) || p.productName.lowercase().contains(q) || p.productKey.lowercase().contains(q)
        }
    }

    val gridCols = when (VIEW_MODES[viewMode]) {
        "grid2" -> 2
        "grid3" -> 3
        "grid4" -> 4
        else -> 2
    }
    val isListMode = VIEW_MODES[viewMode] == "list"

    Column(modifier = modifier.fillMaxSize()) {
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
                listOf("designs" to translationStore.t("creator.mobile.designs", "Designs"), "products" to translationStore.t("creator.mobile.products", "Products")).forEach { (tab, label) ->
                    val active = currentTab == tab
                    Box(
                        modifier = Modifier
                            .clickable { currentTab = tab }
                            .padding(vertical = 12.dp, horizontal = 20.dp)
                            .then(if (active) Modifier.border(2.dp, EazColors.Orange, RoundedCornerShape(0.dp)) else Modifier),
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
                // Toolbar
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
                        onClick = { /* TODO: Design Upload */ },
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

                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                    if (designsLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = EazColors.Orange)
                        }
                    } else if (filteredDesigns.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredDesigns) { design ->
                                CreationDesignListItem(
                                    design = design,
                                    translationStore = translationStore,
                                    onClick = { designPreviewDesign = design }
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridCols),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredDesigns) { design ->
                                CreationDesignCard(
                                    design = design,
                                    onClick = { designPreviewDesign = design }
                                )
                            }
                        }
                    }
                }
            }
            "products" -> {
                // Toolbar (no upload)
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

                Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                    if (productsLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = EazColors.Orange)
                        }
                    } else if (filteredProducts.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredProducts) { product ->
                                CreationProductListItem(
                                    product = product,
                                    translationStore = translationStore,
                                    onClick = {
                                        product.storefrontUrl?.let { url ->
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    }
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(gridCols),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredProducts) { product ->
                                CreationProductCard(
                                    product = product,
                                    onClick = {
                                        product.storefrontUrl?.let { url ->
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                        }
                                    }
                                )
                            }
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
            translationStore = translationStore
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

    if (designPreviewDesign != null) {
        CreatorDesignPreviewModal(
            design = designPreviewDesign,
            onDismiss = { designPreviewDesign = null },
            translationStore = translationStore
        )
    }
}

@Composable
private fun CreationDesignCard(
    design: CreationDesign,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = design.imageUrl,
                contentDescription = design.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
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
        AsyncImage(
            model = design.imageUrl,
            contentDescription = design.title,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
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
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
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
            ) {
                if (!product.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = product.imageUrl,
                        contentDescription = product.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
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
        if (!product.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = product.imageUrl,
                contentDescription = product.title,
                modifier = Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = Color.White.copy(alpha = 0.5f))
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
