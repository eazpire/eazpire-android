package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val MAIN_DESIGN = "design-generator"
private const val MAIN_PUBLISH = "publish"
private const val MAIN_MARKETING = "marketing"

/**
 * Automations — Design Generator | Publish | Marketing (placeholders), status tabs, grid like web.
 */
@Composable
fun AutomationsScreen(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    onHeaderTitleChange: (String) -> Unit,
    maxHeight: Dp = Dp.Infinity,
    modifier: Modifier = Modifier
) {
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId().orEmpty() }
    val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }
    val scope = rememberCoroutineScope()
    var mainTab by remember { mutableStateOf(MAIN_DESIGN) }
    var statusFilter by remember { mutableStateOf("active") }
    var automations by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var showCreateHint by remember { mutableStateOf(false) }

    fun titleForStatus(): String = when (statusFilter) {
        "scheduled" -> translationStore.t("creator.automations.status_scheduled", "Scheduled")
        "expired" -> translationStore.t("creator.automations.status_expired", "Expired")
        else -> translationStore.t("creator.automations.status_active", "Active")
    }

    LaunchedEffect(mainTab, statusFilter) {
        when (mainTab) {
            MAIN_DESIGN -> onHeaderTitleChange(titleForStatus())
            MAIN_PUBLISH -> onHeaderTitleChange(translationStore.t("creator.automations.tab_publish", "Publish"))
            MAIN_MARKETING -> onHeaderTitleChange(translationStore.t("creator.automations.tab_marketing", "Marketing"))
        }
    }

    LaunchedEffect(ownerId, mainTab, statusFilter, reloadKey) {
        if (ownerId.isBlank() || mainTab != MAIN_DESIGN) return@LaunchedEffect
        loading = true
        err = null
        val rows: JSONArray? = withContext(Dispatchers.IO) {
            try {
                val d = api.listDesignAutomations(ownerId, statusFilter)
                if (d.optBoolean("ok", false)) d.optJSONArray("automations") else null
            } catch (_: Exception) {
                null
            }
        }
        loading = false
        if (rows == null) {
            err = translationStore.t("creator.automations.load_error", "Could not load automations.")
            automations = emptyList()
        } else {
            val list = mutableListOf<JSONObject>()
            for (i in 0 until rows.length()) {
                list.add(rows.getJSONObject(i))
            }
            automations = list
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .heightIn(max = maxHeight)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0x660A0514), Color(0x9905020F))
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                MAIN_DESIGN to translationStore.t("creator.automations.tab_design_generator", "Design Generator"),
                MAIN_PUBLISH to translationStore.t("creator.automations.tab_publish", "Publish"),
                MAIN_MARKETING to translationStore.t("creator.automations.tab_marketing", "Marketing")
            ).forEach { (id, label) ->
                Text(
                    text = label,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (mainTab == id) EazColors.Orange.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f)
                        )
                        .clickable { mainTab = id }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (mainTab == id) EazColors.Orange else Color.White.copy(alpha = 0.85f),
                    maxLines = 2
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        when (mainTab) {
            MAIN_PUBLISH -> {
                Text(
                    translationStore.t("creator.automations.coming_soon_publish", "Publish automations coming soon."),
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.padding(24.dp)
                )
            }
            MAIN_MARKETING -> {
                Text(
                    translationStore.t("creator.automations.coming_soon_marketing", "Marketing automations coming soon."),
                    color = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.padding(24.dp)
                )
            }
            MAIN_DESIGN -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("active", "scheduled", "expired").forEach { f ->
                        val lbl = when (f) {
                            "scheduled" -> translationStore.t("creator.automations.status_scheduled", "Scheduled")
                            "expired" -> translationStore.t("creator.automations.status_expired", "Expired")
                            else -> translationStore.t("creator.automations.status_active", "Active")
                        }
                        Text(
                            text = lbl,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (statusFilter == f) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f)
                                )
                                .clickable { statusFilter = f }
                                .padding(vertical = 8.dp, horizontal = 6.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = if (statusFilter == f) 0.95f else 0.7f),
                            maxLines = 1
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (ownerId.isBlank()) {
                    Text(
                        translationStore.t("creator.automations.login_required", "Log in to manage automations."),
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    return@Column
                }
                if (loading) {
                    Text(
                        translationStore.t("creator.common.loading", "Loading..."),
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
                err?.let {
                    Text(it, color = Color(0xFFfca5a5), modifier = Modifier.padding(bottom = 8.dp))
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .fillMaxWidth()
                ) {
                    item {
                        AddAutomationTile(
                            label = translationStore.t("creator.automations.add_tile", "Add automation"),
                            onClick = { showCreateHint = true }
                        )
                    }
                    items(automations.size) { idx ->
                        val row = automations[idx]
                        val st = row.optString("status", "")
                        val showEnd = statusFilter != "expired" && (st == "active" || st == "scheduled")
                        AutomationCard(
                            row = row,
                            translationStore = translationStore,
                            showEnd = showEnd,
                            onEnd = {
                                val id = row.optLong("id", 0L)
                                if (id > 0) {
                                    scope.launch {
                                        withContext(Dispatchers.IO) { api.endDesignAutomation(id) }
                                        reloadKey++
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showCreateHint) {
        AlertDialog(
            onDismissRequest = { showCreateHint = false },
            confirmButton = {
                TextButton(onClick = { showCreateHint = false }) {
                    Text(translationStore.t("creator.common.close", "Close"))
                }
            },
            title = { Text(translationStore.t("creator.automations.modal_title", "New design automation")) },
            text = {
                Text(
                    "Create automations in the web creator dashboard for now. Android will match soon.",
                    color = Color.White.copy(alpha = 0.9f)
                )
            },
            containerColor = Color(0xFF12141f),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun AddAutomationTile(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("+", fontSize = 36.sp, color = EazColors.Orange, fontWeight = FontWeight.Bold)
            Text(
                label,
                color = EazColors.Orange,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun AutomationCard(
    row: JSONObject,
    translationStore: TranslationStore,
    showEnd: Boolean,
    onEnd: () -> Unit
) {
    val previews = row.optJSONArray("preview_urls")
    val urls = mutableListOf<String>()
    if (previews != null) {
        for (i in 0 until minOf(4, previews.length())) {
            val u = previews.optString(i, "").trim()
            if (u.isNotEmpty()) urls.add(u)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xE6281f2b))
            .padding(bottom = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(8.dp)
        ) {
            if (urls.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        translationStore.t("creator.automations.no_designs_yet", "No design generated yet"),
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxSize()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        PreviewCell(urls.getOrNull(0), Modifier.weight(1f))
                        PreviewCell(urls.getOrNull(1), Modifier.weight(1f))
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        PreviewCell(urls.getOrNull(2), Modifier.weight(1f))
                        PreviewCell(urls.getOrNull(3), Modifier.weight(1f))
                    }
                }
            }
        }
        Text(
            row.optString("title", "—"),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )
        Text(
            "${translationStore.t("creator.automations.stat_generations", "Generations")}: ${row.optInt("total_generations", 0)}",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
        if (showEnd) {
            Text(
                translationStore.t("creator.automations.end_automation", "End"),
                color = Color(0xFFfecaca),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clickable(onClick = onEnd)
            )
        }
    }
}

@Composable
private fun PreviewCell(url: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }
    }
}
