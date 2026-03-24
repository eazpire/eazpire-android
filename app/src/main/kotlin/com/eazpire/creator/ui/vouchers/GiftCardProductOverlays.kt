package com.eazpire.creator.ui.vouchers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.ShopifyProductsApi

/**
 * Bottom sheet for product selection — implemented as [Dialog] so it appears above the full-screen
 * gift card [Dialog] (ModalBottomSheet would render behind).
 */
@Composable
fun GiftCardProductPickerOverlay(
    onDismiss: () -> Unit,
    title: String,
    searchValue: String,
    onSearchChange: (String) -> Unit,
    searchLabel: String,
    grid: @Composable () -> Unit,
    footer: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable { onDismiss() }
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .heightIn(max = 580.dp)
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                tonalElevation = 4.dp,
                shadowElevation = 12.dp
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = searchValue,
                        onValueChange = onSearchChange,
                        label = { Text(searchLabel) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    grid()
                    Spacer(Modifier.height(8.dp))
                    footer()
                }
            }
        }
    }
}

/**
 * Variant / size selection for a recommended product (same worker source as PDP).
 */
@Composable
fun GiftCardProductVariantOverlay(
    productTitle: String,
    loading: Boolean,
    loadError: String?,
    detail: ShopifyProductsApi.ProductDetail?,
    selectedVariantId: Long?,
    onSelectVariant: (Long) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String,
    dismissLabel: String,
    cancelLabel: String,
    variantSectionLabel: String,
    noVariantsText: String,
    fmtMoney: (Double, String) -> String,
    currencyCode: String
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .heightIn(max = 560.dp),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 2.dp
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            productTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 2
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = dismissLabel)
                        }
                    }
                    val img = detail?.images?.firstOrNull()?.src
                    if (!img.isNullOrBlank()) {
                        AsyncImage(
                            model = img,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF3F4F6)),
                            contentScale = ContentScale.Fit
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    if (loading) {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (!loadError.isNullOrBlank()) {
                        Text(loadError, color = EazColors.TextSecondary, fontSize = 14.sp)
                    } else if (detail != null && detail.variants.isNotEmpty()) {
                        Text(
                            text = variantSectionLabel,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 220.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            contentPadding = PaddingValues(bottom = 8.dp)
                        ) {
                            items(detail.variants, key = { it.id }) { v ->
                                if (!v.available) return@items
                                val label = listOfNotNull(v.option1, v.option2, v.option3)
                                    .filter { !it.isNullOrBlank() }
                                    .joinToString(" · ")
                                    .ifBlank { "—" }
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            onSelectVariant(v.id)
                                        }
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedVariantId == v.id,
                                        onClick = { onSelectVariant(v.id) }
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(label, fontSize = 14.sp)
                                        Text(
                                            fmtMoney(v.price, currencyCode),
                                            fontSize = 12.sp,
                                            color = EazColors.TextSecondary
                                        )
                                    }
                                }
                            }
                        }
                    } else if (detail != null) {
                        Text(
                            text = noVariantsText,
                            color = EazColors.TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(cancelLabel)
                        }
                        Spacer(Modifier.size(8.dp))
                        TextButton(
                            onClick = onConfirm,
                            enabled = !loading && loadError == null && selectedVariantId != null && detail != null
                        ) {
                            Text(confirmLabel)
                        }
                    }
                }
            }
        }
    }
}
