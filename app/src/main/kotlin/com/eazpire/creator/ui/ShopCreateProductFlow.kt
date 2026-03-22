package com.eazpire.creator.ui

import android.net.Uri
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
import androidx.compose.material3.Button
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray

private const val SHOP_WEB_BASE = "https://www.eazpire.com"

/** Catalog card image rotation: matches web shop-create-product.js */
private const val CATALOG_ROTATION_MS = 1500L
private const val CATALOG_CROSSFADE_MS = 450

private data class CatalogProduct(
    val productKey: String,
    val title: String,
    val mockUrls: List<String>
)

/**
 * Shop Create Product: catalog → mode (bottom sheet) → design studio in a standalone full-screen WebView modal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopCreateProductFlow(
    visible: Boolean,
    onDismiss: () -> Unit,
    api: CreatorApi,
    region: String,
    translation: (String, String) -> String
) {
    var step by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var products by remember { mutableStateOf<List<CatalogProduct>>(emptyList()) }
    var selected: CatalogProduct? by remember { mutableStateOf(null) }
    var studioUrl by remember { mutableStateOf<String?>(null) }
    var studioTitle by remember { mutableStateOf("") }

    LaunchedEffect(visible) {
        if (!visible) {
            step = 0
            selected = null
            error = null
            studioUrl = null
            studioTitle = ""
            return@LaunchedEffect
        }
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

    fun buildStudioUrl(productKey: String, mode: String): String =
        Uri.parse(SHOP_WEB_BASE).buildUpon()
            .path("/pages/design-generator")
            .appendQueryParameter("eaz_shop_create", "1")
            .appendQueryParameter("eaz_shop_embed", "1")
            .appendQueryParameter("product_key", productKey)
            .appendQueryParameter("eaz_shop_mode", mode)
            .build()
            .toString()

    if (!visible) return

    studioUrl?.let { url ->
        ShopDesignStudioDialog(
            url = url,
            productTitle = studioTitle,
            translation = translation,
            onDismiss = { studioUrl = null }
        )
        return
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        modifier = Modifier.fillMaxHeight(0.92f),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            SheetHeader(
                step = step,
                translation = translation,
                onNavigateBack = {
                    when (step) {
                        0 -> onDismiss()
                        1 -> step = 0
                        else -> onDismiss()
                    }
                }
            )

            when (step) {
                0 -> CatalogStep(
                    loading = loading,
                    error = error,
                    products = products,
                    translation = translation,
                    onProductClick = { p ->
                        selected = p
                        step = 1
                    }
                )
                1 -> {
                    val p = selected
                    if (p == null) {
                        step = 0
                    } else {
                        ModeStep(
                            productTitle = p.title,
                            translation = translation,
                            onGenerate = {
                                studioTitle = p.title
                                studioUrl = buildStudioUrl(p.productKey, "generate")
                            },
                            onUpload = {
                                studioTitle = p.title
                                studioUrl = buildStudioUrl(p.productKey, "upload")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(
    step: Int,
    translation: (String, String) -> String,
    onNavigateBack: () -> Unit
) {
    val title = when (step) {
        0 -> translation("creator.shop_create_product.modal1_title", "Choose a catalog product")
        1 -> translation("creator.shop_create_product.modal2_title", "How do you want to create?")
        else -> ""
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onNavigateBack) {
            Icon(
                imageVector = if (step == 0) Icons.Default.Close else Icons.Default.ArrowBack,
                contentDescription = if (step == 0) {
                    translation("creator.common.close", "Close")
                } else {
                    translation("creator.common.back", "Back")
                }
            )
        }
        Text(
            text = title,
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
            Button(
                onClick = onGenerate,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
            ) {
                Text(translation("creator.shop_create_product.generate", "Generate"))
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
            Button(
                onClick = onUpload,
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                Text(translation("creator.shop_create_product.upload", "Upload"))
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
