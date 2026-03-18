package com.eazpire.creator.ui.creator

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.TranslationStore
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Dark theme colors matching web gen-select-overlay */
private val GenModalOverlay = Color(0x33000000)
private val GenModalBg = Color(0x730B1220)
private val GenModalBorder = Color(0x14FFFFFF)
private val GenModalHeaderBg = Color(0x8C0B1220)
private val GenModalBodyBg = Color(0x610B1220)
private val GenCardBg = Color(0x0FFFFFFF)
private val GenCardBorder = Color(0x1AFFFFFF)
private val GenCardBorderHover = Color(0x73F59E0B)
private val GenAccentSoft = Color(0x33F59E0B)
private val GenText = Color(0xFFF9FAFB)
private val GenMuted = Color(0xB3FFFFFF)

private val REF_SOURCE_OPTIONS = listOf(
    "device" to Triple("Device", "Photo or image from your device", Icons.Default.Image),
    "camera" to Triple("Camera", "Take a photo now", Icons.Default.CameraAlt),
    "inspirations" to Triple("Inspirations", "Public designs from the community", Icons.Default.Star),
    "designs" to Triple("My Designs", "Generated & saved designs", Icons.Default.Folder),
    "canvas" to Triple("Canvas", "Create from scratch", Icons.Default.GridOn)
)

