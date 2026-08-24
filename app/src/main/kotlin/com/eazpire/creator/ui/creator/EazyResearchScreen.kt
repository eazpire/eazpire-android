package com.eazpire.creator.ui.creator

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private val CardBg = Color(0xE6120C24)
private val TextMain = Color(0xFFF4F1FF)
private val TextDim = Color(0xC7F4F1FF)
private val Accent = Color(0xFF7C5CFF)
private val HeartOn = Color(0xFFF97316)
private val SafeGreen = Color(0xFF2ECC71)
private val RailBg = Color(0x8010122A)

private const val WATCH_PREFS = "eazy-research"
private const val WATCH_KEY = "eazy-research-watched"
private const val FILTERS_COLLAPSE_KEY = "eazy-research-filters-collapsed"

private data class ResearchProduct(
    val asin: String,
    val marketplace: String,
    val marketplaceTag: String,
    val title: String,
    val brand: String,
    val imageUrl: String,
    val nicheKey: String,
    val subNiche: String,
    val designType: String?,
    val language: String?,
    val personalizable: Boolean,
    val reprintOk: Boolean,
    val rating: Double?,
    val reviews: Int?,
    val reviewDelta: Int?,
    val reviewWindow: String?,
    val bsr: Int?,
    val bsrCategory: String?,
    val bsrDelta: Int?,
    val bsrImproved: Boolean?,
    val capturedAt: Long?,
    val trend: String,
    val risingScore: Int,
)

