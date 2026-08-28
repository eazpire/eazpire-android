package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.components.GlassCircularFlag
import kotlinx.coroutines.delay
import org.json.JSONObject

private val TextMain = Color(0xFFF4F1FF)
private val TextDim = Color(0xC7F4F1FF)
private val Accent = Color(0xFF7C5CFF)
private val AccentOrange = Color(0xFFF97316)
private val PanelBg = Color(0xFF120C22)
private val HeadBg = Color(0xE610122A)

internal data class TrendKeyword(
    val keyword: String,
    val topicKey: String,
    val volumeBucket: String,
    val trend: String,
    val competition: String,
    val productType: String,
)

internal data class TrendTopic(
    val key: String,
    val label: String,
    val volumeBucket: String,
)

internal class TrendsUiState {
    var geo by mutableStateOf("ALL")
    var language by mutableStateOf("all")
    var searchGeo by mutableStateOf("ALL")
    var searchLang by mutableStateOf("all")
    var query by mutableStateOf("")
    var draftQuery by mutableStateOf("")
    var analyzeQuery by mutableStateOf("")
    var keywords by mutableStateOf(listOf<TrendKeyword>())
    var topics by mutableStateOf(listOf<TrendTopic>())
    var selectedTopics by mutableStateOf(setOf<String>())
    var productTypes by mutableStateOf(setOf<String>())
    var volume by mutableStateOf(setOf<String>())
    var time by mutableStateOf("avg_12m")
    var sort by mutableStateOf("volume")
    var sortDir by mutableStateOf("desc")
    var loading by mutableStateOf(true)
    var configured by mutableStateOf(true)
    var remaining by mutableStateOf(5)
    var limit by mutableStateOf(5)
    var searching by mutableStateOf(false)
    var searchId by mutableStateOf("")
    var justAdded by mutableStateOf(setOf<String>())
}

private fun Modifier.orangeVerticalScrollbar(scroll: androidx.compose.foundation.ScrollState): Modifier =
    drawWithContent {
        drawContent()
        val max = scroll.maxValue
        if (max <= 0) return@drawWithContent
        val view = size.height
        val content = view + max
        val thumbH = (view * view / content).coerceAtLeast(24.dp.toPx())
        val travel = (view - thumbH).coerceAtLeast(0f)
        val y = (scroll.value.toFloat() / max) * travel
        val barW = 4.dp.toPx()
        drawRoundRect(
            color = AccentOrange,
            topLeft = Offset(size.width - barW - 1.dp.toPx(), y),
            size = Size(barW, thumbH),
            cornerRadius = CornerRadius(barW / 2f, barW / 2f),
        )
    }

