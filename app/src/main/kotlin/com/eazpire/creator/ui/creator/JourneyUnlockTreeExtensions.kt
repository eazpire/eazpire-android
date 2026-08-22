package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors
import com.eazpire.creator.i18n.TranslationStore
import org.json.JSONArray
import org.json.JSONObject

internal val JOURNEY_TREE_TAB_ORDER = listOf(
    "royalty",
    "eaz_economy",
    "product", "design_type", "creation_limit", "market", "channel", "listing_limit",
    "automation", "promotion", "hero", "social",
    "variant", "design_slot", "creator_name",
)

internal data class JourneyNodeItem(
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
    val metadata: JSONObject? = null,
    val productKey: String = "",
    val designType: String = "",
    val regionCode: String = "",
    val channelId: String = "",
    val slotIndex: Int = 0,
    val parentKey: String = "",
    val freePickEligible: Boolean = false,
    val socialPlatform: String = "",
)

internal class JourneyExpandState {
    var creationLimitParent by mutableStateOf<String?>(null)
    var listingLimitChannel by mutableStateOf<String?>(null)
    var designSlotLevel by mutableStateOf<String?>(null)
    var socialPlatform by mutableStateOf<String?>(null)

    fun toggleCreationLimit(key: String) {
        creationLimitParent = if (creationLimitParent == key) null else key
    }

    fun toggleListingLimit(key: String) {
        listingLimitChannel = if (listingLimitChannel == key) null else key
    }

    fun toggleDesignSlotLevel(key: String) {
        designSlotLevel = if (designSlotLevel == key) null else key
    }

    fun toggleSocialPlatform(key: String) {
        socialPlatform = if (socialPlatform == key) null else key
    }
}

internal fun journeyCategoryLabel(cat: String, translationStore: TranslationStore): String {
    val fallbacks = mapOf(
        "creation_limit" to "Creation Limits",
        "listing_limit" to "Listing Limits",
        "design_type" to "Design types",
        "eaz_economy" to "EAZV Economy",
        "design_slot" to "Design slots",
        "creator_name" to "Creator names",
        "automation" to "Automations",
        "promotion" to "Promotions",
        "hero" to "Hero studio",
    )
    val fb = fallbacks[cat] ?: cat.replace('_', ' ').replaceFirstChar { it.uppercase() }
    return translationStore.t("creator.journey.cat_$cat", fb)
}

