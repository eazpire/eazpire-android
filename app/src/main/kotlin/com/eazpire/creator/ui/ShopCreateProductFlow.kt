package com.eazpire.creator.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray

private const val SHOP_WEB_BASE = "https://www.eazpire.com"

private data class CatalogProduct(
    val productKey: String,
    val title: String,
    val mockUrls: List<String>
)

/**
 * Shop Create Product: catalog (online + mock rotation) → mode → open storefront design-generator in browser.
 */
@Composable
fun ShopCreateProductFlow(
    visible: Boolean,
    onDismiss: () -> Unit,
    api: CreatorApi,
    region: String,
    translation: (String, String) -> String
) {
    val context = LocalContext.current
    var step by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var products by remember { mutableStateOf<List<CatalogProduct>>(emptyList()) }
    var selected: CatalogProduct? by remember { mutableStateOf(null) }

    LaunchedEffect(visible) {
        if (!visible) {
            step = 0
            selected = null
            error = null
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

    if (!visible) return

    fun openGenerator(productKey: String, mode: String) {
        val u = Uri.parse(SHOP_WEB_BASE).buildUpon()
            .path("/pages/design-generator")
            .appendQueryParameter("eaz_shop_create", "1")
            .appendQueryParameter("product_key", productKey)
            .appendQueryParameter("eaz_shop_mode", mode)
            .build()
        context.startActivity(Intent(Intent.ACTION_VIEW, u))
        onDismiss()
    }

    when (step) {
        0 -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(translation("creator.shop_create_product.modal1_title", "Choose a product")) },
            text = {
                Column {
                    Text(
                        translation("creator.shop_create_product.private_hint", "Private in shop."),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    when {
                        loading -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator() }
                        error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                        products.isEmpty() -> Text(translation("creator.shop_create_product.empty", "No products"))
                        else -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(120.dp),
                            modifier = Modifier.heightIn(max = 400.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(4.dp)
                        ) {
                            items(products, key = { it.productKey }) { p ->
                                CatalogProductCard(product = p) {
                                    selected = p
                                    step = 1
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) {
                    Text(translation("creator.common.close", "Close"))
                }
            }
        )
        1 -> {
            val p = selected
            if (p == null) {
                step = 0
                return
            }
            AlertDialog(
                onDismissRequest = { step = 0 },
                title = { Text(translation("creator.shop_create_product.modal2_title", "How to create?")) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(p.title, style = MaterialTheme.typography.titleSmall)
                        Button(onClick = { openGenerator(p.productKey, "generate") }) {
                            Text(translation("creator.shop_create_product.generate", "Generate"))
                        }
                        Button(onClick = { openGenerator(p.productKey, "upload") }) {
                            Text(translation("creator.shop_create_product.upload", "Upload"))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { step = 0 }) {
                        Text(translation("creator.common.close", "Back"))
                    }
                }
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
    val url = urls.getOrNull(urlIndex % (urls.size.coerceAtLeast(1))) ?: ""

    LaunchedEffect(product.productKey, urls.size) {
        if (urls.size < 2) return@LaunchedEffect
        while (isActive) {
            delay(1000)
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
            if (url.isNotEmpty()) {
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth()
                )
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
