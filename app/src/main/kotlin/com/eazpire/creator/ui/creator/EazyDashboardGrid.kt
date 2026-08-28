package com.eazpire.creator.ui.creator

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val Glass = Color(0xFF0B0E14).copy(alpha = 0.78f)
private val GlassBorder = Color(0xFFFF9D00).copy(alpha = 0.18f)
private val Chrome = Color(0xFF0D1118)
private val Accent = Color(0xFFFF9D00)
private val RowH = 56.dp
private const val WIDGET_LONG_PRESS_MS = 300L
private const val WIDGET_DRAG_SLOP_PX = 18f

private data class ManagerDraft(
    val layout: DashboardLayout,
    val qa: List<String>,
    val templateId: String?,
    val isNew: Boolean,
    val mode: String,
    val selectedWidgetId: String = "quick-actions",
    val previewingSystemDefault: Boolean = false,
)

private data class QuickActionSpec(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val onClick: (() -> Unit)? = null,
)

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
    var manager by remember { mutableStateOf(false) }
    var managerDraft by remember { mutableStateOf<ManagerDraft?>(null) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragPreview by remember { mutableStateOf<List<DashboardWidgetPos>?>(null) }
    var dragOrigin by remember { mutableStateOf<DashboardWidgetPos?>(null) }
    val scope = rememberCoroutineScope()
    val t: (String, String) -> String = { k, f -> translationStore.t(k, f) }

    fun openManagerSheet() {
        val snap = state ?: return
        val active = snap.layouts.firstOrNull { it.id == snap.activeLayoutId } ?: snap.layouts.firstOrNull() ?: return
        managerDraft = ManagerDraft(
            layout = active,
            qa = active.quickActionIds,
            templateId = null,
            isNew = false,
            mode = "layout",
        )
        manager = true
    }

    fun persistLayout(next: DashboardLayout) {
        val oid = ownerId ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                persistLayoutUpdate(api, oid, next)
            }
            state = withContext(Dispatchers.IO) { parseDashboardV5(api.getDashboardV5(oid)) }
        }
    }

    fun runLayoutListAction(body: JSONObject, selectId: String? = null) {
        val oid = ownerId ?: return
        scope.launch {
            val res = withContext(Dispatchers.IO) { api.mutateDashboardLayout(oid, body) }
            val next = withContext(Dispatchers.IO) { parseDashboardV5(api.getDashboardV5(oid)) }
            state = next
            val snap2 = next ?: return@launch
            val cur = managerDraft ?: return@launch
            val prefer = selectId ?: res.optJSONObject("layout")?.optString("id")?.takeIf { it.isNotBlank() }
            val pick = prefer?.let { id -> snap2.layouts.firstOrNull { it.id == id } }
                ?: snap2.layouts.firstOrNull { it.id == cur.layout.id }
                ?: snap2.layouts.firstOrNull()
            if (pick != null) {
                managerDraft = cur.copy(
                    layout = pick,
                    qa = pick.quickActionIds,
                    isNew = false,
                    previewingSystemDefault = false,
                    templateId = null,
                )
            }
        }
    }

    LaunchedEffect(ownerId) {
        state = withContext(Dispatchers.IO) {
            parseDashboardV5(api.getDashboardV5(ownerId ?: ""))
        }
    }

    val snap = state
    Column(modifier = modifier.fillMaxWidth()) {
        if (snap == null) {
            Text(t("creator.overview.loading", "Loading..."), color = Color.White.copy(alpha = 0.7f))
            return@Column
        }
        val layout = snap.layouts.firstOrNull { it.id == snap.activeLayoutId } ?: snap.layouts.firstOrNull()
        if (layout == null) return@Column
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val surfaceName = surfaceForWidth(maxWidth.value)
            val surface = layout.surfaceNamed(surfaceName)
            val density = LocalDensity.current
            val haptic = LocalHapticFeedback.current
            val cols = surface.columns.coerceAtLeast(1)
            val gap = 8.dp
            val colW = (maxWidth - gap * (cols - 1)) / cols
            val visible = (dragPreview ?: surface.widgets).filter { it.visible }
            val maxRow = visible.maxOfOrNull { it.y + it.h } ?: 4
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RowH * maxRow + gap * (maxRow - 1).coerceAtLeast(0))
            ) {
                visible.forEach { pos ->
                    val spec = snap.widgets.firstOrNull { it.id == pos.id }
                    val extra = if (dragPreview != null) Offset.Zero else if (dragId == pos.id) dragOffset else Offset.Zero
                    val xDp = (colW + gap) * pos.x
                    val yDp = (RowH + gap) * pos.y
                    val w = colW * pos.w + gap * (pos.w - 1).coerceAtLeast(0)
                    val h = RowH * pos.h + gap * (pos.h - 1).coerceAtLeast(0)
                        DashboardWidgetCard(
                        title = dashboardTitleCase(t(spec?.titleKey ?: pos.id, pos.id)),
                        tracking = spec?.trackingRequired == true,
                        customizeLabel = t("creator.dashboard.widget_customize", "Customize"),
                        hideLabel = t("creator.dashboard.hide_widget", "Hide"),
                        menuLabel = t("creator.dashboard.widget_menu", "Widget menu"),
                        moveLabel = t("creator.dashboard.move_widget", "Move widget"),
                        lifted = dragId == pos.id,
                        onCustomize = { openManagerSheet() },
                        onHide = {
                            val nextWidgets = surface.widgets.map { wdg ->
                                if (wdg.id == pos.id) wdg.copy(visible = false) else wdg
                            }
                            persistLayout(layout.withSurface(surfaceName, surface.copy(widgets = nextWidgets)))
                        },
                        handleModifier = Modifier.pointerInput(pos.id, colW, cols, layout.id, surfaceName, scope) {
                            val startWidgets = surface.widgets
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var armed = false
                                // PointerInputScope/AwaitPointerEventScope are not CoroutineScope in this
                                // Compose version — same pattern as EazyMascot (rememberCoroutineScope).
                                val lpJob = scope.launch {
                                    delay(WIDGET_LONG_PRESS_MS)
                                    armed = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragId = pos.id
                                    dragOffset = Offset.Zero
                                    dragOrigin = pos
                                    dragPreview = null
                                }
                                try {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                        val delta = change.position - down.position
                                        if (change.changedToUp() || !change.pressed) break
                                        if (!armed) {
                                            if (hypot(delta.x, delta.y) >= WIDGET_DRAG_SLOP_PX) {
                                                lpJob.cancel()
                                                return@awaitEachGesture
                                            }
                                        } else {
                                            change.consume()
                                            dragOffset = delta
                                            val origin = dragOrigin ?: pos
                                            val cellW = with(density) { (colW + gap).toPx() }
                                            val cellH = with(density) { (RowH + gap).toPx() }
                                            val dx = (delta.x / cellW).roundToInt()
                                            val dy = (delta.y / cellH).roundToInt()
                                            val tentative = startWidgets.map { wdg ->
                                                if (wdg.id != origin.id) wdg else wdg.copy(
                                                    x = (origin.x + dx).coerceAtLeast(0).coerceAtMost((cols - origin.w).coerceAtLeast(0)),
                                                    y = (origin.y + dy).coerceAtLeast(0),
                                                )
                                            }
                                            dragPreview = resolveWidgetCollisions(tentative, origin.id, cols)
                                        }
                                    }
                                    if (armed) {
                                        val origin = dragOrigin ?: pos
                                        val cellW = with(density) { (colW + gap).toPx() }
                                        val cellH = with(density) { (RowH + gap).toPx() }
                                        val dx = (dragOffset.x / cellW).roundToInt()
                                        val dy = (dragOffset.y / cellH).roundToInt()
                                        val resolved = dragPreview
                                        if (dx != 0 || dy != 0) {
                                            val nextWidgets = resolved ?: startWidgets.map { wdg ->
                                                if (wdg.id != origin.id) wdg else wdg.copy(
                                                    x = (origin.x + dx).coerceAtLeast(0).coerceAtMost((cols - origin.w).coerceAtLeast(0)),
                                                    y = (origin.y + dy).coerceAtLeast(0),
                                                )
                                            }.let { resolveWidgetCollisions(it, origin.id, cols) }
                                            persistLayout(layout.withSurface(surfaceName, surface.copy(widgets = nextWidgets)))
                                        }
                                    }
                                } finally {
                                    lpJob.cancel()
                                    dragId = null
                                    dragOffset = Offset.Zero
                                    dragPreview = null
                                    dragOrigin = null
                                }
                            }
                        },
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    with(density) { xDp.roundToPx() } + extra.x.roundToInt(),
                                    with(density) { yDp.roundToPx() } + extra.y.roundToInt(),
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

    val draft = managerDraft
    if (manager && snap != null && draft != null) {
        ModalBottomSheet(
            onDismissRequest = { manager = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = Color(0xFF0B0E14),
        ) {
            LayoutManagerSheet(
                snap = snap,
                draft = draft,
                t = t,
                onDraftChange = { managerDraft = it },
                onCancel = { manager = false },
                onSave = {
                    val oid = ownerId
                    if (oid.isNullOrBlank()) {
                        manager = false
                        return@LayoutManagerSheet
                    }
                    scope.launch {
                        val qaArr = JSONArray()
                        draft.qa.forEach { qaArr.put(it) }
                        val settings = JSONObject().put(
                            "quick-actions",
                            JSONObject().put("visibleIds", qaArr),
                        )
                        withContext(Dispatchers.IO) {
                            if (draft.isNew) {
                                api.mutateDashboardLayout(
                                    oid,
                                    JSONObject()
                                        .put("action", "create")
                                        .put("title", draft.layout.title)
                                        .put("templateId", draft.templateId ?: "mission-control")
                                        .put("setActive", true)
                                        .put("desktop", draft.layout.desktop.toJson())
                                        .put("tablet", draft.layout.tablet.toJson())
                                        .put("mobile", draft.layout.mobile.toJson())
                                        .put("widgetSettings", settings),
                                )
                            } else {
                                api.mutateDashboardLayout(
                                    oid,
                                    JSONObject()
                                        .put("action", "update")
                                        .put("id", draft.layout.id)
                                        .put("version", draft.layout.version)
                                        .put("desktop", draft.layout.desktop.toJson())
                                        .put("tablet", draft.layout.tablet.toJson())
                                        .put("mobile", draft.layout.mobile.toJson())
                                        .put("widgetSettings", settings),
                                )
                                api.mutateDashboardLayout(
                                    oid,
                                    JSONObject().put("action", "set-active").put("id", draft.layout.id),
                                )
                            }
                        }
                        state = withContext(Dispatchers.IO) { parseDashboardV5(api.getDashboardV5(oid)) }
                        manager = false
                    }
                },
                onRenameLayout = { lay, title ->
                    val qaArr = JSONArray()
                    lay.quickActionIds.forEach { qaArr.put(it) }
                    runLayoutListAction(
                        JSONObject()
                            .put("action", "update")
                            .put("id", lay.id)
                            .put("version", lay.version)
                            .put("title", title)
                            .put("desktop", lay.desktop.toJson())
                            .put("tablet", lay.tablet.toJson())
                            .put("mobile", lay.mobile.toJson())
                            .put("widgetSettings", JSONObject().put("quick-actions", JSONObject().put("visibleIds", qaArr))),
                        lay.id,
                    )
                },
                onDuplicateLayout = { lay ->
                    runLayoutListAction(JSONObject().put("action", "duplicate").put("id", lay.id))
                },
                onRemoveLayout = { lay ->
                    runLayoutListAction(JSONObject().put("action", "delete").put("id", lay.id))
                },
            )
        }
    }
}

private suspend fun persistLayoutUpdate(api: CreatorApi, ownerId: String, layout: DashboardLayout) {
    val qaArr = JSONArray()
    layout.quickActionIds.forEach { qaArr.put(it) }
    api.mutateDashboardLayout(
        ownerId,
        JSONObject()
            .put("action", "update")
            .put("id", layout.id)
            .put("version", layout.version)
            .put("desktop", layout.desktop.toJson())
            .put("tablet", layout.tablet.toJson())
            .put("mobile", layout.mobile.toJson())
            .put(
                "widgetSettings",
                JSONObject().put("quick-actions", JSONObject().put("visibleIds", qaArr)),
            ),
    )
}

@Composable
private fun DashboardWidgetCard(
    title: String,
    tracking: Boolean,
    customizeLabel: String,
    hideLabel: String,
    menuLabel: String,
    moveLabel: String,
    lifted: Boolean = false,
    onCustomize: () -> Unit,
    onHide: () -> Unit,
    handleModifier: Modifier,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = if (lifted) 1.04f else 1f
                scaleY = if (lifted) 1.04f else 1f
            }
            .clip(shape)
            .background(Glass)
            .border(1.dp, if (lifted) Accent.copy(alpha = 0.78f) else GlassBorder, shape)
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NineDotHandle(moveLabel, handleModifier, armed = lifted)
            Text(title, color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp).weight(1f))
            if (tracking) {
                Text(
                    "Tracking",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Outlined.MoreHoriz, contentDescription = menuLabel, tint = Color.White.copy(alpha = 0.7f))
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(customizeLabel, color = Accent) },
                        onClick = {
                            menuOpen = false
                            onCustomize()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(hideLabel, color = Color.White) },
                        onClick = {
                            menuOpen = false
                            onHide()
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun NineDotHandle(moveLabel: String, modifier: Modifier, armed: Boolean = false) {
    Canvas(
        modifier = modifier
            .size(28.dp)
            .semantics { contentDescription = moveLabel },
    ) {
        val step = size.minDimension / 4f
        val r = if (armed) 2.1.dp.toPx() else 1.6.dp.toPx()
        val color = if (armed) Accent else Color.White.copy(alpha = 0.55f)
        for (y in 0..2) {
            for (x in 0..2) {
                drawCircle(
                    color = color,
                    radius = r,
                    center = Offset(step * (x + 1), step * (y + 1)),
                )
            }
        }
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
                QuickActionSpec("generator", t("creator.overview.action_generator_title", "Design Generator"), Icons.Outlined.AutoAwesome, onNavigateToGenerator),
                QuickActionSpec("designs", t("creator.overview.action_designs_title", "My Designs"), Icons.Outlined.Palette, onNavigateToDesigns),
                QuickActionSpec("products", t("creator.overview.action_products_title", "My Products"), Icons.Outlined.Inventory2, onNavigateToProducts),
                QuickActionSpec("content", t("creator.overview.action_content_title", "Content Creation"), Icons.Outlined.Collections, onNavigateToMarketingHero),
                QuickActionSpec("automations", t("creator.overview.action_automations_title", "Automations"), Icons.Outlined.Bolt, onNavigateToAutomations),
                QuickActionSpec("research", t("creator.research.nav", "Research"), Icons.Outlined.Search, onNavigateToResearch),
            )
            QuickActionCarousel(items = actions.filter { it.id in quickIds }, selectedIds = quickIds, onToggle = null)
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

@Composable
private fun QuickActionCarousel(
    items: List<QuickActionSpec>,
    selectedIds: List<String>,
    onToggle: ((String) -> Unit)?,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = { it.id }) { item ->
            val on = item.id in selectedIds
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(76.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on && onToggle != null) Accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f))
                    .border(1.dp, if (on && onToggle != null) Accent else GlassBorder, RoundedCornerShape(12.dp))
                    .clickable {
                        if (onToggle != null) onToggle(item.id) else item.onClick?.invoke()
                    }
                    .padding(vertical = 8.dp, horizontal = 6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(item.icon, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                }
                Text(
                    item.label,
                    color = Color.White,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp),
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun LayoutManagerSheet(
    snap: DashboardV5State,
    draft: ManagerDraft,
    t: (String, String) -> String,
    onDraftChange: (ManagerDraft) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onRenameLayout: (DashboardLayout, String) -> Unit,
    onDuplicateLayout: (DashboardLayout) -> Unit,
    onRemoveLayout: (DashboardLayout) -> Unit,
) {
    val qaCatalog = listOf(
        QuickActionSpec("generator", t("creator.overview.action_generator_title", "Design Generator"), Icons.Outlined.AutoAwesome),
        QuickActionSpec("designs", t("creator.overview.action_designs_title", "My Designs"), Icons.Outlined.Palette),
        QuickActionSpec("products", t("creator.overview.action_products_title", "My Products"), Icons.Outlined.Inventory2),
        QuickActionSpec("content", t("creator.overview.action_content_title", "Content Creation"), Icons.Outlined.Collections),
        QuickActionSpec("automations", t("creator.overview.action_automations_title", "Automations"), Icons.Outlined.Bolt),
        QuickActionSpec("research", t("creator.research.nav", "Research"), Icons.Outlined.Search),
    )
    val widgetIds = linkedSetOf<String>().apply {
        (draft.layout.desktop.widgets + draft.layout.tablet.widgets + draft.layout.mobile.widgets).forEach { add(it.id) }
        if (isEmpty()) snap.widgets.forEach { add(it.id) }
    }.toList()
    val selectedWidget = draft.selectedWidgetId.takeIf { it in widgetIds }
        ?: widgetIds.firstOrNull { it == "quick-actions" }
        ?: widgetIds.firstOrNull().orEmpty()
    var menuForId by remember { mutableStateOf<String?>(null) }
    var renameFor by remember { mutableStateOf<DashboardLayout?>(null) }
    var renameText by remember { mutableStateOf("") }
    val canRemove = snap.layouts.size > 1
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 640.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Chrome)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(t("creator.dashboard.customize", "Customize dashboard"), color = Color.White, modifier = Modifier.weight(1f))
        }
        if (draft.mode == "layout") {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(snap.templates, key = { it.id }) { tpl ->
                    val on = tpl.id == draft.templateId
                    Text(
                        tpl.title,
                        color = if (on) Accent else Color.White.copy(alpha = 0.68f),
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .border(1.dp, if (on) Accent else GlassBorder, RoundedCornerShape(999.dp))
                            .background(if (on) Accent.copy(alpha = 0.16f) else Color.Transparent)
                            .clickable {
                                onDraftChange(
                                    draft.copy(
                                        templateId = tpl.id,
                                        previewingSystemDefault = false,
                                        layout = draft.layout.copy(
                                            desktop = tpl.desktop,
                                            tablet = tpl.tablet,
                                            mobile = tpl.mobile,
                                        ),
                                    ),
                                )
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
        ) {
            Column(
                modifier = Modifier
                    .width(168.dp)
                    .fillMaxHeight()
                    .background(Chrome),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { onDraftChange(draft.copy(mode = "layout")) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            t("creator.dashboard.layout_tab", "Layout"),
                            color = if (draft.mode == "layout") Accent else Color.White.copy(alpha = 0.68f),
                            fontSize = 13.sp,
                        )
                    }
                    TextButton(
                        onClick = { onDraftChange(draft.copy(mode = "widgets")) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            t("creator.dashboard.widget_settings_tab", "Widgets"),
                            color = if (draft.mode == "widgets") Accent else Color.White.copy(alpha = 0.68f),
                            fontSize = 13.sp,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (draft.mode == "widgets") {
                        widgetIds.forEach { id ->
                            val spec = snap.widgets.firstOrNull { it.id == id }
                            val label = dashboardTitleCase(t(spec?.titleKey ?: id, id))
                            val on = id == selectedWidget
                            Text(
                                label,
                                color = if (on) Accent else Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onDraftChange(draft.copy(selectedWidgetId = id)) }
                                    .background(if (on) Accent.copy(alpha = 0.12f) else Color.Transparent)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    } else {
                        Text(
                            t("creator.dashboard.system_layouts", "System"),
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                        Text(
                            t("creator.dashboard.default_layout", "Default"),
                            color = if (draft.previewingSystemDefault) Accent else Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val seed = snap.templates.firstOrNull { it.id == "mission-control" }
                                        ?: snap.templates.firstOrNull()
                                    if (seed == null) return@clickable
                                    onDraftChange(
                                        draft.copy(
                                            templateId = seed.id,
                                            previewingSystemDefault = true,
                                            layout = draft.layout.copy(
                                                desktop = seed.desktop,
                                                tablet = seed.tablet,
                                                mobile = seed.mobile,
                                            ),
                                        ),
                                    )
                                }
                                .background(if (draft.previewingSystemDefault) Accent.copy(alpha = 0.12f) else Color.Transparent)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                        Text(
                            t("creator.dashboard.my_layouts", "My layouts"),
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                        snap.layouts.forEach { lay ->
                            val on = !draft.isNew && !draft.previewingSystemDefault && lay.id == draft.layout.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (on) Accent.copy(alpha = 0.12f) else Color.Transparent),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    lay.title + if (lay.id == snap.activeLayoutId) " · " + t("creator.dashboard.active", "Active") else "",
                                    color = if (on) Accent else Color.White,
                                    fontSize = 13.sp,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            onDraftChange(
                                                ManagerDraft(
                                                    layout = lay,
                                                    qa = lay.quickActionIds,
                                                    templateId = null,
                                                    isNew = false,
                                                    mode = draft.mode,
                                                    selectedWidgetId = draft.selectedWidgetId,
                                                    previewingSystemDefault = false,
                                                ),
                                            )
                                        }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                )
                                Box {
                                    IconButton(
                                        onClick = { menuForId = lay.id },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            Icons.Outlined.MoreHoriz,
                                            contentDescription = t("creator.dashboard.layout_menu", "Layout menu"),
                                            tint = Color.White.copy(alpha = 0.7f),
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = menuForId == lay.id,
                                        onDismissRequest = { menuForId = null },
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(t("creator.dashboard.edit", "Edit"), color = Color.White) },
                                            onClick = {
                                                menuForId = null
                                                renameFor = lay
                                                renameText = lay.title
                                            },
                                        )
                                        DropdownMenuItem(
                                            text = { Text(t("creator.dashboard.duplicate", "Duplicate"), color = Color.White) },
                                            onClick = {
                                                menuForId = null
                                                onDuplicateLayout(lay)
                                            },
                                        )
                                        DropdownMenuItem(
                                            enabled = canRemove,
                                            text = { Text(t("creator.dashboard.remove", "Remove"), color = Color.White) },
                                            onClick = {
                                                menuForId = null
                                                if (canRemove) onRemoveLayout(lay)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                        if (draft.isNew) {
                            Text(
                                draft.layout.title,
                                color = Accent,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Accent.copy(alpha = 0.12f))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
                if (draft.mode == "layout") {
                    TextButton(
                        onClick = {
                            val seed = snap.templates.firstOrNull { it.id == (draft.templateId ?: "mission-control") }
                                ?: snap.templates.firstOrNull()
                            if (seed == null) return@TextButton
                            onDraftChange(
                                ManagerDraft(
                                    layout = DashboardLayout(
                                        id = "__draft__",
                                        title = t("creator.dashboard.new_layout", "New layout"),
                                        description = seed.description,
                                        version = 1,
                                        desktop = seed.desktop,
                                        tablet = seed.tablet,
                                        mobile = seed.mobile,
                                        quickActionIds = draft.qa,
                                    ),
                                    qa = draft.qa,
                                    templateId = seed.id,
                                    isNew = true,
                                    mode = draft.mode,
                                    selectedWidgetId = draft.selectedWidgetId,
                                    previewingSystemDefault = false,
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("+ " + t("creator.dashboard.new_layout", "New layout"), color = Accent)
                    }
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                if (draft.mode == "widgets") {
                    if (selectedWidget == "quick-actions") {
                        Text(t("creator.dashboard.quick_action_items", "Quick Actions items"), color = Color.White, fontSize = 12.sp)
                        Spacer(Modifier.height(8.dp))
                        QuickActionCarousel(
                            items = qaCatalog,
                            selectedIds = draft.qa,
                            onToggle = { id ->
                                val next = if (id in draft.qa) draft.qa.filter { it != id } else (draft.qa + id).distinct()
                                onDraftChange(draft.copy(qa = next))
                            },
                        )
                    } else {
                        val spec = snap.widgets.firstOrNull { it.id == selectedWidget }
                        Text(
                            dashboardTitleCase(t(spec?.titleKey ?: selectedWidget, selectedWidget)),
                            color = Color.White,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            t("creator.dashboard.widget_settings_placeholder", "Settings for this widget come later."),
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = 13.sp,
                        )
                    }
                } else {
                    Text(
                        if (draft.previewingSystemDefault) t("creator.dashboard.default_layout", "Default") else draft.layout.title,
                        color = Color.White,
                    )
                    if (draft.layout.description.isNotBlank()) {
                        Text(draft.layout.description, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    MiniLayoutPreview(draft.layout.desktop)
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Chrome)
                .padding(10.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onCancel) {
                Text(t("creator.common.cancel", "Cancel"), color = Color.White)
            }
            Button(onClick = onSave, modifier = Modifier.padding(start = 8.dp)) {
                Text(t("creator.common.save", "Save"))
            }
        }
    }
    val renameTarget = renameFor
    if (renameTarget != null) {
        AlertDialog(
            onDismissRequest = { renameFor = null },
            title = { Text(t("creator.dashboard.edit", "Edit")) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text(t("creator.dashboard.layout_title", "Layout title")) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val next = renameText.trim()
                        renameFor = null
                        if (next.isNotEmpty()) onRenameLayout(renameTarget, next)
                    },
                ) {
                    Text(t("creator.common.save", "Save"))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameFor = null }) {
                    Text(t("creator.common.cancel", "Cancel"))
                }
            },
        )
    }
}

@Composable
private fun MiniLayoutPreview(surface: DashboardSurface) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val cols = surface.columns.coerceAtLeast(1)
        val gap = 6.dp
        val colW = (maxWidth - gap * (cols - 1)) / cols
        val rowH = 28.dp
        val visible = surface.widgets.filter { it.visible }
        val maxRow = visible.maxOfOrNull { it.y + it.h } ?: 2
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowH * maxRow + gap * (maxRow - 1).coerceAtLeast(0))
        ) {
            val density = LocalDensity.current
            visible.forEach { pos ->
                val xDp = (colW + gap) * pos.x
                val yDp = (rowH + gap) * pos.y
                val w = colW * pos.w + gap * (pos.w - 1).coerceAtLeast(0)
                val h = rowH * pos.h + gap * (pos.h - 1).coerceAtLeast(0)
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                with(density) { xDp.roundToPx() },
                                with(density) { yDp.roundToPx() },
                            )
                        }
                        .width(w)
                        .height(h)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, GlassBorder, RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .padding(6.dp),
                ) {
                    Text(pos.id, color = Color.White.copy(alpha = 0.55f), fontSize = 10.sp)
                }
            }
        }
    }
}
