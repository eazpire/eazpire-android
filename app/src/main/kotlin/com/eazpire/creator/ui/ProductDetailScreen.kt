package com.eazpire.creator.ui

import android.content.Intent
import android.net.Uri
import java.net.URLEncoder
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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import android.annotation.SuppressLint
import android.widget.Toast
import android.text.Html
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.EazColors
import com.eazpire.creator.i18n.LocalTranslationStore
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyProductsApi
import com.eazpire.creator.api.ShopifyStorefrontCartApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.locale.LocaleStore
import com.eazpire.creator.cart.AppCartStore
import com.eazpire.creator.cart.StorefrontCartStore
import com.eazpire.creator.favorites.FavoritesRefreshTrigger
import com.eazpire.creator.ui.footer.GlobalFooter
import com.eazpire.creator.ui.header.CheckoutDrawer
import com.eazpire.creator.ui.share.buildShareUrl
import com.eazpire.creator.ui.share.getActiveRefUrl
import com.eazpire.creator.ui.components.HangerIcon
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.eazpire.creator.pricing.QuantityDiscount
import com.eazpire.creator.util.SizeAiProductTypeMapper
import com.eazpire.creator.util.matchShopifySizeOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Same logic as web getMediaForVariant (eaz-redesign-pdp.js): filter images by selected color
 * using alt prefix (e.g. "white|front|back") from variant featured media.
 */
private fun getMediaViewSortScore(img: ShopifyProductsApi.ProductImage): Int {
    val alt = (img.alt ?: "").lowercase()
    if (alt.isBlank() || !alt.contains("|")) return 50
    val parts = alt.split("|")
    val view = parts.getOrNull(1)?.trim().orEmpty()
    val isPreview = parts.size >= 3 && parts[2].trim().lowercase() == "preview-default"
    if (isPreview) return 0
    if (view == "front" || view.startsWith("front")) return 10
    if (view == "back") return 20
    if (view.startsWith("detail")) return 30
    if (view.startsWith("folded")) return 90
    return 40
}

private fun sortMediaForColorGallery(
    images: List<ShopifyProductsApi.ProductImage>,
    featuredSrc: String?
): List<ShopifyProductsApi.ProductImage> {
    if (images.size < 2) return images
    val sorted = images.sortedBy { getMediaViewSortScore(it) }.toMutableList()
    if (featuredSrc != null) {
        val idx = sorted.indexOfFirst { it.src == featuredSrc }
        if (idx > 0) {
            val featured = sorted.removeAt(idx)
            sorted.add(0, featured)
        }
    }
    return sorted
}

