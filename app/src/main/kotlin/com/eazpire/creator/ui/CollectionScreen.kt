package com.eazpire.creator.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.eazpire.creator.api.ShopifyProductsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val IMAGE_ROTATE_INTERVAL_MS = 1800L
private const val PRODUCTS_PER_PAGE = 24

private data class SortOption(val value: String, val label: String)

// Design filter (creator-mobile-filter-modal): Price, Content Type, Design Type, Design Style, Ratio, Design Language, Product Category, Product Type
private data class ProductFilters(
    val priceMin: String = "",
    val priceMax: String = "",
    val contentTypes: Set<String> = emptySet(),
    val designTypes: Set<String> = emptySet(),
    val designStyles: Set<String> = emptySet(),
    val ratios: Set<String> = emptySet(),           // Portrait, Landscape, Square
    val designLanguages: Set<String> = emptySet(),   // English, German, Bilingual
    val categories: Set<String> = emptySet(),        // Clothing, Accessories, Home & Living, Other
    val productTypes: Set<String> = emptySet()       // dynamic: T-Shirt, Hoodie, Poster, etc.
) {
    fun isEmpty(): Boolean =
        priceMin.isBlank() && priceMax.isBlank() && contentTypes.isEmpty() &&
        designTypes.isEmpty() && designStyles.isEmpty() && ratios.isEmpty() &&
        designLanguages.isEmpty() && categories.isEmpty() && productTypes.isEmpty()
}

/** Wie Web: normalizeValue für Vergleich (creator-inspiration-filter-modal.js, creator-mobile-filter-modal.js) */
private fun normalizeValue(v: String?): String =
    (v ?: "").trim().lowercase()

/** Wie Web (creator-inspiration-filter-modal.js): content_type "Design + Text" -> design_text */
private fun contentTypeToFilterKey(ct: String): String = when {
    ct.equals("Design + Text", ignoreCase = true) || ct.equals("design_text", ignoreCase = true) || ct.equals("design-text", ignoreCase = true) -> "design_text"
    ct.equals("Design Only", ignoreCase = true) || ct.equals("design_only", ignoreCase = true) || ct.equals("design-only", ignoreCase = true) -> "design_only"
    ct.equals("Text Only", ignoreCase = true) || ct.equals("text_only", ignoreCase = true) || ct.equals("text-only", ignoreCase = true) -> "text_only"
    else -> normalizeValue(ct)
}

private fun contentTypeToFilterValue(ct: String): String = when {
    ct.equals("Design + Text", ignoreCase = true) || ct.equals("design_text", ignoreCase = true) -> "Design + Text"
    ct.equals("Design Only", ignoreCase = true) || ct.equals("design_only", ignoreCase = true) -> "Design Only"
    ct.equals("Text Only", ignoreCase = true) || ct.equals("text_only", ignoreCase = true) -> "Text Only"
    else -> ct
}

/** Wie Web: design_type Vergleich via normalizeValue (Classic -> classic) */
private fun designTypeToFilterValue(dt: String): String = when {
    dt.equals("Classic", ignoreCase = true) -> "Classic"
    dt.equals("Pattern", ignoreCase = true) -> "Pattern"
    dt.equals("All Over", ignoreCase = true) || dt.equals("All-Over", ignoreCase = true) || dt.equals("all_over", ignoreCase = true) -> "All Over"
    dt.equals("Full Surface", ignoreCase = true) || dt.equals("Full-Coverage", ignoreCase = true) || dt.equals("full_surface", ignoreCase = true) -> "Full Surface"
    dt.equals("Panorama", ignoreCase = true) -> "Panorama"
    else -> dt
}

/** Wie Web: ratio kann "Portrait", "Landscape", "Square" oder "1:1" etc. sein */
private fun ratioToFilterValue(r: String): String = when {
    r.equals("Portrait", ignoreCase = true) || r.equals("4:5", ignoreCase = true) || r.equals("3:4", ignoreCase = true) -> "Portrait"
    r.equals("Landscape", ignoreCase = true) || r.equals("16:9", ignoreCase = true) || r.equals("4:3", ignoreCase = true) -> "Landscape"
    r.equals("Square", ignoreCase = true) || r.equals("1:1", ignoreCase = true) -> "Square"
    else -> r
}

