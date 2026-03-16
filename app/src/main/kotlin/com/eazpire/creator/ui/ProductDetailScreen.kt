package com.eazpire.creator.ui

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.ShopifyProductsApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Product Detail Screen – 1:1 wie Web Mobile PDP (eaz-pdp-main.liquid, eaz-redesign-pdp.css).
 * Layout: Info oben, Gallery, Mobile Options (Color/Size), Footer mit Qty + Add to Cart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailScreen(
    productHandle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val api = remember { ShopifyProductsApi() }
    val context = LocalContext.current
    var product by remember { mutableStateOf<ShopifyProductsApi.ProductDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var detailsSheetVisible by remember { mutableStateOf(false) }
    var selectedImageIndex by remember { mutableIntStateOf(0) }
    var quantity by remember { mutableIntStateOf(1) }

    LaunchedEffect(productHandle) {
        isLoading = true
        product = withContext(Dispatchers.IO) { api.getProductByHandle(productHandle) }
        isLoading = false
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
    if (p == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("Product not found", color = EazColors.TextSecondary)
        }
        return
    }

    val images = p.images.take(5)
    // Color/Size options – wie Web
    val colorOption = p.options.find { it.name.equals("Color", ignoreCase = true) || it.name.equals("Colour", ignoreCase = true) || it.name.equals("Farbe", ignoreCase = true) }
    val sizeOption = p.options.find { it.name.equals("Size", ignoreCase = true) || it.name.equals("Größe", ignoreCase = true) || it.name.equals("Groesse", ignoreCase = true) }
    var selectedColor by remember { mutableStateOf(colorOption?.values?.firstOrNull() ?: "") }
    var selectedSize by remember { mutableStateOf(sizeOption?.values?.firstOrNull() ?: "") }
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

    Column(modifier = modifier.fillMaxSize()) {
        // Top bar (back)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = EazColors.TextPrimary)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // pdp-info (order 1) – Brand, Title, Product Details btn, Subtitle
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                // Brand / Creator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF0F0F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            p.vendor.take(1).uppercase(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = EazColors.TextSecondary
                        )
                    }
                    Text(
                        p.vendor.ifBlank { "Creator" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = EazColors.TextPrimary
                    )
                }

                // Title row + Product Details btn
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        p.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = EazColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
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
                        Text("Product Details", style = MaterialTheme.typography.labelMedium, color = EazColors.TextPrimary)
                    }
                }

                // Subtitle (product type)
                if (p.productType.isNotBlank()) {
                    Text(
                        p.productType,
                        style = MaterialTheme.typography.bodySmall,
                        color = EazColors.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Gallery (order 2) – Thumbs + Main
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Thumbs (vertical)
                if (images.size > 1) {
                    Column(
                        modifier = Modifier
                            .width(52.dp)
                            .heightIn(max = 250.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        images.forEachIndexed { idx, url ->
                            val thumbActive = idx == selectedImageIndex
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF0F0F0))
                ) {
                    if (images.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(images[selectedImageIndex.coerceIn(0, images.size - 1)]).build(),
                            contentDescription = p.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    // Dots
                    if (images.size > 1) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            images.forEachIndexed { idx, _ ->
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
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
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
                            .padding(bottom = 14.dp),
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
                                    .border(1.5.dp, if (isActive) EazColors.TextPrimary else Color(0xFFDDDDDD), RoundedCornerShape(8.dp))
                                    .clickable { selectedSize = value }
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

            Spacer(modifier = Modifier.height(80.dp)) // Space for footer
        }
    }

    // Sticky footer (pdp-footer)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.97f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Qty
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFF5F5F5)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { if (quantity > 1) quantity-- },
                modifier = Modifier.size(34.dp)
            ) {
                Text("−", style = MaterialTheme.typography.titleMedium, color = EazColors.TextPrimary)
            }
            Text(
                "$quantity",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 8.dp),
                color = EazColors.TextPrimary
            )
            IconButton(
                onClick = { quantity++ },
                modifier = Modifier.size(34.dp)
            ) {
                Text("+", style = MaterialTheme.typography.titleMedium, color = EazColors.TextPrimary)
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        // Icons: Favorite, Copy, Share
        IconButton(onClick = { /* TODO */ }) {
            Icon(Icons.Default.Favorite, contentDescription = "Favorite", tint = EazColors.TextSecondary, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = {
            try {
                val clip = android.content.ClipData.newPlainText("url", p.url)
                (context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(clip)
            } catch (_: Exception) {}
        }) {
            Icon(Icons.Default.Link, contentDescription = "Copy", tint = EazColors.TextSecondary, modifier = Modifier.size(20.dp))
        }
        IconButton(onClick = {
            try {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, p.url)
                    type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share"))
            } catch (_: Exception) {}
        }) {
            Icon(Icons.Default.Share, contentDescription = "Share", tint = EazColors.TextSecondary, modifier = Modifier.size(20.dp))
        }
        // Price
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "CHF %.2f".format(price),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = EazColors.TextPrimary
            )
            if (comparePrice != null && comparePrice > price) {
                Text(
                    "CHF %.2f".format(comparePrice),
                    style = MaterialTheme.typography.labelSmall,
                    color = EazColors.TextSecondary
                )
            }
        }
        // Add to cart / Buy now
        IconButton(
            onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${p.url}?variant=${selectedVariant?.id}")))
                } catch (_: Exception) {}
            },
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFE5E7EB))
        ) {
            Icon(Icons.Default.ShoppingCart, contentDescription = "Add to cart", tint = EazColors.TextPrimary, modifier = Modifier.size(20.dp))
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(EazColors.Orange)
                .clickable(enabled = available) {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${p.url}?variant=${selectedVariant?.id}")))
                    } catch (_: Exception) {}
                }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text("Buy now", style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        }
    }

    // Product Details Modal
    if (detailsSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = { detailsSheetVisible = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    "Product Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = EazColors.TextPrimary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    p.bodyHtml.replace(Regex("<[^>]+>"), " ").trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = EazColors.TextPrimary,
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.5f
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
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
