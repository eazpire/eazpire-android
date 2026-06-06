package com.eazpire.creator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.ShopifyProductsApi
import java.text.Normalizer
import java.util.Locale

/** PLP tri-filter selection (− · +), aligned with theme/assets/eaz-plp-tri-filters-core.js */
internal data class PlpTriFilterSelection(
    val priceMin: String = "",
    val priceMax: String = "",
    val productTypes: Map<String, FacetTriState> = emptyMap(),
    val productNames: Map<String, FacetTriState> = emptyMap(),
    val contentTypes: Map<String, FacetTriState> = emptyMap(),
    val designTypes: Map<String, FacetTriState> = emptyMap(),
    val designRatios: Map<String, FacetTriState> = emptyMap(),
    val designLanguages: Map<String, FacetTriState> = emptyMap(),
) {
    fun isEmpty(): Boolean =
        priceMin.isBlank() && priceMax.isBlank() &&
            productTypes.values.none { it != 0 } &&
            productNames.values.none { it != 0 } &&
            contentTypes.values.none { it != 0 } &&
            designTypes.values.none { it != 0 } &&
            designRatios.values.none { it != 0 } &&
            designLanguages.values.none { it != 0 }
}

/** @deprecated Use [PlpTriFilterSelection] — kept as alias for gradual migration */
internal typealias ProductFilters = PlpTriFilterSelection

internal data class PlpFacetOption(
    val value: String,
    val label: String,
    val count: Int,
)

private enum class PlpFacetKey {
    PRODUCT_TYPE,
    PRODUCT_NAME,
    CONTENT_TYPE,
    DESIGN_TYPE,
    DESIGN_RATIO,
    DESIGN_LANGUAGE,
}

internal fun normalizePlpValue(v: String?): String =
    (v ?: "").trim().lowercase(Locale.ROOT)

private fun labelizePlpValue(value: String): String {
    val s = value.trim()
    if (s.isEmpty()) return ""
    return s.replace(Regex("[-_]+"), " ")
        .split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.ROOT) else ch.toString()
            }
        }
}

private fun getPlpProductField(p: ShopifyProductsApi.ProductItem, key: PlpFacetKey): String = when (key) {
    PlpFacetKey.PRODUCT_TYPE -> normalizePlpValue(p.productType)
    PlpFacetKey.PRODUCT_NAME -> p.patProductName.trim()
    PlpFacetKey.CONTENT_TYPE -> p.contentType.trim()
    PlpFacetKey.DESIGN_TYPE -> p.designType.trim()
    PlpFacetKey.DESIGN_RATIO -> p.ratio.trim()
    PlpFacetKey.DESIGN_LANGUAGE -> p.designLanguage.trim()
}

private fun plpFieldLabel(p: ShopifyProductsApi.ProductItem, key: PlpFacetKey, value: String): String = when (key) {
    PlpFacetKey.PRODUCT_TYPE -> p.productType.trim().ifBlank { labelizePlpValue(value) }
    else -> labelizePlpValue(value).ifBlank { value }
}

private fun plpProductMatchesValue(
    p: ShopifyProductsApi.ProductItem,
    key: PlpFacetKey,
    wanted: String,
): Boolean {
    val valField = getPlpProductField(p, key)
    val w = if (key == PlpFacetKey.PRODUCT_TYPE) normalizePlpValue(wanted) else wanted.trim()
    return w.isNotEmpty() && valField.isNotEmpty() && valField == w
}

private fun triIncludes(states: Map<String, FacetTriState>): List<String> =
    states.filterValues { it == 1 }.keys.toList()

private fun triExcludes(states: Map<String, FacetTriState>): List<String> =
    states.filterValues { it == -1 }.keys.toList()

private fun matchesPlpFacetGroup(
    p: ShopifyProductsApi.ProductItem,
    key: PlpFacetKey,
    states: Map<String, FacetTriState>,
): Boolean {
    val includes = triIncludes(states)
    val excludes = triExcludes(states)
    for (ex in excludes) {
        if (plpProductMatchesValue(p, key, ex)) return false
    }
    if (includes.isNotEmpty() && !includes.any { plpProductMatchesValue(p, key, it) }) return false
    return true
}