internal fun journeyCategoryIcon(category: String): ImageVector = when (category) {
    "eaz_economy", "royalty" -> Icons.Default.AccountBalance
    "product" -> Icons.Default.ShoppingCart
    "design_type" -> Icons.Default.Layers
    "creation_limit" -> Icons.Default.FileUpload
    "listing_limit" -> Icons.Default.Storefront
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

internal fun parseJourneyNodes(data: JSONObject?): List<JourneyNodeItem> {
    val arr = data?.optJSONArray("nodes") ?: JSONArray()
    return buildList {
        for (i in 0 until arr.length()) {
            val n = arr.getJSONObject(i)
            val meta = n.optJSONObject("metadata")
            add(parseJourneyNode(n, meta))
        }
    }
}

private fun parseJourneyNode(n: JSONObject, meta: JSONObject?): JourneyNodeItem {
    val category = n.optString("category", "other")
    val title = buildJourneyNodeTitle(n, meta, category)
    return JourneyNodeItem(
        nodeKey = n.optString("node_key"),
        category = category,
        title = title,
        cost = n.optDouble("cost_eaz", 0.0),
        committed = n.optDouble("eaz_committed", 0.0),
        minLevel = n.optInt("min_level", 2),
        unlocked = n.optBoolean("unlocked", false),
        lockedReason = n.optString("locked_reason", ""),
        imageUrl = meta?.optString("image_url")?.takeIf { it.isNotBlank() },
        catalogIsActive = meta?.optInt("catalog_is_active", 2) ?: 2,
        metadata = meta,
        productKey = n.optString("product_key"),
        designType = n.optString("design_type"),
        regionCode = n.optString("region_code"),
        channelId = n.optString("channel_id"),
        slotIndex = n.optInt("slot_index", 0),
        parentKey = n.optString("parent_key"),
        freePickEligible = n.optBoolean("free_pick_eligible", false),
        socialPlatform = n.optString("social_platform").ifBlank {
            meta?.optString("social_platform").orEmpty()
        },
    )
}

/** Softstyle tee — color → size drill-down — is always a Starter product, regardless of admin config. */
internal const val JOURNEY_SOFTSTYLE_PRODUCT_KEY = "unisex-softstyle-cotton-tee"

internal fun isJourneyStarterProductNode(node: JourneyNodeItem, data: JSONObject?): Boolean {
    if (node.productKey == JOURNEY_SOFTSTYLE_PRODUCT_KEY) return true
    val meta = node.metadata
    if (meta?.has("journey_starter") == true) {
        return meta.optBoolean("journey_starter", false)
    }
    val starterKeys = data?.optJSONObject("starter")?.optJSONArray("product_keys")
    if (starterKeys != null && starterKeys.length() > 0) {
        val pk = node.productKey
        for (i in 0 until starterKeys.length()) {
            if (starterKeys.optString(i) == pk) return true
        }
        return false
    }
    return node.catalogIsActive == 2
}

/** True once the owner used their free starter pick — a saved selection or any unlocked starter product. */
internal fun ownerHasStarterPick(nodes: List<JourneyNodeItem>, data: JSONObject?): Boolean {
    val selection = data?.optJSONObject("starter")?.optJSONObject("selection")
    if (selection != null && selection.optString("product_key").isNotBlank()) return true
    return nodes.any { isJourneyStarterProductNode(it, data) && it.unlocked }
}

private fun buildJourneyNodeTitle(n: JSONObject, meta: JSONObject?, category: String): String {
    if (meta?.has("royalty_percent") == true) {
        return "${meta.optInt("royalty_percent")}% royalty"
    }
    if (meta?.optString("creation_limit_kind") == "parent") {
        return if (meta.optString("creation_limit_axis") == "upload") "Upload" else "Generate"
    }
    if (meta?.optString("listing_limit_kind") == "channel" ||
        meta?.optString("listing_limit_kind") == "axis"
    ) {
        return meta.optString("title").ifBlank { n.optString("channel_id") }
    }
    if (meta?.optString("social_post_limit_kind") == "platform") {
        return meta.optString("title").ifBlank {
            n.optString("social_platform").ifBlank { n.optString("node_key") }
        }
    }
    if (isDesignSlotLevelNode(category, n.optString("node_key"), meta)) {
        val lv = designSlotLevelFromNode(n.optString("node_key"), meta)
        return "Level $lv"
    }
    if (meta?.optString("title")?.isNotBlank() == true) return meta.optString("title")
    if (n.optString("product_key").isNotBlank()) return n.optString("product_key")
    if (n.optString("design_type").isNotBlank()) return n.optString("design_type")
    if (n.optString("region_code").isNotBlank()) return n.optString("region_code")
    if (n.optString("channel_id").isNotBlank()) return n.optString("channel_id")
    if (n.optInt("slot_index", 0) > 0) return "Slot ${n.optInt("slot_index")}"
    return n.optString("node_key")
}

internal fun isCreationLimitParent(node: JourneyNodeItem): Boolean =
    node.category == "creation_limit" && node.metadata?.optString("creation_limit_kind") == "parent"

internal fun isListingLimitChannel(node: JourneyNodeItem): Boolean =
    node.category == "listing_limit" && node.metadata?.optString("listing_limit_kind") == "channel"

internal fun isListingLimitAxisParent(node: JourneyNodeItem): Boolean =
    node.category == "listing_limit" && node.metadata?.optString("listing_limit_kind") == "axis"

internal fun isSocialPlatformNode(node: JourneyNodeItem): Boolean =
    node.category == "social" &&
        node.parentKey.isBlank() &&
        node.metadata?.optString("social_post_limit_kind") != "tier"

internal fun isDesignSlotLevelNode(node: JourneyNodeItem): Boolean =
    isDesignSlotLevelNode(node.category, node.nodeKey, node.metadata)

private fun isDesignSlotLevelNode(category: String, nodeKey: String, meta: JSONObject?): Boolean {
    if (category != "design_slot") return false
    if (meta?.optString("design_slot_kind") == "level") return true
    return nodeKey.startsWith("design_slot_level:")
}

internal fun isDesignSlotChildNode(node: JourneyNodeItem): Boolean {
    if (node.category != "design_slot") return false
    if (isDesignSlotLevelNode(node)) return false
    return node.slotIndex > 0 || node.nodeKey.startsWith("design_slot:")
}

internal fun designSlotLevelFromNode(node: JourneyNodeItem): Int =
    designSlotLevelFromNode(node.nodeKey, node.metadata)

private fun designSlotLevelFromNode(nodeKey: String, meta: JSONObject?): Int {
    meta?.optInt("design_slot_level", 0)?.takeIf { it > 0 }?.let { return it }
    if (nodeKey.startsWith("design_slot_level:")) {
        return nodeKey.removePrefix("design_slot_level:").toIntOrNull() ?: 1
    }
    return 1
}

internal fun creationLimitTierNodes(all: List<JourneyNodeItem>, parent: JourneyNodeItem): List<JourneyNodeItem> {
    val axis = parent.metadata?.optString("creation_limit_axis") ?: return emptyList()
    return all.filter {
        it.category == "creation_limit" &&
            it.metadata?.optString("creation_limit_kind") == "tier" &&
            it.metadata.optString("creation_limit_axis") == axis
    }.sortedBy { it.metadata?.optInt("creation_limit_tier", 0) ?: 0 }
}

internal fun listingLimitTierNodes(all: List<JourneyNodeItem>, channel: JourneyNodeItem): List<JourneyNodeItem> {
    val ch = channel.channelId.ifBlank { channel.metadata?.optString("channel_id") }.orEmpty()
    if (ch.isBlank()) return emptyList()
    val axis = channel.metadata?.optString("listing_limit_axis").orEmpty()
    return all.filter {
        if (it.category != "listing_limit") return@filter false
        if (it.metadata?.optString("listing_limit_kind") != "tier") return@filter false
        val nCh = it.channelId.ifBlank { it.metadata?.optString("channel_id") }.orEmpty()
        if (nCh != ch) return@filter false
        if (axis.isNotBlank()) {
            val tierAxis = it.metadata?.optString("listing_limit_axis").orEmpty()
            if (tierAxis.isNotBlank() && tierAxis != axis) return@filter false
        }
        true
    }.sortedBy { it.metadata?.optInt("listing_tier_level", 0) ?: 0 }
}

internal fun listingLimitTiersForChannelAxis(
    all: List<JourneyNodeItem>,
    channel: JourneyNodeItem,
    axis: String,
): List<JourneyNodeItem> {
    val ch = channel.channelId.ifBlank { channel.metadata?.optString("channel_id") }.orEmpty()
    if (ch.isBlank()) return emptyList()
    return all.filter {
        it.category == "listing_limit" &&
            it.metadata?.optString("listing_limit_kind") == "tier" &&
            it.channelId.ifBlank { it.metadata?.optString("channel_id") }.orEmpty() == ch &&
            (it.metadata?.optString("listing_limit_axis").orEmpty().ifBlank { "daily" } == axis)
    }.sortedBy { it.metadata?.optInt("listing_tier_level", 0) ?: 0 }
}

internal fun listingLimitChannelNodes(nodes: List<JourneyNodeItem>): List<JourneyNodeItem> {
    val channels = nodes.filter { isListingLimitChannel(it) }
    if (channels.isNotEmpty()) return channels
    val seen = mutableSetOf<String>()
    return nodes.mapNotNull { node ->
        if (!isListingLimitAxisParent(node)) return@mapNotNull null
        val ch = node.channelId
        if (ch.isBlank() || !seen.add(ch)) return@mapNotNull null
        node
    }
}

internal fun socialPlatformId(node: JourneyNodeItem): String {
    if (node.socialPlatform.isNotBlank()) return node.socialPlatform
    val fromMeta = node.metadata?.optString("social_platform").orEmpty()
    if (fromMeta.isNotBlank()) return fromMeta
    val key = node.nodeKey
    if (key.startsWith("social:")) {
        return key.removePrefix("social:").substringBefore(":").ifBlank { "" }
    }
    return ""
}

internal fun socialPostTierNodes(all: List<JourneyNodeItem>, platform: JourneyNodeItem): List<JourneyNodeItem> {
    val plat = socialPlatformId(platform)
    if (plat.isBlank()) return emptyList()
    return all.filter {
        it.category == "social" &&
            it.metadata?.optString("social_post_limit_kind") == "tier" &&
            socialPlatformId(it) == plat
    }.sortedBy { it.metadata?.optInt("social_tier_level", 0) ?: 0 }
}

internal fun designSlotChildren(all: List<JourneyNodeItem>, levelNode: JourneyNodeItem): List<JourneyNodeItem> =
    all.filter { isDesignSlotChildNode(it) && it.parentKey == levelNode.nodeKey }
        .sortedBy { it.slotIndex }

internal fun journeySkillIcon(node: JourneyNodeItem): ImageVector? {
    if (!node.imageUrl.isNullOrBlank()) return null
    if (node.category == "market") return null
    when (node.category) {
        "royalty" -> return Icons.Default.Star
        "creation_limit" -> {
            val axis = node.metadata?.optString("creation_limit_axis")
            return if (axis == "upload") Icons.Default.FileUpload else Icons.Default.AutoAwesome
        }
        "listing_limit" -> return listingChannelIcon(node.channelId)
        "design_slot" -> {
            return if (isDesignSlotLevelNode(node)) Icons.Default.Layers else Icons.Default.Image
        }
        "product" -> return Icons.Default.ShoppingCart
        "design_type" -> return Icons.Default.Layers
        "channel" -> return listingChannelIcon(node.channelId)
        "automation" -> return Icons.Default.Bolt
        "promotion" -> return Icons.Default.Star
        "hero" -> return Icons.Default.Shield
        "social" -> return Icons.Default.Groups
        "creator_name" -> return Icons.Default.Person
        "variant" -> return Icons.Default.Apps
    }
    return journeyCategoryIcon(node.category)
}

private fun listingChannelIcon(channelId: String): ImageVector = when (channelId.lowercase()) {
    "shopify" -> Icons.Default.ShoppingCart
    "amazon", "amazon_eu", "amazon_us", "amazon_uk" -> Icons.Default.Store
    "ebay", "etsy" -> Icons.Default.Storefront
    else -> Icons.Default.Storefront
}

internal fun journeyParentLimitLabel(
    node: JourneyNodeItem,
    allNodes: List<JourneyNodeItem>,
    translationStore: TranslationStore,
): String? {
    fun tpl(key: String, fallback: String, vars: Map<String, String>): String {
        var s = translationStore.t(key, fallback)
        vars.forEach { (k, v) -> s = s.replace("{{ $k }}", v).replace("{{$k}}", v) }
        return s
    }
    if (isCreationLimitParent(node)) {
        val active = creationLimitTierNodes(allNodes, node).lastOrNull { it.unlocked }
        val valN = active?.metadata?.optInt("limit_value") ?: return null
        return if (active.metadata?.optString("limit_mode") == "lifetime") {
            tpl("creator.journey.limit_total_label", "{{ n }} Total", mapOf("n" to valN.toString()))
        } else {
            tpl("creator.journey.limit_daily_label", "{{ n }} Daily", mapOf("n" to valN.toString()))
        }
    }
    if (isListingLimitChannel(node) || isListingLimitAxisParent(node)) {
        val axis = node.metadata?.optString("listing_limit_axis").orEmpty().ifBlank { "daily" }
        val active = listingLimitTierNodes(allNodes, node).lastOrNull { it.unlocked }
        val value = if (axis == "cap") {
            active?.metadata?.optInt("listings_cap")
                ?: if (node.unlocked && node.channelId.equals("shopify", true)) 50 else null
        } else {
            active?.metadata?.optInt("listings_per_day")
                ?.takeIf { it > 0 }
        }
        return value?.let {
            if (axis == "cap") {
                tpl("creator.journey.limit_cap_label", "{{ n }} Cap", mapOf("n" to it.toString()))
            } else {
                tpl("creator.journey.limit_daily_label", "{{ n }} Daily", mapOf("n" to it.toString()))
            }
        }
    }
    if (isSocialPlatformNode(node)) {
        val active = socialPostTierNodes(allNodes, node).lastOrNull { it.unlocked }
        val daily = active?.metadata?.optInt("posts_per_day")
        return daily?.let {
            tpl("creator.journey.limit_daily_label", "{{ n }} Daily", mapOf("n" to it.toString()))
        }
    }
    if (isDesignSlotLevelNode(node)) {
        val cap = node.metadata?.optInt("slot_count")
            ?: designSlotChildren(allNodes, node).size.takeIf { it > 0 }
        return cap?.let {
            tpl("creator.journey.design_slot_count_label", "{{ n }}", mapOf("n" to it.toString()))
        }
    }
    return null
}

internal fun isExpandableJourneyNode(node: JourneyNodeItem, allNodes: List<JourneyNodeItem>): Boolean {
    if (isCreationLimitParent(node)) {
        return creationLimitTierNodes(allNodes, node).any { !it.unlocked }
    }
    if (isListingLimitChannel(node) || isListingLimitAxisParent(node)) {
        return false
    }
    if (isSocialPlatformNode(node)) {
        return node.unlocked && socialPostTierNodes(allNodes, node).any { !it.unlocked }
    }
    if (isDesignSlotLevelNode(node)) {
        return node.unlocked && designSlotChildren(allNodes, node).any { !it.unlocked }
    }
    return false
}

internal data class JourneySkillInfo(val title: String, val body: String, val meta: String)

internal fun resolveJourneySkillInfo(
    node: JourneyNodeItem,
    allNodes: List<JourneyNodeItem>,
    translationStore: TranslationStore,
): JourneySkillInfo {
    fun t(key: String, fallback: String) = translationStore.t(key, fallback)
    fun tpl(key: String, fallback: String, vars: Map<String, String>): String {
        var s = t(key, fallback)
        vars.forEach { (k, v) -> s = s.replace("{{ $k }}", v).replace("{{$k}}", v) }
        return s
    }
    val title = node.title
    val body = when {
        node.category == "royalty" -> {
            val tier = node.metadata?.optInt("royalty_tier")
                ?: Regex("royalty:(\\d+)").find(node.nodeKey)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: 1
            val pct = node.metadata?.optInt("royalty_percent") ?: 0
            tpl(
                "creator.journey.royalty_info_tier_$tier",
                "Earn $pct% of net profit per sale.",
                mapOf("pct" to pct.toString()),
            )
        }
        else -> t(
            "creator.journey.info._fallback.body",
            "Unlock this skill in your Creator Journey to expand what you can create, publish, and earn.",
        )
    }
    val metaParts = mutableListOf<String>()
    metaParts += tpl("creator.journey.level_badge", "Level {{ n }}", mapOf("n" to node.minLevel.toString()))
    if (!node.unlocked && node.cost > 0) {
        metaParts += journeyEazBadgeLabel(translationStore, node.committed, node.cost, false)
    }
    journeyParentLimitLabel(node, allNodes, translationStore)?.let { metaParts += it }
    return JourneySkillInfo(title = title, body = body, meta = metaParts.joinToString(" · "))
}

@Composable
internal fun JourneyVariantConnector(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(2.dp)
            .height(28.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFF9D00).copy(alpha = 0.55f),
                        Color(0xFFFF9D00).copy(alpha = 0.12f),
                    ),
                ),
            ),
    )
}

