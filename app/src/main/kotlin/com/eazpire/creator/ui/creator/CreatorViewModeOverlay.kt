package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.i18n.TranslationStore

/**
 * View Mode Overlay – wie Web creator-view-mode-overlay
 * grid2, grid3, grid4, list
 */
@Composable
fun CreatorViewModeOverlay(
    currentMode: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    translationStore: TranslationStore
) {
    val modes = listOf(
        0 to (translationStore.t("creator.creations.view_grid2", "2 columns")),
        1 to (translationStore.t("creator.creations.view_grid3", "3 columns")),
        2 to (translationStore.t("creator.creations.view_grid4", "4 columns")),
        3 to (translationStore.t("creator.creations.view_list", "List view"))
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .background(Color(0xFF1E293B), RoundedCornerShape(16.dp))
                .padding(20.dp)
                .clickable(enabled = false) { },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                translationStore.t("creator.creations.view_mode_label", "View options"),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            for (index in modes.indices) {
                val (_, label) = modes[index]
                val selected = currentMode == index
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) EazColors.Orange.copy(alpha = 0.3f) else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            if (index == 3) Icons.Default.List else Icons.Default.GridView,
                            contentDescription = null,
                            tint = if (selected) EazColors.Orange else Color.White.copy(alpha = 0.8f)
                        )
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selected) EazColors.Orange else Color.White
                        )
                    }
                }
            }
        }
    }
}
