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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.AuthConfig
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.locale.LocaleStore
import kotlinx.coroutines.delay
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

data class HeroProduct(
    val id: String,
    val title: String,
    val image: String?,
    val productType: String?,
    val productKey: String?,
    val region: String
)

@Composable
fun AccountHeroImagesTab(
    tokenStore: SecureTokenStore,
    darkMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val textPrimary = if (darkMode) Color.White else EazColors.TextPrimary
    val textSecondary = if (darkMode) Color.White.copy(alpha = 0.7f) else EazColors.TextSecondary
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }
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
    var generating by remember { mutableStateOf(false) }
    var jobId by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showProductPicker by remember { mutableStateOf(false) }
    var pickerCategory by remember { mutableStateOf("top") }
    var selectedRegion by remember { mutableStateOf(localeRegion) }
    var lockedRegion by remember { mutableStateOf<String?>(null) }
    var regionLockMessage by remember { mutableStateOf<String?>(null) }
    var showConfirmDialog by remember { mutableStateOf(false) }
    var confirmCost by remember { mutableStateOf(0.5) }
    var confirmBalance by remember { mutableStateOf(0.0) }

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
                val catalogResp = api.getCatalogProducts(selectedRegion)
                val allowedKeys = mutableSetOf<String>()
                if (catalogResp.optBoolean("ok", false)) {
                    val arrOnline = catalogResp.optJSONArray("products") ?: org.json.JSONArray()
                    val arrPreview = catalogResp.optJSONArray("preview_products") ?: org.json.JSONArray()
                    for (i in 0 until arrOnline.length()) {
                        arrOnline.optJSONObject(i)?.optString("product_key", null)?.takeIf { it.isNotBlank() }?.let { allowedKeys.add(it) }
                    }
                    for (i in 0 until arrPreview.length()) {
                        arrPreview.optJSONObject(i)?.optString("product_key", null)?.takeIf { it.isNotBlank() }?.let { allowedKeys.add(it) }
                    }
                }

                val resp = api.getShopifyProducts(AuthConfig.SHOP_DOMAIN, ownerId, selectedRegion)
                if (resp.optBoolean("ok", false)) {
                    val arr = resp.optJSONArray("products") ?: org.json.JSONArray()
                    products = (0 until arr.length()).mapNotNull { i ->
                        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                        val imgObj = obj.optJSONObject("image")
                        val img = when {
                            imgObj != null -> imgObj.optString("src", null).takeIf { it.isNotBlank() }
                            else -> obj.optString("image", null).takeIf { it.isNotBlank() }
                        }
                        val id = obj.optString("id", "")
                        if (id.isBlank()) return@mapNotNull null
                        val productKey = obj.optString("product_key", null).takeIf { it.isNotBlank() }
                        if (allowedKeys.isNotEmpty() && (productKey == null || !allowedKeys.contains(productKey))) return@mapNotNull null
                        HeroProduct(
                            id = id,
                            title = obj.optString("title", ""),
                            image = img,
                            productType = obj.optString("product_type", null).takeIf { it.isNotBlank() },
                            productKey = productKey,
                            region = selectedRegion
                        )
                    }
                }
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

    fun extractImageUrl(product: HeroProduct?): String? = product?.image

    fun doGenerate() {
        val productIds = listOfNotNull(selectedTop?.id, selectedAddition?.id).distinct()
        if (productIds.isEmpty()) return

        val productImageUrls = listOfNotNull(
            extractImageUrl(selectedTop),
            extractImageUrl(selectedAddition)
        ).distinct()

        var modelUrl: String? = null
        var backgroundUrl: String? = null

        scope.launch {
            generating = true
            statusMessage = "Uploading..."
            try {
                if (modelImageBytes != null && modelImageUri != null) {
                    val mimeType = context.contentResolver.getType(modelImageUri!!) ?: "image/jpeg"
                    val resp = api.uploadHeroImage(ownerId, "model", modelImageBytes!!, mimeType)
                    if (resp.optBoolean("ok", false)) {
                        modelUrl = resp.optString("image_url", null).takeIf { it.isNotBlank() }
                    }
                }
                if (backgroundImageBytes != null && backgroundImageUri != null) {
                    val mimeType = context.contentResolver.getType(backgroundImageUri!!) ?: "image/jpeg"
                    val resp = api.uploadHeroImage(ownerId, "background", backgroundImageBytes!!, mimeType)
                    if (resp.optBoolean("ok", false)) {
                        backgroundUrl = resp.optString("image_url", null).takeIf { it.isNotBlank() }
                    }
                }
                statusMessage = "Generating..."
                val genResp = api.heroGenerate(
                    ownerId = ownerId,
                    productIds = productIds,
                    prompt = prompt.ifBlank { "Professional product photography with natural lighting and clean background" },
                    productImageUrls = productImageUrls,
                    modelImageUrl = modelUrl,
                    backgroundImageUrl = backgroundUrl,
                    region = selectedRegion
                )
                if (genResp.optBoolean("ok", false)) {
                    val jid = genResp.optString("job_id", null)
                    if (!jid.isNullOrBlank()) {
                        jobId = jid
                        statusMessage = "Running..."
                        while (true) {
                            delay(1500)
                            val status = api.pollJob(jid)
                            if (status.optBoolean("not_found", false)) break
                            statusMessage = status.optString("message", "Running...")
                            if (status.optBoolean("done", false)) {
                                statusMessage = if (status.optString("message", "").lowercase().contains("fail"))
                                    status.optString("message", "Failed")
                                else "Done!"
                                break
                            }
                        }
                    }
                } else {
                    statusMessage = genResp.optString("error", "Failed")
                }
            } catch (e: Exception) {
                statusMessage = e.message ?: "Error"
            }
            generating = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Hero Images", style = MaterialTheme.typography.titleMedium, color = textPrimary)
        Spacer(Modifier.height(8.dp))
        Text("Generate marketing images with AI. Select products and optional model/background.", style = MaterialTheme.typography.bodySmall, color = textSecondary)
        Spacer(Modifier.height(24.dp))

        Text("Products", style = MaterialTheme.typography.labelLarge, color = textSecondary)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroProductCard(
                label = "Top",
                product = selectedTop,
                onClick = { pickerCategory = "top"; showProductPicker = true },
                modifier = Modifier.weight(1f),
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )
            HeroProductCard(
                label = "Addition",
                product = selectedAddition,
                onClick = { pickerCategory = "addition"; showProductPicker = true },
                modifier = Modifier.weight(1f),
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )
        }
        if (!regionLockMessage.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(regionLockMessage!!, style = MaterialTheme.typography.bodySmall, color = EazColors.Orange)
        }
        Spacer(Modifier.height(24.dp))

        Text("Optional uploads", style = MaterialTheme.typography.labelLarge, color = textSecondary)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroUploadCard(
                label = "Model",
                imageUri = modelImageUri,
                onPick = { modelPicker.launch("image/*") },
                onClear = { modelImageUri = null; modelImageBytes = null },
                modifier = Modifier.weight(1f),
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )
            HeroUploadCard(
                label = "Background",
                imageUri = backgroundImageUri,
                onPick = { backgroundPicker.launch("image/*") },
                onClear = { backgroundImageUri = null; backgroundImageBytes = null },
                modifier = Modifier.weight(1f),
                textPrimary = textPrimary,
                textSecondary = textSecondary
            )
        }
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Additional prompt (optional)") },
            minLines = 2,
            maxLines = 4
        )
        Spacer(Modifier.height(24.dp))

        if (statusMessage != null) {
            Text(statusMessage!!, style = MaterialTheme.typography.bodySmall, color = EazColors.Orange)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                scope.launch {
                    val balResp = api.getBalance(ownerId)
                    val balance = if (balResp.optBoolean("ok", false)) balResp.optDouble("balance_eaz", 0.0) else 0.0
                    confirmCost = 0.5
                    confirmBalance = balance
                    showConfirmDialog = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !generating && (selectedTop != null || selectedAddition != null)
        ) {
            if (generating) {
                CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.size(8.dp))
            }
            Text(if (generating) "Generating..." else "Generate Hero Image")
        }

        if (showConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text("Generate Hero Image?") },
                text = {
                    Column {
                        Text("Cost: $confirmCost EAZ")
                        Text("Your balance: $confirmBalance EAZ")
                        Text("Duration: ~30-45 sec")
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        showConfirmDialog = false
                        doGenerate()
                    }) { Text("Generate") }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) { Text("Cancel") }
                }
            )
        }

        if (showProductPicker) {
            HeroProductPickerModal(
                products = products,
                category = pickerCategory,
                currentRegion = selectedRegion,
                lockedRegion = lockedRegion,
                loading = loading,
                onRegionChange = { nextRegion ->
                    if (lockedRegion != null && lockedRegion != nextRegion) {
                        regionLockMessage = "Nur eine Region pro Hero-Entwurf erlaubt."
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
                onDismiss = { showProductPicker = false }
            )
        }
    }
}

