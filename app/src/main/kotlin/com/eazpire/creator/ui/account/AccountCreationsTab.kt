package com.eazpire.creator.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.util.DebugLog

/**
 * My Creations Tab – native Android UI.
 * Web reference: theme/sections/account-my-designs.liquid, account-my-products.liquid
 * Two subtabs: My Designs (Creator + Customer) | My Products
 * Filter: All | Creator | Customer
 */
data class DesignItem(
    val id: String,
    val imageUrl: String,
    val title: String,
    val source: String, // "creator" | "customer"
    val createdAt: Long
)

data class ProductItem(
    val id: Long,
    val designId: Long,
    val productKey: String,
    val productName: String,
    val previewUrl: String,
    val prompt: String?,
    val createdAt: Long
)

private val DESIGN_FILTERS = listOf("all" to "All", "creator" to "Creator", "customer" to "Customer")

@Composable
fun AccountCreationsTab(
    tokenStore: SecureTokenStore,
    modifier: Modifier = Modifier
) {
    val jwt = remember { runCatching { tokenStore.getJwt() }.getOrNull() }
    val ownerId = remember { runCatching { tokenStore.getOwnerId() }.getOrNull() ?: "" }

    var designsSubtab by remember { mutableStateOf(true) } // true = My Designs, false = My Products
    var designFilter by remember { mutableStateOf("all") }
    var designs by remember { mutableStateOf<List<DesignItem>>(emptyList()) }
    var products by remember { mutableStateOf<List<ProductItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val api = remember(jwt) { com.eazpire.creator.api.CreatorApi(jwt = jwt) }

    LaunchedEffect(ownerId, designsSubtab) {
        if (ownerId.isBlank()) return@LaunchedEffect
        isLoading = true
        errorMessage = null
        try {
            if (designsSubtab) {
                val creatorRes = api.listDesigns(ownerId, 100)
                val customerRes = api.getCustomerDesigns(ownerId)
                val creatorItems = (creatorRes.optJSONArray("items") ?: org.json.JSONArray()).let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                        val img = obj.optString("preview_url", "").ifBlank { obj.optString("original_url", "") }
                        if (img.isBlank()) return@mapNotNull null
                        DesignItem(
                            id = obj.optString("id", obj.optString("job_id", "")),
                            imageUrl = img,
                            title = obj.optString("title", obj.optString("prompt", "Design")).take(40),
                            source = "creator",
                            createdAt = (obj.opt("created_at") as? Number)?.toLong() ?: 0L
                        )
                    }
                }
                val customerItems = (customerRes.optJSONArray("designs") ?: org.json.JSONArray()).let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                        val img = obj.optString("preview_url", "").ifBlank { obj.optString("original_url", "") }
                        if (img.isBlank()) return@mapNotNull null
                        DesignItem(
                            id = obj.optString("id", obj.optString("job_id", "")),
                            imageUrl = img,
                            title = obj.optString("prompt", obj.optString("product_key", "Design")).take(40),
                            source = "customer",
                            createdAt = (obj.opt("created_at") as? Number)?.toLong() ?: 0L
                        )
                    }
                }
                designs = (creatorItems + customerItems).sortedByDescending { it.createdAt }
            } else {
                val resp = api.getCustomerProducts(ownerId)
                val arr = resp.optJSONArray("products") ?: org.json.JSONArray()
                products = (0 until arr.length()).mapNotNull { i ->
                    val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                    ProductItem(
                        id = obj.optLong("id", -1L),
                        designId = obj.optLong("design_id", -1L),
                        productKey = obj.optString("product_key", ""),
                        productName = obj.optString("product_name", obj.optString("product_key", "Product")),
                        previewUrl = obj.optString("preview_url", ""),
                        prompt = obj.optString("prompt").takeIf { it.isNotBlank() },
                        createdAt = (obj.opt("created_at") as? Number)?.toLong() ?: 0L
                    )
                }
            }
        } catch (e: Exception) {
            DebugLog.click("Creations load error: ${e.message}")
            errorMessage = "Failed to load"
        } finally {
            isLoading = false
        }
    }

    val filteredDesigns = when (designFilter) {
        "creator" -> designs.filter { it.source == "creator" }
        "customer" -> designs.filter { it.source == "customer" }
        else -> designs
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "My Creations",
            style = MaterialTheme.typography.titleMedium,
            color = EazColors.TextPrimary
        )
        Text(
            text = "Your designs and published products.",
            style = MaterialTheme.typography.bodySmall,
            color = EazColors.TextSecondary
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { designsSubtab = true },
                modifier = Modifier
                    .weight(1f)
                    .then(if (designsSubtab) Modifier.border(2.dp, EazColors.Orange, RoundedCornerShape(8.dp)) else Modifier)
            ) {
                Text("My Designs")
            }
            OutlinedButton(
                onClick = { designsSubtab = false },
                modifier = Modifier
                    .weight(1f)
                    .then(if (!designsSubtab) Modifier.border(2.dp, EazColors.Orange, RoundedCornerShape(8.dp)) else Modifier)
            ) {
                Text("My Products")
            }
        }

        if (designsSubtab) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DESIGN_FILTERS.forEach { (key, label) ->
                    val selected = designFilter == key
                    OutlinedButton(
                        onClick = { designFilter = key },
                        modifier = Modifier
                            .weight(1f)
                            .then(if (selected) Modifier.border(2.dp, EazColors.Orange, RoundedCornerShape(8.dp)) else Modifier)
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (ownerId.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
                    .background(EazColors.TopbarBorder.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Please log in", style = MaterialTheme.typography.bodyMedium, color = EazColors.TextSecondary)
            }
        } else if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(24.dp))
            }
        } else {
            errorMessage?.let { msg ->
                Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            if (designsSubtab) {
                if (filteredDesigns.isEmpty()) {
                    EmptyState(icon = Icons.Default.Palette, title = "No designs yet", subtitle = "Create your first design!")
                } else {
                    DesignGrid(designs = filteredDesigns)
                }
            } else {
                if (products.isEmpty()) {
                    EmptyState(icon = Icons.Default.ShoppingBag, title = "No products yet", subtitle = "No published products yet.")
                } else {
                    ProductGrid(products = products)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp)
            .background(EazColors.TopbarBorder.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.padding(bottom = 8.dp), tint = EazColors.TextSecondary)
            Text(title, style = MaterialTheme.typography.bodyMedium, color = EazColors.TextSecondary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = EazColors.TextSecondary)
        }
    }
}

