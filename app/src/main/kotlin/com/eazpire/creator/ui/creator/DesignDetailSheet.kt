package com.eazpire.creator.ui.creator

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import com.eazpire.creator.ui.modal.EazBottomSheet
import com.eazpire.creator.ui.modal.EazModalInsets
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.billing.EazCostCatalog
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

/** Header, drawer, footer — darker band */
private val CHeaderFooter = Color(0xFF020617)

/** Main sheet / scroll area */
private val CContent = Color(0xFF1E293B)

/** Light surface for design preview / carousel */
private val CLightSurface = Color(0xFFF1F5F9)

/** Text on dark content */
private val CTextPrimary = Color(0xFFF8FAFC)

private val CTextOnLight = Color(0xFF0F172A)

private val CTextMuted = Color(0xFF94A3B8)

private val CAccent = EazColors.Orange

/** Small metadata keys under reference (no system prompt / user_image URL) */
private val REFERENCE_EXTRA_KEYS = listOf("design_source", "ratio", "design_art")

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

private fun collectReferenceImageUrls(
    designJson: JSONObject?,
    draftMeta: JSONObject,
    designFallbackUrl: String
): List<String> {
    val seen = LinkedHashSet<String>()
    fun add(u: String?) {
        val t = u?.trim().orEmpty()
        if (t.isNotBlank()) seen.add(t)
    }
    designJson?.let { j ->
        add(j.optString("preview_url"))
        add(j.optString("original_url"))
    }
    add(draftMeta.optString("user_image_url"))
    add(designFallbackUrl)
    return seen.toList()
}

@Composable
private fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(CAccent)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            color = CTextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.15.sp
        )
    }
}

