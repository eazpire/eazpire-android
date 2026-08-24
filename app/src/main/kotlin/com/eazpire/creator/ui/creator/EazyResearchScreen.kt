package com.eazpire.creator.ui.creator

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

private const val WATCH_PREFS = "eazy-research"
private const val WATCH_KEY = "eazy-research-watched"

private data class ResearchProduct(
    val asin: String,
    val title: String,
    val brand: String,
    val imageUrl: String,
    val nicheKey: String,
    val subNiche: String,
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
    var niche by remember { mutableStateOf("all") }
    var sort by remember { mutableStateOf("review_growth") }
    var view by remember { mutableStateOf("opportunities") }
    var selected by remember { mutableStateOf<ResearchProduct?>(null) }
    var watched by remember { mutableStateOf(loadWatchedAsins(context)) }

    LaunchedEffect(tokenStore.getJwt()) {
        loading = true
        error = null
        try {
            val data = api.call(
                "eazy-research-products",
                mapOf(
                    "reprint_ok" to "1",
                    "limit" to "80",
                    "sort" to sort,
                ),
            )
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
                        "No Amazon.de snapshots yet. Official catalog collection runs in the background.",
                    )
                }
            }
        } catch (_: Exception) {
            error = tr("creator.research.error", "Research data could not be loaded.")
        }
        loading = false
    }

    val filtered = remember(products, query, niche, sort, view, watched) {
        filterProducts(products, query, niche, sort, view, watched)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .heightIn(max = cap)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(tr("creator.research.title", "eazy Research"), color = TextMain, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(
            tr(
                "creator.research.subtitle",
                "Reprint-safe Amazon.de demand signals. Reviews and BSR only — never invented sales.",
            ),
            color = TextDim,
            fontSize = 13.sp,
        )
        if (preview) {
            Text(
                tr("creator.research.preview_banner", "Preview data — live snapshots coming"),
                color = Color(0xFFFFD7B0),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x33F97316))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
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
        ChipRow(
            items = listOf(ResearchNiche("all", tr("creator.research.niche_all", "All"))) + niches,
            selected = niche,
            onSelect = { niche = it },
        )
        ChipRow(
            items = listOf(
                ResearchNiche("opportunities", tr("creator.research.tab_opportunities", "Opportunities")),
                ResearchNiche("rising", tr("creator.research.tab_rising", "Rising")),
                ResearchNiche("review_growth", tr("creator.research.tab_review_growth", "Review growth")),
                ResearchNiche("watched", tr("creator.research.tab_watched", "Watched")),
            ),
            selected = view,
            onSelect = { view = it },
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            listOf(
                "review_growth" to tr("creator.research.sort_review_growth", "Review growth"),
                "reviews" to tr("creator.research.sort_reviews", "Reviews"),
                "bsr" to tr("creator.research.sort_bsr", "BSR"),
                "newest" to tr("creator.research.sort_newest", "Newest snapshot"),
            ).forEach { (id, label) ->
                val on = sort == id
                Text(
                    label,
                    color = if (on) TextMain else TextDim,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (on) Accent.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.06f))
                        .clickable { sort = id }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                )
            }
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
                items(filtered, key = { it.asin }) { product ->
                    val nicheLabel = niches.firstOrNull { it.key == product.nicheKey }?.label
                        ?: product.nicheKey
                    OpportunityCard(
                        product = product,
                        nicheLabel = nicheLabel,
                        watched = watched.contains(product.asin),
                        translationStore = translationStore,
                        onOpen = { selected = product },
                        onToggleWatch = {
                            val next = if (watched.contains(product.asin)) {
                                watched - product.asin
                            } else {
                                watched + product.asin
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

@Composable
private fun ChipRow(
    items: List<ResearchNiche>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        items.forEach { item ->
            val on = selected == item.key
            Text(
                item.label,
                color = TextMain,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (on) Accent.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.06f))
                    .clickable { onSelect(item.key) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
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
            IconButton(
                onClick = onToggleWatch,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .size(40.dp)
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
    niche: String,
    sort: String,
    view: String,
    watched: Set<String>,
): List<ResearchProduct> {
    var rows = products.filter { it.reprintOk }
    if (niche != "all") rows = rows.filter { it.nicheKey == niche }
    val q = query.trim().lowercase(Locale.ROOT)
    if (q.isNotEmpty()) {
        rows = rows.filter {
            listOf(it.title, it.brand, it.asin, it.nicheKey).joinToString(" ").lowercase(Locale.ROOT).contains(q)
        }
    }
    rows = when (view) {
        "rising" -> rows.filter { it.trend == "rising" || it.risingScore > 0 }
        "review_growth" -> rows.filter { (it.reviewDelta ?: 0) > 0 }
        "watched" -> rows.filter { watched.contains(it.asin) }
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
            title = p.optString("title").ifBlank { p.optString("asin") },
            brand = p.optString("brand"),
            imageUrl = p.optString("image_url"),
            nicheKey = p.optString("niche_key"),
            subNiche = p.optString("sub_niche").ifBlank { p.optString("sub_niche_key") },
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

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

private fun JSONObject.optIntOrNull(key: String): Int? =
    if (!has(key) || isNull(key)) null else optInt(key)

private fun JSONObject.optLongOrNull(key: String): Long? =
    if (!has(key) || isNull(key)) null else optLong(key)

private fun JSONObject.optBooleanOrNull(key: String): Boolean? =
    if (!has(key) || isNull(key)) null else optBoolean(key)
