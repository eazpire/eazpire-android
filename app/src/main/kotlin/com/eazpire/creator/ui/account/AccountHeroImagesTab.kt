package com.eazpire.creator.ui.account

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.ui.CREATIONS_PRODUCTS_PER_PAGE
import com.eazpire.creator.ui.PaginationDotsStyle
import com.eazpire.creator.ui.ProductPaginationDots
import com.eazpire.creator.ui.ShopStyleProductImages
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyProductsApi
import com.eazpire.creator.auth.AuthConfig
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.chat.EazySidebarTab
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.locale.LocaleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Locale

private const val HERO_GENERATE_COST_EAZ = 0.5

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

/** `id` / `shopify_product_id` may be numbers in JSON — [org.json.JSONObject.optString] can miss those. */
private fun jsonScalarString(obj: org.json.JSONObject, key: String): String {
    if (!obj.has(key)) return ""
    return when (val v = obj.opt(key)) {
        null -> ""
        org.json.JSONObject.NULL -> ""
        is String -> v.trim()
        is Number -> v.toString().trim()
        else -> v.toString().trim()
    }
}

/**
 * One candidate vs used set (same as web [isHeroProductUsed] inner loop in
 * hero-product-selection-modal-functions.js).
 */
private fun idMatchesHeroUsedOne(usedProductIds: Set<String>, raw: String): Boolean {
    if (raw.isBlank()) return false
    if (raw in usedProductIds) return true
    val numeric = extractNumericProductId(raw)
    if (numeric.isBlank()) return false
    if (numeric in usedProductIds) return true
    if ("gid://shopify/Product/$numeric" in usedProductIds) return true
    return false
}

/** Collect id, shopify_product_id, product_id (web parity with mapShopifyProducts / mapPublishedProducts). */
private fun collectHeroMatchCandidates(
    obj: org.json.JSONObject,
    primaryId: String,
    productKey: String?
): List<String> {
    val out = LinkedHashSet<String>()
    fun add(s: String?) {
        val t = s?.trim().orEmpty()
        if (t.isNotEmpty()) out.add(t)
    }
    add(primaryId)
    add(productKey)
    add(jsonScalarString(obj, "id"))
    add(jsonScalarString(obj, "shopify_product_id"))
    add(jsonScalarString(obj, "product_id"))
    return out.toList()
}

/** True if any candidate matches hero-used IDs (web parity). */
private fun productMatchesHeroUsedSet(
    usedProductIds: Set<String>,
    candidates: List<String>
): Boolean {
    if (usedProductIds.isEmpty()) return false
    val seen = candidates.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    for (c in seen) {
        if (idMatchesHeroUsedOne(usedProductIds, c)) return true
    }
    return false
}

private fun normalizeShopifyImageUrl(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return if (raw.startsWith("//")) "https:$raw" else raw
}

