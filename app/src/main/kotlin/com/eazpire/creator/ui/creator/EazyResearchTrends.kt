package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.components.GlassCircularFlag
import org.json.JSONObject

private val TextMain = Color(0xFFF4F1FF)
private val TextDim = Color(0xC7F4F1FF)
private val Accent = Color(0xFF7C5CFF)
private val PanelBg = Color(0xFF120C22)

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

@Composable
internal fun ResearchTabRow(
    selected: String,
    translationStore: TranslationStore,
    onSelect: (String) -> Unit,
) {
    fun tr(key: String, fallback: String) = translationStore.t(key, fallback)
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                    .background(if (on) Accent.copy(alpha = 0.32f) else Color(0xFF10122A))
                    .clickable { onSelect(id) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
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
                        .verticalScroll(rememberScrollState()),
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
internal fun EazyResearchTrendsPane(
    api: CreatorApi,
    translationStore: TranslationStore,
    loggedIn: Boolean,
    modifier: Modifier = Modifier,
) {
    fun tr(key: String, fallback: String) = translationStore.t(key, fallback)
    var geo by remember { mutableStateOf("ALL") }
    var language by remember { mutableStateOf("all") }
    var query by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf(listOf<TrendKeyword>()) }
    var topics by remember { mutableStateOf(listOf<TrendTopic>()) }
    var selectedTopics by remember { mutableStateOf(setOf<String>()) }
    var loading by remember { mutableStateOf(true) }
    var configured by remember { mutableStateOf(true) }
    var remaining by remember { mutableStateOf(5) }
    var searching by remember { mutableStateOf(false) }

    val geoOptions = listOf(
        Triple("ALL", tr("creator.research.country_all", "All countries"), null as String?),
        Triple("DE", tr("creator.research.geo_DE", "Germany"), "DE"),
        Triple("US", tr("creator.research.geo_US", "United States"), "US"),
        Triple("GB", tr("creator.research.geo_GB", "United Kingdom"), "GB"),
        Triple("FR", tr("creator.research.geo_FR", "France"), "FR"),
        Triple("IT", tr("creator.research.geo_IT", "Italy"), "IT"),
        Triple("ES", tr("creator.research.geo_ES", "Spain"), "ES"),
        Triple("CA", tr("creator.research.geo_CA", "Canada"), "CA"),
        Triple("AU", tr("creator.research.geo_AU", "Australia"), "AU"),
    )
    val langOptions = listOf(
        Triple("all", tr("creator.research.analyze_all", "All"), null as String?),
        Triple("en", tr("creator.research.lang_en", "English"), "GB"),
        Triple("de", tr("creator.research.lang_de", "German"), "DE"),
        Triple("es", tr("creator.research.lang_es", "Spanish"), "ES"),
        Triple("fr", tr("creator.research.lang_fr", "French"), "FR"),
        Triple("it", tr("creator.research.lang_it", "Italian"), "IT"),
    )

    fun bucketLabel(key: String) = when (key) {
        "very_low" -> tr("creator.research.volume_very_low", "Very low")
        "low" -> tr("creator.research.volume_low", "Low")
        "medium" -> tr("creator.research.volume_medium", "Medium")
        "high" -> tr("creator.research.volume_high", "High")
        "very_high" -> tr("creator.research.volume_very_high", "Very high")
        else -> key.ifBlank { "—" }
    }

    suspend fun refresh() {
        loading = true
        try {
            val topicParams = mutableMapOf("geo" to geo, "language" to language)
            val topicData = api.call("eazy-research-trends-topics", topicParams)
            configured = topicData.optBoolean("configured", true)
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
            topics = nextTopics
            val kwParams = mutableMapOf("geo" to geo, "language" to language, "limit" to "40", "offset" to "0")
            if (selectedTopics.isNotEmpty()) kwParams["topic"] = selectedTopics.joinToString(",")
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
            keywords = next
            val limits = kwData.optJSONObject("trends_limits") ?: topicData.optJSONObject("trends_limits")
            if (limits != null) remaining = limits.optInt("remaining", remaining)
        } catch (_: Exception) {
            keywords = emptyList()
        }
        loading = false
    }

    LaunchedEffect(geo, language, selectedTopics) { refresh() }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("creator.research.trends_note", "Estimated monthly searches from Google Keyword Planner. Not Amazon unit sales."), color = TextDim, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CenterChoiceField(
                label = tr("creator.research.country", "Country"),
                value = geoOptions.firstOrNull { it.first == geo }?.second ?: geo,
                flagCode = geoOptions.firstOrNull { it.first == geo }?.third,
                options = geoOptions,
                modifier = Modifier.weight(1f),
                onPick = { geo = it },
            )
            CenterChoiceField(
                label = tr("creator.research.language", "Language"),
                value = langOptions.firstOrNull { it.first == language }?.second ?: language,
                flagCode = langOptions.firstOrNull { it.first == language }?.third,
                options = langOptions,
                modifier = Modifier.weight(1f),
                onPick = { language = it },
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(tr("creator.research.trends_search_placeholder", "Search a topic or keyword"), color = TextDim) },
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextMain, unfocusedTextColor = TextMain, focusedLabelColor = TextDim, unfocusedLabelColor = TextDim, focusedBorderColor = Accent, unfocusedBorderColor = Color.White.copy(alpha = 0.2f)),
        )
        TextButton(
            onClick = {
                if (!loggedIn || query.isBlank() || searching) return@TextButton
                searching = true
            },
            enabled = loggedIn && !searching,
        ) {
            Text(
                tr("creator.research.analyze_remaining", "Analyze ({remaining}/{limit})")
                    .replace("{remaining}", remaining.toString())
                    .replace("{limit}", "5")
                    .replace("Analyze", tr("creator.research.trends_search", "Search")),
                color = TextMain,
            )
        }
        LaunchedEffect(searching) {
            if (!searching || query.isBlank()) return@LaunchedEffect
            try {
                val payload = JSONObject()
                payload.put("q", query)
                payload.put("geo", geo)
                payload.put("language", language)
                val started = api.postDispatchJson(
                    "eazy-research-trends-search",
                    payload,
                )
                started.optJSONObject("analyze_limits")?.let { remaining = it.optInt("remaining", remaining) }
                val searchId = started.optString("search_id")
                if (searchId.isNotBlank()) {
                    for (i in 0 until 12) {
                        kotlinx.coroutines.delay(700)
                        val st = api.call("eazy-research-trends-search-status", mapOf("search_id" to searchId))
                        st.optJSONObject("analyze_limits")?.let { remaining = it.optInt("remaining", remaining) }
                        if (st.optInt("remaining", -1) >= 0) remaining = st.optInt("remaining", remaining)
                        if (st.optBoolean("done", false)) {
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
                            keywords = next
                            break
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                searching = false
            }
        }
        if (topics.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                topics.take(8).forEach { topic ->
                    val on = topic.key in selectedTopics
                    Text(
                        topic.label + if (topic.volumeBucket.isNotBlank()) " · ${bucketLabel(topic.volumeBucket)}" else "",
                        color = TextMain,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (on) Accent.copy(alpha = 0.35f) else Color(0xFF10122A))
                            .clickable {
                                selectedTopics = if (on) selectedTopics - topic.key else selectedTopics + topic.key
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
        when {
            loading -> Text(tr("creator.research.loading", "Loading..."), color = TextDim)
            keywords.isEmpty() -> Text(
                if (!configured) tr("creator.research.trends_unconfigured", "Google Keyword Planner is not connected yet.")
                else tr("creator.research.trends_empty", "No Keyword Planner rows yet. Official Google Ads cache fills in the background."),
                color = TextDim,
            )
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                items(keywords, key = { it.keyword + it.geoSafe() }) { row ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xE6120C24))
                            .padding(10.dp),
                    ) {
                        Text(row.keyword, color = TextMain, fontWeight = FontWeight.SemiBold)
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
            }
        }
    }
}

private fun TrendKeyword.geoSafe() = topicKey
