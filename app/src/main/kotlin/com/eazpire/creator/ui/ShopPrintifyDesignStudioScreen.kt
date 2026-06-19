package com.eazpire.creator.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.graphics.asImageBitmap
import coil.request.ImageRequest
import android.graphics.BitmapFactory
import android.util.Base64
import com.eazpire.creator.favorites.FavoritesRefreshTrigger
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.eazpire.creator.ui.modal.EazBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.unit.sp
import com.eazpire.creator.ui.modal.EazStandardDialog
import coil.compose.AsyncImage
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
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

private data class StudioPickResult(
    val url: String,
    val designId: String? = null,
    val fromPublicInspiration: Boolean = false
)

private data class StudioPickerRow(
    val id: String,
    val url: String,
    val designId: String? = null,
    val fromPublicInspiration: Boolean = false
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
    val isCompact = configuration.screenWidthDp < 1100
    var isFavorite by remember { mutableStateOf(false) }

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
    var optionsTab by remember { mutableStateOf("product") }
    var designSub by remember { mutableStateOf("transform") }
    var optionsSheetOpen by remember { mutableStateOf(false) }
    var optionsSheetShowTabs by remember { mutableStateOf(true) }
    var sourcesDrawerOpen by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }
    var productMeta by remember { mutableStateOf<JSONObject?>(null) }
    var metaLoading by remember { mutableStateOf(false) }
    var selectedColorId by remember { mutableStateOf<Long?>(null) }
    var selectedSizeId by remember { mutableStateOf<Long?>(null) }
    var showDesignPicker by remember { mutableStateOf<String?>(null) }
    var existingShopHandle by remember { mutableStateOf<String?>(null) }
    var existingShopProductName by remember { mutableStateOf<String?>(null) }
    val optionsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
        val img = designUrl?.trim().orEmpty()
        if (oid.isEmpty() || img.isEmpty()) return
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
                val pid = printifyProductId.trim().ifEmpty { null }
                val inlineB64: String?
                val inlineMime: String?
                if (img.startsWith("data:")) {
                    val comma = img.indexOf(',')
                    inlineMime = if (comma > 5) img.substring(5, comma).substringBefore(';') else "image/png"
                    inlineB64 = if (comma >= 0) img.substring(comma + 1) else img
                } else {
                    inlineMime = null
                    inlineB64 = null
                }
                val res = withContext(Dispatchers.IO) {
                    api.printifyStudioTestSync(
                        ownerId = oid,
                        productKey = product.productKey,
                        printifyProductId = pid,
                        placement = placement,
                        imageUrl = if (inlineB64 != null) null else img,
                        designImageBase64 = inlineB64,
                        designImageContentType = inlineMime
                    )
                }
                if (res.optBoolean("ok", false)) {
                    val newPid = res.optString("printify_product_id", "").trim()
                    if (newPid.isNotEmpty()) printifyProductId = newPid
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
            val cfg =
                withContext(Dispatchers.IO) {
                    withTimeoutOrNull(25_000) {
                        api.getDesignStudioShopConfig(oid, product.productKey)
                    }
                }
            if (cfg != null && cfg.optBoolean("ok", false)) {
                val (url, frac) = resolveMockFromConfig(cfg)
                if (!url.isNullOrEmpty()) mockUrl = url
                printAreaFrac = frac
            } else if (mockUrl.isNullOrBlank()) {
                mockUrl = product.mockUrls.firstOrNull()
            }
            if (cfg == null) {
                error =
                    translation(
                        "creator.studio.config_timeout",
                        "Studio config timed out — using catalog preview.",
                    )
            }
        } catch (e: Exception) {
            error = e.message ?: "error"
            if (mockUrl.isNullOrBlank()) {
                mockUrl = product.mockUrls.firstOrNull()
            }
        } finally {
            loading = false
            if (designUrl != null && defaultSnap == null) {
                rememberDefaultPlacement()
                designSelected = true
            }
        }
    }

    LaunchedEffect(printifyProductId, ownerId) {
        val oid = ownerId?.trim().orEmpty()
        val pid = printifyProductId.trim()
        if (oid.isEmpty() || pid.isEmpty()) return@LaunchedEffect
        metaLoading = true
        try {
            val meta = withContext(Dispatchers.IO) {
                api.printifyStudioTestProductMeta(oid, pid)
            }
            if (meta.optBoolean("ok", false)) {
                productMeta = meta
                val prime = primeVariantSelection(meta)
                selectedColorId = prime.first
                selectedSizeId = prime.second
            } else {
                productMeta = null
            }
        } catch (_: Exception) {
            productMeta = null
        } finally {
            metaLoading = false
        }
    }

    BackHandler(enabled = sourcesDrawerOpen || optionsSheetOpen || showDesignPicker != null) {
        when {
            showDesignPicker != null -> showDesignPicker = null
            optionsSheetOpen -> optionsSheetOpen = false
            sourcesDrawerOpen -> sourcesDrawerOpen = false
        }
    }
    BackHandler(enabled = !sourcesDrawerOpen && !optionsSheetOpen, onBack = onDismiss)

    EazStandardDialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCompact) {
                        IconButton(onClick = { sourcesDrawerOpen = true }) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = translation("design_studio.shop.design_source", "Design source"),
                                tint = Color(0xFFF97316)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = translation("creator.shop_printify_studio_test.title", "Design Studio"),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = product.title,
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
                    val mainModifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()

                    if (isCompact) {
                        Column(
                            modifier = mainModifier.padding(horizontal = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            StudioMockEditor(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                useFlexHeight = true,
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
                                onOpenSettings = {
                                    optionsTab = "product"
                                    designSub = "transform"
                                    optionsSheetShowTabs = true
                                    optionsSheetOpen = true
                                },
                                onDesignDragStart = { pushUndo() },
                                onDesignDrag = { dx, dy ->
                                    designDx += dx
                                    designDy += dy
                                },
                                onDesignDragEnd = { scheduleSync() },
                                onSelectDesign = { designSelected = true },
                                onDeselectDesign = { designSelected = false },
                                showSettingsInViewer = isCompact && designSelected && !designUrl.isNullOrBlank(),
                                showOrbitTools = designSelected && !designUrl.isNullOrBlank(),
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
                                        optionsTab = "design"
                                        designSub = "align"
                                        optionsSheetShowTabs = false
                                        optionsSheetOpen = true
                                    }
                                    StudioOrbitBtn(t("creator.shop_printify_studio_test.tool_pattern", "Pattern")) {
                                        optionsTab = "design"
                                        designSub = "pattern"
                                        optionsSheetShowTabs = false
                                        optionsSheetOpen = true
                                    }
                                }
                            )
                        }
                    } else {
                        Row(
                            modifier = mainModifier.padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            StudioMockEditor(
                                    modifier = Modifier
                                        .widthIn(min = 280.dp, max = 520.dp)
                                        .fillMaxHeight(),
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
                                    onOpenSettings = {
                                        optionsTab = "product"
                                        designSub = "transform"
                                        optionsSheetShowTabs = true
                                        if (isCompact) optionsSheetOpen = true
                                    },
                                    onDesignDragStart = { pushUndo() },
                                    onDesignDrag = { dx, dy ->
                                        designDx += dx
                                        designDy += dy
                                    },
                                    onDesignDragEnd = { scheduleSync() },
                                    onSelectDesign = { designSelected = true },
                                    onDeselectDesign = { designSelected = false },
                                    showSettingsInViewer = isCompact && designSelected && !designUrl.isNullOrBlank(),
                                    showOrbitTools = designSelected && !designUrl.isNullOrBlank(),
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
                                            optionsTab = "design"
                                            designSub = "align"
                                            optionsSheetShowTabs = false
                                            if (isCompact) optionsSheetOpen = true
                                        }
                                        StudioOrbitBtn(t("creator.shop_printify_studio_test.tool_pattern", "Pattern")) {
                                            optionsTab = "design"
                                            designSub = "pattern"
                                            optionsSheetShowTabs = false
                                            if (isCompact) optionsSheetOpen = true
                                        }
                                    }
                                )
                            StudioRightPanel(
                                modifier = Modifier
                                    .width(188.dp)
                                    .fillMaxHeight(),
                                showTabRow = true,
                                optionsTab = optionsTab,
                                designSub = designSub,
                                designScale = designScale,
                                designRotate = designRotate,
                                patternEnabled = patternEnabled,
                                printAreaFrac = printAreaFrac,
                                productMeta = productMeta,
                                metaLoading = metaLoading,
                                selectedColorId = selectedColorId,
                                selectedSizeId = selectedSizeId,
                                onColorPick = { id ->
                                    selectedColorId = id
                                    productMeta?.let { meta ->
                                        val v = findVariantBySelections(meta, id, selectedSizeId)
                                        if (v != null) scheduleSync()
                                    }
                                },
                                onSizePick = { id ->
                                    selectedSizeId = id
                                    productMeta?.let { meta ->
                                        val v = findVariantBySelections(meta, selectedColorId, id)
                                        if (v != null) scheduleSync()
                                    }
                                },
                                onTabChange = { tab, sub ->
                                    optionsTab = tab
                                    if (sub != null) designSub = sub
                                },
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

                    if (isCompact) {
                        StudioCompactFooter(
                            isFavorite = isFavorite,
                            onFavorite = {
                                val oid = ownerId
                                if (oid.isNullOrBlank()) {
                                    onRequireLogin()
                                    return@StudioCompactFooter
                                }
                                scope.launch {
                                    runCatching {
                                        val favId = "shop-create:${product.productKey}"
                                        if (isFavorite) {
                                            api.removeFavorite(oid, favId, null)
                                        } else {
                                            api.addFavorite(
                                                customerId = oid,
                                                productId = favId,
                                                variantId = null,
                                                productTitle = product.title,
                                                productImage = mockUrl
                                            )
                                        }
                                    }.onSuccess {
                                        isFavorite = !isFavorite
                                        FavoritesRefreshTrigger.trigger()
                                    }
                                }
                            },
                            onAddToCart = { /* Printify create flow — Shopify variant when published */ },
                            onOpenCart = { /* checkout drawer handled by host */ },
                            t = ::t
                        )
                    }
                }
            }

            if (isCompact && sourcesDrawerOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable { sourcesDrawerOpen = false }
                )
                AnimatedVisibility(
                    visible = sourcesDrawerOpen,
                    enter = slideInHorizontally(initialOffsetX = { -it }),
                    exit = slideOutHorizontally(targetOffsetX = { -it }),
                    modifier = Modifier.align(Alignment.CenterStart)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(280.dp)
                            .background(Color(0xFF0F172A))
                            .padding(12.dp)
                            .clickable(enabled = false) {}
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                t("design_studio.shop.design_source", "Design source"),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { sourcesDrawerOpen = false }) {
                                Icon(Icons.Default.Close, null, tint = Color.White)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        StudioSourcesDrawer(
                            onUpload = { imagePicker.launch("image/*") },
                            onPublicDesigns = {
                                sourcesDrawerOpen = false
                                showDesignPicker = "public"
                            },
                            onMyDesigns = {
                                sourcesDrawerOpen = false
                                showDesignPicker = "mine"
                            },
                            onSavedDrafts = {
                                sourcesDrawerOpen = false
                                showDesignPicker = "drafts"
                            },
                            t = ::t
                        )
                    }
                }
            }

            if (isCompact && optionsSheetOpen) {
                EazBottomSheet(
                    onDismissRequest = { optionsSheetOpen = false },
                    sheetState = optionsSheetState,
                    containerColor = Color(0xFF0F172A),
                    dragHandle = {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 40.dp, height = 4.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color.White.copy(alpha = 0.28f))
                            )
                        }
                    }
                ) {
                    val sheetMaxH = (configuration.screenHeightDp * 0.3f).dp
                    StudioRightPanel(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = sheetMaxH)
                            .padding(horizontal = 8.dp),
                        showTabRow = optionsSheetShowTabs,
                        optionsTab = optionsTab,
                        designSub = designSub,
                        designScale = designScale,
                        designRotate = designRotate,
                        patternEnabled = patternEnabled,
                        printAreaFrac = printAreaFrac,
                        productMeta = productMeta,
                        metaLoading = metaLoading,
                        selectedColorId = selectedColorId,
                        selectedSizeId = selectedSizeId,
                        onColorPick = { id ->
                            selectedColorId = id
                            productMeta?.let { meta ->
                                findVariantBySelections(meta, id, selectedSizeId)
                                scheduleSync()
                            }
                        },
                        onSizePick = { id ->
                            selectedSizeId = id
                            productMeta?.let { meta ->
                                findVariantBySelections(meta, selectedColorId, id)
                                scheduleSync()
                            }
                        },
                        onTabChange = { tab, sub ->
                            optionsTab = tab
                            if (sub != null) designSub = sub
                        },
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

    LaunchedEffect(designUrl, printifyProductId) {
        if (!designUrl.isNullOrBlank() && printifyProductId.isNotBlank()) {
            delay(420)
            scheduleSync()
        }
    }

    fun checkExistingShopProduct(designId: String) {
        val id = designId.trim()
        if (id.isEmpty()) return
        scope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    api.printifyStudioExistingProduct(id, product.productKey)
                }
                if (!data.optBoolean("found", false)) return@launch
                val row = data.optJSONObject("product") ?: return@launch
                val handle = row.optString("shopify_handle", "").trim()
                if (handle.isEmpty()) return@launch
                existingShopProductName = row.optString("product_name", "").trim().ifBlank { null }
                existingShopHandle = handle
            } catch (_: Exception) {
            }
        }
    }

    showDesignPicker?.let { mode ->
        StudioDesignPickerDialog(
            mode = mode,
            api = api,
            ownerId = ownerId,
            productKey = product.productKey,
            onDismiss = { showDesignPicker = null },
            onPick = { pick ->
                val wasFirstDesign = designUrl.isNullOrBlank()
                designUrl = pick.url
                designSelected = true
                showDesignPicker = null
                designDx = 0f
                designDy = 0f
                rememberDefaultPlacement()
                scheduleSync()
                if (pick.fromPublicInspiration && wasFirstDesign && !pick.designId.isNullOrBlank()) {
                    checkExistingShopProduct(pick.designId)
                }
            },
            t = ::t
        )
    }

    existingShopHandle?.let { handle ->
        StudioConfirmDialog(
            title = t(
                "creator.shop_printify_studio_test.existing_product_title",
                "This product is already available in the shop"
            ),
            message = existingShopProductName
                ?: t(
                    "creator.shop_printify_studio_test.existing_product_title",
                    "This product is already available in the shop"
                ),
            confirmLabel = t("creator.shop_printify_studio_test.existing_product_view", "View product"),
            cancelLabel = t("creator.shop_printify_studio_test.existing_product_configure", "Configure yourself"),
            onConfirm = {
                existingShopHandle = null
                existingShopProductName = null
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://www.eazpire.com/products/$handle"))
                )
            },
            onDismiss = {
                existingShopHandle = null
                existingShopProductName = null
            }
        )
    }
}