private fun getMediaForColor(
    selectedColor: String,
    selectedVariant: ShopifyProductsApi.ProductDetail.ProductVariant?,
    allImages: List<ShopifyProductsApi.ProductImage>,
    variants: List<ShopifyProductsApi.ProductDetail.ProductVariant>
): List<String> {
    val fallback = allImages.take(5).map { it.src }
    if (variants.isEmpty() || allImages.isEmpty()) return fallback

    val variantForColor = variants.find { v ->
        v.option1.equals(selectedColor, true) || v.option2.equals(selectedColor, true) || v.option3.equals(selectedColor, true)
    } ?: selectedVariant ?: return fallback

    val featuredSrc = variantForColor.featuredImageSrc
    val featuredImage = featuredSrc?.let { src -> allImages.find { it.src == src } }
    val selectedColorKey = when {
        featuredImage != null -> getMediaAltColorKey(featuredImage).ifBlank { normalizeColorKey(selectedColor) }
        else -> normalizeColorKey(selectedColor)
    }
    val anchorIndex = if (featuredSrc != null) allImages.indexOfFirst { it.src == featuredSrc } else -1

    // 1) Strict color-key matching from alt prefix (e.g. "white|front|...")
    val matched = allImages.filter { img ->
        val key = getMediaAltColorKey(img)
        key.isNotBlank() && key == selectedColorKey
    }
    if (matched.isNotEmpty()) {
        return sortMediaForColorGallery(matched, featuredSrc).map { it.src }.take(5)
    }

    // 2) Contiguous images from anchor forward (same color key or no conflict)
    if (anchorIndex >= 0) {
        val contiguous = mutableListOf<ShopifyProductsApi.ProductImage>()
        for (i in anchorIndex until allImages.size) {
            val img = allImages[i]
            val key = getMediaAltColorKey(img)
            if (selectedColorKey.isNotBlank() && key.isNotBlank() && key != selectedColorKey) {
                if (contiguous.isNotEmpty()) break
                continue
            }
            contiguous.add(img)
            if (contiguous.size >= 5) break
        }
        if (contiguous.isNotEmpty()) {
            return sortMediaForColorGallery(contiguous, featuredSrc).map { it.src }
        }
    }

    // 3) variant_ids fallback — only images tied to this variant and matching color alt when present
    val vid = variantForColor.id
    if (vid != 0L) {
        val byVariant = allImages.filter { img ->
            val key = getMediaAltColorKey(img)
            if (selectedColorKey.isNotBlank() && key.isNotBlank() && key != selectedColorKey) return@filter false
            img.variantIds.contains(vid)
        }
        if (byVariant.isNotEmpty()) {
            return sortMediaForColorGallery(byVariant, featuredSrc).map { it.src }.take(5)
        }
    }

    return featuredSrc?.let { listOf(it) } ?: fallback.ifEmpty { emptyList() }
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

private fun normalizeDesignKey(title: String): String {
    val t = title.replace(" — ", " | ").replace(" – ", " | ").replace(" - ", " | ")
    return t.split(" | ").firstOrNull()?.trim()?.lowercase() ?: title.trim().lowercase()
}

private fun creatorKeyItem(it: ShopifyProductsApi.ProductItem): String =
    it.creator.ifBlank { it.vendor }.trim().lowercase()

private fun buildPdpCarouselSameType(
    p: ShopifyProductsApi.ProductDetail,
    catalog: List<ShopifyProductsApi.ProductItem>
): List<ShopifyProductsApi.ProductItem> {
    val pk = p.productKey?.takeIf { it.isNotBlank() }.orEmpty()
    val seedDesign = normalizeDesignKey(p.title)
    val ck = p.creatorDisplay.trim().lowercase()
    return catalog.filter { o ->
        if (o.id == p.id) return@filter false
        if (creatorKeyItem(o) != ck) return@filter false
        val typeMatch = if (pk.isNotBlank()) o.metaProductKey == pk
        else o.productType.isNotBlank() && o.productType == p.productType
        typeMatch && normalizeDesignKey(o.title) != seedDesign
    }.take(50)
}

private fun buildPdpCarouselSameDesign(
    p: ShopifyProductsApi.ProductDetail,
    catalog: List<ShopifyProductsApi.ProductItem>
): List<ShopifyProductsApi.ProductItem> {
    val pk = p.productKey?.takeIf { it.isNotBlank() }.orEmpty()
    val seedDesign = normalizeDesignKey(p.title)
    val ck = p.creatorDisplay.trim().lowercase()
    val seedDid = p.designIdMeta?.takeIf { it.isNotBlank() }
    return catalog.filter { o ->
        if (o.id == p.id) return@filter false
        if (creatorKeyItem(o) != ck) return@filter false
        val designMatch = if (seedDid != null && o.designId.isNotBlank()) o.designId == seedDid
        else normalizeDesignKey(o.title) == seedDesign
        val diffType = when {
            pk.isNotBlank() -> o.metaProductKey != pk
            else -> o.productType.isNotBlank() && o.productType != p.productType
        }
        designMatch && diffType
    }.take(50)
}

/**
 * Mirrors web [eaz-pdp-recommendations-grid]: weighted "You may also like" picks.
 */
private fun buildYouMayAlsoLike(
    p: ShopifyProductsApi.ProductDetail,
    catalog: List<ShopifyProductsApi.ProductItem>
): List<ShopifyProductsApi.ProductItem> {
    val seedTitle = p.title.lowercase()
        .replace("-", " ").replace("_", " ").replace(".", " ")
        .replace(",", " ").replace("/", " ").replace("(", " ").replace(")", " ")
    val tokens = seedTitle.split(Regex("\\s+")).filter { it.length >= 4 }.take(8)
    val maxItems = 6
    val buckets = Array(6) { mutableListOf<ShopifyProductsApi.ProductItem>() }
    for (o in catalog) {
        if (o.id == p.id) continue
        val isTypeMatch = p.productType.isNotBlank() && o.productType == p.productType
        val isVendorMatch = p.vendor.isNotBlank() && o.vendor == p.vendor
        val candidateTitle = o.title.lowercase()
        val sharesKeyword = tokens.any { t -> candidateTitle.contains(t) }
        when {
            isTypeMatch && isVendorMatch && sharesKeyword -> buckets[0].add(o)
            isTypeMatch && sharesKeyword -> buckets[1].add(o)
            isVendorMatch && sharesKeyword -> buckets[2].add(o)
            isTypeMatch || isVendorMatch -> buckets[3].add(o)
            sharesKeyword -> buckets[4].add(o)
            else -> buckets[5].add(o)
        }
    }
    val out = mutableListOf<ShopifyProductsApi.ProductItem>()
    for (b in buckets) {
        for (item in b) {
            if (out.none { it.id == item.id }) {
                out.add(item)
                if (out.size >= maxItems) return out
            }
        }
    }
    return out
}

private fun formatPdpPromoDuration(ms: Long): String {
    if (ms <= 0L) return ""
    var s = ms / 1000L
    val d = s / 86400L
    s %= 86400L
    val h = s / 3600L
    s %= 3600L
    val m = s / 60L
    val sec = s % 60L
    return when {
        d > 0L -> "${d}d ${h}h"
        h > 0L -> "${h}h ${m}m ${sec}s"
        else -> "${m}m ${sec}s"
    }
}

@Composable
private fun PdpPromoCountdownLine(endsAtMs: Long, prefix: String, endedLabel: String) {
    var now by remember(endsAtMs) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAtMs) {
        while (now < endsAtMs) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val left = endsAtMs - now
    val label = if (left <= 0L) endedLabel else "$prefix ${formatPdpPromoDuration(left)}"
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = EazColors.Orange,
        modifier = Modifier.padding(top = 2.dp)
    )
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
    onTermsClick: (() -> Unit)? = null,
    onNavigateToProduct: ((String) -> Unit)? = null,
    onNavigateToCreator: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val api = remember { ShopifyProductsApi() }
    val creatorApi = remember(tokenStore) { CreatorApi(jwt = tokenStore.getJwt()) }
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var product by remember(productHandle) { mutableStateOf<ShopifyProductsApi.ProductDetail?>(null) }
    var catalogProducts by remember { mutableStateOf<List<ShopifyProductsApi.ProductItem>>(emptyList()) }
    var isLoading by remember(productHandle) { mutableStateOf(true) }
    var detailsSheetVisible by remember { mutableStateOf(false) }
    var reviewsSheetVisible by remember { mutableStateOf(false) }
    var selectedImageIndex by remember { mutableIntStateOf(0) }
    var quantity by remember { mutableIntStateOf(1) }
    var showQuantityDiscountModal by remember { mutableStateOf(false) }
    var showCartToast by remember { mutableStateOf(false) }
    var showFavoriteToast by remember { mutableStateOf(false) }
    var showCartPlusOne by remember { mutableStateOf(false) }
    var showFavoritePlusOne by remember { mutableStateOf(false) }
    var checkoutUrl by remember { mutableStateOf<String?>(null) }
    val storefrontCartStore = remember { StorefrontCartStore(context) }
    val storefrontCartApi = remember { ShopifyStorefrontCartApi() }
    val localeStore = remember { LocaleStore(context) }
    val countryCode by localeStore.countryCode.collectAsState(initial = localeStore.getCountryCodeSync())
    val accessToken = tokenStore.getAccessToken()

    var promoOverlay by remember(productHandle) { mutableStateOf<ShopifyProductsApi.ProductItem?>(null) }
    var mockupTryOnInfo by remember(productHandle) { mutableStateOf<MockupTryOnInfo?>(null) }
    var productColorHexMap by remember(productHandle) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var tryOnActive by remember(productHandle) { mutableStateOf(false) }
    var tryOnImageUrl by remember(productHandle) { mutableStateOf<String?>(null) }
    var tryOnLoading by remember(productHandle) { mutableStateOf(false) }
    var tryOnOverlayMessage by remember(productHandle) { mutableStateOf<String?>(null) }

    LaunchedEffect(productHandle) {
        isLoading = true
        product = withContext(Dispatchers.IO) { api.getProductByHandle(productHandle) }
        catalogProducts = withContext(Dispatchers.IO) { api.getProductsWithFullMetafields(250).products }
        isLoading = false
    }

    LaunchedEffect(productHandle, product?.productKey, product?.designIdMeta) {
        mockupTryOnInfo = null
        productColorHexMap = emptyMap()
        tryOnActive = false
        tryOnImageUrl = null
        tryOnLoading = false
        val ownerId = tokenStore.getOwnerId()?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val prod = product ?: return@LaunchedEffect
        if (!isTryOnApparelProduct(prod.productKey)) return@LaunchedEffect
        try {
            val pk = prod.productKey?.takeIf { it.isNotBlank() }
            if (pk != null) {
                val colorsResp = withContext(Dispatchers.IO) {
                    creatorApi.getColorVariants(pk)
                }
                if (colorsResp.optBoolean("ok", false)) {
                    productColorHexMap = parseProductColorHexMap(colorsResp)
                }
            }
            val map = withContext(Dispatchers.IO) {
                creatorApi.getCustomerMockupMap(ownerId, productHandle)
            }
            mockupTryOnInfo = parseMockupTryOnInfo(
                data = map,
                handle = productHandle,
                productKeyMeta = prod.productKey,
                designIdMeta = prod.designIdMeta
            )
            val wearing = mockupTryOnInfo?.let { info ->
                val pk = info.productKey
                val entry = map.optJSONObject("mockups")?.optJSONObject(pk)
                entry?.optBoolean("use_as_preview", false) == true ||
                    entry?.optInt("use_as_preview", 0) == 1
            } == true
            val sessionTryOn = com.eazpire.creator.mockup.CustomerMockPreviewStore
                .isTryOnSessionActive(context, productHandle)
            if (mockupTryOnInfo != null && (wearing || sessionTryOn)) {
                tryOnActive = true
            }
        } catch (_: Exception) {
            mockupTryOnInfo = null
        }
    }

    LaunchedEffect(productHandle, countryCode) {
        promoOverlay = withContext(Dispatchers.IO) {
            try {
                val j = creatorApi.listActiveShopPromotionProducts(localeStore.getCountryCodeSync())
                ShopifyProductsApi.parseActivePromotionProductsResponse(j).find { it.handle == productHandle }
            } catch (_: Exception) {
                null
            }
        }
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
    val store = LocalTranslationStore.current
    val tr = store?.translations?.collectAsState(initial = emptyMap())?.value
    val t = store?.let { { k: String, d: String -> it.t(k, d) } } ?: { _: String, d: String -> d }
    if (p == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(t("product.not_found", "Product not found"), color = EazColors.TextSecondary)
        }
        return
    }

    // Color/Size options – wie Web
    val colorOption = p.options.find { it.name.equals("Color", ignoreCase = true) || it.name.equals("Colour", ignoreCase = true) || it.name.equals("Farbe", ignoreCase = true) }
    val sizeOption = p.options.find { it.name.equals("Size", ignoreCase = true) || it.name.equals("Größe", ignoreCase = true) || it.name.equals("Groesse", ignoreCase = true) }
    var selectedColor by remember { mutableStateOf(colorOption?.values?.firstOrNull() ?: "") }
    var selectedSize by remember { mutableStateOf(sizeOption?.values?.firstOrNull() ?: "") }
    var userOverrodeSize by remember(productHandle) { mutableStateOf(false) }
    var sizeAiHint by remember(productHandle) { mutableStateOf<String?>(null) }

    LaunchedEffect(productHandle) {
        userOverrodeSize = false
        sizeAiHint = null
    }

    LaunchedEffect(
        p.id,
        productHandle,
        sizeOption?.values?.joinToString(),
        userOverrodeSize
    ) {
        if (userOverrodeSize) return@LaunchedEffect
        val ownerId = tokenStore.getOwnerId()?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val opt = sizeOption ?: return@LaunchedEffect
        val typeKey = SizeAiProductTypeMapper.resolve(p.productKey, p.productType) ?: return@LaunchedEffect
        try {
            val resp = withContext(Dispatchers.IO) {
                creatorApi.getSizeRecommendations(ownerId, typeKey)
            }
            if (!resp.optBoolean("ok", false)) return@LaunchedEffect
            val rec = resp.optJSONObject("recommendation") ?: return@LaunchedEffect
            val sizeStr = rec.optString("size").takeIf { it.isNotBlank() } ?: return@LaunchedEffect
            val matched = matchShopifySizeOption(opt.values, sizeStr) ?: return@LaunchedEffect
            selectedSize = matched
            sizeAiHint = "Recommended by Size AI"
        } catch (_: Exception) {
        }
    }

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
    val lineEstimate = remember(price, quantity) { QuantityDiscount.estimateLineTotals(price, quantity) }
    val unitPriceAfterDiscount = if (quantity > 0) lineEstimate.afterDiscount / quantity else price
    /** Storefront cart needs a real Shopify variant id (>0). */
    val variantIdForCart = (selectedVariant?.id ?: 0L).takeIf { it > 0L }
    // Images for selected variant only – same logic as web getMediaForColor (eaz-redesign-pdp.js)
    val images = remember(selectedColor, selectedVariant, p.images, p.variants) {
        getMediaForColor(selectedColor, selectedVariant, p.images, p.variants)
    }
    LaunchedEffect(selectedColor, selectedVariant?.id) {
        selectedImageIndex = 0
    }

    LaunchedEffect(tryOnActive, selectedColor, mockupTryOnInfo, productColorHexMap) {
        if (!tryOnActive) {
            tryOnImageUrl = null
            tryOnLoading = false
            if (tryOnOverlayMessage != null) {
                kotlinx.coroutines.delay(280)
            }
            tryOnOverlayMessage = null
            return@LaunchedEffect
        }
        val info = mockupTryOnInfo ?: run {
            tryOnActive = false
            tryOnOverlayMessage = null
            return@LaunchedEffect
        }
        val ownerId = tokenStore.getOwnerId()?.takeIf { it.isNotBlank() } ?: run {
            tryOnActive = false
            tryOnOverlayMessage = null
            return@LaunchedEffect
        }
        if (tryOnOverlayMessage.isNullOrBlank()) {
            tryOnOverlayMessage = t("eaz.pdp.try_on_overlay_on", "Putting on your look…")
        }
        tryOnLoading = true
        val url = resolveMockupImageUrl(info, selectedColor, ownerId, productColorHexMap)
        tryOnLoading = false
        tryOnOverlayMessage = null
        if (url.isNullOrBlank()) {
            tryOnActive = false
            tryOnImageUrl = null
        } else {
            tryOnImageUrl = url
        }
    }
    LaunchedEffect(showCartToast) { if (showCartToast) { kotlinx.coroutines.delay(1500); showCartToast = false } }
    LaunchedEffect(showFavoriteToast) { if (showFavoriteToast) { kotlinx.coroutines.delay(1500); showFavoriteToast = false } }
    LaunchedEffect(showCartPlusOne) {
        if (showCartPlusOne) {
            kotlinx.coroutines.delay(900)
            showCartPlusOne = false
        }
    }
    LaunchedEffect(showFavoritePlusOne) {
        if (showFavoritePlusOne) {
            kotlinx.coroutines.delay(900)
            showFavoritePlusOne = false
        }
    }

    val creatorLabel = p.creatorDisplay.ifBlank { p.vendor }.ifBlank { "Creator" }
    var creatorPreview by remember(p.creatorDisplay, p.vendor) { mutableStateOf<CreatorProfilePreview?>(null) }
    LaunchedEffect(creatorLabel) {
        if (creatorLabel.isBlank() || creatorLabel == "Creator") {
            creatorPreview = null
            return@LaunchedEffect
        }
        try {
            val data = withContext(Dispatchers.IO) {
                creatorApi.getCreatorProfile(creatorName = creatorLabel, creatorSlug = creatorLabel)
            }
            if (data.optBoolean("ok", false)) {
                val ratingObj = data.optJSONObject("rating")
                val avatarObj = data.optJSONObject("avatar")
                creatorPreview = CreatorProfilePreview(
                    name = data.optString("creator_name", creatorLabel).ifBlank { creatorLabel },
                    avatarUrl = avatarObj?.optString("image_url", "")?.trim()?.ifBlank { null },
                    ratingAvg = ratingObj?.optDouble("avg")?.takeIf { it > 0.0 }
                        ?: ratingObj?.optDouble("rating")?.takeIf { it > 0.0 },
                    ratingCount = ratingObj?.optInt("count")?.takeIf { it > 0 }
                        ?: ratingObj?.optInt("rating_count")?.takeIf { it > 0 }
                )
            }
        } catch (_: Exception) {
            creatorPreview = null
        }
    }
    val openCreatorProfile: () -> Unit = {
        if (onNavigateToCreator != null) {
            onNavigateToCreator.invoke(creatorPreview?.name ?: creatorLabel)
        } else {
            try {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.eazpire.com/creator/${creatorLabel.lowercase().replace(' ', '-')}")
                    )
                )
            } catch (_: Exception) {
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        // No back button – navigation via breadcrumb (Home / Collection); optional close for modal

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // pdp-info (order 1) – Brand, Title, Product Details btn, Subtitle
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                // Brand / Creator — logo + name, links to creator shop page
                val brandLabel = creatorPreview?.name ?: creatorLabel
                if (brandLabel.isNotBlank() && brandLabel != "Creator" && brandLabel != "?") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = openCreatorProfile)
                            .padding(bottom = 4.dp)
                    ) {
                        CreatorAvatarLogo(
                            name = brandLabel,
                            avatarUrl = creatorPreview?.avatarUrl,
                            size = 36.dp,
                            cornerRadius = 9.dp,
                            onClick = openCreatorProfile
                        )
                        Text(
                            brandLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = EazColors.TextPrimary,
                            modifier = Modifier.clickable(onClick = openCreatorProfile)
                        )
                    }
                }

                // Title row: design title + Product Details btn (like web pdp-title-row)
                val (designTitle, productTypeTitle) = remember(p.title, p.productType, p.productKey) {
                    splitProductTitle(p.title, p.productType, p.productKey)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        designTitle,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = EazColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(100.dp))
                            .clickable { detailsSheetVisible = true }
                            .padding(horizontal = 18.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = EazColors.TextPrimary, modifier = Modifier.size(14.dp))
                        Text(t("product.details", "Product Details"), style = MaterialTheme.typography.labelMedium, color = EazColors.TextPrimary)
                    }
                }
                if (productTypeTitle.isNotBlank()) {
                    Text(
                        productTypeTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = EazColors.TextSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                val ratingAvg = p.ratingAvg
                val ratingCount = p.ratingCount ?: 0
                if (ratingAvg != null && ratingCount > 0) {
                    val reviewsLabel = t("eaz.common.rating_reviews", "{{ count }} reviews")
                        .replace("{{ count }}", ratingCount.toString())
                    ProductPdpRatingRow(
                        rating = ratingAvg,
                        reviewsLabel = reviewsLabel,
                        onClick = { reviewsSheetVisible = true },
                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                    )
                }

            }

            Spacer(modifier = Modifier.height(12.dp))

            // Gallery (order 2) – Thumbs + Main
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Thumbs (vertical)
                val imageCount = images.size
                if (imageCount > 1) {
                    Column(
                        modifier = Modifier
                            .width(60.dp)
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        images.forEachIndexed { idx: Int, url: String ->
                            val thumbActive = idx == selectedImageIndex
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
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
                val density = LocalDensity.current
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF0F0F0))
                        .pointerInput(imageCount, selectedImageIndex) {
                            if (imageCount <= 1) return@pointerInput
                            var totalDrag = 0f
                            val thresholdPx = with(density) { 48.dp.toPx() }
                            detectHorizontalDragGestures(
                                onDragStart = { totalDrag = 0f },
                                onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                                onDragEnd = {
                                    when {
                                        totalDrag > thresholdPx -> {
                                            selectedImageIndex =
                                                (selectedImageIndex - 1).coerceAtLeast(0)
                                        }
                                        totalDrag < -thresholdPx -> {
                                            selectedImageIndex =
                                                (selectedImageIndex + 1).coerceAtMost(imageCount - 1)
                                        }
                                    }
                                },
                            )
                        },
                ) {
                    if (images.isNotEmpty()) {
                        val imgIdx = selectedImageIndex.coerceIn(0, (imageCount - 1).coerceAtLeast(0))
                        val displayUrl = if (tryOnActive && !tryOnImageUrl.isNullOrBlank()) {
                            tryOnImageUrl!!
                        } else {
                            images[imgIdx]
                        }
                        Crossfade(
                            targetState = displayUrl,
                            animationSpec = tween(280),
                            label = "pdpImageCrossfade"
                        ) { url ->
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(url).build(),
                                contentDescription = p.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                    val overlayMsg = tryOnOverlayMessage
                    androidx.compose.animation.AnimatedVisibility(
                        visible = overlayMsg != null,
                        enter = fadeIn(tween(220)) + slideInVertically(
                            initialOffsetY = { it / 5 },
                            animationSpec = tween(220, easing = FastOutSlowInEasing)
                        ),
                        exit = fadeOut(tween(180))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White.copy(alpha = 0.72f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = Color(0xFF111827),
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = overlayMsg.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF111827)
                                )
                            }
                        }
                    }
                    mockupTryOnInfo?.let {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 48.dp)
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (tryOnActive) Color(0xFF111827)
                                    else Color.White.copy(alpha = 0.92f)
                                )
                                .clickable(enabled = overlayMsg == null) {
                                    val next = !tryOnActive
                                    tryOnOverlayMessage = if (next) {
                                        t("eaz.pdp.try_on_overlay_on", "Putting on your look…")
                                    } else {
                                        t("eaz.pdp.try_on_overlay_off", "Taking off mock…")
                                    }
                                    tryOnActive = next
                                    com.eazpire.creator.mockup.CustomerMockPreviewStore
                                        .setTryOnSessionActive(context, productHandle, next)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            HangerIcon(
                                color = if (tryOnActive) Color.White else EazColors.TextPrimary,
                                size = 20.dp
                            )
                        }
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
            Column(modifier = Modifier.padding(horizontal = 8.dp)) {
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
                            .padding(bottom = 6.dp),
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
                                    .border(
                                        width = if (sizeAiHint != null && isActive) 2.dp else 1.5.dp,
                                        color = when {
                                            sizeAiHint != null && isActive -> EazColors.Orange
                                            isActive -> EazColors.TextPrimary
                                            else -> Color(0xFFDDDDDD)
                                        },
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        userOverrodeSize = true
                                        sizeAiHint = null
                                        selectedSize = value
                                    }
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
                    sizeAiHint?.let { hint ->
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodySmall,
                            color = EazColors.Orange,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
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

            val carSameType = remember(p, catalogProducts) { buildPdpCarouselSameType(p, catalogProducts) }
            val carSameDesign = remember(p, catalogProducts) { buildPdpCarouselSameDesign(p, catalogProducts) }
            val youMayAlsoLike = remember(p, catalogProducts) { buildYouMayAlsoLike(p, catalogProducts) }
            val openRelated: (String) -> Unit = { h ->
                if (onNavigateToProduct != null) onNavigateToProduct.invoke(h)
                else try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.eazpire.com/products/$h")))
                } catch (_: Exception) {
                }
            }
            val showCreatorSection =
                carSameType.isNotEmpty() || carSameDesign.isNotEmpty() || p.creatorDisplay.isNotBlank() || p.vendor.isNotBlank()

            if (showCreatorSection) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE8E8E8), RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                if (carSameType.isNotEmpty() || carSameDesign.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Row(
                            modifier = Modifier.widthIn(max = 140.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            CreatorAvatarCircle(
                                name = creatorLabel,
                                avatarUrl = creatorPreview?.avatarUrl,
                                size = 56.dp,
                                onClick = openCreatorProfile
                            )
                            Column(
                                modifier = Modifier.weight(1f, fill = false),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = creatorLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = EazColors.TextPrimary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable(onClick = openCreatorProfile),
                                )
                                if (creatorPreview?.ratingAvg != null && creatorPreview?.ratingCount != null) {
                                    CreatorRatingRow(
                                        avg = creatorPreview!!.ratingAvg!!,
                                        count = creatorPreview!!.ratingCount!!,
                                    )
                                }
                            }
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (carSameType.isNotEmpty()) {
                                PdpInfiniteProductCarouselRow(
                                    title = t("eaz.pdp.carousel_same_type_designs", "More designs on this product"),
                                    products = carSameType,
                                    onProductClick = openRelated
                                )
                            }
                            if (carSameDesign.isNotEmpty()) {
                                PdpInfiniteProductCarouselRow(
                                    title = t("eaz.pdp.carousel_same_design_products", "More products with this design"),
                                    products = carSameDesign,
                                    onProductClick = openRelated
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        CreatorAvatarCircle(
                            name = creatorLabel,
                            avatarUrl = creatorPreview?.avatarUrl,
                            size = 56.dp,
                            onClick = openCreatorProfile
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = creatorLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = EazColors.TextPrimary,
                                modifier = Modifier.clickable(onClick = openCreatorProfile)
                            )
                            if (creatorPreview?.ratingAvg != null && creatorPreview?.ratingCount != null) {
                                CreatorRatingRow(
                                    avg = creatorPreview!!.ratingAvg!!,
                                    count = creatorPreview!!.ratingCount!!
                                )
                            }
                        }
                    }
                }
            }
            }

            if (youMayAlsoLike.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(top = 20.dp)
                ) {
                    Text(
                        text = t("eaz.pdp.similar_title", "You may also like"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = EazColors.TextPrimary,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    youMayAlsoLike.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { item ->
                                val img = item.variantImages.firstOrNull() ?: item.images.firstOrNull()
                                val pk = item.metaProductKey.takeIf { it.isNotBlank() }
                                val (dt, _) = splitProductTitle(item.title, item.productType, pk)
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFF5F5F5))
                                        .clickable { openRelated(item.handle) }
                                        .padding(8.dp)
                                ) {
                                    if (img != null) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context).data(img).build(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(6.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    Text(
                                        dt,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        color = EazColors.TextPrimary,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
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
                Text("$quantity", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 4.dp), color = EazColors.TextPrimary)
                IconButton(
                    onClick = { showQuantityDiscountModal = true },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = t("creator.quantity_discount.info_icon_label", "Quantity discount information"),
                        tint = EazColors.TextSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                IconButton(onClick = { quantity++ }, modifier = Modifier.size(34.dp)) {
                    Text("+", style = MaterialTheme.typography.titleMedium, color = EazColors.TextPrimary)
                }
            }
            Box(modifier = Modifier.width(1.dp).height(20.dp).background(Color(0xFFE8E8E8)))
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                PdpPlusOneLabel(
                    visible = showFavoritePlusOne,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-20).dp)
                )
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
                                    FavoritesRefreshTrigger.trigger()
                                    showFavoritePlusOne = true
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
            Text(
                "Total: CHF %.2f incl.".format(lineEstimate.afterDiscount),
                style = MaterialTheme.typography.labelSmall,
                color = EazColors.TextSecondary,
                maxLines = 1,
            )
        }
        // Row 2: Price, Delivery, Cart, Buy now
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "CHF %.2f".format(unitPriceAfterDiscount),
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.Bold,
                    color = EazColors.TextPrimary,
                    maxLines = 1,
                )
                promoOverlay?.let { po ->
                    val ends = po.promotionEndsAtMs
                    val nextSlot = po.promoCampaignStartsAtMs ?: po.promoNextWindowStartsAtMs
                    when {
                        ends != null && ends > 0L -> {
                            PdpPromoCountdownLine(
                                endsAtMs = ends,
                                prefix = t("eaz.shop.promo_countdown_prefix", "Ends in"),
                                endedLabel = t("eaz.shop.promo_countdown_ended", "Ended")
                            )
                        }
                        (po.promoOutsideSlot || po.promoPrelaunch) && nextSlot != null && nextSlot > 0L -> {
                            PdpPromoCountdownLine(
                                endsAtMs = nextSlot,
                                prefix = if (po.promoPrelaunch) t("eaz.shop.promo_starts_prefix", "Starts in") else t("eaz.shop.promo_next_discount_prefix", "Discount in"),
                                endedLabel = t("eaz.shop.promo_countdown_ended", "Ended")
                            )
                        }
                        else -> {}
                    }
                }
            }
            val deliveryDate = java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.GERMAN).format(java.util.Date(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    Icons.Default.LocalShipping,
                    contentDescription = null,
                    tint = EazColors.TextSecondary,
                    modifier = Modifier.size(14.dp),
                )
                Text("ca. $deliveryDate", style = MaterialTheme.typography.labelSmall, color = EazColors.TextSecondary, maxLines = 1)
            }
            if (comparePrice != null && comparePrice > price) {
                Text("CHF %.2f".format(comparePrice), style = MaterialTheme.typography.labelSmall, color = EazColors.TextSecondary)
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(
                contentAlignment = Alignment.Center
            ) {
                PdpPlusOneLabel(
                    visible = showCartPlusOne,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-22).dp)
                )
                Button(
                    onClick = {
                        val vid = variantIdForCart ?: run {
                            Toast.makeText(context, "Pick a product option", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
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
                                    showCartPlusOne = true
                                } else {
                                    Toast.makeText(
                                        context,
                                        result.message ?: "Could not add to cart",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                val result = withContext(Dispatchers.IO) {
                                    storefrontCartApi.createCart(listOf(vid to quantity), accessToken, countryCode)
                                }
                                if (result.ok && result.cartId != null) {
                                    storefrontCartStore.cartId = result.cartId
                                    AppCartStore.setCount(quantity)
                                    showCartToast = true
                                    showCartPlusOne = true
                                } else {
                                    Toast.makeText(
                                        context,
                                        result.message ?: "Could not create cart",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    },
                    enabled = available,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE5E7EB),
                        contentColor = EazColors.TextPrimary,
                        disabledContainerColor = Color(0xFFE5E7EB).copy(alpha = 0.45f),
                        disabledContentColor = EazColors.TextSecondary.copy(alpha = 0.6f)
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = "Add to cart", modifier = Modifier.size(20.dp))
                }
            }
            Button(
                onClick = {
                    val vid = variantIdForCart ?: run {
                        Toast.makeText(context, "Pick a product option", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    scope.launch {
                        val cartId = storefrontCartStore.cartId
                        val url = if (cartId != null) {
                            val result = withContext(Dispatchers.IO) {
                                storefrontCartApi.addLine(cartId, vid, quantity, accessToken)
                            }
                            if (result.ok && result.cart != null) {
                                storefrontCartStore.cartId = result.cart.cartId
                                AppCartStore.setCount(result.cart.itemCount)
                                showCartPlusOne = true
                                result.cart.checkoutUrl
                            } else {
                                Toast.makeText(
                                    context,
                                    result.message ?: "Could not update cart",
                                    Toast.LENGTH_SHORT
                                ).show()
                                null
                            }
                        } else {
                            val result = withContext(Dispatchers.IO) {
                                storefrontCartApi.createCart(listOf(vid to quantity), accessToken, countryCode)
                            }
                            if (result.ok && result.cartId != null && result.checkoutUrl != null) {
                                storefrontCartStore.cartId = result.cartId
                                AppCartStore.setCount(quantity)
                                showCartPlusOne = true
                                result.checkoutUrl
                            } else {
                                Toast.makeText(
                                    context,
                                    result.message ?: "Could not start checkout",
                                    Toast.LENGTH_SHORT
                                ).show()
                                null
                            }
                        }
                        url?.let { checkoutUrl = it }
                    }
                },
                enabled = available,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EazColors.Orange,
                    contentColor = Color.White,
                    disabledContainerColor = EazColors.Orange.copy(alpha = 0.45f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f)
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text(
                    t("product.buy_now", "Buy now"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }
        }

        // Main Footer – ganz unten (wie Web)
        GlobalFooter(
            onTermsClick = onTermsClick,
            modifier = Modifier.navigationBarsPadding(),
        )
    }

        if (showCloseButton) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .zIndex(2f)
            ) {
                Icon(Icons.Default.Close, contentDescription = t("common.close", "Close"), tint = EazColors.TextPrimary)
            }
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
                    Text(t("cart.added", "Added to cart"), style = MaterialTheme.typography.bodyLarge, color = Color.White)
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
                        t("product.details", "Product Details"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = EazColors.TextPrimary
                    )
                    IconButton(onClick = { detailsSheetVisible = false }) {
                        Icon(Icons.Default.Close, contentDescription = t("common.close", "Close"), tint = EazColors.TextPrimary)
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

    if (reviewsSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { reviewsSheetVisible = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 360.dp, max = 680.dp)
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        t("eaz.common.rating_reviews_section", "Customer reviews"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = EazColors.TextPrimary
                    )
                    IconButton(onClick = { reviewsSheetVisible = false }) {
                        Icon(Icons.Default.Close, contentDescription = t("common.close", "Close"), tint = EazColors.TextPrimary)
                    }
                }
                Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(Color(0xFFE8E8E8)))
                JudgeMeReviewsWebView(
                    productId = p.id,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                )
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

    QuantityDiscountModal(
        visible = showQuantityDiscountModal,
        onDismiss = { showQuantityDiscountModal = false },
        unitPrice = price,
        quantity = quantity,
        currencyLabel = "CHF",
        t = t,
    )
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

/** Orange star rating row under PDP title (opens reviews sheet). */
@Composable
private fun ProductPdpRatingRow(
    rating: Double,
    reviewsLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(5) { index ->
                val starValue = index + 1
                val filled = rating >= starValue - 0.25
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = if (filled) EazColors.Orange else Color(0xFFD8D8D8),
                    modifier = Modifier.size(17.dp)
                )
            }
        }
        Text(
            String.format("%.1f", rating),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = EazColors.TextPrimary
        )
        Text(
            reviewsLabel,
            style = MaterialTheme.typography.bodySmall,
            color = EazColors.TextSecondary
        )
    }
}

