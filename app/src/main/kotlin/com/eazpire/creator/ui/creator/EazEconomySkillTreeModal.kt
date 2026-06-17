package com.eazpire.creator.ui.creator

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.billing.EazBalanceRefreshBus
import com.eazpire.creator.billing.EazCostCatalog
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.modal.EazFullScreenDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val AXIS_TAB_ORDER = listOf("cost", "daily", "cap", "kickstarter")

private val AXIS_LABELS = mapOf(
    "cost" to "Cost",
    "daily" to "Daily",
    "cap" to "Cap",
    "kickstarter" to "Kickstarter",
)

private fun eazSkillLabel(skillKey: String, isAxisGate: Boolean): String {
    if (isAxisGate || skillKey.startsWith("axis_")) {
        val tab = skillKey.removePrefix("axis_")
        return AXIS_LABELS[tab] ?: tab.replaceFirstChar { it.uppercase() }
    }
    if (skillKey.startsWith("kickstarter_")) {
        val part = skillKey.removePrefix("kickstarter_")
        return "Kickstarter ${AXIS_LABELS[part] ?: part.replaceFirstChar { it.uppercase() }}"
    }
    val match = Regex("^(cost|daily|cap)_(\\d+)$").find(skillKey) ?: return skillKey.replace('_', ' ')
    val axis = match.groupValues[1]
    val num = match.groupValues[2]
    return "${AXIS_LABELS[axis] ?: axis} $num"
}

private fun eazBonusLabel(axis: String, bonusPct: Double): String {
    val bonus = (bonusPct * 100).toInt()
    if (bonus == 0) return ""
    return if (axis == "cost") "-$bonus%" else "+$bonus%"
}

