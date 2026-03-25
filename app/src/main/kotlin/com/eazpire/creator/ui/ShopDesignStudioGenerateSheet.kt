package com.eazpire.creator.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.eazpire.creator.R
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.creator.CanvasSessionState
import com.eazpire.creator.ui.creator.GenCanvasEditModal
import com.eazpire.creator.ui.creator.GenCanvasModal
import com.eazpire.creator.ui.creator.GenColorState
import com.eazpire.creator.ui.creator.GenDesignTypeModal
import com.eazpire.creator.ui.creator.GenInspirationModal
import com.eazpire.creator.ui.creator.GenLanguageState
import com.eazpire.creator.ui.creator.GenMyDesignsModal
import com.eazpire.creator.ui.creator.GenOptionsModal
import com.eazpire.creator.ui.creator.GenRefSourceModal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.util.Base64
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.toArgb
import com.eazpire.creator.ui.creator.CanvasStroke
import org.json.JSONArray
import org.json.JSONObject

private data class ShopRefSlot(
    val label: String,
    val url: String,
    val strength: Int = 80,
    /** JSON array string of canvas strokes (same schema as web `creator-canvas-sketch-modal.js`). */
    val canvasStrokesJson: String? = null
)

private fun serializeCanvasStrokes(strokes: List<CanvasStroke>): String {
    val arr = JSONArray()
    for (st in strokes) {
        val o = JSONObject()
        val pts = JSONArray()
        for (p in st.points) {
            pts.put(JSONObject().put("x", p.x.toDouble()).put("y", p.y.toDouble()))
        }
        o.put("points", pts)
        o.put("color", String.format("#%08X", st.color.toArgb()))
        o.put("width", st.width.toDouble())
        val tr = st.transform
        o.put(
            "transform",
            JSONObject()
                .put("tx", tr.tx.toDouble())
                .put("ty", tr.ty.toDouble())
                .put("scale", tr.scale.toDouble())
                .put("rotation", tr.rotation.toDouble())
        )
        arr.put(o)
    }
    return arr.toString()
}

private val DESIGN_TYPE_LABELS = listOf(
    "classic" to "Classic",
    "pattern" to "Pattern",
    "all-over" to "All-Over",
    "full-coverage" to "Full-Coverage",
    "panorama" to "Panorama"
)

