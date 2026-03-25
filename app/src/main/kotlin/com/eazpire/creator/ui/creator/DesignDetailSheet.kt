package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class DesignDetailTab {
    Overview, Reference, Details
}

/** Metadata keys that reflect user/generation inputs — not AI-generated SEO fields. */
private val REFERENCE_META_KEYS_ORDER = listOf(
    "user_prompt",
    "design_prompt",
    "final_prompt",
    "system_prompt",
    "user_image_url",
    "design_source",
    "ratio",
    "design_art"
)

private fun normalizeTagsString(raw: String): String {
    val parts = raw.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase(Locale.getDefault()) }
    return parts.joinToString(", ")
}

private fun parseTagsList(tagsStr: String): List<String> =
    tagsStr.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase(Locale.getDefault()) }

private fun jsonArrayToStringList(arr: JSONArray?): List<String> {
    if (arr == null) return emptyList()
    val out = mutableListOf<String>()
    for (i in 0 until arr.length()) {
        arr.optString(i)?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
    }
    return out.distinctBy { it.lowercase(Locale.getDefault()) }
}

private fun getTopicList(meta: JSONObject): List<String> {
    val a = meta.optJSONArray("topic") ?: meta.optJSONArray("topics")
    if (a != null) return jsonArrayToStringList(a)
    val s = meta.optString("topic").ifBlank { meta.optString("topics") }
    if (s.isNotBlank()) {
        return s.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.getDefault()) }
    }
    return emptyList()
}

private fun getSubtopicList(meta: JSONObject): List<String> {
    val a = meta.optJSONArray("subtopic") ?: meta.optJSONArray("subtopics")
    if (a != null) return jsonArrayToStringList(a)
    val s = meta.optString("subtopic").ifBlank { meta.optString("subtopics") }
    if (s.isNotBlank()) {
        return s.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.getDefault()) }
    }
    return emptyList()
}

