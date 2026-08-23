package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import org.json.JSONArray

private val CardBg = Color(0xE6120C24)
private val TextMain = Color(0xFFF4F1FF)
private val TextDim = Color(0xC7F4F1FF)

private data class ResearchRow(
    val asin: String,
    val title: String,
    val imageUrl: String,
    val meta: String,
    val trend: String,
)

@Composable
fun EazyResearchScreen(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    maxHeight: Dp = Dp.Infinity,
    modifier: Modifier = Modifier,
) {
    val cap = if (maxHeight == Dp.Infinity) 4000.dp else maxHeight
    val api = remember(tokenStore.getJwt()) { CreatorApi(jwt = tokenStore.getJwt()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf("") }
    var rising by remember { mutableStateOf(listOf<ResearchRow>()) }
    var reviewGrowth by remember { mutableStateOf(listOf<ResearchRow>()) }
    var niches by remember { mutableStateOf(listOf<String>()) }
    var selectedAsin by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<String?>(null) }

    fun tr(key: String, fallback: String) = translationStore.t(key, fallback)

    LaunchedEffect(tokenStore.getJwt()) {
        loading = true
        error = null
        try {
            val data = api.call("eazy-research-overview")
            if (!data.optBoolean("ok", false)) {
                error = tr("creator.research.error", "Research data could not be loaded.")
            } else {
                val last = data.optJSONObject("last_run")
                status = if (last != null) {
                    tr("creator.research.last_run", "Last snapshot") + " · " +
                        last.optString("niche_pack", last.optString("source", "")) + " · " +
                        tr("creator.research.reviews_note", "Review counts stay empty until Amazon returns them.")
                } else {
                    tr(
                        "creator.research.empty",
                        "No Amazon.de snapshots yet. Official catalog collection runs in the background."
                    )
                }
                rising = parseRows(data.optJSONArray("rising"))
                reviewGrowth = parseRows(data.optJSONArray("review_growth"))
                niches = parseNiches(data.optJSONArray("niches"), ::tr)
            }
        } catch (_: Exception) {
            error = tr("creator.research.error", "Research data could not be loaded.")
        }
        loading = false
    }

    LaunchedEffect(selectedAsin, tokenStore.getJwt()) {
        val asin = selectedAsin ?: return@LaunchedEffect
        detail = tr("creator.research.loading", "Loading...")
        try {
            val data = api.call("eazy-research-product", mapOf("asin" to asin))
            val p = data.optJSONObject("product")
            detail = if (p == null) {
                tr("creator.research.not_found", "Product not found.")
            } else {
                val reprintOk = p.optBoolean("reprint_ok", false)
                val safe = if (reprintOk) {
                    tr("creator.research.reprint_ok", "Reprint-safe")
                } else {
                    tr("creator.research.blocked", "Hidden from ranking")
                }
                buildString {
                    append(p.optString("title").ifBlank { asin })
                    append('\n')
                    append(asin)
                    append(" · ")
                    append(safe)
                    append('\n')
                    append(tr("creator.research.review_delta", "Review change"))
                    append(": ")
                    append(p.opt("review_delta")?.toString() ?: tr("creator.research.unknown", "Unknown"))
                    append('\n')
                    append(
                        tr(
                            "creator.research.no_sales_claim",
                            "We never show invented unit sales. BSR and reviews are observed snapshots only."
                        )
                    )
                }
            }
        } catch (_: Exception) {
            detail = tr("creator.research.error", "Research data could not be loaded.")
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .heightIn(max = cap)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(tr("creator.research.title", "eazy Research"), color = TextMain, fontSize = 22.sp)
        Text(
            tr(
                "creator.research.subtitle",
                "Reprint-safe Amazon.de demand signals. Reviews and BSR only — never invented sales."
            ),
            color = TextDim,
            fontSize = 14.sp
        )
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier
                    .padding(top = 24.dp)
                    .align(Alignment.CenterHorizontally),
                color = Color(0xFF7C5CFF)
            )
            error != null -> Text(error ?: "", color = TextDim, fontSize = 14.sp)
            else -> {
                Text(status, color = TextDim, fontSize = 13.sp)
                ResearchBlock(
                    title = tr("creator.research.rising_title", "Rising reprint-safe products"),
                    empty = tr("creator.research.empty_rising", "No rising reprint-safe products yet."),
                    rows = rising,
                    onOpen = { selectedAsin = it }
                )
                ResearchBlock(
                    title = tr("creator.research.reviews_title", "Strongest review growth"),
                    empty = tr(
                        "creator.research.empty_reviews",
                        "No review growth yet — needs at least two snapshots."
                    ),
                    rows = reviewGrowth,
                    onOpen = { selectedAsin = it }
                )
                CardColumn {
                    Text(tr("creator.research.niches_title", "Niches"), color = TextMain, fontSize = 16.sp)
                    Spacer(Modifier.height(8.dp))
                    if (niches.isEmpty()) {
                        Text(
                            tr("creator.research.empty_niches", "Niche packs are ready; snapshots will fill scores."),
                            color = TextDim,
                            fontSize = 13.sp
                        )
                    } else {
                        niches.forEach { line ->
                            Text(line, color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                        }
                    }
                }
                if (detail != null) {
                    CardColumn { Text(detail ?: "", color = TextMain, fontSize = 14.sp) }
                }
            }
        }
    }
}

@Composable
private fun CardColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .padding(14.dp),
        content = { content() }
    )
}

@Composable
private fun ResearchBlock(
    title: String,
    empty: String,
    rows: List<ResearchRow>,
    onOpen: (String) -> Unit,
) {
    CardColumn {
        Text(title, color = TextMain, fontSize = 16.sp)
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) {
            Text(empty, color = TextDim, fontSize = 13.sp)
        } else {
            rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpen(row.asin) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (row.imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = row.imageUrl,
                            contentDescription = row.title,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(row.title, color = TextMain, fontSize = 14.sp, maxLines = 2)
                        Text(row.meta, color = TextDim, fontSize = 12.sp)
                    }
                    Text(row.trend, color = TextDim, fontSize = 11.sp)
                }
            }
        }
    }
}

private fun parseRows(arr: JSONArray?): List<ResearchRow> {
    if (arr == null) return emptyList()
    val out = ArrayList<ResearchRow>(arr.length())
    for (i in 0 until arr.length()) {
        val p = arr.optJSONObject(i) ?: continue
        val latest = p.optJSONObject("latest")
        val reviews = latest?.opt("reviews_count")?.toString() ?: "—"
        val bsr = latest?.opt("bsr")?.let { "BSR $it" } ?: "No BSR"
        val delta = p.opt("review_delta")?.toString() ?: "Unknown"
        out += ResearchRow(
            asin = p.optString("asin"),
            title = p.optString("title").ifBlank { p.optString("asin") },
            imageUrl = p.optString("image_url"),
            meta = "$bsr · $reviews · $delta",
            trend = p.optString("trend").ifBlank { "unknown" },
        )
    }
    return out
}

private fun parseNiches(arr: JSONArray?, tr: (String, String) -> String): List<String> {
    if (arr == null) return emptyList()
    val out = ArrayList<String>(arr.length())
    for (i in 0 until arr.length()) {
        val n = arr.optJSONObject(i) ?: continue
        val label = n.optString("label").ifBlank { n.optString("niche_key") }
        val score = n.opt("score")?.toString() ?: "0"
        out += "$label · ${tr("creator.research.review_velocity", "Review velocity")} $score"
    }
    return out
}