private fun productTypeToCategory(productType: String, title: String): String {
    val t = (productType + " " + title).lowercase()
    return when {
        Regex("shirt|tee|hoodie|tank|jacket|sweat|pullover|t-shirt").containsMatchIn(t) -> "Clothing"
        Regex("bag|cap|hat|beanie|backpack").containsMatchIn(t) -> "Accessories"
        Regex("poster|pillow|mug|blanket|cushion").containsMatchIn(t) -> "Home & Living"
        else -> "Other"
    }
}

private fun applyFilters(
    products: List<ShopifyProductsApi.ProductItem>,
    filters: ProductFilters
): List<ShopifyProductsApi.ProductItem> {
    if (filters.isEmpty()) return products
    return products.filter { p ->
        val priceMin = filters.priceMin.toDoubleOrNull()
        val priceMax = filters.priceMax.toDoubleOrNull()
        if (priceMin != null && p.price < priceMin) return@filter false
        if (priceMax != null && p.price > priceMax) return@filter false
        if (filters.contentTypes.isNotEmpty()) {
            val ct = contentTypeToFilterValue(p.contentType)
            if (ct.isBlank() || ct !in filters.contentTypes) return@filter false
        }
        if (filters.designTypes.isNotEmpty()) {
            val dt = designTypeToFilterValue(p.designType)
            if (dt.isBlank() || dt !in filters.designTypes) return@filter false
        }
        if (filters.designStyles.isNotEmpty()) {
            val styles = p.designStyle.map { it.trim() }.filter { it.isNotBlank() }
            if (styles.isEmpty() || !styles.any { s -> filters.designStyles.any { s.equals(it, ignoreCase = true) } }) return@filter false
        }
        if (filters.ratios.isNotEmpty()) {
            val r = ratioToFilterValue(p.ratio).ifBlank { return@filter false }
            if (r !in filters.ratios) return@filter false
        }
        if (filters.designLanguages.isNotEmpty()) {
            val dl = p.designLanguage.ifBlank { return@filter false }
            if (!filters.designLanguages.any { dl.equals(it, ignoreCase = true) }) return@filter false
        }
        if (filters.categories.isNotEmpty()) {
            val cat = productTypeToCategory(p.productType, p.title)
            if (cat !in filters.categories) return@filter false
        }
        if (filters.productTypes.isNotEmpty()) {
            val pt = p.productType.ifBlank { "Other" }
            if (pt !in filters.productTypes) return@filter false
        }
        true
    }
}