private fun replaceDraftMeta(draftMeta: JSONObject, block: JSONObject.() -> Unit): JSONObject {
    val n = JSONObject(draftMeta.toString())
    n.block()
    return n
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val scope = rememberCoroutineScope()
    val jwt = remember { runCatching { tokenStore.getJwt() }.getOrNull() }
    val ownerId = remember { runCatching { tokenStore.getOwnerId() }.getOrNull().orEmpty() }
    val api = remember(jwt) { CreatorApi(jwt = jwt) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
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

    var newTopicLine by remember { mutableStateOf("") }
    var newSubtopicLine by remember { mutableStateOf("") }

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

    LaunchedEffect(showHistoryDialog) {
        if (showHistoryDialog) loadHistory()
    }

    val drawerBg = Color(0xFF020617)
    val drawerItemSelected = Color(0xFF1E293B)
    val sheetBg = Color(0xFF0F172A)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        dragHandle = null,
        modifier = Modifier.heightIn(max = maxSheet)
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.background(drawerBg),
                    drawerContainerColor = drawerBg,
                    drawerContentColor = Color.White
                ) {
                    Text(
                        t("creator.design_detail.nav_title", "Design"),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
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
                                .background(if (selected) drawerItemSelected else Color.Transparent)
                                .clickable {
                                    tab = dest
                                    scope.launch { drawerState.close() }
                                }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) EazColors.Orange else Color.White.copy(alpha = 0.85f),
                            fontSize = 15.sp
                        )
                    }
                }
            },
            content = {
                Scaffold(
                    containerColor = sheetBg,
                    topBar = {
                        TopAppBar(
                            modifier = Modifier
                                .height(52.dp)
                                .offset(y = (-6).dp),
                            title = {
                                Text(
                                    draftMeta.optString("title").ifBlank { design.title },
                                    color = Color.White,
                                    maxLines = 1,
                                    fontSize = 17.sp
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(
                                        Icons.Default.Menu,
                                        contentDescription = t("creator.design_detail.menu", "Menu"),
                                        tint = Color.White
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = onDismiss) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = t("accessibility.close", "Close"),
                                        tint = Color.White
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = sheetBg)
                        )
                    },
                    bottomBar = {
                        when (tab) {
                            DesignDetailTab.Overview -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(sheetBg)
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (designId.isNotBlank()) {
                                                onRequestGeneratorPrefill(GeneratorPrefillRequest(designId, "remix"))
                                                onDismiss()
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = t("creator.design_detail.content_desc_remix", "Remix"),
                                            tint = EazColors.Orange
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            if (designId.isNotBlank()) {
                                                onRequestGeneratorPrefill(GeneratorPrefillRequest(designId, "regenerate"))
                                                onDismiss()
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = t("creator.design_detail.content_desc_generate_new", "Generate new"),
                                            tint = EazColors.Orange
                                        )
                                    }
                                    IconButton(onClick = { showMoveDialog = true }) {
                                        Icon(
                                            Icons.Default.DriveFileMove,
                                            contentDescription = t("creator.design_detail.content_desc_move", "Move design"),
                                            tint = Color.White
                                        )
                                    }
                                    IconButton(onClick = { showDeleteConfirm = true }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = t("creator.design_detail.content_desc_delete", "Delete design"),
                                            tint = Color(0xFFFF6B6B)
                                        )
                                    }
                                }
                            }
                            DesignDetailTab.Reference -> Spacer(Modifier.height(0.dp))
                            DesignDetailTab.Details -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(sheetBg)
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                try {
                                                    val res = withContext(Dispatchers.IO) {
                                                        api.regenerateDesignMetadata(ownerId, designId)
                                                    }
                                                    if (res.optBoolean("ok", false)) {
                                                        val m = res.optJSONObject("metadata") ?: JSONObject()
                                                        draftMeta = JSONObject(m.toString())
                                                        recomputeBaseline()
                                                    }
                                                } catch (_: Exception) {
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = t("creator.design_detail.content_desc_regenerate", "Regenerate metadata"),
                                            tint = EazColors.Orange
                                        )
                                    }
                                    IconButton(
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
                                    ) {
                                        Icon(
                                            Icons.Default.Save,
                                            contentDescription = t("creator.design_detail.content_desc_save", "Save"),
                                            tint = if (isDirtyDetails() && !saving) EazColors.Orange else Color.White.copy(alpha = 0.35f)
                                        )
                                    }
                                    IconButton(onClick = {
                                        showHistoryDialog = true
                                    }) {
                                        Icon(
                                            Icons.Default.History,
                                            contentDescription = t("creator.design_detail.content_desc_history", "History"),
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) { padding ->
                    val previewUrl = designJson?.optString("preview_url").orEmpty().ifBlank {
                        design.imageUrl
                    }
                    Column(
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
                                if (tab == DesignDetailTab.Overview && previewUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = previewUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f),
                                        contentScale = ContentScale.Fit
                                    )
                                }
                                Column(
                                    modifier = Modifier
                                        .weight(1f, fill = true)
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .padding(16.dp)
                                ) {
                                    when (tab) {
                                        DesignDetailTab.Overview -> {
                                            OutlinedTextField(
                                                value = draftPrompt,
                                                onValueChange = {},
                                                readOnly = true,
                                                label = {
                                                    Text(
                                                        t("creator.design_detail.design_prompt", "Design prompt"),
                                                        color = Color.White.copy(alpha = 0.7f)
                                                    )
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                minLines = 3,
                                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                                    focusedTextColor = Color.White.copy(alpha = 0.95f),
                                                    unfocusedTextColor = Color.White.copy(alpha = 0.95f),
                                                    focusedBorderColor = Color.White.copy(alpha = 0.4f),
                                                    unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                                                )
                                            )
                                            Spacer(Modifier.height(12.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    t("creator.design_detail.visibility", "Visibility"),
                                                    color = Color.White,
                                                    fontSize = 15.sp
                                                )
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        if (draftVisibility == "public") {
                                                            t("creator.design_detail.visibility_public", "Public")
                                                        } else {
                                                            t("creator.design_detail.private", "Private")
                                                        },
                                                        color = Color.White.copy(alpha = 0.85f),
                                                        modifier = Modifier.padding(end = 8.dp),
                                                        fontSize = 14.sp
                                                    )
                                                    Switch(
                                                        checked = draftVisibility == "public",
                                                        onCheckedChange = { on ->
                                                            draftVisibility = if (on) "public" else "private"
                                                        },
                                                        colors = SwitchDefaults.colors(
                                                            checkedThumbColor = Color.White,
                                                            checkedTrackColor = EazColors.Orange,
                                                            uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                                                            uncheckedTrackColor = Color.White.copy(alpha = 0.25f)
                                                        )
                                                    )
                                                }
                                            }
                                            if (isDirtyOverview()) {
                                                Spacer(Modifier.height(16.dp))
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
                                                    modifier = Modifier.fillMaxWidth(),
                                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                                        containerColor = EazColors.Orange
                                                    )
                                                ) {
                                                    Text(t("creator.design_detail.save", "Save"), color = Color.White)
                                                }
                                            }
                                        }
                                        DesignDetailTab.Reference -> {
                                            GenerationReferenceBlock(
                                                designJson = designJson,
                                                draftMeta = draftMeta,
                                                t = t
                                            )
                                        }
                                        DesignDetailTab.Details -> {
                                            DesignDetailsEditor(
                                                draftMeta = draftMeta,
                                                onDraftMetaChange = { draftMeta = it },
                                                newTopicLine = newTopicLine,
                                                onNewTopicLineChange = { newTopicLine = it },
                                                newSubtopicLine = newSubtopicLine,
                                                onNewSubtopicLineChange = { newSubtopicLine = it },
                                                t = t
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
                            val lineTitle = df.format(Date(ts)) + " · " + label
                            Text(
                                lineTitle,
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
                                        if (m != null) {
                                            draftMeta = try {
                                                JSONObject(m.toString())
                                            } catch (_: Exception) {
                                                draftMeta
                                            }
                                        }
                                        newTopicLine = ""
                                        newSubtopicLine = ""
                                        showHistoryDialog = false
                                        recomputeBaseline()
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
private fun GenerationReferenceBlock(
    designJson: JSONObject?,
    draftMeta: JSONObject,
    t: (String, String) -> String
) {
    val prompt = designJson?.optString("prompt").orEmpty().trim()
    val parentId = designJson?.optString("parent_design_id").orEmpty().trim()

    Text(
        t("creator.design_detail.reference_section_prompt", "Generation prompt"),
        color = Color.White.copy(alpha = 0.65f),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium
    )
    Spacer(Modifier.height(6.dp))
    Text(
        if (prompt.isNotBlank()) prompt else "—",
        color = Color.White,
        fontSize = 18.sp,
        lineHeight = 24.sp
    )
    Spacer(Modifier.height(16.dp))

    if (parentId.isNotBlank()) {
        Text(
            t("creator.design_detail.reference_parent_design", "Parent design"),
            color = Color.White.copy(alpha = 0.65f),
            fontSize = 13.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(parentId, color = Color.White, fontSize = 17.sp)
        Spacer(Modifier.height(16.dp))
    }

    Text(
        t("creator.design_detail.reference_section_inputs", "Inputs"),
        color = Color.White.copy(alpha = 0.65f),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium
    )
    Spacer(Modifier.height(8.dp))

    for (key in REFERENCE_META_KEYS_ORDER) {
        val raw = draftMeta.opt(key) ?: continue
        val s = when (raw) {
            is String -> raw.trim()
            else -> raw.toString().trim()
        }
        if (s.isBlank()) continue
        Text(
            key,
            color = Color.White.copy(alpha = 0.55f),
            style = MaterialTheme.typography.labelMedium,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            s,
            color = Color.White,
            fontSize = 17.sp,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(12.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DesignDetailsEditor(
    draftMeta: JSONObject,
    onDraftMetaChange: (JSONObject) -> Unit,
    newTopicLine: String,
    onNewTopicLineChange: (String) -> Unit,
    newSubtopicLine: String,
    onNewSubtopicLineChange: (String) -> Unit,
    t: (String, String) -> String
) {
    val tagsStr = draftMeta.optString("tags")
    val tagList = remember(tagsStr) { parseTagsList(tagsStr) }

    OutlinedTextField(
        value = draftMeta.optString("title"),
        onValueChange = { v ->
            onDraftMetaChange(replaceDraftMeta(draftMeta) { put("title", v) })
        },
        label = { Text(t("creator.design_detail.meta_title", "Title"), color = Color.White.copy(0.7f)) },
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        )
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value = draftMeta.optString("description"),
        onValueChange = { v ->
            onDraftMetaChange(replaceDraftMeta(draftMeta) { put("description", v) })
        },
        label = {
            Text(
                t("creator.design_detail.design_description", "Design description"),
                color = Color.White.copy(0.7f)
            )
        },
        modifier = Modifier.fillMaxWidth(),
        minLines = 4,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        )
    )

    Spacer(Modifier.height(16.dp))
    Text(t("creator.design_detail.tags_label", "Tags"), color = Color.White, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = tagsStr,
        onValueChange = { v ->
            onDraftMetaChange(replaceDraftMeta(draftMeta) { put("tags", normalizeTagsString(v)) })
        },
        label = {
            Text(
                t("creator.design_detail.tags_input_hint", "Add tags, comma-separated"),
                color = Color.White.copy(0.7f)
            )
        },
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
        )
    )
    Spacer(Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        tagList.forEach { tag ->
            InputChip(
                selected = false,
                onClick = {
                    val next = tagList.filter { it != tag }
                    onDraftMetaChange(
                        replaceDraftMeta(draftMeta) {
                            put("tags", next.joinToString(", "))
                        }
                    )
                },
                label = { Text(tag, color = Color.White, fontSize = 13.sp) },
                trailingIcon = {
                    Icon(Icons.Default.Close, contentDescription = null, tint = Color.White.copy(0.8f))
                }
            )
        }
    }

    Spacer(Modifier.height(20.dp))
    Text(t("creator.design_detail.topics_label", "Topics"), color = Color.White, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    val topics = getTopicList(draftMeta)
    topics.forEachIndexed { idx, item ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(item, color = Color.White, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                val next = topics.toMutableList().also { it.removeAt(idx) }
                onDraftMetaChange(replaceDraftMeta(draftMeta) { put("topic", JSONArray(next)) })
            }) {
                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color(0xFFFF6B6B))
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = newTopicLine,
            onValueChange = onNewTopicLineChange,
            modifier = Modifier.weight(1f),
            label = { Text(t("creator.design_detail.add_row_hint", "Add…"), color = Color.White.copy(0.6f)) },
            singleLine = true,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
        TextButton(onClick = {
            val add = newTopicLine.trim()
            if (add.isEmpty()) return@TextButton
            val next = topics.toMutableList()
            if (next.none { it.equals(add, ignoreCase = true) }) next.add(add)
            onDraftMetaChange(replaceDraftMeta(draftMeta) { put("topic", JSONArray(next)) })
            onNewTopicLineChange("")
        }) { Text(t("creator.design_detail.add", "Add"), color = EazColors.Orange) }
    }

    Spacer(Modifier.height(16.dp))
    Text(t("creator.design_detail.subtopics_label", "Subtopics"), color = Color.White, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(6.dp))
    val subtopics = getSubtopicList(draftMeta)
    subtopics.forEachIndexed { idx, item ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(item, color = Color.White, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                val next = subtopics.toMutableList().also { it.removeAt(idx) }
                onDraftMetaChange(replaceDraftMeta(draftMeta) { put("subtopic", JSONArray(next)) })
            }) {
                Icon(Icons.Default.Close, contentDescription = "Remove", tint = Color(0xFFFF6B6B))
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = newSubtopicLine,
            onValueChange = onNewSubtopicLineChange,
            modifier = Modifier.weight(1f),
            label = { Text(t("creator.design_detail.add_row_hint", "Add…"), color = Color.White.copy(0.6f)) },
            singleLine = true,
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )
        TextButton(onClick = {
            val add = newSubtopicLine.trim()
            if (add.isEmpty()) return@TextButton
            val next = subtopics.toMutableList()
            if (next.none { it.equals(add, ignoreCase = true) }) next.add(add)
            onDraftMetaChange(replaceDraftMeta(draftMeta) { put("subtopic", JSONArray(next)) })
            onNewSubtopicLineChange("")
        }) { Text(t("creator.design_detail.add", "Add"), color = EazColors.Orange) }
    }
}