private data class ResearchNiche(
    val key: String,
    val label: String,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EazyResearchScreen(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    maxHeight: Dp = Dp.Unspecified,
    onSendToGenerator: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val cap = if (maxHeight == Dp.Unspecified) 4000.dp else maxHeight
    val api = remember(tokenStore.getJwt()) { CreatorApi(jwt = tokenStore.getJwt()) }
    val context = LocalContext.current
    fun tr(key: String, fallback: String) = translationStore.t(key, fallback)

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var products by remember { mutableStateOf(listOf<ResearchProduct>()) }
    var niches by remember { mutableStateOf(listOf<ResearchNiche>()) }
    var query by remember { mutableStateOf("") }
    var selectedNiches by remember { mutableStateOf(setOf<String>()) }
    var designType by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("") }
    var personalization by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf("review_growth") }
    var view by remember { mutableStateOf("opportunities") }
    var selected by remember { mutableStateOf<ResearchProduct?>(null) }
    var watched by remember { mutableStateOf(loadWatchedAsins(context)) }
    var filtersCollapsed by remember { mutableStateOf(loadFiltersCollapsed(context)) }
    var filtersSheetOpen by remember { mutableStateOf(false) }

    LaunchedEffect(tokenStore.getJwt()) {
        loading = true
        error = null
        try {
            val params = mutableMapOf(
                "reprint_ok" to "1",
                "limit" to "80",
                "sort" to sort,
            )
            if (selectedNiches.isNotEmpty()) params["niche"] = selectedNiches.joinToString(",")
            val data = api.call("eazy-research-products", params)
            if (!data.optBoolean("ok", false)) {
                error = tr("creator.research.error", "Research data could not be loaded.")
            } else {
                preview = data.optBoolean("preview", false)
                products = parseProducts(data.optJSONArray("products"))
                niches = parseNiches(data.optJSONArray("niches"))
                val last = data.optJSONObject("last_run")
                status = if (preview) {
                    tr("creator.research.preview_banner", "Preview data — live snapshots coming") +
                        " · ${products.size}"
                } else if (last != null) {
                    tr("creator.research.last_run", "Last snapshot") + " · " +
                        last.optString("niche_pack", last.optString("source", ""))
                } else {
                    tr(
                        "creator.research.empty",
                        "No Amazon snapshots yet. Official catalog collection runs in the background.",
                    )
                }
            }
        } catch (_: Exception) {
            error = tr("creator.research.error", "Research data could not be loaded.")
        }
        loading = false
    }

    val filtered = remember(products, query, selectedNiches, designType, language, personalization, sort, view, watched) {
        filterProducts(products, query, selectedNiches, designType, language, personalization, sort, view, watched)
    }

    val filterPanel: @Composable () -> Unit = {
        ResearchFilterPanel(
            translationStore = translationStore,
            query = query,
            onQuery = { query = it },
            sort = sort,
            onSort = { sort = it },
            niches = niches,
            selectedNiches = selectedNiches,
            onToggleNiche = { key ->
                selectedNiches = if (key == "all") emptySet() else {
                    if (key in selectedNiches) selectedNiches - key else selectedNiches + key
                }
            },
            designType = designType,
            onDesignType = { designType = if (designType == it) "" else it },
            language = language,
            onLanguage = { language = if (language == it) "" else it },
            personalization = personalization,
            onPersonalization = { personalization = if (personalization == it) "" else it },
            view = view,
            onView = { view = it },
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .heightIn(max = cap),
    ) {
        val compact = maxWidth < 600.dp
        Row(Modifier.fillMaxSize()) {
            if (!compact) {
                FilterCollapseRail(
                    collapsed = filtersCollapsed,
                    translationStore = translationStore,
                    onToggle = {
                        filtersCollapsed = !filtersCollapsed
                        saveFiltersCollapsed(context, filtersCollapsed)
                    },
                )
                if (!filtersCollapsed) {
                    Column(
                        modifier = Modifier
                            .width(272.dp)
                            .fillMaxHeight()
                            .background(RailBg)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    ) {
                        filterPanel()
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (compact) {
                    Text(
                        tr("creator.research.filter_open", "Open filters"),
                        color = TextMain,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Accent.copy(alpha = 0.28f))
                            .clickable { filtersSheetOpen = true }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    )
                }
                when {
                    loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Accent)
                    }
                    error != null -> Text(error ?: "", color = TextDim, fontSize = 14.sp)
                    filtered.isEmpty() -> Text(
                        if (view == "watched") {
                            tr(
                                "creator.research.empty_watched",
                                "No watched products yet. Tap the heart on a product to start tracking it.",
                            )
                        } else {
                            tr(
                                "creator.research.empty_search",
                                "No reprint-safe products match this search. Try a broader niche such as Coffee or Hiking.",
                            )
                        },
                        color = TextDim,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(160.dp),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                    ) {
                        items(filtered, key = { watchId(it) }) { product ->
                            val nicheLabel = niches.firstOrNull { it.key == product.nicheKey }?.label
                                ?: product.nicheKey
                            OpportunityCard(
                                product = product,
                                nicheLabel = nicheLabel,
                                watched = isWatched(watched, product),
                                translationStore = translationStore,
                                onOpen = { selected = product },
                                onToggleWatch = {
                                    val id = watchId(product)
                                    val next = if (isWatched(watched, product)) {
                                        watched - id - product.asin
                                    } else {
                                        watched + id
                                    }
                                    watched = next
                                    saveWatchedAsins(context, next)
                                },
                            )
                        }
                    }
                }
                selected?.let { product ->
                    Dialog(onDismissRequest = { selected = null }) {
                        ProductDetailCard(
                            product = product,
                            niches = niches,
                            translationStore = translationStore,
                            onClose = { selected = null },
                        )
                    }
                }
                Text(status, color = TextDim, fontSize = 11.sp)
            }
        }
        if (compact && filtersSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { filtersSheetOpen = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = Color(0xFF120C22),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    filterPanel()
                }
            }
        }
    }
}

