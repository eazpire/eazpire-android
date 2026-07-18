package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.TranslationStore
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private enum class ProductSkillTab {
    Overview,
    Skill,
    Variants,
    Regions,
    PrintAreas,
    Details,
}

@Composable
internal fun JourneyProductSkillInfoDialog(
    node: JourneyNodeItem,
    api: CreatorApi,
    translationStore: TranslationStore,
    onDismiss: () -> Unit,
) {
    fun t(key: String, fallback: String) = translationStore.t(key, fallback)
    fun tpl(key: String, fallback: String, vars: Map<String, String>): String {
        var s = t(key, fallback)
        vars.forEach { (k, v) -> s = s.replace("{{ $k }}", v).replace("{{$k}}", v) }
        return s
    }

    var loading by remember(node.nodeKey) { mutableStateOf(true) }
    var error by remember(node.nodeKey) { mutableStateOf<String?>(null) }
    var payload by remember(node.nodeKey) { mutableStateOf<JSONObject?>(null) }
    var tab by remember(node.nodeKey) { mutableStateOf(ProductSkillTab.Overview) }
    var expandedColor by remember(node.nodeKey) { mutableStateOf<String?>(null) }
    var expandedVariant by remember(node.nodeKey) { mutableStateOf<String?>(null) }
    var expandedContinent by remember(node.nodeKey) { mutableStateOf<String?>(null) }
    var expandedDetail by remember(node.nodeKey) { mutableStateOf("product_features") }

    LaunchedEffect(node.productKey) {
        loading = true
        error = null
        payload = null
        val pk = node.productKey.trim()
        if (pk.isBlank()) {
            error = t("creator.journey.product_skill.load_error", "Could not load product information.")
            loading = false
            return@LaunchedEffect
        }
        runCatching { api.getJourneyProductSkillInfo(pk) }
            .onSuccess { res ->
                if (res.optBoolean("ok", false)) {
                    payload = res
                } else {
                    error = t("creator.journey.product_skill.load_error", "Could not load product information.")
                }
            }
            .onFailure {
                error = t("creator.journey.product_skill.load_error", "Could not load product information.")
            }
        loading = false
    }

    val title = payload?.optString("title")?.takeIf { it.isNotBlank() } ?: node.title
    val meta = buildString {
        append(tpl("creator.journey.level_badge", "Level {{ n }}", mapOf("n" to node.minLevel.toString())))
        if (!node.unlocked && node.cost > 0) {
            append(" · ")
            append(journeyEazBadgeLabel(translationStore, node.committed, node.cost, false))
        }
    }

    val tabs = listOf(
        ProductSkillTab.Overview to t("creator.journey.product_skill.tab_overview", "Overview"),
        ProductSkillTab.Skill to t("creator.journey.product_skill.tab_skill", "Skill Info"),
        ProductSkillTab.Variants to t("creator.journey.product_skill.tab_variants", "Variants"),
        ProductSkillTab.Regions to t("creator.journey.product_skill.tab_regions", "Regions"),
        ProductSkillTab.PrintAreas to t("creator.journey.product_skill.tab_print_areas", "Print Areas"),
        ProductSkillTab.Details to t("creator.journey.product_skill.tab_details", "Product Details"),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                if (meta.isNotBlank()) {
                    Text(meta, color = Color(0xFFFBBF24), fontSize = 12.sp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    tabs.forEach { (key, label) ->
                        val active = tab == key
                        Text(
                            text = label,
                            color = if (active) Color(0xFFFFD28A) else Color(0xFFD1D5DB),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (active) Color(0x29FF9D00) else Color(0x14FFFFFF),
                                )
                                .border(
                                    1.dp,
                                    if (active) Color(0x8CFF9D00) else Color(0x22FFFFFF),
                                    RoundedCornerShape(999.dp),
                                )
                                .clickable { tab = key }
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 360.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x38000000))
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    when {
                        loading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center),
                                color = Color(0xFFFF9D00),
                            )
                        }
                        error != null -> {
                            Text(error ?: "", color = Color(0xFFFCA5A5), fontSize = 14.sp)
                        }
                        payload != null -> {
                            val data = payload!!
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                when (tab) {
                                    ProductSkillTab.Overview -> ProductSkillOverview(data, translationStore)
                                    ProductSkillTab.Skill -> ProductSkillSkillTab(
                                        data = data,
                                        translationStore = translationStore,
                                        expandedColor = expandedColor,
                                        onToggleColor = { name ->
                                            expandedColor = if (expandedColor == name) null else name
                                        },
                                    )
                                    ProductSkillTab.Variants -> ProductSkillVariantsTab(
                                        data = data,
                                        translationStore = translationStore,
                                        expandedVariant = expandedVariant,
                                        onToggleVariant = { name ->
                                            expandedVariant = if (expandedVariant == name) null else name
                                        },
                                    )
                                    ProductSkillTab.Regions -> ProductSkillRegionsTab(
                                        data = data,
                                        translationStore = translationStore,
                                        expandedContinent = expandedContinent,
                                        onToggleContinent = { code ->
                                            expandedContinent = if (expandedContinent == code) null else code
                                        },
                                    )
                                    ProductSkillTab.PrintAreas -> ProductSkillPrintAreasTab(data, translationStore)
                                    ProductSkillTab.Details -> ProductSkillDetailsTab(
                                        data = data,
                                        translationStore = translationStore,
                                        expanded = expandedDetail,
                                        onToggle = { key ->
                                            expandedDetail = if (expandedDetail == key) "" else key
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(translationStore.t("creator.common.close", "Close"))
            }
        },
        containerColor = Color(0xFF0B1220),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFE5E7EB),
    )
}

