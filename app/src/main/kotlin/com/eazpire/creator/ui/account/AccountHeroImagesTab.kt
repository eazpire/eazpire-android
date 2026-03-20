package com.eazpire.creator.ui.account

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyProductsApi
import com.eazpire.creator.auth.AuthConfig
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.locale.LocaleStore
import kotlinx.coroutines.launch

private val HERO_REGION_TABS_ANDROID = listOf(
    "EU" to "Europa",
    "US" to "USA",
    "GB" to "UK",
    "CA" to "Kanada",
    "AU" to "Australien",
    "CN" to "China",
    "OTHER" to "Sonstige"
)

private fun extractNumericProductId(value: String?): String {
    if (value.isNullOrBlank()) return ""
    val gidMatch = Regex("gid://shopify/Product/(\\d+)", RegexOption.IGNORE_CASE).find(value)
    if (gidMatch != null) return gidMatch.groupValues.getOrNull(1).orEmpty()
    return value.filter { it.isDigit() }
}

private fun normalizeShopifyImageUrl(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return if (raw.startsWith("//")) "https:$raw" else raw
}

private fun extractShopifyHandleFromUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return try {
        val segments = Uri.parse(url).pathSegments
        val idx = segments.indexOf("products")
        if (idx >= 0 && idx + 1 < segments.size) {
            segments[idx + 1].substringBefore("?").trim().takeIf { it.isNotBlank() }
        } else null
    } catch (_: Exception) {
        null
    }
}

data class HeroProduct(
    val id: String,
    val title: String,
    val image: String?,
    val shopifyHandle: String?,
    val storefrontUrl: String?,
    val productType: String?,
    val productKey: String?,
    val region: String,
    val used: Boolean = false
)

