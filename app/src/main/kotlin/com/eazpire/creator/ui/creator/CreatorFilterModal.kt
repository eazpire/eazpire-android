package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.i18n.TranslationStore

/** Filter state wie Web creator-mobile-filter-modal.js */
data class CreationsFilterState(
    val designArt: Set<String> = emptySet(),
    val ratio: Set<String> = emptySet(),
    val designType: Set<String> = emptySet(),
    val contentType: Set<String> = emptySet(),
    val productCategory: Set<String> = emptySet(),
    val sales: Set<String> = emptySet(),
    val priceMin: String = "",
    val priceMax: String = ""
) {
    fun isEmpty() = designArt.isEmpty() && ratio.isEmpty() && designType.isEmpty() &&
        contentType.isEmpty() && productCategory.isEmpty() && sales.isEmpty() &&
        priceMin.isBlank() && priceMax.isBlank()
}

/**
 * Filter Modal – wie Web (creator-mobile-filter-modal.liquid)
 * Design Filter | Product Filter tabs, alle Optionen funktional
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorFilterModal(
    onDismiss: () -> Unit,
    source: String,
    translationStore: TranslationStore,
    initialFilter: CreationsFilterState = CreationsFilterState(),
    onApply: (CreationsFilterState) -> Unit = {},
    designs: List<CreationDesign> = emptyList(),
    products: List<CreationProduct> = emptyList()
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var filterTab by remember { mutableStateOf(if (source == "products") "product" else "design") }

    var designArt by remember { mutableStateOf(initialFilter.designArt) }
    var ratio by remember { mutableStateOf(initialFilter.ratio) }
    var designType by remember { mutableStateOf(initialFilter.designType) }
    var contentType by remember { mutableStateOf(initialFilter.contentType) }
    var productCategory by remember { mutableStateOf(initialFilter.productCategory) }
    var sales by remember { mutableStateOf(initialFilter.sales) }
    var priceMin by remember { mutableStateOf(initialFilter.priceMin) }
    var priceMax by remember { mutableStateOf(initialFilter.priceMax) }

    fun toggleDesignArt(v: String) { designArt = if (v in designArt) designArt - v else designArt + v }
    fun toggleRatio(v: String) { ratio = if (v in ratio) ratio - v else ratio + v }
    fun toggleDesignType(v: String) { designType = if (v in designType) designType - v else designType + v }
    fun toggleContentType(v: String) { contentType = if (v in contentType) contentType - v else contentType + v }
    fun toggleProductCategory(v: String) { productCategory = if (v in productCategory) productCategory - v else productCategory + v }
    fun toggleSales(v: String) { sales = if (v in sales) sales - v else sales + v }

    fun reset() {
        designArt = emptySet()
        ratio = emptySet()
        designType = emptySet()
        contentType = emptySet()
        productCategory = emptySet()
        sales = emptySet()
        priceMin = ""
        priceMax = ""
    }

    fun apply() {
        onApply(CreationsFilterState(
            designArt = designArt,
            ratio = ratio,
            designType = designType,
            contentType = contentType,
            productCategory = productCategory,
            sales = sales,
            priceMin = priceMin,
            priceMax = priceMax
        ))
        onDismiss()
    }

    val config = LocalConfiguration.current
    val maxHeight = (config.screenHeightDp * 0.88f).dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
        ) {
            // Header – wie Web creator-filter-modal__header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF02060F).copy(alpha = 0.85f))
                    .padding(16.dp, 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = null,
                        tint = EazColors.Orange,
                        modifier = Modifier.height(20.dp)
                    )
                    Text(
                        translationStore.t("creator.design_modal.filter_title", "Filters"),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFE5E7EB)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .clickable { onDismiss() }
                        .padding(6.dp)
                ) {
                    Text("×", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF9CA3AF))
                }
            }

            // Tabs – wie Web (underline für active)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF080F1C).copy(alpha = 0.75f))
                    .padding(horizontal = 16.dp)
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
                            .height(48.dp)
                            .clickable { filterTab = tab },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) EazColors.Orange else Color.White.copy(alpha = 0.6f)
                            )
                            if (active) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                        .padding(top = 4.dp)
                                        .background(EazColors.Orange, RoundedCornerShape(1.dp))
                                )
                            }
                        }
                    }
                }
            }

            // Body – scrollbar wie Web
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                when (filterTab) {
                    "design" -> {
                        val designCounts = remember(designs) {
                            mapOf(
                                "generated" to designs.count { it.designSource.equals("Generated", true) },
                                "uploaded" to designs.count { it.designSource.equals("Uploaded", true) },
                                "personalized" to designs.count { it.designSource.equals("Saved", true) || it.designSource.equals("Personalized", true) },
                                "portrait" to designs.count { it.ratio == "portrait" },
                                "landscape" to designs.count { it.ratio == "landscape" },
                                "square" to designs.count { it.ratio == "square" },
                                "design_text" to designs.count { it.contentType == "design_text" },
                                "design_only" to designs.count { it.contentType == "design_only" },
                                "text_only" to designs.count { it.contentType == "text_only" },
                                "classic" to designs.count { it.designType == "classic" },
                                "pattern" to designs.count { it.designType == "pattern" },
                                "all_over" to designs.count { it.designType == "all_over" }
                            )
                        }
                        FilterGroup(translationStore.t("creator.filter.design_source", "Design Source")) {
                            FilterOptionWithCount(translationStore.t("creator.filter.generated", "Generated"), "generated", designArt, designCounts["generated"] ?: 0) { toggleDesignArt("generated") }
                            FilterOptionWithCount(translationStore.t("creator.filter.uploaded", "Uploaded"), "uploaded", designArt, designCounts["uploaded"] ?: 0) { toggleDesignArt("uploaded") }
                            FilterOptionWithCount(translationStore.t("creator.filter.personalized", "Personalized"), "personalized", designArt, designCounts["personalized"] ?: 0) { toggleDesignArt("personalized") }
                        }
                        FilterGroup(translationStore.t("creator.filter.ratio", "Ratio")) {
                            FilterOptionWithCount(translationStore.t("creator.filter.portrait", "Portrait"), "portrait", ratio, designCounts["portrait"] ?: 0) { toggleRatio("portrait") }
                            FilterOptionWithCount(translationStore.t("creator.filter.landscape", "Landscape"), "landscape", ratio, designCounts["landscape"] ?: 0) { toggleRatio("landscape") }
                            FilterOptionWithCount(translationStore.t("creator.filter.square", "Square"), "square", ratio, designCounts["square"] ?: 0) { toggleRatio("square") }
                        }
                        FilterGroup(translationStore.t("creator.filter.content_type", "Content Type")) {
                            FilterOptionWithCount("Design + Text", "design_text", contentType, designCounts["design_text"] ?: 0) { toggleContentType("design_text") }
                            FilterOptionWithCount("Design Only", "design_only", contentType, designCounts["design_only"] ?: 0) { toggleContentType("design_only") }
                            FilterOptionWithCount("Text Only", "text_only", contentType, designCounts["text_only"] ?: 0) { toggleContentType("text_only") }
                        }
                        FilterGroup(translationStore.t("creator.filter.design_type", "Design Type")) {
                            FilterOptionWithCount(translationStore.t("creator.filter.classic", "Classic"), "classic", designType, designCounts["classic"] ?: 0) { toggleDesignType("classic") }
                            FilterOptionWithCount(translationStore.t("creator.filter.pattern", "Pattern"), "pattern", designType, designCounts["pattern"] ?: 0) { toggleDesignType("pattern") }
                            FilterOptionWithCount(translationStore.t("creator.filter.all_over", "All Over"), "all_over", designType, designCounts["all_over"] ?: 0) { toggleDesignType("all_over") }
                        }
                    }
                    "product" -> {
                        val productCounts = remember(products) {
                            mapOf(
                                "0" to products.count { it.publishedCount == 0 },
                                "1-10" to products.count { it.publishedCount in 1..10 },
                                "11-50" to products.count { it.publishedCount in 11..50 },
                                "51-100" to products.count { it.publishedCount in 51..100 },
                                "100+" to products.count { it.publishedCount >= 100 }
                            )
                        }
                        FilterGroup(translationStore.t("creator.filter.price", "Price")) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = priceMin,
                                    onValueChange = { priceMin = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("Min", color = Color(0xFF9CA3AF)) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.White.copy(alpha = 0.3f),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                        focusedTextColor = Color(0xFFE5E7EB),
                                        unfocusedTextColor = Color(0xFFE5E7EB)
                                    )
                                )
                                Text("–", color = Color(0xFF9CA3AF))
                                OutlinedTextField(
                                    value = priceMax,
                                    onValueChange = { priceMax = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("Max", color = Color(0xFF9CA3AF)) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.White.copy(alpha = 0.3f),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                                        focusedTextColor = Color(0xFFE5E7EB),
                                        unfocusedTextColor = Color(0xFFE5E7EB)
                                    )
                                )
                            }
                        }
                        FilterGroup(translationStore.t("creator.filter.product_category", "Product Category")) {
                            FilterOptionWithCount("Clothing", "clothing", productCategory, 0) { toggleProductCategory("clothing") }
                            FilterOptionWithCount("Accessories", "accessories", productCategory, 0) { toggleProductCategory("accessories") }
                            FilterOptionWithCount("Home & Living", "home", productCategory, 0) { toggleProductCategory("home") }
                            FilterOptionWithCount("Other", "other", productCategory, 0) { toggleProductCategory("other") }
                        }
                        FilterGroup(translationStore.t("creator.filter.sales", "Sales")) {
                            FilterOptionWithCount(translationStore.t("creator.filter.no_sales", "No sales"), "0", sales, productCounts["0"] ?: 0) { toggleSales("0") }
                            FilterOptionWithCount("1-10", "1-10", sales, productCounts["1-10"] ?: 0) { toggleSales("1-10") }
                            FilterOptionWithCount("11-50", "11-50", sales, productCounts["11-50"] ?: 0) { toggleSales("11-50") }
                            FilterOptionWithCount("51-100", "51-100", sales, productCounts["51-100"] ?: 0) { toggleSales("51-100") }
                            FilterOptionWithCount("100+", "100+", sales, productCounts["100+"] ?: 0) { toggleSales("100+") }
                        }
                    }
                }
            }

            // Footer – wie Web creator-filter-modal__footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF02060F).copy(alpha = 0.9f))
                    .padding(16.dp, 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { reset() },
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(translationStore.t("creator.filter.reset", "Reset"), color = Color.White)
                }
                Button(
                    onClick = { apply() },
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
private fun FilterOptionWithCount(
    label: String,
    value: String,
    selected: Set<String>,
    count: Int,
    onToggle: () -> Unit
) {
    val isSelected = value in selected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isSelected) EazColors.Orange.copy(alpha = 0.25f)
                else Color.White.copy(alpha = 0.08f)
            )
            .border(
                if (isSelected) 1.dp else 0.dp,
                if (isSelected) EazColors.Orange else Color.Transparent,
                RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onToggle)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = EazColors.Orange,
                uncheckedColor = Color.White.copy(alpha = 0.6f)
            )
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        Text(
            "($count)",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}