@Composable
internal fun JourneySkillInfoDialog(
    info: JourneySkillInfo?,
    translationStore: TranslationStore,
    onDismiss: () -> Unit,
) {
    if (info == null) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(info.title, color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(info.body, color = Color(0xFFE5E7EB), fontSize = 14.sp)
                if (info.meta.isNotBlank()) {
                    Text(info.meta, color = Color(0xFF9CA3AF), fontSize = 12.sp)
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
internal fun JourneyCreationLimitTreePanel(
    nodes: List<JourneyNodeItem>,
    allNodes: List<JourneyNodeItem>,
    expandState: JourneyExpandState,
    isCreator: Boolean,
    busy: Boolean,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier,
    onInfoClick: (JourneyNodeItem) -> Unit,
    onCommitClick: (JourneyNodeItem) -> Unit,
    onUnlock: (String) -> Unit,
) {
    val parents = nodes.filter { isCreationLimitParent(it) }
    if (parents.isEmpty()) {
        Box(modifier = modifier.padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                translationStore.t("creator.journey.starter_empty", "No items in this category yet."),
                color = Color(0xFF9CA3AF),
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                translationStore.t("creator.journey.creation_limits_title", "Creation Limits"),
                color = EazColors.Orange,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(parents, key = { it.nodeKey }) { parent ->
                    JourneyLimitParentCard(
                        node = parent,
                        allNodes = allNodes,
                        expanded = expandState.creationLimitParent == parent.nodeKey,
                        isCreator = isCreator,
                        busy = busy,
                        translationStore = translationStore,
                        onExpandToggle = { expandState.toggleCreationLimit(parent.nodeKey) },
                        onInfoClick = { onInfoClick(parent) },
                        onCommitClick = onCommitClick,
                        onUnlock = onUnlock,
                        modifier = Modifier.width(156.dp),
                    )
                }
            }
        }
        expandState.creationLimitParent?.let { parentKey ->
            val parent = parents.firstOrNull { it.nodeKey == parentKey } ?: return@let
            val tiers = creationLimitTierNodes(allNodes, parent).filter { !it.unlocked }
            if (tiers.isNotEmpty()) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        JourneyVariantConnector()
                        Text(
                            parent.title,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(tiers, key = { it.nodeKey }) { tier ->
                                JourneyLimitTierCard(
                                    node = tier,
                                    allNodes = allNodes,
                                    isCreator = isCreator,
                                    busy = busy,
                                    translationStore = translationStore,
                                    onInfoClick = { onInfoClick(tier) },
                                    onCommitClick = onCommitClick,
                                    onUnlock = onUnlock,
                                    modifier = Modifier.width(156.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun JourneyListingLimitTreePanel(
    nodes: List<JourneyNodeItem>,
    allNodes: List<JourneyNodeItem>,
    @Suppress("UNUSED_PARAMETER") expandState: JourneyExpandState,
    isCreator: Boolean,
    busy: Boolean,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier,
    onInfoClick: (JourneyNodeItem) -> Unit,
    onCommitClick: (JourneyNodeItem) -> Unit,
    onUnlock: (String) -> Unit,
) {
    val channels = listingLimitChannelNodes(nodes)
    if (channels.isEmpty()) {
        Box(modifier = modifier.padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                translationStore.t("creator.journey.starter_empty", "No items in this category yet."),
                color = Color(0xFF9CA3AF),
            )
        }
        return
    }
    val dailyTitle = translationStore.t("creator.journey.listing_limits_daily_title", "Daily")
    val capTitle = translationStore.t("creator.journey.listing_limits_cap_title", "Cap")
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(channels, key = { it.nodeKey }) { channel ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                JourneyLimitParentCard(
                    node = channel,
                    allNodes = allNodes,
                    expanded = false,
                    isCreator = isCreator,
                    busy = busy,
                    translationStore = translationStore,
                    onExpandToggle = {},
                    onInfoClick = { onInfoClick(channel) },
                    onCommitClick = onCommitClick,
                    onUnlock = onUnlock,
                    modifier = Modifier.width(156.dp),
                )
                if (channel.unlocked) {
                    JourneyVariantConnector()
                    Text(
                        channel.title,
                        color = EazColors.Orange,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        letterSpacing = 0.8.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    JourneyListingLimitSkillCarousel(
                        title = dailyTitle,
                        tiers = listingLimitTiersForChannelAxis(allNodes, channel, "daily"),
                        allNodes = allNodes,
                        isCreator = isCreator,
                        busy = busy,
                        translationStore = translationStore,
                        onInfoClick = onInfoClick,
                        onCommitClick = onCommitClick,
                        onUnlock = onUnlock,
                    )
                    JourneyListingLimitSkillCarousel(
                        title = capTitle,
                        tiers = listingLimitTiersForChannelAxis(allNodes, channel, "cap"),
                        allNodes = allNodes,
                        isCreator = isCreator,
                        busy = busy,
                        translationStore = translationStore,
                        onInfoClick = onInfoClick,
                        onCommitClick = onCommitClick,
                        onUnlock = onUnlock,
                    )
                }
            }
        }
    }
}

@Composable
private fun JourneyListingLimitSkillCarousel(
    title: String,
    tiers: List<JourneyNodeItem>,
    allNodes: List<JourneyNodeItem>,
    isCreator: Boolean,
    busy: Boolean,
    translationStore: TranslationStore,
    onInfoClick: (JourneyNodeItem) -> Unit,
    onCommitClick: (JourneyNodeItem) -> Unit,
    onUnlock: (String) -> Unit,
) {
    if (tiers.isEmpty()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                2.dp,
                Color(0xFFFFC83C).copy(alpha = 0.85f),
                RoundedCornerShape(16.dp),
            )
            .background(
                Color.Black.copy(alpha = 0.32f),
                RoundedCornerShape(16.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            title,
            color = EazColors.Orange,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            letterSpacing = 0.8.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(tiers, key = { it.nodeKey }) { tier ->
                JourneyLimitTierCard(
                    node = tier,
                    allNodes = allNodes,
                    isCreator = isCreator,
                    busy = busy,
                    translationStore = translationStore,
                    onInfoClick = { onInfoClick(tier) },
                    onCommitClick = onCommitClick,
                    onUnlock = onUnlock,
                    modifier = Modifier.width(156.dp),
                )
            }
        }
    }
}

@Composable
internal fun JourneySocialTreePanel(
    nodes: List<JourneyNodeItem>,
    allNodes: List<JourneyNodeItem>,
    expandState: JourneyExpandState,
    isCreator: Boolean,
    busy: Boolean,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier,
    onInfoClick: (JourneyNodeItem) -> Unit,
    onCommitClick: (JourneyNodeItem) -> Unit,
    onUnlock: (String) -> Unit,
) {
    val platforms = nodes.filter { isSocialPlatformNode(it) }
    val unlocked = platforms.filter { it.unlocked }
    val available = platforms.filter { !it.unlocked }
    if (platforms.isEmpty()) {
        Box(modifier = modifier.padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                translationStore.t("creator.journey.starter_empty", "No items in this category yet."),
                color = Color(0xFF9CA3AF),
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (unlocked.isNotEmpty()) {
            item {
                Text(
                    translationStore.t("creator.journey.unlocked_skills", "Unlocked"),
                    color = EazColors.Orange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(unlocked, key = { it.nodeKey }) { platform ->
                        JourneyLimitParentCard(
                            node = platform,
                            allNodes = allNodes,
                            expanded = expandState.socialPlatform == platform.nodeKey,
                            isCreator = isCreator,
                            busy = busy,
                            translationStore = translationStore,
                            onExpandToggle = { expandState.toggleSocialPlatform(platform.nodeKey) },
                            onInfoClick = { onInfoClick(platform) },
                            onCommitClick = onCommitClick,
                            onUnlock = onUnlock,
                            modifier = Modifier.width(156.dp),
                        )
                    }
                }
            }
            expandState.socialPlatform?.let { platformKey ->
                val platform = unlocked.firstOrNull { it.nodeKey == platformKey } ?: return@let
                val tiers = socialPostTierNodes(allNodes, platform).filter { !it.unlocked }
                if (tiers.isNotEmpty()) {
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            JourneyVariantConnector()
                            Text(
                                platform.title + " · " +
                                    translationStore.t("creator.journey.social_posts_title", "Daily posts"),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(tiers, key = { it.nodeKey }) { tier ->
                                    JourneyLimitTierCard(
                                        node = tier,
                                        allNodes = allNodes,
                                        isCreator = isCreator,
                                        busy = busy,
                                        translationStore = translationStore,
                                        onInfoClick = { onInfoClick(tier) },
                                        onCommitClick = onCommitClick,
                                        onUnlock = onUnlock,
                                        modifier = Modifier.width(156.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (available.isNotEmpty()) {
            item {
                Text(
                    translationStore.t("creator.journey.available_skills", "Available"),
                    color = EazColors.Orange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(available, key = { it.nodeKey }) { platform ->
                        JourneyLimitParentCard(
                            node = platform,
                            allNodes = allNodes,
                            expanded = false,
                            isCreator = isCreator,
                            busy = busy,
                            translationStore = translationStore,
                            onExpandToggle = {},
                            onInfoClick = { onInfoClick(platform) },
                            onCommitClick = onCommitClick,
                            onUnlock = onUnlock,
                            modifier = Modifier.width(156.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun JourneyDesignSlotTreePanel(
    nodes: List<JourneyNodeItem>,
    allNodes: List<JourneyNodeItem>,
    expandState: JourneyExpandState,
    isCreator: Boolean,
    busy: Boolean,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier,
    onInfoClick: (JourneyNodeItem) -> Unit,
    onCommitClick: (JourneyNodeItem) -> Unit,
    onUnlock: (String) -> Unit,
) {
    fun t(key: String, fallback: String) = translationStore.t(key, fallback)
    val levels = nodes.filter { isDesignSlotLevelNode(it) }
        .sortedBy { designSlotLevelFromNode(it) }
    if (levels.isEmpty()) {
        Box(modifier = modifier.padding(16.dp), contentAlignment = Alignment.Center) {
            Text(t("creator.journey.starter_empty", "No items in this category yet."), color = Color(0xFF9CA3AF))
        }
        return
    }
    val unlocked = levels.filter { it.unlocked }.sortedByDescending { designSlotLevelFromNode(it) }
    val locked = levels.filter { !it.unlocked }
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (unlocked.isNotEmpty()) {
            item {
                JourneyDesignSlotLevelRow(
                    title = t("creator.journey.unlocked_skills", "Unlocked"),
                    levels = unlocked,
                    allNodes = allNodes,
                    expandState = expandState,
                    isCreator = isCreator,
                    busy = busy,
                    translationStore = translationStore,
                    onInfoClick = onInfoClick,
                    onCommitClick = onCommitClick,
                    onUnlock = onUnlock,
                )
            }
        }
        if (locked.isNotEmpty()) {
            item {
                JourneyDesignSlotLevelRow(
                    title = t("creator.journey.available_skills", "Available"),
                    levels = locked,
                    allNodes = allNodes,
                    expandState = expandState,
                    isCreator = isCreator,
                    busy = busy,
                    translationStore = translationStore,
                    onInfoClick = onInfoClick,
                    onCommitClick = onCommitClick,
                    onUnlock = onUnlock,
                )
            }
        }
    }
}

@Composable
private fun JourneyDesignSlotLevelRow(
    title: String,
    levels: List<JourneyNodeItem>,
    allNodes: List<JourneyNodeItem>,
    expandState: JourneyExpandState,
    isCreator: Boolean,
    busy: Boolean,
    translationStore: TranslationStore,
    onInfoClick: (JourneyNodeItem) -> Unit,
    onCommitClick: (JourneyNodeItem) -> Unit,
    onUnlock: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = EazColors.Orange, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(levels, key = { it.nodeKey }) { level ->
                JourneyLimitParentCard(
                    node = level,
                    allNodes = allNodes,
                    expanded = expandState.designSlotLevel == level.nodeKey,
                    isCreator = isCreator,
                    busy = busy,
                    translationStore = translationStore,
                    onExpandToggle = { expandState.toggleDesignSlotLevel(level.nodeKey) },
                    onInfoClick = { onInfoClick(level) },
                    onCommitClick = onCommitClick,
                    onUnlock = onUnlock,
                    modifier = Modifier.width(156.dp),
                )
            }
        }
        expandState.designSlotLevel?.let { levelKey ->
            val level = levels.firstOrNull { it.nodeKey == levelKey } ?: return@let
            if (!level.unlocked) return@let
            val slots = designSlotChildren(allNodes, level).filter { !it.unlocked }
            if (slots.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    JourneyVariantConnector()
                    Text(
                        translationStore.t("creator.journey.design_slots_bucket_complete", "All slots in this level are unlocked."),
                        color = Color(0xFF9CA3AF),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                val nextIdx = designSlotChildren(allNodes, level).firstOrNull { !it.unlocked }?.slotIndex ?: 0
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    JourneyVariantConnector()
                    Text(
                        translationStore.t("creator.journey.design_slots_inactive_title", "Available slots") +
                            " · ${level.title}",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        translationStore.t(
                            "creator.journey.design_slots_sequential_hint",
                            "Unlock slots in order — only the next slot can be activated.",
                        ),
                        color = Color(0xFF9CA3AF),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(slots, key = { it.nodeKey }) { slot ->
                            val seqBlocked = nextIdx > 0 && slot.slotIndex != nextIdx
                            JourneyLimitTierCard(
                                node = slot,
                                allNodes = allNodes,
                                isCreator = isCreator,
                                busy = busy,
                                translationStore = translationStore,
                                onInfoClick = { onInfoClick(slot) },
                                onCommitClick = onCommitClick,
                                onUnlock = onUnlock,
                                seqBlocked = seqBlocked,
                                modifier = Modifier.width(156.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JourneyLimitParentCard(
    node: JourneyNodeItem,
    allNodes: List<JourneyNodeItem>,
    expanded: Boolean,
    isCreator: Boolean,
    busy: Boolean,
    translationStore: TranslationStore,
    onExpandToggle: () -> Unit,
    onInfoClick: () -> Unit,
    onCommitClick: (JourneyNodeItem) -> Unit,
    onUnlock: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expandable = isExpandableJourneyNode(node, allNodes)
    Column(modifier = modifier) {
        JourneyTreeCardShell(
            node = node,
            allNodes = allNodes,
            isCreator = isCreator,
            busy = busy,
            translationStore = translationStore,
            expandable = expandable,
            expanded = expanded,
            onCardClick = if (expandable) onExpandToggle else onInfoClick,
            onInfoClick = if (expandable) onInfoClick else null,
            onCommitClick = { onCommitClick(node) },
            onUnlockClick = { onUnlock(node.nodeKey) },
            skipParentActions = isCreationLimitParent(node) || isListingLimitChannel(node) || isSocialPlatformNode(node),
        )
    }
}

@Composable
private fun JourneyLimitTierCard(
    node: JourneyNodeItem,
    allNodes: List<JourneyNodeItem>,
    isCreator: Boolean,
    busy: Boolean,
    translationStore: TranslationStore,
    onInfoClick: () -> Unit,
    onCommitClick: (JourneyNodeItem) -> Unit,
    onUnlock: (String) -> Unit,
    modifier: Modifier = Modifier,
    seqBlocked: Boolean = false,
) {
    JourneyTreeCardShell(
        node = node,
        allNodes = allNodes,
        isCreator = isCreator,
        busy = busy,
        translationStore = translationStore,
        expandable = false,
        expanded = false,
        onCardClick = onInfoClick,
        onInfoClick = null,
        onCommitClick = { onCommitClick(node) },
        onUnlockClick = { onUnlock(node.nodeKey) },
        seqBlocked = seqBlocked,
        modifier = modifier,
    )
}

@Composable
internal fun JourneyTreeCardShell(
    node: JourneyNodeItem,
    allNodes: List<JourneyNodeItem>,
    isCreator: Boolean,
    busy: Boolean,
    translationStore: TranslationStore,
    expandable: Boolean,
    expanded: Boolean,
    onCardClick: () -> Unit,
    onInfoClick: (() -> Unit)?,
    onCommitClick: () -> Unit,
    onUnlockClick: () -> Unit,
    modifier: Modifier = Modifier,
    skipParentActions: Boolean = false,
    seqBlocked: Boolean = false,
) {
    val levelLocked = node.lockedReason == "level_required"
    val canAct = !skipParentActions && !node.unlocked && isCreator &&
        node.lockedReason.isEmpty() && !levelLocked && !seqBlocked && node.cost > 0
    val unlockReady = canAct && (node.freePickEligible || node.committed + 1e-9 >= node.cost)
    val hasAction = canAct
    val icon = journeySkillIcon(node)
    val limitLabel = journeyParentLimitLabel(node, allNodes, translationStore)
    val borderColor = when {
        unlockReady -> Color(0xFFFFB428).copy(alpha = 0.95f)
        node.unlocked -> Color(0xFFFFC83C).copy(alpha = 0.85f)
        else -> Color(0xFFFF9D00).copy(alpha = 0.35f)
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(2.dp, borderColor, RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(listOf(Color(0xFF1E2330), Color(0xFF0C1018))),
                    RoundedCornerShape(16.dp),
                )
                .clip(RoundedCornerShape(16.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onCardClick() },
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    node.title,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                )
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (icon != null) {
                            Icon(icon, contentDescription = null, tint = EazColors.Orange, modifier = Modifier.size(36.dp))
                        }
                        limitLabel?.let {
                            Text(it, color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                if (!skipParentActions && !node.unlocked && !unlockReady && node.cost > 0) {
                    Text(
                        journeyEazBadgeLabel(translationStore, node.committed, node.cost, false),
                        color = EazColors.Orange,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(bottom = 10.dp)
                            .background(EazColors.Orange.copy(alpha = 0.14f), RoundedCornerShape(999.dp))
                            .border(1.dp, EazColors.Orange.copy(alpha = 0.28f), RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                } else if (node.unlocked && !skipParentActions) {
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
            if (node.unlocked) {
                Box(
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp)
                        .background(Color(0xFFFBBF24), RoundedCornerShape(999.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✓", color = Color(0xFF111827), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            onInfoClick?.let {
                IconButton(
                    onClick = it,
                    modifier = Modifier.align(Alignment.TopStart).size(32.dp),
                ) {
                    Icon(Icons.Default.Info, contentDescription = translationStore.t("creator.journey.info_aria", "About"), tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                }
            }
            if (expandable) {
                Text(
                    if (expanded) "▲" else "▼",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
                )
            }
        }
        if (hasAction) {
            JourneyTreeSkillActionButton(
                unlockReady = unlockReady,
                busy = busy,
                translationStore = translationStore,
                onCommitClick = onCommitClick,
                onUnlockClick = onUnlockClick,
            )
        }
    }
}

@Composable
internal fun JourneyTreeSkillActionButton(
    unlockReady: Boolean,
    busy: Boolean,
    translationStore: TranslationStore,
    onCommitClick: () -> Unit,
    onUnlockClick: () -> Unit,
) {
    fun t(key: String, fallback: String) = translationStore.t(key, fallback)
    val shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
    val label = if (unlockReady) {
        t("creator.journey.unlock_short", "Unlock")
    } else {
        t("creator.journey.commit_eaz", "Commit")
    }
    Button(
        onClick = { if (unlockReady) onUnlockClick() else onCommitClick() },
        enabled = !busy,
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .border(2.dp, Color(0xFFFF9D00).copy(alpha = 0.35f), shape),
        colors = if (unlockReady) {
            ButtonDefaults.buttonColors(
                containerColor = EazColors.Orange,
                contentColor = Color(0xFF111827),
                disabledContainerColor = EazColors.Orange.copy(alpha = 0.45f),
                disabledContentColor = Color(0xFF111827).copy(alpha = 0.6f),
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = Color(0xFF0C1018),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFF0C1018).copy(alpha = 0.45f),
                disabledContentColor = Color.White.copy(alpha = 0.6f),
            )
        },
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

internal fun journeyEazBadgeLabel(
    translationStore: TranslationStore,
    committed: Double,
    cost: Double,
    unlocked: Boolean,
): String {
    if (unlocked) return translationStore.t("creator.journey.unlocked", "Unlocked")
    if (cost <= 0) return translationStore.t("creator.journey.eaz_free", "Free")
    val committedLabel = if (kotlin.math.abs(committed - committed.toLong()) < 1e-9) {
        committed.toLong().toString()
    } else {
        String.format(java.util.Locale.US, "%.2f", committed)
    }
    val costLabel = if (kotlin.math.abs(cost - cost.toLong()) < 1e-9) {
        cost.toLong().toString()
    } else {
        String.format(java.util.Locale.US, "%.2f", cost)
    }
    var s = translationStore.t("creator.journey.eaz_badge", "{{ committed }}/{{ cost }} EAZV")
    s = s.replace("{{ committed }}", committedLabel).replace("{{committed}}", committedLabel)
    s = s.replace("{{ cost }}", costLabel).replace("{{cost}}", costLabel)
    return s
}