@Composable
private fun HeroProductCard(
    label: String,
    product: HeroProduct?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textPrimary: Color = EazColors.TextPrimary,
    textSecondary: Color = EazColors.TextSecondary
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, EazColors.Orange.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
            .background(EazColors.OrangeBg.copy(alpha = 0.2f))
            .clickable(onClick = onClick)
    ) {
        if (product != null) {
            if (!product.image.isNullOrBlank()) {
                AsyncImage(
                    model = product.image,
                    contentDescription = product.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(EazColors.Orange.copy(alpha = 0.8f))
                    .padding(8.dp)
            ) {
                Text(product.title, style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White, maxLines = 1)
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Add, null, tint = EazColors.Orange, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = EazColors.Orange)
            }
        }
    }
}

@Composable
private fun HeroUploadCard(
    label: String,
    imageUri: Uri?,
    onPick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    textPrimary: Color = EazColors.TextPrimary,
    textSecondary: Color = EazColors.TextSecondary
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, EazColors.Orange.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .background(EazColors.OrangeBg.copy(alpha = 0.15f))
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
            ) {
                Icon(Icons.Default.Close, null, tint = textPrimary, modifier = Modifier.size(16.dp))
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.Image, null, tint = EazColors.Orange.copy(alpha = 0.7f), modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = textSecondary)
            }
        }
    }
}

