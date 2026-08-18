package com.eazpire.creator.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.modal.EazBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val Teal = Color(0xFF0D9488)
private val TealHi = Color(0xFF2DD4BF)
private val Panel = Color(0xFF151B2B)

internal data class StudioUserFont(
    val id: String,
    val name: String,
    val source: String,
    val prompt: String,
    val glyphCount: Int,
    val canDownload: Boolean,
    val canEdit: Boolean,
    val fontFileUrl: String?,
    val raw: JSONObject
)

internal fun parseStudioUserFont(row: JSONObject): StudioUserFont? {
    val id = row.optString("id", "").trim()
    if (id.isEmpty()) return null
    return StudioUserFont(
        id = id,
        name = row.optString("name", "Font"),
        source = row.optString("source", ""),
        prompt = row.optString("prompt", ""),
        glyphCount = row.optInt("glyph_count", 0),
        canDownload = row.optBoolean("can_download", false),
        canEdit = row.optBoolean("can_edit", false),
        fontFileUrl = row.optString("font_file_url", "").takeIf { it.isNotBlank() },
        raw = row
    )
}

internal fun parseFontList(data: JSONObject): List<StudioUserFont> {
    val arr = data.optJSONArray("items") ?: data.optJSONArray("fonts") ?: return emptyList()
    val out = mutableListOf<StudioUserFont>()
    for (i in 0 until arr.length()) {
        val row = arr.optJSONObject(i) ?: continue
        parseStudioUserFont(row)?.let { out.add(it) }
    }
    return out
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ShopFontGeneratorSheet(
    api: CreatorApi,
    ownerId: String?,
    translationStore: TranslationStore,
    translation: (String, String) -> String,
    onDismiss: () -> Unit,
    onRequireLogin: () -> Unit,
    onApplyFont: (StudioUserFont) -> Unit = {}
) {
    val t = translation
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var tab by remember { mutableStateOf("generator") }
    var prompt by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var viewer by remember { mutableStateOf("Aa 123 ÄÖÜ") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var completeLeft by remember { mutableStateOf<Int?>(null) }
    var glyphLeft by remember { mutableStateOf<Int?>(null) }
    var completeMax by remember { mutableStateOf(10) }
    var glyphMax by remember { mutableStateOf(10) }
    var active by remember { mutableStateOf<JSONObject?>(null) }
    var mine by remember { mutableStateOf<List<StudioUserFont>>(emptyList()) }
    var pendingGlyph by remember { mutableStateOf<String?>(null) }
    var miniPrompt by remember { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<StudioUserFont?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val oid = ownerId?.trim().orEmpty()
        if (uri == null) return@rememberLauncherForActivityResult
        if (oid.isEmpty()) {
            onRequireLogin()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            busy = true
            error = null
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (bytes == null) {
                    error = "Could not read file."
                    return@launch
                }
                val filename = uri.lastPathSegment?.substringAfterLast('/') ?: "font.ttf"
                val mime = context.contentResolver.getType(uri)?.ifBlank { null } ?: "application/octet-stream"
                val res = withContext(Dispatchers.IO) {
                    api.fontGeneratorUpload(oid, bytes, filename, mime)
                }
                if (res.optBoolean("ok", false)) {
                    refreshLists(api, oid) { mine = it }
                    applyLimits(res.optJSONObject("limits")) { c, cm, g, gm ->
                        completeLeft = c; completeMax = cm; glyphLeft = g; glyphMax = gm
                    }
                    tab = "mine"
                } else {
                    error = res.optString("message", res.optString("error", "Upload failed."))
                }
            } finally {
                busy = false
            }
        }
    }

    fun loadAll() {
        val oid = ownerId?.trim().orEmpty()
        if (oid.isEmpty()) return
        scope.launch {
            val limits = withContext(Dispatchers.IO) { api.fontGeneratorLimits(oid) }
            applyLimits(limits) { c, cm, g, gm ->
                completeLeft = c; completeMax = cm; glyphLeft = g; glyphMax = gm
            }
            refreshLists(api, oid) { mine = it }
        }
    }

    LaunchedEffect(ownerId) { loadAll() }
    BackHandler(onBack = onDismiss)

    EazBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Panel,
        maxHeightFraction = 0.94f
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        t("design_studio.shop.design_generator_eyebrow", "Design Studio"),
                        color = TealHi,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        t("design_studio.shop.font_generator", "Font Generator"),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = t("creator.common.close", "Close"), tint = Color.White)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(
                    "generator" to t("design_studio.shop.font_tab_generator", "Generator"),
                    "upload" to t("design_studio.shop.font_tab_upload", "Upload Font"),
                    "mine" to t("design_studio.shop.font_tab_my_fonts", "My Fonts")
                ).forEach { (id, label) ->
                    val on = tab == id
                    Text(
                        text = label,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) TealHi else Color(0xFF0F172A))
                            .clickable { tab = id }
                            .padding(vertical = 10.dp),
                        color = if (on) Color(0xFF042F2E) else Color(0xFFCBD5E1),
                        fontWeight = FontWeight.SemiBold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            val cLeft = completeLeft
            val gLeft = glyphLeft
            if (cLeft != null && gLeft != null) {
                Text(
                    t("design_studio.shop.font_limit_hint", "{{ remaining }} of {{ max }} fonts left today")
                        .replace("{{ remaining }}", cLeft.toString())
                        .replace("{{ max }}", completeMax.toString()) +
                        " · " +
                        t("design_studio.shop.font_glyph_limit_hint", "{{ remaining }} of {{ max }} glyph retries left today")
                            .replace("{{ remaining }}", gLeft.toString())
                            .replace("{{ max }}", glyphMax.toString()),
                    color = TealHi,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            if (error != null) {
                Text(error!!, color = Color(0xFFF87171), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            }
            when (tab) {
                "upload" -> {
                    Text(
                        t("design_studio.shop.font_upload_hint", "TTF, OTF, WOFF, WOFF2 or SVG font. Private — only you can use or download it."),
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                    FontSheetBtn(onClick = { picker.launch("*/*") }, modifier = Modifier.padding(top = 12.dp)) {
                        Text(t("design_studio.shop.font_upload_choose", "Choose font file"))
                    }
                }
                "mine" -> {
                    if (mine.isEmpty()) {
                        Text(
                            t("design_studio.shop.font_my_empty", "No fonts yet. Generate or upload one."),
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .heightIn(max = 420.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            mine.forEach { font ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF0B1220))
                                        .padding(10.dp)
                                ) {
                                    Text(font.name, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text("${font.source} · ${font.glyphCount} glyphs", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                        FontSheetBtn(onClick = {
                                            active = font.raw
                                            onApplyFont(font)
                                            tab = "generator"
                                        }) { Text(t("design_studio.shop.font_open", "Open")) }
                                        if (font.canDownload) {
                                            FontSheetBtn(onClick = {
                                                scope.launch {
                                                    val oid = ownerId?.trim().orEmpty()
                                                    val res = withContext(Dispatchers.IO) { api.fontGeneratorDownload(oid, font.id) }
                                                    val url = res.optString("url", "")
                                                    if (url.isNotBlank()) {
                                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                                                        context.startActivity(intent)
                                                    }
                                                }
                                            }) { Text(t("design_studio.shop.font_download", "Download")) }
                                        }
                                        if (font.canEdit) {
                                            FontSheetBtn(onClick = { pendingDelete = font }) {
                                                Text(t("design_studio.shop.font_delete", "Delete"))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    val fieldColors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = TealHi,
                        unfocusedBorderColor = Color.White.copy(0.2f),
                        cursorColor = TealHi
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(t("design_studio.shop.font_name", "Font name")) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = fieldColors
                    )
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text(t("design_studio.shop.font_prompt", "Describe the font")) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        minLines = 3,
                        colors = fieldColors
                    )
                    FontSheetBtn(
                        onClick = {
                            val oid = ownerId?.trim().orEmpty()
                            if (oid.isEmpty()) {
                                onRequireLogin()
                                return@FontSheetBtn
                            }
                            if (prompt.isBlank()) return@FontSheetBtn
                            scope.launch {
                                busy = true
                                error = null
                                try {
                                    val res = withContext(Dispatchers.IO) { api.fontGeneratorStart(oid, prompt, name) }
                                    if (res.optBoolean("ok", false)) {
                                        active = res.optJSONObject("font")
                                        name = active?.optString("name", name) ?: name
                                        applyLimits(res.optJSONObject("limits")) { c, cm, g, gm ->
                                            completeLeft = c; completeMax = cm; glyphLeft = g; glyphMax = gm
                                        }
                                        refreshLists(api, oid) { mine = it }
                                        parseStudioUserFont(active ?: JSONObject())?.let { onApplyFont(it) }
                                    } else {
                                        error = res.optString("message", res.optString("error", "Could not generate font."))
                                    }
                                } finally {
                                    busy = false
                                }
                            }
                        },
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        Text(t("design_studio.shop.font_generate", "Generate font"))
                    }
                    OutlinedTextField(
                        value = viewer,
                        onValueChange = { viewer = it },
                        label = { Text(t("design_studio.shop.font_live_viewer", "Live text")) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = fieldColors
                    )
                    Text(viewer, color = Color.White, fontSize = 28.sp, modifier = Modifier.padding(top = 8.dp))
                    val glyphs = remember(active) { glyphChars(active) }
                    if (glyphs.isNotEmpty()) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(44.dp),
                            modifier = Modifier.heightIn(max = 280.dp).padding(top = 8.dp),
                            contentPadding = PaddingValues(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(glyphs, key = { it }) { ch ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0B1220))
                                        .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(8.dp))
                                        .combinedClickable(
                                            onClick = {},
                                            onLongClick = { pendingGlyph = ch }
                                        )
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(ch, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
            if (busy) {
                CircularProgressIndicator(color = TealHi, modifier = Modifier.align(Alignment.CenterHorizontally).padding(12.dp))
            }
        }
    }

    pendingGlyph?.let { ch ->
        AlertDialog(
            onDismissRequest = { pendingGlyph = null },
            title = { Text(t("design_studio.shop.font_generate_new", "Generate New")) },
            text = {
                Column {
                    Text(ch, fontSize = 28.sp)
                    OutlinedTextField(
                        value = miniPrompt,
                        onValueChange = { miniPrompt = it },
                        label = { Text(t("design_studio.shop.font_mini_prompt", "Mini prompt for this character")) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val oid = ownerId?.trim().orEmpty()
                    val fontId = active?.optString("id").orEmpty()
                    pendingGlyph = null
                    if (oid.isEmpty() || fontId.isEmpty()) return@TextButton
                    scope.launch {
                        busy = true
                        val res = withContext(Dispatchers.IO) {
                            api.fontGeneratorRegenerateGlyph(oid, fontId, ch, miniPrompt)
                        }
                        if (res.optBoolean("ok", false)) {
                            active = res.optJSONObject("font") ?: active
                            applyLimits(res.optJSONObject("limits")) { c, cm, g, gm ->
                                completeLeft = c; completeMax = cm; glyphLeft = g; glyphMax = gm
                            }
                        } else {
                            error = res.optString("message", res.optString("error", "Could not regenerate."))
                        }
                        busy = false
                        miniPrompt = ""
                    }
                }) { Text(t("creator.common.apply", "Apply")) }
            },
            dismissButton = {
                TextButton(onClick = { pendingGlyph = null }) { Text(t("creator.common.cancel", "Cancel")) }
            }
        )
    }

    pendingDelete?.let { font ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(t("design_studio.shop.font_delete", "Delete")) },
            text = { Text(t("design_studio.shop.font_delete_confirm", "Delete this font? This cannot be undone.")) },
            confirmButton = {
                TextButton(onClick = {
                    val oid = ownerId?.trim().orEmpty()
                    pendingDelete = null
                    scope.launch {
                        withContext(Dispatchers.IO) { api.fontGeneratorDelete(oid, font.id) }
                        refreshLists(api, oid) { mine = it }
                    }
                }) { Text(t("design_studio.shop.font_delete", "Delete")) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(t("creator.common.cancel", "Cancel")) }
            }
        )
    }
}

private val DefaultGlyphs: List<String> = buildList {
    addAll(('A'..'Z').map { it.toString() })
    addAll(('a'..'z').map { it.toString() })
    addAll(listOf("Ä", "Ö", "Ü", "ä", "ö", "ü", "ß"))
    addAll(('0'..'9').map { it.toString() })
    addAll(".,!?:;…¿¡'\"‘’“”‚„«»‹›`´-–—−_()[]{}/\\|&=@#%+=*~^€$£¥¢₹©®™°×÷±≠≈<>≤≥·•§¶†‡№".map { it.toString() })
}

private fun glyphChars(font: JSONObject?): List<String> {
    if (font == null) return emptyList()
    val map = font.optJSONObject("glyph_map")
    if (map != null && map.length() > 0) {
        val keys = mutableListOf<String>()
        val it = map.keys()
        while (it.hasNext()) keys.add(it.next())
        return keys
    }
    return DefaultGlyphs
}

private fun applyLimits(
    limits: JSONObject?,
    block: (Int, Int, Int, Int) -> Unit
) {
    if (limits == null) return
    val c = limits.optJSONObject("complete")
    val g = limits.optJSONObject("glyph")
    block(
        c?.optInt("remaining", 0) ?: 0,
        c?.optInt("max", 10) ?: 10,
        g?.optInt("remaining", 0) ?: 0,
        g?.optInt("max", 10) ?: 10
    )
}

private suspend fun refreshLists(api: CreatorApi, ownerId: String, onMine: (List<StudioUserFont>) -> Unit) {
    val data = withContext(Dispatchers.IO) { api.fontGeneratorListMine(ownerId) }
    onMine(parseFontList(data))
}

@Composable
private fun FontSheetBtn(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = Teal, contentColor = Color.White),
        shape = RoundedCornerShape(12.dp)
    ) { content() }
}