private fun readPlpPriceBounds(sel: PlpTriFilterSelection): Pair<Double?, Double?> {
    val min = sel.priceMin.toDoubleOrNull()
    val max = sel.priceMax.toDoubleOrNull()
    return min to max
}

private fun matchesPlpPrice(p: ShopifyProductsApi.ProductItem, sel: PlpTriFilterSelection): Boolean {
    val (min, max) = readPlpPriceBounds(sel)
    if (min != null && p.price < min) return false
    if (max != null && p.price > max) return false
    return true
}

private fun matchesPlpFacets(
    p: ShopifyProductsApi.ProductItem,
    sel: PlpTriFilterSelection,
    excludeKey: PlpFacetKey? = null,
): Boolean {
    if (excludeKey != PlpFacetKey.PRODUCT_TYPE && !matchesPlpFacetGroup(p, PlpFacetKey.PRODUCT_TYPE, sel.productTypes)) return false
    if (excludeKey != PlpFacetKey.PRODUCT_NAME && !matchesPlpFacetGroup(p, PlpFacetKey.PRODUCT_NAME, sel.productNames)) return false
    if (excludeKey != PlpFacetKey.CONTENT_TYPE && !matchesPlpFacetGroup(p, PlpFacetKey.CONTENT_TYPE, sel.contentTypes)) return false
    if (excludeKey != PlpFacetKey.DESIGN_TYPE && !matchesPlpFacetGroup(p, PlpFacetKey.DESIGN_TYPE, sel.designTypes)) return false
    if (excludeKey != PlpFacetKey.DESIGN_RATIO && !matchesPlpFacetGroup(p, PlpFacetKey.DESIGN_RATIO, sel.designRatios)) return false
    if (excludeKey != PlpFacetKey.DESIGN_LANGUAGE && !matchesPlpFacetGroup(p, PlpFacetKey.DESIGN_LANGUAGE, sel.designLanguages)) return false
    return true
}

internal fun applyCollectionProductFilters(
    products: List<ShopifyProductsApi.ProductItem>,
    filters: PlpTriFilterSelection,
): List<ShopifyProductsApi.ProductItem> {
    if (filters.isEmpty()) return products
    return products.filter { p ->
        matchesPlpPrice(p, filters) && matchesPlpFacets(p, filters, null)
    }
}

private fun poolForPlpFacetCounts(
    products: List<ShopifyProductsApi.ProductItem>,
    filters: PlpTriFilterSelection,
    withinSearchQuery: String,
    creatorName: String,
    excludeKey: PlpFacetKey,
): List<ShopifyProductsApi.ProductItem> =
    products.filter { p ->
        productMatchesPlpWithinSearch(p, withinSearchQuery, creatorName) &&
            matchesPlpPrice(p, filters) &&
            matchesPlpFacets(p, filters, excludeKey)
    }

private fun computePlpFacetOptions(
    products: List<ShopifyProductsApi.ProductItem>,
    filters: PlpTriFilterSelection,
    withinSearchQuery: String,
    creatorName: String,
    key: PlpFacetKey,
): List<PlpFacetOption> {
    val pool = poolForPlpFacetCounts(products, filters, withinSearchQuery, creatorName, key)
    val map = linkedMapOf<String, PlpFacetOption>()
    pool.forEach { p ->
        val value = getPlpProductField(p, key)
        if (value.isEmpty()) return@forEach
        val existing = map[value]
        if (existing == null) {
            map[value] = PlpFacetOption(
                value = value,
                label = plpFieldLabel(p, key, value),
                count = 1,
            )
        } else {
            map[value] = existing.copy(count = existing.count + 1)
        }
    }
    return map.values.sortedBy { it.label.lowercase(Locale.ROOT) }
}