@Composable
fun GenRefSourceModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    translationStore: TranslationStore,
    onDevice: () -> Unit,
    onCamera: () -> Unit,
    onInspirations: () -> Unit,
    onMyDesigns: () -> Unit,
    onCanvas: () -> Unit
) {
    if (!visible) return
    GenModalBase(
        title = translationStore.t("creator.generator.select_reference", "Select reference image"),
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            REF_SOURCE_OPTIONS.forEach { (source, triple) ->
                val (titleKey, descKey, icon) = triple
                val title = when (source) {
                    "device" -> translationStore.t("creator.generator.source_device", "Device")
                    "camera" -> translationStore.t("creator.generator.source_camera", "Camera")
                    "inspirations" -> translationStore.t("creator.upload_source.inspirations", "Inspirations")
                    "designs" -> translationStore.t("creator.generator.source_designs", "My Designs")
                    "canvas" -> translationStore.t("creator.generator.source_canvas", "Canvas")
                    else -> titleKey
                }
                val desc = when (source) {
                    "device" -> translationStore.t("creator.generator.source_device_desc", "Photo or image from your device")
                    "camera" -> translationStore.t("creator.generator.source_camera_desc", "Take a photo now")
                    "inspirations" -> translationStore.t("creator.upload_source.inspirations_desc", "Public designs from the community")
                    "designs" -> translationStore.t("creator.generator.source_designs_desc", "Generated & saved designs")
                    "canvas" -> translationStore.t("creator.generator.source_canvas_desc", "Create from scratch")
                    else -> descKey
                }
                val iconColor = when (source) {
                    "device" -> Color(0xFFA78BFA)
                    "camera" -> Color(0xFF67E8F9)
                    "inspirations" -> GenText
                    "designs" -> Color(0xFFEAB308)
                    "canvas" -> Color(0xFF60A5FA)
                    else -> GenText
                }
                GenRefSourceCard(
                    title = title,
                    desc = desc,
                    icon = icon,
                    iconColor = iconColor,
                    onClick = {
                        onDismiss()
                        when (source) {
                            "device" -> onDevice()
                            "camera" -> onCamera()
                            "inspirations" -> onInspirations()
                            "designs" -> onMyDesigns()
                            "canvas" -> onCanvas()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun GenRefSourceCard(
    title: String,
    desc: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(GenCardBg)
            .border(1.dp, GenCardBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(28.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = GenText
            )
            Text(
                text = desc,
                fontSize = 13.sp,
                color = GenMuted
            )
        }
    }
}

@Composable
fun GenTargetProductModal(
    visible: Boolean,
    currentValue: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    translationStore: TranslationStore
) {
    if (!visible) return
    val options = listOf(
        "all" to translationStore.t("creator.generator.anything", "Anything"),
        "unisex-softstyle-cotton-tee" to "Unisex Softstyle Cotton Tee"
    )
    GenModalBase(
        title = translationStore.t("creator.generator.target_product", "Target product"),
        onDismiss = onDismiss
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            options.forEach { (value, label) ->
                GenSelectOptionCard(
                    label = label,
                    selected = currentValue == value,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onSelect(value)
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
fun GenDesignTypeModal(
    visible: Boolean,
    currentValue: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    translationStore: TranslationStore
) {
    if (!visible) return
    val options = listOf(
        "classic" to translationStore.t("creator.design_type.classic", "Classic"),
        "pattern" to "Pattern",
        "all-over" to "All-Over",
        "full-coverage" to "Full-Coverage",
        "panorama" to "Panorama"
    )
    GenModalBase(
        title = translationStore.t("creator.generator.design_type", "Design type"),
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            options.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { (value, label) ->
                        GenSelectOptionCard(
                            label = label,
                            selected = currentValue == value,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onSelect(value)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GenSelectOptionCard(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) GenAccentSoft else GenCardBg)
            .border(
                1.dp,
                if (selected) EazColors.Orange else GenCardBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(14.dp))
    {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = GenText
        )
    }
}

@Composable
fun GenOptionsModal(
    visible: Boolean,
    ratio: String,
    contentType: String,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    onRatioChange: (String) -> Unit,
    onContentTypeChange: (String) -> Unit,
    translationStore: TranslationStore
) {
    val applyLabel = translationStore.t("creator.common.apply", "Apply")
    if (!visible) return
    GenModalBase(
        title = translationStore.t("creator.generator.more_options", "More options"),
        onDismiss = onDismiss,
        showApply = true,
        applyLabel = applyLabel,
        onApply = onApply
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp, 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            GenOptionsRow(
                label = translationStore.t("creator.generator.ratio_title", "Design Ratio"),
                content = {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("square" to "□", "portrait" to "▯", "landscape" to "▭").forEach { (value, _) ->
                            val label = when (value) {
                                "square" -> translationStore.t("creator.generator.ratio_square", "Square")
                                "portrait" -> translationStore.t("creator.generator.ratio_portrait", "Portrait")
                                "landscape" -> translationStore.t("creator.generator.ratio_landscape", "Landscape")
                                else -> value
                            }
                            GenRatioButton(
                                modifier = Modifier.weight(1f),
                                label = label,
                                selected = ratio == value,
                                onClick = { onRatioChange(value) }
                            )
                        }
                    }
                }
            )
            GenOptionsRow(
                label = translationStore.t("creator.generator.content_type_title", "Content Type"),
                content = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        listOf(
                            "design-text" to translationStore.t("creator.generator.content_design_text", "Design + Text"),
                            "design-only" to translationStore.t("creator.generator.content_design_only", "Design Only"),
                            "text-only" to translationStore.t("creator.generator.content_text_only", "Text Only")
                        ).forEach { (value, label) ->
                            GenContentTypeCard(
                                modifier = Modifier.weight(1f),
                                label = label,
                                selected = contentType == value,
                                onClick = { onContentTypeChange(value) }
                            )
                        }
                    }
                }
            )
            GenOptionsRow(
                label = translationStore.t("creator.generator.style_title", "Design Style"),
                content = {
                    GenSummaryButton(
                        text = "0 ${translationStore.t("creator.generator.styles_selected", "styles selected")}"
                    )
                }
            )
            GenOptionsRow(
                label = translationStore.t("creator.generator.language_title", "Language"),
                content = {
                    GenSummaryButton(
                        text = translationStore.t("creator.generator.language_as_design", "As in design")
                    )
                }
            )
            GenOptionsRow(
                label = translationStore.t("creator.generator.design_colors", "Design colors"),
                content = {
                    GenSummaryButton(
                        text = translationStore.t("creator.generator.design_summary_initial", "Design: 0 · Background: transparent")
                    )
                }
            )
        }
    }
}

@Composable
private fun GenOptionsRow(
    label: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = GenMuted
        )
        content()
    }
}

@Composable
private fun GenRatioButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) GenAccentSoft else Color.White.copy(alpha = 0.08f))
            .border(
                1.dp,
                if (selected) EazColors.Orange else GenCardBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = GenText
        )
    }
}

