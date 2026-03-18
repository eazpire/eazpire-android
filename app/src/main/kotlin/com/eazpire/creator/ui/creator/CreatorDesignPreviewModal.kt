package com.eazpire.creator.ui.creator

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.i18n.TranslationStore

/**
 * Design Preview Modal – 1:1 wie Web (creator-design-preview-modal)
 * Zeigt Design-Details, Produkt-Count, View in Shop
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorDesignPreviewModal(
    design: CreationDesign?,
    onDismiss: () -> Unit,
    translationStore: TranslationStore,
    storeBaseUrl: String = "https://allyoucanpink.com"
) {
    if (design == null) return

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val designUrl = design.id?.let { id ->
        "$storeBaseUrl/pages/my-creations?design_id=$id"
    } ?: storeBaseUrl

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E293B),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = design.imageUrl,
                    contentDescription = design.title,
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                design.title.ifBlank { "Design" },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )

            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    design.designSource,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )
                if (design.productsCount > 0) {
                    Text(
                        "${design.productsCount} ${translationStore.t("creator.design_modal.products", "products")}",
                        style = MaterialTheme.typography.labelMedium,
                        color = EazColors.Orange
                    )
                }
            }

            design.prompt?.takeIf { it.isNotBlank() }?.let { prompt ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    prompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 3
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(designUrl)))
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = EazColors.Orange),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    translationStore.t("creator.design_modal.view_in_shop", "View in shop"),
                    color = Color.White
                )
            }
        }
    }
}
