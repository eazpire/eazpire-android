package com.eazpire.creator.ui

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.LocalTranslationStore
import com.eazpire.creator.locale.LocaleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val imageUrl: String?,
    val price: String?,
    val priceAmount: Double? = null,
    val createdAtMs: Long? = null,
    val productType: String? = null
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
    var products by remember(creatorName) { mutableStateOf<List<CreatorShopProduct>>(emptyList()) }
    var sortBy by remember(creatorName) { mutableStateOf("date-desc") }
    var filterQuery by remember(creatorName) { mutableStateOf("") }
    var showSortSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    LaunchedEffect(creatorName, countryCode, catalogRegion) {
        loading = true
        error = null
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
            val ownerId = profileJson.optString("owner_id", "").trim().ifBlank { null }
            val coverUrl = coverObj?.optString("image_url", "")?.trim()?.ifBlank { null }
                ?: coverObj?.optJSONArray("cover_rotation_slides")?.optJSONObject(0)
                    ?.optString("image_url", "")?.trim()?.ifBlank { null }

            val productsJson = withContext(Dispatchers.IO) {
                api.getCreatorShopProducts(
                    creatorName = resolvedName,
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
            products = list
            profile = CreatorProfilePreview(
                name = resolvedName,
                avatarUrl = avatarObj?.optString("image_url", "")?.trim()?.ifBlank { null },
                coverUrl = coverUrl,
                ratingAvg = ratingObj?.optDouble("avg")?.takeIf { it > 0.0 }
                    ?: ratingObj?.optDouble("rating")?.takeIf { it > 0.0 },
                ratingCount = ratingObj?.optInt("count")?.takeIf { it > 0 }
                    ?: ratingObj?.optInt("rating_count")?.takeIf { it > 0 },
                productCount = list.size
            )
        } catch (e: Exception) {
            error = e.message ?: "error"
        } finally {
            loading = false
        }
    }

    val filteredSortedProducts by remember(products, sortBy, filterQuery) {
        derivedStateOf {
            val q = filterQuery.trim().lowercase(Locale.ROOT)
            val filtered = if (q.isBlank()) products
            else products.filter { it.title.lowercase(Locale.ROOT).contains(q) || it.handle.contains(q) }
            sortCreatorProducts(filtered, sortBy)
        }
    }

    val sortLabel = CREATOR_PROFILE_SORT_OPTIONS.find { it.value == sortBy }?.label ?: "Date, new to old"

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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        CreatorProfileHero(
                            name = p?.name ?: creatorName,
                            avatarUrl = p?.avatarUrl,
                            coverUrl = p?.coverUrl,
                            ratingAvg = p?.ratingAvg,
                            ratingCount = p?.ratingCount,
                            productCount = p?.productCount ?: products.size,
                            t = t
                        )
                    }
                    stickyHeader {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = t("eaz.sidebar.nav_home", "Home"),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EazColors.Orange,
                                    modifier = Modifier.clickable(onClick = onBack)
                                )
                                Text(
                                    text = " > ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EazColors.TextSecondary
                                )
                                Text(
                                    text = p?.name ?: creatorName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EazColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            CollectionResultsBar(
                                filteredCount = filteredSortedProducts.size,
                                totalCount = products.size,
                                sortBy = sortBy,
                                sortLabel = sortLabel,
                                t = t,
                                onFilterClick = { showFilterSheet = true },
                                onSortClick = { showSortSheet = true }
                            )
                        }
                    }
                    if (filteredSortedProducts.isEmpty()) {
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
                                        modifier = Modifier.weight(1f),
                                        onClick = { onProductClick(item.handle) }
                                    )
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
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

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    t("collection.filter", "Filter"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(t("collection.search_within", "Search within results")) },
                    singleLine = true
                )
            }
        }
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
    t: (String, String) -> String
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
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Bottom
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
                        text = t("eaz.creator_profile.products_count", "{{ count }} products")
                            .replace("{{ count }}", productCount.toString()),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (ratingAvg != null && ratingCount != null) {
                    Spacer(Modifier.height(6.dp))
                    CreatorHeroRatingRow(avg = ratingAvg, count = ratingCount)
                }
            }
        }
    }
}

@Composable
private fun CreatorHeroRatingRow(avg: Double, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (designTitle, productType) = splitCreatorProductTitle(product.title, product.productType)
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        AsyncImage(
            model = product.imageUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFEFEFEF)),
            contentScale = ContentScale.Crop
        )
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
        if (productType.isNotBlank()) {
            Text(
                text = productType,
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
    return CreatorShopProduct(
        handle = handle,
        title = title,
        imageUrl = run {
            val preview = o.optString("preview_image_url", "").trim()
            if (preview.isNotBlank()) preview
            else o.optJSONArray("images")?.optJSONObject(0)?.optString("src", "")?.trim()?.ifBlank { null }
                ?: o.optString("image", "").trim().ifBlank { null }
        },
        price = priceRaw.ifBlank { null },
        priceAmount = priceAmount,
        createdAtMs = createdAtMs,
        productType = o.optString("product_type", "").trim().ifBlank { null }
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

private fun splitCreatorProductTitle(title: String, productType: String?): Pair<String, String> {
    val normalized = title
        .replace(" — ", " | ")
        .replace(" – ", " | ")
        .replace(" - ", " | ")
    val parts = normalized.split(" | ").map { it.trim() }.filter { it.isNotBlank() }
    val design = parts.firstOrNull() ?: title
    val type = productType?.trim().orEmpty().ifBlank {
        parts.drop(1).joinToString(" - ")
    }
    return design to type
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
