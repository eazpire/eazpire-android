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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.components.GlassCircularFlag
import kotlinx.coroutines.delay
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
    val topic: String,
    val subNiche: String,
    val tags: List<String>,
    val prompt: String,
    val designType: String?,
    val language: String?,
    val personalizable: Boolean,
    val audience: String,
    val reprintOk: Boolean,
    val rating: Double?,
    val reviews: Int?,
    val reviewDelta: Int?,
    val reviewWindow: String?,
    val bsr: Int?,
    val bsrCategory: String?,
    val bsrDelta: Int?,
    val bsrImproved: Boolean?,
    val relevanceScore: Int?,
    val capturedAt: Long?,
    val trend: String,
    val risingScore: Int,
)

private data class ResearchNiche(
    val key: String,
    val label: String,
)

private data class ResearchFacet(
    val key: String,
    val count: Int,
)

private data class ResearchMarketplace(
    val host: String,
    val tag: String,
)

private data class AnalyzeLimits(
    val used: Int = 0,
    val remaining: Int = 5,
    val limit: Int = 5,
    val busy: Boolean = false,
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
    var analyzing by remember { mutableStateOf(false) }
    var searchId by remember { mutableStateOf("") }
    var searchEmptyReason by remember { mutableStateOf("") }
    var searchAmazonReturned by remember { mutableStateOf(0) }
    var pendingAnalyze by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var products by remember { mutableStateOf(listOf<ResearchProduct>()) }
    var niches by remember { mutableStateOf(listOf<ResearchNiche>()) }
    var facets by remember { mutableStateOf(mapOf<String, List<ResearchFacet>>()) }
    var marketplaces by remember { mutableStateOf(listOf<ResearchMarketplace>()) }
    var marketplace by remember { mutableStateOf("all") }
    var analyzeLimits by remember { mutableStateOf(AnalyzeLimits()) }
    var query by remember { mutableStateOf("") }
    var selectedNiches by remember { mutableStateOf(setOf<String>()) }
    var designTypes by remember { mutableStateOf(setOf<String>()) }
    var languages by remember { mutableStateOf(setOf<String>()) }
    var personalizations by remember { mutableStateOf(setOf<String>()) }
    var selectedAudiences by remember { mutableStateOf(setOf<String>()) }
    var sort by remember { mutableStateOf("review_growth") }
    var view by remember { mutableStateOf("opportunities") }
    var selected by remember { mutableStateOf<ResearchProduct?>(null) }
    var watched by remember { mutableStateOf(loadWatchedAsins(context)) }
    var filtersCollapsed by remember { mutableStateOf(loadFiltersCollapsed(context)) }
    var filtersSheetOpen by remember { mutableStateOf(false) }
    val filterScroll = rememberScrollState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(tokenStore.getJwt(), marketplace) {
        loading = true
        error = null
        try {
            val params = mutableMapOf(
                "reprint_ok" to "1",
                "limit" to "80",
                "sort" to sort,
            )
            if (marketplace.isNotBlank() && marketplace != "all") params["marketplace"] = marketplace
            val data = api.call("eazy-research-products", params)
            if (!data.optBoolean("ok", false)) {
                error = tr("creator.research.error", "Research data could not be loaded.")
            } else {
                preview = data.optBoolean("preview", false)
                if (searchId.isBlank()) products = parseProducts(data.optJSONArray("products"))
                niches = parseNiches(data.optJSONArray("niches"))
                facets = parseFacets(data.optJSONObject("facets"))
                marketplaces = parseMarketplaces(data.optJSONArray("marketplaces"))
                analyzeLimits = parseAnalyzeLimits(data.optJSONObject("analyze_limits"))
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

    LaunchedEffect(pendingAnalyze) {
        if (!pendingAnalyze) return@LaunchedEffect
        val q = query.trim()
        if (q.isEmpty()) {
            analyzing = false
            pendingAnalyze = false
            status = tr("creator.research.analyze_empty_query", "Type a search before Analyze.")
            return@LaunchedEffect
        }
        if (!tokenStore.isLoggedIn()) {
            analyzing = false
            pendingAnalyze = false
            status = tr("creator.research.analyze_login", "Log in to run a live catalog search.")
            return@LaunchedEffect
        }
        try {
            val payload = JSONObject().put("q", q)
            if (marketplace.isNotBlank() && marketplace != "all") payload.put("marketplace", marketplace)
            val data = api.postDispatchJson(
                "eazy-research-analyze-search",
                payload,
            )
            if (!data.optBoolean("ok", false)) {
                analyzing = false
                pendingAnalyze = false
                analyzeLimits = parseAnalyzeLimits(data.optJSONObject("daily") ?: data)
                status = when (data.optString("error")) {
                    "login_required" -> tr("creator.research.analyze_login", "Log in to run a live catalog search.")
                    "cooldown" -> tr("creator.research.analyze_cooldown", "Please wait before another live search.")
                    "daily_limit" -> tr("creator.research.analyze_daily_limit", "Daily live search limit reached (5 per UTC day).")
                    "busy" -> tr("creator.research.analyze_busy", "Another Analyze is running. Please wait a few seconds.")
                    else -> tr("creator.research.analyze_error", "Live catalog search could not start.")
                }
                return@LaunchedEffect
            }
            searchId = data.optString("search_id")
            analyzeLimits = parseAnalyzeLimits(data.optJSONObject("daily"))
        } catch (_: Exception) {
            analyzing = false
            status = tr("creator.research.analyze_error", "Live catalog search could not start.")
        }
        pendingAnalyze = false
    }

    LaunchedEffect(searchId) {
        if (searchId.isBlank()) return@LaunchedEffect
        while (true) {
            try {
                val data = api.call("eazy-research-search-status", mapOf("search_id" to searchId))
                if (data.optBoolean("ok", false)) {
                    products = parseProducts(data.optJSONArray("products"))
                    preview = false
                    searchEmptyReason = data.optString("empty_reason")
                    searchAmazonReturned = data.optInt("amazon_returned", 0)
                    val done = data.optBoolean("done", false) ||
                        data.optString("status") == "done" ||
                        data.optString("status") == "error"
                    if (done) {
                        analyzing = false
                        if (data.optString("status") == "error") {
                            searchEmptyReason = "error"
                            status = tr("creator.research.analyze_error", "Live catalog search could not start.")
                        } else if (products.isNotEmpty()) {
                            status = tr("creator.research.analyze_found", "{n} products found")
                                .replace("{n}", products.size.toString())
                        }
                        break
                    }
                }
            } catch (_: Exception) {
                // keep polling until the worker marks the search done
            }
            delay(900)
        }
    }

    val filtered = remember(products, query, selectedNiches, designTypes, languages, personalizations, selectedAudiences, sort, view, watched, searchId, marketplace) {
        filterProducts(
            products,
            query,
            selectedNiches,
            designTypes,
            languages,
            personalizations,
            selectedAudiences,
            sort,
            view,
            watched,
            marketplace,
            sessionSearch = searchId.isNotBlank(),
        )
    }

    val filterPanel: @Composable () -> Unit = {
        ResearchFilterPanel(
            translationStore = translationStore,
            query = query,
            onQuery = { query = it },
            analyzeEnabled = tokenStore.isLoggedIn() && !analyzing,
            analyzing = analyzing,
            onAnalyze = {
                analyzing = true
                products = emptyList()
                searchId = ""
                searchEmptyReason = "running"
                searchAmazonReturned = 0
                selectedNiches = emptySet()
                designTypes = emptySet()
                languages = emptySet()
                personalizations = emptySet()
                selectedAudiences = emptySet()
                view = "opportunities"
                pendingAnalyze = true
            },
            sort = sort,
            onSort = { sort = it },
            marketplace = marketplace,
            marketplaces = marketplaces,
            onMarketplace = { marketplace = it },
            niches = niches,
            selectedNiches = selectedNiches,
            onToggleNiche = { key ->
                selectedNiches = if (key in selectedNiches) selectedNiches - key else selectedNiches + key
            },
            designTypes = designTypes,
            onToggleDesignType = { key ->
                designTypes = if (key in designTypes) designTypes - key else designTypes + key
            },
            languages = languages,
            onToggleLanguage = { key ->
                languages = if (key in languages) languages - key else languages + key
            },
            personalizations = personalizations,
            onTogglePersonalization = { key ->
                personalizations = if (key in personalizations) personalizations - key else personalizations + key
            },
            selectedAudiences = selectedAudiences,
            onToggleAudience = { key ->
                selectedAudiences = if (key in selectedAudiences) selectedAudiences - key else selectedAudiences + key
            },
            facets = facets,
            analyzeLimits = analyzeLimits,
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
                if (!filtersCollapsed) {
                    Column(
                        modifier = Modifier
                            .width(248.dp)
                            .fillMaxHeight()
                            .background(RailBg)
                            .verticalScroll(filterScroll)
                            .padding(10.dp),
                    ) {
                        filterPanel()
                    }
                }
                FilterCollapseRail(
                    collapsed = filtersCollapsed,
                    translationStore = translationStore,
                    onToggle = {
                        filtersCollapsed = !filtersCollapsed
                        saveFiltersCollapsed(context, filtersCollapsed)
                    },
                )
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
                    error != null && products.isEmpty() && !analyzing -> Text(error ?: "", color = TextDim, fontSize = 14.sp)
                    else -> Box(Modifier.weight(1f).fillMaxWidth()) {
                        val showSkeleton = (loading && products.isEmpty()) || (analyzing && filtered.isEmpty())
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(160.dp),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 8.dp),
                        ) {
                            if (showSkeleton) {
                                items(8) {
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(0.72f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color.White.copy(alpha = 0.08f)),
                                    )
                                }
                            } else {
                                items(filtered, key = { watchId(it) }) { product ->
                                    OpportunityCard(
                                        product = product,
                                        nicheLabels = displayTopicLabels(product, niches),
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
                        if (!showSkeleton && filtered.isEmpty()) {
                            Text(
                                emptyGridCopy(
                                    translationStore = translationStore,
                                    view = view,
                                    searchId = searchId,
                                    analyzing = analyzing,
                                    emptyReason = searchEmptyReason,
                                    amazonReturned = searchAmazonReturned,
                                ),
                                color = TextDim,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 8.dp),
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
                        .verticalScroll(filterScroll)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ResearchFilterPanel(
    translationStore: TranslationStore,
    query: String,
    onQuery: (String) -> Unit,
    analyzeEnabled: Boolean,
    analyzing: Boolean,
    onAnalyze: () -> Unit,
    sort: String,
    onSort: (String) -> Unit,
    marketplace: String,
    marketplaces: List<ResearchMarketplace>,
    onMarketplace: (String) -> Unit,
    niches: List<ResearchNiche>,
    selectedNiches: Set<String>,
    onToggleNiche: (String) -> Unit,
    designTypes: Set<String>,
    onToggleDesignType: (String) -> Unit,
    languages: Set<String>,
    onToggleLanguage: (String) -> Unit,
    personalizations: Set<String>,
    onTogglePersonalization: (String) -> Unit,
    selectedAudiences: Set<String>,
    onToggleAudience: (String) -> Unit,
    facets: Map<String, List<ResearchFacet>>,
    analyzeLimits: AnalyzeLimits,
    view: String,
    onView: (String) -> Unit,
) {
    fun tr(key: String, fallback: String) = translationStore.t(key, fallback)
    fun countOf(group: String, key: String): Int =
        facets[group]?.firstOrNull { it.key == key }?.count ?: 0
    val hosts = marketplaces.ifEmpty {
        listOf("amazon.de", "amazon.co.uk", "amazon.fr", "amazon.it", "amazon.es", "amazon.com", "amazon.ca")
            .map { ResearchMarketplace(it, marketplaceTagFromHost(it)) }
    }
    val countryOptions = listOf("all" to tr("creator.research.country_all", "All countries")) +
        hosts.map { it.host to countryLabel(it.host, translationStore) }
    val sorts = listOf(
        "review_growth" to tr("creator.research.sort_review_growth", "Review growth"),
        "reviews" to tr("creator.research.sort_reviews", "Reviews"),
        "bsr" to tr("creator.research.sort_bsr", "BSR"),
        "newest" to tr("creator.research.sort_newest", "Newest snapshot"),
    )
    val views = listOf(
        "opportunities" to tr("creator.research.tab_opportunities", "Opportunities"),
        "rising" to tr("creator.research.tab_rising", "Rising"),
        "review_growth" to tr("creator.research.tab_review_growth", "Review growth"),
        "watched" to tr("creator.research.tab_watched", "Watched"),
    )
    var countryOpen by remember { mutableStateOf(false) }
    var viewsOpen by remember { mutableStateOf(false) }
    val countryLabelText = countryOptions.firstOrNull { it.first == marketplace }?.second ?: countryOptions.first().second
    val viewLabel = views.firstOrNull { it.first == view }?.second ?: views.first().second
    ExposedDropdownMenuBox(expanded = countryOpen, onExpandedChange = { countryOpen = it }) {
        OutlinedTextField(
            value = countryLabelText,
            onValueChange = {},
            readOnly = true,
            label = { Text(tr("creator.research.country", "Country"), color = TextDim) },
            leadingIcon = {
                flagCodeForMarketplace(marketplace)?.let { code ->
                    GlassCircularFlag(countryCode = code, size = 20.dp)
                }
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(countryOpen) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextMain,
                unfocusedTextColor = TextMain,
                focusedBorderColor = Accent,
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                focusedLabelColor = TextDim,
                unfocusedLabelColor = TextDim,
            ),
        )
        ExposedDropdownMenu(expanded = countryOpen, onDismissRequest = { countryOpen = false }) {
            countryOptions.forEach { (id, label) ->
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            flagCodeForMarketplace(id)?.let { code ->
                                GlassCircularFlag(countryCode = code, size = 18.dp)
                            }
                            Text(label)
                        }
                    },
                    onClick = {
                        onMarketplace(id)
                        countryOpen = false
                    },
                )
            }
        }
    }
    ExposedDropdownMenuBox(expanded = viewsOpen, onExpandedChange = { viewsOpen = it }) {
        OutlinedTextField(
            value = viewLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(tr("creator.research.views", "Views"), color = TextDim) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(viewsOpen) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextMain,
                unfocusedTextColor = TextMain,
                focusedBorderColor = Accent,
                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                focusedLabelColor = TextDim,
                unfocusedLabelColor = TextDim,
            ),
        )
        ExposedDropdownMenu(expanded = viewsOpen, onDismissRequest = { viewsOpen = false }) {
            views.forEach { (id, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onView(id)
                        viewsOpen = false
                    },
                )
            }
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f),
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
        Text(
            if (analyzing) {
                tr("creator.research.analyze_loading", "Analyzing…")
            } else {
                tr("creator.research.analyze_remaining", "Analyze ({remaining}/{limit})")
                    .replace("{remaining}", analyzeLimits.remaining.toString())
                    .replace("{limit}", analyzeLimits.limit.toString())
            },
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .heightIn(min = 36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (analyzeEnabled) Color(0xFFF97316) else Color.White.copy(alpha = 0.12f))
                .clickable(enabled = analyzeEnabled, onClick = onAnalyze)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
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
    Text(
        tr("creator.research.topics", "Topics"),
        color = TextDim,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
    if (selectedNiches.isEmpty()) {
        Text(
            tr("creator.research.topics_all_hint", "All topics"),
            color = TextDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
    val topicRows = (facets["topics"] ?: emptyList()).ifEmpty {
        niches.map { ResearchFacet(it.key, countOf("topics", it.key)) }
    }.filter { it.key.isNotBlank() && it.key != "user_search" }
    topicRows.forEach { item ->
        val label = niches.firstOrNull { it.key == item.key }?.label ?: item.key
        FilterCheckRow(label = label, count = item.count, checked = item.key in selectedNiches, onToggle = { onToggleNiche(item.key) })
    }
    Text(
        tr("creator.research.audience", "Audience"),
        color = TextDim,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
    if (selectedAudiences.isEmpty()) {
        Text(
            tr("creator.research.audience_all_hint", "All audiences"),
            color = TextDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
    listOf(
        "men" to tr("creator.research.audience_men", "Men"),
        "women" to tr("creator.research.audience_women", "Women"),
        "kids" to tr("creator.research.audience_kids", "Kids"),
        "toddler" to tr("creator.research.audience_toddler", "Toddler"),
    ).forEach { (id, label) ->
        FilterCheckRow(label = label, count = countOf("audience", id), checked = id in selectedAudiences, onToggle = { onToggleAudience(id) })
    }
    Text(
        tr("creator.research.custom_design", "Custom Design"),
        color = TextDim,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
    )
    if (personalizations.isEmpty() || personalizations.size == 2) {
        Text(
            tr("creator.research.custom_design_all_hint", "Neither or both = all listings"),
            color = TextDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
    FilterCheckRow(
        label = tr("creator.research.custom_design_yes", "Yes"),
        count = countOf("personalization", "personalizable"),
        checked = "personalizable" in personalizations,
        onToggle = { onTogglePersonalization("personalizable") },
    )
    FilterCheckRow(
        label = tr("creator.research.custom_design_no", "No"),
        count = countOf("personalization", "standard"),
        checked = "standard" in personalizations,
        onToggle = { onTogglePersonalization("standard") },
    )
    Text(
        tr("creator.research.design_type", "Design type"),
        color = TextDim,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
    if (designTypes.isEmpty()) {
        Text(
            tr("creator.research.design_type_all_hint", "All design types"),
            color = TextDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
    listOf(
        "design_only" to tr("creator.quick_inspirations.content_type_design_only", "Design Only"),
        "text_only" to tr("creator.quick_inspirations.content_type_text_only", "Text Only"),
        "design_text" to tr("creator.quick_inspirations.content_type_design_text", "Design + Text"),
    ).forEach { (id, label) ->
        FilterCheckRow(label = label, count = countOf("design_type", id), checked = id in designTypes, onToggle = { onToggleDesignType(id) })
    }
    Text(
        tr("creator.research.language", "Language"),
        color = TextDim,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
    if (languages.isEmpty()) {
        Text(
            tr("creator.research.language_all_hint", "All languages"),
            color = TextDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
    listOf(
        "none" to tr("creator.quick_inspirations.language_none", "None"),
        "en" to tr("creator.research.lang_en", "English"),
        "de" to tr("creator.research.lang_de", "German"),
        "es" to tr("creator.research.lang_es", "Spanish"),
        "fr" to tr("creator.research.lang_fr", "French"),
        "it" to tr("creator.research.lang_it", "Italian"),
    ).forEach { (id, label) ->
        FilterCheckRow(
            label = label,
            count = countOf("language", id),
            checked = id in languages,
            flagCode = flagCodeForLanguage(id),
            onToggle = { onToggleLanguage(id) },
        )
    }
}

@Composable
private fun FilterCheckRow(
    label: String,
    count: Int,
    checked: Boolean,
    onToggle: () -> Unit,
    flagCode: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = Color(0xFFF97316), uncheckedColor = TextDim),
        )
        flagCode?.let { GlassCircularFlag(countryCode = it, size = 18.dp) }
        Text(label, color = TextMain, fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(count.toString(), color = TextDim, fontSize = 11.sp)
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
        fontSize = 11.sp,
        modifier = Modifier
            .heightIn(min = 28.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color(0xFFF97316).copy(alpha = 0.28f) else Color.White.copy(alpha = 0.06f))
            .border(
                width = 1.dp,
                color = if (selected) Color(0xB3FB923C) else Color.White.copy(alpha = 0.14f),
                shape = RoundedCornerShape(999.dp),
            )
            .semantics {
                this.selected = selected
                role = if (radio) Role.RadioButton else Role.Checkbox
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}

@Composable
private fun OpportunityCard(
    product: ResearchProduct,
    nicheLabels: List<String>,
    watched: Boolean,
    translationStore: TranslationStore,
    onOpen: () -> Unit,
    onToggleWatch: () -> Unit,
) {
    fun tr(key: String, fallback: String) = translationStore.t(key, fallback)
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
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(product.title, color = TextMain, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, minLines = 2)
            ResearchStatsBox(product = product, translationStore = translationStore)
            product.bsrCategory?.takeIf { it.isNotBlank() }?.let { cat ->
                Text(cat, color = TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            nicheLabels.forEach { label ->
                Text(label, color = TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    val topics = displayTopicLabels(product, niches)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
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
                    .heightIn(min = 220.dp, max = 360.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit,
            )
        }
        Text(product.title, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (product.brand.isNotBlank()) {
            StatRow(tr("creator.research.brand", "Brand"), product.brand)
        }
        if (product.marketplaceTag.isNotBlank()) {
            StatFlagRow(
                label = tr("creator.research.marketplace", "Marketplace"),
                flagCode = flagCodeForMarketplace(product.marketplace),
                value = product.marketplaceTag,
            )
        }
        StatRow(tr("creator.research.bsr_label", "BSR"), formatBsr(product, translationStore))
        formatBsrChange(product, translationStore)?.let { (label, _) ->
            StatRow(tr("creator.research.bsr_change", "BSR change"), label)
        }
        product.relevanceScore?.let { score ->
            StatRow(tr("creator.research.relevance", "Category rank"), score.toString())
        }
        product.reviews?.let { count ->
            StatRow(tr("creator.research.reviews_total", "Reviews total"), count.toString())
        }
        if (topics.isNotEmpty()) {
            StatRow(tr("creator.research.topic", "Topic"), topics.joinToString(" · "))
        }
        product.designType?.takeIf { it.isNotBlank() }?.let { type ->
            StatRow(tr("creator.research.design_type", "Design type"), type.replace('_', ' '))
        }
        product.language?.takeIf { it.isNotBlank() }?.let { lang ->
            StatFlagRow(
                label = tr("creator.research.language", "Language"),
                flagCode = flagCodeForLanguage(lang),
                value = if (lang.equals("none", ignoreCase = true)) {
                    tr("creator.quick_inspirations.language_none", "None")
                } else {
                    lang.uppercase(Locale.ROOT)
                },
            )
        }
        StatRow(
            tr("creator.research.custom_design", "Custom Design"),
            if (product.personalizable) tr("creator.research.yes", "Yes") else tr("creator.research.no", "No"),
        )
        if (product.tags.isNotEmpty()) {
            StatRow(tr("creator.research.tags", "Tags"), product.tags.joinToString(", "))
        }
        if (product.prompt.isNotBlank()) {
            StatRow(tr("creator.research.prompt", "Prompt"), product.prompt)
        }
        Text(
            tr("creator.research.relevance_hint", "Score 1–100 from this listing's BSR in its own marketplace category. Not demand or sales."),
            color = TextDim,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextDim, fontSize = 14.sp)
        Text(value, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatFlagRow(label: String, flagCode: String?, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextDim, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            flagCode?.let { GlassCircularFlag(countryCode = it, size = 16.dp) }
            Text(value, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ResearchStatsBox(product: ResearchProduct, translationStore: TranslationStore) {
    fun tr(key: String, fallback: String) = translationStore.t(key, fallback)
    val change = formatBsrChange(product, translationStore)
    val rankText = formatBsrRank(product) ?: tr("creator.research.bsr_missing", "No BSR")
    val catScore = product.relevanceScore?.toString() ?: "—"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xC70A0618))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    tr("creator.research.bsr_label", "BSR").uppercase(Locale.ROOT),
                    color = TextDim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(rankText, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    change?.let { (_, improved) ->
                        Text(
                            if (improved) "↑" else "↓",
                            color = if (improved) SafeGreen else Color(0xFFFF8D85),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    tr("creator.research.cat_rank", "Cat. rank").uppercase(Locale.ROOT),
                    color = TextDim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(HeartOn),
                    )
                    Text(catScore, color = Color(0xFFFFD7B0), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        product.language?.takeIf { it.isNotBlank() }?.let { lang ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                flagCodeForLanguage(lang)?.let { GlassCircularFlag(countryCode = it, size = 16.dp) }
                Text(
                    if (lang.equals("none", ignoreCase = true)) {
                        tr("creator.quick_inspirations.language_none", "None")
                    } else {
                        lang.uppercase(Locale.ROOT)
                    },
                    color = TextMain,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun formatBsrRank(product: ResearchProduct): String? {
    val rank = product.bsr ?: return null
    return String.format(Locale.GERMANY, "%,d", rank)
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

private fun emptyGridCopy(
    translationStore: TranslationStore,
    view: String,
    searchId: String,
    analyzing: Boolean,
    emptyReason: String,
    amazonReturned: Int,
): String {
    fun tr(key: String, fallback: String) = translationStore.t(key, fallback)
    if (searchId.isNotBlank() && !analyzing) {
        when (emptyReason) {
            "catalog_empty" -> return tr(
                "creator.research.analyze_no_amazon",
                "Amazon catalog had no matches for this search. Try a more specific term such as vegan t-shirt.",
            )
            "filtered_reprint" -> return tr(
                "creator.research.analyze_no_reprint",
                "Amazon returned {n} products, but none are reprint-safe.",
            ).replace("{n}", amazonReturned.toString())
            "error" -> return tr("creator.research.analyze_error", "Live catalog search could not start.")
        }
    }
    if (view == "watched") {
        return tr(
            "creator.research.empty_watched",
            "No watched products yet. Tap the heart on a product to start tracking it.",
        )
    }
    return tr(
        "creator.research.empty_search",
        "No reprint-safe products match this search. Try a broader niche such as Coffee or Hiking.",
    )
}

private fun filterProducts(
    products: List<ResearchProduct>,
    query: String,
    niches: Set<String>,
    designTypes: Set<String>,
    languages: Set<String>,
    personalizations: Set<String>,
    audiences: Set<String>,
    sort: String,
    view: String,
    watched: Set<String>,
    marketplace: String,
    sessionSearch: Boolean = false,
): List<ResearchProduct> {
    var rows = products.filter { it.reprintOk }
    if (marketplace.isNotBlank() && marketplace != "all") {
        rows = rows.filter { it.marketplace.equals(marketplace, ignoreCase = true) }
    }
    if (!sessionSearch && niches.isNotEmpty()) rows = rows.filter { topicKeyOf(it) in niches }
    if (!sessionSearch && designTypes.isNotEmpty()) {
        rows = rows.filter { (it.designType ?: "").lowercase(Locale.ROOT) in designTypes }
    }
    if (!sessionSearch && languages.isNotEmpty()) {
        rows = rows.filter { (it.language ?: "").lowercase(Locale.ROOT) in languages }
    }
    if (!sessionSearch && personalizations.size == 1) {
        rows = rows.filter { product ->
            val key = if (product.personalizable) "personalizable" else "standard"
            key in personalizations
        }
    }
    if (!sessionSearch && audiences.isNotEmpty()) {
        rows = rows.filter { it.audience in audiences }
    }
    val q = query.trim().lowercase(Locale.ROOT)
    if (q.isNotEmpty() && !sessionSearch) {
        rows = rows.filter {
            listOf(it.title, it.brand, it.asin, it.nicheKey, it.marketplace, it.marketplaceTag)
                .joinToString(" ").lowercase(Locale.ROOT).contains(q)
        }
    }
    if (!sessionSearch) {
        rows = when (view) {
            "rising" -> rows.filter { it.trend == "rising" || it.risingScore > 0 }
            "review_growth" -> rows.filter { (it.reviewDelta ?: 0) > 0 }
            "watched" -> rows.filter { isWatched(watched, it) }
            else -> rows
        }
    }
    return when (sort) {
        "reviews" -> rows.sortedByDescending { it.reviews ?: 0 }
        "bsr" -> rows.sortedBy { it.bsr ?: Int.MAX_VALUE }
        "newest" -> rows.sortedByDescending { it.capturedAt ?: 0L }
        else -> rows.sortedByDescending { it.reviewDelta ?: 0 }
    }
}

private val ToddlerRe =
    Regex("""\b(toddlers?|bab(?:y|ies)|infants?|newborns?|kleinkind(?:er)?|s[äa]ugling(?:e)?|b[ée]b[ée]s?|neonat[ioe]s?|neugeboren(?:e|es)?)\b""", RegexOption.IGNORE_CASE)
private val KidsRe =
    Regex("""\b(kids?|kinder|child(?:ren)?|youth|jungen|m[äa]dchen|boys?|girls?|juniors?|teens?)\b""", RegexOption.IGNORE_CASE)
private val MenRe =
    Regex("""\b(men'?s|mens\b|herren|homme(?:s)?|uomo|uomini|hombre(?:s)?|m[äa]nner)\b""", RegexOption.IGNORE_CASE)
private val WomenRe =
    Regex("""\b(women'?s|womens\b|damen|femme(?:s)?|donna|donne|mujer(?:es)?|ladies|lady)\b""", RegexOption.IGNORE_CASE)

private fun audienceScore(text: String, re: Regex, weight: Int): Int =
    if (text.isBlank()) 0 else re.findAll(text).count() * weight

private fun classifyAudience(title: String, category: String): String {
    val t = title
    val c = category
    val blob = "$t $c".trim()
    if (blob.isEmpty()) return ""
    if (ToddlerRe.containsMatchIn(blob)) return "toddler"
    if (KidsRe.containsMatchIn(blob)) return "kids"
    val men = audienceScore(t, MenRe, 3) + audienceScore(c, MenRe, 1)
    val women = audienceScore(t, WomenRe, 3) + audienceScore(c, WomenRe, 1)
    if (men > 0 && women > 0) {
        if (men == women) return ""
        return if (men > women) "men" else "women"
    }
    if (men > 0) return "men"
    if (women > 0) return "women"
    return ""
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
            topic = p.optString("topic"),
            subNiche = p.optString("subtopic").ifBlank { p.optString("sub_niche") }.ifBlank { p.optString("sub_niche_key") },
            tags = parseStringList(p.opt("tags")),
            prompt = p.optString("prompt"),
            designType = p.optString("design_type").ifBlank { null },
            language = p.optString("language").ifBlank { null },
            personalizable = p.optInt("personalizable", 0) == 1 ||
                p.optBoolean("personalizable", false) ||
                p.optString("personalization").equals("personalizable", ignoreCase = true),
            audience = p.optString("audience").ifBlank {
                classifyAudience(
                    p.optString("title"),
                    listOf(
                        latest.optString("bsr_category"),
                        p.optString("bsr_category"),
                        p.optString("browse_node"),
                        p.optString("category"),
                    ).filter { it.isNotBlank() }.joinToString(" "),
                )
            },
            reprintOk = p.optBoolean("reprint_ok", true),
            rating = latest.optDoubleOrNull("rating"),
            reviews = latest.optIntOrNull("reviews_count"),
            reviewDelta = p.optIntOrNull("review_delta"),
            reviewWindow = p.optString("review_delta_window").ifBlank { null },
            bsr = latest.optIntOrNull("bsr"),
            bsrCategory = latest.optString("bsr_category").ifBlank { p.optString("bsr_category") }.ifBlank { null },
            bsrDelta = p.optIntOrNull("bsr_delta"),
            bsrImproved = p.optBooleanOrNull("bsr_improved"),
            relevanceScore = p.optIntOrNull("relevance_score"),
            capturedAt = latest.optLongOrNull("captured_at"),
            trend = p.optString("trend"),
            risingScore = p.optInt("rising_score", 0),
        )
    }
    return out
}

private fun parseFacets(obj: JSONObject?): Map<String, List<ResearchFacet>> {
    if (obj == null) return emptyMap()
    val out = mutableMapOf<String, List<ResearchFacet>>()
    listOf("topics", "audience", "personalization", "design_type", "language").forEach { group ->
        val arr = obj.optJSONArray(group) ?: return@forEach
        val rows = ArrayList<ResearchFacet>(arr.length())
        for (i in 0 until arr.length()) {
            val row = arr.optJSONObject(i) ?: continue
            val key = row.optString("key")
            if (key.isBlank()) continue
            rows += ResearchFacet(key, row.optInt("count", 0))
        }
        out[group] = rows
    }
    return out
}

private fun parseMarketplaces(arr: JSONArray?): List<ResearchMarketplace> {
    if (arr == null) return emptyList()
    val out = ArrayList<ResearchMarketplace>(arr.length())
    for (i in 0 until arr.length()) {
        val row = arr.optJSONObject(i) ?: continue
        val host = row.optString("host")
        if (host.isBlank()) continue
        out += ResearchMarketplace(host, row.optString("tag").ifBlank { marketplaceTagFromHost(host) })
    }
    return out
}

private fun parseAnalyzeLimits(obj: JSONObject?): AnalyzeLimits {
    if (obj == null) return AnalyzeLimits()
    return AnalyzeLimits(
        used = obj.optInt("used", 0),
        remaining = obj.optInt("remaining", 5),
        limit = obj.optInt("limit", 5),
        busy = obj.optBoolean("busy", false),
    )
}

private fun parseStringList(raw: Any?): List<String> {
    if (raw is JSONArray) {
        return (0 until raw.length()).mapNotNull { raw.optString(it).takeIf { s -> s.isNotBlank() } }
    }
    if (raw is String && raw.isNotBlank()) {
        return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
    }
    return emptyList()
}

private fun topicKeyOf(product: ResearchProduct): String {
    val topic = product.topic.trim().lowercase(Locale.ROOT)
    if (topic.isNotBlank()) return topic
    val key = product.nicheKey.trim().lowercase(Locale.ROOT)
    return if (key.isNotBlank() && key != "user_search") key else ""
}

private fun displayTopicLabels(product: ResearchProduct, niches: List<ResearchNiche>): List<String> {
    val out = mutableListOf<String>()
    val topic = product.topic.trim()
    val sub = product.subNiche.trim()
    if (topic.isNotBlank()) out += if (sub.isNotBlank()) "$topic · $sub" else topic
    val key = product.nicheKey.trim()
    if (key.isNotBlank() && key.lowercase(Locale.ROOT) != "user_search") {
        val label = niches.firstOrNull { it.key == key }?.label ?: key.replace('_', ' ')
        val already = topic.isNotBlank() && (topic.equals(key, true) || topic.equals(label, true))
        if (!already) out += label
    }
    return out
}

private fun flagCodeForLanguage(language: String?): String? {
    val code = language?.trim()?.lowercase(Locale.ROOT).orEmpty()
    if (code.isBlank() || code == "none") return null
    return when (code) {
        "en" -> "GB"
        "de" -> "DE"
        "es" -> "ES"
        "fr" -> "FR"
        "it" -> "IT"
        else -> null
    }
}

private fun flagCodeForMarketplace(host: String): String? = when (host.trim().lowercase(Locale.ROOT)) {
    "amazon.de" -> "DE"
    "amazon.co.uk" -> "GB"
    "amazon.fr" -> "FR"
    "amazon.it" -> "IT"
    "amazon.es" -> "ES"
    "amazon.com" -> "US"
    "amazon.ca" -> "CA"
    "amazon.co.jp" -> "JP"
    "amazon.com.au" -> "AU"
    else -> null
}

private fun countryLabel(host: String, translationStore: TranslationStore): String {
    val key = when (host) {
        "amazon.de" -> "creator.research.country_amazon_de"
        "amazon.co.uk" -> "creator.research.country_amazon_co_uk"
        "amazon.fr" -> "creator.research.country_amazon_fr"
        "amazon.it" -> "creator.research.country_amazon_it"
        "amazon.es" -> "creator.research.country_amazon_es"
        "amazon.com" -> "creator.research.country_amazon_com"
        "amazon.ca" -> "creator.research.country_amazon_ca"
        "amazon.co.jp" -> "creator.research.country_amazon_co_jp"
        "amazon.com.au" -> "creator.research.country_amazon_com_au"
        else -> ""
    }
    val fallback = when (host) {
        "amazon.de" -> "DE · Amazon.de"
        "amazon.co.uk" -> "UK · Amazon.co.uk"
        "amazon.fr" -> "FR · Amazon.fr"
        "amazon.it" -> "IT · Amazon.it"
        "amazon.es" -> "ES · Amazon.es"
        "amazon.com" -> "US · Amazon.com"
        "amazon.ca" -> "CA · Amazon.ca"
        "amazon.co.jp" -> "JP · Amazon.co.jp"
        "amazon.com.au" -> "AU · Amazon.com.au"
        else -> host
    }
    return if (key.isBlank()) fallback else translationStore.t(key, fallback)
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
