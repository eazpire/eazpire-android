package com.eazpire.creator.ui.creator

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class DesignDetailTab {
    Overview, Reference, Details
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignDetailSheet(
    design: CreationDesign,
    onDismiss: () -> Unit,
    translationStore: TranslationStore,
    tokenStore: SecureTokenStore,
    storeBaseUrl: String = "https://www.eazpire.com",
    onRequestGeneratorPrefill: (GeneratorPrefillRequest) -> Unit = {}
) {
    val t = { key: String, def: String -> translationStore.t(key, def) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val jwt = remember { runCatching { tokenStore.getJwt() }.getOrNull() }
    val ownerId = remember { runCatching { tokenStore.getOwnerId() }.getOrNull().orEmpty() }
    val api = remember(jwt) { CreatorApi(jwt = jwt) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val drawerState = rememberDrawerState(initialValue = androidx.compose.material3.DrawerValue.Closed)
    val config = LocalConfiguration.current
    val maxSheet = (config.screenHeightDp.dp * 0.96f)

    var tab by remember { mutableStateOf(DesignDetailTab.Overview) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var designJson by remember { mutableStateOf<JSONObject?>(null) }

    var draftPrompt by remember { mutableStateOf("") }
    var draftVisibility by remember { mutableStateOf("private") }
    var draftMeta by remember { mutableStateOf(JSONObject()) }
    var baselineSerialized by remember { mutableStateOf("") }

    var saving by remember { mutableStateOf(false) }
    var historyItems by remember { mutableStateOf<List<org.json.JSONObject>>(emptyList()) }
    var historyLoading by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var moveName by remember { mutableStateOf("") }

    val designId = design.id ?: design.designId ?: ""

    fun recomputeBaseline() {
        val o = JSONObject()
            .put("prompt", draftPrompt)
            .put("visibility", draftVisibility)
            .put("metadata", draftMeta)
        baselineSerialized = o.toString()
    }

    fun isDirtyOverview(): Boolean {
        if (baselineSerialized.isEmpty()) return false
        val o = JSONObject()
            .put("prompt", draftPrompt)
            .put("visibility", draftVisibility)
            .put("metadata", draftMeta)
        return o.toString() != baselineSerialized
    }

    fun isDirtyDetails(): Boolean = isDirtyOverview()

    LaunchedEffect(designId, ownerId) {
        if (designId.isBlank() || ownerId.isBlank()) {
            loading = false
            loadError = "missing id"
            return@LaunchedEffect
        }
        loading = true
        loadError = null
        try {
            val res = withContext(Dispatchers.IO) { api.getDesign(ownerId, designId) }
            if (!res.optBoolean("ok", false)) {
                loadError = res.optString("error", "load failed")
                loading = false
                return@LaunchedEffect
            }
            val d = res.optJSONObject("design")
            if (d == null) {
                loadError = "no design"
                loading = false
                return@LaunchedEffect
            }
            designJson = d
            draftPrompt = d.optString("prompt", "").ifBlank {
                d.optJSONObject("metadata")?.optString("user_prompt", "").orEmpty()
            }
            draftVisibility = d.optString("visibility", "private").lowercase().let {
                if (it == "public") "public" else "private"
            }
            val meta = d.optJSONObject("metadata") ?: JSONObject()
            draftMeta = try {
                JSONObject(meta.toString())
            } catch (_: Exception) {
                JSONObject()
            }
            recomputeBaseline()
        } catch (e: Exception) {
            loadError = e.message
        } finally {
            loading = false
        }
    }

    fun loadHistory() {
        if (designId.isBlank() || ownerId.isBlank()) return
        scope.launch {
            historyLoading = true
            try {
                val res = withContext(Dispatchers.IO) { api.listDesignMetadataHistory(ownerId, designId, 40) }
                val arr = res.optJSONArray("items") ?: org.json.JSONArray()
                val list = mutableListOf<org.json.JSONObject>()
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { list.add(it) }
                }
                historyItems = list
            } catch (_: Exception) {
                historyItems = emptyList()
            } finally {
                historyLoading = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E293B),
        dragHandle = null,
        modifier = Modifier.heightIn(max = maxSheet)
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.background(Color(0xFF0F172A))
                ) {
                    Text(
                        t("creator.design_detail.nav_title", "Design"),
                        modifier = Modifier.padding(16.dp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    for (dest in DesignDetailTab.values()) {
                        val label = when (dest) {
                            DesignDetailTab.Overview -> t("creator.design_detail.tab_overview", "Design Overview")
                            DesignDetailTab.Reference -> t("creator.design_detail.tab_reference", "Design Reference")
                            DesignDetailTab.Details -> t("creator.design_detail.tab_details", "Design Details")
                        }
                        val selected = tab == dest
                        Text(
                            label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    tab = dest
                                    scope.launch { drawerState.close() }
                                }
                                .padding(16.dp),
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) EazColors.Orange else Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            },
            content = {
                Scaffold(
                    containerColor = Color(0xFF1E293B),
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    draftMeta.optString("title").ifBlank { design.title },
                                    color = Color.White,
                                    maxLines = 1
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, contentDescription = t("creator.design_detail.menu", "Menu"), tint = Color.White)
                                }
                            },
                            actions = {
                                IconButton(onClick = onDismiss) {
                                    Icon(Icons.Default.Close, contentDescription = t("accessibility.close", "Close"), tint = Color.White)
                                }
                            },
                            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E293B))
                        )
                    },
                    bottomBar = {
                        when (tab) {
                            DesignDetailTab.Overview -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TextButton(onClick = {
                                        if (designId.isNotBlank()) {
                                            onRequestGeneratorPrefill(GeneratorPrefillRequest(designId, "remix"))
                                            onDismiss()
                                        }
                                    }) { Text(t("creator.design_detail.remix", "Remix"), color = EazColors.Orange) }
                                    TextButton(onClick = {
                                        if (designId.isNotBlank()) {
                                            onRequestGeneratorPrefill(GeneratorPrefillRequest(designId, "regenerate"))
                                            onDismiss()
                                        }
                                    }) { Text(t("creator.design_detail.generate_new", "Generate New"), color = EazColors.Orange) }
                                    TextButton(onClick = { showMoveDialog = true }) {
                                        Text(t("creator.design_detail.move", "Move"), color = Color.White)
                                    }
                                    TextButton(onClick = { showDeleteConfirm = true }) {
                                        Text(t("creator.design_detail.delete", "Delete"), color = Color(0xFFFF6B6B))
                                    }
                                }
                            }
                            DesignDetailTab.Reference -> Spacer(Modifier.height(0.dp))
                            DesignDetailTab.Details -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TextButton(
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    val res = withContext(Dispatchers.IO) {
                                                        api.regenerateDesignMetadata(ownerId, designId)
                                                    }
                                                    if (res.optBoolean("ok", false)) {
                                                        val m = res.optJSONObject("metadata") ?: JSONObject()
                                                        draftMeta = m
                                                    }
                                                } catch (_: Exception) {}
                                            }
                                        }
                                    ) { Text(t("creator.design_detail.gen_meta", "Generate New"), color = EazColors.Orange) }
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                saving = true
                                                try {
                                                    val body = JSONObject()
                                                        .put("design_id", designId)
                                                        .put("owner_id", ownerId)
                                                        .put("metadata", draftMeta)
                                                        .put("history_source", "manual_save")
                                                    val res = withContext(Dispatchers.IO) { api.updateDesign(body) }
                                                    if (res.optBoolean("ok", false)) {
                                                        recomputeBaseline()
                                                    }
                                                } catch (_: Exception) {
                                                } finally {
                                                    saving = false
                                                }
                                            }
                                        },
                                        enabled = isDirtyDetails() && !saving
                                    ) { Text(t("creator.common.save", "Save")) }
                                    TextButton(onClick = {
                                        loadHistory()
                                        showHistoryDialog = true
                                    }) { Text(t("creator.design_detail.reset", "Reset"), color = Color.White) }
                                }
                            }
                        }
                    }
                ) { padding ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .padding(padding)
                    ) {
                        when {
                            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = EazColors.Orange)
                            }
                            loadError != null -> Text(loadError ?: "", color = Color(0xFFFF6B6B), modifier = Modifier.padding(16.dp))
                            else -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp)
                                ) {
                                    val previewUrl = designJson?.optString("preview_url").orEmpty().ifBlank {
                                        design.imageUrl
                                    }
                                    if (previewUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = previewUrl,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f),
                                            contentScale = ContentScale.Fit
                                        )
                                        Spacer(Modifier.height(12.dp))
                                    }
                                    when (tab) {
                                        DesignDetailTab.Overview -> {
                                            OutlinedTextField(
                                                value = draftMeta.optString("title").ifBlank { design.title },
                                                onValueChange = { v ->
                                                    draftMeta.put("title", v)
                                                },
                                                label = { Text(t("creator.design_detail.design_title", "Title"), color = Color.White.copy(0.7f)) },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White
                                                )
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            OutlinedTextField(
                                                value = draftPrompt,
                                                onValueChange = { draftPrompt = it },
                                                label = { Text(t("creator.design_detail.design_prompt", "Design prompt"), color = Color.White.copy(0.7f)) },
                                                modifier = Modifier.fillMaxWidth(),
                                                minLines = 3,
                                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White
                                                )
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text(t("creator.design_detail.visibility", "Visibility"), color = Color.White)
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                FilterChip(
                                                    selected = draftVisibility == "private",
                                                    onClick = { draftVisibility = "private" },
                                                    label = { Text(t("creator.design_detail.private", "Private")) }
                                                )
                                                FilterChip(
                                                    selected = draftVisibility == "public",
                                                    onClick = { draftVisibility = "public" },
                                                    label = { Text(t("creator.design_detail.public", "Public")) }
                                                )
                                            }
                                            Spacer(Modifier.height(16.dp))
                                            val shopUrl = "$storeBaseUrl/pages/my-creations?design_id=$designId"
                                            Button(
                                                onClick = {
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(shopUrl)))
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = EazColors.Orange)
                                            ) {
                                                Text(t("creator.design_detail.view_in_shop", "View in shop"), color = Color.White)
                                            }
                                            if (isDirtyOverview()) {
                                                Spacer(Modifier.height(12.dp))
                                                Button(
                                                    onClick = {
                                                        scope.launch {
                                                            saving = true
                                                            try {
                                                                val body = JSONObject()
                                                                    .put("design_id", designId)
                                                                    .put("owner_id", ownerId)
                                                                    .put("prompt", draftPrompt)
                                                                    .put("visibility", draftVisibility)
                                                                    .put("metadata", draftMeta)
                                                                    .put("history_source", "manual_save")
                                                                val res = withContext(Dispatchers.IO) { api.updateDesign(body) }
                                                                if (res.optBoolean("ok", false)) {
                                                                    recomputeBaseline()
                                                                }
                                                            } catch (_: Exception) {
                                                            } finally {
                                                                saving = false
                                                            }
                                                        }
                                                    },
                                                    enabled = !saving,
                                                    modifier = Modifier.fillMaxWidth()
                                                ) { Text(t("creator.common.save", "Save")) }
                                            }
                                        }
                                        DesignDetailTab.Reference -> {
                                            MetaReadonlyRows(draftMeta, t)
                                        }
                                        DesignDetailTab.Details -> {
                                            OutlinedTextField(
                                                value = draftMeta.optString("title"),
                                                onValueChange = { draftMeta.put("title", it) },
                                                label = { Text(t("creator.design_detail.meta_title", "SEO / Title"), color = Color.White.copy(0.7f)) },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White
                                                )
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            OutlinedTextField(
                                                value = draftMeta.optString("description"),
                                                onValueChange = { draftMeta.put("description", it) },
                                                label = { Text(t("creator.design_detail.meta_description", "Description"), color = Color.White.copy(0.7f)) },
                                                modifier = Modifier.fillMaxWidth(),
                                                minLines = 4,
                                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White
                                                )
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                t("creator.design_detail.raw_meta_hint", "Additional fields are preserved when you save."),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.White.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            val res = withContext(Dispatchers.IO) { api.deleteDesign(ownerId, designId) }
                            if (res.optBoolean("ok", false)) {
                                showDeleteConfirm = false
                                onDismiss()
                            }
                        } catch (_: Exception) {}
                    }
                }) { Text(t("creator.design_detail.delete", "Delete")) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(t("creator.common.cancel", "Cancel")) }
            },
            title = { Text(t("creator.design_detail.delete_confirm", "Delete this design?")) },
            text = { Text(t("creator.design_detail.delete_hint", "Published products may be unpublished.")) }
        )
    }

    if (showMoveDialog) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                val res = withContext(Dispatchers.IO) {
                                    api.transferDesign(ownerId, designId, moveName.trim())
                                }
                                if (res.optBoolean("ok", false)) {
                                    showMoveDialog = false
                                    onDismiss()
                                }
                            } catch (_: Exception) {}
                        }
                    },
                    enabled = moveName.isNotBlank()
                ) { Text(t("creator.design_detail.move", "Move")) }
            },
            dismissButton = {
                TextButton(onClick = { showMoveDialog = false }) { Text(t("creator.common.cancel", "Cancel")) }
            },
            title = { Text(t("creator.design_detail.move_title", "Transfer to creator")) },
            text = {
                OutlinedTextField(
                    value = moveName,
                    onValueChange = { moveName = it },
                    label = { Text(t("creator.design_detail.new_creator_name", "Creator name")) }
                )
            }
        )
    }

    if (showHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showHistoryDialog = false },
            confirmButton = {
                TextButton(onClick = { showHistoryDialog = false }) { Text(t("creator.common.close", "Close")) }
            },
            title = { Text(t("creator.design_detail.history_title", "Snapshot history")) },
            text = {
                if (historyLoading) {
                    CircularProgressIndicator()
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(historyItems) { row ->
                            val ts = row.optLong("created_at", 0L)
                            val label = row.optString("source", "")
                            val df = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                            val title = df.format(Date(ts)) + " · " + label
                            Text(
                                title,
                                color = EazColors.Orange,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val snap = row.optJSONObject("snapshot") ?: return@clickable
                                        draftPrompt = snap.optString("prompt", draftPrompt)
                                        draftVisibility = snap.optString("visibility", draftVisibility).lowercase().let {
                                            if (it == "public") "public" else "private"
                                        }
                                        val m = snap.optJSONObject("metadata")
                                        if (m != null) draftMeta = try {
                                            JSONObject(m.toString())
                                        } catch (_: Exception) {
                                            draftMeta
                                        }
                                        showHistoryDialog = false
                                    }
                                    .padding(vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun MetaReadonlyRows(meta: JSONObject, t: (String, String) -> String) {
    val keys = meta.keys().asSequence().toList().sorted()
    for (k in keys) {
        val v = meta.opt(k)
        val s = when (v) {
            is String -> v
            else -> v?.toString() ?: ""
        }
        if (s.isBlank()) continue
        Text(
            k,
            color = Color.White.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall
        )
        Text(s, color = Color.White, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
    }
}