@Composable
fun EazEconomySkillTreeModal(
    ownerId: String,
    api: CreatorApi,
    translationStore: TranslationStore,
    onDismiss: () -> Unit,
) {
    EazFullScreenDialog(onDismissRequest = onDismiss) {
        EazEconomySkillTreePanel(
            ownerId = ownerId,
            api = api,
            translationStore = translationStore,
            embedded = false,
            onDismiss = onDismiss,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EazEconomySkillTreePanel(
    ownerId: String,
    api: CreatorApi,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
    onDismiss: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var treeData by remember { mutableStateOf<JSONObject?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var kickstarterCode by remember { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }
    var axisFilter by remember { mutableStateOf("cost") }

    LaunchedEffect(ownerId, refresh) {
        if (ownerId.isBlank()) {
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        try {
            val r = withContext(Dispatchers.IO) { api.getEazEconomyTree(ownerId) }
            treeData = if (r.optBoolean("ok", false)) r else null
        } catch (_: Exception) {
            treeData = null
        } finally {
            isLoading = false
        }
    }

    fun reload() {
        refresh++
    }

    fun activateSkill(key: String) {
        scope.launch {
            try {
                val r = withContext(Dispatchers.IO) { api.activateEazEconomySkill(ownerId, key) }
                if (r.optBoolean("ok", false)) {
                    EazBalanceRefreshBus.requestRefresh()
                    reload()
                } else {
                    Toast.makeText(
                        context,
                        translationStore.t("creator.eaz_economy.activate_fail", "Could not activate skill."),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (_: Exception) {
                Toast.makeText(
                    context,
                    translationStore.t("creator.eaz_economy.activate_fail", "Could not activate skill."),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (embedded) Modifier else Modifier.background(Color(0xFF0F1117))
            )
            .padding(if (embedded) 0.dp else 16.dp)
    ) {
        if (!embedded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    translationStore.t("creator.eaz_economy.title", "EAZ Skill Tree"),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                if (onDismiss != null) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EazColors.Orange)
            }
        } else if (treeData == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Failed to load", color = Color.White.copy(alpha = 0.6f))
            }
        } else {
            val data = treeData!!
            val nodes = data.optJSONArray("nodes") ?: JSONArray()
            val mascotLevel = data.optInt("mascot_level", 1)

            val allNodes = buildList {
                for (i in 0 until nodes.length()) {
                    nodes.optJSONObject(i)?.let { add(it) }
                }
            }

            val axisNode = allNodes.firstOrNull {
                it.optBoolean("is_axis_gate", false) && (it.optString("tab", it.optString("axis")) == axisFilter)
            }
            val axisOpen = axisNode?.let {
                val st = it.optString("status")
                st == "active" || st == "grandfathered"
            } == true

            val tabNodes = allNodes.filter {
                !it.optBoolean("is_axis_gate", false) &&
                    (it.optString("tab", it.optString("axis")) == axisFilter)
            }

            val kickstarterRedeemed = data.optBoolean("kickstarter_redeemed", false)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AXIS_TAB_ORDER.forEach { tab ->
                        val selected = tab == axisFilter
                        val label = translationStore.t("creator.eaz_economy.tab_$tab", AXIS_LABELS[tab] ?: tab)
                        Box(
                            modifier = Modifier
                                .background(
                                    if (selected) Color(0xFFF97316).copy(alpha = 0.14f) else Color.White.copy(alpha = 0.04f),
                                    RoundedCornerShape(999.dp)
                                )
                                .border(
                                    1.dp,
                                    if (selected) Color(0xFFF97316).copy(alpha = 0.65f) else Color.White.copy(alpha = 0.14f),
                                    RoundedCornerShape(999.dp)
                                )
                                .clickable { axisFilter = tab }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                label,
                                color = if (selected) Color.White else Color.White.copy(alpha = 0.72f),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }

                if (axisFilter == "kickstarter") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            translationStore.t("creator.eaz_economy.kickstarter_hint", "Redeem a Kickstarter code to unlock bonus nodes."),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.72f)
                        )
                        OutlinedTextField(
                            value = kickstarterCode,
                            onValueChange = { kickstarterCode = it },
                            placeholder = {
                                Text(translationStore.t("creator.eaz_economy.kickstarter_code_placeholder", "KS-…"))
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = EazColors.Orange,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                            )
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        val r = withContext(Dispatchers.IO) {
                                            api.redeemKickstarterEazBonus(ownerId, kickstarterCode.trim())
                                        }
                                        if (r.optBoolean("ok", false)) {
                                            kickstarterCode = ""
                                            Toast.makeText(
                                                context,
                                                translationStore.t("creator.eaz_economy.kickstarter_success", "Kickstarter bonus unlocked."),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            reload()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                translationStore.t("creator.eaz_economy.activate_fail", "Could not activate skill."),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } catch (_: Exception) {
                                        Toast.makeText(
                                            context,
                                            translationStore.t("creator.eaz_economy.activate_fail", "Could not activate skill."),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange)
                        ) {
                            Text(
                                translationStore.t("creator.eaz_economy.kickstarter_redeem", "Redeem"),
                                color = Color.White
                            )
                        }
                        val campaignUrl = data.optString("kickstarter_campaign_url", "").trim()
                        if (campaignUrl.isNotBlank()) {
                            TextButton(onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(campaignUrl)))
                            }) {
                                Text(
                                    translationStore.t("creator.eaz_economy.kickstarter_campaign", "View Kickstarter campaign"),
                                    color = EazColors.Orange
                                )
                            }
                        }
                    }
                    if (!kickstarterRedeemed) return@Column
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    axisNode?.let { node ->
                        Box(modifier = Modifier.widthIn(max = 156.dp)) {
                            EazEconomySkillCard(
                                node = node,
                                translationStore = translationStore,
                                onActivate = ::activateSkill,
                            )
                        }
                    }

                    if (!axisOpen) {
                        Text(
                            translationStore.t(
                                "creator.eaz_economy.axis_unlock_hint",
                                "Unlock this category with EAZ to access its skills."
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(28.dp)
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFFFF9D00).copy(alpha = 0.55f),
                                            Color(0xFFFF9D00).copy(alpha = 0.12f),
                                        )
                                    )
                                )
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            val activeNodes = tabNodes.filter {
                                val st = it.optString("status")
                                st == "active" || st == "grandfathered"
                            }
                            if (activeNodes.isNotEmpty()) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    activeNodes.forEach { node ->
                                        Text(
                                            eazSkillLabel(node.optString("skill_key"), node.optBoolean("is_axis_gate", false)),
                                            color = Color(0xFFFBBF24),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            if (axisFilter == "kickstarter") {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    maxItemsInEachRow = 5,
                                ) {
                                    tabNodes.forEach { node ->
                                        Box(modifier = Modifier.widthIn(min = 132.dp, max = 156.dp)) {
                                            EazEconomySkillCard(
                                                node = node,
                                                translationStore = translationStore,
                                                onActivate = ::activateSkill,
                                            )
                                        }
                                    }
                                }
                            } else {
                                val byLevel = tabNodes.groupBy { it.optInt("mascot_min_level", 1) }.toSortedMap()
                                byLevel.forEach { (level, levelNodes) ->
                                    val rowLocked = level > mascotLevel
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            translationStore.t("creator.journey.level_row", "Level {{ n }}")
                                                .replace("{{ n }}", level.toString())
                                                .replace("{{n}}", level.toString()),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (rowLocked) Color.White.copy(alpha = 0.45f) else EazColors.Orange,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                                            verticalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.fillMaxWidth(),
                                            maxItemsInEachRow = 5,
                                        ) {
                                            levelNodes.forEach { node ->
                                                Box(modifier = Modifier.widthIn(min = 132.dp, max = 156.dp)) {
                                                    EazEconomySkillCard(
                                                        node = node,
                                                        translationStore = translationStore,
                                                        onActivate = ::activateSkill,
                                                    )
                                                }
                                            }
                                        }
                                    }
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
private fun EazEconomySkillCard(
    node: JSONObject,
    translationStore: TranslationStore,
    onActivate: (String) -> Unit,
) {
    val status = node.optString("status", "locked")
    val skillKey = node.optString("skill_key", "")
    val isAxisGate = node.optBoolean("is_axis_gate", false)
    val axis = node.optString("axis", "cost")
    val title = eazSkillLabel(skillKey, isAxisGate)
    val minLevel = node.optInt("mascot_min_level", 0)
    val cost = node.optDouble("activation_cost_eaz", 0.0)
    val isActive = status == "active" || status == "grandfathered"
    val canActivate = status == "unlocked"

    val badge = when {
        isActive -> translationStore.t("creator.eaz_economy.active", "Active")
        status == "kickstarter_locked" -> translationStore.t("creator.eaz_economy.kickstarter_locked", "Kickstarter")
        status == "axis_locked" -> translationStore.t("creator.eaz_economy.axis_locked", "Unlock category first")
        status == "locked" -> translationStore.t("creator.eaz_economy.locked", "Locked")
        isAxisGate -> buildString {
            if (cost > 0) append("${EazCostCatalog.fmtEaz(cost)} EAZ")
            if (minLevel > 0) {
                if (isNotEmpty()) append(" · ")
                append("Lv.$minLevel")
            }
        }
        else -> buildString {
            val bonus = eazBonusLabel(axis, node.optDouble("bonus_pct", 0.0))
            if (bonus.isNotBlank()) append(bonus)
            if (minLevel > 0) {
                if (isNotEmpty()) append(" · ")
                append("Lv.$minLevel")
            }
            if (cost > 0) {
                if (isNotEmpty()) append(" · ")
                append("${EazCostCatalog.fmtEaz(cost)} EAZ")
            }
        }
    }

    val frameColor = when {
        isActive -> Color(0xFF14532D).copy(alpha = 0.35f)
        canActivate -> Color(0xFFF97316).copy(alpha = 0.12f)
        else -> Color.White.copy(alpha = 0.04f)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(frameColor, RoundedCornerShape(14.dp))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(Color(0xFFF97316).copy(alpha = 0.25f), Color.White.copy(alpha = 0.05f))
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(vertical = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isActive) {
                    Text("✓", color = Color(0xFF4ADE80), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text(
                badge,
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }
        if (canActivate) {
            Button(
                onClick = { onActivate(skillKey) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = EazColors.Orange),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(translationStore.t("creator.eaz_economy.activate", "Activate"), color = Color.White)
            }
        }
    }
}
