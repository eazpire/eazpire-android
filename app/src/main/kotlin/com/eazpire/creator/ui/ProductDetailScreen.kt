package com.eazpire.creator.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.annotation.SuppressLint
import android.text.Html
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyProductsApi
import com.eazpire.creator.api.ShopifyStorefrontCartApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.locale.LocaleStore
import com.eazpire.creator.cart.AppCartStore
import com.eazpire.creator.cart.StorefrontCartStore
import com.eazpire.creator.ui.footer.GlobalFooter
import com.eazpire.creator.ui.header.CheckoutDrawer
import com.eazpire.creator.ui.share.buildShareUrl
import com.eazpire.creator.ui.share.getActiveRefUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Same logic as web getMediaForColor (eaz-redesign-pdp.js): filter images by selected color
 * using alt prefix (e.g. "navy|front|back") or variant_ids fallback.
 */
private fun getMediaForColor(
    selectedColor: String,
    selectedVariant: ShopifyProductsApi.ProductDetail.ProductVariant?,
    allImages: List<ShopifyProductsApi.ProductImage>,
    variants: List<ShopifyProductsApi.ProductDetail.ProductVariant>
): List<String> {
    val fallback = allImages.take(5).map { it.src }
    if (selectedColor.isBlank() || variants.isEmpty() || allImages.isEmpty()) return fallback

    val variantForColor = variants.find { v ->
        v.option1.equals(selectedColor, true) || v.option2.equals(selectedColor, true) || v.option3.equals(selectedColor, true)
    } ?: selectedVariant ?: return fallback

    val featuredSrc = variantForColor.featuredImageSrc
    val selectedColorKey = normalizeColorKey(selectedColor)
    val anchorIndex = if (featuredSrc != null) allImages.indexOfFirst { it.src == featuredSrc } else -1

    // 1) Strict color-key matching from alt prefix (e.g. "navy|front|...")
    val matched = allImages.filter { img ->
        val key = getMediaAltColorKey(img)
        key.isNotBlank() && key == selectedColorKey
    }
    if (matched.size >= 2) return matched.map { it.src }.take(5)

    // 2) Fallback: contiguous images from anchor forward (same color key or no conflict)
    if (anchorIndex >= 0) {
        val contiguous = mutableListOf<ShopifyProductsApi.ProductImage>()
        for (i in anchorIndex until allImages.size) {
            val img = allImages[i]
            val key = getMediaAltColorKey(img)
            if (key.isNotBlank() && key != selectedColorKey) {
                if (contiguous.isNotEmpty()) break
                continue
            }
            contiguous.add(img)
            if (contiguous.size >= 5) break
        }
        if (contiguous.isNotEmpty()) return contiguous.map { it.src }
    }

    // 3) variant_ids fallback (when no alt-based matching)
    val vid = variantForColor.id
    if (vid != 0L) {
        val byVariant = allImages.filter { it.variantIds.isEmpty() || it.variantIds.contains(vid) }
        if (byVariant.isNotEmpty()) {
            val withFeat = if (featuredSrc != null && byVariant.none { it.src == featuredSrc })
                listOf(ShopifyProductsApi.ProductImage(featuredSrc, listOf(vid), null)) + byVariant
            else byVariant
            return withFeat.map { it.src }.take(5)
        }
    }

    return fallback.ifEmpty { featuredSrc?.let { listOf(it) } ?: emptyList() }
}

private fun normalizeColorKey(value: String): String =
    value.lowercase().replace(Regex("[_-]+"), " ").replace(Regex("\\s+"), " ").trim()

private fun getMediaAltColorKey(img: ShopifyProductsApi.ProductImage): String {
    val alt = (img.alt ?: "").lowercase()
    if (alt.isBlank()) return ""
    return normalizeColorKey(alt.split("|").firstOrNull() ?: "")
}

