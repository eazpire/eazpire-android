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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.modal.EazBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private data class JourneyTab(val id: Int, val labelKey: String, val fallback: String)

@OptIn(ExperimentalMaterial3Api::class)
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
    var loading by remember { mutableStateOf(true) }
    var journeyData by remember { mutableStateOf<JSONObject?>(null) }
    var actionBusy by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val tabs = listOf(
        JourneyTab(0, "creator.journey.nav_overview", "Overview"),
        JourneyTab(1, "creator.journey.nav_unlock_tree", "Unlock Tree"),
        JourneyTab(2, "creator.journey.nav_level", "Level"),
    )

    fun t(key: String, fallback: String) = translationStore.t(key, fallback)

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

    EazBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF070B14),
        modifier = modifier,
        fullscreen = true,
        dragHandle = null,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF070B14))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = t("creator.journey.title", "Creator Journey"),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                        color = Color.White,
                    )
                    Text(
                        text = t("creator.journey.subtitle", "Grow your creator studio"),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9CA3AF),
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = t("creator.common.close", "Close"), tint = Color.White)
                }
            }

            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .width(56.dp)
                        .background(Color(0xFF070B14))
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    tabs.forEach { tab ->
                        val active = tab.id == currentTab
                        Icon(
                            Icons.Default.Star,
                            contentDescription = t(tab.labelKey, tab.fallback),
                            tint = if (active) EazColors.Orange else Color.White.copy(alpha = 0.7f),
                            modifier = Modifier
                                .padding(8.dp)
                                .background(
                                    if (active) EazColors.Orange.copy(alpha = 0.2f) else Color.Transparent,
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() },
                                ) { currentTab = tab.id },
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(Color(0xFF0B1220))
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (loading) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = EazColors.Orange)
                        }
                        return@Column
                    }

                    when (currentTab) {
                        0 -> JourneyOverviewPanel(
                            data = journeyData,
                            translationStore = translationStore,
                            busy = actionBusy,
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
                            onCommit = { nodeKey, amount ->
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
    }
}

@Composable
private fun JourneyOverviewPanel(
    data: JSONObject?,
    translationStore: TranslationStore,
    busy: Boolean,
    onSaveStarter: (String, String) -> Unit,
) {
    fun t(key: String, fallback: String) = translationStore.t(key, fallback)
    if (data == null) {
        Text(t("creator.mobile.loading", "Loading…"), color = Color(0xFF9CA3AF))
        return
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

@Composable
private fun JourneyUnlockTreePanel(
    data: JSONObject?,
    translationStore: TranslationStore,
    busy: Boolean,
    onCommit: (String, Double) -> Unit,
    onUnlock: (String) -> Unit,
) {
    fun t(key: String, fallback: String) = translationStore.t(key, fallback)
    val nodes = data?.optJSONArray("nodes") ?: JSONArray()
    val balance = data?.optDouble("balance_eaz", 0.0) ?: 0.0
    val isCreator = data?.optBoolean("is_creator", false) == true

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (i in 0 until nodes.length()) {
            val n = nodes.getJSONObject(i)
            val nodeKey = n.optString("node_key")
            val title = n.optString("product_key")
                .ifBlank { n.optString("design_type") }
                .ifBlank { n.optString("region_code") }
                .ifBlank { n.optString("channel_id") }
                .ifBlank { nodeKey }
            val cost = n.optDouble("cost_eaz", 0.0)
            val committed = n.optDouble("eaz_committed", 0.0)
            val unlocked = n.optBoolean("unlocked", false)
            val lockedReason = n.optString("locked_reason", "")
            val progress = if (cost > 0) (committed / cost).toFloat().coerceIn(0f, 1f) else if (unlocked) 1f else 0f

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111827), RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(title, color = Color.White, modifier = Modifier.weight(1f))
                    Text("Lv.${n.optInt("min_level", 2)}", color = EazColors.Orange, fontSize = 12.sp)
                }
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    color = EazColors.Orange,
                    trackColor = Color(0xFF374151),
                )
                Text(
                    "${committed.toInt()} / ${cost.toInt()} EAZ" +
                        if (unlocked) " · ${t("creator.journey.unlocked", "Unlocked")}" else "",
                    color = Color(0xFF9CA3AF),
                    fontSize = 12.sp,
                )
                if (!unlocked && isCreator && lockedReason.isBlank() && cost > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        OutlinedButton(
                            onClick = { if (balance > 0) onCommit(nodeKey, balance) },
                            enabled = !busy && balance > 0,
                        ) {
                            Text(t("creator.journey.commit_all", "Commit available EAZ"), fontSize = 11.sp)
                        }
                        if (committed < cost) {
                            Button(onClick = { onUnlock(nodeKey) }, enabled = !busy) {
                                Text(t("creator.journey.unlock_now", "Unlock now"), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
