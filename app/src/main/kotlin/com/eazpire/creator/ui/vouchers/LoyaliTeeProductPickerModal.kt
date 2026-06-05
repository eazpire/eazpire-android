package com.eazpire.creator.ui.vouchers

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyStorefrontCartApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.auth.ShopSessionGuard
import com.eazpire.creator.cart.AppCartStore
import com.eazpire.creator.cart.StorefrontCartStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URLEncoder

private const val SHOP_ORIGIN = "https://www.eazpire.com"

private data class LoyaliteeProduct(
    val productId: String,
    val title: String,
    val imageUrl: String?,
    val variants: List<LoyaliteeVariant>
)

private data class LoyaliteeVariant(
    val id: String,
    val title: String,
    val available: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoyaliTeeProductPickerModal(
    rewardId: String?,
    customerId: String,
    api: CreatorApi,
    tokenStore: SecureTokenStore,
    t: (String, String) -> String,
    onDismiss: () -> Unit,
    onRedeemSuccess: () -> Unit,
    onCheckout: (String) -> Unit,
) {
    if (rewardId.isNullOrBlank()) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cartStore = remember { StorefrontCartStore(context) }
    val cartApi = remember { ShopifyStorefrontCartApi() }

    var loading by remember(rewardId) { mutableStateOf(true) }
    var redeeming by remember { mutableStateOf(false) }
    var products by remember { mutableStateOf<List<LoyaliteeProduct>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedProduct by remember { mutableStateOf<LoyaliteeProduct?>(null) }
    var selectedVariantId by remember { mutableStateOf<String?>(null) }

    suspend fun loadProducts(q: String) {
        loading = true
        try {
            val resp = withContext(Dispatchers.IO) { api.listLoyaliteeProducts(limit = 48, q = q.ifBlank { null }) }
            if (!resp.optBoolean("ok", false)) throw IllegalStateException(resp.optString("error", "load_failed"))
            val arr = resp.optJSONArray("products") ?: JSONArray()
            products = (0 until arr.length()).mapNotNull { i ->
                val p = arr.getJSONObject(i)
                val variantsArr = p.optJSONArray("variants") ?: JSONArray()
                val variants = (0 until variantsArr.length()).mapNotNull { vi ->
                    val v = variantsArr.getJSONObject(vi)
                    val id = v.optString("id").ifBlank { return@mapNotNull null }
                    val title = v.optString("title").ifBlank {
                        listOf(
                            v.optString("option1"),
                            v.optString("option2"),
                            v.optString("option3")
                        ).filter { it.isNotBlank() }.joinToString(" / ")
                    }
                    LoyaliteeVariant(id = id, title = title.ifBlank { id }, available = v.optBoolean("available", true))
                }
                LoyaliteeProduct(
                    productId = p.optString("product_id"),
                    title = p.optString("title"),
                    imageUrl = p.optString("image_url").takeIf { it.isNotBlank() },
                    variants = variants
                )
            }.filter { it.productId.isNotBlank() }
        } catch (_: Exception) {
            products = emptyList()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(rewardId) {
        searchQuery = ""
        selectedProduct = null
        selectedVariantId = null
        loadProducts("")
    }

    LaunchedEffect(searchQuery) {
        delay(280)
        loadProducts(searchQuery.trim())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (selectedProduct == null) {
                                t("eaz.loyalitee.picker_title", "Choose your free tee")
                            } else {
                                selectedProduct!!.title
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        if (selectedProduct != null) {
                            IconButton(onClick = {
                                selectedProduct = null
                                selectedVariantId = null
                            }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = t("eaz.loyalitee.back_to_grid", "Back to designs"))
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = t("accessibility.close", "Close"))
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (selectedProduct == null) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text(t("eaz.loyalitee.search_placeholder", "Search designs…")) },
                        singleLine = true
                    )
                    when {
                        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        products.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                t("eaz.loyalitee.no_products", "No eligible tees found."),
                                color = Color(0xFF6B7280)
                            )
                        }
                        else -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(140.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(products, key = { it.productId }) { product ->
                                Column(
                                    Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(10.dp))
                                        .clickable {
                                            selectedProduct = product
                                            selectedVariantId = product.variants.firstOrNull { it.available }?.id
                                        }
                                        .padding(8.dp)
                                ) {
                                    if (product.imageUrl != null) {
                                        AsyncImage(
                                            model = product.imageUrl,
                                            contentDescription = product.title,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f)
                                                .background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp))
                                        )
                                    }
                                    Text(
                                        product.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    val product = selectedProduct!!
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        if (product.imageUrl != null) {
                            AsyncImage(
                                model = product.imageUrl,
                                contentDescription = product.title,
                                modifier = Modifier
                                    .fillMaxWidth(0.55f)
                                    .aspectRatio(1f)
                                    .align(Alignment.CenterHorizontally)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            t("eaz.loyalitee.choose_variant", "Size & color"),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        LoyaliteeVariantChips(
                            variants = product.variants,
                            selectedId = selectedVariantId,
                            onSelect = { selectedVariantId = it },
                            soldOutLabel = t("eaz.loyalitee.sold_out", "Sold out")
                        )
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = {
                                val variantId = selectedVariantId ?: return@Button
                                scope.launch {
                                    redeeming = true
                                    try {
                                        withContext(Dispatchers.IO) {
                                            ShopSessionGuard.refreshAccessTokenIfNeeded(context, tokenStore)
                                        }
                                        val redeemResp = withContext(Dispatchers.IO) {
                                            api.redeemLoyaltyReward(
                                                customerId = customerId,
                                                rewardId = rewardId,
                                                productId = product.productId,
                                                variantId = variantId
                                            )
                                        }
                                        if (!redeemResp.optBoolean("ok", false)) {
                                            throw IllegalStateException(
                                                redeemResp.optString("error", redeemResp.optString("message", "redeem_failed"))
                                            )
                                        }
                                        val code = redeemResp.optString("discount_code")
                                        if (code.isBlank()) throw IllegalStateException("missing_discount_code")

                                        val customerToken = tokenStore.getAccessToken()?.trim()?.takeIf { it.isNotBlank() }
                                        val variantLong = variantId.toLongOrNull()
                                            ?: variantId.substringAfterLast("/").toLongOrNull()
                                            ?: throw IllegalStateException("invalid_variant")

                                        val cartResult = withContext(Dispatchers.IO) {
                                            cartApi.createCart(listOf(variantLong to 1), customerToken)
                                        }
                                        if (!cartResult.ok || cartResult.checkoutUrl.isNullOrBlank()) {
                                            throw IllegalStateException(cartResult.message ?: "cart_failed")
                                        }
                                        cartResult.cartId?.let { cartStore.cartId = it }
                                        AppCartStore.setCount(1)

                                        val redirect = URLEncoder.encode(cartResult.checkoutUrl, "UTF-8")
                                        val checkoutWithDiscount =
                                            "$SHOP_ORIGIN/discount/${URLEncoder.encode(code, "UTF-8")}?redirect=$redirect"

                                        onRedeemSuccess()
                                        onDismiss()
                                        onCheckout(checkoutWithDiscount)
                                    } catch (_: Exception) {
                                        Toast.makeText(
                                            context,
                                            t("eaz.loyalitee.redeem_error", "Could not redeem. Please try again."),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } finally {
                                        redeeming = false
                                    }
                                }
                            },
                            enabled = !redeeming && selectedVariantId != null &&
                                product.variants.any { it.id == selectedVariantId && it.available },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange)
                        ) {
                            if (redeeming) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(t("eaz.loyalitee.redeem_button", "Redeem & checkout"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LoyaliteeVariantChips(
    variants: List<LoyaliteeVariant>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    soldOutLabel: String
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        variants.forEach { variant ->
            val label = if (!variant.available) "${variant.title} — $soldOutLabel" else variant.title
            FilterChip(
                selected = selectedId == variant.id,
                onClick = { if (variant.available) onSelect(variant.id) },
                enabled = variant.available,
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}