/** Split product title into design title + product type (like web eaz-pdp-main.liquid). */
private fun splitProductTitle(title: String, productType: String, productKey: String?): Pair<String, String> {
    val designTitle: String
    val productTypeTitle: String
    val normalized = title
        .replace(" — ", " | ")
        .replace(" – ", " | ")
        .replace(" - ", " | ")
    val parts = normalized.split(" | ")
    designTitle = parts.firstOrNull()?.trim()?.ifBlank { title } ?: title
    val productNameFromKey = when (productKey?.lowercase()) {
        "unisex-softstyle-cotton-tee" -> "Unisex Softstyle Cotton Tee"
        "womens-favorite-tee" -> "Women's Favorite Tee"
        "backprint-unisex-hooded-sweatshirt" -> "Unisex Hooded Sweatshirt"
        "unisex-crewneck-sweatshirt" -> "Unisex Crewneck Sweatshirt"
        "unisex-jersey-tank" -> "Unisex Jersey Tank"
        "tote-bag" -> "Tote Bag"
        "coffee-mug" -> "Coffee Mug"
        "color-changing-mug" -> "Color Changing Mug"
        "mouse-pad" -> "Mouse Pad"
        else -> productKey?.replace("-", " ")?.split(" ")?.joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }?.trim()
    }
    productTypeTitle = when {
        productNameFromKey != null && productNameFromKey.isNotBlank() -> productNameFromKey
        parts.size > 1 -> parts.drop(1).joinToString(" - ").trim()
        productType.isNotBlank() -> productType
        else -> ""
    }
    return designTitle to productTypeTitle
}

