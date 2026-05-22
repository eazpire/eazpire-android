package com.eazpire.creator.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt

private data class StudioPlacementSnap(
    val dx: Float,
    val dy: Float,
    val scale: Float,
    val rotate: Float,
    val patternEnabled: Boolean = false
)

private data class PrintAreaFrac(
    val l: Float = 0.28f,
    val t: Float = 0.22f,
    val w: Float = 0.44f,
    val h: Float = 0.48f
)

private fun parsePrintAreaFrac(obj: JSONObject?): PrintAreaFrac {
    if (obj == null) return PrintAreaFrac()
    val l = obj.optDouble("l", obj.optDouble("left", 0.28)).toFloat()
    val t = obj.optDouble("t", obj.optDouble("top", 0.22)).toFloat()
    val w = obj.optDouble("w", obj.optDouble("width", 0.44)).toFloat()
    val h = obj.optDouble("h", obj.optDouble("height", 0.48)).toFloat()
    return PrintAreaFrac(l, t, w, h)
}

private fun resolveMockFromConfig(cfg: JSONObject): Pair<String?, PrintAreaFrac> {
    val byColor = cfg.optJSONObject("mocks_by_color") ?: return null to PrintAreaFrac()
    val keys = byColor.keys()
    var listKey = "default"
    while (keys.hasNext()) {
        val k = keys.next()
        listKey = k
        break
    }
    val arr = byColor.optJSONArray(listKey) ?: return null to PrintAreaFrac()
    for (i in 0 until arr.length()) {
        val item = arr.optJSONObject(i) ?: continue
        val pos = item.optString("position", "").lowercase()
        if (pos == "front" || i == 0) {
            val url = item.optString("shop_mock_url", "")
                .ifBlank { item.optString("editor_mock_url", "") }
                .ifBlank { item.optString("mock_url", "") }
                .trim()
            val frac = parsePrintAreaFrac(item.optJSONObject("print_area_frac"))
            if (url.isNotEmpty()) return url to frac
        }
    }
    return null to PrintAreaFrac()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShopPrintifyDesignStudioScreen(
    product: CatalogProduct,
    initialDesignUrl: String?,
    api: CreatorApi,
    ownerId: String?,
    translationStore: TranslationStore,
    translation: (String, String) -> String,
    onDismiss: () -> Unit,
    onRequireLogin: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isCompact = configuration.screenWidthDp < 720

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var mockUrl by remember { mutableStateOf<String?>(product.mockUrls.firstOrNull()) }
    var printAreaFrac by remember { mutableStateOf(PrintAreaFrac()) }
    var printifyProductId by remember { mutableStateOf("") }
    var designUrl by remember { mutableStateOf(initialDesignUrl) }
    var designSelected by remember { mutableStateOf(initialDesignUrl != null) }
    var designDx by remember { mutableFloatStateOf(0f) }
    var designDy by remember { mutableFloatStateOf(0f) }
    var designScale by remember { mutableFloatStateOf(0.95f) }
    var designRotate by remember { mutableFloatStateOf(0f) }
    var patternEnabled by remember { mutableStateOf(false) }
    var defaultSnap by remember { mutableStateOf<StudioPlacementSnap?>(null) }
    val undoStack = remember { mutableStateListOf<StudioPlacementSnap>() }
    val redoStack = remember { mutableStateListOf<StudioPlacementSnap>() }
    var rightPanel by remember { mutableStateOf("product") }
    var syncing by remember { mutableStateOf(false) }

    fun t(key: String, def: String) = translationStore.t(key, def)

    fun snapNow() = StudioPlacementSnap(designDx, designDy, designScale, designRotate, patternEnabled)

    fun pushUndo() {
        undoStack.add(snapNow())
        if (undoStack.size > 48) undoStack.removeAt(0)
        redoStack.clear()
    }

    fun applySnap(s: StudioPlacementSnap) {
        designDx = s.dx
        designDy = s.dy
        designScale = s.scale
        designRotate = s.rotate
        patternEnabled = s.patternEnabled
    }

    fun rememberDefaultPlacement() {
        defaultSnap = snapNow()
        undoStack.clear()
        redoStack.clear()
    }

    fun scheduleSync() {
        val oid = ownerId?.trim().orEmpty()
        val pid = printifyProductId.trim()
        val img = designUrl?.trim().orEmpty()
        if (oid.isEmpty() || pid.isEmpty() || img.isEmpty()) return
        scope.launch {
            syncing = true
            try {
                val zoneW = max(1f, 200f)
                val px = (0.5f + designDx / zoneW).coerceIn(0f, 1f)
                val py = (0.5f + designDy / zoneW).coerceIn(0f, 1f)
                val pattern = JSONObject().apply {
                    put("enabled", patternEnabled)
                    if (patternEnabled) {
                        put("mode", "grid")
                        put("spacing_x", 1.0)
                        put("spacing_y", 1.0)
                    }
                }
                val placement = JSONObject()
                    .put("x", px.toDouble())
                    .put("y", py.toDouble())
                    .put("scale", designScale.toDouble())
                    .put("angle", designRotate.toDouble())
                    .put("pattern", pattern)
                    .put("printify_position", "front")
                withContext(Dispatchers.IO) {
                    api.printifyStudioTestSync(
                        ownerId = oid,
                        productKey = product.productKey,
                        printifyProductId = pid,
                        placement = placement,
                        imageUrl = img
                    )
                }
            } catch (_: Exception) {
            } finally {
                syncing = false
            }
        }
    }

    fun applyAlign(kind: String, zoneWpx: Float, zoneHpx: Float) {
        val tileW = zoneWpx * designScale
        val tileH = tileW
        val ex = tileW / 2f
        val ey = tileH / 2f
        val lx = ex / zoneWpx
        val rx = 1f - ex / zoneWpx
        val ty = ey / zoneHpx
        val by = 1f - ey / zoneHpx
        val pxNow = 0.5f + designDx / zoneWpx
        val pyNow = 0.5f + designDy / zoneHpx
        fun setFrom(nx: Float, ny: Float) {
            designDx = (nx.coerceIn(0f, 1f) - 0.5f) * zoneWpx
            designDy = (ny.coerceIn(0f, 1f) - 0.5f) * zoneHpx
        }
        when (kind) {
            "left" -> setFrom(lx, pyNow)
            "right" -> setFrom(rx, pyNow)
            "center-h" -> setFrom(0.5f, pyNow)
            "top" -> setFrom(pxNow, ty)
            "bottom" -> setFrom(pxNow, by)
            "middle-v" -> setFrom(pxNow, 0.5f)
        }
        scheduleSync()
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                } ?: return@launch
                val mime = context.contentResolver.getType(uri) ?: "image/png"
                val b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                designUrl = "data:$mime;base64,$b64"
                designSelected = true
                designDx = 0f
                designDy = 0f
                rememberDefaultPlacement()
                scheduleSync()
            } catch (_: Exception) {
            }
        }
    }

    LaunchedEffect(product.productKey, ownerId) {
        val oid = ownerId?.trim().orEmpty()
        if (oid.isEmpty()) {
            loading = false
            onRequireLogin()
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
            val cfg = withContext(Dispatchers.IO) {
                api.getDesignStudioShopConfig(oid, product.productKey)
            }
            if (cfg.optBoolean("ok", false)) {
                val (url, frac) = resolveMockFromConfig(cfg)
                if (!url.isNullOrEmpty()) mockUrl = url
                printAreaFrac = frac
            }
            val open = withContext(Dispatchers.IO) {
                api.printifyStudioTestOpen(oid, product.productKey)
            }
            if (!open.optBoolean("ok", false)) {
                error = open.optString("error", "open_failed")
            } else {
                printifyProductId = open.optString("printify_product_id", "")
            }
        } catch (e: Exception) {
            error = e.message ?: "error"
        } finally {
            loading = false
            if (designUrl != null && defaultSnap == null) {
                rememberDefaultPlacement()
            }
        }
    }

    BackHandler(onBack = onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = translation("creator.shop_printify_studio_test.title", "Design Studio"),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = product.title,
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = translation("creator.common.close", "Close"),
                            tint = Color.White
                        )
                    }
                }

                if (loading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFF97316))
                    }
                } else if (error != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(error ?: "", color = Color.White.copy(alpha = 0.8f))
                    }
                } else {
                    val mainModifier = if (isCompact) {
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    } else {
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    }

                    if (isCompact) {
                        Column(modifier = mainModifier.padding(horizontal = 8.dp)) {
                            StudioMockEditor(
                                mockUrl = mockUrl,
                                designUrl = designUrl,
                                designSelected = designSelected,
                                printAreaFrac = printAreaFrac,
                                designDx = designDx,
                                designDy = designDy,
                                designScale = designScale,
                                designRotate = designRotate,
                                syncing = syncing,
                                undoEnabled = undoStack.isNotEmpty(),
                                redoEnabled = redoStack.isNotEmpty(),
                                t = ::t,
                                onUndo = {
                                    if (undoStack.isNotEmpty()) {
                                        redoStack.add(snapNow())
                                        applySnap(undoStack.removeAt(undoStack.lastIndex))
                                        scheduleSync()
                                    }
                                },
                                onRedo = {
                                    if (redoStack.isNotEmpty()) {
                                        undoStack.add(snapNow())
                                        applySnap(redoStack.removeAt(redoStack.lastIndex))
                                        scheduleSync()
                                    }
                                },
                                onReset = {
                                    pushUndo()
                                    defaultSnap?.let { applySnap(it) }
                                        ?: run {
                                            designDx = 0f
                                            designDy = 0f
                                            designScale = 0.95f
                                            designRotate = 0f
                                            patternEnabled = false
                                        }
                                    scheduleSync()
                                },
                                onOpenSettings = { rightPanel = "transform" },
                                onDesignDragStart = { pushUndo() },
                                onDesignDrag = { dx, dy ->
                                    designDx += dx
                                    designDy += dy
                                },
                                onDesignDragEnd = { scheduleSync() },
                                onSelectDesign = { designSelected = true },
                                orbitTop = {
                                    StudioOrbitBtn(t("creator.shop_printify_studio_test.tool_fit", "Fit")) {
                                        pushUndo()
                                        designScale = 0.95f
                                        designDx = 0f
                                        designDy = 0f
                                        scheduleSync()
                                    }
                                },
                                orbitBottom = {
                                    StudioOrbitBtn(t("creator.shop_printify_studio_test.tool_align", "Align")) {
                                        rightPanel = "align"
                                    }
                                    StudioOrbitBtn(t("creator.shop_printify_studio_test.tool_pattern", "Pattern")) {
                                        rightPanel = "pattern"
                                    }
                                }
                            )
                            StudioToolRow(
                                onUpload = { imagePicker.launch("image/*") },
                                t = ::t
                            )
                            StudioRightPanel(
                                rightPanel = rightPanel,
                                designScale = designScale,
                                designRotate = designRotate,
                                patternEnabled = patternEnabled,
                                printAreaFrac = printAreaFrac,
                                onBackToProduct = { rightPanel = "product" },
                                onScaleChange = {
                                    if (undoStack.isEmpty()) pushUndo()
                                    designScale = it
                                },
                                onScaleFinished = { scheduleSync() },
                                onRotateChange = {
                                    if (undoStack.isEmpty()) pushUndo()
                                    designRotate = it
                                },
                                onRotateFinished = { scheduleSync() },
                                onPatternToggle = { enabled ->
                                    pushUndo()
                                    patternEnabled = enabled
                                    scheduleSync()
                                },
                                onAlign = { kind, zw, zh ->
                                    pushUndo()
                                    applyAlign(kind, zw, zh)
                                },
                                t = ::t
                            )
                        }
                    } else {
                        Row(
                            modifier = mainModifier.padding(horizontal = 8.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                StudioMockEditor(
                                    mockUrl = mockUrl,
                                    designUrl = designUrl,
                                    designSelected = designSelected,
                                    printAreaFrac = printAreaFrac,
                                    designDx = designDx,
                                    designDy = designDy,
                                    designScale = designScale,
                                    designRotate = designRotate,
                                    syncing = syncing,
                                    undoEnabled = undoStack.isNotEmpty(),
                                    redoEnabled = redoStack.isNotEmpty(),
                                    t = ::t,
                                    onUndo = {
                                        if (undoStack.isNotEmpty()) {
                                            redoStack.add(snapNow())
                                            applySnap(undoStack.removeAt(undoStack.lastIndex))
                                            scheduleSync()
                                        }
                                    },
                                    onRedo = {
                                        if (redoStack.isNotEmpty()) {
                                            undoStack.add(snapNow())
                                            applySnap(redoStack.removeAt(redoStack.lastIndex))
                                            scheduleSync()
                                        }
                                    },
                                    onReset = {
                                        pushUndo()
                                        defaultSnap?.let { applySnap(it) }
                                            ?: run {
                                                designDx = 0f
                                                designDy = 0f
                                                designScale = 0.95f
                                                designRotate = 0f
                                                patternEnabled = false
                                            }
                                        scheduleSync()
                                    },
                                    onOpenSettings = { rightPanel = "transform" },
                                    onDesignDragStart = { pushUndo() },
                                    onDesignDrag = { dx, dy ->
                                        designDx += dx
                                        designDy += dy
                                    },
                                    onDesignDragEnd = { scheduleSync() },
                                    onSelectDesign = { designSelected = true },
                                    orbitTop = {
                                        StudioOrbitBtn(t("creator.shop_printify_studio_test.tool_fit", "Fit")) {
                                            pushUndo()
                                            designScale = 0.95f
                                            designDx = 0f
                                            designDy = 0f
                                            scheduleSync()
                                        }
                                    },
                                    orbitLeft = {
                                        StudioOrbitScaleSlider(
                                            label = t("creator.shop_printify_studio_test.scale_label", "Scale"),
                                            value = designScale,
                                            onChange = {
                                                if (undoStack.isEmpty()) pushUndo()
                                                designScale = it
                                            },
                                            onFinished = { scheduleSync() }
                                        )
                                    },
                                    orbitRight = {
                                        StudioOrbitRotateSlider(
                                            label = t("creator.shop_printify_studio_test.rotate_label", "Rotation"),
                                            value = designRotate,
                                            onChange = {
                                                if (undoStack.isEmpty()) pushUndo()
                                                designRotate = it
                                            },
                                            onFinished = { scheduleSync() }
                                        )
                                    },
                                    orbitBottom = {
                                        StudioOrbitBtn(t("creator.shop_printify_studio_test.tool_align", "Align")) {
                                            rightPanel = "align"
                                        }
                                        StudioOrbitBtn(t("creator.shop_printify_studio_test.tool_pattern", "Pattern")) {
                                            rightPanel = "pattern"
                                        }
                                    }
                                )
                                StudioToolRow(
                                    onUpload = { imagePicker.launch("image/*") },
                                    t = ::t
                                )
                            }
                            StudioRightPanel(
                                modifier = Modifier
                                    .widthIn(min = 140.dp, max = 220.dp)
                                    .fillMaxHeight(),
                                rightPanel = rightPanel,
                                designScale = designScale,
                                designRotate = designRotate,
                                patternEnabled = patternEnabled,
                                printAreaFrac = printAreaFrac,
                                onBackToProduct = { rightPanel = "product" },
                                onScaleChange = {
                                    if (undoStack.isEmpty()) pushUndo()
                                    designScale = it
                                },
                                onScaleFinished = { scheduleSync() },
                                onRotateChange = {
                                    if (undoStack.isEmpty()) pushUndo()
                                    designRotate = it
                                },
                                onRotateFinished = { scheduleSync() },
                                onPatternToggle = { enabled ->
                                    pushUndo()
                                    patternEnabled = enabled
                                    scheduleSync()
                                },
                                onAlign = { kind, zw, zh ->
                                    pushUndo()
                                    applyAlign(kind, zw, zh)
                                },
                                t = ::t
                            )
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(designUrl, printifyProductId) {
        if (!designUrl.isNullOrBlank() && printifyProductId.isNotBlank()) {
            delay(420)
            scheduleSync()
        }
    }
}

@Composable
private fun StudioMockEditor(
    mockUrl: String?,
    designUrl: String?,
    designSelected: Boolean,
    printAreaFrac: PrintAreaFrac,
    designDx: Float,
    designDy: Float,
    designScale: Float,
    designRotate: Float,
    syncing: Boolean,
    undoEnabled: Boolean,
    redoEnabled: Boolean,
    t: (String, String) -> String,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onReset: () -> Unit,
    onOpenSettings: () -> Unit,
    onDesignDragStart: () -> Unit,
    onDesignDrag: (Float, Float) -> Unit,
    onDesignDragEnd: () -> Unit,
    onSelectDesign: () -> Unit,
    orbitTop: @Composable () -> Unit = {},
    orbitLeft: @Composable () -> Unit = {},
    orbitRight: @Composable () -> Unit = {},
    orbitBottom: @Composable () -> Unit = {}
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 280.dp, max = 520.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onUndo,
                enabled = undoEnabled,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFF0F172A).copy(0.82f), RoundedCornerShape(10.dp))
            ) {
                Text("↶", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
            IconButton(
                onClick = onRedo,
                enabled = redoEnabled,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFF0F172A).copy(0.82f), RoundedCornerShape(10.dp))
            ) {
                Text("↷", color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
        TextButton(
            onClick = onReset,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .background(Color(0xFF0F172A).copy(0.82f), RoundedCornerShape(10.dp))
        ) {
            Text(t("design_studio.shop.reset_design", "Reset"), color = Color.White)
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp, bottom = 52.dp, start = 8.dp, end = 8.dp)
        ) {
            val stageW = maxWidth
            val stageH = maxHeight
            val zoneW = stageW * printAreaFrac.w
            val zoneH = stageH * printAreaFrac.h
            val zoneLeft = stageW * printAreaFrac.l
            val zoneTop = stageH * printAreaFrac.t

            mockUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            if (designUrl != null && designSelected) {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (with(density) { zoneLeft.toPx() + zoneW.toPx() / 2f }).roundToInt() - 18.dp.roundToPx(),
                                (with(density) { zoneTop.toPx() }).roundToInt() - 40.dp.roundToPx()
                            )
                        }
                        .size(36.dp)
                        .background(Color(0xFF0F172A).copy(0.9f), RoundedCornerShape(999.dp))
                ) {
                    Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                orbitTop()
            }

            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                orbitLeft()
            }

            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                orbitRight()
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                orbitBottom()
            }

            Box(
                modifier = Modifier
                    .offset(x = zoneLeft, y = zoneTop)
                    .size(zoneW, zoneH)
                    .border(3.dp, Color(0xFFEF4444), RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .clipToBounds()
            ) {
                designUrl?.let { dUrl ->
                    AsyncImage(
                        model = dUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .graphicsLayer {
                                translationX = designDx
                                translationY = designDy
                                scaleX = designScale
                                scaleY = designScale
                                rotationZ = designRotate
                            }
                            .pointerInput(dUrl) {
                                detectDragGestures(
                                    onDragStart = {
                                        onSelectDesign()
                                        onDesignDragStart()
                                    },
                                    onDrag = { change, drag ->
                                        change.consume()
                                        onDesignDrag(drag.x, drag.y)
                                    },
                                    onDragEnd = { onDesignDragEnd() }
                                )
                            },
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        if (syncing) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
                    .size(22.dp),
                color = Color(0xFFF97316),
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
private fun StudioOrbitBtn(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
        modifier = Modifier.heightIn(min = 32.dp)
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun StudioOrbitScaleSlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit
) {
    Column(
        modifier = Modifier
            .background(Color(0xFF0F172A).copy(0.88f), RoundedCornerShape(10.dp))
            .padding(8.dp)
            .widthIn(max = 110.dp)
    ) {
        Text(label, color = Color.White.copy(0.85f), style = MaterialTheme.typography.labelSmall)
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = 0.08f..2.5f
        )
    }
}

@Composable
private fun StudioOrbitRotateSlider(
    label: String,
    value: Float,
    onChange: (Float) -> Unit,
    onFinished: () -> Unit
) {
    Column(
        modifier = Modifier
            .background(Color(0xFF0F172A).copy(0.88f), RoundedCornerShape(10.dp))
            .padding(8.dp)
            .widthIn(max = 110.dp)
    ) {
        Text(label, color = Color.White.copy(0.85f), style = MaterialTheme.typography.labelSmall)
        Slider(
            value = value,
            onValueChange = onChange,
            onValueChangeFinished = onFinished,
            valueRange = -180f..180f
        )
    }
}

@Composable
private fun StudioToolRow(
    onUpload: () -> Unit,
    t: (String, String) -> String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StudioDarkBtn(onClick = onUpload) {
            Text(t("design_studio.shop.browse_device", "Upload from device"))
        }
    }
}

@Composable
private fun StudioRightPanel(
    modifier: Modifier = Modifier,
    rightPanel: String,
    designScale: Float,
    designRotate: Float,
    patternEnabled: Boolean,
    printAreaFrac: PrintAreaFrac,
    onBackToProduct: () -> Unit,
    onScaleChange: (Float) -> Unit,
    onScaleFinished: () -> Unit,
    onRotateChange: (Float) -> Unit,
    onRotateFinished: () -> Unit,
    onPatternToggle: (Boolean) -> Unit,
    onAlign: (String, Float, Float) -> Unit,
    t: (String, String) -> String
) {
    val zoneW = 200f * printAreaFrac.w
    val zoneH = 200f * printAreaFrac.h

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 8.dp, top = 4.dp, bottom = 8.dp)
    ) {
        if (rightPanel != "product") {
            TextButton(onClick = onBackToProduct) {
                Text("←", color = Color.White)
            }
        }
        when (rightPanel) {
            "align" -> {
                Text(t("creator.shop_printify_studio_test.align_title", "Align"), color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                listOf(
                    "left" to t("creator.shop_printify_studio_test.align_left", "Align left"),
                    "center-h" to t("creator.shop_printify_studio_test.align_center_h", "Center horizontal"),
                    "right" to t("creator.shop_printify_studio_test.align_right", "Align right"),
                    "top" to t("creator.shop_printify_studio_test.align_top", "Align top"),
                    "middle-v" to t("creator.shop_printify_studio_test.align_middle", "Center vertical"),
                    "bottom" to t("creator.shop_printify_studio_test.align_bottom", "Align bottom")
                ).forEach { (kind, label) ->
                    StudioDarkBtn(onClick = { onAlign(kind, zoneW, zoneH) }) {
                        Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            "pattern" -> {
                Text(t("creator.shop_printify_studio_test.pattern_title", "Pattern"), color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        t("creator.shop_printify_studio_test.pattern_active_label", "Pattern active"),
                        color = Color.White.copy(0.85f),
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = patternEnabled, onCheckedChange = onPatternToggle)
                }
            }
            "transform" -> {
                Text(t("design_studio.shop.settings", "Design settings"), color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(t("creator.shop_printify_studio_test.scale_label", "Scale"), color = Color.White.copy(0.8f))
                Slider(
                    value = designScale,
                    onValueChange = onScaleChange,
                    onValueChangeFinished = onScaleFinished,
                    valueRange = 0.08f..2.5f
                )
                Text(t("creator.shop_printify_studio_test.rotate_label", "Rotation"), color = Color.White.copy(0.8f))
                Slider(
                    value = designRotate,
                    onValueChange = onRotateChange,
                    onValueChangeFinished = onRotateFinished,
                    valueRange = -180f..180f
                )
            }
            else -> {
                Text(t("design_studio.shop.product_options", "Product options"), color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(t("creator.shop_printify_studio_test.pick_color_title", "Color"), color = Color.White.copy(0.75f))
                Text(t("creator.shop_printify_studio_test.pick_size_title", "Size"), color = Color.White.copy(0.75f), modifier = Modifier.padding(top = 12.dp))
                Text(
                    t("creator.shop_printify_studio_test.variant_loading", "Loading options…"),
                    color = Color.White.copy(0.5f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun StudioDarkBtn(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides Color.White
        ) {
            content()
        }
    }
}