@Composable
private fun DesignGrid(designs: List<DesignItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        designs.chunked(2).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { design ->
                    Card(
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            ) {
                                AsyncImage(
                                    model = design.imageUrl,
                                    contentDescription = design.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .background(
                                            if (design.source == "creator") Color(0xFF3B82F6) else Color(0xFF10B981),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        design.source.replaceFirstChar { it.uppercase() },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                            }
                            Text(
                                design.title.ifBlank { "Design" },
                                style = MaterialTheme.typography.bodySmall,
                                color = EazColors.TextPrimary,
                                modifier = Modifier.padding(8.dp),
                                maxLines = 2
                            )
                        }
                    }
                }
                if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ProductGrid(products: List<ProductItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        products.chunked(2).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { product ->
                    Card(
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            ) {
                                if (product.previewUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = product.previewUrl,
                                        contentDescription = product.productName,
                                        modifier = Modifier.fillMaxWidth(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(EazColors.TopbarBorder.copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = EazColors.TextSecondary)
                                    }
                                }
                            }
                            Text(
                                product.productName.ifBlank { "Product" },
                                style = MaterialTheme.typography.bodySmall,
                                color = EazColors.TextPrimary,
                                modifier = Modifier.padding(8.dp),
                                maxLines = 2
                            )
                        }
                    }
                }
                if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}
