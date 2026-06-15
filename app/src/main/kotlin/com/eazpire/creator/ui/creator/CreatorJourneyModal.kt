package com.eazpire.creator.ui.creator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.modal.EazFullScreenDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private data class JourneyTabItem(val label: String, val icon: ImageVector)

private data class JourneyNodeItem(
    val nodeKey: String,
    val category: String,
    val title: String,
    val cost: Double,
    val committed: Double,
    val minLevel: Int,
    val unlocked: Boolean,
    val lockedReason: String,
    val imageUrl: String? = null,
    val catalogIsActive: Int = 2,
)

private val JOURNEY_CATEGORY_ORDER = listOf(
    "product", "design_type", "market", "channel",
    "automation", "promotion", "hero", "social",
    "variant", "design_slot", "creator_name",
)

private const val EAZ_COIN_URL =
    "https://pub-2ffb11d4a361463498b9a842a87a870c.r2.dev/brand/coin/eaz-coin-logo.png"

private fun journeyCategoryIcon(category: String): ImageVector = when (category) {
    "eaz_economy" -> Icons.Default.AccountBalance
    "product" -> Icons.Default.ShoppingCart
    "design_type" -> Icons.Default.Layers
    "market" -> Icons.Default.Store
    "channel" -> Icons.Default.Tv
    "automation" -> Icons.Default.Bolt
    "promotion" -> Icons.Default.Star
    "hero" -> Icons.Default.Shield
    "social" -> Icons.Default.Groups
    "variant" -> Icons.Default.Apps
    "design_slot" -> Icons.Default.Image
    "creator_name" -> Icons.Default.Person
    else -> Icons.Default.Star
}

@Composable
fun CreatorJourneyModal(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    onDismiss: () -> Unit,
    initialTab: Int = 0,
    modifier: Modifier = Modifier,
) {
    val ownerId = tokenStore.getOwnerId()
    val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }
    val scope = rememberCoroutineScope()
    var currentTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    var drawerOpen by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var journeyData by remember { mutableStateOf<JSONObject?>(null) }
    var actionBusy by remember { mutableStateOf(false) }

    val tabs = listOf(
        JourneyTabItem(translationStore.t("creator.journey.nav_overview", "Overview"), Icons.Default.Home),
        JourneyTabItem(translationStore.t("creator.journey.nav_unlock_tree", "Unlock Tree"), Icons.Default.Star),
        JourneyTabItem(translationStore.t("creator.journey.nav_level", "Level"), Icons.Default.KeyboardArrowUp),
    )

    suspend fun reload() {
        if (ownerId.isNullOrBlank()) {
            journeyData = null
            loading = false
            return
        }
        loading = true
        try {
            journeyData = withContext(Dispatchers.IO) { api.getCreatorJourney(ownerId) }
        } catch (_: Exception) {
            journeyData = null
        } finally {
            loading = false
        }
    }

    LaunchedEffect(ownerId) { reload() }

    EazFullScreenDialog(onDismissRequest = onDismiss) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(Color(0xFF070B14)),
        ) {
            Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF070B14))
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { drawerOpen = true },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = translationStore.t("creator.settings.menu_open", "Open menu"),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = translationStore.t("creator.journey.title", "Creator Journey"),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color(0xFF0B1220)),
                ) {
                    if (loading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = EazColors.Orange)
                        }
                    } else {
                        when (currentTab) {
                            0 -> JourneyOverviewPanel(
                                data = journeyData,
                                translationStore = translationStore,
                                busy = actionBusy,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp),
                                onSaveStarter = { productKey, regionCode ->
                                    if (ownerId.isNullOrBlank()) return@JourneyOverviewPanel
                                    scope.launch {
                                        actionBusy = true
                                        try {
                                            withContext(Dispatchers.IO) {
                                                api.setStarterSelection(ownerId, productKey, regionCode)
                                            }
                                            reload()
                                        } finally {
                                            actionBusy = false
                                        }
                                    }
                                },
                            )
                            1 -> JourneyUnlockTreePanel(
                                data = journeyData,
                                ownerId = ownerId,
                                api = api,
                                translationStore = translationStore,
                                busy = actionBusy,
                                modifier = Modifier.fillMaxSize(),
                                onCommitRequest = { nodeKey, amount ->
                                    if (ownerId.isNullOrBlank()) return@JourneyUnlockTreePanel
                                    scope.launch {
                                        actionBusy = true
                                        try {
                                            withContext(Dispatchers.IO) {
                                                api.commitCreatorUnlock(ownerId, nodeKey, amount)
                                            }
                                            reload()
                                        } finally {
                                            actionBusy = false
                                        }
                                    }
                                },
                                onUnlock = { nodeKey ->
                                    if (ownerId.isNullOrBlank()) return@JourneyUnlockTreePanel
                                    scope.launch {
                                        actionBusy = true
                                        try {
                                            withContext(Dispatchers.IO) {
                                                api.unlockCreatorNode(ownerId, nodeKey)
                                            }
                                            reload()
                                        } finally {
                                            actionBusy = false
                                        }
                                    }
                                },
                            )
                            2 -> CreatorSettingsLevelPanel(ownerId.orEmpty(), api, translationStore)
                        }
                    }
                }
            }

            if (drawerOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { drawerOpen = false },
                )
            }

            AnimatedVisibility(
                visible = drawerOpen,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it }),
                modifier = Modifier.fillMaxHeight(),
            ) {
                JourneyNavDrawer(
                    tabs = tabs,
                    currentTab = currentTab,
                    journeyData = journeyData,
                    ownerId = ownerId,
                    api = api,
                    translationStore = translationStore,
                    onTabSelected = { index ->
                        currentTab = index
                        drawerOpen = false
                    },
                    onDismiss = { drawerOpen = false },
                )
            }
        }
    }
}