@Composable
private fun FilterCollapseRail(
    collapsed: Boolean,
    translationStore: TranslationStore,
    onToggle: () -> Unit,
) {
    val label = if (collapsed) {
        translationStore.t("creator.research.filter_expand", "Expand filters")
    } else {
        translationStore.t("creator.research.filter_collapse", "Collapse filters")
    }
    Column(
        modifier = Modifier
            .width(56.dp)
            .fillMaxHeight()
            .background(RailBg)
            .clickable(onClick = onToggle)
            .padding(top = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(onClick = onToggle, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = if (collapsed) Icons.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowLeft,
                contentDescription = label,
                tint = TextDim,
            )
        }
        Icon(
            imageVector = Icons.Filled.FilterList,
            contentDescription = null,
            tint = TextDim,
            modifier = Modifier.size(18.dp).padding(top = 8.dp),
        )
        Text(
            translationStore.t("creator.research.filter_toggle", "Filters"),
            color = TextDim,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResearchFilterPanel(
    translationStore: TranslationStore,
    query: String,
    onQuery: (String) -> Unit,
    sort: String,
    onSort: (String) -> Unit,
    niches: List<ResearchNiche>,
    selectedNiches: Set<String>,
    onToggleNiche: (String) -> Unit,
    designType: String,
    onDesignType: (String) -> Unit,
    language: String,
    onLanguage: (String) -> Unit,
    personalization: String,
    onPersonalization: (String) -> Unit,
    view: String,
    onView: (String) -> Unit,
) {
    fun tr(key: String, fallback: String) = translationStore.t(key, fallback)
    val sorts = listOf(
        "review_growth" to tr("creator.research.sort_review_growth", "Review growth"),
        "reviews" to tr("creator.research.sort_reviews", "Reviews"),
        "bsr" to tr("creator.research.sort_bsr", "BSR"),
        "newest" to tr("creator.research.sort_newest", "Newest snapshot"),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1.15f),
            singleLine = true,
            placeholder = {
                Text(tr("creator.research.search_placeholder", "Search reprint-safe products"), color = TextDim)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextMain,
                unfocusedTextColor = TextMain,
                focusedBorderColor = Accent,
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
            ),
        )
        FlowRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            sorts.forEach { (id, label) ->
                FilterChip(
                    label = label,
                    selected = sort == id,
                    onClick = { onSort(id) },
                )
            }
        }
    }
    Text(
        tr("creator.research.topics", "Topics"),
        color = TextDim,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    )
    if (selectedNiches.isEmpty()) {
        Text(
            tr("creator.research.topics_all_hint", "All topics"),
            color = TextDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            label = tr("creator.research.niche_all", "All"),
            selected = selectedNiches.isEmpty(),
            onClick = { onToggleNiche("all") },
        )
        niches.forEach { item ->
            FilterChip(
                label = item.label,
                selected = item.key in selectedNiches,
                onClick = { onToggleNiche(item.key) },
            )
        }
    }
    Text(
        tr("creator.research.design_type", "Design type"),
        color = TextDim,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    )
    if (designType.isEmpty()) {
        Text(
            tr("creator.research.design_type_all_hint", "All design types"),
            color = TextDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            "design_only" to tr("creator.quick_inspirations.content_type_design_only", "Design Only"),
            "text_only" to tr("creator.quick_inspirations.content_type_text_only", "Text Only"),
            "design_text" to tr("creator.quick_inspirations.content_type_design_text", "Design + Text"),
        ).forEach { (id, label) ->
            FilterChip(
                label = label,
                selected = designType == id,
                onClick = { onDesignType(id) },
                radio = true,
            )
        }
    }
    Text(
        tr("creator.research.language", "Language"),
        color = TextDim,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    )
    if (language.isEmpty()) {
        Text(
            tr("creator.research.language_all_hint", "All languages"),
            color = TextDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            "none" to tr("creator.quick_inspirations.language_none", "None"),
            "en" to tr("creator.research.lang_en", "English"),
            "de" to tr("creator.research.lang_de", "German"),
            "es" to tr("creator.research.lang_es", "Spanish"),
            "fr" to tr("creator.research.lang_fr", "French"),
            "it" to tr("creator.research.lang_it", "Italian"),
        ).forEach { (id, label) ->
            FilterChip(
                label = label,
                selected = language == id,
                onClick = { onLanguage(id) },
                radio = true,
            )
        }
    }
    Text(
        tr("creator.research.personalization", "Personalization"),
        color = TextDim,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    )
    if (personalization.isEmpty()) {
        Text(
            tr("creator.research.personalization_all_hint", "Standard and personalizable"),
            color = TextDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 6.dp),
        )
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            "standard" to tr("creator.research.personalization_standard", "Standard"),
            "personalizable" to tr("creator.research.personalization_personalizable", "Personalizable"),
        ).forEach { (id, label) ->
            FilterChip(
                label = label,
                selected = personalization == id,
                onClick = { onPersonalization(id) },
                radio = true,
            )
        }
    }
    Text(
        tr("creator.research.views", "Views"),
        color = TextDim,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            "opportunities" to tr("creator.research.tab_opportunities", "Opportunities"),
            "rising" to tr("creator.research.tab_rising", "Rising"),
            "review_growth" to tr("creator.research.tab_review_growth", "Review growth"),
            "watched" to tr("creator.research.tab_watched", "Watched"),
        ).forEach { (id, label) ->
            FilterChip(
                label = label,
                selected = view == id,
                onClick = { onView(id) },
                radio = true,
            )
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    radio: Boolean = false,
) {
    Text(
        label,
        color = TextMain,
        fontSize = 13.sp,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Accent.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.06f))
            .border(
                width = 1.dp,
                color = if (selected) Color(0xB3A78BFA) else Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(999.dp),
            )
            .semantics {
                this.selected = selected
                role = if (radio) Role.RadioButton else Role.Checkbox
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    )
}

