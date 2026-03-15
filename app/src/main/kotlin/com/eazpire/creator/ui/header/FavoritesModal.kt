package com.eazpire.creator.ui.header

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Favorite item from get-favorites API */
data class FavoriteItem(
    val id: String,
    val productId: String,
    val variantId: String?,
    val productTitle: String,
    val productImage: String?,
    val variantTitle: String?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesModal(
    visible: Boolean,
    customerId: String?,
    api: CreatorApi,
    onDismiss: () -> Unit,
    onCountChange: (Int) -> Unit = {}
) {
    if (!visible) return

    val scope = remember { CoroutineScope(Dispatchers.Main) }
    var items by remember { mutableStateOf<List<FavoriteItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(visible, customerId) {
        if (!visible || customerId.isNullOrBlank()) {
            items = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        errorMsg = null
        try {
            val resp = api.getFavorites(customerId)
            if (resp.optBoolean("ok", false)) {
                val arr = resp.optJSONArray("items") ?: org.json.JSONArray()
                items = (0 until arr.length()).map { i ->
                    val obj = arr.optJSONObject(i) ?: JSONObject()
                    FavoriteItem(
                        id = obj.optString("id", ""),
                        productId = obj.optString("product_id", ""),
                        variantId = obj.optString("variant_id", null).takeIf { it.isNullOrBlank().not() },
                        productTitle = obj.optString("product_title", "Product"),
                        productImage = obj.optString("product_image", null).takeIf { it.isNullOrBlank().not() },
                        variantTitle = obj.optString("variant_title", null).takeIf { it.isNullOrBlank().not() }
                    )
                }
                onCountChange(items.size)
            } else {
                errorMsg = resp.optString("error", "Failed to load favorites")
            }
        } catch (e: Exception) {
            errorMsg = e.message ?: "Error loading favorites"
        } finally {
            loading = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = androidx.compose.ui.graphics.Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Favorites", style = MaterialTheme.typography.titleLarge, color = EazColors.TextPrimary)
                    Text(
                        if (items.isEmpty() && !loading) "No favorites yet" else "${items.size} item${if (items.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.bodySmall,
                        color = EazColors.TextSecondary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = EazColors.TextPrimary)
                }
            }

            when {
                customerId.isNullOrBlank() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Log in to save favorites",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EazColors.TextSecondary
                        )
                    }
                }
                loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Loading...", style = MaterialTheme.typography.bodyMedium, color = EazColors.TextSecondary)
                    }
                }
                errorMsg != null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(errorMsg!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
                items.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = null,
                                tint = EazColors.TextSecondary.copy(alpha = 0.3f),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                "No favorites yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = EazColors.TextSecondary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Text(
                                "Add products to your favorites while shopping.",
                                style = MaterialTheme.typography.bodySmall,
                                color = EazColors.TextSecondary
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        items(items) { item ->
                            FavoritesListItem(
                                item = item,
                                onRemove = {
                                    scope.launch {
                                        try {
                                            val resp = api.removeFavorite(customerId, item.productId, item.variantId)
                                            if (resp.optBoolean("ok", false)) {
                                                items = items.filter { it.productId != item.productId || it.variantId != item.variantId }
                                                onCountChange(items.size)
                                            }
                                        } catch (_: Exception) {}
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesListItem(
    item: FavoriteItem,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Color.Transparent)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(EazColors.OrangeBg.copy(alpha = 0.3f))
        ) {
            if (!item.productImage.isNullOrBlank()) {
                AsyncImage(
                    model = item.productImage,
                    contentDescription = item.productTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = EazColors.Orange.copy(alpha = 0.4f),
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.Center)
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = item.productTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = EazColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!item.variantTitle.isNullOrBlank()) {
                Text(
                    text = item.variantTitle!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = EazColors.TextSecondary
                )
            }
        }
        OutlinedButton(
            onClick = onRemove,
            modifier = Modifier.padding(0.dp),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text("Remove", style = MaterialTheme.typography.labelSmall)
        }
    }
}