/**
 * Product Detail Screen – 1:1 wie Web Mobile PDP (eaz-pdp-main.liquid, eaz-redesign-pdp.css).
 * Layout: Info oben, Gallery, Mobile Options (Color/Size), Footer mit Qty + Add to Cart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    productHandle: String,
    onBack: () -> Unit,
    tokenStore: SecureTokenStore,
    showCloseButton: Boolean = false,
    modifier: Modifier = Modifier
) {
    val api = remember { ShopifyProductsApi() }
    val creatorApi = remember(tokenStore) { CreatorApi(jwt = tokenStore.getJwt()) }
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var product by remember { mutableStateOf<ShopifyProductsApi.ProductDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var detailsSheetVisible by remember { mutableStateOf(false) }
    var selectedImageIndex by remember { mutableIntStateOf(0) }
    var quantity by remember { mutableIntStateOf(1) }
    var showCartToast by remember { mutableStateOf(false) }
    var showFavoriteToast by remember { mutableStateOf(false) }
    var checkoutUrl by remember { mutableStateOf<String?>(null) }
    val storefrontCartStore = remember { StorefrontCartStore(context) }
    val storefrontCartApi = remember { ShopifyStorefrontCartApi() }
    val localeStore = remember { LocaleStore(context) }
    val countryCode by localeStore.countryCode.collectAsState(initial = localeStore.getCountryCodeSync())
    val accessToken = tokenStore.getAccessToken()

    LaunchedEffect(productHandle) {
        isLoading = true
        product = withContext(Dispatchers.IO) { api.getProductByHandle(productHandle) }
        isLoading = false
    }

    if (isLoading) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = EazColors.Orange)
        }
        return
    }

    val p = product
    if (p == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Product not found", color = EazColors.TextSecondary)
        }
        return
    }

    // Color/Size options – wie Web
    val colorOption = p.options.find { it.name.equals("Color", ignoreCase = true) || it.name.equals("Colour", ignoreCase = true) || it.name.equals("Farbe", ignoreCase = true) }
    val sizeOption = p.options.find { it.name.equals("Size", ignoreCase = true) || it.name.equals("Größe", ignoreCase = true) || it.name.equals("Groesse", ignoreCase = true) }
    var selectedColor by remember { mutableStateOf(colorOption?.values?.firstOrNull() ?: "") }
    var selectedSize by remember { mutableStateOf(sizeOption?.values?.firstOrNull() ?: "") }
    // Resolve variant by selected color/size
    val selectedVariant = remember(selectedColor, selectedSize, p.variants) {
        p.variants.find { v ->
            val matchColor = colorOption == null || v.option1.equals(selectedColor, true) || v.option2.equals(selectedColor, true) || v.option3.equals(selectedColor, true)
            val matchSize = sizeOption == null || v.option1.equals(selectedSize, true) || v.option2.equals(selectedSize, true) || v.option3.equals(selectedSize, true)
            matchColor && matchSize
        } ?: p.variants.firstOrNull()
    }
    val price = selectedVariant?.price ?: 0.0
    val comparePrice = selectedVariant?.compareAtPrice
    val available = selectedVariant?.available ?: true
    // Images for selected variant only – same logic as web getMediaForColor (eaz-redesign-pdp.js)
    val images = remember(selectedColor, selectedVariant, p.images, p.variants) {
        getMediaForColor(selectedColor, selectedVariant, p.images, p.variants)
    }
    LaunchedEffect(selectedVariant?.id) { selectedImageIndex = 0 }
    LaunchedEffect(showCartToast) { if (showCartToast) { kotlinx.coroutines.delay(1500); showCartToast = false } }
    LaunchedEffect(showFavoriteToast) { if (showFavoriteToast) { kotlinx.coroutines.delay(1500); showFavoriteToast = false } }

    Box(modifier = modifier.fillMaxSize()) {
        if (showCloseButton) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = EazColors.TextPrimary)
            }
        }
    Column(modifier = Modifier.fillMaxSize()) {
        // No back button – navigation via breadcrumb (Home / Collection); optional close for modal

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // pdp-info (order 1) – Brand, Title, Product Details btn, Subtitle
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                // Brand / Creator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF0F0F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            p.vendor.take(1).uppercase(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = EazColors.TextSecondary
                        )
                    }
                    Text(
                        p.vendor.ifBlank { "Creator" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = EazColors.TextPrimary
                    )
                }

                // Title row: Design Title + Product Details btn (like web pdp-title-row)
                val (designTitle, productTypeTitle) = remember(p.title, p.productType, p.productKey) {
                    splitProductTitle(p.title, p.productType, p.productKey)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            designTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = EazColors.TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (productTypeTitle.isNotBlank()) {
                            Text(
                                productTypeTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = EazColors.TextSecondary,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .clickable { detailsSheetVisible = true }
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = EazColors.TextPrimary, modifier = Modifier.size(14.dp))
                        Text("Product Details", style = MaterialTheme.typography.labelMedium, color = EazColors.TextPrimary)
                    }
                }

            }

            Spacer(modifier = Modifier.height(12.dp))

            // Gallery (order 2) – Thumbs + Main
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Thumbs (vertical)
                val imageCount = images.size
                if (imageCount > 1) {
                    Column(
                        modifier = Modifier
                            .width(52.dp)
                            .heightIn(max = 250.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        images.forEachIndexed { idx: Int, url: String ->
                            val thumbActive = idx == selectedImageIndex
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(2.dp, if (thumbActive) EazColors.Orange else Color.Transparent, RoundedCornerShape(6.dp))
                                    .clickable { selectedImageIndex = idx }
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(url).build(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
                // Main image
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF0F0F0))
                ) {
                    if (images.isNotEmpty()) {
                        val imgIdx = selectedImageIndex.coerceIn(0, (imageCount - 1).coerceAtLeast(0))
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(images[imgIdx]).build(),
                            contentDescription = p.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    // Dots
                    if (imageCount > 1) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            repeat(imageCount) { idx ->
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (idx == selectedImageIndex) Color.White
                                            else Color.White.copy(alpha = 0.5f)
                                        )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mobile options (order 3) – Color, Size, Stock
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                if (colorOption != null) {
                    Text(
                        "${colorOption.name} $selectedColor",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = EazColors.TextPrimary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        colorOption.values.forEach { value ->
                            val isActive = value.equals(selectedColor, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colorForName(value))
                                    .border(2.dp, if (isActive) EazColors.Orange else Color.Transparent, CircleShape)
                                    .clickable { selectedColor = value }
                            )
                        }
                    }
                }
                if (sizeOption != null) {
                    Text(
                        sizeOption.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = EazColors.TextPrimary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        sizeOption.values.forEach { value ->
                            val isActive = value.equals(selectedSize, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isActive) EazColors.TextPrimary
                                        else Color.White
                                    )
                                    .border(1.5.dp, if (isActive) EazColors.TextPrimary else Color(0xFFDDDDDD), RoundedCornerShape(8.dp))
                                    .clickable { selectedSize = value }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    value,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isActive) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                                    color = if (isActive) Color.White else EazColors.TextPrimary
                                )
                            }
                        }
                    }
                }
                // Stock
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (available) Color(0xFF16A34A) else Color(0xFFDC2626))
                    )
                    Text(
                        if (available) "In Stock" else "Out of Stock",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (available) Color(0xFF16A34A) else Color(0xFFDC2626)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // PDP Sub-Footer – 2 rows like web (Row 1: qty, fav, share, total | Row 2: price, delivery, cart, buy)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 12.dp)
        ) {
        // Row 1: Qty, Favorite, Share, Delivery, Total
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF5F5F5)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { if (quantity > 1) quantity-- }, modifier = Modifier.size(34.dp)) {
                    Text("−", style = MaterialTheme.typography.titleMedium, color = EazColors.TextPrimary)
                }
                Text("$quantity", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp), color = EazColors.TextPrimary)
                IconButton(onClick = { quantity++ }, modifier = Modifier.size(34.dp)) {
                    Text("+", style = MaterialTheme.typography.titleMedium, color = EazColors.TextPrimary)
                }
            }
            Box(modifier = Modifier.width(1.dp).height(20.dp).background(Color(0xFFE8E8E8)))
            IconButton(onClick = {
                val ownerId = tokenStore.getOwnerId()
                if (!ownerId.isNullOrBlank()) {
                    scope.launch {
                        try {
                            val resp = creatorApi.addFavorite(
                                customerId = ownerId!!,
                                productId = p.id.toString(),
                                variantId = selectedVariant?.id?.toString(),
                                productTitle = p.title,
                                productImage = images.firstOrNull()
                            )
                            if (resp.optBoolean("ok", false)) {
                                com.eazpire.creator.favorites.FavoritesRefreshTrigger.trigger()
                            }
                            showFavoriteToast = true
                        } catch (_: Exception) { showFavoriteToast = true }
                    }
                } else {
                    showFavoriteToast = true
                }
            }) {
                Icon(Icons.Default.Favorite, contentDescription = "Favorite", tint = EazColors.TextSecondary, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = {
                scope.launch {
                    val productPath = "/products/${p.handle}"
                    val urlToShare = tokenStore.getJwt()?.let { jwt ->
                        tokenStore.getOwnerId()?.let { ownerId ->
                            getActiveRefUrl(creatorApi, ownerId)?.let { refUrl ->
                                buildShareUrl(refUrl, productPath)
                            }
                        }
                    } ?: p.url
                    withContext(Dispatchers.Main) {
                        try {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, urlToShare)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share"))
                        } catch (_: Exception) {}
                    }
                }
            }) {
                Icon(Icons.Default.Share, contentDescription = "Share", tint = EazColors.TextSecondary, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
            Text("Total: CHF %.2f incl.".format(price), style = MaterialTheme.typography.labelSmall, color = EazColors.TextSecondary)
        }
        // Row 2: Price, Delivery, Cart, Buy now
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("CHF %.2f".format(price), style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = EazColors.TextPrimary)
            val deliveryDate = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMAN).format(java.util.Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000))
            Text("ca. $deliveryDate", style = MaterialTheme.typography.labelSmall, color = EazColors.TextSecondary)
            if (comparePrice != null && comparePrice > price) {
                Text("CHF %.2f".format(comparePrice), style = MaterialTheme.typography.labelSmall, color = EazColors.TextSecondary)
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFE5E7EB))
                    .clickable(enabled = available) {
                        val vid = selectedVariant?.id ?: return@clickable
                        scope.launch {
                            val cartId = storefrontCartStore.cartId
                            if (cartId != null) {
                                val result = withContext(Dispatchers.IO) {
                                    storefrontCartApi.addLine(cartId, vid, quantity, accessToken)
                                }
                                if (result.ok && result.cart != null) {
                                    storefrontCartStore.cartId = result.cart.cartId
                                    AppCartStore.setCount(result.cart.itemCount)
                                    showCartToast = true
                                }
                            } else {
                                val result = withContext(Dispatchers.IO) {
                                    storefrontCartApi.createCart(listOf(vid to quantity), accessToken, countryCode)
                                }
                                if (result.ok && result.cartId != null) {
                                    storefrontCartStore.cartId = result.cartId
                                    AppCartStore.setCount(quantity)
                                    showCartToast = true
                                }
                            }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = "Add to cart", tint = EazColors.TextPrimary, modifier = Modifier.size(20.dp))
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(EazColors.Orange)
                    .clickable(enabled = available) {
                        val vid = selectedVariant?.id ?: return@clickable
                        scope.launch {
                            val cartId = storefrontCartStore.cartId
                            val url = if (cartId != null) {
                                val result = withContext(Dispatchers.IO) {
                                    storefrontCartApi.addLine(cartId, vid, quantity, accessToken)
                                }
                                if (result.ok && result.cart != null) {
                                    storefrontCartStore.cartId = result.cart.cartId
                                    AppCartStore.setCount(result.cart.itemCount)
                                    result.cart.checkoutUrl
                                } else null
                            } else {
                                val result = withContext(Dispatchers.IO) {
                                    storefrontCartApi.createCart(listOf(vid to quantity), accessToken, countryCode)
                                }
                                if (result.ok && result.cartId != null && result.checkoutUrl != null) {
                                    storefrontCartStore.cartId = result.cartId
                                    AppCartStore.setCount(quantity)
                                    result.checkoutUrl
                                } else null
                            }
                            url?.let { checkoutUrl = it }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text("Buy now", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
        }

        // Main Footer – ganz unten (wie Web)
        GlobalFooter()
    }

        // Toast overlays – mittig
        AnimatedVisibility(
            visible = showCartToast,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Text("Added to cart", style = MaterialTheme.typography.bodyLarge, color = Color.White)
                }
            }
        }
        AnimatedVisibility(
            visible = showFavoriteToast,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = EazColors.Orange, modifier = Modifier.size(24.dp))
                    Text(
                        if (tokenStore.getOwnerId()?.isNotBlank() == true) "Added to favorites" else "Login to save favorites",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                }
            }
        }
    }

    // Product Details Modal – von unten nach oben, Header + X, Tabs
    if (detailsSheetVisible) {
        val sections = remember(p.bodyHtml) { parseDescriptionSections(p.bodyHtml) }
        var selectedTabIndex by remember { mutableIntStateOf(0) }

        ModalBottomSheet(
            onDismissRequest = { detailsSheetVisible = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(520.dp)
            ) {
                // Header + X
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Product Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = EazColors.TextPrimary
                    )
                    IconButton(onClick = { detailsSheetVisible = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = EazColors.TextPrimary)
                    }
                }
                Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(Color(0xFFE8E8E8)))
                // Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .background(Color.White)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    sections.forEachIndexed { idx, (title, _) ->
                        val isSelected = idx == selectedTabIndex
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) EazColors.Orange else Color(0xFFF5F5F5))
                                .clickable { selectedTabIndex = idx }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                title,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (isSelected) Color.White else EazColors.TextPrimary,
                                fontWeight = if (isSelected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal
                            )
                        }
                    }
                }
                Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(Color(0xFFF0F0F0)))
                // Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                    val (_, content) = sections.getOrNull(selectedTabIndex) ?: ("" to "")
                    if (content.isNotBlank()) {
                        val isTableContent = content.contains("<table", ignoreCase = true)
                        if (isTableContent) {
                            HtmlWebView(content = content, modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp))
                        } else {
                            AndroidView(
                                factory = { ctx ->
                                    TextView(ctx).apply {
                                        setTextColor(android.graphics.Color.parseColor("#1A1A1A"))
                                        textSize = 14f
                                        setLineSpacing(4f, 1.2f)
                                    }
                                },
                                update = { tv ->
                                    @Suppress("DEPRECATION")
                                    tv.text = Html.fromHtml(content)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        Text(
                            "Not available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EazColors.TextSecondary
                        )
                    }
                }
            }
        }
    }

    // Checkout as right-side drawer (like Cart)
    val url = checkoutUrl
    if (url != null) {
        CheckoutDrawer(
            visible = true,
            checkoutUrl = url,
            onDismiss = { checkoutUrl = null }
        )
    }
}

/** Parse body_html into sections by headings: Design Detail, Product Features, Care Instructions, Size Table, GPSR */
private fun parseDescriptionSections(bodyHtml: String): List<Pair<String, String>> {
    val raw = bodyHtml.trim()
    val tabOrder = listOf("Design Detail", "Product Features", "Care Instructions", "Size Table", "GPSR")
    val keyToTitle = mapOf(
        "design detail" to "Design Detail",
        "product features" to "Product Features",
        "care instructions" to "Care Instructions",
        "size table" to "Size Table",
        "gpsr" to "GPSR"
    )
    if (raw.isBlank()) return tabOrder.map { it to "Not available" }
    val sectionPattern = Regex("(?i)<p>\\s*<strong>\\s*([^:]+):\\s*</strong>\\s*</p>\\s*")
    val matches = sectionPattern.findAll(raw)
    val parsed = mutableMapOf<String, String>()
    for (m in matches) {
        val key = m.groupValues[1].trim().lowercase().replace(":", "")
        val displayTitle = keyToTitle[key] ?: m.groupValues[1].trim()
        val contentStart = m.range.last + 1
        val nextMatch = sectionPattern.find(raw, contentStart)
        val contentEnd = nextMatch?.range?.first ?: raw.length
        val content = raw.substring(contentStart, contentEnd).trim()
        parsed[displayTitle] = content
    }
    val fallback = if (parsed.isEmpty()) raw.replace(Regex("<[^>]+>"), " ").trim().ifBlank { "Not available" } else null
    return tabOrder.mapIndexed { i, title ->
        val content = parsed[title]?.takeIf { it.isNotBlank() }
            ?: if (i == 0 && fallback != null) fallback else "Not available"
        title to content
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlWebView(content: String, modifier: Modifier = Modifier) {
    val htmlWithStyle = """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <style>
        body { font-family: sans-serif; font-size: 14px; color: #1A1A1A; line-height: 1.4; margin: 0; padding: 0; }
        table { width: 100%; border-collapse: collapse; }
        th, td { border: 1px solid #ddd; padding: 8px 10px; text-align: left; }
        th { background: #f5f5f5; font-weight: 600; }
        </style>
        </head>
        <body>$content</body>
        </html>
    """.trimIndent()
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.evaluateJavascript("document.body.offsetHeight") { }
                    }
                }
                loadDataWithBaseURL(null, htmlWithStyle, "text/html", "UTF-8", null)
            }
        },
        modifier = modifier
    )
}

private fun colorForName(name: String): Color {
    val n = name.trim().lowercase()
    return when (n) {
        "white" -> Color(0xFFFFFFFF)
        "black" -> Color(0xFF111111)
        "navy" -> Color(0xFF0B1F3A)
        "red" -> Color(0xFFD11A2A)
        "purple" -> Color(0xFF6B21A8)
        "sport grey", "sport-grey" -> Color(0xFF9CA3AF)
        "dark heather", "dark-heather" -> Color(0xFF4B5563)
        "military green" -> Color(0xFF4B5D3A)
        "natural" -> Color(0xFFEFE7D6)
        "sand" -> Color(0xFFD8C7A0)
        "daisy" -> Color(0xFFF4D000)
        "light blue" -> Color(0xFF7FB7FF)
        "tropical blue" -> Color(0xFF00A3D7)
        "dark chocolate" -> Color(0xFF3A2618)
        "heather navy" -> Color(0xFF2B3A55)
        else -> Color(0xFFD1D5DB)
    }
}