@Composable
private fun OpportunityCard(
    product: ResearchProduct,
    nicheLabel: String,
    watched: Boolean,
    translationStore: TranslationStore,
    onOpen: () -> Unit,
    onToggleWatch: () -> Unit,
) {
    fun tr(key: String, fallback: String) = translationStore.t(key, fallback)
    val change = formatBsrChange(product, translationStore)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .clickable(onClick = onOpen),
    ) {
        Box {
            if (product.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.title,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xFF1B1430)))
            }
            if (product.marketplaceTag.isNotBlank()) {
                Text(
                    product.marketplaceTag,
                    color = TextMain,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xC70A0618))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
            IconButton(
                onClick = onToggleWatch,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xB80A0618)),
            ) {
                Icon(
                    imageVector = if (watched) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (watched) {
                        tr("creator.research.watch_remove", "Remove from watchlist")
                    } else {
                        tr("creator.research.watch_add", "Add to watchlist")
                    },
                    tint = if (watched) HeartOn else TextMain,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(product.title, color = TextMain, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
            if (nicheLabel.isNotBlank()) {
                Text(nicheLabel, color = TextDim, fontSize = 11.sp)
            }
            Text(
                formatBsr(product, translationStore),
                color = TextMain,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            change?.let { (label, improved) ->
                Text(
                    label,
                    color = if (improved) SafeGreen else Color(0xFFFF8D85),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            product.reviews?.let { count ->
                Text(
                    tr("creator.research.reviews_count", "{count} reviews").replace("{count}", count.toString()),
                    color = TextDim,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun ProductDetailCard(
    product: ResearchProduct,
    niches: List<ResearchNiche>,
    translationStore: TranslationStore,
    onClose: () -> Unit,
) {
    fun tr(key: String, fallback: String) = translationStore.t(key, fallback)
    val nicheLabel = niches.firstOrNull { it.key == product.nicheKey }?.label ?: product.nicheKey
    val nicheLine = listOf(nicheLabel, product.subNiche).filter { it.isNotBlank() }.joinToString(" · ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onClose) { Text("×", color = TextMain, fontSize = 22.sp) }
        }
        if (product.imageUrl.isNotBlank()) {
            AsyncImage(
                model = product.imageUrl,
                contentDescription = product.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit,
            )
        }
        Text(product.title, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (product.marketplaceTag.isNotBlank()) {
            Text(
                product.marketplaceTag,
                color = TextMain,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xC70A0618))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
        if (nicheLine.isNotBlank()) {
            Text(nicheLine, color = TextDim, fontSize = 13.sp)
        }
        StatRow(tr("creator.research.bsr_label", "BSR"), formatBsr(product, translationStore))
        formatBsrChange(product, translationStore)?.let { (label, _) ->
            StatRow(tr("creator.research.bsr_change", "BSR change"), label)
        }
        product.reviews?.let { count ->
            StatRow(tr("creator.research.reviews_total", "Reviews total"), count.toString())
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextDim, fontSize = 14.sp)
        Text(value, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatBsr(product: ResearchProduct, translationStore: TranslationStore): String {
    val rank = product.bsr ?: return translationStore.t("creator.research.bsr_missing", "No BSR")
    val rankText = String.format(Locale.GERMANY, "%,d", rank)
    val category = product.bsrCategory?.trim().orEmpty()
    return if (category.isNotEmpty()) {
        translationStore.t("creator.research.bsr_with_category", "BSR {rank} · {category}")
            .replace("{rank}", rankText)
            .replace("{category}", category)
    } else {
        "${translationStore.t("creator.research.bsr_label", "BSR")} $rankText"
    }
}

private fun formatBsrChange(
    product: ResearchProduct,
    translationStore: TranslationStore,
): Pair<String, Boolean>? {
    val delta = product.bsrDelta ?: return null
    if (delta == 0) return null
    val improved = product.bsrImproved == true || delta < 0
    val label = if (improved) {
        translationStore.t("creator.research.bsr_change_improved", "↑ Improved")
    } else {
        translationStore.t("creator.research.bsr_change_worse", "↓ Worse")
    }
    return label to improved
}

private fun filterProducts(
    products: List<ResearchProduct>,
    query: String,
    niches: Set<String>,
    designType: String,
    language: String,
    personalization: String,
    sort: String,
    view: String,
    watched: Set<String>,
): List<ResearchProduct> {
    var rows = products.filter { it.reprintOk }
    if (niches.isNotEmpty()) rows = rows.filter { it.nicheKey in niches }
    if (designType.isNotBlank()) rows = rows.filter { it.designType.equals(designType, ignoreCase = true) }
    if (language.isNotBlank()) rows = rows.filter { it.language.equals(language, ignoreCase = true) }
    if (personalization.isNotBlank()) {
        rows = rows.filter { product ->
            val key = if (product.personalizable) "personalizable" else "standard"
            key.equals(personalization, ignoreCase = true)
        }
    }
    val q = query.trim().lowercase(Locale.ROOT)
    if (q.isNotEmpty()) {
        rows = rows.filter {
            listOf(it.title, it.brand, it.asin, it.nicheKey, it.marketplace, it.marketplaceTag)
                .joinToString(" ").lowercase(Locale.ROOT).contains(q)
        }
    }
    rows = when (view) {
        "rising" -> rows.filter { it.trend == "rising" || it.risingScore > 0 }
        "review_growth" -> rows.filter { (it.reviewDelta ?: 0) > 0 }
        "watched" -> rows.filter { isWatched(watched, it) }
        else -> rows
    }
    return when (sort) {
        "reviews" -> rows.sortedByDescending { it.reviews ?: 0 }
        "bsr" -> rows.sortedBy { it.bsr ?: Int.MAX_VALUE }
        "newest" -> rows.sortedByDescending { it.capturedAt ?: 0L }
        else -> rows.sortedByDescending { it.reviewDelta ?: 0 }
    }
}

private fun parseProducts(arr: JSONArray?): List<ResearchProduct> {
    if (arr == null) return emptyList()
    val out = ArrayList<ResearchProduct>(arr.length())
    for (i in 0 until arr.length()) {
        val p = arr.optJSONObject(i) ?: continue
        val latest = p.optJSONObject("latest") ?: JSONObject()
        out += ResearchProduct(
            asin = p.optString("asin"),
            marketplace = p.optString("marketplace"),
            marketplaceTag = p.optString("marketplace_tag").ifBlank {
                marketplaceTagFromHost(p.optString("marketplace"))
            },
            title = p.optString("title").ifBlank { p.optString("asin") },
            brand = p.optString("brand"),
            imageUrl = p.optString("image_url"),
            nicheKey = p.optString("niche_key"),
            subNiche = p.optString("sub_niche").ifBlank { p.optString("sub_niche_key") },
            designType = p.optString("design_type").ifBlank { null },
            language = p.optString("language").ifBlank { null },
            personalizable = p.optInt("personalizable", 0) == 1 ||
                p.optBoolean("personalizable", false) ||
                p.optString("personalization").equals("personalizable", ignoreCase = true),
            reprintOk = p.optBoolean("reprint_ok", true),
            rating = latest.optDoubleOrNull("rating"),
            reviews = latest.optIntOrNull("reviews_count"),
            reviewDelta = p.optIntOrNull("review_delta"),
            reviewWindow = p.optString("review_delta_window").ifBlank { null },
            bsr = latest.optIntOrNull("bsr"),
            bsrCategory = latest.optString("bsr_category").ifBlank { p.optString("bsr_category") }.ifBlank { null },
            bsrDelta = p.optIntOrNull("bsr_delta"),
            bsrImproved = p.optBooleanOrNull("bsr_improved"),
            capturedAt = latest.optLongOrNull("captured_at"),
            trend = p.optString("trend"),
            risingScore = p.optInt("rising_score", 0),
        )
    }
    return out
}

private fun parseNiches(arr: JSONArray?): List<ResearchNiche> {
    if (arr == null) return emptyList()
    val out = ArrayList<ResearchNiche>(arr.length())
    for (i in 0 until arr.length()) {
        val n = arr.optJSONObject(i) ?: continue
        val key = n.optString("niche_key").ifBlank { n.optString("key") }
        if (key.isBlank()) continue
        out += ResearchNiche(key, n.optString("label").ifBlank { key })
    }
    return out
}

private fun watchId(product: ResearchProduct): String =
    if (product.marketplace.isBlank()) product.asin else "${product.asin}:${product.marketplace}"

private fun isWatched(watched: Set<String>, product: ResearchProduct): Boolean =
    watched.contains(watchId(product)) || watched.contains(product.asin)

private fun marketplaceTagFromHost(host: String): String = when (host.trim().lowercase(Locale.ROOT)) {
    "amazon.de" -> "DE"
    "amazon.co.uk" -> "UK"
    "amazon.fr" -> "FR"
    "amazon.it" -> "IT"
    "amazon.es" -> "ES"
    "amazon.com" -> "US"
    "amazon.ca" -> "CA"
    "amazon.co.jp" -> "JP"
    "amazon.com.au" -> "AU"
    else -> ""
}

private fun loadWatchedAsins(context: Context): Set<String> {
    val raw = context.getSharedPreferences(WATCH_PREFS, Context.MODE_PRIVATE)
        .getString(WATCH_KEY, "[]") ?: "[]"
    return try {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }.toSet()
    } catch (_: Exception) {
        emptySet()
    }
}

private fun saveWatchedAsins(context: Context, asins: Set<String>) {
    val arr = JSONArray()
    asins.forEach { arr.put(it) }
    context.getSharedPreferences(WATCH_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(WATCH_KEY, arr.toString())
        .apply()
}

private fun loadFiltersCollapsed(context: Context): Boolean =
    context.getSharedPreferences(WATCH_PREFS, Context.MODE_PRIVATE)
        .getBoolean(FILTERS_COLLAPSE_KEY, false)

private fun saveFiltersCollapsed(context: Context, collapsed: Boolean) {
    context.getSharedPreferences(WATCH_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(FILTERS_COLLAPSE_KEY, collapsed)
        .apply()
}

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (!has(key) || isNull(key)) null else optInt(key)

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (!has(key) || isNull(key)) null else optLong(key)

private fun JSONObject.optBooleanOrNull(key: String): Boolean? =
    if (!has(key) || isNull(key)) null else optBoolean(key)