@Composable
fun AccountHeroImagesTab(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore? = null,
    darkMode: Boolean = false,
    onGenerated: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    fun t(key: String, fallback: String): String = translationStore?.t(key, fallback) ?: fallback
    val textPrimary = if (darkMode) Color.White else EazColors.TextPrimary
    val textSecondary = if (darkMode) Color.White.copy(alpha = 0.7f) else EazColors.TextSecondary
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }
    val shopifyApi = remember { ShopifyProductsApi() }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() ?: "" }
    val localeStore = remember { LocaleStore(context) }
    val localeRegion by localeStore.regionCode.collectAsState(initial = localeStore.getRegionCodeSync())

    var products by remember { mutableStateOf<List<HeroProduct>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedTop by remember { mutableStateOf<HeroProduct?>(null) }
    var selectedAddition by remember { mutableStateOf<HeroProduct?>(null) }
    var modelImageUri by remember { mutableStateOf<Uri?>(null) }
    var backgroundImageUri by remember { mutableStateOf<Uri?>(null) }
    var modelImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var backgroundImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var prompt by remember { mutableStateOf("") }
    var showProductPicker by remember { mutableStateOf(false) }
    var pickerCategory by remember { mutableStateOf("top") }
    var selectedRegion by remember { mutableStateOf(localeRegion) }
    var lockedRegion by remember { mutableStateOf<String?>(null) }
    var regionLockMessage by remember { mutableStateOf<String?>(null) }
    var usedProductIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var usageFilter by remember { mutableStateOf("unused") } // unused | used
    var modalSearchQuery by remember { mutableStateOf("") }

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            modelImageUri = it
            scope.launch {
                try {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        modelImageBytes = stream.readBytes()
                    }
                } catch (_: Exception) {}
            }
        }
    }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            backgroundImageUri = it
            scope.launch {
                try {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        backgroundImageBytes = stream.readBytes()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun loadProducts() {
        scope.launch {
            if (ownerId.isBlank()) return@launch
            loading = true
            try {
                try {
                    val usedResp = api.getHeroUsedProducts(ownerId)
                    if (usedResp.optBoolean("ok", false)) {
                        val usedArr = usedResp.optJSONArray("used_product_ids") ?: org.json.JSONArray()
                        val usedSet = mutableSetOf<String>()
                        for (i in 0 until usedArr.length()) {
                            val raw = usedArr.optString(i, "").trim()
                            if (raw.isNotBlank()) {
                                usedSet.add(raw)
                                extractNumericProductId(raw).takeIf { it.isNotBlank() }?.let { usedSet.add(it) }
                            }
                        }
                        usedProductIds = usedSet
                    } else {
                        usedProductIds = emptySet()
                    }
                } catch (_: Exception) {
                    usedProductIds = emptySet()
                }

                val catalogResp = api.getCatalogProducts(selectedRegion)
                val allowedKeys = mutableSetOf<String>()
                if (catalogResp.optBoolean("ok", false)) {
                    val arrOnline = catalogResp.optJSONArray("products") ?: org.json.JSONArray()
                    val arrPreview = catalogResp.optJSONArray("preview_products") ?: org.json.JSONArray()
                    for (i in 0 until arrOnline.length()) {
                        arrOnline.optJSONObject(i)?.optString("product_key", "")?.takeIf { it.isNotBlank() }?.let { allowedKeys.add(it) }
                    }
                    for (i in 0 until arrPreview.length()) {
                        arrPreview.optJSONObject(i)?.optString("product_key", "")?.takeIf { it.isNotBlank() }?.let { allowedKeys.add(it) }
                    }
                }

                fun mapShopifyArray(arr: org.json.JSONArray): List<HeroProduct> {
                    return (0 until arr.length()).mapNotNull { i ->
                        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                        fun toImageStr(v: Any?): String? = when (v) {
                            is String -> v.takeIf { it.isNotBlank() }
                            is org.json.JSONObject -> (
                                v.optString("src", "").takeIf { it.isNotBlank() }
                                    ?: v.optString("url", "").takeIf { it.isNotBlank() }
                                    ?: v.optString("image_url", "").takeIf { it.isNotBlank() }
                                    ?: v.optString("preview_url", "").takeIf { it.isNotBlank() }
                            )
                            else -> null
                        }
                        val img = normalizeShopifyImageUrl(
                            toImageStr(obj.opt("featured_image"))
                                ?: toImageStr(obj.opt("image_url"))
                                ?: toImageStr(obj.opt("image"))
                                ?: toImageStr(obj.opt("preview_url"))
                                ?: toImageStr(obj.optJSONArray("images")?.opt(0))
                        )
                        val id = obj.optString("id", "")
                            .ifBlank { obj.optString("shopify_product_id", "") }
                            .ifBlank { obj.optString("product_id", "") }
                        if (id.isBlank()) return@mapNotNull null
                        val productKey = obj.optString("product_key", "").takeIf { it.isNotBlank() }
                        val numericId = extractNumericProductId(id)
                        val isUsed = usedProductIds.contains(id) || (numericId.isNotBlank() && usedProductIds.contains(numericId))
                        val title = obj.optString("title", "")
                            .ifBlank { obj.optString("product_name", "") }
                            .ifBlank { productKey ?: id }
                        HeroProduct(
                            id = id,
                            title = title,
                            image = img,
                            shopifyHandle = obj.optString("handle", "").takeIf { it.isNotBlank() }
                                ?: obj.optString("shopify_handle", "").takeIf { it.isNotBlank() },
                            storefrontUrl = obj.optString("storefront_url", "").takeIf { it.isNotBlank() },
                            productType = obj.optString("product_type", "").takeIf { it.isNotBlank() },
                            productKey = productKey,
                            region = obj.optString("region", selectedRegion).ifBlank { selectedRegion },
                            used = isUsed
                        )
                    }
                }

                fun mapPublishedArray(arr: org.json.JSONArray): List<HeroProduct> {
                    return (0 until arr.length()).mapNotNull { i ->
                        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                        val rawId = obj.optString("shopify_product_id", "")
                            .ifBlank { obj.optString("product_key", "") }
                            .ifBlank { obj.optString("id", "") }
                        if (rawId.isBlank()) return@mapNotNull null
                        val productKey = obj.optString("product_key", "").takeIf { it.isNotBlank() }
                        val storefrontUrl = obj.optString("storefront_url", "").takeIf { it.isNotBlank() }
                        val image = obj.optString("featured_image", "")
                            .ifBlank { obj.optString("image_url", "") }
                            .ifBlank { obj.optString("preview_url", "") }
                            .ifBlank { obj.optString("image", "") }
                            .takeIf { it.isNotBlank() }
                            ?.let { normalizeShopifyImageUrl(it) }
                        val numericId = extractNumericProductId(rawId)
                        val isUsed = usedProductIds.contains(rawId) || (numericId.isNotBlank() && usedProductIds.contains(numericId))
                        HeroProduct(
                            id = rawId,
                            title = obj.optString("product_name", obj.optString("title", productKey ?: rawId)),
                            image = image,
                            shopifyHandle = obj.optString("shopify_handle", "").takeIf { it.isNotBlank() }
                                ?: obj.optString("handle", "").takeIf { it.isNotBlank() }
                                ?: extractShopifyHandleFromUrl(storefrontUrl),
                            storefrontUrl = storefrontUrl,
                            productType = obj.optString("product_type", "").takeIf { it.isNotBlank() },
                            productKey = productKey,
                            region = obj.optString("region", selectedRegion).ifBlank { selectedRegion },
                            used = isUsed
                        )
                    }
                }

                val shopifyResp = api.getShopifyProducts(AuthConfig.SHOP_DOMAIN, ownerId, null)
                var nextProducts = if (shopifyResp.optBoolean("ok", false)) {
                    mapShopifyArray(shopifyResp.optJSONArray("products") ?: org.json.JSONArray())
                } else {
                    emptyList()
                }

                if (allowedKeys.isNotEmpty()) {
                    val filteredByKeys = nextProducts.filter { p ->
                        val key = p.productKey
                        !key.isNullOrBlank() && allowedKeys.contains(key)
                    }
                    if (filteredByKeys.isNotEmpty()) {
                        nextProducts = filteredByKeys
                    } else {
                        nextProducts = emptyList()
                    }
                }

                // Web parity: fallback to published products when Shopify source is empty.
                if (nextProducts.isEmpty()) {
                    val publishedResp = api.getPublishedProducts(ownerId, AuthConfig.SHOP_DOMAIN)
                    if (publishedResp.optBoolean("ok", false)) {
                        nextProducts = mapPublishedArray(publishedResp.optJSONArray("products") ?: org.json.JSONArray())
                        val hydrated = mutableListOf<HeroProduct>()
                        for (p in nextProducts) {
                            val needsImage = p.image.isNullOrBlank()
                            val handle = p.shopifyHandle
                            if (!needsImage || handle.isNullOrBlank()) {
                                hydrated.add(p)
                                continue
                            }
                            val detail = runCatching { shopifyApi.getProductByHandle(handle) }.getOrNull()
                            val fallbackImage = normalizeShopifyImageUrl(detail?.images?.firstOrNull()?.src)
                            hydrated.add(p.copy(image = fallbackImage ?: p.image))
                        }
                        nextProducts = hydrated
                        if (allowedKeys.isNotEmpty()) {
                            nextProducts = nextProducts.filter { p ->
                                val key = p.productKey
                                !key.isNullOrBlank() && allowedKeys.contains(key)
                            }
                        }
                    }
                }

                products = nextProducts.distinctBy { it.id }
            } catch (_: Exception) {}
            loading = false
        }
    }

    LaunchedEffect(localeRegion) {
        if (lockedRegion == null && selectedTop == null && selectedAddition == null) {
            selectedRegion = localeRegion
        }
    }

    LaunchedEffect(ownerId, selectedRegion) {
        if (ownerId.isNotBlank()) loadProducts()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                .background(Color(0x99312937))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = t("creator.marketing.select_products", "Select products"),
                    style = MaterialTheme.typography.titleMedium,
                    color = textPrimary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HeroProductCard(
                            label = "Top",
                            product = selectedTop,
                            onClick = {
                                pickerCategory = "top"
                                usageFilter = "unused"
                                modalSearchQuery = ""
                                showProductPicker = true
                            },
                            onClear = {
                                selectedTop = null
                                lockedRegion = selectedAddition?.region
                                if (lockedRegion == null) selectedRegion = localeRegion
                            },
                            modifier = Modifier.weight(1f)
                        )
                        HeroProductCard(
                            label = "Bottom",
                            product = null,
                            onClick = {},
                            showSoon = true,
                            disabled = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HeroProductCard(
                            label = "Feet",
                            product = null,
                            onClick = {},
                            showSoon = true,
                            disabled = true,
                            modifier = Modifier.weight(1f)
                        )
                        HeroProductCard(
                            label = "Addition",
                            product = selectedAddition,
                            onClick = {
                                pickerCategory = "addition"
                                usageFilter = "unused"
                                modalSearchQuery = ""
                                showProductPicker = true
                            },
                            onClear = {
                                selectedAddition = null
                                lockedRegion = selectedTop?.region
                                if (lockedRegion == null) selectedRegion = localeRegion
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
        if (!regionLockMessage.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(regionLockMessage!!, style = MaterialTheme.typography.bodySmall, color = EazColors.Orange)
        }
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroUploadCard(
                label = t("creator.marketing.model", "Model"),
                iconEmoji = "\uD83D\uDC64",
                imageUri = modelImageUri,
                onPick = { modelPicker.launch("image/*") },
                onClear = { modelImageUri = null; modelImageBytes = null },
                helperText = t("creator.marketing.upload_mock_model", "Upload your mock model image"),
                modifier = Modifier.weight(1f)
            )
            HeroUploadCard(
                label = t("creator.marketing.background", "Background"),
                iconEmoji = "\uD83C\uDF05",
                imageUri = backgroundImageUri,
                onPick = { backgroundPicker.launch("image/*") },
                onClear = { backgroundImageUri = null; backgroundImageBytes = null },
                helperText = t("creator.marketing.upload_mock_background", "Upload your mock background image"),
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(t("creator.marketing.additional_info", "Add any additional information for your hero image...")) },
            minLines = 3,
            maxLines = 5,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.White.copy(alpha = 0.18f),
                unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                cursorColor = EazColors.Orange,
                focusedPlaceholderColor = Color.White.copy(alpha = 0.42f),
                unfocusedPlaceholderColor = Color.White.copy(alpha = 0.42f),
                focusedContainerColor = Color(0x99312937),
                unfocusedContainerColor = Color(0x99312937)
            )
        )
        Spacer(Modifier.height(12.dp))

        if (showProductPicker) {
            HeroProductPickerModal(
                products = products,
                category = pickerCategory,
                currentRegion = selectedRegion,
                lockedRegion = lockedRegion,
                usageFilter = usageFilter,
                searchQuery = modalSearchQuery,
                loading = loading,
                onUsageFilterChange = { usageFilter = it },
                onSearchQueryChange = { modalSearchQuery = it },
                onRegionChange = { nextRegion ->
                    if (lockedRegion != null && lockedRegion != nextRegion) {
                        regionLockMessage = t("creator.marketing.region_lock", "Only one region per hero draft is allowed.")
                    } else {
                        regionLockMessage = null
                        selectedRegion = nextRegion
                    }
                },
                onSelect = { product ->
                    when (pickerCategory) {
                        "top" -> selectedTop = product
                        "addition" -> selectedAddition = product
                    }
                    lockedRegion = product.region
                    selectedRegion = product.region
                    regionLockMessage = null
                    showProductPicker = false
                },
                onDismiss = { showProductPicker = false },
                resolveImageByHandle = { handle ->
                    val detail = runCatching { shopifyApi.getProductByHandle(handle) }.getOrNull()
                    normalizeShopifyImageUrl(detail?.images?.firstOrNull()?.src)
                },
                t = ::t
            )
        }
    }
}

@Composable
private fun HeroProductCard(
    label: String,
    product: HeroProduct?,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null,
    showSoon: Boolean = false,
    disabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .border(
                2.dp,
                when {
                    disabled -> Color.White.copy(alpha = 0.06f)
                    product != null -> EazColors.Orange.copy(alpha = 0.8f)
                    else -> Color.White.copy(alpha = 0.12f)
                },
                RoundedCornerShape(12.dp)
            )
            .background(
                when {
                    disabled -> Color(0x99111827)
                    product != null -> EazColors.Orange.copy(alpha = 0.12f)
                    else -> Color(0xCC111827)
                }
            )
            .clickable(enabled = !disabled, onClick = onClick)
    ) {
        if (showSoon) {
            Text(
                text = "COMING SOON",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF1F2937),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(EazColors.Orange.copy(alpha = 0.9f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
        if (product != null) {
            if (!product.image.isNullOrBlank()) {
                AsyncImage(
                    model = product.image,
                    contentDescription = product.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            onClear?.let { clear ->
                IconButton(
                    onClick = clear,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    text = product.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    maxLines = 1
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    if (disabled) Icons.Default.Lock else Icons.Default.Add,
                    null,
                    tint = Color.White.copy(alpha = if (disabled) 0.55f else 0.8f),
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(label, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.88f))
            }
        }
    }
}

@Composable
private fun HeroUploadCard(
    label: String,
    iconEmoji: String,
    imageUri: Uri?,
    onPick: () -> Unit,
    onClear: () -> Unit,
    helperText: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .background(Color(0x99111827))
            .clickable(onClick = onPick)
    ) {
        if (imageUri != null) {
            AsyncImage(
                model = imageUri,
                contentDescription = label,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            IconButton(
                onClick = onClear,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(6.dp))
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = iconEmoji,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White.copy(alpha = 0.82f)
                )
                Spacer(Modifier.height(8.dp))
                Text(label, style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.9f))
                Spacer(Modifier.height(4.dp))
                Text(
                    text = helperText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.62f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeroProductPickerModal(
    products: List<HeroProduct>,
    category: String,
    currentRegion: String,
    lockedRegion: String?,
    usageFilter: String,
    searchQuery: String,
    loading: Boolean,
    onUsageFilterChange: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onRegionChange: (String) -> Unit,
    onSelect: (HeroProduct) -> Unit,
    onDismiss: () -> Unit,
    resolveImageByHandle: suspend (String) -> String?,
    t: (String, String) -> String
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val gridState = rememberLazyGridState()
    val topKeywords = listOf("t-shirt", "hoodie", "sweatshirt", "shirt", "top", "tee", "polo", "jacket", "sweater", "pullover", "jacke", "oberteil")
    fun isTopProduct(p: HeroProduct): Boolean {
        val t = (p.title + " " + (p.productType ?: "")).lowercase()
        return topKeywords.any { t.contains(it) }
    }
    val filteredByCategoryStrict = when (category) {
        "top" -> products.filter { isTopProduct(it) }
        "addition" -> products.filter { !isTopProduct(it) }
        else -> products
    }
    val filteredByCategory = if (filteredByCategoryStrict.isNotEmpty()) filteredByCategoryStrict else products
    val filteredByUsage = when (usageFilter) {
        "used" -> filteredByCategory.filter { it.used }
        else -> filteredByCategory.filter { !it.used }
    }
    val usageBase = if (filteredByUsage.isNotEmpty()) filteredByUsage else filteredByCategory
    val filtered = usageBase.filter {
        searchQuery.isBlank() ||
            it.title.contains(searchQuery.trim(), ignoreCase = true) ||
            (it.productType ?: "").contains(searchQuery.trim(), ignoreCase = true)
    }
    var selectedProductId by remember { mutableStateOf<String?>(null) }
    var imageHydrationLimit by remember { mutableIntStateOf(10) }
    var imageOverrides by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var imageRequestedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(filtered.size, usageFilter, searchQuery, category, currentRegion) {
        imageHydrationLimit = 10
    }

    LaunchedEffect(filtered, imageHydrationLimit) {
        val candidates = filtered.take(imageHydrationLimit.coerceAtLeast(10)).filter { p ->
            p.image.isNullOrBlank() && !p.shopifyHandle.isNullOrBlank() && !imageRequestedIds.contains(p.id)
        }
        if (candidates.isEmpty()) return@LaunchedEffect
        imageRequestedIds = imageRequestedIds + candidates.map { it.id }
        val newImages = mutableMapOf<String, String>()
        for (product in candidates) {
            val handle = product.shopifyHandle ?: continue
            val image = runCatching { resolveImageByHandle(handle) }.getOrNull()
            if (!image.isNullOrBlank()) {
                newImages[product.id] = image
            }
        }
        if (newImages.isNotEmpty()) {
            imageOverrides = imageOverrides + newImages
        }
    }

    LaunchedEffect(filtered) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .collect { lastVisible ->
                val nextLimit = (lastVisible + 10).coerceAtLeast(10)
                if (nextLimit > imageHydrationLimit) {
                    imageHydrationLimit = nextLimit
                }
            }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1F2937),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(680.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111827))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = t(
                        "creator.marketing.select_product_for",
                        "Produkt auswählen - ${if (category == "top") "Top" else "Additional"}"
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFE5E7EB)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null, tint = Color(0xFF9CA3AF))
                }
            }

            // Subheader
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1F2937))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HERO_REGION_TABS_ANDROID.forEach { (code, label) ->
                        val lockedOut = lockedRegion != null && lockedRegion != code
                        val active = currentRegion == code
                        Text(
                            text = label,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .border(
                                    1.dp,
                                    when {
                                        lockedOut -> Color(0xFF64748B).copy(alpha = 0.5f)
                                        active -> EazColors.Orange
                                        else -> EazColors.Orange.copy(alpha = 0.4f)
                                    },
                                    RoundedCornerShape(999.dp)
                                )
                                .background(
                                    if (active) EazColors.Orange.copy(alpha = 0.25f)
                                    else EazColors.OrangeBg.copy(alpha = 0.12f)
                                )
                                .clickable(enabled = !lockedOut) { onRegionChange(code) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            color = if (lockedOut) Color.White.copy(alpha = 0.4f) else Color.White,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text(t("creator.common.search", "Produkte durchsuchen...")) },
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFFE5E7EB),
                            unfocusedTextColor = Color(0xFFE5E7EB),
                            focusedBorderColor = Color(0xFF3B82F6),
                            unfocusedBorderColor = Color(0xFF374151),
                            focusedContainerColor = Color(0xFF111827),
                            unfocusedContainerColor = Color(0xFF111827),
                            focusedPlaceholderColor = Color(0xFF9CA3AF),
                            unfocusedPlaceholderColor = Color(0xFF9CA3AF)
                        )
                    )
                    Row(
                        modifier = Modifier
                            .height(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF111827))
                            .border(1.dp, Color(0xFF374151), RoundedCornerShape(8.dp))
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterTabPill(
                            active = usageFilter == "unused",
                            label = t("creator.marketing.unused", "Unused"),
                            onClick = { onUsageFilterChange("unused") }
                        )
                        FilterTabPill(
                            active = usageFilter == "used",
                            label = t("creator.marketing.used", "Used"),
                            onClick = { onUsageFilterChange("used") }
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                if (loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = EazColors.Orange)
                    }
                } else {
                    if (filtered.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = t("creator.common.no_products_found", "Keine Produkte gefunden."),
                                color = Color(0xFF9CA3AF)
                            )
                        }
                    } else {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filtered) { product ->
                                val isSelected = selectedProductId == product.id
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(
                                            2.dp,
                                            if (isSelected) Color(0xFF3B82F6) else Color(0xFF374151),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .background(if (isSelected) Color(0xFF1E3A8A) else Color(0xFF111827))
                                        .clickable { selectedProductId = product.id }
                                ) {
                                    val cardImage = imageOverrides[product.id] ?: product.image
                                    if (!cardImage.isNullOrBlank()) {
                                        AsyncImage(
                                            model = cardImage,
                                            contentDescription = product.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
                                                .size(24.dp)
                                                .clip(RoundedCornerShape(999.dp))
                                                .background(Color(0xFF3B82F6)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("✓", color = Color.White, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1F2937))
                    .border(1.dp, Color(0xFF374151))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF374151), RoundedCornerShape(6.dp))
                ) { Text(t("creator.common.cancel", "Cancel"), color = Color(0xFFD1D5DB)) }
                Button(
                    onClick = {
                        filtered.firstOrNull { it.id == selectedProductId }?.let(onSelect)
                    },
                    enabled = selectedProductId != null,
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF3B82F6),
                        contentColor = Color.White
                    )
                ) {
                    Text(t("creator.common.confirm", "Confirm"))
                }
            }
        }
    }
}

@Composable
private fun FilterTabPill(
    active: Boolean,
    label: String,
    onClick: () -> Unit
) {
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(
                1.dp,
                if (active) Color(0xFF3B82F6) else Color(0xFF374151),
                RoundedCornerShape(6.dp)
            )
            .background(
                if (active) Color(0xFF3B82F6) else Color(0xFF111827),
                RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        color = if (active) Color.White else Color(0xFF9CA3AF),
        style = MaterialTheme.typography.labelSmall
    )
}