@Composable
private fun StudioMockEditor(
    modifier: Modifier = Modifier,
    useFlexHeight: Boolean = false,
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
    onDeselectDesign: () -> Unit = {},
    showSettingsInViewer: Boolean = false,
    showOrbitTools: Boolean = false,
    orbitTop: @Composable () -> Unit = {},
    orbitLeft: @Composable () -> Unit = {},
    orbitRight: @Composable () -> Unit = {},
    orbitBottom: @Composable () -> Unit = {}
) {
    val density = LocalDensity.current
    val stageSizeModifier = if (useFlexHeight) {
        Modifier.fillMaxWidth().fillMaxHeight().heightIn(min = 240.dp)
    } else {
        Modifier.fillMaxWidth().heightIn(min = 280.dp, max = 520.dp)
    }
    Box(
        modifier = modifier
            .then(stageSizeModifier)
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

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { onDeselectDesign() }
                    }
            ) {
                mockUrl?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            if (showSettingsInViewer) {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(40.dp)
                        .background(Color(0xFF0F172A).copy(0.9f), RoundedCornerShape(999.dp))
                ) {
                    Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            if (showOrbitTools) {
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
            }

            if (!designUrl.isNullOrBlank()) {
                val dUrl = designUrl!!
                val zoneModifier = Modifier
                    .offset(x = zoneLeft, y = zoneTop)
                    .size(zoneW, zoneH)
                    .clip(RoundedCornerShape(4.dp))
                    .clipToBounds()
                    .then(
                        if (designSelected) {
                            Modifier.border(3.dp, Color(0xFFEF4444), RoundedCornerShape(4.dp))
                        } else {
                            Modifier
                        }
                    )
                Box(modifier = zoneModifier) {
                    StudioPlacedDesignImage(
                        url = dUrl,
                        modifier = Modifier
                            .fillMaxSize()
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
                        designDx = designDx,
                        designDy = designDy,
                        designScale = designScale,
                        designRotate = designRotate
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
private fun StudioCompactFooter(
    isFavorite: Boolean,
    onFavorite: () -> Unit,
    onAddToCart: () -> Unit,
    onOpenCart: () -> Unit,
    t: (String, String) -> String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconButton(onClick = onFavorite) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = t("eaz.pdp.favorite", "Favorite"),
                tint = if (isFavorite) Color(0xFFF97316) else Color.White.copy(alpha = 0.85f)
            )
        }
        OutlinedButton(
            onClick = onAddToCart,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF97316).copy(alpha = 0.55f))
        ) {
            Text(
                t("eaz.pdp.add_to_cart", "Add to cart"),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onOpenCart) {
            Icon(
                Icons.Default.ShoppingCart,
                contentDescription = t("eaz.pdp.add_to_cart", "Add to cart"),
                tint = Color(0xFFF97316)
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
    showTabRow: Boolean = true,
    optionsTab: String,
    designSub: String,
    designScale: Float,
    designRotate: Float,
    patternEnabled: Boolean,
    printAreaFrac: PrintAreaFrac,
    productMeta: JSONObject?,
    metaLoading: Boolean,
    selectedColorId: Long?,
    selectedSizeId: Long?,
    onColorPick: (Long) -> Unit,
    onSizePick: (Long) -> Unit,
    onTabChange: (String, String?) -> Unit,
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
    val colors = remember(productMeta) { parseColorOptions(productMeta) }
    val sizes = remember(productMeta) { parseSizeOptions(productMeta) }

    Column(
        modifier = modifier
            .padding(start = 8.dp, top = 4.dp, bottom = 8.dp)
    ) {
        if (showTabRow) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StudioDarkBtn(onClick = { onTabChange("product", null) }) {
                    Text(
                        t("design_studio.shop.tab_product_options", "Product options"),
                        fontWeight = if (optionsTab == "product") FontWeight.Bold else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StudioDarkBtn(onClick = { onTabChange("design", "transform") }) {
                    Text(
                        t("design_studio.shop.tab_design_settings", "Design settings"),
                        fontWeight = if (optionsTab == "design") FontWeight.Bold else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
        ) {
            when {
                optionsTab == "design" && designSub == "align" -> {
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
                optionsTab == "design" && designSub == "pattern" -> {
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
                optionsTab == "design" -> {
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
                    if (metaLoading) {
                        Text(
                            t("creator.shop_printify_studio_test.variant_loading", "Loading options…"),
                            color = Color.White.copy(0.5f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else if (colors.isEmpty() && sizes.isEmpty()) {
                        Text(
                            t("creator.shop_printify_studio_test.variant_none", "No options available."),
                            color = Color.White.copy(0.5f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        if (colors.isNotEmpty()) {
                            Text(t("creator.shop_printify_studio_test.pick_color_title", "Color"), color = Color.White.copy(0.75f))
                            Spacer(Modifier.height(6.dp))
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 28.dp),
                                modifier = Modifier.heightIn(max = 120.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(colors, key = { it.id }) { c ->
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(parseHexColor(c.hex))
                                            .border(
                                                width = if (selectedColorId == c.id) 2.dp else 1.dp,
                                                color = if (selectedColorId == c.id) Color(0xFFF97316) else Color.White.copy(0.25f),
                                                shape = RoundedCornerShape(999.dp)
                                            )
                                            .clickable { onColorPick(c.id) }
                                    )
                                }
                            }
                        }
                        if (sizes.isNotEmpty()) {
                            Text(
                                t("creator.shop_printify_studio_test.pick_size_title", "Size"),
                                color = Color.White.copy(0.75f),
                                modifier = Modifier.padding(top = 10.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                sizes.forEach { s ->
                                    StudioDarkBtn(onClick = { onSizePick(s.id) }) {
                                        Text(
                                            s.title,
                                            fontWeight = if (selectedSizeId == s.id) FontWeight.Bold else FontWeight.Normal
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

private data class StudioColorOpt(val id: Long, val hex: String)
private data class StudioSizeOpt(val id: Long, val title: String)

private fun parseHexColor(hex: String): Color {
    val h = hex.trim().removePrefix("#")
    return runCatching {
        when (h.length) {
            6 -> Color(0xFF000000 or (h.toLong(16) and 0xFFFFFF))
            else -> Color.White
        }
    }.getOrDefault(Color.White)
}

private fun inferColorSizeIndices(options: JSONArray): Pair<Int, Int> {
    var colorIdx = -1
    var sizeIdx = -1
    for (i in 0 until options.length()) {
        val row = options.optJSONObject(i) ?: continue
        val typ = row.optString("type", "").lowercase()
        val name = row.optString("name", "").lowercase()
        if (colorIdx < 0 && (typ == "color" || name.contains("color"))) colorIdx = i
        if (sizeIdx < 0 && (typ == "size" || name.contains("size") || name.contains("grö") || name.contains("grosse"))) {
            sizeIdx = i
        }
    }
    return colorIdx to sizeIdx
}

private fun parseColorOptions(meta: JSONObject?): List<StudioColorOpt> {
    if (meta == null || !meta.optBoolean("ok", false)) return emptyList()
    val options = meta.optJSONArray("options") ?: return emptyList()
    val (colorIdx, _) = inferColorSizeIndices(options)
    if (colorIdx < 0) return emptyList()
    val vals = options.optJSONObject(colorIdx)?.optJSONArray("values") ?: return emptyList()
    val out = mutableListOf<StudioColorOpt>()
    for (i in 0 until vals.length()) {
        val v = vals.optJSONObject(i) ?: continue
        val id = v.optLong("id", -1L)
        if (id < 0) continue
        val colorsArr = v.optJSONArray("colors")
        val hex = if (colorsArr != null && colorsArr.length() > 0) colorsArr.optString(0, "#ffffff") else "#ffffff"
        out.add(StudioColorOpt(id, hex))
    }
    return out
}

private fun parseSizeOptions(meta: JSONObject?): List<StudioSizeOpt> {
    if (meta == null || !meta.optBoolean("ok", false)) return emptyList()
    val options = meta.optJSONArray("options") ?: return emptyList()
    val (_, sizeIdx) = inferColorSizeIndices(options)
    if (sizeIdx >= 0) {
        val vals = options.optJSONObject(sizeIdx)?.optJSONArray("values") ?: return emptyList()
        val out = mutableListOf<StudioSizeOpt>()
        for (i in 0 until vals.length()) {
            val v = vals.optJSONObject(i) ?: continue
            val id = v.optLong("id", -1L)
            if (id < 0) continue
            out.add(StudioSizeOpt(id, v.optString("title", "").ifBlank { "?" }))
        }
        if (out.isNotEmpty()) return out
    }
    val seen = linkedSetOf<String>()
    val variants = meta.optJSONArray("variants") ?: return emptyList()
    val fallback = mutableListOf<StudioSizeOpt>()
    for (i in 0 until variants.length()) {
        val v = variants.optJSONObject(i) ?: continue
        if (v.optBoolean("is_enabled", true) == false) continue
        val title = v.optString("title", "").substringBefore("/").trim()
        if (title.isEmpty() || !seen.add(title)) continue
        val opts = v.optJSONArray("options")
        val id = if (sizeIdx >= 0 && opts != null && sizeIdx < opts.length()) {
            opts.optLong(sizeIdx, -1L)
        } else {
            -1L
        }
        if (id >= 0) fallback.add(StudioSizeOpt(id, title))
    }
    return fallback
}

private fun primeVariantSelection(meta: JSONObject): Pair<Long?, Long?> {
    val colors = parseColorOptions(meta)
    val sizes = parseSizeOptions(meta)
    val colorId = colors.firstOrNull { it.hex.equals("#ffffff", true) }?.id ?: colors.firstOrNull()?.id
    val sizeId = sizes.firstOrNull { it.title.equals("M", true) }?.id ?: sizes.firstOrNull()?.id
    return colorId to sizeId
}

private fun findVariantBySelections(meta: JSONObject, colorId: Long?, sizeId: Long?): JSONObject? {
    val options = meta.optJSONArray("options") ?: return null
    val (colorIdx, sizeIdx) = inferColorSizeIndices(options)
    val variants = meta.optJSONArray("variants") ?: return null
    for (i in 0 until variants.length()) {
        val v = variants.optJSONObject(i) ?: continue
        if (!v.optBoolean("is_enabled", true)) continue
        val opts = v.optJSONArray("options") ?: continue
        if (colorId != null && colorIdx >= 0 && colorIdx < opts.length()) {
            if (opts.optLong(colorIdx, -1) != colorId) continue
        }
        if (sizeId != null && sizeIdx >= 0 && sizeIdx < opts.length()) {
            if (opts.optLong(sizeIdx, -1) != sizeId) continue
        }
        return v
    }
    for (i in 0 until variants.length()) {
        val v = variants.optJSONObject(i) ?: continue
        if (v.optBoolean("is_enabled", true)) return v
    }
    return null
}

@Composable
private fun StudioPlacedDesignImage(
    url: String,
    modifier: Modifier = Modifier,
    designDx: Float,
    designDy: Float,
    designScale: Float,
    designRotate: Float
) {
    val context = LocalContext.current
    val bitmap = remember(url) {
        if (!url.startsWith("data:")) return@remember null
        runCatching {
            val payload = url.substringAfter("base64,", url)
            val bytes = Base64.decode(payload, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = modifier.graphicsLayer {
                translationX = designDx
                translationY = designDy
                scaleX = designScale
                scaleY = designScale
                rotationZ = designRotate
            },
            contentScale = ContentScale.Fit
        )
    } else {
        AsyncImage(
            model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
            contentDescription = null,
            modifier = modifier.graphicsLayer {
                translationX = designDx
                translationY = designDy
                scaleX = designScale
                scaleY = designScale
                rotationZ = designRotate
            },
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun StudioSourcesDrawer(
    onUpload: () -> Unit,
    onPublicDesigns: () -> Unit,
    onMyDesigns: () -> Unit,
    onSavedDrafts: () -> Unit,
    t: (String, String) -> String
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        StudioDarkBtn(onClick = onUpload) {
            Text(t("design_studio.shop.browse_device", "Upload from device"))
        }
        StudioDarkBtn(onClick = onPublicDesigns) {
            Text(t("design_studio.shop.public_designs", "Public inspirations"))
        }
        StudioDarkBtn(onClick = onMyDesigns) {
            Text(t("design_studio.shop.my_designs", "My designs"))
        }
        StudioDarkBtn(onClick = onSavedDrafts) {
            Text(t("design_studio.shop.saved_drafts", "Saved drafts"))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudioDesignPickerDialog(
    mode: String,
    api: CreatorApi,
    ownerId: String?,
    productKey: String,
    onDismiss: () -> Unit,
    onPick: (StudioPickResult) -> Unit,
    t: (String, String) -> String
) {
    var loading by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<StudioPickerRow>>(emptyList()) }
    var totalCount by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var filterParams by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    var pendingDeleteDraftId by remember { mutableStateOf<String?>(null) }
    var deleteInProgress by remember { mutableStateOf(false) }
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val isPublic = mode == "public"
    val isDrafts = mode == "drafts"
    val oid = ownerId?.trim().orEmpty()

    fun pickDesignUrl(it: JSONObject): String? {
        val url = it.optString("preview_url", "")
            .ifBlank { it.optString("original_url", "") }
            .ifBlank { it.optString("image_url", "") }
            .ifBlank { it.optString("design_image_url", "") }
        return url.takeIf { it.startsWith("http") || it.startsWith("data:") }
    }

    LaunchedEffect(mode, oid, searchQuery, filterParams, reloadKey) {
        loading = true
        items = emptyList()
        totalCount = 0
        if (oid.isEmpty() && mode != "public") {
            loading = false
            return@LaunchedEffect
        }
        try {
            when (mode) {
                "public" -> {
                    val (rows, total) = withContext(Dispatchers.IO) {
                        api.listPublicAll(
                            search = searchQuery.trim().ifBlank { null },
                            filterParams = filterParams
                        )
                    }
                    totalCount = total
                    items = rows.mapNotNull { row ->
                        val url = pickDesignUrl(row) ?: return@mapNotNull null
                        val id = row.optString("id", "")
                        StudioPickerRow(id, url, designId = id, fromPublicInspiration = true)
                    }
                }
                "mine" -> {
                    val data = withContext(Dispatchers.IO) { api.listDesigns(oid, 100) }
                    if (data.optBoolean("ok", false)) {
                        val arr = data.optJSONArray("items") ?: JSONArray()
                        val list = mutableListOf<StudioPickerRow>()
                        for (i in 0 until arr.length()) {
                            val it = arr.optJSONObject(i) ?: continue
                            val url = pickDesignUrl(it) ?: continue
                            list.add(StudioPickerRow("${it.optString("id", "$i")}", url))
                        }
                        items = list
                        totalCount = list.size
                    }
                }
                "drafts" -> {
                    val data = withContext(Dispatchers.IO) {
                        api.printifyStudioTestListDrafts(oid, productKey)
                    }
                    val list = mutableListOf<StudioPickerRow>()
                    val drafts = data.optJSONArray("items") ?: data.optJSONArray("drafts") ?: JSONArray()
                    for (i in 0 until drafts.length()) {
                        val d = drafts.optJSONObject(i) ?: continue
                        val url = pickDesignUrl(d) ?: continue
                        list.add(StudioPickerRow(d.optString("id", "$i"), url))
                    }
                    items = list
                    totalCount = list.size
                }
            }
        } catch (_: Exception) {
            items = emptyList()
            totalCount = 0
        } finally {
            loading = false
        }
    }

    pendingDeleteDraftId?.let { draftId ->
        StudioConfirmDialog(
            title = t("creator.shop_printify_studio_test.drafts_delete_yes", "Delete"),
            message = t(
                "creator.shop_printify_studio_test.drafts_delete_confirm",
                "Delete this draft? This cannot be undone."
            ),
            confirmLabel = t("creator.shop_printify_studio_test.drafts_delete_yes", "Delete"),
            cancelLabel = t("creator.common.cancel", "Cancel"),
            confirmPrimary = true,
            confirmEnabled = !deleteInProgress,
            onConfirm = {
                val idNum = draftId.toLongOrNull() ?: return@StudioConfirmDialog
                if (oid.isEmpty()) {
                    pendingDeleteDraftId = null
                    return@StudioConfirmDialog
                }
                deleteInProgress = true
                scope.launch {
                    try {
                        val res = withContext(Dispatchers.IO) {
                            api.printifyStudioTestDeleteDraft(oid, idNum)
                        }
                        if (res.optBoolean("ok", false)) {
                            pendingDeleteDraftId = null
                            reloadKey += 1
                        }
                    } catch (_: Exception) {
                    } finally {
                        deleteInProgress = false
                    }
                }
            },
            onDismiss = {
                if (!deleteInProgress) pendingDeleteDraftId = null
            }
        )
    }

    if (showFilterSheet && isPublic) {
        EazBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = filterSheetState,
            containerColor = Color(0xFF0F172A)
        ) {
            StudioPublicDesignFilterSheet(
                current = filterParams,
                onApply = {
                    filterParams = it
                    showFilterSheet = false
                },
                onReset = {
                    filterParams = emptyMap()
                    showFilterSheet = false
                },
                t = t
            )
        }
    }

    EazStandardDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.82f)
                .background(Color(0xFF0F172A), RoundedCornerShape(14.dp))
                .padding(12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (mode) {
                        "public" -> t("design_studio.shop.public_designs", "Public inspirations")
                        "mine" -> t("design_studio.shop.my_designs", "My designs")
                        else -> t("design_studio.shop.saved_drafts", "Saved drafts")
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isDrafts) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        placeholder = {
                            Text(
                                t("creator.shop_printify_studio_test.design_picker_search_placeholder", "Search designs…"),
                                color = Color.White.copy(0.45f)
                            )
                        },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, null, tint = Color.White.copy(0.7f))
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFF97316),
                            unfocusedBorderColor = Color.White.copy(0.25f),
                            cursorColor = Color(0xFFF97316)
                        )
                    )
                }
                if (isPublic) {
                    Text(
                        "$totalCount Designs",
                        color = Color.White.copy(0.55f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                    IconButton(
                        onClick = { showFilterSheet = true },
                        modifier = Modifier
                            .size(40.dp)
                            .border(2.dp, Color(0xFFF97316), RoundedCornerShape(999.dp))
                    ) {
                        Text("☰", color = Color(0xFFF97316), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color(0xFFF97316))
                }
            } else if (items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        when (mode) {
                            "drafts" -> t(
                                "creator.shop_printify_studio_test.drafts_empty",
                                "No saved drafts yet."
                            )
                            "mine" -> t("design_studio.shop.my_designs_empty", "No designs in your library yet.")
                            else -> t("creator.shop_printify_studio_test.design_picker_empty", "No public designs found.")
                        },
                        color = Color.White.copy(0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 100.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.id }) { row ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .clickable {
                                    onPick(
                                        StudioPickResult(
                                            url = row.url,
                                            designId = row.designId,
                                            fromPublicInspiration = row.fromPublicInspiration
                                        )
                                    )
                                }
                        ) {
                            AsyncImage(
                                model = row.url,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp),
                                contentScale = ContentScale.Fit
                            )
                            if (isDrafts) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .border(1.dp, Color(0xFFCBD5E1), CircleShape)
                                        .clickable { pendingDeleteDraftId = row.id },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "×",
                                        color = Color(0xFF334155),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
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

@Composable
private fun StudioPublicDesignFilterSheet(
    current: Map<String, String>,
    onApply: (Map<String, String>) -> Unit,
    onReset: () -> Unit,
    t: (String, String) -> String
) {
    var draft by remember(current) { mutableStateOf(current.toMutableMap()) }

    fun toggleParam(key: String, value: String) {
        draft = draft.toMutableMap().apply {
            if (this[key] == value) remove(key) else put(key, value)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .navigationBarsPadding()
    ) {
        Text(
            t("creator.filter_modal.title", "Filter"),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(10.dp))
        Text(t("creator.shop_printify_studio_test.pick_color_title", "Source"), color = Color(0xFFF97316), fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            listOf("generated" to "Generated", "uploaded" to "Uploaded", "personalized" to "Personalized").forEach { (v, label) ->
                StudioDarkBtn(onClick = { toggleParam("filter_design_art", v) }) {
                    Text(label, fontWeight = if (draft["filter_design_art"] == v) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Text(t("creator.shop_printify_studio_test.pick_size_title", "Ratio"), color = Color(0xFFF97316), fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            listOf("portrait" to "Portrait", "landscape" to "Landscape", "square" to "Square").forEach { (v, label) ->
                StudioDarkBtn(onClick = { toggleParam("filter_ratio", v) }) {
                    Text(label, fontWeight = if (draft["filter_ratio"] == v) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.weight(1f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.3f))
            ) {
                Text(t("creator.filter_modal.reset", "Reset"), color = Color.White)
            }
            OutlinedButton(
                onClick = { onApply(draft.toMap()) },
                modifier = Modifier.weight(1f),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF0F172A)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF97316))
            ) {
                Text(t("creator.common.close", "Close"), color = Color(0xFFF97316))
            }
        }
    }
}

@Composable
private fun StudioConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmPrimary: Boolean = true,
    confirmEnabled: Boolean = true
) {
    EazStandardDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0F172A))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF020617))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    message,
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF020617))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text(cancelLabel, maxLines = 2, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onConfirm,
                    enabled = confirmEnabled,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (confirmPrimary) Color(0xFFF97316) else Color(0xFFB91C1C),
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFFF97316).copy(alpha = 0.45f)
                    )
                ) {
                    Text(confirmLabel, maxLines = 2, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                }
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
