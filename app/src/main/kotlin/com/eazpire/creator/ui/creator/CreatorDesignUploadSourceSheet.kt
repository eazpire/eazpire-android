package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.modal.EazBottomSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorDesignUploadSourceSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onDevice: () -> Unit,
    onCanvas: () -> Unit,
    translationStore: TranslationStore,
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun t(key: String, fallback: String) = translationStore.t(key, fallback)
    EazBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A),
        dragHandle = null,
        maxHeightFraction = 0.55f,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        t("creator.my_creations.upload_choose_title", "Add your finished design"),
                        color = Color.White,
                        fontSize = 18.sp,
                    )
                    Text(
                        t("creator.my_creations.upload_choose_subtitle", "Where is the file?"),
                        color = Color(0xFF9CA3AF),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = t("creator.my_creations.canvas_close", "Close"), tint = Color.White)
                }
            }
            SourceOptionCard(
                title = t("creator.my_creations.upload_from_device", "This device"),
                desc = t("creator.my_creations.upload_from_device_desc", "Choose PNG, JPG, or SVG from your computer"),
                onClick = onDevice,
            )
            SourceOptionCard(
                title = t("creator.my_creations.upload_from_canvas", "Canvas"),
                desc = t("creator.my_creations.upload_from_canvas_desc", "Create a design on a blank transparent canvas"),
                onClick = onCanvas,
                highlight = true,
            )
        }
    }
}

@Composable
private fun SourceOptionCard(
    title: String,
    desc: String,
    onClick: () -> Unit,
    highlight: Boolean = false,
) {
    val border = if (highlight) EazColors.Orange else Color.White.copy(alpha = 0.18f)
    val bg = if (highlight) Color(0x14F59E0B) else Color(0xFF111827)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, color = Color.White, fontSize = 16.sp)
        Text(desc, color = Color(0xFF9CA3AF), fontSize = 13.sp)
    }
}
