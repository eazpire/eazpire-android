package com.eazpire.creator.ui.account

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyProductsApi
import com.eazpire.creator.auth.AuthConfig
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.chat.EazySidebarTab
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.locale.LocaleStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val VIDEO_GENERATE_COST_EAZ = 2.0

/**
 * Marketing video generation (parity with web creator-content-creation-video.js):
 * reference image, products (top + addition), prompt, async job.
 */
@Composable
fun AccountVideoTab(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore? = null,
    darkMode: Boolean = false,
    onGenerated: (() -> Unit)? = null,
    onVideoJobStarted: (jobId: String, summary: String) -> Unit = { _, _ -> },
    onOpenEazyChat: (EazySidebarTab) -> Unit = {},
    onVideoEazyReadyChange: (Boolean) -> Unit = {},
    onVideoGeneratingChange: (Boolean) -> Unit = {},
    /** Increment from docked/header „Start generation“ (same as hero tab). */
    headerStartNonce: Int = 0,
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
    var referenceImageUri by remember { mutableStateOf<Uri?>(null) }
    var referenceImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var referenceMime by remember { mutableStateOf("image/jpeg") }
    var prompt by remember { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    SideEffect { onVideoGeneratingChange(generating) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    LaunchedEffect(headerStartNonce) {
        if (headerStartNonce == 0) return@LaunchedEffect
        if (generating) return@LaunchedEffect
        if (ownerId.isBlank() || (selectedTop == null && selectedAddition == null) ||
            referenceImageBytes == null || referenceImageBytes?.isEmpty() == true
        ) {
            return@LaunchedEffect
        }
        showConfirmDialog = true
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
    var usageFilter by remember { mutableStateOf("unused") }
    var modalSearchQuery by remember { mutableStateOf("") }

    val referencePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            referenceImageUri = it
            referenceMime = context.contentResolver.getType(it) ?: "image/jpeg"
            scope.launch {
                try {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        referenceImageBytes = stream.readBytes()
                    }
                } catch (_: Exception) {}
            }
        }
    }

    LaunchedEffect(showConfirmDialog) {
        if (!showConfirmDialog || ownerId.isBlank()) return@LaunchedEffect
        confirmBalanceEaz = null
        try {
            val b = withContext(Dispatchers.IO) { api.getBalance(ownerId) }
            if (b.optBoolean("ok", false) && b.has("balance_eaz")) {
                confirmBalanceEaz = b.optDouble("balance_eaz", 0.0)
            }
        } catch (_: Exception) {}
    }

    fun loadProducts() {
        scope.launch {
            if (ownerId.isBlank()) return@launch
            loading = true
            try {
                try {
                    val usedResp = api.getVideoUsedProducts(ownerId)
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
                        arrOnline.optJSONObject(i)?.optString("product_key", "")?.takeIf { it.isNotBlank() }
                            ?.let { allowedKeys.add(it) }
                    }
                    for (i in 0 until arrPreview.length()) {
                        arrPreview.optJSONObject(i)?.optString("product_key", "")?.takeIf { it.isNotBlank() }
                            ?.let { allowedKeys.add(it) }
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

    fun runVideoGenerate() {
        if (ownerId.isBlank() || generating) return
        val top = selectedTop
        val add = selectedAddition
        if (top == null && add == null) {
            generateStatus = t("creator.hero_images.select_products_first", "Select at least one product.")
            generateStatusError = true
            return
        }
        val bytes = referenceImageBytes
        if (bytes == null || bytes.isEmpty()) {
            generateStatus = t("creator.content_creation.videos.reference_required", "Upload a reference image.")
            generateStatusError = true
            return
        }
        val idList = listOfNotNull(top?.id, add?.id).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (idList.isEmpty()) return
        val finalPrompt = prompt.trim().ifBlank {
            t("creator.content_creation.videos.default_prompt", "Cinematic product showcase, smooth camera motion")
        }
        scope.launch {
            generating = true
            generateStatus = null
            generateStatusError = false
            try {
                val up = api.uploadHeroImage(ownerId, "reference", bytes, referenceMime)
                val sourceUrl = if (up.optBoolean("ok", false)) {
                    up.optString("image_url", "").takeIf { it.isNotBlank() }
                } else null
                if (sourceUrl == null) {
                    generateStatus = t("creator.hero_images.generation_failed_generic", "Generation failed.")
                    generateStatusError = true
                    generating = false
                    return@launch
                }
                val imageUrls = listOfNotNull(top?.let { heroProductMainImageUrl(it) }, add?.let { heroProductMainImageUrl(it) })
                    .distinct()
                val regionCode = (lockedRegion ?: selectedRegion).ifBlank { localeRegion }
                val resp = api.videoGenerate(
                    ownerId = ownerId,
                    productIds = idList,
                    prompt = finalPrompt,
                    sourceImageUrl = sourceUrl,
                    productImageUrls = imageUrls,
                    region = regionCode
                )
                if (resp.optBoolean("ok", false) && resp.optString("job_id", "").isNotBlank()) {
                    val jobId = resp.optString("job_id", "")
                    val summary = buildString {
                        appendLine(t("creator.content_creation.videos.job_summary_title", "Video generation"))
                        selectedTop?.let { appendLine("• ${it.title}") }
                        selectedAddition?.let { appendLine("• ${it.title}") }
                        appendLine(
                            "${t("creator.marketing.region", "Region")}: ${(lockedRegion ?: selectedRegion).ifBlank { localeRegion }}"
                        )
                        val p = prompt.trim().ifBlank {
                            t("creator.content_creation.videos.default_prompt", "Cinematic product showcase, smooth camera motion")
                        }
                        appendLine(
                            "${t("creator.hero_eazy.job_summary_prompt", "Prompt")}: ${p.take(120)}${if (p.length > 120) "…" else ""}"
                        )
                    }.trim()
                    onVideoJobStarted(jobId, summary)
                    onGenerated?.invoke()
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

    LaunchedEffect(localeRegion) {
        if (lockedRegion == null && selectedTop == null && selectedAddition == null) {
            selectedRegion = localeRegion
        }
    }

    LaunchedEffect(ownerId, selectedRegion) {
        if (ownerId.isNotBlank()) loadProducts()
    }

    val canGenerate = ownerId.isNotBlank() && !generating &&
        (selectedTop != null || selectedAddition != null) && referenceImageBytes != null
    SideEffect { onVideoEazyReadyChange(canGenerate) }

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

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            HeroUploadCard(
                label = t("creator.content_creation.videos.reference_image", "Reference image"),
                iconEmoji = "\uD83D\uDCF7",
                imageUri = referenceImageUri,
                onPick = { referencePicker.launch("image/*") },
                onClear = { referenceImageUri = null; referenceImageBytes = null },
                helperText = t("creator.content_creation.videos.reference_hint", "Start frame for the video"),
                modifier = Modifier.widthIn(max = 280.dp)
            )
        }
        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    t(
                        "creator.content_creation.videos.prompt_placeholder",
                        "Describe motion and style for your video…"
                    )
                )
            },
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

        if (showConfirmDialog) {
            val regionCode = (lockedRegion ?: selectedRegion).ifBlank { localeRegion }
            val previewPrompt = prompt.trim().ifBlank {
                t("creator.content_creation.videos.default_prompt", "Cinematic product showcase, smooth camera motion")
            }.let { p -> if (p.length > 200) p.take(200) + "…" else p }
            val lowBalance = confirmBalanceEaz != null && confirmBalanceEaz!! + 1e-9 < VIDEO_GENERATE_COST_EAZ
            AlertDialog(
                onDismissRequest = { if (!generating) showConfirmDialog = false },
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
                                String.format(Locale.US, "%.1f", VIDEO_GENERATE_COST_EAZ) +
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
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showConfirmDialog = false
                            runVideoGenerate()
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
                        onClick = { showConfirmDialog = false },
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
