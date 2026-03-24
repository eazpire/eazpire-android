package com.eazpire.creator.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Catalog card image rotation: matches web shop-create-product.js */
private const val CATALOG_ROTATION_MS = 1500L
private const val CATALOG_CROSSFADE_MS = 450

internal data class CatalogProduct(
    val productKey: String,
    val title: String,
    val mockUrls: List<String>
)

private sealed interface ShopCreateProductPhase {
    data object Closed : ShopCreateProductPhase
    data object Catalog : ShopCreateProductPhase
    data class Mode(val product: CatalogProduct) : ShopCreateProductPhase
    data class StudioGenerate(val product: CatalogProduct, val catalogProducts: List<CatalogProduct>) : ShopCreateProductPhase
    data class StudioUpload(val product: CatalogProduct, val imageUri: Uri) : ShopCreateProductPhase
}

/**
 * Shop Create Product: catalog → mode → native generate/upload (customer-design API, no WebView).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopCreateProductFlow(
    visible: Boolean,
    onDismiss: () -> Unit,
    api: CreatorApi,
    tokenStore: SecureTokenStore,
    region: String,
    translationStore: TranslationStore,
    translation: (String, String) -> String,
    onRequireLogin: () -> Unit = {}
) {
    var phase by remember(visible) {
        mutableStateOf(
            if (visible) ShopCreateProductPhase.Catalog else ShopCreateProductPhase.Closed
        )
    }
    var pendingUploadProduct by remember { mutableStateOf<CatalogProduct?>(null) }
    val uploadPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val p = pendingUploadProduct
        pendingUploadProduct = null
        if (p != null && uri != null) {
            phase = ShopCreateProductPhase.StudioUpload(p, uri)
        } else if (p != null) {
            phase = ShopCreateProductPhase.Mode(p)
        }
    }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var products by remember { mutableStateOf<List<CatalogProduct>>(emptyList()) }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() }

    LaunchedEffect(visible, region) {
        if (!visible) return@LaunchedEffect
        loading = true
        error = null
        try {
            val data = withContext(Dispatchers.IO) {
                api.getShopCreateProductCatalog(region)
            }
            if (!data.optBoolean("ok", false)) {
                error = data.optString("error", "catalog_error")
                products = emptyList()
            } else {
                val arr = data.optJSONArray("products") ?: JSONArray()
                val list = mutableListOf<CatalogProduct>()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val pk = o.optString("product_key", "").trim()
                    if (pk.isEmpty()) continue
                    val urls = mutableListOf<String>()
                    val mu = o.optJSONArray("mock_urls")
                    if (mu != null) {
                        for (j in 0 until mu.length()) {
                            val u = mu.optString(j, "").trim()
                            if (u.isNotEmpty()) urls.add(u)
                        }
                    }
                    if (urls.isEmpty()) {
                        o.optString("preview_image_url", "").trim().takeIf { it.isNotEmpty() }?.let { urls.add(it) }
                    }
                    list.add(
                        CatalogProduct(
                            productKey = pk,
                            title = o.optString("title", pk).ifBlank { pk },
                            mockUrls = urls
                        )
                    )
                }
                products = list
            }
        } catch (e: Exception) {
            error = e.message ?: "error"
            products = emptyList()
        } finally {
            loading = false
        }
    }

    if (!visible) return

    when (val current = phase) {
        ShopCreateProductPhase.Closed -> {}
        ShopCreateProductPhase.Catalog -> {
            ShopCatalogBottomSheet(
                loading = loading,
                error = error,
                products = products,
                translation = translation,
                onDismissRequest = onDismiss,
                onProductClick = { p -> phase = ShopCreateProductPhase.Mode(p) }
            )
        }
        is ShopCreateProductPhase.Mode -> {
            val p = current.product
            ShopModeBottomSheet(
                productTitle = p.title,
                translation = translation,
                onDismissRequest = { phase = ShopCreateProductPhase.Catalog },
                onGenerate = {
                    phase = ShopCreateProductPhase.StudioGenerate(p, products)
                },
                onUpload = {
                    pendingUploadProduct = p
                    uploadPicker.launch("image/*")
                }
            )
        }
        is ShopCreateProductPhase.StudioGenerate -> {
            val p = current.product
            ShopDesignStudioGenerateSheet(
                product = p,
                catalogProducts = current.catalogProducts,
                api = api,
                ownerId = ownerId,
                translationStore = translationStore,
                translation = translation,
                onDismiss = { phase = ShopCreateProductPhase.Mode(p) },
                onRequireLogin = onRequireLogin
            )
        }
        is ShopCreateProductPhase.StudioUpload -> {
            val p = current.product
            ShopUploadNativeSheet(
                product = p,
                imageUri = current.imageUri,
                api = api,
                ownerId = ownerId,
                translationStore = translationStore,
                translation = translation,
                onDismiss = { phase = ShopCreateProductPhase.Mode(p) },
                onRequireLogin = onRequireLogin
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShopCatalogBottomSheet(
    loading: Boolean,
    error: String?,
    products: List<CatalogProduct>,
    translation: (String, String) -> String,
    onDismissRequest: () -> Unit,
    onProductClick: (CatalogProduct) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    BackHandler(onBack = onDismissRequest)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color.White,
        modifier = Modifier.fillMaxHeight(0.92f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                )
            }
        }
    ) {
        ShopLightSheetTheme {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                CatalogSheetHeader(
                    translation = translation,
                    onClose = onDismissRequest
                )
                CatalogStep(
                    loading = loading,
                    error = error,
                    products = products,
                    translation = translation,
                    onProductClick = onProductClick
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShopModeBottomSheet(
    productTitle: String,
    translation: (String, String) -> String,
    onDismissRequest: () -> Unit,
    onGenerate: () -> Unit,
    onUpload: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    BackHandler(onBack = onDismissRequest)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color.White,
        modifier = Modifier.fillMaxHeight(0.92f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                )
            }
        }
    ) {
        ShopLightSheetTheme {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                ModeSheetHeader(
                    translation = translation,
                    onBack = onDismissRequest
                )
                ModeStep(
                    productTitle = productTitle,
                    translation = translation,
                    onGenerate = onGenerate,
                    onUpload = onUpload
                )
            }
        }
    }
}

@Composable
private fun CatalogSheetHeader(
    translation: (String, String) -> String,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = translation("creator.common.close", "Close")
            )
        }
        Text(
            text = translation("creator.shop_create_product.modal1_title", "Choose a catalog product"),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.width(48.dp))
    }
}

@Composable
private fun ModeSheetHeader(
    translation: (String, String) -> String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = translation("creator.common.back", "Back")
            )
        }
        Text(
            text = translation("creator.shop_create_product.modal2_title", "How do you want to create?"),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.width(48.dp))
    }
}

@Composable
private fun CatalogStep(
    loading: Boolean,
    error: String?,
    products: List<CatalogProduct>,
    translation: (String, String) -> String,
    onProductClick: (CatalogProduct) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            translation("creator.shop_create_product.private_hint", "Shop designs stay private."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        when {
            loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            error != null -> Text(error, color = MaterialTheme.colorScheme.error)
            products.isEmpty() -> Text(translation("creator.shop_create_product.empty", "No products"))
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(120.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(4.dp),
                userScrollEnabled = true
            ) {
                items(products, key = { it.productKey }) { p ->
                    CatalogProductCard(product = p) { onProductClick(p) }
                }
            }
        }
    }
}

@Composable
private fun ModeStep(
    productTitle: String,
    translation: (String, String) -> String,
    onGenerate: () -> Unit,
    onUpload: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text(
            text = productTitle,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShopSheetPrimaryButton(
                onClick = onGenerate,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
            ) {
                Text(
                    translation("creator.shop_create_product.generate", "Generate"),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Text(
                text = translation(
                    "creator.shop_create_product.generate_desc",
                    "Create an all-new design with AI from scratch."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShopSheetPrimaryButton(
                onClick = onUpload,
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                Text(
                    translation("creator.shop_create_product.upload", "Upload"),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Text(
                text = translation(
                    "creator.shop_create_product.upload_desc",
                    "Upload a finished design or image as it will appear on the product."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.92f)
            )
        }
    }
}

@Composable
private fun CatalogProductCard(
    product: CatalogProduct,
    onClick: () -> Unit
) {
    var urlIndex by remember(product.productKey) { mutableIntStateOf(0) }
    val urls = product.mockUrls

    LaunchedEffect(product.productKey, urls.size) {
        if (urls.size < 2) return@LaunchedEffect
        while (isActive) {
            delay(CATALOG_ROTATION_MS)
            urlIndex = (urlIndex + 1) % urls.size
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            Crossfade(
                targetState = urlIndex,
                animationSpec = tween(
                    durationMillis = CATALOG_CROSSFADE_MS,
                    easing = FastOutSlowInEasing
                ),
                label = "catalog_thumb"
            ) { idx ->
                val u = urls.getOrNull(idx % urls.size.coerceAtLeast(1)).orEmpty()
                if (u.isNotEmpty()) {
                    AsyncImage(
                        model = u,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Text(
            product.title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
