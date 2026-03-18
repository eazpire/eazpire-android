package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.i18n.TranslationStore

/**
 * Filter Modal – 1:1 wie Web (creator-mobile-filter-modal.liquid)
 * Design Filter | Product Filter tabs
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorFilterModal(
    onDismiss: () -> Unit,
    source: String,
    translationStore: TranslationStore
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filterTab by remember { mutableStateOf(if (source == "products") "product" else "design") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E293B),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    translationStore.t("creator.design_modal.filter_title", "Filters"),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
                Text(
                    "×",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(8.dp)
                )
            }

            // Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val filterTabs = listOf(
                    "design" to (translationStore.t("creator.filter.design_filter", "Design Filter")),
                    "product" to (translationStore.t("creator.filter.product_filter", "Product Filter"))
                )
                for ((tab, label) in filterTabs) {
                    val active = filterTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) EazColors.Orange.copy(alpha = 0.3f) else Color.Transparent)
                            .border(
                                if (active) 2.dp else 1.dp,
                                if (active) EazColors.Orange else Color.White.copy(alpha = 0.2f),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { filterTab = tab }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (active) EazColors.Orange else Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                when (filterTab) {
                    "design" -> {
                        FilterGroup(translationStore.t("creator.filter.design_source", "Design Source")) {
                            FilterOption(translationStore.t("creator.filter.generated", "Generated"))
                            FilterOption(translationStore.t("creator.filter.uploaded", "Uploaded"))
                            FilterOption(translationStore.t("creator.filter.personalized", "Personalized"))
                        }
                        FilterGroup(translationStore.t("creator.filter.ratio", "Ratio")) {
                            FilterOption(translationStore.t("creator.filter.portrait", "Portrait"))
                            FilterOption(translationStore.t("creator.filter.landscape", "Landscape"))
                            FilterOption(translationStore.t("creator.filter.square", "Square"))
                        }
                        FilterGroup(translationStore.t("creator.filter.design_type", "Design Type")) {
                            FilterOption(translationStore.t("creator.filter.classic", "Classic"))
                            FilterOption(translationStore.t("creator.filter.pattern", "Pattern"))
                            FilterOption(translationStore.t("creator.filter.all_over", "All Over"))
                        }
                    }
                    "product" -> {
                        FilterGroup(translationStore.t("creator.filter.product_category", "Product Category")) {
                            FilterOption("Clothing")
                            FilterOption("Accessories")
                            FilterOption("Home & Living")
                            FilterOption("Other")
                        }
                        FilterGroup(translationStore.t("creator.filter.sales", "Sales")) {
                            FilterOption(translationStore.t("creator.filter.no_sales", "No sales"))
                            FilterOption("1-10")
                            FilterOption("11-50")
                            FilterOption("51-100")
                            FilterOption("100+")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(translationStore.t("creator.filter.reset", "Reset"), color = Color.White)
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = EazColors.Orange),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(translationStore.t("creator.filter.apply", "Apply"), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun FilterGroup(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            content()
        }
    }
}

@Composable
private fun FilterOption(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable { }
            .padding(12.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
    }
}
