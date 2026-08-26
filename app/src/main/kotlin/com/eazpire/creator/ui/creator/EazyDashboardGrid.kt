package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val Glass = Color(0xFF0B1220).copy(alpha = 0.72f)
private val GlassBorder = Color.White.copy(alpha = 0.12f)
private val RowH = 56.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EazyDashboardGrid(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    onOpenSalesModal: () -> Unit,
    onOpenJourney: () -> Unit,
    onNavigateToGenerator: () -> Unit,
    onNavigateToDesigns: () -> Unit,
    onNavigateToProducts: () -> Unit,
    onNavigateToMarketingHero: () -> Unit,
    onNavigateToAutomations: () -> Unit,
    onNavigateToResearch: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val ownerId = tokenStore.getOwnerId()
    val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }
    var state by remember { mutableStateOf<DashboardV5State?>(null) }
    var editing by remember { mutableStateOf(false) }
    var manager by remember { mutableStateOf(false) }
    var draftQa by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val t: (String, String) -> String = { k, f -> translationStore.t(k, f) }

    LaunchedEffect(ownerId) {
        state = withContext(Dispatchers.IO) {
            parseDashboardV5(api.getDashboardV5(ownerId ?: ""))
        }
    }

    val snap = state
    Column(modifier = modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            TextButton(onClick = { editing = !editing }) {
                Text(
                    t("creator.dashboard.edit_layout", "Edit layout"),
                    color = if (editing) EazColors.Orange else Color.White
                )
            }
            TextButton(onClick = {
                val active = snap?.layouts?.firstOrNull { it.id == snap.activeLayoutId }
                draftQa = active?.quickActionIds ?: emptyList()
                manager = true
            }) {
                Text(t("creator.dashboard.customize", "Customize dashboard"), color = Color.White)
            }
        }
        if (snap == null) {
            Text(t("creator.overview.loading", "Loading..."), color = Color.White.copy(alpha = 0.7f))
            return@Column
        }
        val layout = snap.layouts.firstOrNull { it.id == snap.activeLayoutId } ?: snap.layouts.firstOrNull()
        if (layout == null) return@Column
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val surfaceName = surfaceForWidth(maxWidth.value)
            val surface = when (surfaceName) {
                "mobile" -> layout.mobile
                "tablet" -> layout.tablet
                else -> layout.desktop
            }
            val density = LocalDensity.current
            val cols = surface.columns.coerceAtLeast(1)
            val gap = 8.dp
            val colW = (maxWidth - gap * (cols - 1)) / cols
            val visible = surface.widgets.filter { it.visible }
            val maxRow = visible.maxOfOrNull { it.y + it.h } ?: 4
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RowH * maxRow + gap * (maxRow - 1).coerceAtLeast(0))
            ) {
                visible.forEach { pos ->
                    val spec = snap.widgets.firstOrNull { it.id == pos.id }
                    val xDp = (colW + gap) * pos.x
                    val yDp = (RowH + gap) * pos.y
                    val w = colW * pos.w + gap * (pos.w - 1).coerceAtLeast(0)
                    val h = RowH * pos.h + gap * (pos.h - 1).coerceAtLeast(0)
                    DashboardWidgetCard(
                        title = t(spec?.titleKey ?: pos.id, pos.id),
                        tracking = spec?.trackingRequired == true,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    with(density) { xDp.roundToPx() },
                                    with(density) { yDp.roundToPx() },
                                )
                            }
                            .width(w)
                            .height(h),
                    ) {
                        WidgetBody(
                            id = pos.id,
                            snap = snap,
                            quickIds = layout.quickActionIds,
                            tokenStore = tokenStore,
                            t = t,
                            onOpenSalesModal = onOpenSalesModal,
                            onOpenJourney = onOpenJourney,
                            onNavigateToGenerator = onNavigateToGenerator,
                            onNavigateToDesigns = onNavigateToDesigns,
                            onNavigateToProducts = onNavigateToProducts,
                            onNavigateToMarketingHero = onNavigateToMarketingHero,
                            onNavigateToAutomations = onNavigateToAutomations,
                            onNavigateToResearch = onNavigateToResearch,
                        )
                    }
                }
            }
        }
    }

    if (manager && snap != null) {
        ModalBottomSheet(
            onDismissRequest = { manager = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF0B1220),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                Text(t("creator.dashboard.layout_manager", "Dashboard layouts"), color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text(t("creator.dashboard.templates", "Templates"), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                snap.templates.forEach { (id, title) ->
                    Text(
                        title,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val oid = ownerId ?: return@clickable
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        api.mutateDashboardLayout(
                                            oid,
                                            JSONObject()
                                                .put("action", "create")
                                                .put("templateId", id)
                                                .put("title", title),
                                        )
                                    }
                                    state = withContext(Dispatchers.IO) { parseDashboardV5(api.getDashboardV5(oid)) }
                                }
                            }
                            .padding(vertical = 8.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(t("creator.dashboard.my_layouts", "My layouts"), color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                snap.layouts.forEach { lay ->
                    val active = lay.id == snap.activeLayoutId
                    Text(
                        lay.title + if (active) " · " + t("creator.dashboard.active", "Active") else "",
                        color = if (active) EazColors.Orange else Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val oid = ownerId ?: return@clickable
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        api.mutateDashboardLayout(
                                            oid,
                                            JSONObject().put("action", "set-active").put("id", lay.id),
                                        )
                                    }
                                    state = withContext(Dispatchers.IO) { parseDashboardV5(api.getDashboardV5(oid)) }
                                }
                            }
                            .padding(vertical = 8.dp),
                    )
                }
                val active = snap.layouts.firstOrNull { it.id == snap.activeLayoutId }
                if (active != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(t("creator.dashboard.quick_action_items", "Quick Actions items"), color = Color.White)
                    val options = listOf(
                        "generator" to t("creator.overview.action_generator_title", "Design Generator"),
                        "designs" to t("creator.overview.action_designs_title", "My Designs"),
                        "products" to t("creator.overview.action_products_title", "My Products"),
                        "content" to t("creator.overview.action_content_title", "Content Creation"),
                        "automations" to t("creator.overview.action_automations_title", "Automations"),
                        "research" to t("creator.research.nav", "Research"),
                    )
                    options.forEach { (id, label) ->
                        val on = id in draftQa
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    draftQa = if (on) draftQa.filter { it != id } else (draftQa + id).distinct()
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            Checkbox(
                                checked = on,
                                onCheckedChange = { checked ->
                                    draftQa = if (checked) (draftQa + id).distinct() else draftQa.filter { it != id }
                                },
                            )
                            Text(label, color = Color.White, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                Button(
                    onClick = {
                        val oid = ownerId
                        val activeLay = snap.layouts.firstOrNull { it.id == snap.activeLayoutId }
                        if (oid.isNullOrBlank() || activeLay == null) {
                            manager = false
                            return@Button
                        }
                        scope.launch {
                            val qaArr = JSONArray()
                            draftQa.forEach { qaArr.put(it) }
                            val settings = JSONObject().put(
                                "quick-actions",
                                JSONObject().put("visibleIds", qaArr),
                            )
                            withContext(Dispatchers.IO) {
                                api.mutateDashboardLayout(
                                    oid,
                                    JSONObject()
                                        .put("action", "update")
                                        .put("id", activeLay.id)
                                        .put("version", activeLay.version)
                                        .put("widgetSettings", settings),
                                )
                            }
                            state = withContext(Dispatchers.IO) { parseDashboardV5(api.getDashboardV5(oid)) }
                            manager = false
                        }
                    },
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text(t("creator.common.save", "Save"))
                }
            }
        }
    }
}

@Composable
private fun DashboardWidgetCard(
    title: String,
    tracking: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Glass)
            .border(1.dp, GlassBorder, shape)
            .padding(10.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("⋮⋮", color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
            Text(title, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
            if (tracking) {
                Spacer(Modifier.weight(1f))
                Text("Tracking", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun DualStat(aLabel: String, aValue: String, bLabel: String, bValue: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(aLabel, color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
            Text(aValue, color = Color.White, fontSize = 20.sp)
        }
        Column {
            Text(bLabel, color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp)
            Text(bValue, color = Color.White, fontSize = 20.sp)
        }
    }
}

@Composable
private fun WidgetBody(
    id: String,
    snap: DashboardV5State,
    quickIds: List<String>,
    tokenStore: SecureTokenStore,
    t: (String, String) -> String,
    onOpenSalesModal: () -> Unit,
    onOpenJourney: () -> Unit,
    onNavigateToGenerator: () -> Unit,
    onNavigateToDesigns: () -> Unit,
    onNavigateToProducts: () -> Unit,
    onNavigateToMarketingHero: () -> Unit,
    onNavigateToAutomations: () -> Unit,
    onNavigateToResearch: () -> Unit,
) {
    when (id) {
        "designs" -> DualStat(t("creator.overview.designs_generated", "Generated"), snap.designsGenerated, t("creator.overview.designs_uploaded", "Uploaded"), snap.designsUploaded, onNavigateToDesigns)
        "products" -> DualStat(t("creator.overview.products_online", "Online"), snap.productsOnline, t("creator.overview.products_offline", "Offline"), snap.productsOffline, onNavigateToProducts)
        "heroes" -> DualStat(t("creator.overview.designs_generated", "Generated"), snap.heroesGenerated, t("creator.overview.products_online", "Online"), snap.heroesOnline, onNavigateToMarketingHero)
        "sales" -> DualStat(t("creator.overview.sales_eazpire", "eazpire"), snap.salesEazpire, t("creator.overview.sales_amazon", "Amazon"), snap.salesAmazon, onOpenSalesModal)
        "creator-journey" -> CompactJourneyBody(
            ownerId = tokenStore.getOwnerId(),
            api = remember { CreatorApi(jwt = tokenStore.getJwt()) },
            t = t,
            onOpenJourney = onOpenJourney,
        )
        "quick-actions" -> {
            val actions = listOf(
                "generator" to (t("creator.overview.action_generator_title", "Design Generator") to onNavigateToGenerator),
                "designs" to (t("creator.overview.action_designs_title", "My Designs") to onNavigateToDesigns),
                "products" to (t("creator.overview.action_products_title", "My Products") to onNavigateToProducts),
                "content" to (t("creator.overview.action_content_title", "Content Creation") to onNavigateToMarketingHero),
                "automations" to (t("creator.overview.action_automations_title", "Automations") to onNavigateToAutomations),
                "research" to (t("creator.research.nav", "Research") to onNavigateToResearch),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                actions.filter { it.first in quickIds }.forEach { (_, pair) ->
                    Text(
                        pair.first,
                        color = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .clickable(onClick = pair.second)
                            .padding(12.dp),
                    )
                }
            }
        }
        "hero-impressions" -> Text(snap.heroImpressions ?: t("creator.dashboard.tracking_required", "Tracking required"), color = Color.White)
        "hero-clicks" -> Text(snap.heroClicks ?: t("creator.dashboard.tracking_required", "Tracking required"), color = Color.White)
        "performance" -> DualStat(t("creator.dashboard.impressions", "Impressions"), snap.heroImpressions ?: "–", t("creator.dashboard.clicks", "Clicks"), snap.heroClicks ?: "–")
        else -> Text(
            if (snap.tracking.values.any { !it } && snap.widgets.firstOrNull { it.id == id }?.trackingRequired == true) {
                t("creator.dashboard.tracking_required", "Tracking required")
            } else {
                t("creator.dashboard.status_from_screens", "Status from the matching Creator screen.")
            },
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun CompactJourneyBody(
    ownerId: String?,
    api: CreatorApi,
    t: (String, String) -> String,
    onOpenJourney: () -> Unit,
) {
    var lines by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(ownerId) {
        if (ownerId.isNullOrBlank()) {
            loading = false
            lines = emptyList()
            return@LaunchedEffect
        }
        loading = true
        lines = withContext(Dispatchers.IO) {
            try {
                val r = api.getOnboardingProgress(ownerId)
                if (!r.optBoolean("ok", false)) return@withContext emptyList()
                val completed = HashSet<String>()
                val doneArr = r.optJSONArray("completed_todos") ?: JSONArray()
                for (i in 0 until doneArr.length()) completed.add(doneArr.optString(i))
                val todos = r.optJSONArray("todos") ?: JSONArray()
                val open = ArrayList<String>()
                for (i in 0 until todos.length()) {
                    val obj = todos.optJSONObject(i) ?: continue
                    val id = obj.optString("id")
                    if (completed.contains(id) || obj.optBoolean("completed", false)) continue
                    val pres = obj.optJSONObject("presentation") ?: JSONObject()
                    val key = pres.optString("title_shopify_key")
                    val title = if (key.isNotBlank()) t(key, id) else id.replace("todo_", "").replace('_', ' ')
                    open.add(title)
                    if (open.size >= 3) break
                }
                open
            } catch (_: Exception) {
                emptyList()
            }
        }
        loading = false
    }
    Column {
        Text(
            t("creator.dashboard.open_all_quests", "Open all quests"),
            color = EazColors.Orange,
            modifier = Modifier.clickable(onClick = onOpenJourney),
        )
        if (loading) {
            Text(
                t("creator.overview.loading_todos", "Loading your journey..."),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else if (lines.isEmpty()) {
            Text(
                t("creator.overview.no_open_tasks", "You're all caught up!"),
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            lines.forEach { line ->
                Text(
                    line,
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