@Composable
private fun JourneyNavDrawer(
    tabs: List<JourneyTabItem>,
    currentTab: Int,
    journeyData: JSONObject?,
    ownerId: String?,
    api: CreatorApi,
    translationStore: TranslationStore,
    onTabSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val showBalance = journeyData?.optBoolean("is_creator", false) == true &&
        journeyData.has("balance_eaz")

    Row(modifier = Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight()
                .background(Color(0xFF0D1118))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { },
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(top = 16.dp, bottom = 8.dp),
            ) {
                tabs.forEachIndexed { index, tab ->
                    val isActive = index == currentTab
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isActive) {
                                    Brush.linearGradient(
                                        listOf(
                                            EazColors.Orange.copy(alpha = 0.18f),
                                            EazColors.Orange.copy(alpha = 0.08f),
                                        ),
                                    )
                                } else {
                                    Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
                                },
                            )
                            .border(
                                width = 1.dp,
                                color = if (isActive) EazColors.Orange.copy(alpha = 0.55f) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable { onTabSelected(index) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    if (isActive) EazColors.Orange.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f),
                                    RoundedCornerShape(8.dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                tab.icon,
                                contentDescription = null,
                                tint = if (isActive) EazColors.Orange else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            text = tab.label,
                            color = if (isActive) EazColors.Orange else Color.White.copy(alpha = 0.68f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            JourneySidebarLevelWidget(
                ownerId = ownerId,
                api = api,
                translationStore = translationStore,
            )

            if (showBalance) {
                val balanceValue = journeyData?.opt("balance_eaz")?.toString().orEmpty()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .border(1.dp, EazColors.Orange.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
                        .background(Color(0xFF0A0E14), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AsyncImage(
                        model = EAZ_COIN_URL,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Column {
                        Text(
                            text = translationStore.t("creator.journey.balance_your", "Your balance"),
                            color = Color.White.copy(alpha = 0.68f),
                            fontSize = 11.sp,
                        )
                        Text(
                            text = "$balanceValue EAZ",
                            color = EazColors.Orange,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onDismiss() },
        )
    }
}

@Composable
private fun JourneySidebarLevelWidget(
    ownerId: String?,
    api: CreatorApi,
    translationStore: TranslationStore,
) {
    fun t(key: String, fallback: String) = translationStore.t(key, fallback)
    fun tpl(key: String, fallback: String, vars: Map<String, String>): String {
        var s = t(key, fallback)
        vars.forEach { (k, v) -> s = s.replace("{{ $k }}", v).replace("{{$k}}", v) }
        return s
    }

    var visible by remember { mutableStateOf(false) }
    var levelNum by remember { mutableIntStateOf(1) }
    var xpText by remember { mutableStateOf("—") }
    var nextText by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0f) }

    LaunchedEffect(ownerId) {
        if (ownerId.isNullOrBlank()) {
            visible = false
            return@LaunchedEffect
        }
        try {
            val r = withContext(Dispatchers.IO) { api.getLevel(ownerId) }
            if (!r.optBoolean("ok", false)) {
                visible = false
                return@LaunchedEffect
            }
            val lv = r.optInt("current_level", r.optInt("level", 1))
            val totalXp = r.optInt("total_xp", 0)
            val thresholds = when {
                r.has("level_thresholds") && r.getJSONArray("level_thresholds").length() > 0 ->
                    r.getJSONArray("level_thresholds")
                r.has("thresholds") && r.getJSONArray("thresholds").length() > 0 ->
                    r.getJSONArray("thresholds")
                else -> null
            }
            fun xpAt(level: Int): Int {
                if (thresholds == null) return 0
                for (i in 0 until thresholds.length()) {
                    val row = thresholds.getJSONObject(i)
                    if (row.optInt("level", 0) == level) return row.optInt("xp_required", 0)
                }
                return 0
            }
            val curReq = xpAt(lv)
            val nextReq = xpAt(lv + 1).takeIf { it > curReq } ?: (totalXp + 1)
            val span = (nextReq - curReq).coerceAtLeast(1)
            progress = ((totalXp - curReq).toFloat() / span.toFloat()).coerceIn(0f, 1f)
            levelNum = lv
            xpText = tpl(
                "creator.journey.float_xp",
                "{{ current }} / {{ next }} XP",
                mapOf("current" to totalXp.toString(), "next" to nextReq.toString()),
            )
            val rem = (nextReq - totalXp).coerceAtLeast(0)
            nextText = if (rem > 0 && lv < 10) {
                tpl(
                    "creator.journey.float_next",
                    "Next level {{ n }} · {{ xp }} XP",
                    mapOf("n" to (lv + 1).toString(), "xp" to rem.toString()),
                )
            } else {
                null
            }
            visible = true
        } catch (_: Exception) {
            visible = false
        }
    }

    if (!visible) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .border(1.dp, EazColors.Orange.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .background(Color(0xFF0A0E14), RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier.size(92.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 8.dp.toPx()
                val pad = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(pad, pad)
                drawArc(
                    color = Color.White.copy(alpha = 0.12f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = EazColors.Orange,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = t("creator.journey.float_level", "Level"),
                    color = Color.White.copy(alpha = 0.68f),
                    fontSize = 9.sp,
                    letterSpacing = 0.8.sp,
                )
                Text(
                    text = levelNum.toString(),
                    color = EazColors.Orange,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
        Text(
            text = xpText,
            color = Color.White.copy(alpha = 0.68f),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
        )
        nextText?.let {
            Text(
                text = it,
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                lineHeight = 11.sp,
            )
        }
    }
}

@Composable
private fun JourneyOverviewPanel(
    data: JSONObject?,
    translationStore: TranslationStore,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onSaveStarter: (String, String) -> Unit,
) {
    fun t(key: String, fallback: String) = translationStore.t(key, fallback)
    Column(modifier = modifier) {
        if (data == null) {
            Text(t("creator.mobile.loading", "Loading…"), color = Color(0xFF9CA3AF))
            return@Column
        }

        val isCreator = data.optBoolean("is_creator", false)
        if (!isCreator) {
            Text(
                t("creator.journey.code_hint", "Redeem a Creator Code to unlock EAZ progression and the full tree."),
                color = Color(0xFF9CA3AF),
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        val starter = data.optJSONObject("starter")
        val selection = starter?.optJSONObject("selection")
        Text(
            t("creator.journey.starter_title", "Starter setup"),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        if (selection != null) {
            Text(
                "${selection.optString("product_key")} · ${selection.optString("region_code")}",
                color = EazColors.Orange,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            val keys = starter?.optJSONArray("product_keys") ?: JSONArray()
            var productKey by remember(keys) {
                mutableStateOf(if (keys.length() > 0) keys.optString(0) else "")
            }
            val nodes = data.optJSONArray("nodes") ?: JSONArray()
            val regions = remember(productKey, nodes) {
                buildList {
                    for (i in 0 until nodes.length()) {
                        val n = nodes.getJSONObject(i)
                        if (n.optString("category") == "market" && n.optString("product_key") == productKey) {
                            add(n.optString("region_code"))
                        }
                    }
                }.ifEmpty { listOf("EU") }
            }
            var regionCode by remember(productKey) { mutableStateOf(regions.firstOrNull() ?: "EU") }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                Text(t("creator.journey.starter_hint", "Choose your first product and region before publishing."), color = Color(0xFF9CA3AF))
                if (keys.length() == 0) {
                    Text(t("creator.journey.starter_empty", "No starter products configured"), color = Color(0xFF9CA3AF))
                } else {
                    Text(t("creator.journey.starter_product", "Starter product") + ": $productKey", color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (i in 0 until keys.length()) {
                            val k = keys.optString(i)
                            OutlinedButton(onClick = { productKey = k; regionCode = regions.firstOrNull() ?: "EU" }) {
                                Text(k, fontSize = 11.sp)
                            }
                        }
                    }
                    Text(t("creator.journey.starter_region", "Starter region") + ": $regionCode", color = Color.White)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        regions.forEach { rc ->
                            OutlinedButton(onClick = { regionCode = rc }) { Text(rc, fontSize = 11.sp) }
                        }
                    }
                    Button(
                        onClick = { if (productKey.isNotBlank()) onSaveStarter(productKey, regionCode) },
                        enabled = !busy && productKey.isNotBlank(),
                    ) {
                        Text(t("creator.journey.starter_save", "Save starter selection"))
                    }
                }
            }
        }

        if (isCreator && data.has("balance_eaz")) {
            Text(
                "${t("creator.journey.balance_label", "Available EAZ")}: ${data.opt("balance_eaz")}",
                color = Color(0xFF9CA3AF),
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun JourneyUnlockTreePanel(
    data: JSONObject?,
    ownerId: String?,
    api: CreatorApi,
    translationStore: TranslationStore,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onCommitRequest: (String, Double) -> Unit,
    onUnlock: (String) -> Unit,
) {
    fun t(key: String, fallback: String) = translationStore.t(key, fallback)
    fun tpl(key: String, fallback: String, vars: Map<String, String>): String {
        var s = t(key, fallback)
        vars.forEach { (k, v) -> s = s.replace("{{ $k }}", v).replace("{{$k}}", v) }
        return s
    }
    fun categoryLabel(cat: String): String =
        t("creator.journey.cat_$cat", cat.replace('_', ' '))

    val balance = data?.optDouble("balance_eaz", 0.0) ?: 0.0
    val isCreator = data?.optBoolean("is_creator", false) == true
    var commitTarget by remember { mutableStateOf<JourneyNodeItem?>(null) }
    var commitAmount by remember { mutableStateOf("") }

    val nodes = remember(data) {
        val arr = data?.optJSONArray("nodes") ?: JSONArray()
        buildList {
            for (i in 0 until arr.length()) {
                val n = arr.getJSONObject(i)
                val meta = n.optJSONObject("metadata")
                val title = meta?.optString("title")?.takeIf { it.isNotBlank() }
                    ?: n.optString("product_key").takeIf { it.isNotBlank() }
                    ?: n.optString("design_type").takeIf { it.isNotBlank() }
                    ?: n.optString("region_code").takeIf { it.isNotBlank() }
                    ?: n.optString("channel_id").takeIf { it.isNotBlank() }
                    ?: n.optString("node_key")
                add(
                    JourneyNodeItem(
                        nodeKey = n.optString("node_key"),
                        category = n.optString("category", "other"),
                        title = title,
                        cost = n.optDouble("cost_eaz", 0.0),
                        committed = n.optDouble("eaz_committed", 0.0),
                        minLevel = n.optInt("min_level", 2),
                        unlocked = n.optBoolean("unlocked", false),
                        lockedReason = n.optString("locked_reason", ""),
                        imageUrl = meta?.optString("image_url")?.takeIf { it.isNotBlank() },
                        catalogIsActive = meta?.optInt("catalog_is_active", 2) ?: 2,
                    )
                )
            }
        }
    }

    val filterCats = remember(nodes) {
        buildList {
            add("eaz_economy")
            addAll(JOURNEY_CATEGORY_ORDER.filter { cat -> nodes.any { it.category == cat } })
        }
    }
    var treeFilter by remember { mutableStateOf("eaz_economy") }

    LaunchedEffect(filterCats) {
        if (treeFilter !in filterCats) treeFilter = filterCats.firstOrNull() ?: "eaz_economy"
    }

    val filteredNodes = remember(nodes, treeFilter) {
        if (treeFilter == "eaz_economy") emptyList() else nodes.filter { it.category == treeFilter }
    }

    val levelRows = remember(filteredNodes) {
        filteredNodes
            .groupBy { it.minLevel }
            .toSortedMap()
            .map { (level, items) -> level to items }
    }

    Column(modifier = modifier) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            itemsIndexed(filterCats) { _, cat ->
                val selected = treeFilter == cat
                Column(
                    modifier = Modifier
                        .width(78.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(
                            1.dp,
                            if (selected) EazColors.Orange.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.12f),
                            RoundedCornerShape(14.dp),
                        )
                        .background(
                            if (selected) Color(0xFF141A24) else Color(0xFF080C12).copy(alpha = 0.72f),
                            RoundedCornerShape(14.dp),
                        )
                        .clickable { treeFilter = cat }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            journeyCategoryIcon(cat),
                            contentDescription = null,
                            tint = if (selected) EazColors.Orange else Color.White.copy(alpha = 0.68f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Text(
                        text = categoryLabel(cat),
                        color = if (selected) EazColors.Orange else Color.White.copy(alpha = 0.68f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 12.sp,
                    )
                }
            }
        }

        if (treeFilter == "eaz_economy" && !ownerId.isNullOrBlank()) {
            EazEconomySkillTreePanel(
                ownerId = ownerId,
                api = api,
                translationStore = translationStore,
                embedded = true,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
            )
        } else if (treeFilter == "product") {
            JourneyProductTreePanel(
                nodes = filteredNodes,
                data = data,
                displayLevel = data?.optInt("display_level", 1) ?: 1,
                isCreator = isCreator,
                busy = busy,
                translationStore = translationStore,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                onCommitClick = { node ->
                    commitTarget = node
                    commitAmount = if (balance > 0) {
                        String.format(java.util.Locale.US, "%.2f", balance)
                    } else ""
                },
                onUnlock = onUnlock,
            )
        } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(156.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
        ) {
            levelRows.forEach { (level, items) ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = tpl("creator.journey.level_row", "Level {{ n }}", mapOf("n" to level.toString())),
                        style = MaterialTheme.typography.titleSmall,
                        color = EazColors.Orange,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 4.dp),
                    )
                }
                items(items, key = { it.nodeKey }) { node ->
                    JourneyGridCard(
                        node = node,
                        isCreator = isCreator,
                        busy = busy,
                        translationStore = translationStore,
                        onCommitClick = {
                            commitTarget = node
                            commitAmount = if (balance > 0) {
                                String.format(java.util.Locale.US, "%.2f", balance)
                            } else ""
                        },
                        onUnlockClick = { onUnlock(node.nodeKey) },
                    )
                }
            }
        }
        }
    }

    commitTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { commitTarget = null },
            title = { Text(t("creator.journey.commit_modal_title", "Commit EAZ")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(t("creator.journey.commit_modal_hint", "How much EAZ do you want to allocate?"), fontSize = 13.sp)
                    Text(target.title, color = EazColors.Orange, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Text(
                        tpl("creator.journey.commit_modal_available", "Available: {{ amount }} EAZ", mapOf("amount" to balance.toString())),
                        fontSize = 12.sp,
                        color = Color(0xFF9CA3AF),
                    )
                    OutlinedTextField(
                        value = commitAmount,
                        onValueChange = { commitAmount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = commitAmount.toDoubleOrNull() ?: 0.0
                        if (amt > 0) {
                            onCommitRequest(target.nodeKey, amt)
                            commitTarget = null
                        }
                    },
                    enabled = !busy && (commitAmount.toDoubleOrNull() ?: 0.0) > 0,
                ) { Text(t("creator.journey.commit_confirm", "Confirm")) }
            },
            dismissButton = {
                OutlinedButton(onClick = { commitTarget = null }) {
                    Text(t("creator.journey.commit_cancel", "Cancel"))
                }
            },
            containerColor = Color(0xFF0B1220),
            titleContentColor = Color.White,
            textContentColor = Color(0xFFE5E7EB),
        )
    }
}

@Composable
private fun JourneyUnlockedStrip(
    nodes: List<JourneyNodeItem>,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier,
) {
    val unlocked = nodes.filter { it.unlocked }
    if (unlocked.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(unlocked, key = { it.nodeKey }) { node ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.width(72.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .border(2.dp, Color(0xFFFBBF24).copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.04f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!node.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = node.imageUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Text(
                    text = node.title,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun JourneyProductTreePanel(
    nodes: List<JourneyNodeItem>,
    data: JSONObject?,
    displayLevel: Int,
    isCreator: Boolean,
    busy: Boolean,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier,
    onCommitClick: (JourneyNodeItem) -> Unit,
    onUnlock: (String) -> Unit,
) {
    fun t(key: String, fallback: String) = translationStore.t(key, fallback)
    fun tpl(key: String, fallback: String, vars: Map<String, String>): String {
        var s = t(key, fallback)
        vars.forEach { (k, v) -> s = s.replace("{{ $k }}", v).replace("{{$k}}", v) }
        return s
    }
    val ps = data?.optJSONObject("product_sections")
    val previewLevel = ps?.optInt("preview_min_level", 3) ?: 3
    val premiumLevel = ps?.optInt("premium_min_level", 5) ?: 5
    val starter = nodes.filter { it.catalogIsActive == 2 }
    val preview = nodes.filter { it.catalogIsActive == 1 }
    val offline = nodes.filter { it.catalogIsActive == 0 }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            JourneyUnlockedStrip(
                nodes = nodes,
                translationStore = translationStore,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        if (starter.isNotEmpty()) {
            item {
                JourneyProductSection(
                    title = t("creator.journey.starter_products", "Starter Products"),
                    subtitle = null,
                    nodes = starter,
                    sectionLocked = false,
                    isCreator = isCreator,
                    busy = busy,
                    translationStore = translationStore,
                    onCommitClick = onCommitClick,
                    onUnlock = onUnlock,
                )
            }
        }
        if (preview.isNotEmpty()) {
            item {
                JourneyProductSection(
                    title = tpl("creator.journey.level_row", "Level {{ n }}", mapOf("n" to previewLevel.toString())),
                    subtitle = null,
                    nodes = preview,
                    sectionLocked = displayLevel < previewLevel,
                    isCreator = isCreator,
                    busy = busy,
                    translationStore = translationStore,
                    onCommitClick = onCommitClick,
                    onUnlock = onUnlock,
                )
            }
        }
        if (offline.isNotEmpty()) {
            item {
                JourneyProductSection(
                    title = tpl("creator.journey.level_row", "Level {{ n }}", mapOf("n" to premiumLevel.toString())),
                    subtitle = t("creator.journey.product_premium_hint", "Premium products available at this level"),
                    nodes = offline,
                    sectionLocked = displayLevel < premiumLevel,
                    isCreator = isCreator,
                    busy = busy,
                    translationStore = translationStore,
                    onCommitClick = onCommitClick,
                    onUnlock = onUnlock,
                )
            }
        }
        if (starter.isEmpty() && preview.isEmpty() && offline.isEmpty()) {
            item {
                Text(
                    t("creator.journey.starter_empty", "No items in this category yet."),
                    color = Color(0xFF9CA3AF),
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun JourneyProductSection(
    title: String,
    subtitle: String?,
    nodes: List<JourneyNodeItem>,
    sectionLocked: Boolean,
    isCreator: Boolean,
    busy: Boolean,
    translationStore: TranslationStore,
    onCommitClick: (JourneyNodeItem) -> Unit,
    onUnlock: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = title,
            color = EazColors.Orange,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = Color(0xFF9CA3AF),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        val rows = nodes.chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { node ->
                        JourneyProductCard(
                            node = node,
                            isCreator = isCreator,
                            busy = busy,
                            sectionLocked = sectionLocked,
                            translationStore = translationStore,
                            modifier = Modifier.weight(1f),
                            onCommitClick = { onCommitClick(node) },
                            onUnlockClick = { onUnlock(node.nodeKey) },
                        )
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun JourneyProductCard(
    node: JourneyNodeItem,
    isCreator: Boolean,
    busy: Boolean,
    sectionLocked: Boolean,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier,
    onCommitClick: () -> Unit,
    onUnlockClick: () -> Unit,
) {
    fun t(key: String, fallback: String) = translationStore.t(key, fallback)
    fun tpl(key: String, fallback: String, vars: Map<String, String>): String {
        var s = t(key, fallback)
        vars.forEach { (k, v) -> s = s.replace("{{ $k }}", v).replace("{{$k}}", v) }
        return s
    }
    val levelLocked = sectionLocked || node.lockedReason == "level_required"
    val canAct = !node.unlocked && isCreator && node.lockedReason.isEmpty() && !levelLocked && !sectionLocked
    val unlockReady = canAct && node.committed + 1e-9 >= node.cost
    val progress = if (node.cost > 0) (node.committed / node.cost).toFloat().coerceIn(0f, 1f) else if (node.unlocked) 1f else 0f
    val alpha = if (sectionLocked || levelLocked) 0.42f else 1f

    Column(
        modifier = modifier
            .alpha(alpha)
            .border(
                1.dp,
                if (node.unlocked) Color(0xFFFBBF24).copy(alpha = 0.75f) else Color.White.copy(alpha = 0.12f),
                RoundedCornerShape(14.dp),
            )
            .background(Color(0xFF111827), RoundedCornerShape(14.dp))
            .padding(bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
        ) {
            if (!node.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = node.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.linearGradient(listOf(Color(0xFF1F2937), Color(0xFF111827)))),
                )
            }
            if (node.unlocked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(22.dp)
                        .background(Color(0xFFFBBF24), RoundedCornerShape(999.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", color = Color(0xFF111827), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.72f)),
            ) {
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = EazColors.Orange,
                    trackColor = Color.White.copy(alpha = 0.12f),
                )
                Text(
                    text = tpl(
                        "creator.journey.eaz_badge",
                        "{{ committed }}/{{ cost }} EAZ",
                        mapOf(
                            "committed" to node.committed.toInt().toString(),
                            "cost" to node.cost.toInt().toString(),
                        ),
                    ),
                    color = EazColors.Orange,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                )
            }
        }
        Text(
            text = node.title,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .height(36.dp),
        )
        if (canAct && node.cost > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(
                    onClick = onCommitClick,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(t("creator.journey.commit_eaz", "Commit"), fontSize = 10.sp)
                }
                Button(
                    onClick = onUnlockClick,
                    enabled = !busy && unlockReady,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(t("creator.journey.unlock_short", "Unlock"), fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun JourneyGridCard(
    node: JourneyNodeItem,
    isCreator: Boolean,
    busy: Boolean,
    translationStore: TranslationStore,
    onCommitClick: () -> Unit,
    onUnlockClick: () -> Unit,
) {
    fun t(key: String, fallback: String) = translationStore.t(key, fallback)
    fun tpl(key: String, fallback: String, vars: Map<String, String>): String {
        var s = t(key, fallback)
        vars.forEach { (k, v) -> s = s.replace("{{ $k }}", v).replace("{{$k}}", v) }
        return s
    }

    val locked = !node.unlocked && node.lockedReason in listOf("level_required", "creator_code_required")
    val canAct = !node.unlocked && isCreator && node.lockedReason.isEmpty() && node.cost > 0
    val unlockReady = canAct && node.committed + 1e-9 >= node.cost
    val pulse = rememberInfiniteTransition(label = "unlockPulse")
    val unlockScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (unlockReady) 1.04f else 1f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "unlockScale",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (node.unlocked) Color(0xFF34D399).copy(alpha = 0.35f) else Color.White.copy(alpha = 0.1f),
                RoundedCornerShape(14.dp),
            )
            .background(Color(0xFF111827), RoundedCornerShape(14.dp))
            .then(if (locked) Modifier else Modifier)
            .padding(bottom = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(
                    Brush.linearGradient(listOf(Color(0xFF1F2937), Color(0xFF111827), Color(0xFF0B1220))),
                    RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.42f)
                    .aspectRatio(1f)
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp)),
            )
        }
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = node.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = tpl(
                        "creator.journey.eaz_badge",
                        "{{ committed }}/{{ cost }} EAZ",
                        mapOf(
                            "committed" to node.committed.toInt().toString(),
                            "cost" to node.cost.toInt().toString(),
                        ),
                    ),
                    fontSize = 10.sp,
                    color = EazColors.Orange,
                    modifier = Modifier
                        .background(EazColors.Orange.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Text(
                    text = tpl("creator.journey.level_badge", "Level {{ n }}", mapOf("n" to node.minLevel.toString())),
                    fontSize = 10.sp,
                    color = Color(0xFF9CA3AF),
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            if (node.unlocked) {
                Text(
                    t("creator.journey.unlocked", "Unlocked"),
                    fontSize = 10.sp,
                    color = Color(0xFF6EE7B7),
                )
            } else if (node.lockedReason == "creator_code_required") {
                Text(t("creator.journey.code_hint_short", "Creator Code required"), fontSize = 10.sp, color = Color(0xFF9CA3AF))
            } else if (node.lockedReason == "level_required") {
                Text(t("creator.journey.level_required", "Higher level required"), fontSize = 10.sp, color = Color(0xFF9CA3AF))
            }
            if (canAct) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedButton(onClick = onCommitClick, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Text(t("creator.journey.commit_eaz", "Commit"), fontSize = 11.sp)
                    }
                    Button(
                        onClick = onUnlockClick,
                        enabled = !busy && unlockReady,
                        modifier = Modifier
                            .weight(1f)
                            .scale(if (unlockReady) unlockScale else 1f),
                    ) {
                        Text(t("creator.journey.unlock_short", "Unlock"), fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