private fun heroProductMainImageUrl(p: HeroProduct): String? {
    val raw = p.shopImageUrls.firstOrNull { !it.isNullOrBlank() } ?: p.image
    return normalizeShopifyImageUrl(raw)
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

/** Same URLs as shop [CollectionScreen] (product-json → variantImages). */
private suspend fun enrichHeroProductsWithShopImages(
    list: List<HeroProduct>,
    shopifyApi: ShopifyProductsApi
): List<HeroProduct> = coroutineScope {
    val sem = Semaphore(12)
    list.map { p ->
        async(Dispatchers.IO) {
            sem.withPermit {
                val handle = p.shopifyHandle?.takeIf { it.isNotBlank() }
                    ?: extractShopifyHandleFromUrl(p.storefrontUrl)
                if (handle.isNullOrBlank()) {
                    val fallback = listOfNotNull(p.image)
                        .mapNotNull { normalizeShopifyImageUrl(it) }
                        .filter { it.isNotBlank() }
                    return@withPermit p.copy(
                        shopImageUrls = fallback,
                        image = fallback.firstOrNull() ?: p.image
                    )
                }
                val urls = runCatching { shopifyApi.getShopCardImageUrls(handle) }
                    .getOrNull()
                    .orEmpty()
                    .mapNotNull { normalizeShopifyImageUrl(it) }
                    .filter { it.isNotBlank() }
                    .distinct()
                val merged = urls.ifEmpty {
                    listOfNotNull(p.image)
                        .mapNotNull { normalizeShopifyImageUrl(it) }
                        .filter { it.isNotBlank() }
                }
                p.copy(
                    shopImageUrls = merged,
                    image = merged.firstOrNull() ?: p.image
                )
            }
        }
    }.awaitAll()
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
    val used: Boolean = false,
    /** Filled by [enrichHeroProductsWithShopImages]; same list as shop product cards. */
    val shopImageUrls: List<String> = emptyList()
)

@Composable
fun AccountHeroImagesTab(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore? = null,
    darkMode: Boolean = false,
    onGenerated: (() -> Unit)? = null,
    onHeroJobStarted: (jobId: String, summary: String) -> Unit = { _, _ -> },
    onOpenEazyChat: (EazySidebarTab) -> Unit = {},
    onHeroEazyReadyChange: (Boolean) -> Unit = {},
    /** Increment from header "Start generation" bubble (Creator flow). */
    headerStartNonce: Int = 0,
    onHeroGeneratingChange: (Boolean) -> Unit = {},
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
    var modelMime by remember { mutableStateOf("image/jpeg") }
    var backgroundMime by remember { mutableStateOf("image/jpeg") }
    var prompt by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    SideEffect { onHeroGeneratingChange(generating) }
    var showHeroConfirmDialog by remember { mutableStateOf(false) }
    LaunchedEffect(headerStartNonce) {
        if (headerStartNonce == 0) return@LaunchedEffect
        if (generating) return@LaunchedEffect
        if (ownerId.isBlank() || (selectedTop == null && selectedAddition == null)) return@LaunchedEffect
        showHeroConfirmDialog = true
    }
    var confirmBalanceEaz by remember { mutableStateOf<Double?>(null) }
    var generateStatus by remember { mutableStateOf<String?>(null) }
    var generateStatusError by remember { mutableStateOf(false) }
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
            modelMime = context.contentResolver.getType(it) ?: "image/jpeg"
            scope.launch {
                try {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        modelImageBytes = stream.readBytes()
                    }
                } catch (_: Exception) {}
            }
        }
    }
    LaunchedEffect(showHeroConfirmDialog) {
        if (!showHeroConfirmDialog || ownerId.isBlank()) return@LaunchedEffect
        confirmBalanceEaz = null
        try {
            val b = withContext(Dispatchers.IO) { api.getBalance(ownerId) }
            if (b.optBoolean("ok", false) && b.has("balance_eaz")) {
                confirmBalanceEaz = b.optDouble("balance_eaz", 0.0)
            }
        } catch (_: Exception) {}
    }

    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            backgroundImageUri = it
            backgroundMime = context.contentResolver.getType(it) ?: "image/jpeg"
            scope.launch {
                try {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        backgroundImageBytes = stream.readBytes()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun runHeroGenerate() {
        if (ownerId.isBlank() || generating) return
        val top = selectedTop
        val add = selectedAddition
        if (top == null && add == null) {
            generateStatus = t("creator.hero_images.select_products_first", "Select at least one product.")
            generateStatusError = true
            return
        }
        val idList = listOfNotNull(top?.id, add?.id).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (idList.isEmpty()) return
        val finalPrompt = prompt.trim().ifBlank {
            t(
                "creator.hero_images.default_scene_prompt",
                "Professional product photography with natural lighting and clean background"
            )
        }
        scope.launch {
            generating = true
            generateStatus = null
            generateStatusError = false
            try {
                var modelUrl: String? = null
                var bgUrl: String? = null
                modelImageBytes?.let { bytes ->
                    val up = api.uploadHeroImage(ownerId, "model", bytes, modelMime)
                    if (up.optBoolean("ok", false)) {
                        modelUrl = up.optString("image_url", "").takeIf { it.isNotBlank() }
                    }
                }
                backgroundImageBytes?.let { bytes ->
                    val up = api.uploadHeroImage(ownerId, "background", bytes, backgroundMime)
                    if (up.optBoolean("ok", false)) {
                        bgUrl = up.optString("image_url", "").takeIf { it.isNotBlank() }
                    }
                }
                val imageUrls = listOfNotNull(top?.let { heroProductMainImageUrl(it) }, add?.let { heroProductMainImageUrl(it) })
                    .distinct()
                val regionCode = (lockedRegion ?: selectedRegion).ifBlank { localeRegion }
                val resp = api.heroGenerate(
                    ownerId = ownerId,
                    productIds = idList,
                    prompt = finalPrompt,
                    productImageUrls = imageUrls,
                    modelImageUrl = modelUrl,
                    backgroundImageUrl = bgUrl,
                    region = regionCode
                )
                if (resp.optBoolean("ok", false) && resp.optString("job_id", "").isNotBlank()) {
                    val jobId = resp.optString("job_id", "")
                    val summary = buildString {
                        appendLine(t("creator.hero_eazy.job_summary_title", "Hero image generation"))
                        selectedTop?.let { appendLine("• ${it.title}") }
                        selectedAddition?.let { appendLine("• ${it.title}") }
                        appendLine(
                            "${t("creator.marketing.region", "Region")}: ${(lockedRegion ?: selectedRegion).ifBlank { localeRegion }}"
                        )
                        val p = prompt.trim().ifBlank {
                            t(
                                "creator.hero_images.default_scene_prompt",
                                "Professional product photography with natural lighting and clean background"
                            )
                        }
                        appendLine(
                            "${t("creator.hero_eazy.job_summary_prompt", "Prompt")}: ${p.take(120)}${if (p.length > 120) "…" else ""}"
                        )
                    }.trim()
                    onHeroJobStarted(jobId, summary)
                    generateStatus = null
                    generateStatusError = false
                    onOpenEazyChat(EazySidebarTab.Jobs)
                } else {
                    generateStatus = resp.optString("error", "")
                        .ifBlank { t("creator.hero_images.generation_failed_generic", "Generation failed.") }
                    generateStatusError = true
                }
            } catch (e: Exception) {
                generateStatus = e.message?.take(200)
                    ?: t("creator.hero_images.generation_network_error", "Network error.")
                generateStatusError = true
            } finally {
                generating = false
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
                            val raw = when (val v = usedArr.opt(i)) {
                                null -> ""
                                org.json.JSONObject.NULL -> ""
                                is String -> v.trim()
                                is Number -> v.toString().trim()
                                else -> v.toString().trim()
                            }
                            if (raw.isNotBlank()) {
                                usedSet.add(raw)
                                extractNumericProductId(raw).takeIf { it.isNotBlank() }?.let { num ->
                                    usedSet.add(num)
                                    usedSet.add("gid://shopify/Product/$num")
                                }
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
                        val id = jsonScalarString(obj, "id")
                            .ifBlank { jsonScalarString(obj, "shopify_product_id") }
                            .ifBlank { jsonScalarString(obj, "product_id") }
                        if (id.isBlank()) return@mapNotNull null
                        val productKey = obj.optString("product_key", "").takeIf { it.isNotBlank() }
                        val isUsed = productMatchesHeroUsedSet(
                            usedProductIds,
                            collectHeroMatchCandidates(obj, id, productKey)
                        )
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
                        val rawId = jsonScalarString(obj, "shopify_product_id")
                            .ifBlank { jsonScalarString(obj, "product_key") }
                            .ifBlank { jsonScalarString(obj, "id") }
                        if (rawId.isBlank()) return@mapNotNull null
                        val productKey = obj.optString("product_key", "").takeIf { it.isNotBlank() }
                        val storefrontUrl = obj.optString("storefront_url", "").takeIf { it.isNotBlank() }
                        val fiRaw = obj.opt("featured_image")
                        val image = when (fiRaw) {
                            is org.json.JSONObject -> (
                                fiRaw.optString("src", "").takeIf { it.isNotBlank() }
                                    ?: fiRaw.optString("url", "").takeIf { it.isNotBlank() }
                                )
                            else -> obj.optString("featured_image", "").takeIf { it.isNotBlank() }
                        }
                            ?.let { normalizeShopifyImageUrl(it) }
                            ?: normalizeShopifyImageUrl(
                                obj.optString("image_url", "")
                                    .ifBlank { obj.optString("preview_url", "") }
                                    .ifBlank { obj.optString("image", "") }
                                    .takeIf { it.isNotBlank() }
                            )
                        val isUsed = productMatchesHeroUsedSet(
                            usedProductIds,
                            collectHeroMatchCandidates(obj, rawId, productKey)
                        )
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
                        if (allowedKeys.isNotEmpty()) {
                            nextProducts = nextProducts.filter { p ->
                                val key = p.productKey
                                !key.isNullOrBlank() && allowedKeys.contains(key)
                            }
                        }
                    }
                }

                nextProducts = withContext(Dispatchers.IO) {
                    enrichHeroProductsWithShopImages(nextProducts, shopifyApi)
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

        val canGenerate = ownerId.isNotBlank() && !generating && (selectedTop != null || selectedAddition != null)
        val showEazyBubble = canGenerate
        SideEffect { onHeroEazyReadyChange(showEazyBubble) }

        if (generating) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 12.dp, top = 4.dp)
                        .size(28.dp),
                    color = EazColors.Orange,
                    strokeWidth = 2.dp
                )
            }
        }

        if (showHeroConfirmDialog) {
            val regionCode = (lockedRegion ?: selectedRegion).ifBlank { localeRegion }
            val previewPrompt = prompt.trim().ifBlank {
                t(
                    "creator.hero_images.default_scene_prompt",
                    "Professional product photography with natural lighting and clean background"
                )
            }.let { p -> if (p.length > 200) p.take(200) + "…" else p }
            val lowBalance = confirmBalanceEaz != null && confirmBalanceEaz!! + 1e-9 < HERO_GENERATE_COST_EAZ
            AlertDialog(
                onDismissRequest = { if (!generating) showHeroConfirmDialog = false },
                containerColor = Color(0xFF1F2937),
                titleContentColor = Color.White,
                textContentColor = Color.White.copy(alpha = 0.88f),
                title = {
                    Text(t("creator.hero_eazy.confirm_title", "Start generation?"))
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${t("creator.hero_eazy.confirm_cost_label", "Cost")}: " +
                                String.format(Locale.US, "%.1f", HERO_GENERATE_COST_EAZ) +
                                " ${t("creator.hero_eazy.eaz_unit", "EAZ")}"
                        )
                        when {
                            confirmBalanceEaz == null -> Text(
                                t("creator.hero_eazy.balance_loading", "Loading balance…"),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.55f)
                            )
                            else -> Text(
                                "${t("creator.hero_eazy.confirm_balance_label", "Your balance")}: " +
                                    String.format(Locale.US, "%.2f", confirmBalanceEaz!!) +
                                    " ${t("creator.hero_eazy.eaz_unit", "EAZ")}"
                            )
                        }
                        if (lowBalance) {
                            Text(
                                text = t(
                                    "creator.hero_eazy.insufficient_balance",
                                    "Not enough EAZ for this generation."
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = EazColors.Orange
                            )
                        }
                        selectedTop?.let {
                            Text(
                                "${t("creator.hero_eazy.confirm_product_line", "Product")}: ${it.title}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        selectedAddition?.let {
                            Text(
                                "${t("creator.hero_eazy.confirm_addition_line", "Second product")}: ${it.title}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(
                            "${t("creator.marketing.region", "Region")}: $regionCode",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "${t("creator.hero_eazy.confirm_prompt_label", "Prompt")}: $previewPrompt",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = if (modelImageBytes != null) {
                                t("creator.hero_eazy.confirm_model_yes", "Model image: uploaded")
                            } else {
                                t("creator.hero_eazy.confirm_model_no", "Model image: none")
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = if (backgroundImageBytes != null) {
                                t("creator.hero_eazy.confirm_background_yes", "Background image: uploaded")
                            } else {
                                t("creator.hero_eazy.confirm_background_no", "Background image: none")
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showHeroConfirmDialog = false
                            runHeroGenerate()
                        },
                        enabled = !generating && (confirmBalanceEaz == null || !lowBalance)
                    ) {
                        Text(
                            t("creator.common.confirm", "Confirm"),
                            color = if (!generating && (confirmBalanceEaz == null || !lowBalance)) {
                                EazColors.Orange
                            } else {
                                Color.Gray
                            }
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showHeroConfirmDialog = false },
                        enabled = !generating
                    ) {
                        Text(t("creator.common.cancel", "Cancel"), color = Color.White.copy(alpha = 0.85f))
                    }
                }
            )
        }

        generateStatus?.let { msg ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = msg,
                style = MaterialTheme.typography.bodySmall,
                color = if (generateStatusError) EazColors.Orange else textSecondary
            )
        }

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
            val cardUrls = product.shopImageUrls.ifEmpty {
                listOfNotNull(product.image)
                    .mapNotNull { normalizeShopifyImageUrl(it) }
                    .filter { it.isNotBlank() }
            }
            if (cardUrls.isNotEmpty()) {
                ShopStyleProductImages(
                    imageUrls = cardUrls,
                    contentDescription = product.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    cornerRadius = 0.dp
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
    t: (String, String) -> String
) {
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
    // Do not fall back to the full list when "used" / "unused" yields zero — that broke the filter UX.
    val filtered = filteredByUsage.filter {
        searchQuery.isBlank() ||
            it.title.contains(searchQuery.trim(), ignoreCase = true) ||
            (it.productType ?: "").contains(searchQuery.trim(), ignoreCase = true)
    }
    val filteredSig = remember(filtered) { filtered.joinToString("\u0001") { it.id } }
    var pickerPage by remember { mutableIntStateOf(1) }
    var selectedProductId by remember { mutableStateOf<String?>(null) }
    val pickerTotalPages = remember(filtered.size) {
        maxOf(1, (filtered.size + CREATIONS_PRODUCTS_PER_PAGE - 1) / CREATIONS_PRODUCTS_PER_PAGE)
    }
    LaunchedEffect(filteredSig, usageFilter, category, currentRegion, searchQuery) {
        pickerPage = 1
        selectedProductId = null
    }
    LaunchedEffect(pickerTotalPages) {
        if (pickerPage > pickerTotalPages) pickerPage = pickerTotalPages
    }
    val pagedFiltered = remember(filtered, pickerPage, pickerTotalPages) {
        val idx = (pickerPage - 1).coerceIn(0, (pickerTotalPages - 1).coerceAtLeast(0))
        val start = idx * CREATIONS_PRODUCTS_PER_PAGE
        filtered.drop(start).take(CREATIONS_PRODUCTS_PER_PAGE)
    }
    val pickerPaginationDensity = LocalDensity.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1F2937)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
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
                    FilterTabPill(
                        active = usageFilter == "unused",
                        label = t("creator.marketing.unused", "Unused"),
                        onClick = { onUsageFilterChange("unused") },
                        modifier = Modifier
                            .height(56.dp)
                            .widthIn(min = 108.dp)
                    )
                    FilterTabPill(
                        active = usageFilter == "used",
                        label = t("creator.marketing.used", "Used"),
                        onClick = { onUsageFilterChange("used") },
                        modifier = Modifier
                            .height(56.dp)
                            .widthIn(min = 108.dp)
                    )
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
                        Column(modifier = Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Fixed(2),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .pointerInput(pickerPage, pickerTotalPages) {
                                        var totalDrag = 0f
                                        val thresholdPx = with(pickerPaginationDensity) { 80.dp.toPx() }
                                        detectHorizontalDragGestures(
                                            onDragStart = { totalDrag = 0f },
                                            onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                                            onDragEnd = {
                                                when {
                                                    totalDrag > thresholdPx && pickerPage > 1 ->
                                                        pickerPage = pickerPage - 1
                                                    totalDrag < -thresholdPx && pickerPage < pickerTotalPages ->
                                                        pickerPage = pickerPage + 1
                                                }
                                            }
                                        )
                                    },
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                            items(pagedFiltered, key = { it.id }) { product ->
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
                                    val cardUrls = product.shopImageUrls.ifEmpty {
                                        listOfNotNull(product.image)
                                            .mapNotNull { normalizeShopifyImageUrl(it) }
                                            .filter { it.isNotBlank() }
                                    }
                                    if (cardUrls.isNotEmpty()) {
                                        ShopStyleProductImages(
                                            imageUrls = cardUrls,
                                            contentDescription = product.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                            cornerRadius = 0.dp
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
                            if (pickerTotalPages > 1) {
                                ProductPaginationDots(
                                    totalPages = pickerTotalPages,
                                    currentPage = pickerPage,
                                    onPageClick = { pickerPage = it },
                                    onSwipePrev = {
                                        if (pickerPage > 1) pickerPage = pickerPage - 1
                                    },
                                    onSwipeNext = {
                                        if (pickerPage < pickerTotalPages) pickerPage = pickerPage + 1
                                    },
                                    style = PaginationDotsStyle.Dark
                                )
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
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
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp),
            color = if (active) Color.White else Color(0xFF9CA3AF),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}
