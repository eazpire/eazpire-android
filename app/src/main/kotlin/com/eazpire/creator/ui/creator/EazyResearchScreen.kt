package com.eazpire.creator.ui.creator

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
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
private const val FILTER_FOLDS_KEY = "eazy-research-filter-folds"
internal val FILTER_FOLD_DEFAULTS = mapOf(
    "topics" to true,
    "audience" to false,
    "custom_design" to false,
    "design_type" to false,
    "language" to false,
    "opportunity" to false,
    "trends_topics" to true,
    "trends_type" to false,
    "trends_time" to false,
    "trends_volume" to false,
)

private data class ResearchProduct(
    val asin: String,
    val marketplace: String,
    val marketplaceTag: String,
    val title: String,
    val brand: String,
    val imageUrl: String,
    val imageThumbUrl: String,
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
    val searchIngestedAt: Long?,
    val trend: String,
    val risingScore: Int,
    val opportunityBucket: String?,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EazyResearchScreen(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    maxHeight: Dp = Dp.Unspecified,
    onSendToGenerator: (ResearchGeneratorHandoff) -> Unit = {},
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
    var analyzeLimits by remember { mutableStateOf(AnalyzeLimits()) }
    val savedIdeas = remember { ResearchFilterPrefs.loadIdeas(context) }
    var marketplace by remember { mutableStateOf(savedIdeas.marketplace) }
    var query by remember { mutableStateOf(savedIdeas.query) }
    var draftQuery by remember { mutableStateOf(savedIdeas.query) }
    var analyzeQuery by remember { mutableStateOf("") }
    var analyzeModalOpen by remember { mutableStateOf(false) }
    var justAddedIds by remember { mutableStateOf(setOf<String>()) }
    var justAddedUntil by remember { mutableStateOf(0L) }
    var seenAnalyzeIds by remember { mutableStateOf(setOf<String>()) }
    var debouncedQuery by remember { mutableStateOf(savedIdeas.query) }
    var hasMore by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var selectedNiches by remember { mutableStateOf(savedIdeas.niches) }
    var designTypes by remember { mutableStateOf(savedIdeas.designTypes) }
    var languages by remember { mutableStateOf(savedIdeas.languages) }
    var personalizations by remember { mutableStateOf(savedIdeas.personalizations) }
    var selectedAudiences by remember { mutableStateOf(savedIdeas.audiences) }
    var selectedOpportunity by remember { mutableStateOf(savedIdeas.opportunity) }
    var researchTab by remember { mutableStateOf("ideas") }
    val trendsState = remember { TrendsUiState() }
    var sort by remember { mutableStateOf(savedIdeas.sort) }
    var sortDir by remember { mutableStateOf(savedIdeas.sortDir) }
    var view by remember { mutableStateOf("opportunities") }
    var analyzeMarketplace by remember { mutableStateOf("all") }
    var analyzeLanguage by remember { mutableStateOf("all") }
    var analyzeResolvedMarketplace by remember { mutableStateOf("") }
    var analyzeResolvedLanguage by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<ResearchProduct?>(null) }
    var watched by remember { mutableStateOf(loadWatchedAsins(context)) }
    var filtersCollapsed by remember { mutableStateOf(loadFiltersCollapsed(context)) }
    var filtersSheetOpen by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    fun researchListParams(offset: Int): Map<String, String> {
        val params = mutableMapOf(
            "reprint_ok" to "1",
            "limit" to "32",
            "offset" to offset.coerceAtLeast(0).toString(),
            "sort" to sort,
            "dir" to sortDir,
            "view" to if (view == "watched") "opportunities" else view,
        )
        if (marketplace.isNotBlank() && marketplace != "all") params["marketplace"] = marketplace
        if (debouncedQuery.isNotBlank()) params["q"] = debouncedQuery
        if (selectedNiches.isNotEmpty()) params["niche"] = selectedNiches.joinToString(",")
        if (designTypes.isNotEmpty()) params["design_type"] = designTypes.joinToString(",")
        if (languages.isNotEmpty()) params["language"] = languages.joinToString(",")
        if (personalizations.isNotEmpty()) params["personalization"] = personalizations.joinToString(",")
        if (selectedAudiences.isNotEmpty()) params["audience"] = selectedAudiences.joinToString(",")
        if (selectedOpportunity.isNotEmpty()) params["opportunity"] = selectedOpportunity.joinToString(",")
        return params
    }

    fun applyResearchListPayload(data: org.json.JSONObject, append: Boolean) {
        preview = data.optBoolean("preview", false)
        val page = parseProducts(data.optJSONArray("products"))
        if (searchId.isBlank()) {
            products = if (append) {
                val seen = products.map { watchId(it) }.toMutableSet()
                products + page.filter { seen.add(watchId(it)) }
            } else {
                page
            }
        }
        hasMore = data.optBoolean("has_more", false)
        if (data.has("niches") && !data.isNull("niches")) {
            niches = parseNiches(data.optJSONArray("niches"))
        }
        if (data.has("facets") && !data.isNull("facets")) {
            facets = parseFacets(data.optJSONObject("facets"))
        }
        if (data.has("marketplaces") && !data.isNull("marketplaces")) {
            marketplaces = parseMarketplaces(data.optJSONArray("marketplaces"))
        }
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

    LaunchedEffect(query) {
        debouncedQuery = query.trim()
    }

    LaunchedEffect(
        query,
        selectedNiches,
        designTypes,
        languages,
        personalizations,
        selectedAudiences,
        selectedOpportunity,
        marketplace,
        sort,
        sortDir,
    ) {
        ResearchFilterPrefs.saveIdeas(
            context,
            ResearchFilterSnapshot(
                query = query,
                niches = selectedNiches,
                designTypes = designTypes,
                languages = languages,
                personalizations = personalizations,
                audiences = selectedAudiences,
                opportunity = selectedOpportunity,
                marketplace = marketplace,
                sort = sort,
                sortDir = sortDir,
            ),
        )
    }

    LaunchedEffect(Unit) {
        ResearchAnalyzeStore.restore(context)
        val savedTrends = ResearchFilterPrefs.loadTrends(context)
        trendsState.geo = savedTrends.geo
        trendsState.language = savedTrends.language
        trendsState.query = savedTrends.query
        trendsState.draftQuery = savedTrends.query
        trendsState.selectedTopics = savedTrends.selectedTopics
        trendsState.productTypes = savedTrends.productTypes
        trendsState.volume = savedTrends.volume
        trendsState.time = savedTrends.time
        trendsState.sort = savedTrends.sort
        trendsState.sortDir = savedTrends.sortDir
        val job = ResearchAnalyzeStore.ideas
        if (job.running && job.searchId.isNotBlank()) {
            searchId = job.searchId
            analyzing = true
            if (job.query.isNotBlank()) analyzeQuery = job.query
        } else {
            try {
                val data = api.call("eazy-research-search-status", emptyMap())
                val sid = data.optString("search_id")
                val done = data.optBoolean("done", false) ||
                    data.optString("status") == "done" ||
                    data.optString("status") == "error" ||
                    data.optString("status") == "idle" ||
                    sid.isBlank()
                if (!done) {
                    searchId = sid
                    analyzing = true
                    analyzeQuery = data.optString("query").ifBlank { analyzeQuery }
                    ResearchAnalyzeStore.saveIdeas(
                        context,
                        ResearchJobSnapshot(searchId = sid, query = analyzeQuery, running = true),
                    )
                }
            } catch (_: Exception) {
            }
        }
        val tJob = ResearchAnalyzeStore.trends
        if (tJob.running && tJob.searchId.isNotBlank()) {
            trendsState.searchId = tJob.searchId
            trendsState.searching = true
            if (tJob.query.isNotBlank()) trendsState.analyzeQuery = tJob.query
        }
    }

    LaunchedEffect(justAddedUntil) {
        if (justAddedUntil <= 0L) return@LaunchedEffect
        val wait = justAddedUntil - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        justAddedIds = emptySet()
    }

    val ideasToast = ResearchAnalyzeStore.doneToast
    LaunchedEffect(ideasToast) {
        if (ideasToast == null) return@LaunchedEffect
        delay(RESEARCH_DONE_TOAST_MS)
        ResearchAnalyzeStore.clearToast()
    }

    LaunchedEffect(
        tokenStore.getJwt(),
        marketplace,
        selectedNiches,
        designTypes,
        languages,
        personalizations,
        selectedAudiences,
        selectedOpportunity,
        sort,
        view,
        debouncedQuery,
        searchId,
    ) {
        if (searchId.isNotBlank() || view == "watched") return@LaunchedEffect
        loading = true
        error = null
        products = emptyList()
        hasMore = false
        try {
            val data = api.call("eazy-research-products", researchListParams(0))
            if (!data.optBoolean("ok", false)) {
                error = tr("creator.research.error", "Research data could not be loaded.")
            } else {
                applyResearchListPayload(data, append = false)
                gridState.scrollToItem(0)
            }
        } catch (_: Exception) {
            error = tr("creator.research.error", "Research data could not be loaded.")
        }
        loading = false
    }

    LaunchedEffect(gridState, hasMore, searchId, view) {
        snapshotFlow {
            Triple(
                gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0,
                products.size,
                loading || loadingMore,
            )
        }.collect { (lastVisible, size, busy) ->
            if (
                hasMore &&
                !busy &&
                searchId.isBlank() &&
                view != "watched" &&
                size > 0 &&
                lastVisible >= size - 5
            ) {
                loadingMore = true
                try {
                    val data = api.call("eazy-research-products", researchListParams(size))
                    if (data.optBoolean("ok", false)) applyResearchListPayload(data, append = true)
                } catch (_: Exception) {
                    // nächste Scroll-Position versucht es erneut
                }
                loadingMore = false
            }
        }
    }

    LaunchedEffect(pendingAnalyze) {
        if (!pendingAnalyze) return@LaunchedEffect
        val q = analyzeQuery.trim()
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
            if (analyzeMarketplace.isNotBlank() && analyzeMarketplace != "all") {
                payload.put("marketplace", analyzeMarketplace)
            }
            if (analyzeLanguage.isNotBlank() && analyzeLanguage != "all") {
                payload.put("language", analyzeLanguage)
            }
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
            analyzeResolvedMarketplace = data.optString("marketplace")
            analyzeResolvedLanguage = data.optString("language")
            analyzeLimits = parseAnalyzeLimits(data.optJSONObject("daily"))
            seenAnalyzeIds = emptySet()
            justAddedIds = emptySet()
            ResearchAnalyzeStore.saveIdeas(
                context,
                ResearchJobSnapshot(searchId = searchId, query = q, running = true),
            )
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
                    val incoming = products.map { watchId(it) }.toSet()
                    val fresh = incoming - seenAnalyzeIds
                    if (fresh.isNotEmpty()) {
                        seenAnalyzeIds = incoming
                        justAddedIds = justAddedIds + fresh
                        justAddedUntil = System.currentTimeMillis() + RESEARCH_JUST_ADDED_MS
                    }
                    ResearchAnalyzeStore.saveIdeas(
                        context,
                        ResearchJobSnapshot(
                            searchId = searchId,
                            query = analyzeQuery,
                            running = true,
                            resultCount = products.size,
                        ),
                    )
                    val done = data.optBoolean("done", false) ||
                        data.optString("status") == "done" ||
                        data.optString("status") == "error"
                    if (done) {
                        analyzing = false
                        ResearchAnalyzeStore.saveIdeas(
                            context,
                            ResearchJobSnapshot(
                                searchId = searchId,
                                query = analyzeQuery,
                                running = false,
                                resultCount = products.size,
                            ),
                        )
                        ResearchAnalyzeStore.showDone(analyzeQuery, products.size, "ideas")
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

    val displayed = remember(products, query, selectedNiches, designTypes, languages, personalizations, selectedAudiences, selectedOpportunity, sort, sortDir, view, watched, searchId, marketplace) {
        if (searchId.isNotBlank() || view == "watched") {
            filterProducts(
                products,
                query,
                selectedNiches,
                designTypes,
                languages,
                personalizations,
                selectedAudiences,
                selectedOpportunity,
                sort,
                sortDir,
                view,
                watched,
                marketplace,
                sessionSearch = searchId.isNotBlank(),
            )
        } else {
            products
        }
    }

    fun persistTrendsFilters() {
        ResearchFilterPrefs.saveTrends(
            context,
            TrendsFilterSnapshot(
                geo = trendsState.geo,
                language = trendsState.language,
                query = trendsState.query,
                selectedTopics = trendsState.selectedTopics,
                productTypes = trendsState.productTypes,
                volume = trendsState.volume,
                time = trendsState.time,
                sort = trendsState.sort,
                sortDir = trendsState.sortDir,
            ),
        )
    }

    fun clearActiveFilters() {
        if (researchTab == "trends") {
            trendsState.selectedTopics = emptySet()
            trendsState.productTypes = emptySet()
            trendsState.volume = emptySet()
            trendsState.time = "avg_12m"
            trendsState.draftQuery = ""
            trendsState.query = ""
            persistTrendsFilters()
        } else {
            selectedNiches = emptySet()
            designTypes = emptySet()
            languages = emptySet()
            personalizations = emptySet()
            selectedAudiences = emptySet()
            selectedOpportunity = emptySet()
            draftQuery = ""
            query = ""
            marketplace = "all"
        }
    }

    fun applyDrawerSearch() {
        if (researchTab == "trends") {
            trendsState.query = trendsState.draftQuery.trim()
            persistTrendsFilters()
        } else {
            query = draftQuery.trim()
        }
        filtersSheetOpen = false
    }

    fun startLiveAnalyze() {
        analyzeModalOpen = false
        filtersSheetOpen = false
        if (researchTab == "trends") {
            trendsState.searching = true
        } else {
            analyzing = true
            products = emptyList()
            searchId = ""
            searchEmptyReason = "running"
            searchAmazonReturned = 0
            view = "opportunities"
            pendingAnalyze = true
        }
    }

    val ideasChips = remember(query, selectedNiches, designTypes, languages, personalizations, selectedAudiences, selectedOpportunity, marketplace, niches) {
        buildList {
            if (query.isNotBlank()) add(ResearchChip("q", query))
            if (marketplace.isNotBlank() && marketplace != "all") add(ResearchChip("market", marketplaceTagFromHost(marketplace)))
            selectedNiches.forEach { key ->
                add(ResearchChip("niche:$key", niches.firstOrNull { it.key == key }?.label ?: key))
            }
            designTypes.forEach { add(ResearchChip("dt:$it", it.replace('_', ' '))) }
            languages.forEach { add(ResearchChip("lang:$it", it)) }
            personalizations.forEach { add(ResearchChip("pers:$it", it)) }
            selectedAudiences.forEach { add(ResearchChip("aud:$it", it)) }
            selectedOpportunity.forEach { add(ResearchChip("opp:$it", it.replace('_', ' '))) }
        }
    }
    val trendsChips = remember(
        trendsState.query,
        trendsState.selectedTopics,
        trendsState.productTypes,
        trendsState.volume,
        trendsState.time,
        trendsState.geo,
    ) {
        buildList {
            if (trendsState.query.isNotBlank()) add(ResearchChip("q", trendsState.query))
            if (trendsState.geo != "ALL") add(ResearchChip("geo", trendsState.geo))
            trendsState.selectedTopics.forEach { add(ResearchChip("topic:$it", it.replace('_', ' '))) }
            trendsState.productTypes.forEach { add(ResearchChip("type:$it", it)) }
            trendsState.volume.forEach { add(ResearchChip("vol:$it", it.replace('_', ' '))) }
            if (trendsState.time != "avg_12m") add(ResearchChip("time", trendsState.time.replace('_', ' ')))
        }
    }

    val filterPanel: @Composable () -> Unit = {
        if (researchTab == "trends") {
            TrendsFilterPanel(translationStore = translationStore, trends = trendsState)
        } else ResearchFilterPanel(
            translationStore = translationStore,
            query = query,
            onQuery = { draftQuery = it },
            analyzeEnabled = tokenStore.isLoggedIn() && !analyzing,
            analyzing = analyzing,
            onAnalyze = { analyzeModalOpen = true },
            sort = sort,
            sortDir = sortDir,
            onSort = { id ->
                if (sort == id) {
                    sortDir = if (sortDir == "asc") "desc" else "asc"
                } else {
                    sort = id
                    sortDir = defaultSortDir(id)
                }
            },
            marketplace = marketplace,
            marketplaces = marketplaces,
            onMarketplace = { marketplace = it },
            analyzeMarketplace = analyzeMarketplace,
            onAnalyzeMarketplace = { analyzeMarketplace = it },
            analyzeLanguage = analyzeLanguage,
            onAnalyzeLanguage = { analyzeLanguage = it },
            analyzeResolvedMarketplace = analyzeResolvedMarketplace,
            analyzeResolvedLanguage = analyzeResolvedLanguage,
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
            selectedOpportunity = selectedOpportunity,
            onToggleOpportunity = { key ->
                selectedOpportunity = if (key in selectedOpportunity) selectedOpportunity - key else selectedOpportunity + key
            },
            facets = facets,
            analyzeLimits = analyzeLimits,
        )
    }

    val hosts = marketplaces.ifEmpty {
        listOf("amazon.de", "amazon.co.uk", "amazon.fr", "amazon.it", "amazon.es", "amazon.com", "amazon.ca")
            .map { ResearchMarketplace(it, marketplaceTagFromHost(it)) }
    }
    val countryOptions = listOf("all" to tr("creator.research.country_all", "All countries")) +
        hosts.map { it.host to countryLabel(it.host, translationStore) }
    val langOptions = listOf("all" to tr("creator.research.analyze_all", "All")) + listOf(
        "en" to tr("creator.research.lang_en", "English"),
        "de" to tr("creator.research.lang_de", "German"),
        "es" to tr("creator.research.lang_es", "Spanish"),
        "fr" to tr("creator.research.lang_fr", "French"),
        "it" to tr("creator.research.lang_it", "Italian"),
        "none" to tr("creator.quick_inspirations.language_none", "None"),
    )
    val analyzeLangOptions = langOptions.filter { it.first != "none" }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .heightIn(max = cap),
    ) {
        Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ResearchPageHeader(
                selected = researchTab,
                translationStore = translationStore,
                onSelect = { researchTab = it },
                onOpenFilters = { filtersSheetOpen = true },
            )
            ResearchActiveChips(
                chips = if (researchTab == "trends") trendsChips else ideasChips,
                onRemove = { id ->
                    when {
                        id == "q" && researchTab == "trends" -> {
                            trendsState.query = ""
                            trendsState.draftQuery = ""
                        }
                        id == "q" -> {
                            query = ""
                            draftQuery = ""
                        }
                        id == "market" -> marketplace = "all"
                        id == "geo" -> trendsState.geo = "ALL"
                        id == "time" -> trendsState.time = "avg_12m"
                        id.startsWith("niche:") -> selectedNiches = selectedNiches - id.removePrefix("niche:")
                        id.startsWith("dt:") -> designTypes = designTypes - id.removePrefix("dt:")
                        id.startsWith("lang:") -> languages = languages - id.removePrefix("lang:")
                        id.startsWith("pers:") -> personalizations = personalizations - id.removePrefix("pers:")
                        id.startsWith("aud:") -> selectedAudiences = selectedAudiences - id.removePrefix("aud:")
                        id.startsWith("opp:") -> selectedOpportunity = selectedOpportunity - id.removePrefix("opp:")
                        id.startsWith("topic:") -> trendsState.selectedTopics = trendsState.selectedTopics - id.removePrefix("topic:")
                        id.startsWith("type:") -> trendsState.productTypes = trendsState.productTypes - id.removePrefix("type:")
                        id.startsWith("vol:") -> trendsState.volume = trendsState.volume - id.removePrefix("vol:")
                    }
                },
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (researchTab == "trends") {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        EazyResearchTrendsPane(
                            api = api,
                            translationStore = translationStore,
                            trends = trendsState,
                            modifier = Modifier.fillMaxSize(),
                        )
                        ResearchLockOverlay(translationStore, trendsState.searching)
                    }
                } else when {
                    error != null && products.isEmpty() && !analyzing -> Text(error ?: "", color = TextDim, fontSize = 14.sp)
                    else -> Box(Modifier.weight(1f).fillMaxWidth()) {
                        val placeholderCount = when {
                            analyzing -> (RESEARCH_ANALYZE_SLOT_CAP - displayed.size).coerceAtLeast(0)
                            loading && displayed.isEmpty() -> 8
                            else -> 0
                        }
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(160.dp),
                            state = gridState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 8.dp),
                        ) {
                            items(displayed, key = { watchId(it) }) { product ->
                                OpportunityCard(
                                    product = product,
                                    nicheLabels = displayTopicLabels(product, niches),
                                    watched = isWatched(watched, product),
                                    justAdded = watchId(product) in justAddedIds,
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
                                    onOpenAmazon = {
                                        try {
                                            context.startActivity(
                                                android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse(amazonListingUrl(product.asin, product.marketplace)),
                                                )
                                            )
                                        } catch (_: Exception) {}
                                    },
                                    onSendToGenerator = {
                                        onSendToGenerator(product.toGeneratorHandoff())
                                    },
                                )
                            }
                            items(placeholderCount, key = { "ph-$it" }) {
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(0.72f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White.copy(alpha = 0.08f)),
                                )
                            }
                        }
                        if (!analyzing && !loading && displayed.isEmpty()) {
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
                        ResearchLockOverlay(translationStore, analyzing)
                    }
                }
                selected?.let { product ->
                    Dialog(
                        onDismissRequest = { selected = null },
                        properties = DialogProperties(
                            usePlatformDefaultWidth = false,
                            decorFitsSystemWindows = false,
                        ),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xC7080618))
                                .clickable { selected = null },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                    ) { },
                            ) {
                                ProductDetailCard(
                                    product = product,
                                    niches = niches,
                                    translationStore = translationStore,
                                    onClose = { selected = null },
                                    onOpenAmazon = {
                                        try {
                                            context.startActivity(
                                                android.content.Intent(
                                                    android.content.Intent.ACTION_VIEW,
                                                    android.net.Uri.parse(amazonListingUrl(product.asin, product.marketplace)),
                                                )
                                            )
                                        } catch (_: Exception) {}
                                    },
                                    onSendToGenerator = {
                                        selected = null
                                        onSendToGenerator(product.toGeneratorHandoff())
                                    },
                                )
                            }
                        }
                    }
                }
                Text(status, color = TextDim, fontSize = 11.sp)
            }
        }
        ResearchRightDrawer(open = filtersSheetOpen, onDismiss = { filtersSheetOpen = false }) {
            val headLang = if (researchTab == "trends") trendsState.language else languages.firstOrNull() ?: "all"
            val headCountry = if (researchTab == "trends") trendsState.geo else marketplace
            val headCountryLabel = if (researchTab == "trends") {
                headCountry
            } else {
                countryOptions.firstOrNull { it.first == marketplace }?.second ?: countryOptions.first().second
            }
            val selectedCount = if (researchTab == "trends") trendsChips.size else ideasChips.size
            Column(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        CenterChoiceField(
                            label = tr("creator.research.country", "Country"),
                            value = if (researchTab == "trends") {
                                if (trendsState.geo == "ALL") tr("creator.research.country_all", "All countries") else trendsState.geo
                            } else headCountryLabel,
                            flagCode = if (researchTab == "trends") {
                                if (trendsState.geo == "ALL") null else trendsState.geo
                            } else flagCodeForMarketplace(marketplace),
                            options = if (researchTab == "trends") {
                                listOf(
                                    Triple("ALL", tr("creator.research.country_all", "All countries"), null),
                                    Triple("DE", tr("creator.research.geo_DE", "Germany"), "DE"),
                                    Triple("US", tr("creator.research.geo_US", "United States"), "US"),
                                    Triple("GB", tr("creator.research.geo_GB", "United Kingdom"), "GB"),
                                    Triple("FR", tr("creator.research.geo_FR", "France"), "FR"),
                                    Triple("IT", tr("creator.research.geo_IT", "Italy"), "IT"),
                                    Triple("ES", tr("creator.research.geo_ES", "Spain"), "ES"),
                                    Triple("CA", tr("creator.research.geo_CA", "Canada"), "CA"),
                                    Triple("AU", tr("creator.research.geo_AU", "Australia"), "AU"),
                                )
                            } else countryOptions.map { (id, label) -> Triple(id, label, flagCodeForMarketplace(id)) },
                            modifier = Modifier.weight(1f),
                            onPick = { id ->
                                if (researchTab == "trends") trendsState.geo = id else marketplace = id
                            },
                        )
                        CenterChoiceField(
                            label = tr("creator.research.language", "Language"),
                            value = langOptions.firstOrNull { it.first == headLang }?.second ?: langOptions.first().second,
                            flagCode = flagCodeForLanguage(headLang),
                            options = langOptions.map { (id, label) -> Triple(id, label, flagCodeForLanguage(id)) },
                            modifier = Modifier.weight(1f),
                            onPick = { id ->
                                if (researchTab == "trends") {
                                    trendsState.language = id
                                } else {
                                    languages = if (id == "all") emptySet() else setOf(id)
                                }
                            },
                        )
                    }
                    OutlinedTextField(
                        value = if (researchTab == "trends") trendsState.draftQuery else draftQuery,
                        onValueChange = {
                            if (researchTab == "trends") trendsState.draftQuery = it else draftQuery = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = {
                            Text(
                                if (researchTab == "trends") {
                                    tr("creator.research.trends_search_placeholder", "Search a topic or keyword")
                                } else {
                                    tr("creator.research.search_placeholder", "Search reprint-safe products")
                                },
                                color = TextDim,
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextMain,
                            unfocusedTextColor = TextMain,
                            focusedBorderColor = Color(0xFFF97316),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        ),
                    )
                    if (selectedCount > 0) {
                        Text(
                            tr("creator.research.filter_selected", "{count} selected")
                                .replace("{count}", selectedCount.toString()),
                            color = TextDim,
                            fontSize = 12.sp,
                        )
                    }
                    Text(
                        tr("creator.research.filter_clear", "Clear all filters"),
                        color = TextMain,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .clickable { clearActiveFilters() }
                            .padding(vertical = 8.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                ) {
                    filterPanel()
                }
                ResearchDrawerFooter(
                    translationStore = translationStore,
                    onApply = { applyDrawerSearch() },
                    onAnalyze = { analyzeModalOpen = true },
                    analyzeEnabled = tokenStore.isLoggedIn() && !(if (researchTab == "trends") trendsState.searching else analyzing),
                    remaining = if (researchTab == "trends") trendsState.remaining else analyzeLimits.remaining,
                    limit = if (researchTab == "trends") trendsState.limit else analyzeLimits.limit,
                    analyzing = if (researchTab == "trends") trendsState.searching else analyzing,
                )
            }
        }
        if (analyzeModalOpen) {
            val modalCountry = if (researchTab == "trends") trendsState.searchGeo else analyzeMarketplace
            val modalLang = if (researchTab == "trends") trendsState.searchLang else analyzeLanguage
            ResearchAnalyzeModal(
                translationStore = translationStore,
                query = if (researchTab == "trends") trendsState.analyzeQuery else analyzeQuery,
                onQuery = {
                    if (researchTab == "trends") trendsState.analyzeQuery = it else analyzeQuery = it
                },
                countryLabel = if (researchTab == "trends") {
                    if (modalCountry == "ALL") tr("creator.research.country_all", "All countries") else modalCountry
                } else {
                    countryOptions.firstOrNull { it.first == analyzeMarketplace }?.second
                        ?: tr("creator.research.analyze_all", "All")
                },
                countryFlag = if (researchTab == "trends") {
                    if (modalCountry == "ALL") null else modalCountry
                } else flagCodeForMarketplace(analyzeMarketplace),
                countryOptions = if (researchTab == "trends") {
                    listOf(
                        Triple("ALL", tr("creator.research.country_all", "All countries"), null),
                        Triple("DE", tr("creator.research.geo_DE", "Germany"), "DE"),
                        Triple("US", tr("creator.research.geo_US", "United States"), "US"),
                        Triple("GB", tr("creator.research.geo_GB", "United Kingdom"), "GB"),
                        Triple("FR", tr("creator.research.geo_FR", "France"), "FR"),
                        Triple("IT", tr("creator.research.geo_IT", "Italy"), "IT"),
                        Triple("ES", tr("creator.research.geo_ES", "Spain"), "ES"),
                        Triple("CA", tr("creator.research.geo_CA", "Canada"), "CA"),
                        Triple("AU", tr("creator.research.geo_AU", "Australia"), "AU"),
                    )
                } else countryOptions.map { (id, label) -> Triple(id, label, flagCodeForMarketplace(id)) },
                onCountry = {
                    if (researchTab == "trends") trendsState.searchGeo = it else analyzeMarketplace = it
                },
                languageLabel = analyzeLangOptions.firstOrNull { it.first == modalLang }?.second
                    ?: tr("creator.research.analyze_all", "All"),
                languageFlag = flagCodeForLanguage(modalLang),
                languageOptions = analyzeLangOptions.map { (id, label) -> Triple(id, label, flagCodeForLanguage(id)) },
                onLanguage = {
                    if (researchTab == "trends") trendsState.searchLang = it else analyzeLanguage = it
                },
                remaining = if (researchTab == "trends") trendsState.remaining else analyzeLimits.remaining,
                limit = if (researchTab == "trends") trendsState.limit else analyzeLimits.limit,
                analyzeEnabled = tokenStore.isLoggedIn() &&
                    (if (researchTab == "trends") trendsState.analyzeQuery.isNotBlank() else analyzeQuery.isNotBlank()),
                onCancel = { analyzeModalOpen = false },
                onAnalyze = { startLiveAnalyze() },
                searchPlaceholder = if (researchTab == "trends") {
                    tr("creator.research.trends_search_placeholder", "Search a topic or keyword")
                } else {
                    tr("creator.research.search_placeholder", "Search reprint-safe products")
                },
            )
        }
        ideasToast?.let { toast ->
            ResearchDoneToast(
                translationStore = translationStore,
                query = toast.query,
                count = toast.count,
                showGoToResearch = false,
                onGoToResearch = {},
                onDismiss = { ResearchAnalyzeStore.clearToast() },
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResearchFilterPanel(
    translationStore: TranslationStore,
    query: String,
    onQuery: (String) -> Unit,
    analyzeEnabled: Boolean,
    analyzing: Boolean,
    onAnalyze: () -> Unit,
    sort: String,
    sortDir: String,
    onSort: (String) -> Unit,
    marketplace: String,
    marketplaces: List<ResearchMarketplace>,
    onMarketplace: (String) -> Unit,
    analyzeMarketplace: String,
    onAnalyzeMarketplace: (String) -> Unit,
    analyzeLanguage: String,
    onAnalyzeLanguage: (String) -> Unit,
    analyzeResolvedMarketplace: String,
    analyzeResolvedLanguage: String,
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
    selectedOpportunity: Set<String>,
    onToggleOpportunity: (String) -> Unit,
    facets: Map<String, List<ResearchFacet>>,
    analyzeLimits: AnalyzeLimits,
) {
    fun tr(key: String, fallback: String) = translationStore.t(key, fallback)
    fun countOf(group: String, key: String): Int =
        facets[group]?.firstOrNull { it.key == key }?.count ?: 0
    val context = LocalContext.current
    val panelScroll = rememberScrollState()
    var folds by remember { mutableStateOf(loadFilterFolds(context)) }
    fun foldOpen(id: String) = folds[id] ?: FILTER_FOLD_DEFAULTS[id] ?: false
    fun toggleFold(id: String) {
        val next = folds.toMutableMap()
        next[id] = !foldOpen(id)
        folds = next
        saveFilterFolds(context, next)
    }
    val hosts = marketplaces.ifEmpty {
        listOf("amazon.de", "amazon.co.uk", "amazon.fr", "amazon.it", "amazon.es", "amazon.com", "amazon.ca")
            .map { ResearchMarketplace(it, marketplaceTagFromHost(it)) }
    }
    val countryOptions = listOf("all" to tr("creator.research.country_all", "All countries")) +
        hosts.map { it.host to countryLabel(it.host, translationStore) }
    val platformOptions = listOf("all" to tr("creator.research.analyze_all", "All")) +
        hosts.map { it.host to countryLabel(it.host, translationStore) }
    val analyzeLangOptions = listOf("all" to tr("creator.research.analyze_all", "All")) + listOf(
        "en" to tr("creator.research.lang_en", "English"),
        "de" to tr("creator.research.lang_de", "German"),
        "es" to tr("creator.research.lang_es", "Spanish"),
        "fr" to tr("creator.research.lang_fr", "French"),
        "it" to tr("creator.research.lang_it", "Italian"),
    )
    val sorts = listOf(
        "review_growth" to tr("creator.research.sort_review_growth", "Review growth"),
        "reviews" to tr("creator.research.sort_reviews", "Reviews"),
        "bsr" to tr("creator.research.sort_bsr", "BSR"),
        "newest" to tr("creator.research.sort_newest", "Newest snapshot"),
    )
    var sortOpen by remember { mutableStateOf(false) }
    val countryLabelText = countryOptions.firstOrNull { it.first == marketplace }?.second ?: countryOptions.first().second
    val platformLabelText = platformOptions.firstOrNull { it.first == analyzeMarketplace }?.second ?: platformOptions.first().second
    val analyzeLangLabel = analyzeLangOptions.firstOrNull { it.first == analyzeLanguage }?.second ?: analyzeLangOptions.first().second
    val sortLabel = sortDisplayLabel(sort, sortDir, translationStore)
    Column(modifier = Modifier.fillMaxHeight()) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(panelScroll),
    ) {
    CenterChoiceField(
        label = tr("creator.research.country", "Country"),
        value = countryLabelText,
        flagCode = flagCodeForMarketplace(marketplace),
        options = countryOptions.map { (id, label) -> Triple(id, label, flagCodeForMarketplace(id)) },
        onPick = onMarketplace,
    )
    ExposedDropdownMenuBox(
        expanded = sortOpen,
        onExpandedChange = { sortOpen = it },
        modifier = Modifier.padding(top = 8.dp),
    ) {
        OutlinedTextField(
            value = sortLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(tr("creator.research.sort_label", "Sort"), color = TextDim) },
            leadingIcon = {
                Icon(Icons.Filled.Sort, contentDescription = null, tint = TextDim)
            },
            trailingIcon = {
                Icon(
                    imageVector = if (sortDir == "asc") Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                    contentDescription = sortLabel,
                    tint = TextDim,
                )
            },
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
        ExposedDropdownMenu(expanded = sortOpen, onDismissRequest = { sortOpen = false }) {
            sorts.forEach { (id, label) ->
                val selected = sort == id
                val rowDir = if (selected) sortDir else defaultSortDir(id)
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(label, color = if (selected) HeartOn else TextMain)
                            Icon(
                                imageVector = if (rowDir == "asc") Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                                contentDescription = null,
                                tint = if (selected) HeartOn else TextDim,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    onClick = {
                        onSort(id)
                        sortOpen = false
                    },
                )
            }
        }
    }
    FilterFold(
        title = tr("creator.research.topics", "Topics"),
        open = foldOpen("topics"),
        selectedCount = selectedNiches.size,
        translationStore = translationStore,
        onToggle = { toggleFold("topics") },
    ) {
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
    }
    FilterFold(
        title = tr("creator.research.audience", "Audience"),
        open = foldOpen("audience"),
        selectedCount = selectedAudiences.size,
        translationStore = translationStore,
        onToggle = { toggleFold("audience") },
    ) {
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
    }
    FilterFold(
        title = tr("creator.research.custom_design", "Custom Design"),
        open = foldOpen("custom_design"),
        selectedCount = personalizations.size,
        translationStore = translationStore,
        onToggle = { toggleFold("custom_design") },
    ) {
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
    }
    FilterFold(
        title = tr("creator.research.design_type", "Design type"),
        open = foldOpen("design_type"),
        selectedCount = designTypes.size,
        translationStore = translationStore,
        onToggle = { toggleFold("design_type") },
    ) {
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
    }
    FilterFold(
        title = tr("creator.research.language", "Language"),
        open = foldOpen("language"),
        selectedCount = languages.size,
        translationStore = translationStore,
        onToggle = { toggleFold("language") },
    ) {
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
    FilterFold(
        title = tr("creator.research.opportunity", "Opportunity"),
        open = foldOpen("opportunity"),
        selectedCount = selectedOpportunity.size,
        translationStore = translationStore,
        onToggle = { toggleFold("opportunity") },
    ) {
        if (selectedOpportunity.isEmpty()) {
            Text(
                tr("creator.research.opportunity_all_hint", "All opportunity ratings. Empty when Google has no data — never invented."),
                color = TextDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        listOf(
            "very_high" to tr("creator.research.opportunity_very_high", "Very high"),
            "high" to tr("creator.research.opportunity_high", "High"),
            "medium" to tr("creator.research.opportunity_medium", "Medium"),
            "low" to tr("creator.research.opportunity_low", "Low"),
            "very_low" to tr("creator.research.opportunity_very_low", "Very low"),
        ).forEach { (id, label) ->
            FilterCheckRow(label = label, count = countOf("opportunity", id), checked = id in selectedOpportunity, onToggle = { onToggleOpportunity(id) })
        }
    }
    }
    }
}

@Composable
private fun ResearchAnalyzeCluster(
    translationStore: TranslationStore,
    query: String,
    onQuery: (String) -> Unit,
    analyzeEnabled: Boolean,
    analyzing: Boolean,
    onAnalyze: () -> Unit,
    marketplaces: List<ResearchMarketplace>,
    analyzeMarketplace: String,
    onAnalyzeMarketplace: (String) -> Unit,
    analyzeLanguage: String,
    onAnalyzeLanguage: (String) -> Unit,
    analyzeResolvedMarketplace: String,
    analyzeResolvedLanguage: String,
    analyzeLimits: AnalyzeLimits,
    modifier: Modifier = Modifier,
) {
    fun tr(key: String, fallback: String) = translationStore.t(key, fallback)
    val hosts = marketplaces.ifEmpty {
        listOf("amazon.de", "amazon.co.uk", "amazon.fr", "amazon.it", "amazon.es", "amazon.com", "amazon.ca")
            .map { ResearchMarketplace(it, marketplaceTagFromHost(it)) }
    }
    val platformOptions = listOf("all" to tr("creator.research.analyze_all", "All")) +
        hosts.map { it.host to countryLabel(it.host, translationStore) }
    val analyzeLangOptions = listOf("all" to tr("creator.research.analyze_all", "All")) + listOf(
        "en" to tr("creator.research.lang_en", "English"),
        "de" to tr("creator.research.lang_de", "German"),
        "es" to tr("creator.research.lang_es", "Spanish"),
        "fr" to tr("creator.research.lang_fr", "French"),
        "it" to tr("creator.research.lang_it", "Italian"),
    )
    val platformLabelText = platformOptions.firstOrNull { it.first == analyzeMarketplace }?.second ?: platformOptions.first().second
    val analyzeLangLabel = analyzeLangOptions.firstOrNull { it.first == analyzeLanguage }?.second ?: analyzeLangOptions.first().second
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CenterChoiceField(
                label = tr("creator.research.platform", "Platform"),
                value = platformLabelText,
                flagCode = flagCodeForMarketplace(analyzeMarketplace),
                options = platformOptions.map { (id, label) -> Triple(id, label, flagCodeForMarketplace(id)) },
                modifier = Modifier.weight(1f),
                onPick = onAnalyzeMarketplace,
            )
            CenterChoiceField(
                label = tr("creator.research.language", "Language"),
                value = analyzeLangLabel,
                flagCode = flagCodeForLanguage(analyzeLanguage),
                options = analyzeLangOptions.map { (id, label) -> Triple(id, label, flagCodeForLanguage(id)) },
                modifier = Modifier.weight(1f),
                onPick = onAnalyzeLanguage,
            )
        }
        if (analyzeResolvedMarketplace.isNotBlank() || analyzeResolvedLanguage.isNotBlank()) {
            val usedPlatform = if (analyzeResolvedMarketplace.isNotBlank() && analyzeResolvedMarketplace != "all") {
                countryLabel(analyzeResolvedMarketplace, translationStore)
            } else {
                tr("creator.research.analyze_all", "All")
            }
            val usedLang = analyzeLangOptions.firstOrNull { it.first == analyzeResolvedLanguage }?.second
                ?: tr("creator.research.analyze_all", "All")
            Text(
                tr("creator.research.analyze_used", "{platform} · {language}")
                    .replace("{platform}", usedPlatform)
                    .replace("{language}", usedLang),
                color = TextDim,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
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
                    focusedBorderColor = Color(0xFFF97316),
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
    }
}

@Composable
internal fun FilterFold(
    title: String,
    open: Boolean,
    selectedCount: Int,
    translationStore: TranslationStore,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    val hint = if (!open && selectedCount > 0) {
        translationStore.t("creator.research.filter_selected", "{count} selected")
            .replace("{count}", selectedCount.toString())
    } else {
        ""
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(top = 10.dp, bottom = 4.dp)
            .semantics {
                role = Role.Button
                stateDescription = if (open) "Expanded" else "Collapsed"
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            color = TextDim,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            modifier = Modifier.weight(1f),
        )
        if (hint.isNotEmpty()) {
            Text(hint, color = TextDim, fontSize = 11.sp, maxLines = 1)
        }
        Icon(
            imageVector = if (open) Icons.Default.ExpandMore else Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = TextDim,
            modifier = Modifier.size(18.dp),
        )
    }
    if (open) content()
}

@Composable
internal fun FilterCheckRow(
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
private fun OpportunityCard(
    product: ResearchProduct,
    nicheLabels: List<String>,
    watched: Boolean,
    justAdded: Boolean = false,
    translationStore: TranslationStore,
    onOpen: () -> Unit,
    onToggleWatch: () -> Unit,
    onOpenAmazon: () -> Unit,
    onSendToGenerator: () -> Unit,
) {
    fun tr(key: String, fallback: String) = translationStore.t(key, fallback)
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .clickable(onClick = onOpen),
    ) {
        Box {
            val thumb = product.imageThumbUrl.ifBlank { product.imageUrl }
            if (thumb.isNotBlank()) {
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(thumb)
                        .size(Size(400, 400))
                        .crossfade(true)
                        .build(),
                    contentDescription = product.title,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color(0xFF1B1430)))
            }
            if (justAdded) {
                Text(
                    tr("creator.research.just_added", "Just added"),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF97316))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
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
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
            ) {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xB80A0618)),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = tr("creator.research.menu", "Product actions"),
                        tint = TextMain,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(tr("creator.research.open_source", "Open on Amazon")) },
                        onClick = {
                            menuOpen = false
                            onOpenAmazon()
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (watched) tr("creator.research.watch_remove", "Remove from watchlist")
                                else tr("creator.research.watch", "Watch")
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onToggleWatch()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(tr("creator.research.send_generator", "Send to Generator")) },
                        onClick = {
                            menuOpen = false
                            onSendToGenerator()
                        },
                    )
                }
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
    onOpenAmazon: () -> Unit = {},
    onSendToGenerator: () -> Unit = {},
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
        TextButton(onClick = onOpenAmazon) {
            Text(tr("creator.research.open_source", "Open on Amazon"), color = Accent)
        }
        TextButton(onClick = onSendToGenerator) {
            Text(tr("creator.research.send_generator", "Send to Generator"), color = Accent)
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
        product.opportunityBucket?.takeIf { it.isNotBlank() }?.let { bucket ->
            val label = when (bucket) {
                "very_low" -> tr("creator.research.opportunity_very_low", "Very low")
                "low" -> tr("creator.research.opportunity_low", "Low")
                "medium" -> tr("creator.research.opportunity_medium", "Medium")
                "high" -> tr("creator.research.opportunity_high", "High")
                "very_high" -> tr("creator.research.opportunity_very_high", "Very high")
                else -> bucket
            }
            Text(
                tr("creator.research.opportunity", "Opportunity") + " · " + label,
                color = Color(0xFFFFD7B0),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
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
    opportunity: Set<String>,
    sort: String,
    sortDir: String,
    view: String,
    watched: Set<String>,
    marketplace: String,
    sessionSearch: Boolean = false,
): List<ResearchProduct> {
    var rows = products.filter { it.reprintOk }
    if (marketplace.isNotBlank() && marketplace != "all") {
        rows = rows.filter { it.marketplace.equals(marketplace, ignoreCase = true) }
    }
    if (niches.isNotEmpty()) rows = rows.filter { topicKeyOf(it) in niches }
    if (designTypes.isNotEmpty()) {
        rows = rows.filter { (it.designType ?: "").lowercase(Locale.ROOT) in designTypes }
    }
    if (languages.isNotEmpty()) {
        rows = rows.filter { (it.language ?: "").lowercase(Locale.ROOT) in languages }
    }
    if (personalizations.size == 1) {
        rows = rows.filter { product ->
            val key = if (product.personalizable) "personalizable" else "standard"
            key in personalizations
        }
    }
    if (audiences.isNotEmpty()) {
        rows = rows.filter { it.audience in audiences }
    }
    if (opportunity.isNotEmpty()) {
        rows = rows.filter { (it.opportunityBucket ?: "") in opportunity }
    }
    val q = query.trim().lowercase(Locale.ROOT)
    if (q.isNotEmpty()) {
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
    if (sessionSearch) {
        return rows.sortedByDescending { it.searchIngestedAt ?: it.capturedAt ?: 0L }
    }
    val base = when (sort) {
        "reviews" -> rows.sortedBy { it.reviews ?: 0 }
        "bsr" -> rows.sortedBy { it.bsr ?: Int.MAX_VALUE }
        "newest" -> rows.sortedBy { it.capturedAt ?: 0L }
        else -> rows.sortedBy { it.reviewDelta ?: 0 }
    }
    return if (resolveSortDir(sort, sortDir) == "asc") base else base.reversed()
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
            imageThumbUrl = p.optString("image_thumb_url").ifBlank { p.optString("image_url") },
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
            searchIngestedAt = p.optLongOrNull("search_ingested_at") ?: p.optLongOrNull("ingested_at"),
            trend = p.optString("trend"),
            risingScore = p.optInt("rising_score", 0),
            opportunityBucket = p.optString("opportunity_bucket").ifBlank { null },
        )
    }
    return out
}

private fun parseFacets(obj: JSONObject?): Map<String, List<ResearchFacet>> {
    if (obj == null) return emptyMap()
    val out = mutableMapOf<String, List<ResearchFacet>>()
    listOf("topics", "audience", "personalization", "design_type", "language", "opportunity").forEach { group ->
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

private fun defaultSortDir(sort: String): String = if (sort == "bsr") "asc" else "desc"

private fun resolveSortDir(sort: String, dir: String): String {
    val raw = dir.trim().lowercase(Locale.ROOT)
    return if (raw == "asc" || raw == "desc") raw else defaultSortDir(sort)
}

private fun sortDisplayLabel(sort: String, dir: String, translationStore: TranslationStore): String {
    val d = resolveSortDir(sort, dir)
    return when (sort) {
        "newest" -> if (d == "asc") {
            translationStore.t("creator.research.sort_oldest_first", "Oldest first")
        } else {
            translationStore.t("creator.research.sort_newest_first", "Newest first")
        }
        "reviews" -> if (d == "asc") {
            translationStore.t("creator.research.sort_reviews_low", "Fewest reviews")
        } else {
            translationStore.t("creator.research.sort_reviews_high", "Most reviews")
        }
        "bsr" -> if (d == "asc") {
            translationStore.t("creator.research.sort_bsr_best", "Best BSR")
        } else {
            translationStore.t("creator.research.sort_bsr_worst", "Highest BSR")
        }
        else -> if (d == "asc") {
            translationStore.t("creator.research.sort_growth_low", "Lowest growth")
        } else {
            translationStore.t("creator.research.sort_growth_high", "Highest growth")
        }
    }
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

private fun ResearchProduct.toGeneratorHandoff() = ResearchGeneratorHandoff(
    imageUrl = imageUrl,
    prompt = prompt,
    topic = topic,
    subtopic = subNiche,
    tags = tags,
    designType = designType,
    language = language,
    asin = asin,
    marketplace = marketplace,
)

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

internal fun loadFilterFolds(context: Context): Map<String, Boolean> {
    val out = FILTER_FOLD_DEFAULTS.toMutableMap()
    val raw = context.getSharedPreferences(WATCH_PREFS, Context.MODE_PRIVATE)
        .getString(FILTER_FOLDS_KEY, null) ?: return out
    return try {
        val obj = JSONObject(raw)
        FILTER_FOLD_DEFAULTS.keys.forEach { key ->
            if (obj.has(key)) out[key] = obj.optBoolean(key, FILTER_FOLD_DEFAULTS.getValue(key))
        }
        out
    } catch (_: Exception) {
        out
    }
}

internal fun saveFilterFolds(context: Context, folds: Map<String, Boolean>) {
    val obj = JSONObject()
    FILTER_FOLD_DEFAULTS.keys.forEach { key ->
        obj.put(key, folds[key] ?: FILTER_FOLD_DEFAULTS.getValue(key))
    }
    context.getSharedPreferences(WATCH_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(FILTER_FOLDS_KEY, obj.toString())
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