@Composable
internal fun ResearchPageHeader(
    selected: String,
    translationStore: TranslationStore,
    onSelect: (String) -> Unit,
    onOpenFilters: () -> Unit,
) {
    fun tr(key: String, fallback: String) = translationStore.t(key, fallback)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HeadBg)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f),
        ) {
            listOf(
                "ideas" to tr("creator.research.tab_design_ideas", "Design Ideas"),
                "trends" to tr("creator.research.tab_trends", "Trends"),
            ).forEach { (id, label) ->
                val on = selected == id
                Text(
                    label,
                    color = TextMain,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (on) AccentOrange.copy(alpha = 0.32f) else Color(0xFF10122A))
                        .clickable { onSelect(id) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
        ResearchFunnelButton(translationStore = translationStore, onClick = onOpenFilters)
    }
}

@Composable
internal fun CenterChoiceField(
    label: String,
    value: String,
    flagCode: String?,
    options: List<Triple<String, String, String?>>,
    modifier: Modifier = Modifier,
    onPick: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val listScroll = rememberScrollState()
    Box(modifier.clickable { open = true }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(label, color = TextDim, maxLines = 1) },
            leadingIcon = {
                flagCode?.let { GlassCircularFlag(countryCode = it, size = 18.dp) }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = TextMain,
                disabledBorderColor = Color.White.copy(alpha = 0.2f),
                disabledLabelColor = TextDim,
                focusedTextColor = TextMain,
                unfocusedTextColor = TextMain,
            ),
        )
    }
    if (open) {
        Dialog(
            onDismissRequest = { open = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xC7080618))
                    .clickable { open = false },
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(PanelBg)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { }
                        .padding(16.dp)
                        .heightIn(max = 480.dp)
                        .orangeVerticalScrollbar(listScroll)
                        .verticalScroll(listScroll),
                ) {
                    Text(label, color = TextMain, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
                    options.forEach { (id, optLabel, flag) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable {
                                    onPick(id)
                                    open = false
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            flag?.let { GlassCircularFlag(countryCode = it, size = 20.dp) }
                            Text(optLabel, color = TextMain, fontSize = 15.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TrendsSearchCluster(
    trends: TrendsUiState,
    translationStore: TranslationStore,
    loggedIn: Boolean,
    modifier: Modifier = Modifier,
) {
    fun tr(key: String, fallback: String) = translationStore.t(key, fallback)
    val geoOptions = trendGeoOptions(translationStore)
    val langOptions = trendLangOptions(translationStore)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CenterChoiceField(
                label = tr("creator.research.country", "Country"),
                value = geoOptions.firstOrNull { it.first == trends.searchGeo }?.second ?: trends.searchGeo,
                flagCode = geoOptions.firstOrNull { it.first == trends.searchGeo }?.third,
                options = geoOptions,
                modifier = Modifier.weight(1f),
                onPick = { trends.searchGeo = it },
            )
            CenterChoiceField(
                label = tr("creator.research.language", "Language"),
                value = langOptions.firstOrNull { it.first == trends.searchLang }?.second ?: trends.searchLang,
                flagCode = langOptions.firstOrNull { it.first == trends.searchLang }?.third,
                options = langOptions,
                modifier = Modifier.weight(1f),
                onPick = { trends.searchLang = it },
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = trends.query,
                onValueChange = { trends.query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = {
                    Text(tr("creator.research.trends_search_placeholder", "Search a topic or keyword"), color = TextDim)
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextMain,
                    unfocusedTextColor = TextMain,
                    focusedBorderColor = AccentOrange,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                ),
            )
            val enabled = loggedIn && !trends.searching && trends.query.isNotBlank()
            Text(
                if (trends.searching) {
                    tr("creator.research.trends_search", "Search")
                } else {
                    tr("creator.research.analyze_remaining", "Analyze ({remaining}/{limit})")
                        .replace("{remaining}", trends.remaining.toString())
                        .replace("{limit}", "5")
                        .replace("Analyze", tr("creator.research.trends_search", "Search"))
                },
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (enabled) AccentOrange else Color.White.copy(alpha = 0.12f))
                    .clickable(enabled = enabled) { trends.searching = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrendsFilterPanel(
    translationStore: TranslationStore,
    trends: TrendsUiState,
) {
    fun tr(key: String, fallback: String) = translationStore.t(key, fallback)
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
    val geoOptions = trendGeoOptions(translationStore)
    val sorts = listOf(
        "volume" to tr("creator.research.sort_volume", "Search volume"),
        "keyword" to tr("creator.research.sort_keyword", "Keyword"),
        "competition" to tr("creator.research.sort_competition", "Competition"),
        "trend" to tr("creator.research.sort_trend", "Trend"),
    )
    var sortOpen by remember { mutableStateOf(false) }
    val sortLabel = sorts.firstOrNull { it.first == trends.sort }?.second ?: sorts.first().second
    val productTypes = listOf(
        "tshirt" to tr("creator.research.product_type_tshirt", "T-shirt"),
        "hoodie" to tr("creator.research.product_type_hoodie", "Hoodie"),
        "mug" to tr("creator.research.product_type_mug", "Mug"),
        "doormat" to tr("creator.research.product_type_doormat", "Doormat"),
        "tote" to tr("creator.research.product_type_tote", "Tote"),
        "poster" to tr("creator.research.product_type_poster", "Poster"),
        "sticker" to tr("creator.research.product_type_sticker", "Sticker"),
    )
    val times = listOf(
        "avg_12m" to tr("creator.research.time_avg_12m", "12-month average"),
        "last_month" to tr("creator.research.time_last_month", "Last month"),
        "rising" to tr("creator.research.time_rising", "Rising"),
        "stable" to tr("creator.research.time_stable", "Stable"),
        "falling" to tr("creator.research.time_falling", "Falling"),
    )
    val volumes = listOf(
        "very_high" to tr("creator.research.volume_very_high", "Very high"),
        "high" to tr("creator.research.volume_high", "High"),
        "medium" to tr("creator.research.volume_medium", "Medium"),
        "low" to tr("creator.research.volume_low", "Low"),
        "very_low" to tr("creator.research.volume_very_low", "Very low"),
    )
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .verticalScroll(panelScroll),
    ) {
        CenterChoiceField(
            label = tr("creator.research.country", "Country"),
            value = geoOptions.firstOrNull { it.first == trends.geo }?.second ?: trends.geo,
            flagCode = geoOptions.firstOrNull { it.first == trends.geo }?.third,
            options = geoOptions,
            onPick = { trends.geo = it },
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
                        imageVector = if (trends.sortDir == "asc") Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
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
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            if (trends.sort == id) {
                                trends.sortDir = if (trends.sortDir == "asc") "desc" else "asc"
                            } else {
                                trends.sort = id
                                trends.sortDir = if (id == "keyword") "asc" else "desc"
                            }
                            sortOpen = false
                        },
                    )
                }
            }
        }
        FilterFold(
            title = tr("creator.research.topics", "Topics"),
            open = foldOpen("trends_topics"),
            selectedCount = trends.selectedTopics.size,
            translationStore = translationStore,
            onToggle = { toggleFold("trends_topics") },
        ) {
            if (trends.topics.isEmpty()) {
                Text(
                    tr("creator.research.topics_all_hint", "All topics"),
                    color = TextDim,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            trends.topics.forEach { topic ->
                FilterCheckRow(
                    label = topic.label,
                    count = 0,
                    checked = topic.key in trends.selectedTopics,
                    onToggle = {
                        trends.selectedTopics = if (topic.key in trends.selectedTopics) {
                            trends.selectedTopics - topic.key
                        } else {
                            trends.selectedTopics + topic.key
                        }
                    },
                )
            }
        }
        FilterFold(
            title = tr("creator.research.product_type", "Product type"),
            open = foldOpen("trends_type"),
            selectedCount = trends.productTypes.size,
            translationStore = translationStore,
            onToggle = { toggleFold("trends_type") },
        ) {
            Text(
                tr("creator.research.product_type_all_hint", "All product types"),
                color = TextDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            productTypes.forEach { (id, label) ->
                FilterCheckRow(
                    label = label,
                    count = 0,
                    checked = id in trends.productTypes,
                    onToggle = {
                        trends.productTypes = if (id in trends.productTypes) trends.productTypes - id else trends.productTypes + id
                    },
                )
            }
        }
        FilterFold(
            title = tr("creator.research.time", "Time"),
            open = foldOpen("trends_time"),
            selectedCount = if (trends.time != "avg_12m") 1 else 0,
            translationStore = translationStore,
            onToggle = { toggleFold("trends_time") },
        ) {
            times.forEach { (id, label) ->
                FilterCheckRow(
                    label = label,
                    count = 0,
                    checked = trends.time == id,
                    onToggle = { trends.time = id },
                )
            }
        }
        FilterFold(
            title = tr("creator.research.volume", "Volume"),
            open = foldOpen("trends_volume"),
            selectedCount = trends.volume.size,
            translationStore = translationStore,
            onToggle = { toggleFold("trends_volume") },
        ) {
            volumes.forEach { (id, label) ->
                FilterCheckRow(
                    label = label,
                    count = 0,
                    checked = id in trends.volume,
                    onToggle = {
                        trends.volume = if (id in trends.volume) trends.volume - id else trends.volume + id
                    },
                )
            }
        }
    }
}

@Composable
internal fun EazyResearchTrendsPane(
    api: CreatorApi,
    translationStore: TranslationStore,
    trends: TrendsUiState,
    modifier: Modifier = Modifier,
) {
    fun tr(key: String, fallback: String) = translationStore.t(key, fallback)
    val context = LocalContext.current
    fun bucketLabel(key: String) = when (key) {
        "very_low" -> tr("creator.research.volume_very_low", "Very low")
        "low" -> tr("creator.research.volume_low", "Low")
        "medium" -> tr("creator.research.volume_medium", "Medium")
        "high" -> tr("creator.research.volume_high", "High")
        "very_high" -> tr("creator.research.volume_very_high", "Very high")
        else -> key.ifBlank { "—" }
    }

    suspend fun refresh() {
        trends.loading = true
        try {
            val topicParams = mutableMapOf("geo" to trends.geo, "language" to trends.language)
            if (trends.productTypes.isNotEmpty()) topicParams["product_type"] = trends.productTypes.joinToString(",")
            val topicData = api.call("eazy-research-trends-topics", topicParams)
            trends.configured = topicData.optBoolean("configured", true)
            val topicArr = topicData.optJSONArray("topics")
            val nextTopics = ArrayList<TrendTopic>()
            if (topicArr != null) {
                for (i in 0 until topicArr.length()) {
                    val row = topicArr.optJSONObject(i) ?: continue
                    nextTopics += TrendTopic(
                        row.optString("key"),
                        row.optString("label"),
                        row.optString("volume_bucket"),
                    )
                }
            }
            trends.topics = nextTopics
            val period = if (trends.time == "last_month") "last_month" else "avg_12m"
            val trendFlag = if (trends.time == "rising" || trends.time == "stable" || trends.time == "falling") trends.time else ""
            val kwParams = mutableMapOf(
                "geo" to trends.geo,
                "language" to trends.language,
                "period" to period,
                "sort" to trends.sort,
                "dir" to trends.sortDir,
                "limit" to "40",
                "offset" to "0",
            )
            if (trendFlag.isNotBlank()) kwParams["trend"] = trendFlag
            if (trends.selectedTopics.isNotEmpty()) kwParams["topic"] = trends.selectedTopics.joinToString(",")
            if (trends.productTypes.isNotEmpty()) kwParams["product_type"] = trends.productTypes.joinToString(",")
            if (trends.volume.isNotEmpty()) kwParams["volume"] = trends.volume.joinToString(",")
            val kwData = api.call("eazy-research-trends-keywords", kwParams)
            val arr = kwData.optJSONArray("keywords")
            val next = ArrayList<TrendKeyword>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val row = arr.optJSONObject(i) ?: continue
                    next += TrendKeyword(
                        row.optString("keyword"),
                        row.optString("topic_key"),
                        row.optString("volume_bucket"),
                        row.optString("trend"),
                        row.optString("competition"),
                        row.optString("product_type"),
                    )
                }
            }
            trends.keywords = next
            val limits = kwData.optJSONObject("trends_limits") ?: topicData.optJSONObject("trends_limits")
            if (limits != null) trends.remaining = limits.optInt("remaining", trends.remaining)
        } catch (_: Exception) {
            trends.keywords = emptyList()
        }
        trends.loading = false
    }

    LaunchedEffect(
        trends.geo,
        trends.language,
        trends.selectedTopics,
        trends.productTypes,
        trends.volume,
        trends.time,
        trends.sort,
        trends.sortDir,
    ) { refresh() }

    LaunchedEffect(trends.searching) {
        if (!trends.searching) return@LaunchedEffect
        val q = trends.analyzeQuery.trim().ifBlank { trends.query.trim() }
        if (q.isBlank() && trends.searchId.isBlank()) {
            trends.searching = false
            return@LaunchedEffect
        }
        try {
            var searchId = trends.searchId
            if (searchId.isBlank()) {
                val payload = JSONObject()
                payload.put("q", q)
                payload.put("geo", trends.searchGeo)
                payload.put("language", trends.searchLang)
                val started = api.postDispatchJson("eazy-research-trends-search", payload)
                started.optJSONObject("analyze_limits")?.let {
                    trends.remaining = it.optInt("remaining", trends.remaining)
                    trends.limit = it.optInt("limit", trends.limit)
                }
                searchId = started.optString("search_id")
                trends.searchId = searchId
            }
            if (searchId.isNotBlank()) {
                ResearchAnalyzeStore.saveTrends(
                    context,
                    ResearchJobSnapshot(searchId = searchId, query = q, running = true, tab = "trends"),
                )
                val seen = trends.keywords.map { it.keyword + it.topicKey }.toMutableSet()
                for (i in 0 until 20) {
                    kotlinx.coroutines.delay(700)
                    val st = api.call("eazy-research-trends-search-status", mapOf("search_id" to searchId))
                    st.optJSONObject("analyze_limits")?.let {
                        trends.remaining = it.optInt("remaining", trends.remaining)
                        trends.limit = it.optInt("limit", trends.limit)
                    }
                    val arr = st.optJSONArray("keywords")
                    val next = ArrayList<TrendKeyword>()
                    if (arr != null) {
                        for (j in 0 until arr.length()) {
                            val row = arr.optJSONObject(j) ?: continue
                            next += TrendKeyword(
                                row.optString("keyword"),
                                row.optString("topic_key"),
                                row.optString("volume_bucket"),
                                row.optString("trend"),
                                row.optString("competition"),
                                row.optString("product_type"),
                            )
                        }
                    }
                    val fresh = next.map { it.keyword + it.topicKey }.filter { it !in seen }.toSet()
                    if (fresh.isNotEmpty()) {
                        trends.justAdded = trends.justAdded + fresh
                        seen.addAll(fresh)
                    }
                    if (next.isNotEmpty()) trends.keywords = next
                    if (st.optBoolean("done", false) || st.optString("status") == "done" || st.optString("status") == "error") {
                        if (next.isNotEmpty()) trends.keywords = next
                        ResearchAnalyzeStore.saveTrends(
                            context,
                            ResearchJobSnapshot(searchId = searchId, query = q, running = false, tab = "trends", resultCount = next.size),
                        )
                        ResearchAnalyzeStore.showDone(q, next.size, "trends")
                        break
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            trends.searching = false
        }
    }

    val shownKeywords = remember(
        trends.keywords,
        trends.query,
        trends.selectedTopics,
        trends.productTypes,
        trends.volume,
    ) {
        var rows = trends.keywords
        if (trends.selectedTopics.isNotEmpty()) {
            rows = rows.filter { it.topicKey in trends.selectedTopics }
        }
        if (trends.productTypes.isNotEmpty()) {
            rows = rows.filter { it.productType in trends.productTypes }
        }
        if (trends.volume.isNotEmpty()) {
            rows = rows.filter { it.volumeBucket in trends.volume }
        }
        val q = trends.query.trim().lowercase()
        if (q.isNotEmpty()) {
            rows = rows.filter {
                it.keyword.lowercase().contains(q) || it.topicKey.lowercase().contains(q)
            }
        }
        rows
    }
    val placeholderCount = if (trends.searching) (RESEARCH_ANALYZE_SLOT_CAP - shownKeywords.size).coerceAtLeast(0) else 0

    LaunchedEffect(trends.justAdded) {
        if (trends.justAdded.isEmpty()) return@LaunchedEffect
        delay(RESEARCH_JUST_ADDED_MS)
        trends.justAdded = emptySet()
    }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            tr("creator.research.trends_note", "Estimated monthly searches from Google Keyword Planner. Not Amazon unit sales."),
            color = TextDim,
            fontSize = 11.sp,
        )
        when {
            trends.loading && shownKeywords.isEmpty() && !trends.searching -> Text(tr("creator.research.loading", "Loading..."), color = TextDim)
            shownKeywords.isEmpty() && !trends.searching -> Text(
                if (!trends.configured) tr("creator.research.trends_unconfigured", "Google Keyword Planner is not connected yet.")
                else tr("creator.research.trends_empty", "No Keyword Planner rows yet. Official Google Ads cache fills in the background."),
                color = TextDim,
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                items(shownKeywords, key = { it.keyword + it.topicKey }) { row ->
                    val added = (row.keyword + row.topicKey) in trends.justAdded
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xE6120C24))
                            .padding(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(row.keyword, color = TextMain, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            if (added) {
                                Text(
                                    tr("creator.research.just_added", "Just added"),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AccentOrange)
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                        Text(
                            listOf(
                                row.topicKey.replace('_', ' '),
                                bucketLabel(row.volumeBucket),
                                row.trend,
                                row.competition,
                                row.productType,
                            ).filter { it.isNotBlank() }.joinToString(" · "),
                            color = TextDim,
                            fontSize = 12.sp,
                        )
                    }
                }
                items(placeholderCount, key = { "kw-ph-$it" }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f)),
                    )
                }
            }
        }
    }
}

private fun trendGeoOptions(translationStore: TranslationStore): List<Triple<String, String, String?>> {
    fun tr(key: String, fallback: String) = translationStore.t(key, fallback)
    return listOf(
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
}

private fun trendLangOptions(translationStore: TranslationStore): List<Triple<String, String, String?>> {
    fun tr(key: String, fallback: String) = translationStore.t(key, fallback)
    return listOf(
        Triple("all", tr("creator.research.analyze_all", "All"), null),
        Triple("en", tr("creator.research.lang_en", "English"), "GB"),
        Triple("de", tr("creator.research.lang_de", "German"), "DE"),
        Triple("es", tr("creator.research.lang_es", "Spanish"), "ES"),
        Triple("fr", tr("creator.research.lang_fr", "French"), "FR"),
        Triple("it", tr("creator.research.lang_it", "Italian"), "IT"),
    )
}