internal fun normalizeWithinSearch(s: String): String {
    val n = try {
        Normalizer.normalize(s, Normalizer.Form.NFD)
    } catch (_: Exception) {
        s
    }
    return n.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .lowercase(Locale.ROOT)
        .trim()
}

internal fun buildPlpSearchBlob(p: ShopifyProductsApi.ProductItem, creatorName: String = ""): String = buildString {
    append(p.title).append(' ')
    append(p.handle).append(' ')
    append(p.productType).append(' ')
    append(p.patProductName).append(' ')
    append(p.contentType).append(' ')
    append(p.designType).append(' ')
    append(p.ratio).append(' ')
    append(p.designLanguage).append(' ')
    if (creatorName.isNotBlank()) append(creatorName).append(' ')
}.toString()

internal fun productMatchesPlpWithinSearch(
    p: ShopifyProductsApi.ProductItem,
    queryRaw: String,
    creatorName: String = "",
): Boolean {
    val q = queryRaw.trim()
    if (q.isEmpty()) return true
    return normalizeWithinSearch(buildPlpSearchBlob(p, creatorName)).contains(normalizeWithinSearch(q))
}

internal fun applyCollectionWithinSearchFilter(
    products: List<ShopifyProductsApi.ProductItem>,
    query: String,
    creatorName: String = "",
): List<ShopifyProductsApi.ProductItem> {
    if (query.isBlank()) return products
    return products.filter { productMatchesPlpWithinSearch(it, query, creatorName) }
}

