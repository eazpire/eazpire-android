package com.eazpire.creator.ui.creator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
)

private val JOURNEY_CATEGORY_ORDER = listOf(
    "product", "design_type", "market", "channel",
    "automation", "promotion", "hero", "social",
    "variant", "design_slot", "creator_name",
)

private const val EAZ_COIN_URL =
    "https://pub-2ffb11d4a361463498b9a842a87a870c.r2.dev/brand/coin/eaz-coin-logo.png"

private fun journeyCategoryIcon(category: String): ImageVector = when (category) {
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
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { drawerOpen = true }) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = translationStore.t("creator.settings.menu_open", "Open menu"),
                            tint = Color.White,
                        )
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(
                            text = translationStore.t("creator.journey.title", "Creator Journey"),
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = tabs[currentTab].label,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9CA3AF),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
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
                    )
                )
            }
        }
    }

    val filterCats = remember(nodes) {
        JOURNEY_CATEGORY_ORDER.filter { cat -> nodes.any { it.category == cat } }
            .ifEmpty { listOf("product") }
    }
    var treeFilter by remember(filterCats) { mutableStateOf(filterCats.first()) }

    LaunchedEffect(filterCats) {
        if (treeFilter !in filterCats) treeFilter = filterCats.first()
    }

    val filteredNodes = remember(nodes, treeFilter) {
        nodes.filter { it.category == treeFilter }
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
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 12.sp,
                    )
                }
            }
        }

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
                OutlinedButton(onClick = onCommitClick, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(t("creator.journey.commit_eaz", "Commit"), fontSize = 11.sp)
                }
                Button(
                    onClick = onUnlockClick,
                    enabled = !busy && unlockReady,
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(if (unlockReady) unlockScale else 1f),
                ) {
                    Text(t("creator.journey.unlock_now", "Unlock now"), fontSize = 11.sp)
                }
            }
        }
    }
}
