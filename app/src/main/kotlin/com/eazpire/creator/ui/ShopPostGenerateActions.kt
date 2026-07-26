package com.eazpire.creator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Post-generate / post-upload actions — parity with theme `eaz-shop-post-actions.js`.
 */
@Composable
internal fun ShopPostGenerateActionsPanel(
    jobId: String,
    product: CatalogProduct,
    catalogProducts: List<CatalogProduct>,
    api: CreatorApi,
    ownerId: String,
    translation: (String, String) -> String,
    onDismissSheet: () -> Unit,
    onRegenerate: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showAddProducts by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            translation(
                "creator.shop_create_product.post_ready",
                "Your shop design is ready. Save the product, discard, regenerate, or add more products."
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        message?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        ShopSheetPrimaryButton(
            onClick = {
                if (busy) return@ShopSheetPrimaryButton
                busy = true
                message = null
                scope.launch {
                    try {
                        val res = withContext(Dispatchers.IO) {
                            api.saveCustomerDesign(ownerId, jobId, productKey = product.productKey)
                        }
                        message = if (res.optBoolean("ok", false)) {
                            translation("creator.shop_create_product.post_save_ok", "Save started.")
                        } else {
                            res.optString("error").ifBlank {
                                res.optString(
                                    "message",
                                    translation("creator.shop_create_product.post_error", "Request failed")
                                )
                            }
                        }
                    } catch (e: Exception) {
                        message = e.message
                            ?: translation("creator.shop_create_product.post_error", "Request failed")
                    } finally {
                        busy = false
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(translation("creator.shop_create_product.post_save", "Save product"))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShopSheetOutlinedButton(
                onClick = {
                    if (busy) return@ShopSheetOutlinedButton
                    busy = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                api.discardCustomerDesign(ownerId, jobId)
                            }
                            onDismissSheet()
                        } catch (_: Exception) {
                            message = translation("creator.shop_create_product.post_error", "Request failed")
                            busy = false
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) {
                Text(translation("creator.shop_create_product.post_discard", "Discard"))
            }
            ShopSheetOutlinedButton(
                onClick = onRegenerate,
                enabled = !busy,
                modifier = Modifier.weight(1f)
            ) {
                Text(translation("creator.shop_create_product.post_regenerate", "Regenerate"))
            }
        }
        ShopSheetOutlinedButton(
            onClick = { showAddProducts = true },
            enabled = !busy && catalogProducts.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(translation("creator.shop_create_product.post_add_products", "Add products"))
        }
        ShopSheetOutlinedButton(
            onClick = onDismissSheet,
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(translation("creator.common.close", "Close"))
        }
    }

    if (showAddProducts) {
        var selected by remember {
            mutableStateOf(setOf(product.productKey))
        }
        AlertDialog(
            onDismissRequest = { if (!busy) showAddProducts = false },
            title = {
                Text(translation("creator.shop_create_product.post_add_products", "Add products"))
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(catalogProducts, key = { it.productKey }) { p ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = selected.contains(p.productKey),
                                onCheckedChange = { checked ->
                                    selected = if (checked) {
                                        selected + p.productKey
                                    } else {
                                        selected - p.productKey
                                    }
                                }
                            )
                            Text(
                                p.title.ifBlank { p.productKey },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val keys = selected.toList()
                        if (keys.isEmpty() || busy) return@TextButton
                        busy = true
                        message = null
                        scope.launch {
                            try {
                                val res = withContext(Dispatchers.IO) {
                                    api.saveCustomerDesign(ownerId, jobId, productKeys = keys)
                                }
                                message = if (res.optBoolean("ok", false)) {
                                    translation("creator.shop_create_product.post_save_ok", "Save started.")
                                } else {
                                    res.optString("error").ifBlank {
                                        res.optString(
                                            "message",
                                            translation(
                                                "creator.shop_create_product.post_error",
                                                "Request failed"
                                            )
                                        )
                                    }
                                }
                                showAddProducts = false
                            } catch (e: Exception) {
                                message = e.message
                                    ?: translation(
                                        "creator.shop_create_product.post_error",
                                        "Request failed"
                                    )
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) {
                    Text(translation("creator.common.save", "Save"))
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!busy) showAddProducts = false }) {
                    Text(translation("creator.common.cancel", "Cancel"))
                }
            }
        )
    }
}