@Composable
private fun BodyTextBlock(text: String, isLarge: Boolean = false) {
    Text(
        text = if (text.isNotBlank()) text else "—",
        color = if (text.isNotBlank()) CTextPrimary else CTextMuted,
        fontSize = if (isLarge) 17.sp else 15.sp,
        lineHeight = if (isLarge) 24.sp else 22.sp,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReferenceImageCarousel(
    urls: List<String>,
    t: (String, String) -> String
) {
    SectionHeader(t("creator.design_detail.reference_images_title", "Reference images"))
    Spacer(Modifier.height(8.dp))
    Surface(
        color = CLightSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        if (urls.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    t("creator.design_detail.reference_no_images", "No reference images"),
                    color = CTextMuted,
                    fontSize = 16.sp
                )
            }
        } else {
            val pagerState = rememberPagerState(pageCount = { urls.size })
            Column {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                ) { page ->
                    AsyncImage(
                        model = urls[page],
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    repeat(urls.size) { i ->
                        Box(
                            modifier = Modifier
                                .padding(2.dp)
                                .size(if (pagerState.currentPage == i) 8.dp else 6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    if (pagerState.currentPage == i) CAccent else CTextMuted.copy(alpha = 0.45f)
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DesignPreviewLightBox(
    previewUrl: String,
    cropMode: Boolean = false,
    cropRect: CropRect = CropRect.FULL,
    onCropRectChange: (CropRect) -> Unit = {},
    cropBusy: Boolean = false,
) {
    Surface(
        color = CLightSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        var containerSize by remember { mutableStateOf(IntSize.Zero) }
        var imageSize by remember { mutableStateOf<IntSize?>(null) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(12.dp)
                .onSizeChanged { containerSize = it },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = previewUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
                onSuccess = { state ->
                    val d = state.result.drawable
                    if (d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
                        imageSize = IntSize(d.intrinsicWidth, d.intrinsicHeight)
                    }
                }
            )
            val imgSz = imageSize
            val imageDisplayRect = if (
                cropMode &&
                imgSz != null &&
                containerSize.width > 0 &&
                containerSize.height > 0
            ) {
                val scale = minOf(
                    containerSize.width.toFloat() / imgSz.width,
                    containerSize.height.toFloat() / imgSz.height
                )
                val dw = imgSz.width * scale
                val dh = imgSz.height * scale
                val left = (containerSize.width - dw) / 2f
                val top = (containerSize.height - dh) / 2f
                Rect(left, top, left + dw, top + dh)
            } else null
            if (imageDisplayRect != null) {
                CropFrameOverlay(
                    imageDisplayRect = imageDisplayRect,
                    cropRect = cropRect,
                    onCropRectChange = onCropRectChange,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (cropBusy) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CAccent)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun DesignDetailSheet(
    design: CreationDesign,
    onDismiss: () -> Unit,
    translationStore: TranslationStore,
    tokenStore: SecureTokenStore,
    storeBaseUrl: String = "https://www.eazpire.com",
    onRequestGeneratorPrefill: (GeneratorPrefillRequest) -> Unit = {},
    onGlowUpJobStarted: (jobId: String, summary: String) -> Unit = { _, _ -> },
    onOpenEazyJobs: () -> Unit = {}
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
    var saveError by remember { mutableStateOf<String?>(null) }
    var historyItems by remember { mutableStateOf<List<org.json.JSONObject>>(emptyList()) }
    var historyLoading by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRegenConfirm by remember { mutableStateOf(false) }
    var regenBusy by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var moveName by remember { mutableStateOf("") }
    var showGlowUpStyles by remember { mutableStateOf(false) }
    var showGlowUpConfirm by remember { mutableStateOf(false) }
    var glowUpStyles by remember { mutableStateOf(listOf("eazy_amplified" to "eazy Amplified")) }
    var selectedGlowUpStyleId by remember { mutableStateOf("eazy_amplified") }
    var selectedGlowUpStyleName by remember { mutableStateOf("eazy Amplified") }
    var glowUpStarting by remember { mutableStateOf(false) }
    var glowUpBalance by remember { mutableStateOf<Double?>(null) }
    var glowUpCost by remember { mutableStateOf(15.0) }
    var glowUpError by remember { mutableStateOf<String?>(null) }
    var cropMode by remember { mutableStateOf(false) }
    var cropRect by remember { mutableStateOf(CropRect.FULL) }
    var cropBusy by remember { mutableStateOf(false) }
    var cropError by remember { mutableStateOf<String?>(null) }

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

    fun applyLoadedDesign(d: JSONObject) {
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
    }

    fun applyCropToServer() {
        if (cropBusy || designId.isBlank() || ownerId.isBlank()) return
        val serverW = designJson?.optInt("width", 0) ?: 0
        val serverH = designJson?.optInt("height", 0) ?: 0
        val pixels = cropRect.toManualCropPixels(serverW, serverH)
        if (pixels == null) {
            cropError = t("creator.js.crop_failed", "Crop failed")
            return
        }
        scope.launch {
            cropBusy = true
            cropError = null
            try {
                val res = withContext(Dispatchers.IO) {
                    api.cropDesign(ownerId, designId, pixels.x, pixels.y, pixels.w, pixels.h)
                }
                if (res.optBoolean("ok", false)) {
                    val updated = res.optJSONObject("design")
                    if (updated != null) applyLoadedDesign(updated)
                    cropMode = false
                    cropRect = CropRect.FULL
                } else {
                    val code = res.optString("error", "crop_failed")
                    val detail = res.optString("message", "").trim()
                    cropError = if (detail.isNotBlank() && detail != code) {
                        t("creator.js.crop_failed", "Crop failed") + ": " + detail
                    } else {
                        t("creator.js.crop_failed", "Crop failed")
                    }
                }
            } catch (e: Exception) {
                val raw = e.message.orEmpty()
                cropError = if (raw.contains("timeout", ignoreCase = true) ||
                    raw.contains("failed to connect", ignoreCase = true) ||
                    raw.contains("Unable to resolve", ignoreCase = true)
                ) {
                    t(
                        "creator.preview_modal.crop_network_failed",
                        "Could not reach the crop server. Please try again."
                    )
                } else {
                    t("creator.js.crop_failed", "Crop failed") +
                        if (raw.isNotBlank()) ": ${raw.take(180)}" else ""
                }
            } finally {
                cropBusy = false
            }
        }
    }

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

    val drawerItemSelected = Color(0xFF1E293B)

    EazBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CContent,
        dragHandle = null,
        modifier = Modifier.heightIn(max = maxSheet)
    ) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.background(CHeaderFooter),
                    drawerContainerColor = CHeaderFooter,
                    drawerContentColor = Color.White
                ) {
                    Text(
                        t("creator.design_detail.nav_title", "Design"),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Divider(color = Color.White.copy(alpha = 0.12f))
                    Spacer(Modifier.height(4.dp))
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
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) CAccent else Color.White.copy(alpha = 0.92f),
                            fontSize = 15.sp
                        )
                    }
                }
            },
            content = {
                Scaffold(
                    containerColor = CContent,
                    topBar = {
                        TopAppBar(
                            modifier = Modifier.height(56.dp),
                            title = {
                                Text(
                                    draftMeta.optString("title").ifBlank { design.title },
                                    color = Color.White,
                                    maxLines = 1,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold
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
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = CHeaderFooter,
                                titleContentColor = Color.White,
                                navigationIconContentColor = Color.White,
                                actionIconContentColor = Color.White
                            )
                        )
                    },
                    bottomBar = {
                        when (tab) {
                            DesignDetailTab.Overview -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(EazModalInsets.stickyFooter())
                                        .background(CHeaderFooter)
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (cropMode) {
                                        IconButton(
                                            onClick = {
                                                if (!cropBusy) {
                                                    cropMode = false
                                                    cropRect = CropRect.FULL
                                                    cropError = null
                                                }
                                            },
                                            enabled = !cropBusy
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = t("creator.design_detail.content_desc_crop_cancel", "Cancel crop"),
                                                tint = Color.White
                                            )
                                        }
                                        IconButton(
                                            onClick = { applyCropToServer() },
                                            enabled = !cropBusy
                                        ) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = t("creator.design_detail.content_desc_crop_apply", "Apply crop"),
                                                tint = CAccent
                                            )
                                        }
                                        return@Row
                                    }
                                    IconButton(
                                        onClick = {
                                            cropError = null
                                            cropRect = CropRect.FULL
                                            cropMode = true
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Crop,
                                            contentDescription = t("creator.design_detail.content_desc_crop", "Crop"),
                                            tint = CAccent
                                        )
                                    }
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
                                            tint = CAccent
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            showGlowUpStyles = true
                                            glowUpError = null
                                            scope.launch {
                                                try {
                                                    val res = withContext(Dispatchers.IO) { api.listGlowUpStyles() }
                                                    val arr = res.optJSONArray("items")
                                                    if (arr != null && arr.length() > 0) {
                                                        val next = mutableListOf<Pair<String, String>>()
                                                        for (i in 0 until arr.length()) {
                                                            val o = arr.optJSONObject(i) ?: continue
                                                            val id = o.optString("id", "").trim()
                                                            val name = o.optString("name", id).trim()
                                                            if (id.isNotBlank()) next.add(id to name)
                                                        }
                                                        if (next.isNotEmpty()) glowUpStyles = next
                                                    }
                                                } catch (_: Exception) {
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = t("creator.preview_modal.glow_up", "Glow Up"),
                                            tint = CAccent
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
                                            tint = CAccent
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
                                        .then(EazModalInsets.stickyFooter())
                                        .background(CHeaderFooter)
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { if (!regenBusy) showRegenConfirm = true },
                                        enabled = !regenBusy
                                    ) {
                                        Icon(
                                            Icons.Default.AutoAwesome,
                                            contentDescription = t("creator.design_detail.content_desc_regenerate", "Regenerate metadata"),
                                            tint = CAccent
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                saving = true
                                                saveError = null
                                                try {
                                                    val body = JSONObject()
                                                        .put("design_id", designId)
                                                        .put("owner_id", ownerId)
                                                        .put("metadata", draftMeta)
                                                        .put("history_source", "manual_save")
                                                    val res = withContext(Dispatchers.IO) { api.updateDesign(body) }
                                                    if (res.optBoolean("ok", false)) {
                                                        recomputeBaseline()
                                                    } else if (res.optString("error") == "blocked_phrase") {
                                                        val phrase = res.optJSONArray("phrases")?.optString(0).orEmpty()
                                                        saveError = t(
                                                            "creator.preview_modal.meta_blocked_phrase",
                                                            "This text contains a blocked phrase ({{phrase}}) and cannot be saved."
                                                        ).replace("{{phrase}}", phrase.ifBlank { "blocked phrase" })
                                                    } else {
                                                        saveError = res.optString("message", res.optString("error", "Save failed"))
                                                    }
                                                } catch (_: Exception) {
                                                    saveError = t("creator.preview_modal.meta_save_failed", "Could not save metadata.")
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
                                            tint = if (isDirtyDetails() && !saving) CAccent else Color.White.copy(alpha = 0.35f)
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
                    val cropImageUrl = if (cropMode) {
                        designJson?.optString("original_url").orEmpty()
                            .ifBlank { design.originalUrl }
                            .ifBlank { previewUrl }
                    } else {
                        previewUrl
                    }
                    val refUrls = remember(designJson, draftMeta, previewUrl) {
                        collectReferenceImageUrls(designJson, draftMeta, previewUrl)
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .padding(padding)
                            .background(CContent)
                    ) {
                        when {
                            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = CAccent)
                            }
                            loadError != null -> Text(
                                loadError ?: "",
                                color = Color(0xFFFF6B6B),
                                modifier = Modifier.padding(16.dp)
                            )
                            else -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight()
                                ) {
                                    when (tab) {
                                        DesignDetailTab.Overview -> {
                                            if (previewUrl.isNotBlank()) {
                                                DesignPreviewLightBox(
                                                    previewUrl = cropImageUrl,
                                                    cropMode = cropMode,
                                                    cropRect = cropRect,
                                                    onCropRectChange = { cropRect = it },
                                                    cropBusy = cropBusy
                                                )
                                                cropError?.let { err ->
                                                    Text(
                                                        err,
                                                        color = Color(0xFFFF6B6B),
                                                        fontSize = 13.sp,
                                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                        DesignDetailTab.Reference -> {
                                            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                                ReferenceImageCarousel(refUrls, t)
                                            }
                                        }
                                        DesignDetailTab.Details -> {
                                            Spacer(Modifier.height(4.dp))
                                        }
                                    }
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .verticalScroll(rememberScrollState())
                                            .padding(horizontal = 16.dp, vertical = 14.dp)
                                    ) {
                                        when (tab) {
                                            DesignDetailTab.Overview -> {
                                                val glowLabel = draftMeta.optString("glow_up_style_name", "").trim()
                                                if (draftMeta.optBoolean("glow_up", false) ||
                                                    draftMeta.optString("design_source", "").equals("Glow Up", ignoreCase = true)
                                                ) {
                                                    Text(
                                                        text = if (glowLabel.isNotBlank()) {
                                                            t("creator.preview_modal.glow_up_badge", "Glow Up") + " · " + glowLabel
                                                        } else {
                                                            t("creator.preview_modal.glow_up_badge", "Glow Up")
                                                        },
                                                        color = Color(0xFF111827),
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier
                                                            .padding(bottom = 10.dp)
                                                            .background(Color(0xFFF59E0B), RoundedCornerShape(999.dp))
                                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                                    )
                                                }
                                                Surface(
                                                    color = Color(0xFF334155),
                                                    shape = RoundedCornerShape(14.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(Modifier.padding(16.dp)) {
                                                        SectionHeader(t("creator.design_detail.design_prompt", "Design prompt"))
                                                        OutlinedTextField(
                                                            value = draftPrompt,
                                                            onValueChange = {},
                                                            readOnly = true,
                                                            label = null,
                                                            modifier = Modifier.fillMaxWidth(),
                                                            minLines = 3,
                                                            colors = OutlinedTextFieldDefaults.colors(
                                                                focusedTextColor = CTextPrimary,
                                                                unfocusedTextColor = CTextPrimary,
                                                                focusedBorderColor = CAccent.copy(alpha = 0.6f),
                                                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                                                cursorColor = CAccent,
                                                                focusedContainerColor = Color.Transparent,
                                                                unfocusedContainerColor = Color.Transparent
                                                            )
                                                        )
                                                    }
                                                }
                                                Spacer(Modifier.height(12.dp))
                                                Surface(
                                                    color = Color(0xFF334155),
                                                    shape = RoundedCornerShape(14.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(horizontal = 16.dp, vertical = 14.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            t("creator.design_detail.visibility", "Visibility"),
                                                            color = CTextPrimary,
                                                            fontSize = 16.sp,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            Text(
                                                                if (draftVisibility == "public") {
                                                                    t("creator.design_detail.visibility_public", "Public")
                                                                } else {
                                                                    t("creator.design_detail.private", "Private")
                                                                },
                                                                color = CTextMuted,
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
                                                                    checkedTrackColor = CAccent,
                                                                    uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                                                                    uncheckedTrackColor = Color.White.copy(alpha = 0.25f)
                                                                )
                                                            )
                                                        }
                                                    }
                                                }
                                                if (isDirtyOverview()) {
                                                    Spacer(Modifier.height(16.dp))
                                                    Button(
                                                        onClick = {
                                                            scope.launch {
                                                                saving = true
                                                                saveError = null
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
                                                                    } else if (res.optString("error") == "blocked_phrase") {
                                                                        val phrase = res.optJSONArray("phrases")?.optString(0).orEmpty()
                                                                        saveError = t(
                                                                            "creator.preview_modal.meta_blocked_phrase",
                                                                            "This text contains a blocked phrase ({{phrase}}) and cannot be saved."
                                                                        ).replace("{{phrase}}", phrase.ifBlank { "blocked phrase" })
                                                                    } else {
                                                                        saveError = res.optString("message", res.optString("error", "Save failed"))
                                                                    }
                                                                } catch (_: Exception) {
                                                                    saveError = t("creator.preview_modal.meta_save_failed", "Could not save metadata.")
                                                                } finally {
                                                                    saving = false
                                                                }
                                                            }
                                                        },
                                                        enabled = !saving,
                                                        modifier = Modifier.fillMaxWidth(),
                                                        colors = ButtonDefaults.buttonColors(containerColor = CAccent)
                                                    ) {
                                                        Text(t("creator.design_detail.save", "Save"), color = Color.White)
                                                    }
                                                }
                                                if (!saveError.isNullOrBlank()) {
                                                    Spacer(Modifier.height(10.dp))
                                                    Text(saveError ?: "", color = Color(0xFFF87171), fontSize = 13.sp)
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
                                                if (!saveError.isNullOrBlank()) {
                                                    Text(saveError ?: "", color = Color(0xFFF87171), fontSize = 13.sp)
                                                    Spacer(Modifier.height(10.dp))
                                                }
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
            }
        )
    }

    if (showGlowUpStyles) {
        AlertDialog(
            onDismissRequest = { if (!glowUpStarting) showGlowUpStyles = false },
            containerColor = Color(0xFF1F2937),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.88f),
            title = { Text(t("creator.preview_modal.glow_up_styles", "Glow Up Styles")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    glowUpStyles.forEach { (id, name) ->
                        Button(
                            onClick = {
                                selectedGlowUpStyleId = id
                                selectedGlowUpStyleName = name
                                showGlowUpStyles = false
                                showGlowUpConfirm = true
                                glowUpBalance = null
                                glowUpCost = 15.0
                                scope.launch {
                                    try {
                                        val b = withContext(Dispatchers.IO) { api.getBalance(ownerId) }
                                        glowUpBalance = b.optDouble("balance_total", b.optDouble("balance_eaz", Double.NaN))
                                            .takeIf { !it.isNaN() }
                                        glowUpCost = EazCostCatalog.resolveCost(b, "design_generate")
                                    } catch (_: Exception) {
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CAccent, contentColor = Color(0xFF111827)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(name, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showGlowUpStyles = false }) {
                    Text(t("creator.common.cancel", "Cancel"), color = Color.White)
                }
            }
        )
    }

    if (showGlowUpConfirm) {
        val lowBalance = glowUpBalance != null && glowUpBalance!! + 1e-9 < glowUpCost
        AlertDialog(
            onDismissRequest = { if (!glowUpStarting) showGlowUpConfirm = false },
            containerColor = Color(0xFF1F2937),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.88f),
            title = { Text(t("creator.preview_modal.glow_up", "Glow Up")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        t(
                            "creator.preview_modal.confirm_glow_up_android",
                            "Create a Glow Up of this design? This creates a new design. Your original stays."
                        )
                    )
                    Text(
                        t("creator.hero_eazy.confirm_cost_label", "Cost") + ": " +
                            EazCostCatalog.fmtEaz(glowUpCost) + " EAZ"
                    )
                    glowUpBalance?.let {
                        Text(t("creator.hero_eazy.balance_label", "Balance") + ": " + EazCostCatalog.fmtEaz(it) + " EAZ")
                    }
                    if (lowBalance) {
                        Text(
                            t("creator.generator_eazy.low_balance", "Not enough EAZ."),
                            color = Color(0xFFF87171)
                        )
                    }
                    glowUpError?.let { Text(it, color = Color(0xFFF87171)) }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !glowUpStarting && !lowBalance,
                    onClick = {
                        if (glowUpStarting || designId.isBlank() || ownerId.isBlank()) return@TextButton
                        glowUpStarting = true
                        glowUpError = null
                        scope.launch {
                            try {
                                val body = JSONObject()
                                    .put("owner_id", ownerId)
                                    .put("prompt", draftPrompt)
                                    .put("image_url", design.originalUrl.ifBlank { design.previewUrl.ifBlank { design.imageUrl } })
                                    .put("parent_design_id", designId)
                                    .put("glow_up", true)
                                    .put("glow_up_style_id", selectedGlowUpStyleId)
                                    .put("design_type", design.designType ?: "classic")
                                    .put("target_product", "tshirt")
                                    .put("ratio", "portrait")
                                    .put("content_type", "design-text")
                                    .put("background", JSONObject().put("mode", "transparent"))
                                val resp = withContext(Dispatchers.IO) { api.submitGenerateJob(ownerId, body) }
                                val jobId = resp.optString("jobId", "").ifBlank { resp.optString("job_id", "") }
                                if (jobId.isBlank()) {
                                    glowUpError = resp.optString("message", "")
                                        .ifBlank { resp.optString("error", "") }
                                        .ifBlank { t("creator.preview_modal.glow_up_failed", "Glow Up could not be started.") }
                                } else {
                                    onGlowUpJobStarted(
                                        jobId,
                                        t("creator.preview_modal.glow_up", "Glow Up") + " · " + selectedGlowUpStyleName
                                    )
                                    showGlowUpConfirm = false
                                    onOpenEazyJobs()
                                    onDismiss()
                                }
                            } catch (e: Exception) {
                                glowUpError = e.message?.take(200)
                                    ?: t("creator.preview_modal.glow_up_failed", "Glow Up could not be started.")
                            } finally {
                                glowUpStarting = false
                            }
                        }
                    }
                ) {
                    Text(
                        if (glowUpStarting) t("creator.common.loading", "Loading")
                        else t("creator.generator.confirm_generate_yes", "Yes, generate"),
                        color = CAccent
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { if (!glowUpStarting) showGlowUpConfirm = false }) {
                    Text(t("creator.common.cancel", "Cancel"), color = Color.White)
                }
            }
        )
    }

    if (showRegenConfirm) {
        AlertDialog(
            onDismissRequest = { if (!regenBusy) showRegenConfirm = false },
            confirmButton = {
                TextButton(
                    enabled = !regenBusy,
                    onClick = {
                        scope.launch {
                            regenBusy = true
                            saveError = null
                            try {
                                val res = withContext(Dispatchers.IO) {
                                    api.regenerateDesignMetadata(ownerId, designId)
                                }
                                if (res.optBoolean("ok", false)) {
                                    val m = res.optJSONObject("metadata") ?: JSONObject()
                                    draftMeta = JSONObject(m.toString())
                                    recomputeBaseline()
                                } else {
                                    saveError = res.optString("message").ifBlank {
                                        res.optString("error").ifBlank {
                                            t("creator.preview_modal.meta_regenerate_failed", "Could not regenerate metadata.")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                saveError = e.message?.takeIf { it.isNotBlank() }
                                    ?: t("creator.preview_modal.meta_regenerate_failed", "Could not regenerate metadata.")
                            } finally {
                                regenBusy = false
                                showRegenConfirm = false
                            }
                        }
                    }
                ) {
                    Text(
                        if (regenBusy) t("creator.preview_modal.meta_regenerate_working", "Analyzing the design image…")
                        else t("creator.preview_modal.meta_regenerate", "Regenerate metadata")
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !regenBusy,
                    onClick = { showRegenConfirm = false }
                ) { Text(t("creator.common.cancel", "Cancel")) }
            },
            title = { Text(t("creator.preview_modal.meta_regenerate_confirm", "Regenerate metadata?")) },
            text = {
                Text(
                    t(
                        "creator.preview_modal.meta_regenerate_confirm_body",
                        "This analyzes the design image and replaces title, description, tags, and topics. You still need to Save to keep the new draft."
                    )
                )
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
                                color = CAccent,
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
    val genPrompt = designJson?.optString("prompt").orEmpty().trim()
    val designPrompt = draftMeta.optString("design_prompt").ifBlank {
        draftMeta.optString("final_prompt")
    }.trim()
    val userPrompt = draftMeta.optString("user_prompt").trim()
    val parentId = designJson?.optString("parent_design_id").orEmpty().trim()

    Divider(color = Color.White.copy(alpha = 0.08f), modifier = Modifier.padding(vertical = 8.dp))

    SectionHeader(t("creator.design_detail.reference_section_prompt", "Generation prompt"))
    BodyTextBlock(genPrompt, isLarge = true)

    Spacer(Modifier.height(12.dp))
    Divider(color = Color.White.copy(alpha = 0.08f))
    Spacer(Modifier.height(12.dp))

    SectionHeader(t("creator.design_detail.reference_design_prompt_label", "Design prompt"))
    BodyTextBlock(designPrompt, isLarge = true)

    Spacer(Modifier.height(12.dp))
    Divider(color = Color.White.copy(alpha = 0.08f))
    Spacer(Modifier.height(12.dp))

    SectionHeader(t("creator.design_detail.reference_user_prompt_label", "User prompt"))
    BodyTextBlock(userPrompt, isLarge = true)

    if (parentId.isNotBlank()) {
        Spacer(Modifier.height(16.dp))
        SectionHeader(t("creator.design_detail.reference_parent_design", "Parent design"))
        BodyTextBlock(parentId, isLarge = false)
    }

    val extras = REFERENCE_EXTRA_KEYS.mapNotNull { key ->
        val raw = draftMeta.opt(key) ?: return@mapNotNull null
        val s = when (raw) {
            is String -> raw.trim()
            else -> raw.toString().trim()
        }
        if (s.isBlank()) return@mapNotNull null
        key to s
    }
    if (extras.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        SectionHeader(t("creator.design_detail.reference_section_more", "More"))
        Spacer(Modifier.height(8.dp))
        extras.forEach { (key, value) ->
            Text(
                key.replace('_', ' '),
                color = CAccent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                value,
                color = CTextPrimary,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }
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

    Surface(
        color = Color(0xFF334155),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            SectionHeader(t("creator.design_detail.meta_title", "Title"))
            OutlinedTextField(
                value = draftMeta.optString("title"),
                onValueChange = { v ->
                    onDraftMetaChange(replaceDraftMeta(draftMeta) { put("title", v) })
                },
                label = null,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = CTextPrimary,
                    unfocusedTextColor = CTextPrimary,
                    focusedBorderColor = CAccent.copy(alpha = 0.7f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Surface(
        color = Color(0xFF334155),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            SectionHeader(t("creator.design_detail.design_description", "Design description"))
            OutlinedTextField(
                value = draftMeta.optString("description"),
                onValueChange = { v ->
                    onDraftMetaChange(replaceDraftMeta(draftMeta) { put("description", v) })
                },
                label = null,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = CTextPrimary,
                    unfocusedTextColor = CTextPrimary,
                    focusedBorderColor = CAccent.copy(alpha = 0.7f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
        }
    }

    Spacer(Modifier.height(20.dp))
    SectionHeader(t("creator.design_detail.tags_label", "Tags"))
    Spacer(Modifier.height(8.dp))
    Surface(
        color = CLightSurface,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = tagsStr,
                onValueChange = { v ->
                    onDraftMetaChange(replaceDraftMeta(draftMeta) { put("tags", normalizeTagsString(v)) })
                },
                label = {
                    Text(
                        t("creator.design_detail.tags_input_hint", "Add tags, comma-separated"),
                        color = CTextOnLight.copy(alpha = 0.65f)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = CTextOnLight,
                    unfocusedTextColor = CTextOnLight,
                    focusedBorderColor = CAccent,
                    unfocusedBorderColor = CTextOnLight.copy(alpha = 0.35f),
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                )
            )
            Spacer(Modifier.height(10.dp))
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
                        label = { Text(tag, color = CTextOnLight, fontSize = 13.sp) },
                        trailingIcon = {
                            Icon(Icons.Default.Close, contentDescription = null, tint = CTextOnLight.copy(alpha = 0.65f))
                        }
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(20.dp))
    SectionHeader(t("creator.design_detail.topics_label", "Topics"))
    Spacer(Modifier.height(8.dp))
    Surface(
        color = Color(0xFF334155),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            val topics = getTopicList(draftMeta)
            topics.forEachIndexed { idx, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(item, color = CTextPrimary, modifier = Modifier.weight(1f))
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
                    label = { Text(t("creator.design_detail.add_row_hint", "Add…"), color = CTextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CTextPrimary,
                        unfocusedTextColor = CTextPrimary,
                        focusedBorderColor = CAccent.copy(alpha = 0.7f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
                TextButton(onClick = {
                    val add = newTopicLine.trim()
                    if (add.isEmpty()) return@TextButton
                    val next = topics.toMutableList()
                    if (next.none { it.equals(add, ignoreCase = true) }) next.add(add)
                    onDraftMetaChange(replaceDraftMeta(draftMeta) { put("topic", JSONArray(next)) })
                    onNewTopicLineChange("")
                }) { Text(t("creator.design_detail.add", "Add"), color = CAccent) }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    SectionHeader(t("creator.design_detail.subtopics_label", "Subtopics"))
    Spacer(Modifier.height(8.dp))
    Surface(
        color = Color(0xFF334155),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            val subtopics = getSubtopicList(draftMeta)
            subtopics.forEachIndexed { idx, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(item, color = CTextPrimary, modifier = Modifier.weight(1f))
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
                    label = { Text(t("creator.design_detail.add_row_hint", "Add…"), color = CTextMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = CTextPrimary,
                        unfocusedTextColor = CTextPrimary,
                        focusedBorderColor = CAccent.copy(alpha = 0.7f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
                TextButton(onClick = {
                    val add = newSubtopicLine.trim()
                    if (add.isEmpty()) return@TextButton
                    val next = subtopics.toMutableList()
                    if (next.none { it.equals(add, ignoreCase = true) }) next.add(add)
                    onDraftMetaChange(replaceDraftMeta(draftMeta) { put("subtopic", JSONArray(next)) })
                    onNewSubtopicLineChange("")
                }) { Text(t("creator.design_detail.add", "Add"), color = CAccent) }
            }
        }
    }
}