/**
 * Shop Create Product — generate: parity with web design studio (light shell, Eazy CTA, up to 5 refs, no EAZ price).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShopDesignStudioGenerateSheet(
    product: CatalogProduct,
    catalogProducts: List<CatalogProduct>,
    api: CreatorApi,
    ownerId: String?,
    translationStore: TranslationStore,
    translation: (String, String) -> String,
    onDismiss: () -> Unit,
    onRequireLogin: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var prompt by remember { mutableStateOf("") }
    var refs by remember { mutableStateOf<List<ShopRefSlot>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    var suggestLoading by remember { mutableStateOf(false) }

    var selectedProductKeys by remember(product.productKey) {
        mutableStateOf(setOf(product.productKey))
    }
    var designType by remember { mutableStateOf("classic") }
    var ratio by remember { mutableStateOf("portrait") }
    var contentType by remember { mutableStateOf("design-text") }
    var selectedStyles by remember { mutableStateOf<List<String>>(emptyList()) }
    var languageState by remember { mutableStateOf(GenLanguageState()) }
    var colorState by remember { mutableStateOf(GenColorState()) }

    var showRefSource by remember { mutableStateOf(false) }
    var showInsp by remember { mutableStateOf(false) }
    var showMyDesigns by remember { mutableStateOf(false) }
    var showCanvas by remember { mutableStateOf(false) }
    var showCanvasEdit by remember { mutableStateOf(false) }
    var canvasEditIndex by remember { mutableStateOf(0) }
    var canvasEmptySessionKey by remember { mutableStateOf(0) }
    var canvasEditSessionKey by remember { mutableStateOf(0) }
    val shopCanvasSession = remember(canvasEmptySessionKey) { CanvasSessionState() }
    val shopCanvasEditSession = remember(canvasEditIndex, canvasEditSessionKey) { CanvasSessionState() }
    var showDesignType by remember { mutableStateOf(false) }
    var showTargetProducts by remember { mutableStateOf(false) }
    var showOptions by remember { mutableStateOf(false) }
    var similarityEditIndex by remember { mutableStateOf<Int?>(null) }
    var eazyDragX by remember { mutableStateOf(0f) }
    var eazyDragY by remember { mutableStateOf(0f) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val refRowScroll = rememberScrollState()

    fun t(key: String, def: String) = translationStore.t(key, def)

    fun relabel(list: List<ShopRefSlot>): List<ShopRefSlot> {
        val letters = listOf("A", "B", "C", "D", "E")
        return list.mapIndexed { i, r -> r.copy(label = letters.getOrElse(i) { "?" }) }
    }

    fun addRef(url: String, canvasStrokesJson: String? = null) {
        if (refs.size >= 5) return
        refs = relabel(refs + ShopRefSlot("?", url, 80, canvasStrokesJson = canvasStrokesJson))
    }

    fun removeRef(index: Int) {
        refs = relabel(refs.filterIndexed { i, _ -> i != index })
    }

    fun updateRefStrength(index: Int, strength: Int) {
        if (index !in refs.indices) return
        val v = strength.coerceIn(0, 100)
        refs = relabel(refs.mapIndexed { i, r -> if (i == index) r.copy(strength = v) else r })
    }

    var lastCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok) {
            lastCameraUri?.let { uri ->
                scope.launch {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val bytes = stream.readBytes()
                            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            addRef("data:image/jpeg;base64,$b64")
                        }
                    } catch (_: Exception) {
                    }
                    lastCameraUri = null
                }
            }
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "shop_capture_${System.currentTimeMillis()}.jpg")
            val u = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            lastCameraUri = u
            cameraLauncher.launch(u)
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                try {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        val bytes = stream.readBytes()
                        val mime = context.contentResolver.getType(it) ?: "image/jpeg"
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        addRef("data:$mime;base64,$b64")
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    BackHandler(onBack = onDismiss)

    GenRefSourceModal(
        visible = showRefSource,
        onDismiss = { showRefSource = false },
        translationStore = translationStore,
        shopLightChrome = true,
        onDevice = {
            showRefSource = false
            imagePicker.launch("image/*")
        },
        onCamera = {
            showRefSource = false
            val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                val file = File(context.cacheDir, "shop_capture_${System.currentTimeMillis()}.jpg")
                val u = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                lastCameraUri = u
                cameraLauncher.launch(u)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        },
        onInspirations = {
            showRefSource = false
            showInsp = true
        },
        onMyDesigns = {
            showRefSource = false
            showMyDesigns = true
        },
        onCanvas = {
            showRefSource = false
            canvasEmptySessionKey++
            showCanvas = true
        }
    )

    GenInspirationModal(
        visible = showInsp,
        onDismiss = { showInsp = false },
        api = api,
        translationStore = translationStore,
        shopLightChrome = true,
        onSelect = { imageUrl ->
            scope.launch {
                try {
                    val fullUrl = when {
                        imageUrl.startsWith("http") -> imageUrl
                        imageUrl.startsWith("//") -> "https:$imageUrl"
                        else -> "https://www.eazpire.com${if (imageUrl.startsWith("/")) imageUrl else "/$imageUrl"}"
                    }
                    addRef(fullUrl)
                } catch (_: Exception) {
                }
            }
            showInsp = false
        }
    )

    GenMyDesignsModal(
        visible = showMyDesigns,
        onDismiss = { showMyDesigns = false },
        api = api,
        ownerId = ownerId.orEmpty(),
        translationStore = translationStore,
        shopLightChrome = true,
        onSelect = { imageUrl ->
            addRef(imageUrl)
            showMyDesigns = false
        }
    )

    GenCanvasModal(
        visible = showCanvas,
        onDismiss = { showCanvas = false },
        translationStore = translationStore,
        shopLightChrome = true,
        externalSession = shopCanvasSession,
        onConfirm = { dataUrl ->
            val sj = serializeCanvasStrokes(shopCanvasSession.strokes.toList())
            addRef(dataUrl, sj)
            showCanvas = false
        }
    )

    GenCanvasEditModal(
        visible = showCanvasEdit,
        backgroundImageDataUrl = refs.getOrNull(canvasEditIndex)?.url,
        onDismiss = { showCanvasEdit = false },
        translationStore = translationStore,
        externalSession = shopCanvasEditSession,
        shopLightChrome = true,
        onConfirm = { dataUrl ->
            if (canvasEditIndex in refs.indices) {
                val sj = serializeCanvasStrokes(shopCanvasEditSession.strokes.toList())
                refs = relabel(refs.mapIndexed { i, x ->
                    if (i == canvasEditIndex) x.copy(url = dataUrl, canvasStrokesJson = sj) else x
                })
            }
            showCanvasEdit = false
        }
    )

    GenDesignTypeModal(
        visible = showDesignType,
        currentValue = designType,
        onDismiss = { showDesignType = false },
        onSelect = { designType = it },
        translationStore = translationStore,
        shopLightChrome = true
    )

    GenOptionsModal(
        visible = showOptions,
        ratio = ratio,
        contentType = contentType,
        selectedStyles = selectedStyles,
        languageState = languageState,
        colorState = colorState,
        onDismiss = { showOptions = false },
        onApply = { showOptions = false },
        onRatioChange = { ratio = it },
        onContentTypeChange = { contentType = it },
        onStylesChange = { selectedStyles = it },
        onLanguageChange = { languageState = it },
        onColorStateChange = { colorState = it },
        api = api,
        translationStore = translationStore,
        shopLightChrome = true
    )

    ShopCatalogTargetsModal(
        visible = showTargetProducts,
        catalogProducts = catalogProducts,
        selectedKeys = selectedProductKeys,
        onDismiss = { showTargetProducts = false },
        onApply = { keys ->
            if (keys.isNotEmpty()) selectedProductKeys = keys
            showTargetProducts = false
        },
        translationStore = translationStore
    )

    similarityEditIndex?.let { simIdx ->
        if (simIdx in refs.indices) {
            var pct by remember(simIdx, refs) { mutableStateOf(refs[simIdx].strength.toFloat()) }
            AlertDialog(
                onDismissRequest = { similarityEditIndex = null },
                title = { Text(t("creator.reference_influence.adjust_influence", "Adjust influence")) },
                text = {
                    Column {
                        Text("${pct.toInt()}%", style = MaterialTheme.typography.titleMedium)
                        Slider(
                            value = pct,
                            onValueChange = { pct = it },
                            valueRange = 0f..100f
                        )
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        updateRefStrength(simIdx, pct.toInt())
                        similarityEditIndex = null
                    }) {
                        Text(t("creator.common.apply", "Apply"))
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { similarityEditIndex = null }) {
                        Text(t("creator.common.cancel", "Cancel"))
                    }
                }
            )
        }
    }

    val ready = prompt.trim().isNotEmpty() || refs.isNotEmpty()
    val targetSummary = when {
        selectedProductKeys.isEmpty() -> t("creator.shop_create_product.pick_targets", "Select products")
        selectedProductKeys.size == 1 -> catalogProducts.find { it.productKey == selectedProductKeys.first() }?.title
            ?: selectedProductKeys.first()
        else -> "${selectedProductKeys.size} ${t("creator.shop_create_product.products_selected", "products")}"
    }
    val designTypeLabel = DESIGN_TYPE_LABELS.find { it.first == designType }?.second ?: designType

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        modifier = Modifier.fillMaxHeight(0.95f),
        dragHandle = { ShopSheetDragHandle() }
    ) {
        ShopLightSheetTheme {
            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 88.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            translation("creator.shop_create_product.studio_label", "DESIGNSTUDIO"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = translation("creator.common.close", "Close"))
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            product.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            maxLines = 2
                        )
                        if (!ready) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Image(
                                painter = painterResource(R.drawable.ic_eazy_mascot),
                                contentDescription = null,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    if (ownerId.isNullOrBlank()) {
                        Text(
                            translation("creator.shop_create_product.login_required", "Sign in to create a design."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ShopSheetPrimaryButton(onClick = onRequireLogin, modifier = Modifier.fillMaxWidth()) {
                            Text(translation("creator.shop_create_product.sign_in", "Sign in"))
                        }
                    } else if (success) {
                        Text(
                            translation(
                                "creator.shop_create_product.job_queued",
                                "Your design is being created. You will find it in My designs when it is ready."
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        ShopSheetPrimaryButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                            Text(translation("creator.common.close", "Close"))
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ShopSheetOutlinedButton(
                                onClick = { showTargetProducts = true },
                                enabled = !busy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column {
                                    Text(
                                        t("creator.generator.target_product", "Target product"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        targetSummary,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2
                                    )
                                }
                            }
                            ShopSheetOutlinedButton(
                                onClick = { showDesignType = true },
                                enabled = !busy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column {
                                    Text(
                                        t("creator.generator.design_type", "Design type"),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        designTypeLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFFFF7ED))
                                .border(1.dp, EazColors.Orange.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                                .clickable(enabled = !busy) { showRefSource = true },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📁", fontSize = 32.sp)
                                Text(
                                    translation("creator.upload_source.title", "Select reference image"),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (refs.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                translation("creator.generator.reference_images", "Reference images"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(refRowScroll),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                refs.forEachIndexed { index, slot ->
                                    ShopRefStripItem(
                                        label = slot.label,
                                        imageUrl = slot.url,
                                        strengthPct = slot.strength,
                                        onRemove = { removeRef(index) },
                                        onCanvas = {
                                            canvasEditIndex = index
                                            canvasEditSessionKey++
                                            showCanvasEdit = true
                                        },
                                        onSimilarity = { similarityEditIndex = index },
                                        translationStore = translationStore
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ShopSheetOutlinedButton(
                                onClick = {
                                    scope.launch {
                                        suggestLoading = true
                                        try {
                                            val resp = api.suggestPrompt()
                                            if (resp.optBoolean("ok", false)) {
                                                val s = resp.optString("suggestedPrompt", "")
                                                if (s.isNotBlank()) prompt = s
                                            }
                                        } catch (_: Exception) {
                                        }
                                        suggestLoading = false
                                    }
                                },
                                enabled = !busy && !suggestLoading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text(t("creator.generator.suggest", "Suggest"), modifier = Modifier.padding(start = 4.dp))
                            }
                            ShopSheetOutlinedButton(
                                onClick = { prompt = "" },
                                enabled = !busy,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(t("creator.js.clear", "Clear"))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = prompt,
                            onValueChange = { prompt = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                            label = {
                                Text(translation("creator.shop_create_product.prompt_label", "Describe your design"))
                            },
                            minLines = 4,
                            enabled = !busy,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = EazColors.Orange,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedLabelColor = EazColors.Orange,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                cursorColor = EazColors.Orange,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                            )
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            ShopSheetOutlinedButton(onClick = { showOptions = true }, enabled = !busy) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(t("creator.generator.more_options", "More options"))
                            }
                        }

                        error?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        if (busy) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = EazColors.Orange)
                            }
                        }
                    }
                }

                if (ownerId != null && !ownerId.isBlank() && !success && ready) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .offset { IntOffset(eazyDragX.roundToInt(), eazyDragY.roundToInt()) }
                            .pointerInput(Unit) {
                                detectDragGestures { change, drag ->
                                    change.consume()
                                    eazyDragX += drag.x
                                    eazyDragY += drag.y
                                }
                            }
                    ) {
                        ShopEazySpeechCluster(
                            bubbleText = translationStore.t("creator.generator_eazy.bubble_start", "Start generation"),
                            enabled = !busy,
                            onStart = {
                                val p = prompt.trim()
                                if (p.isEmpty() && refs.isEmpty()) {
                                    error = translationStore.t(
                                        "creator.js.please_prompt_or_image",
                                        "Please provide a prompt or image"
                                    )
                                    return@ShopEazySpeechCluster
                                }
                                if (selectedProductKeys.isEmpty()) {
                                    error = translationStore.t(
                                        "creator.shop_create_product.pick_targets",
                                        "Select at least one target product"
                                    )
                                    return@ShopEazySpeechCluster
                                }
                                error = null
                                scope.launch {
                                    busy = true
                                    try {
                                        val oid = ownerId
                                        val refPayload = refs.map {
                                            CreatorApi.ShopReferenceImage(
                                                url = it.url,
                                                label = it.label,
                                                strength = it.strength
                                            )
                                        }
                                        val bgMode = if (colorState.backgroundTransparent) "transparent" else "solid"
                                        val bgColors = if (bgMode == "solid") colorState.backgroundColors.take(5) else emptyList()
                                        val strokeArr = JSONArray()
                                        refs.forEach { ref ->
                                            val cj = ref.canvasStrokesJson
                                            if (!cj.isNullOrBlank()) {
                                                try {
                                                    strokeArr.put(JSONArray(cj))
                                                } catch (_: Exception) {
                                                    strokeArr.put(null)
                                                }
                                            } else {
                                                strokeArr.put(null)
                                            }
                                        }
                                        val generatorUiSnapshot = JSONObject().apply {
                                            put("v", 1)
                                            put("prompt", p)
                                            put("reference_canvas_strokes", strokeArr)
                                            put("generator_options", JSONObject().apply {
                                                put("ratio", ratio)
                                                put("content_type", contentType)
                                                put("styles", JSONArray().apply { selectedStyles.forEach { put(it) } })
                                                put("design_colors", JSONArray().apply { colorState.designColors.forEach { put(it) } })
                                                put("background_colors", JSONArray().apply { bgColors.forEach { put(it) } })
                                                put("background", JSONObject().put("mode", bgMode))
                                                put("backgroundTransparent", colorState.backgroundTransparent)
                                                put("language", JSONObject().apply {
                                                    put("mode", languageState.mode)
                                                    if (languageState.mode == "manual") put("language", languageState.langCode)
                                                })
                                                put("design_type", designType)
                                            })
                                        }
                                        val res = withContext(Dispatchers.IO) {
                                            api.acceptShopCustomerDesignGenerate(
                                                ownerId = oid,
                                                productKey = product.productKey,
                                                prompt = p,
                                                referenceImages = refPayload,
                                                designType = designType,
                                                targetProductCsv = selectedProductKeys.joinToString(","),
                                                ratio = ratio,
                                                contentType = contentType,
                                                styles = selectedStyles,
                                                designColors = colorState.designColors,
                                                backgroundColors = bgColors,
                                                backgroundMode = bgMode,
                                                languageMode = languageState.mode,
                                                languageCode = languageState.langCode,
                                                generatorUiSnapshot = generatorUiSnapshot
                                            )
                                        }
                                        if (!res.optBoolean("ok", false)) {
                                            error = res.optString("message")
                                                .ifBlank { res.optString("error", "error") }
                                            return@launch
                                        }
                                        val jobId = res.optString("job_id", "").trim()
                                        if (jobId.isEmpty()) {
                                            error = "No job id"
                                            return@launch
                                        }
                                        pollShopDesignJob(api, jobId)
                                        success = true
                                    } catch (e: Exception) {
                                        error = e.message ?: "error"
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopRefStripItem(
    label: String,
    imageUrl: String,
    strengthPct: Int,
    onRemove: () -> Unit,
    onCanvas: () -> Unit,
    onSimilarity: () -> Unit,
    translationStore: TranslationStore
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(88.dp)
    ) {
        Text(
            label,
            fontWeight = FontWeight.Bold,
            color = EazColors.Orange,
            fontSize = 14.sp
        )
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF3F4F6))
                .clickable(onClick = onSimilarity)
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "${strengthPct}%",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onSimilarity)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            IconButton(
                onClick = onCanvas,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Brush, contentDescription = translationStore.t("creator.upload_source.canvas", "Canvas"), modifier = Modifier.size(16.dp))
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = translationStore.t("creator.common.remove", "Remove"), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun ShopCatalogTargetsModal(
    visible: Boolean,
    catalogProducts: List<CatalogProduct>,
    selectedKeys: Set<String>,
    onDismiss: () -> Unit,
    onApply: (Set<String>) -> Unit,
    translationStore: TranslationStore
) {
    if (!visible) return
    var keys by remember(selectedKeys, visible) { mutableStateOf(selectedKeys) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 520.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(16.dp)
        ) {
            Text(
                translationStore.t("creator.generator.target_product", "Target product"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 380.dp)
            ) {
                items(catalogProducts) { p ->
                    val sel = keys.contains(p.productKey)
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .border(2.dp, if (sel) EazColors.Orange else Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
                            .clickable {
                                keys = if (sel) {
                                    val n = keys - p.productKey
                                    if (n.isEmpty()) keys else n
                                } else keys + p.productKey
                            }
                            .padding(8.dp)
                    ) {
                        val img = p.mockUrls.firstOrNull()
                        if (img != null) {
                            AsyncImage(
                                model = img,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(80.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                        Text(p.title, maxLines = 2, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.TextButton(onClick = onDismiss) {
                    Text(translationStore.t("creator.common.cancel", "Cancel"))
                }
                androidx.compose.material3.TextButton(
                    onClick = { onApply(keys) },
                    enabled = keys.isNotEmpty()
                ) {
                    Text(translationStore.t("creator.common.apply", "Apply"))
                }
            }
        }
    }
}

@Composable
internal fun ShopEazySpeechCluster(
    bubbleText: String,
    enabled: Boolean,
    onStart: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "eazySpeechPulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(675, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val bubbleBrush = Brush.linearGradient(
        listOf(Color(0xFFFF9F40), Color(0xFFF97316), Color(0xFFEA580C))
    )
    Row(
        modifier = Modifier.graphicsLayer {
            scaleX = pulseScale
            scaleY = pulseScale
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 168.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(bubbleBrush)
                .border(2.dp, Color.White.copy(alpha = 0.95f), RoundedCornerShape(14.dp))
                .clickable(enabled = enabled, onClick = onStart)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                bubbleText,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Canvas(
            modifier = Modifier
                .size(width = 11.dp, height = 20.dp)
        ) {
            val path = Path().apply {
                moveTo(0f, size.height * 0.18f)
                lineTo(size.width, size.height * 0.5f)
                lineTo(0f, size.height * 0.82f)
                close()
            }
            drawPath(path = path, brush = bubbleBrush)
        }
        Image(
            painter = painterResource(R.drawable.ic_eazy_mascot),
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clickable(enabled = enabled, onClick = onStart)
        )
    }
}