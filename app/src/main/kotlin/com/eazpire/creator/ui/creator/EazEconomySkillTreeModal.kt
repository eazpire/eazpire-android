package com.eazpire.creator.ui.creator

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
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

private val AXIS_LABELS = mapOf(
    "cost" to "Cost",
    "daily" to "Daily",
    "cap" to "Cap",
)

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

        Text(
            translationStore.t(
                "creator.eaz_economy.intro",
                "Unlock mascot-gated bonuses with EAZ. Cost discounts apply only after you activate a node here."
            ),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.72f),
            modifier = Modifier.padding(bottom = 12.dp)
        )

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
            val byAxis = linkedMapOf<String, MutableList<JSONObject>>()
            for (i in 0 until nodes.length()) {
                val n = nodes.optJSONObject(i) ?: continue
                val axis = n.optString("axis", "cost")
                byAxis.getOrPut(axis) { mutableListOf() }.add(n)
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                byAxis.forEach { (axis, list) ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            AXIS_LABELS[axis] ?: axis,
                            style = MaterialTheme.typography.labelMedium,
                            color = EazColors.Orange,
                            fontWeight = FontWeight.Bold
                        )
                        list.forEach { node ->
                            SkillTreeNodeRow(
                                node = node,
                                translationStore = translationStore,
                                onActivate = { key ->
                                    scope.launch {
                                        try {
                                            val r = withContext(Dispatchers.IO) {
                                                api.activateEazEconomySkill(ownerId, key)
                                            }
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
                            )
                        }
                    }
                }

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
            }
        }
    }
}

@Composable
private fun SkillTreeNodeRow(
    node: JSONObject,
    translationStore: TranslationStore,
    onActivate: (String) -> Unit,
) {
    val status = node.optString("status", "locked")
    val skillKey = node.optString("skill_key", "")
    val bonusPct = (node.optDouble("bonus_pct", 0.0) * 100).toInt()
    val minLevel = node.optInt("mascot_min_level", 0)
    val cost = node.optDouble("activation_cost_eaz", 0.0)
    val meta = buildString {
        append("+$bonusPct%")
        if (minLevel > 0) append(" · Lv.$minLevel")
        if (cost > 0) append(" · ${EazCostCatalog.fmtEaz(cost)} EAZ")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (status == "active" || status == "grandfathered") Color(0xFF14532D).copy(alpha = 0.25f)
                else Color.White.copy(alpha = 0.03f),
                RoundedCornerShape(8.dp)
            )
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(skillKey, color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Text(meta, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.labelSmall)
        }
        when (status) {
            "unlocked" -> {
                OutlinedButton(onClick = { onActivate(skillKey) }) {
                    Text(translationStore.t("creator.eaz_economy.activate", "Activate"))
                }
            }
            "active", "grandfathered" -> {
                Text(
                    translationStore.t("creator.eaz_economy.active", "Active"),
                    color = Color(0xFF4ADE80),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            "kickstarter_locked" -> {
                Text(
                    translationStore.t("creator.eaz_economy.kickstarter_locked", "Kickstarter"),
                    color = Color.White.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            else -> {
                Text(
                    translationStore.t("creator.eaz_economy.locked", "Locked"),
                    color = Color.White.copy(alpha = 0.45f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