private fun buildJudgeMeReviewsHtml(productId: Long): String = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<link rel="stylesheet" href="https://cdn.judge.me/widget_v3/base.css">
<style>
:root { --jdgm-star-color: #EA7618; --jdgm-palette-color: #EA7618; }
body { margin: 0; padding: 8px 4px 24px; font-family: sans-serif; background: #fff; }
.jdgm-rev-widg__title { display: none; }
.jdgm-histogram__row {
  display: grid !important;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 8px 12px;
  margin-bottom: 8px;
}
.jdgm-histogram__frequency { margin: 0; text-align: right; white-space: nowrap; }
.jdgm-histogram__bar { margin: 0; min-width: 0; }
.jdgm-histogram__bar-content { background: #EA7618 !important; }
.jdgm-star.jdgm--on { color: #EA7618 !important; }
</style>
</head>
<body>
<div class="jdgm-widget jdgm-review-widget" data-id="$productId"></div>
<script>window.jdgm=window.jdgm||{};jdgm.SHOP_DOMAIN='eazpire.myshopify.com';jdgm.PLATFORM='shopify';</script>
<script async data-cfasync="false" src="https://cdn.judge.me/widget_preloader.js"></script>
</body>
</html>
""".trimIndent()

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun JudgeMeReviewsWebView(productId: Long, modifier: Modifier = Modifier) {
    val html = remember(productId) { buildJudgeMeReviewsHtml(productId) }
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.WHITE)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = WebViewClient()
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL("https://www.eazpire.com/", html, "text/html", "UTF-8", null)
        },
        modifier = modifier
    )
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

@Composable
private fun PdpInfiniteProductCarouselRow(
    title: String,
    products: List<ShopifyProductsApi.ProductItem>,
    onProductClick: (String) -> Unit
) {
    val base = products.take(50)
    if (base.isEmpty()) return
    val ctx = LocalContext.current
    val repeated = remember(base) {
        List(base.size * 40) { idx -> base[idx % base.size] }
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = base.size * 20)
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = EazColors.TextPrimary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 4.dp),
        ) {
            items(repeated.size, key = { "pdp-carousel-$it-${repeated[it].handle}" }) { index ->
                val item = repeated[index]
                val img = item.variantImages.firstOrNull() ?: item.images.firstOrNull()
                val pk = item.metaProductKey.takeIf { it.isNotBlank() }
                val (dt, _) = splitProductTitle(item.title, item.productType, pk)
                Column(
                    modifier = Modifier
                        .width(122.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF5F5F5))
                        .clickable { onProductClick(item.handle) }
                        .padding(6.dp)
                ) {
                    if (img != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(ctx).data(img).build(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Text(
                        dt,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = EazColors.TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun PdpProductCarouselRow(
    title: String,
    products: List<ShopifyProductsApi.ProductItem>,
    onProductClick: (String) -> Unit
) {
    if (products.isEmpty()) return
    val ctx = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            color = EazColors.TextPrimary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 4.dp)
        ) {
            items(products, key = { it.handle }) { item ->
                val img = item.variantImages.firstOrNull() ?: item.images.firstOrNull()
                val pk = item.metaProductKey.takeIf { it.isNotBlank() }
                val (dt, _) = splitProductTitle(item.title, item.productType, pk)
                Column(
                    modifier = Modifier
                        .width(122.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF5F5F5))
                        .clickable { onProductClick(item.handle) }
                        .padding(6.dp)
                ) {
                    if (img != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(ctx).data(img).build(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Text(
                        dt,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = EazColors.TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun PdpPlusOneLabel(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(200)) + slideInVertically(
            initialOffsetY = { it / 2 },
            animationSpec = tween(350, easing = FastOutSlowInEasing)
        ),
        exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(
            targetOffsetY = { -it / 3 },
            animationSpec = tween(300)
        ),
        modifier = modifier
    ) {
        Text(
            "+1",
            color = EazColors.Orange,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
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