@Composable
private fun PlpTriFacetGroupSection(
    title: String,
    options: List<PlpFacetOption>,
    states: Map<String, FacetTriState>,
    onStateChange: (String, FacetTriState) -> Unit,
) {
    val visible = options.filter { it.count > 0 }
    if (visible.isEmpty()) return
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = EazColors.TextPrimary,
        modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
    )
    Column(modifier = Modifier.padding(bottom = 14.dp)) {
        visible.forEach { opt ->
            FacetTriSwitchRow(
                label = opt.label,
                count = opt.count,
                state = states[opt.value] ?: 0,
                onStateChange = { onStateChange(opt.value, it) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectionFilterDrawer(
    filters: PlpTriFilterSelection,
    products: List<ShopifyProductsApi.ProductItem>,
    withinSearchQuery: String,
    onWithinSearchChange: (String) -> Unit,
    onFiltersChange: (PlpTriFilterSelection) -> Unit,
    onDismiss: () -> Unit,
    creatorName: String = "",
    t: (String, String) -> String = { _, d -> d },
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val productTypeOptions = remember(products, filters, withinSearchQuery, creatorName) {
        computePlpFacetOptions(products, filters, withinSearchQuery, creatorName, PlpFacetKey.PRODUCT_TYPE)
    }
    val productNameOptions = remember(products, filters, withinSearchQuery, creatorName) {
        computePlpFacetOptions(products, filters, withinSearchQuery, creatorName, PlpFacetKey.PRODUCT_NAME)
    }
    val contentTypeOptions = remember(products, filters, withinSearchQuery, creatorName) {
        computePlpFacetOptions(products, filters, withinSearchQuery, creatorName, PlpFacetKey.CONTENT_TYPE)
    }
    val designTypeOptions = remember(products, filters, withinSearchQuery, creatorName) {
        computePlpFacetOptions(products, filters, withinSearchQuery, creatorName, PlpFacetKey.DESIGN_TYPE)
    }
    val designRatioOptions = remember(products, filters, withinSearchQuery, creatorName) {
        computePlpFacetOptions(products, filters, withinSearchQuery, creatorName, PlpFacetKey.DESIGN_RATIO)
    }
    val designLanguageOptions = remember(products, filters, withinSearchQuery, creatorName) {
        computePlpFacetOptions(products, filters, withinSearchQuery, creatorName, PlpFacetKey.DESIGN_LANGUAGE)
    }
    val filteredCount = remember(products, filters, withinSearchQuery, creatorName) {
        applyCollectionWithinSearchFilter(
            applyCollectionProductFilters(products, filters),
            withinSearchQuery,
            creatorName,
        ).size
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .heightIn(min = 450.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Default.FilterList,
                        contentDescription = null,
                        tint = EazColors.Orange,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        t("collection.filter", "Filters"),
                        style = MaterialTheme.typography.titleMedium,
                        color = EazColors.TextPrimary,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = t("common.close", "Close"),
                        tint = EazColors.TextSecondary,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp),
            ) {
                TextField(
                    value = withinSearchQuery,
                    onValueChange = onWithinSearchChange,
                    placeholder = {
                        Text(
                            t("content.within_collection_search_placeholder", "Search titles, creator, type…"),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .heightIn(min = 44.dp),
                    singleLine = true,
                )

                Text(
                    "${t("eaz.collection.price_min", "Min")} / ${t("eaz.collection.price_max", "Max")}",
                    style = MaterialTheme.typography.labelLarge,
                    color = EazColors.TextPrimary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Row(
                    modifier = Modifier.padding(bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextField(
                        value = filters.priceMin,
                        onValueChange = { onFiltersChange(filters.copy(priceMin = it)) },
                        placeholder = {
                            Text(t("eaz.collection.price_min", "Min"), style = MaterialTheme.typography.bodySmall)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    Text("–", color = EazColors.TextSecondary)
                    TextField(
                        value = filters.priceMax,
                        onValueChange = { onFiltersChange(filters.copy(priceMax = it)) },
                        placeholder = {
                            Text(t("eaz.collection.price_max", "Max"), style = MaterialTheme.typography.bodySmall)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                }

                PlpTriFacetGroupSection(
                    title = t("eaz.creator_profile.facet_category", "Category"),
                    options = productTypeOptions,
                    states = filters.productTypes,
                    onStateChange = { v, st ->
                        onFiltersChange(filters.copy(productTypes = filters.productTypes + (v to st)))
                    },
                )
                PlpTriFacetGroupSection(
                    title = t("eaz.creator_profile.facet_product", "Product"),
                    options = productNameOptions,
                    states = filters.productNames,
                    onStateChange = { v, st ->
                        onFiltersChange(filters.copy(productNames = filters.productNames + (v to st)))
                    },
                )
                PlpTriFacetGroupSection(
                    title = t("eaz.creator_profile.facet_content_type", "Content type"),
                    options = contentTypeOptions,
                    states = filters.contentTypes,
                    onStateChange = { v, st ->
                        onFiltersChange(filters.copy(contentTypes = filters.contentTypes + (v to st)))
                    },
                )
                PlpTriFacetGroupSection(
                    title = t("eaz.creator_profile.facet_design_type", "Design type"),
                    options = designTypeOptions,
                    states = filters.designTypes,
                    onStateChange = { v, st ->
                        onFiltersChange(filters.copy(designTypes = filters.designTypes + (v to st)))
                    },
                )
                PlpTriFacetGroupSection(
                    title = t("eaz.creator_profile.facet_design_ratio", "Design ratio"),
                    options = designRatioOptions,
                    states = filters.designRatios,
                    onStateChange = { v, st ->
                        onFiltersChange(filters.copy(designRatios = filters.designRatios + (v to st)))
                    },
                )
                PlpTriFacetGroupSection(
                    title = t("eaz.creator_profile.facet_design_language", "Design language"),
                    options = designLanguageOptions,
                    states = filters.designLanguages,
                    onStateChange = { v, st ->
                        onFiltersChange(filters.copy(designLanguages = filters.designLanguages + (v to st)))
                    },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F5F5))
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .clickable {
                            onFiltersChange(PlpTriFilterSelection())
                            onWithinSearchChange("")
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        t("eaz.creator_profile.reset_filters", "Reset filters"),
                        style = MaterialTheme.typography.labelLarge,
                        color = EazColors.Orange,
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(EazColors.Orange)
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        t("collection.apply_count", "Apply (%d)").format(filteredCount),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
