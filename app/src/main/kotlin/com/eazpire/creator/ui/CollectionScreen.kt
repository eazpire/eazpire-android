package com.eazpire.creator.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
    var products by remember { mutableStateOf<List<ShopifyProductsApi.ProductItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var currentPage by remember { mutableStateOf(1) }
    var totalPages by remember { mutableStateOf(1) }
    var sortBy by remember { mutableStateOf("manual") }
    var sortSheetVisible by remember { mutableStateOf(false) }
    var filterDrawerVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(collectionHandle, currentPage) {
        isLoading = true
        val result = withContext(Dispatchers.IO) {
            val fromCollection = api.getProducts(
                collectionHandle = collectionHandle,
                limit = PRODUCTS_PER_PAGE,
                page = currentPage
            )
            if (fromCollection.isEmpty()) api.getProducts(limit = PRODUCTS_PER_PAGE, page = currentPage)
            else fromCollection
        }
        products = result
        totalPages = if (result.size >= PRODUCTS_PER_PAGE) currentPage + 1 else currentPage
        totalPages = maxOf(1, totalPages)
        isLoading = false
    }

    val sortedProducts = remember(products, sortBy) { sortProducts(products, sortBy) }
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
                productCount = products.size,
                collectionTitle = title,
                sortBy = sortBy,
                sortLabel = currentSortLabel,
                onFilterClick = { filterDrawerVisible = true },
                onSortClick = { sortSheetVisible = true }
            )
            LazyVerticalGrid(
                modifier = Modifier.weight(1f),
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(sortedProducts) { product ->
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
            if (totalPages > 1) {
                PaginationDots(
                    totalPages = totalPages,
                    currentPage = currentPage,
                    onPageClick = { currentPage = it }
                )
            }
        }
    }

    if (filterDrawerVisible) {
        FilterDrawer(onDismiss = { filterDrawerVisible = false })
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun FilterDrawer(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Popup(
        onDismissRequest = onDismiss,
        alignment = Alignment.TopStart,
        properties = PopupProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(onClick = onDismiss)
            )
            AnimatedVisibility(
                visible = true,
                enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Column(
                    modifier = modifier
                        .width(280.dp)
                        .fillMaxHeight()
                        .background(Color.White)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            "Price",
                            style = MaterialTheme.typography.labelLarge,
                            color = EazColors.TextPrimary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Placeholder for price filter – client-side filtering can be added later
                            Text(
                                "Min",
                                style = MaterialTheme.typography.bodySmall,
                                color = EazColors.TextSecondary
                            )
                            Text("–", color = EazColors.TextSecondary)
                            Text(
                                "Max",
                                style = MaterialTheme.typography.bodySmall,
                                color = EazColors.TextSecondary
                            )
                        }
                        Text(
                            "Product filters coming soon.",
                            style = MaterialTheme.typography.bodySmall,
                            color = EazColors.TextSecondary,
                            modifier = Modifier.padding(top = 24.dp)
                        )
                    }
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(vertical = 16.dp),
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
    val images = product.images
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
                val url = images.getOrNull(currentIndex) ?: images.first()
                AnimatedContent(
                    targetState = url,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "productImage"
                ) { imgUrl ->
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imgUrl)
                            .crossfade(300)
                            .build(),
                        contentDescription = product.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )
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