@Composable
private fun ProductSkillOverview(data: JSONObject, translationStore: TranslationStore) {
    fun t(key: String, fallback: String) = translationStore.t(key, fallback)
    val ov = data.optJSONObject("overview") ?: JSONObject()
    val audience = ov.optJSONArray("audience").toStringList()
    val printAreas = ov.optJSONArray("print_areas").toStringList()
    KvRow(t("creator.journey.product_skill.audience", "Audience"), audience.joinToString(", ").ifBlank { "—" })
    ShippingCountryRow(ov, translationStore)
    KvRow(
        t("creator.journey.product_skill.base_product_model", "Base product model"),
        ov.optString("base_product_model").ifBlank { data.optString("title").ifBlank { "—" } },
    )
    KvRow(
        t("creator.journey.product_skill.base_product_brand", "Base product brand"),
        ov.optString("provider_brand").ifBlank { "—" },
    )
    KvRow(
        t("creator.journey.product_skill.print_areas", "Print areas"),
        printAreas.joinToString(", ").ifBlank { "—" },
    )
}

@Composable
private fun ProductSkillSkillTab(
    data: JSONObject,
    translationStore: TranslationStore,
    expandedColor: String?,
    onToggleColor: (String) -> Unit,
) {
    fun t(key: String, fallback: String) = translationStore.t(key, fallback)
    fun tpl(key: String, fallback: String, vars: Map<String, String>): String {
        var s = t(key, fallback)
        vars.forEach { (k, v) -> s = s.replace("{{ $k }}", v).replace("{{$k}}", v) }
        return s
    }
    val skill = data.optJSONObject("skill") ?: JSONObject()
    val colors = skill.optJSONArray("colors") ?: JSONArray()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        StatCard(
            label = t("creator.journey.product_skill.required_level", "Required level"),
            value = skill.optInt("min_level", 0).takeIf { it > 0 }?.toString() ?: "—",
            modifier = Modifier.weight(1f),
        )
        StatCard(
            label = t("creator.journey.product_skill.unlock_cost", "Unlock cost"),
            value = "${skill.optInt("unlock_cost_eaz", 0)} EAZV",
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        tpl(
            "creator.journey.product_skill.variant_cost_hint",
            "Each additional color or size variant costs {{ n }} EAZV after free picks.",
            mapOf("n" to skill.optInt("variant_unlock_cost_eaz", 60).toString()),
        ),
        color = Color(0xFF9CA3AF),
        fontSize = 12.sp,
    )
    if (colors.length() == 0) {
        Text(t("creator.journey.product_skill.empty", "No data available yet."), color = Color(0xFF9CA3AF))
        return
    }
    for (i in 0 until colors.length()) {
        val c = colors.optJSONObject(i) ?: continue
        val name = c.optString("name")
        val hex = parseHexColor(c.optString("hex"), Color(0xFF888888))
        val sizes = c.optJSONArray("sizes").toStringList()
        val open = expandedColor == name
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleColor(name) }
                .padding(vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(hex))
                Text(name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            if (open) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(start = 20.dp, top = 8.dp),
                ) {
                    if (sizes.isEmpty()) {
                        Text(t("creator.journey.product_skill.empty", "No data available yet."), color = Color(0xFF9CA3AF), fontSize = 12.sp)
                    } else {
                        sizes.forEach { sz ->
                            Text(
                                sz,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x14FFFFFF))
                                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductSkillVariantsTab(
    data: JSONObject,
    translationStore: TranslationStore,
    expandedVariant: String?,
    onToggleVariant: (String) -> Unit,
) {
    fun t(key: String, fallback: String) = translationStore.t(key, fallback)
    val variants = data.optJSONArray("variants") ?: JSONArray()
    if (variants.length() == 0) {
        Text(t("creator.journey.product_skill.empty", "No data available yet."), color = Color(0xFF9CA3AF))
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in 0 until variants.length()) {
            val v = variants.optJSONObject(i) ?: continue
            val name = v.optString("name")
            val open = expandedVariant == name
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x14FFFFFF))
                    .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleVariant(name) }
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val imageUrl = v.optString("image_url")
                    if (imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x14FFFFFF)),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(parseHexColor(v.optString("hex"), Color(0xFF888888))),
                        )
                    }
                    Text(
                        name,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (open) {
                    val sizes = v.optJSONArray("sizes") ?: JSONArray()
                    Column(
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (sizes.length() == 0) {
                            Text(
                                t("creator.journey.product_skill.empty", "No data available yet."),
                                color = Color(0xFF9CA3AF),
                                fontSize = 12.sp,
                            )
                        } else {
                            for (j in 0 until sizes.length()) {
                                val szObj = sizes.optJSONObject(j)
                                val sizeName = when {
                                    szObj != null -> szObj.optString("name").ifBlank { szObj.optString("size") }
                                    else -> sizes.optString(j)
                                }.trim()
                                if (sizeName.isBlank()) continue
                                val costCents = szObj?.optDouble("cost_cents", Double.NaN) ?: Double.NaN
                                val price = formatEuroFromCents(costCents)
                                    ?: t("creator.journey.product_skill.price_na", "Price n/a")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0x14FFFFFF))
                                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(sizeName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(price, color = Color(0xFFFFD28A), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductSkillRegionsTab(
    data: JSONObject,
    translationStore: TranslationStore,
    expandedContinent: String?,
    onToggleContinent: (String) -> Unit,
) {
    fun t(key: String, fallback: String) = translationStore.t(key, fallback)
    fun tpl(key: String, fallback: String, vars: Map<String, String>): String {
        var s = t(key, fallback)
        vars.forEach { (k, v) -> s = s.replace("{{ $k }}", v).replace("{{$k}}", v) }
        return s
    }
    Text(
        t("creator.journey.product_skill.shipping_placeholder", "Shipping costs will be configured in Admin."),
        color = Color(0xFF9CA3AF),
        fontSize = 12.sp,
    )
    val continents = data.optJSONObject("regions")?.optJSONArray("continents") ?: JSONArray()
    if (continents.length() == 0) {
        Text(t("creator.journey.product_skill.empty", "No data available yet."), color = Color(0xFF9CA3AF))
        return
    }
    for (i in 0 until continents.length()) {
        val cont = continents.optJSONObject(i) ?: continue
        val code = cont.optString("code")
        val countries = cont.optJSONArray("countries") ?: JSONArray()
        val open = expandedContinent == code
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleContinent(code) }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(cont.optString("title").ifBlank { code }, color = Color.White, fontWeight = FontWeight.Bold)
                Text(
                    tpl(
                        "creator.journey.product_skill.countries_count",
                        "{{ n }} countries",
                        mapOf("n" to countries.length().toString()),
                    ),
                    color = Color(0xFF9CA3AF),
                    fontSize = 12.sp,
                )
            }
            if (open) {
                for (j in 0 until countries.length()) {
                    val c = countries.optJSONObject(j) ?: continue
                    val flagCode = c.optString("flag_code").ifBlank { c.optString("code").lowercase(Locale.US) }
                    val tba = t("creator.journey.product_skill.shipping_tba", "TBA")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AsyncImage(
                            model = "https://cdn.jsdelivr.net/gh/lipis/flag-icons@7.2.2/flags/4x3/$flagCode.svg",
                            contentDescription = null,
                            modifier = Modifier
                                .width(22.dp)
                                .height(16.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Text(
                            c.optString("name").ifBlank { c.optString("code") },
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${t("creator.journey.product_skill.shipping_first", "1st")}: $tba · ${t("creator.journey.product_skill.shipping_additional", "Add.")}: $tba",
                            color = Color(0xFF9CA3AF),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductSkillPrintAreasTab(data: JSONObject, translationStore: TranslationStore) {
    fun t(key: String, fallback: String) = translationStore.t(key, fallback)
    val areas = data.optJSONArray("print_areas") ?: JSONArray()
    if (areas.length() == 0) {
        Text(t("creator.journey.product_skill.empty", "No data available yet."), color = Color(0xFF9CA3AF))
        return
    }
    // Phone: 2 columns (matches web mobile). Larger widths still stay compact tiles.
    val chunked = (0 until areas.length()).chunked(2)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chunked.forEach { rowIdxs ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowIdxs.forEach { i ->
                    val a = areas.optJSONObject(i)
                    if (a == null) {
                        Spacer(modifier = Modifier.weight(1f))
                        return@forEach
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x14FFFFFF))
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp))
                            .padding(8.dp),
                    ) {
                        Text(
                            a.optString("label").ifBlank { a.optString("position") },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x33000000)),
                            contentAlignment = Alignment.Center,
                        ) {
                            val url = a.optString("shop_mock_url")
                            if (url.isNotBlank()) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Text(
                                    t("creator.journey.product_skill.empty", "No data available yet."),
                                    color = Color(0xFF9CA3AF),
                                    fontSize = 10.sp,
                                )
                            }
                        }
                    }
                }
                if (rowIdxs.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ProductSkillDetailsTab(
    data: JSONObject,
    translationStore: TranslationStore,
    expanded: String,
    onToggle: (String) -> Unit,
) {
    fun t(key: String, fallback: String) = translationStore.t(key, fallback)
    val details = data.optJSONObject("product_details") ?: JSONObject()
    val sections = listOf(
        "product_features" to t("creator.journey.product_skill.details_features", "Product Features"),
        "care_instructions" to t("creator.journey.product_skill.details_care", "Care Instructions"),
        "size_table_html" to t("creator.journey.product_skill.details_size", "Size Table"),
        "gpsr_html" to t("creator.journey.product_skill.details_gpsr", "GPSR"),
    )
    sections.forEach { (key, label) ->
        val html = details.optString(key).trim()
        val open = expanded == key
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(key) }
                    .padding(vertical = 10.dp),
            )
            if (open) {
                Text(
                    stripHtml(html).ifBlank { t("creator.journey.product_skill.details_empty", "No details yet.") },
                    color = Color(0xFFE5E7EB),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ShippingCountryRow(ov: JSONObject, translationStore: TranslationStore) {
    fun t(key: String, fallback: String) = translationStore.t(key, fallback)
    val flagCode = ov.optString("shipping_flag_code").ifBlank {
        ov.optString("shipping_country").lowercase(Locale.US)
    }
    val name = ov.optString("shipping_country_name").ifBlank {
        ov.optString("shipping_country")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            t("creator.journey.product_skill.shipping_country", "Shipping country"),
            color = Color(0xFF9CA3AF),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(120.dp),
        )
        if (name.isBlank()) {
            Text("—", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
        } else {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (flagCode.isNotBlank()) {
                    AsyncImage(
                        model = "https://cdn.jsdelivr.net/gh/lipis/flag-icons@7.2.2/flags/4x3/$flagCode.svg",
                        contentDescription = null,
                        modifier = Modifier
                            .width(22.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                Text(name, color = Color.White, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun KvRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, color = Color(0xFF9CA3AF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(120.dp))
        Text(value, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x14FFFFFF))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Text(label, color = Color(0xFF9CA3AF), fontSize = 11.sp)
        Text(value, color = Color(0xFFFFD28A), fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            val s = optString(i).trim()
            if (s.isNotBlank()) add(s)
        }
    }
}

private fun parseHexColor(raw: String?, fallback: Color): Color {
    val h = raw?.trim()?.removePrefix("#").orEmpty()
    if (h.length != 6) return fallback
    return runCatching {
        Color(android.graphics.Color.parseColor("#$h"))
    }.getOrDefault(fallback)
}

private fun formatEuroFromCents(cents: Double): String? {
    if (cents.isNaN() || !cents.isFinite() || cents <= 0) return null
    return String.format(Locale.GERMANY, "€%.2f", cents / 100.0)
}

private fun stripHtml(html: String): String {
    if (html.isBlank()) return ""
    return html
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</p>"), "\n")
        .replace(Regex("(?i)</li>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}