@Composable
private fun HeroProductPickerModal(
    products: List<HeroProduct>,
    category: String,
    currentRegion: String,
    lockedRegion: String?,
    loading: Boolean,
    onRegionChange: (String) -> Unit,
    onSelect: (HeroProduct) -> Unit,
    onDismiss: () -> Unit
) {
    val topKeywords = listOf("t-shirt", "hoodie", "sweatshirt", "shirt", "top", "tee", "polo", "jacket", "sweater", "pullover", "jacke", "oberteil")
    fun isTopProduct(p: HeroProduct): Boolean {
        val t = (p.title + " " + (p.productType ?: "")).lowercase()
        return topKeywords.any { t.contains(it) }
    }
    val filtered = when (category) {
        "top" -> products.filter { isTopProduct(it) }
        "addition" -> products.filter { !isTopProduct(it) }
        else -> products
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Select $category", style = MaterialTheme.typography.titleMedium, color = EazColors.TextPrimary)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null, tint = EazColors.TextPrimary)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                                if (active) EazColors.Orange else EazColors.Orange.copy(alpha = 0.4f),
                                RoundedCornerShape(999.dp)
                            )
                            .background(if (active) EazColors.Orange.copy(alpha = 0.25f) else EazColors.OrangeBg.copy(alpha = 0.12f))
                            .clickable(enabled = !lockedOut) { onRegionChange(code) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        color = if (lockedOut) EazColors.TextSecondary.copy(alpha = 0.6f) else EazColors.TextPrimary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            if (loading) {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = EazColors.Orange)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.height(400.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered) { product ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, EazColors.Orange.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .clickable { onSelect(product) }
                        ) {
                            if (!product.image.isNullOrBlank()) {
                                AsyncImage(
                                    model = product.image,
                                    contentDescription = product.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .fillMaxWidth()
                                    .background(EazColors.Orange.copy(alpha = 0.8f))
                                    .padding(4.dp)
                            ) {
                                Text(product.title, style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
