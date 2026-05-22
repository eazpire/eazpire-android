package com.eazpire.creator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors

data class CollectionSortOption(val value: String, val label: String)

val COLLECTION_SORT_OPTIONS = listOf(
    CollectionSortOption("manual", "Featured"),
    CollectionSortOption("created-descending", "Newest"),
    CollectionSortOption("created-ascending", "Oldest"),
    CollectionSortOption("price-ascending", "Price: Low to High"),
    CollectionSortOption("price-descending", "Price: High to Low"),
    CollectionSortOption("title-ascending", "A–Z"),
    CollectionSortOption("title-descending", "Z–A"),
)

/** Shared PLP toolbar: filter icon + x/x products + sort chip (matches [CollectionScreen]). */
@Composable
fun CollectionResultsBar(
    filteredCount: Int,
    totalCount: Int,
    sortBy: String,
    sortLabel: String,
    t: (String, String) -> String = { _, d -> d },
    onFilterClick: () -> Unit,
    onSortClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isFeatured = sortBy == "manual"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onFilterClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.FilterList,
                    contentDescription = t("collection.filter", "Filter"),
                    tint = EazColors.TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Text(
                text = "$filteredCount/$totalCount ${t("collection.products", "products")}",
                style = MaterialTheme.typography.bodySmall,
                color = EazColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(EazColors.Orange)
                .clickable(onClick = onSortClick)
                .padding(
                    horizontal = if (isFeatured) 10.dp else 14.dp,
                    vertical = 7.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.SwapVert,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            if (!isFeatured) {
                Text(
                    sortLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White
                )
            }
            Icon(
                Icons.Default.ExpandMore,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionSortBottomSheet(
    visible: Boolean,
    sortBy: String,
    sortOptions: List<CollectionSortOption> = COLLECTION_SORT_OPTIONS,
    t: (String, String) -> String = { _, d -> d },
    onDismiss: () -> Unit,
    onSortSelected: (String) -> Unit
) {
    if (!visible) return
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                t("collection.sort_by", "Sort by"),
                style = MaterialTheme.typography.titleMedium,
                color = EazColors.TextPrimary,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            sortOptions.forEach { opt ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSortSelected(opt.value)
                            onDismiss()
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        t("collection.sort_${opt.value}", opt.label),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (sortBy == opt.value) EazColors.Orange else EazColors.TextPrimary
                    )
                }
            }
        }
    }
}