private val SORT_OPTIONS = listOf(
    SortOption("manual", "Featured"),
    SortOption("created-descending", "Newest"),
    SortOption("created-ascending", "Oldest"),
    SortOption("price-ascending", "Price: Low to High"),
    SortOption("price-descending", "Price: High to Low"),
    SortOption("title-ascending", "A–Z"),
    SortOption("title-descending", "Z–A"),
)

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
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val api = remember { ShopifyProductsApi() }
    var productsByPage by remember { mutableStateOf<Map<Int, List<ShopifyProductsApi.ProductItem>>>(emptyMap()) }
    var pageCursors by remember { mutableStateOf(listOf<String?>(null)) }
    var hasNextPage by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var currentPage by remember { mutableStateOf(1) }
    var sortBy by remember { mutableStateOf("manual") }
    var sortSheetVisible by remember { mutableStateOf(false) }
    var filterDrawerVisible by remember { mutableStateOf(false) }
    var productFilters by remember { mutableStateOf(ProductFilters()) }
    var filterCountProducts by remember { mutableStateOf<List<ShopifyProductsApi.ProductItem>>(emptyList()) }
    val context = LocalContext.current
    val density = LocalDensity.current

    val products = productsByPage[currentPage] ?: emptyList()
    val totalPages = maxOf(1, if (hasNextPage) currentPage + 1 else currentPage)

    LaunchedEffect(collectionHandle) {
        productsByPage = emptyMap()
        pageCursors = listOf(null)
        hasNextPage = false
        currentPage = 1
        filterCountProducts = emptyList()
    }

    LaunchedEffect(collectionHandle, currentPage) {
        if (productsByPage.containsKey(currentPage)) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        val cursor = pageCursors.getOrNull(currentPage - 1)
        val result = withContext(Dispatchers.IO) {
            var r = api.getProducts(
                collectionHandle = collectionHandle.ifBlank { null },
                limit = PRODUCTS_PER_PAGE,
                cursor = cursor
            )
            if (r.products.isEmpty() && collectionHandle.isNotBlank()) {
                r = api.getProducts(limit = PRODUCTS_PER_PAGE, cursor = cursor)
            }
            r
        }
        productsByPage = productsByPage + (currentPage to result.products)
        if (result.hasNextPage && result.nextCursor != null && currentPage >= pageCursors.size) {
            pageCursors = pageCursors + result.nextCursor
        }
        hasNextPage = result.hasNextPage
        isLoading = false
    }

    // Load 250 products for filtering: when drawer opens OR when filters applied but not yet loaded.
    // Key includes productFilters.isEmpty() so we re-run when user applies – then load if still empty
    // (handles fast Apply before initial load completed / LaunchedEffect was cancelled).
    LaunchedEffect(collectionHandle, filterDrawerVisible, productFilters.isEmpty()) {
        val needFilterProducts = filterDrawerVisible || !productFilters.isEmpty()
        if (needFilterProducts && filterCountProducts.isEmpty()) {
            filterCountProducts = withContext(Dispatchers.IO) {
                var r = api.getProducts(
                    collectionHandle = collectionHandle.ifBlank { null },
                    limit = 250,
                    cursor = null
                )
                if (r.products.isEmpty() && collectionHandle.isNotBlank()) {
                    r = api.getProducts(limit = 250, cursor = null)
                }
                r.products
            }
        }
    }

    val allLoadedProducts = remember(productsByPage) { productsByPage.values.flatten() }
    val productsToFilter = when {
        productFilters.isEmpty() -> products
        filterCountProducts.isNotEmpty() -> filterCountProducts
        else -> allLoadedProducts
    }
    val sortedProducts = remember(productsToFilter, sortBy) { sortProducts(productsToFilter, sortBy) }
    val filteredProducts = remember(sortedProducts, productFilters) {
        applyFilters(sortedProducts, productFilters)
    }
    val currentSortLabel = SORT_OPTIONS.find { it.value == sortBy }?.label ?: "Sort by"

    Column(modifier = modifier.fillMaxSize()) {
        if (isLoading && products.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = EazColors.Orange)
            }
        } else if (products.isEmpty()) {
            CollectionComingSoon(title = title, onBrowseAll = onBack)
        } else {
            ResultsBar(
                productCount = filteredProducts.size,
                collectionTitle = title,
                sortBy = sortBy,
                sortLabel = currentSortLabel,
                onFilterClick = { filterDrawerVisible = true },
                onSortClick = { sortSheetVisible = true }
            )
            LazyVerticalGrid(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(currentPage, totalPages) {
                        var totalDrag = 0f
                        val thresholdPx = with(density) { 80.dp.toPx() }
                        detectHorizontalDragGestures(
                            onDragStart = { totalDrag = 0f },
                            onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                            onDragEnd = {
                                when {
                                    totalDrag > thresholdPx && currentPage > 1 -> currentPage = currentPage - 1
                                    totalDrag < -thresholdPx && currentPage < totalPages -> currentPage = currentPage + 1
                                }
                            }
                        )
                    },
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(filteredProducts) { product ->
                    CollectionProductCard(
                        product = product,
                        onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(product.url)))
                            } catch (_: Exception) {}
                        }
                    )
                }
            }
            if (totalPages > 1 && productFilters.isEmpty()) {
                PaginationDots(
                    totalPages = totalPages,
                    currentPage = currentPage,
                    onPageClick = { currentPage = it },
                    onSwipePrev = { if (currentPage > 1) currentPage = currentPage - 1 },
                    onSwipeNext = { if (currentPage < totalPages) currentPage = currentPage + 1 }
                )
            }
        }
    }

    if (filterDrawerVisible) {
        val allLoadedProducts = remember(productsByPage) {
            productsByPage.values.flatten()
        }
        val productsForCounts = if (filterCountProducts.isNotEmpty()) filterCountProducts else allLoadedProducts
        FilterDrawer(
            filters = productFilters,
            products = productsForCounts,
            onFiltersChange = { productFilters = it },
            onDismiss = { filterDrawerVisible = false }
        )
    }

    if (sortSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { sortSheetVisible = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Sort by",
                    style = MaterialTheme.typography.titleMedium,
                    color = EazColors.TextPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                SORT_OPTIONS.forEach { opt ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                sortBy = opt.value
                                sortSheetVisible = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            opt.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (sortBy == opt.value) EazColors.Orange else EazColors.TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultsBar(
    productCount: Int,
    collectionTitle: String,
    sortBy: String,
    sortLabel: String,
    onFilterClick: () -> Unit,
    onSortClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isFeatured = sortBy == "manual"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onFilterClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = "Filter",
                    tint = EazColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = "$productCount products in $collectionTitle",
                style = MaterialTheme.typography.bodySmall,
                color = EazColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(EazColors.Orange)
                .clickable(onClick = onSortClick)
                .padding(
                    horizontal = if (isFeatured) 10.dp else 14.dp,
                    vertical = 7.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.SwapVert,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            if (!isFeatured) {
                Text(
                    sortLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// Design filter (creator-mobile-filter-modal, saveDesign.js, custom-fields-metadata.md)
private val CONTENT_TYPE_OPTIONS = listOf(
    "Design + Text" to "Design + Text",
    "Design Only" to "Design Only",
    "Text Only" to "Text Only"
)

private val DESIGN_TYPE_OPTIONS = listOf(
    "Classic" to "Classic",
    "Pattern" to "Pattern",
    "All Over" to "All Over",
    "Full Surface" to "Full Surface",
    "Panorama" to "Panorama"
)

private val DESIGN_STYLE_OPTIONS = listOf(
    "Minimalist" to "Minimalist",
    "Urban" to "Urban",
    "Modern" to "Modern",
    "Vintage" to "Vintage",
    "Bold" to "Bold",
    "Playful" to "Playful",
    "Retro" to "Retro",
    "Elegant" to "Elegant",
    "Scandinavian" to "Scandinavian",
    "Abstract" to "Abstract",
    "Geometric" to "Geometric",
    "Floral" to "Floral"
)

private val RATIO_OPTIONS = listOf(
    "Portrait" to "Portrait",
    "Landscape" to "Landscape",
    "Square" to "Square"
)

private val DESIGN_LANGUAGE_OPTIONS = listOf(
    "English" to "English",
    "German" to "German",
    "Bilingual" to "Bilingual"
)

private val CATEGORY_OPTIONS = listOf(
    "Clothing" to "Clothing",
    "Accessories" to "Accessories",
    "Home & Living" to "Home & Living",
    "Other" to "Other"
)

@Composable
private fun FilterOptionRow(
    label: String,
    count: Int? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayLabel = if (count != null) "$label ($count)" else label
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.size(20.dp),
            colors = androidx.compose.material3.CheckboxDefaults.colors(
                checkedColor = EazColors.Orange
            )
        )
        Text(
            displayLabel,
            style = MaterialTheme.typography.bodySmall,
            color = EazColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDrawer(
    filters: ProductFilters,
    products: List<ShopifyProductsApi.ProductItem>,
    onFiltersChange: (ProductFilters) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val productTypesWithCounts = remember(products) {
        products
            .map { it.productType.ifBlank { "Other" } }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
    }
    // Wie Web (creator-inspiration-filter-modal, creator-mobile-filter-modal): normalizeValue + content_type mapping
    val contentTypesCount = remember(products) {
        CONTENT_TYPE_OPTIONS.associate { (value, _) ->
            val filterKey = when (value) {
                "Design + Text" -> "design_text"
                "Design Only" -> "design_only"
                "Text Only" -> "text_only"
                else -> normalizeValue(value)
            }
            value to products.count { normalizeValue(contentTypeToFilterKey(it.contentType)) == filterKey }
        }
    }
    val designTypesCount = remember(products) {
        DESIGN_TYPE_OPTIONS.associate { (value, _) ->
            value to products.count { normalizeValue(designTypeToFilterValue(it.designType)) == normalizeValue(value) }
        }
    }
    val designStylesCount = remember(products) {
        DESIGN_STYLE_OPTIONS.associate { (value, _) ->
            value to products.count { p ->
                p.designStyle.any { normalizeValue(it) == normalizeValue(value) }
            }
        }
    }
    val ratiosCount = remember(products) {
        RATIO_OPTIONS.associate { (value, _) ->
            value to products.count { normalizeValue(ratioToFilterValue(it.ratio)) == normalizeValue(value) }
        }
    }
    val designLanguagesCount = remember(products) {
        DESIGN_LANGUAGE_OPTIONS.associate { (value, _) ->
            value to products.count { normalizeValue(it.designLanguage) == normalizeValue(value) }
        }
    }
    val categoriesCount = remember(products) {
        CATEGORY_OPTIONS.associate { (value, _) ->
            value to products.count { productTypeToCategory(it.productType, it.title) == value }
        }
    }
    val filteredCount = remember(products, filters) {
        applyFilters(products, filters).size
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .heightIn(min = 450.dp)
        ) {
            // Header (fixed)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = null,
                        tint = EazColors.Orange,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        "Filters",
                        style = MaterialTheme.typography.titleMedium,
                        color = EazColors.TextPrimary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = EazColors.TextSecondary)
                }
            }

            // Content (scrollable)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
            ) {
            Text(
                "Price",
                style = MaterialTheme.typography.labelLarge,
                color = EazColors.TextPrimary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Row(
                modifier = Modifier.padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextField(
                    value = filters.priceMin,
                    onValueChange = { onFiltersChange(filters.copy(priceMin = it)) },
                    placeholder = { Text("Min", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                Text("–", color = EazColors.TextSecondary)
                TextField(
                    value = filters.priceMax,
                    onValueChange = { onFiltersChange(filters.copy(priceMax = it)) },
                    placeholder = { Text("Max", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            }

            Text(
                "Content Type",
                style = MaterialTheme.typography.labelLarge,
                color = EazColors.TextPrimary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Column(modifier = Modifier.padding(bottom = 14.dp)) {
                CONTENT_TYPE_OPTIONS.forEach { (value, label) ->
                    FilterOptionRow(
                        label = label,
                        count = contentTypesCount[value] ?: 0,
                        checked = value in filters.contentTypes,
                        onCheckedChange = {
                            val next = if (it) filters.contentTypes + value else filters.contentTypes - value
                            onFiltersChange(filters.copy(contentTypes = next))
                        },
                        onClick = {
                            val next = if (value in filters.contentTypes) filters.contentTypes - value else filters.contentTypes + value
                            onFiltersChange(filters.copy(contentTypes = next))
                        }
                    )
                }
            }

            Text(
                "Design Type",
                style = MaterialTheme.typography.labelLarge,
                color = EazColors.TextPrimary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Column(modifier = Modifier.padding(bottom = 14.dp)) {
                DESIGN_TYPE_OPTIONS.forEach { (value, label) ->
                    FilterOptionRow(
                        label = label,
                        checked = value in filters.designTypes,
                        onCheckedChange = {
                            val next = if (it) filters.designTypes + value else filters.designTypes - value
                            onFiltersChange(filters.copy(designTypes = next))
                        },
                        onClick = {
                            val next = if (value in filters.designTypes) filters.designTypes - value else filters.designTypes + value
                            onFiltersChange(filters.copy(designTypes = next))
                        }
                    )
                }
            }

            Text(
                "Design Style",
                style = MaterialTheme.typography.labelLarge,
                color = EazColors.TextPrimary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Column(modifier = Modifier.padding(bottom = 14.dp)) {
                DESIGN_STYLE_OPTIONS.forEach { (value, label) ->
                    FilterOptionRow(
                        label = label,
                        count = designStylesCount[value] ?: 0,
                        checked = value in filters.designStyles,
                        onCheckedChange = {
                            val next = if (it) filters.designStyles + value else filters.designStyles - value
                            onFiltersChange(filters.copy(designStyles = next))
                        },
                        onClick = {
                            val next = if (value in filters.designStyles) filters.designStyles - value else filters.designStyles + value
                            onFiltersChange(filters.copy(designStyles = next))
                        }
                    )
                }
            }

            Text(
                "Ratio",
                style = MaterialTheme.typography.labelLarge,
                color = EazColors.TextPrimary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Column(modifier = Modifier.padding(bottom = 14.dp)) {
                RATIO_OPTIONS.forEach { (value, label) ->
                    FilterOptionRow(
                        label = label,
                        count = ratiosCount[value] ?: 0,
                        checked = value in filters.ratios,
                        onCheckedChange = {
                            val next = if (it) filters.ratios + value else filters.ratios - value
                            onFiltersChange(filters.copy(ratios = next))
                        },
                        onClick = {
                            val next = if (value in filters.ratios) filters.ratios - value else filters.ratios + value
                            onFiltersChange(filters.copy(ratios = next))
                        }
                    )
                }
            }

            Text(
                "Design Language",
                style = MaterialTheme.typography.labelLarge,
                color = EazColors.TextPrimary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Column(modifier = Modifier.padding(bottom = 14.dp)) {
                DESIGN_LANGUAGE_OPTIONS.forEach { (value, label) ->
                    FilterOptionRow(
                        label = label,
                        checked = value in filters.designLanguages,
                        onCheckedChange = {
                            val next = if (it) filters.designLanguages + value else filters.designLanguages - value
                            onFiltersChange(filters.copy(designLanguages = next))
                        },
                        onClick = {
                            val next = if (value in filters.designLanguages) filters.designLanguages - value else filters.designLanguages + value
                            onFiltersChange(filters.copy(designLanguages = next))
                        }
                    )
                }
            }

            Text(
                "Product Category",
                style = MaterialTheme.typography.labelLarge,
                color = EazColors.TextPrimary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Column(modifier = Modifier.padding(bottom = 14.dp)) {
                CATEGORY_OPTIONS.forEach { (value, label) ->
                    FilterOptionRow(
                        label = label,
                        count = categoriesCount[value] ?: 0,
                        checked = value in filters.categories,
                        onCheckedChange = {
                            val next = if (it) filters.categories + value else filters.categories - value
                            onFiltersChange(filters.copy(categories = next))
                        },
                        onClick = {
                            val next = if (value in filters.categories) filters.categories - value else filters.categories + value
                            onFiltersChange(filters.copy(categories = next))
                        }
                    )
                }
            }

            Text(
                "Product Type",
                style = MaterialTheme.typography.labelLarge,
                color = EazColors.TextPrimary,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                productTypesWithCounts.forEach { (pt, count) ->
                    FilterOptionRow(
                        label = pt,
                        count = count,
                        checked = pt in filters.productTypes,
                        onCheckedChange = {
                            val next = if (it) filters.productTypes + pt else filters.productTypes - pt
                            onFiltersChange(filters.copy(productTypes = next))
                        },
                        onClick = {
                            val next = if (pt in filters.productTypes) filters.productTypes - pt else filters.productTypes + pt
                            onFiltersChange(filters.copy(productTypes = next))
                        }
                    )
                }
            }
            }

            // Footer (fixed)
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
                        .clickable { onFiltersChange(ProductFilters()) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Reset",
                        style = MaterialTheme.typography.labelLarge,
                        color = EazColors.Orange
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(EazColors.Orange)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Apply ($filteredCount)",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun PaginationDots(
    totalPages: Int,
    currentPage: Int,
    onPageClick: (Int) -> Unit,
    onSwipePrev: () -> Unit = {},
    onSwipeNext: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(vertical = 16.dp)
            .pointerInput(currentPage, totalPages) {
                var totalDrag = 0f
                val thresholdPx = with(density) { 60.dp.toPx() }
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                    onDragEnd = {
                        when {
                            totalDrag > thresholdPx -> onSwipePrev()
                            totalDrag < -thresholdPx -> onSwipeNext()
                        }
                    }
                )
            },
        horizontalArrangement = Arrangement.Center
    ) {
        (1..totalPages).forEach { page ->
            Box(
                modifier = Modifier
                    .padding(4.dp)
                    .size(if (page == currentPage) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (page == currentPage) EazColors.Orange
                        else EazColors.TextSecondary.copy(alpha = 0.3f)
                    )
                    .clickable { onPageClick(page) }
            )
        }
    }
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

@Composable
private fun CollectionProductCard(
    product: ShopifyProductsApi.ProductItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val images = product.variantImages
    var currentIndex by remember(product.id) { mutableStateOf(0) }

    LaunchedEffect(product.id, images.size) {
        if (images.size <= 1) return@LaunchedEffect
        while (true) {
            delay(IMAGE_ROTATE_INTERVAL_MS)
            currentIndex = (currentIndex + 1) % images.size
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
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
        ) {
            if (images.isNotEmpty()) {
                images.forEachIndexed { index, url ->
                    val isActive = index == currentIndex
                    val alpha by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (isActive) 1f else 0f,
                        animationSpec = androidx.compose.animation.core.tween(
                            durationMillis = 1200,
                            easing = androidx.compose.animation.core.FastOutSlowInEasing
                        ),
                        label = "productImageAlpha"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(alpha = alpha)
                            .zIndex(if (isActive) 1f else 0f)
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(url)
                                .crossfade(0)
                                .build(),
                            contentDescription = product.title,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        Text(
            text = product.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
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