@Composable
private fun GenContentTypeCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) GenAccentSoft else Color.White.copy(alpha = 0.08f))
            .border(
                1.dp,
                if (selected) EazColors.Orange else GenCardBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = GenText
        )
    }
}

@Composable
private fun GenSummaryButton(
    text: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.2f))
            .border(1.dp, GenCardBorder, RoundedCornerShape(10.dp))
            .padding(12.dp, 14.dp)
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = GenText
        )
    }
}

@Composable
fun GenInspirationModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    api: CreatorApi,
    translationStore: TranslationStore,
    onSelect: (String) -> Unit
) {
    if (!visible) return
    var designs by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(visible, searchQuery) {
        if (!visible) return@LaunchedEffect
        loading = true
        try {
            val resp = api.listPublic(limit = 100, search = searchQuery.ifBlank { null })
            designs = if (resp.optBoolean("ok", false)) {
                resp.optJSONArray("items")?.let { arr ->
                    (0 until arr.length()).map { arr.getJSONObject(it) }
                } ?: emptyList()
            } else emptyList()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            designs = emptyList()
        } finally {
            if (coroutineContext.isActive) loading = false
        }
    }

    GenModalBase(
        title = translationStore.t("creator.inspiration.title", "Inspirations"),
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        translationStore.t("creator.inspiration.search_placeholder", "Search designs..."),
                        color = GenMuted
                    )
                },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = GenText,
                    unfocusedTextColor = GenText,
                    focusedBorderColor = EazColors.Orange.copy(alpha = 0.5f),
                    unfocusedBorderColor = GenCardBorder,
                    cursorColor = EazColors.Orange,
                    focusedContainerColor = Color.Black.copy(alpha = 0.2f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.2f)
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "${designs.size} ${translationStore.t("creator.inspiration.designs", "Designs")}",
                fontSize = 12.sp,
                color = GenMuted
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = EazColors.Orange)
                }
            } else if (designs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = translationStore.t("creator.inspiration.empty", "No public designs found."),
                        color = GenMuted
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(designs) { design ->
                        val url = design.optString("preview_url", "").ifBlank {
                            design.optString("original_url", "")
                        }
                        if (url.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(GenCardBg)
                                    .border(1.dp, GenCardBorder, RoundedCornerShape(10.dp))
                                    .clickable { onSelect(url) },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GenMyDesignsModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    api: CreatorApi,
    ownerId: String,
    translationStore: TranslationStore,
    onSelect: (String) -> Unit
) {
    if (!visible) return
    var designs by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(visible, ownerId) {
        if (!visible || ownerId.isBlank()) return@LaunchedEffect
        loading = true
        try {
            val savedResp = api.listDesigns(ownerId, limit = 100)
            val genResp = api.listGenerated(ownerId, limit = 200)
            val saved = if (savedResp.optBoolean("ok", false)) {
                savedResp.optJSONArray("items")?.let { arr ->
                    (0 until arr.length()).map { arr.getJSONObject(it) }
                } ?: emptyList()
            } else emptyList()
            val generated = if (genResp.optBoolean("ok", false)) {
                genResp.optJSONArray("items")?.let { arr ->
                    (0 until arr.length()).map { arr.getJSONObject(it) }
                } ?: emptyList()
            } else emptyList()
            val savedJobIds = saved.mapNotNull { it.optString("job_id", null).takeIf { s -> s.isNotBlank() } }.toSet()
            val merged = saved.map { obj ->
                obj.put("_url", obj.optString("original_url", "").ifBlank { obj.optString("preview_url", "") })
                obj.put("_title", obj.optString("title", "Design"))
            } + generated.filter { it.optString("job_id", "").let { j -> j.isBlank() || j !in savedJobIds } }.map { obj ->
                obj.put("_url", obj.optString("image_url", "").ifBlank { obj.optString("preview_url", "") })
                obj.put("_title", obj.optString("prompt", "Generated design"))
            }
            designs = merged.filter { it.optString("_url", "").isNotBlank() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            designs = emptyList()
        } finally {
            if (coroutineContext.isActive) loading = false
        }
    }

    val filteredDesigns = remember(designs, searchQuery) {
        if (searchQuery.isBlank()) designs
        else {
            val q = searchQuery.lowercase()
            designs.filter {
                (it.optString("_title", "") + it.optString("title", "") + it.optString("prompt", "")).lowercase().contains(q)
            }
        }
    }

    GenModalBase(
        title = translationStore.t("creator.generator.source_designs", "My Designs"),
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        translationStore.t("creator.inspiration.search_placeholder", "Search designs..."),
                        color = GenMuted
                    )
                },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = GenText,
                    unfocusedTextColor = GenText,
                    focusedBorderColor = EazColors.Orange.copy(alpha = 0.5f),
                    unfocusedBorderColor = GenCardBorder,
                    cursorColor = EazColors.Orange,
                    focusedContainerColor = Color.Black.copy(alpha = 0.2f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.2f)
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = EazColors.Orange)
                }
            } else if (filteredDesigns.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = translationStore.t("creator.my_designs.empty", "No designs found."),
                        color = GenMuted
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredDesigns) { design ->
                        val url = design.optString("_url", "")
                        if (url.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(GenCardBg)
                                    .border(1.dp, GenCardBorder, RoundedCornerShape(10.dp))
                                    .clickable { onSelect(url) },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class CanvasStroke(val points: List<Offset>, val color: Color, val width: Float)

private val CANVAS_COLORS = listOf(
    Color.Black, Color.White, Color(0xFFEF4444), Color(0xFFF97316), Color(0xFFEAB308),
    Color(0xFF22C55E), Color(0xFF3B82F6), Color(0xFF8B5CF6), Color(0xFFEC4899)
)

@Composable
private fun DataUrlImage(
    dataUrl: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    var bitmap by remember(dataUrl) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(dataUrl) {
        if (dataUrl.isBlank()) bitmap = null
        else {
            bitmap = withContext(Dispatchers.Default) {
                try {
                    val base64 = dataUrl.substringAfter(",", "")
                    if (base64.isBlank()) return@withContext null
                    val bytes = Base64.decode(base64, Base64.DEFAULT)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    bmp?.asImageBitmap()
                } catch (_: Exception) { null }
            }
        }
    }
    bitmap?.let { bmp ->
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}

@Composable
fun GenCanvasEditModal(
    visible: Boolean,
    backgroundImageDataUrl: String?,
    onDismiss: () -> Unit,
    translationStore: TranslationStore,
    onConfirm: (String) -> Unit
) {
    if (!visible || backgroundImageDataUrl.isNullOrBlank()) return
    var drawColor by remember { mutableStateOf(Color.Black) }
    var strokeWidth by remember { mutableStateOf(8f) }
    val strokes = remember { mutableStateListOf<CanvasStroke>() }
    var currentStroke by remember { mutableStateOf<MutableList<Offset>?>(null) }
    var canvasSize by remember { mutableStateOf(400f) }
    val scope = rememberCoroutineScope()

    fun exportToDataUrl() {
        val strokesSnapshot = strokes.toList()
        val currentSnapshot = currentStroke?.takeIf { it.size >= 2 }?.let { CanvasStroke(it, drawColor, strokeWidth) }
        val allStrokesSnapshot = strokesSnapshot + (currentSnapshot?.let { listOf(it) } ?: emptyList())
        val sizeSnapshot = if (canvasSize < 10f) 400f else canvasSize
        val bgSnapshot = backgroundImageDataUrl
        scope.launch {
            val dataUrl = withContext(Dispatchers.Default) {
                val outSize = 512
                val bitmap = android.graphics.Bitmap.createBitmap(outSize, outSize, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                try {
                    val base64 = bgSnapshot.substringAfter(",", "")
                    if (base64.isNotBlank()) {
                        val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                        val bg = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bg != null) {
                            val dst = android.graphics.Rect(0, 0, outSize, outSize)
                            canvas.drawBitmap(bg, null, dst, android.graphics.Paint().apply { isFilterBitmap = true })
                            if (!bg.isRecycled) bg.recycle()
                        } else {
                            canvas.drawColor(android.graphics.Color.WHITE)
                        }
                    } else {
                        canvas.drawColor(android.graphics.Color.WHITE)
                    }
                } catch (_: Exception) {
                    canvas.drawColor(android.graphics.Color.WHITE)
                }
                val paint = android.graphics.Paint().apply {
                    style = android.graphics.Paint.Style.STROKE
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                    isAntiAlias = true
                }
                val scale = outSize.toFloat() / sizeSnapshot
                allStrokesSnapshot.forEach { stroke ->
                    if (stroke.points.size < 2) return@forEach
                    paint.color = stroke.color.toArgb()
                    paint.strokeWidth = stroke.width * scale
                    val path = android.graphics.Path()
                    path.moveTo(stroke.points[0].x * scale, stroke.points[0].y * scale)
                    stroke.points.drop(1).forEach { path.lineTo(it.x * scale, it.y * scale) }
                    canvas.drawPath(path, paint)
                }
                val out = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                "data:image/png;base64,${android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)}"
            }
            onConfirm(dataUrl)
        }
    }

    GenModalBase(
        title = translationStore.t("creator.canvas.edit_title", "Edit design"),
        onDismiss = onDismiss,
        showApply = true,
        applyLabel = translationStore.t("creator.canvas.save_edit", "Save & use"),
        onApply = { exportToDataUrl() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = translationStore.t("creator.canvas.color", "Color"),
                        fontSize = 12.sp,
                        color = GenMuted
                    )
                    CANVAS_COLORS.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(color)
                                .border(
                                    width = if (drawColor == color) 2.dp else 1.dp,
                                    color = if (drawColor == color) EazColors.Orange else GenCardBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { drawColor = color }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                Text(
                    text = translationStore.t("creator.canvas.brush_size", "Size"),
                    fontSize = 12.sp,
                    color = GenMuted
                )
                Slider(
                    value = strokeWidth,
                    onValueChange = { strokeWidth = it },
                    valueRange = 2f..40f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = EazColors.Orange,
                        activeTrackColor = EazColors.Orange
                    )
                )
                TextButton(onClick = {
                    strokes.clear()
                    currentStroke = null
                }) {
                    Text(
                        text = translationStore.t("creator.common.clear", "Clear"),
                        color = GenText
                    )
                }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, GenCardBorder, RoundedCornerShape(12.dp))
                    .onSizeChanged { canvasSize = minOf(it.width, it.height).toFloat() }
                    .pointerInput(backgroundImageDataUrl) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentStroke = mutableListOf(offset)
                            },
                            onDrag = { change, _ ->
                                currentStroke?.add(change.position)
                            },
                            onDragEnd = {
                                currentStroke?.let { pts ->
                                    if (pts.size >= 2) strokes.add(CanvasStroke(pts.toList(), drawColor, strokeWidth))
                                }
                                currentStroke = null
                            }
                        )
                    }
            ) {
                DataUrlImage(
                    dataUrl = backgroundImageDataUrl,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
                Canvas(modifier = Modifier.fillMaxSize()) {
                    canvasSize = size.minDimension
                    val allStrokes = strokes + (currentStroke?.takeIf { it.size >= 2 }?.let { listOf(CanvasStroke(it, drawColor, strokeWidth)) } ?: emptyList())
                    allStrokes.forEach { stroke ->
                        if (stroke.points.size < 2) return@forEach
                        val path = Path().apply {
                            moveTo(stroke.points[0].x, stroke.points[0].y)
                            stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(
                            path = path,
                            color = stroke.color,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = stroke.width,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GenCanvasModal(
    visible: Boolean,
    onDismiss: () -> Unit,
    translationStore: TranslationStore,
    onConfirm: (String) -> Unit
) {
    if (!visible) return
    var drawColor by remember { mutableStateOf(Color.Black) }
    var strokeWidth by remember { mutableStateOf(8f) }
    val strokes = remember { mutableStateListOf<CanvasStroke>() }
    var currentStroke by remember { mutableStateOf<MutableList<Offset>?>(null) }
    var canvasSize by remember { mutableStateOf(400f) }
    val scope = rememberCoroutineScope()

    fun exportToDataUrl() {
        val strokesSnapshot = strokes.toList()
        val currentSnapshot = currentStroke?.takeIf { it.size >= 2 }?.let { CanvasStroke(it, drawColor, strokeWidth) }
        val allStrokesSnapshot = strokesSnapshot + (currentSnapshot?.let { listOf(it) } ?: emptyList())
        val sizeSnapshot = if (canvasSize < 10f) 400f else canvasSize
        scope.launch {
            val dataUrl = withContext(Dispatchers.Default) {
                val outSize = 512
                val bitmap = android.graphics.Bitmap.createBitmap(outSize, outSize, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(android.graphics.Color.WHITE)
                val paint = android.graphics.Paint().apply {
                    style = android.graphics.Paint.Style.STROKE
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                    isAntiAlias = true
                }
                val scale = outSize.toFloat() / sizeSnapshot
                allStrokesSnapshot.forEach { stroke ->
                    if (stroke.points.size < 2) return@forEach
                    paint.color = stroke.color.toArgb()
                    paint.strokeWidth = stroke.width * scale
                    val path = android.graphics.Path()
                    path.moveTo(stroke.points[0].x * scale, stroke.points[0].y * scale)
                    stroke.points.drop(1).forEach { path.lineTo(it.x * scale, it.y * scale) }
                    canvas.drawPath(path, paint)
                }
                val out = java.io.ByteArrayOutputStream()
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                "data:image/png;base64,${android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)}"
            }
            onConfirm(dataUrl)
        }
    }

    GenModalBase(
        title = translationStore.t("creator.canvas.title", "Canvas Sketch"),
        onDismiss = onDismiss,
        showApply = true,
        applyLabel = translationStore.t("creator.canvas.use_drawing", "Use drawing"),
        onApply = { exportToDataUrl() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = translationStore.t("creator.canvas.color", "Color"),
                        fontSize = 12.sp,
                        color = GenMuted
                    )
                    CANVAS_COLORS.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(color)
                                .border(
                                    width = if (drawColor == color) 2.dp else 1.dp,
                                    color = if (drawColor == color) EazColors.Orange else GenCardBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { drawColor = color }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = translationStore.t("creator.canvas.brush_size", "Size"),
                        fontSize = 12.sp,
                        color = GenMuted
                    )
                    Slider(
                        value = strokeWidth,
                        onValueChange = { strokeWidth = it },
                        valueRange = 2f..40f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = EazColors.Orange,
                            activeTrackColor = EazColors.Orange
                        )
                    )
                    TextButton(onClick = {
                        strokes.clear()
                        currentStroke = null
                    }) {
                        Text(
                            translationStore.t("creator.common.clear", "Clear"),
                            color = GenText
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .border(1.dp, GenCardBorder, RoundedCornerShape(12.dp))
                    .onSizeChanged { canvasSize = minOf(it.width, it.height).toFloat() }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentStroke = mutableListOf(offset)
                            },
                            onDrag = { change, _ ->
                                currentStroke?.add(change.position)
                            },
                            onDragEnd = {
                                currentStroke?.let { pts ->
                                    if (pts.size >= 2) strokes.add(CanvasStroke(pts.toList(), drawColor, strokeWidth))
                                }
                                currentStroke = null
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    canvasSize = size.minDimension
                    val allStrokes = strokes + (currentStroke?.takeIf { it.size >= 2 }?.let { listOf(CanvasStroke(it, drawColor, strokeWidth)) } ?: emptyList())
                    allStrokes.forEach { stroke ->
                        if (stroke.points.size < 2) return@forEach
                        val path = Path().apply {
                            moveTo(stroke.points[0].x, stroke.points[0].y)
                            stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(
                            path = path,
                            color = stroke.color,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = stroke.width,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GenModalBase(
    title: String,
    onDismiss: () -> Unit,
    showApply: Boolean = false,
    applyLabel: String = "Apply",
    onApply: () -> Unit = {},
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(GenModalOverlay)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GenModalBg)
                    .border(1.dp, GenModalBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(GenModalHeaderBg)
                        .padding(12.dp, 18.dp, 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = GenText
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .border(1.dp, GenCardBorder, RoundedCornerShape(10.dp))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = GenText, modifier = Modifier.size(14.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(GenModalBodyBg)
                ) {
                    content()
                }
                if (showApply) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.2f))
                            .padding(14.dp, 18.dp, 18.dp)
                    ) {
                        TextButton(
                            onClick = {
                                onApply()
                                onDismiss()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(EazColors.Orange.copy(alpha = 0.2f))
                                .border(1.dp, EazColors.Orange, RoundedCornerShape(12.dp))
                        ) {
                            Text(
                                text = applyLabel,
                                color = GenText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
