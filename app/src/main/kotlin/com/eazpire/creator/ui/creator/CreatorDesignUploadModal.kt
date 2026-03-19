package com.eazpire.creator.ui.creator

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Design Upload Modal – wie Filter Modal (gleicher Stil, Header, Footer)
 * - Öffnet erst nach File-Explorer-Auswahl
 * - Vorschaubild, Möglichkeit zu entfernen und neues hochzuladen
 * - Creator-Auswahl via Modal (gespeicherte Creator-Namen)
 * - Erst nach User-Klick „Hochladen“ → Upload-Queue
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatorDesignUploadModal(
    onDismiss: () -> Unit,
    selectedImageUri: Uri?,
    onSelectImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onUpload: (creatorName: String?, visibility: String, imageBytes: ByteArray, mimeType: String) -> Unit,
    uploadInProgress: Boolean,
    translationStore: TranslationStore,
    api: CreatorApi,
    ownerId: String
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val config = LocalConfiguration.current
    val maxHeight = (config.screenHeightDp * 0.88f).dp

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var creatorNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedCreator by remember { mutableStateOf<String?>(null) }
    var creatorSelectModalVisible by remember { mutableStateOf(false) }
    var visibilityPublic by remember { mutableStateOf(true) }
    var cropRect by remember { mutableStateOf(CropRect.FULL) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var imageSize by remember { mutableStateOf<IntSize?>(null) }
    var processedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var cropProcessing by remember { mutableStateOf(false) }
    var removeBgProcessing by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) return@LaunchedEffect
        val resp = withContext(Dispatchers.IO) { api.getSettings(ownerId) }
        val settings = resp.optJSONObject("settings") ?: resp
        val arr = settings.optJSONArray("creator_names") ?: JSONArray()
        creatorNames = (0 until arr.length()).mapNotNull { i ->
            arr.optString(i).takeIf { it.isNotBlank() }
        }.distinct()
        if (creatorNames.isNotEmpty() && selectedCreator == null) {
            selectedCreator = creatorNames.firstOrNull()
        }
    }

    LaunchedEffect(selectedImageUri) {
        processedImageBytes = null
        imageSize = null
        cropRect = CropRect.FULL
        actionError = null
        val uri = selectedImageUri ?: return@LaunchedEffect
        val mime = context.contentResolver.getType(uri) ?: ""
        if (mime.contains("svg")) return@LaunchedEffect
        val size = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { ins ->
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(ins, null, opts)
                    IntSize(opts.outWidth, opts.outHeight).takeIf { opts.outWidth > 0 && opts.outHeight > 0 }
                }
            }.getOrNull()
        }
        imageSize = size
    }

    LaunchedEffect(processedImageBytes) {
        processedImageBytes?.let { bytes ->
            val size = withContext(Dispatchers.IO) {
                runCatching {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                    IntSize(opts.outWidth, opts.outHeight).takeIf { opts.outWidth > 0 && opts.outHeight > 0 }
                }.getOrNull()
            }
            imageSize = size
            cropRect = CropRect.FULL
        }
    }

    suspend fun loadCurrentImageBytes(): ByteArray? {
        val uri = selectedImageUri ?: return null
        processedImageBytes?.let { return it }
        return withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }
    }

    fun bytesToPng(bytes: ByteArray): ByteArray {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, out)
        return out.toByteArray()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
        ) {
            // Header – wie Filter Modal
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF02060F).copy(alpha = 0.85f))
                    .padding(16.dp, 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Upload, contentDescription = null, tint = EazColors.Orange, modifier = Modifier.height(20.dp))
                    Text(
                        translationStore.t("creator.upload_modal.title", "Design hochladen"),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color(0xFFE5E7EB)
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .clickable { onDismiss() }
                        .padding(6.dp)
                ) {
                    Text("×", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF9CA3AF))
                }
            }

            // Body
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // Preview-Stack wie Web: gestrichelte orange Linie oben, grau unten
                val density = LocalDensity.current
                val strokeWidthPx = with(density) { 2.dp.toPx() }
                val cornerRadiusPx = with(density) { 12.dp.toPx() }
                val dashPathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                val dashedStroke = Stroke(width = strokeWidthPx, pathEffect = dashPathEffect)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    // Preview – mehr Padding (24dp), gestrichelte orange Linie, oben abgerundet
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .background(Color(0xFF1A1F2E))
                            .drawWithCache {
                                onDrawBehind {
                                    val path = Path().apply {
                                        addRoundRect(
                                            RoundRect(
                                                rect = androidx.compose.ui.geometry.Rect(Offset.Zero, Size(size.width, size.height)),
                                                topLeft = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                                                topRight = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                                                bottomLeft = CornerRadius.Zero,
                                                bottomRight = CornerRadius.Zero
                                            )
                                        )
                                    }
                                    drawPath(path, EazColors.Orange, style = dashedStroke)
                                }
                            }
                            .padding(24.dp)
                            .then(
                                if (selectedImageUri == null) Modifier.clickable(onClick = onSelectImage)
                                else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { containerSize = it },
                        contentAlignment = Alignment.Center
                    ) {
                    if (selectedImageUri == null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(48.dp), tint = Color(0xFF6B7280))
                            Text(
                                translationStore.t("creator.upload_modal.select_new_image", "Neues Bild auswählen"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFE5E7EB)
                            )
                        }
                    } else {
                        val displayModel = if (processedImageBytes != null) {
                            ImageRequest.Builder(context).data(processedImageBytes).build()
                        } else {
                            ImageRequest.Builder(context).data(selectedImageUri).build()
                        }
                        AsyncImage(
                            model = displayModel,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                        val imgSize = imageSize
                        val cSize = containerSize
                        val imageDisplayRect = if (imgSize != null && cSize.width > 0 && cSize.height > 0) {
                            val scale = minOf(cSize.width.toFloat() / imgSize.width, cSize.height.toFloat() / imgSize.height)
                            val dw = imgSize.width * scale
                            val dh = imgSize.height * scale
                            val left = (cSize.width - dw) / 2
                            val top = (cSize.height - dh) / 2
                            Rect(left, top, left + dw, top + dh)
                        } else null
                        if (imageDisplayRect != null) {
                            CropFrameOverlay(
                                imageDisplayRect = imageDisplayRect,
                                cropRect = cropRect,
                                onCropRectChange = { cropRect = it }
                            )
                        }
                    }
                }
                    IconButton(
                        onClick = onRemoveImage,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color(0xFFDC2626).copy(alpha = 0.9f), RoundedCornerShape(50))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                    // Action Bar (wie Web) – gestrichelte graue Linie, keine EAZ-Kosten
                    if (selectedImageUri != null && imageSize != null) {
                    actionError?.let { err ->
                        Text(
                            err,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFDC2626),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A))
                            .drawWithCache {
                                onDrawBehind {
                                    val path = Path().apply {
                                        addRoundRect(
                                            RoundRect(
                                                rect = androidx.compose.ui.geometry.Rect(Offset.Zero, Size(size.width, size.height)),
                                                topLeft = CornerRadius.Zero,
                                                topRight = CornerRadius.Zero,
                                                bottomLeft = CornerRadius(cornerRadiusPx, cornerRadiusPx),
                                                bottomRight = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                                            )
                                        )
                                    }
                                    drawPath(path, Color(0xFF4B5563), style = dashedStroke)
                                }
                            }
                            .padding(12.dp)
                            .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                actionError = null
                                scope.launch {
                                    removeBgProcessing = true
                                    try {
                                        val bytes = loadCurrentImageBytes() ?: return@launch
                                        val png = bytesToPng(bytes)
                                        val result = api.removeBackground(ownerId, png, "upload.png")
                                        processedImageBytes = result
                                    } catch (e: Exception) {
                                        actionError = e.message ?: "Remove background failed"
                                    } finally {
                                        removeBgProcessing = false
                                    }
                                }
                            },
                            enabled = !cropProcessing && !removeBgProcessing && !uploadInProgress,
                            modifier = Modifier.weight(1f),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1F2E)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (removeBgProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = EazColors.Orange, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.FilterCenterFocus, contentDescription = null, modifier = Modifier.size(18.dp), tint = EazColors.Orange)
                            }
                            Spacer(Modifier.size(6.dp))
                            Text(
                                translationStore.t("creator.upload_modal.remove_background", "Remove background"),
                                color = Color(0xFFE5E7EB),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Button(
                            onClick = {
                                actionError = null
                                scope.launch {
                                    cropProcessing = true
                                    try {
                                        val bytes = loadCurrentImageBytes() ?: return@launch
                                        val png = bytesToPng(bytes)
                                        val result = api.cropImage(ownerId, png, "upload.png")
                                        processedImageBytes = result
                                    } catch (e: Exception) {
                                        actionError = e.message ?: "Crop failed"
                                    } finally {
                                        cropProcessing = false
                                    }
                                }
                            },
                            enabled = !cropProcessing && !removeBgProcessing && !uploadInProgress,
                            modifier = Modifier.weight(1f),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1F2E)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (cropProcessing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = EazColors.Orange, strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp), tint = EazColors.Orange)
                            }
                            Spacer(Modifier.size(6.dp))
                            Text(
                                translationStore.t("creator.upload_modal.crop_image", "Crop image"),
                                color = Color(0xFFE5E7EB),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Creator – klickbar öffnet Auswahl-Modal, schmaler
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        translationStore.t("creator.upload_modal.select_creator", "Creator"),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    if (creatorNames.isEmpty()) {
                        Text(
                            translationStore.t("creator.upload_modal.loading_creator", "Loading…"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF9CA3AF)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .clickable { creatorSelectModalVisible = true }
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    selectedCreator ?: translationStore.t("creator.upload_modal.select_creator", "Creator"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFE5E7EB)
                                )
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF9CA3AF))
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Sichtbarkeit – Switch rechts neben Public mit Padding, Info-Text darunter
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        translationStore.t("creator.common.visibility", "Visibility"),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Text(
                            if (visibilityPublic) translationStore.t("creator.common.visibility_public", "Public")
                            else translationStore.t("creator.common.visibility_private", "Private"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFE5E7EB)
                        )
                        Spacer(Modifier.size(12.dp))
                        Switch(
                            checked = visibilityPublic,
                            onCheckedChange = { visibilityPublic = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = EazColors.Orange)
                        )
                    }
                    Text(
                        if (visibilityPublic)
                            translationStore.t("creator.upload_modal.visibility_public_info", "Design visible to everyone. Published to all products and marketplaces. Can be changed to Private later.")
                        else
                            translationStore.t("creator.upload_modal.visibility_private_info", "Design visible only to you. No products will be published. Can be manually published or changed to Public later."),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF9CA3AF),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Footer – Cancel schmaler, Upload mehr Fläche
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF02060F).copy(alpha = 0.9f))
                    .padding(16.dp, 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(0.4f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(translationStore.t("creator.common.cancel", "Abbrechen"), color = Color.White)
                }
                Button(
                    onClick = {
                        scope.launch {
                            val bytes = loadCurrentImageBytes() ?: return@launch
                            val rect = if (cropRect == CropRect.FULL) null else cropRect
                            val finalBytes = if (rect != null) {
                                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@launch
                                val r = rect.toPixelRect(bmp.width, bmp.height)
                                if (r.width() > 0 && r.height() > 0) {
                                    val cropped = android.graphics.Bitmap.createBitmap(bmp, r.left, r.top, r.width(), r.height())
                                    val out = java.io.ByteArrayOutputStream()
                                    cropped.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, out)
                                    cropped.recycle()
                                    out.toByteArray()
                                } else bytes
                            } else bytes
                            val mime = if (selectedImageUri != null) {
                                context.contentResolver.getType(selectedImageUri!!) ?: "image/png"
                            } else "image/png"
                            val effectiveMime = if (processedImageBytes != null || rect != null) "image/png" else mime
                            onUpload(
                                selectedCreator?.takeIf { it.isNotBlank() },
                                if (visibilityPublic) "public" else "private",
                                finalBytes,
                                effectiveMime
                            )
                        }
                    },
                    enabled = selectedImageUri != null && !uploadInProgress && !cropProcessing && !removeBgProcessing,
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = EazColors.Orange),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (uploadInProgress) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(translationStore.t("creator.upload_modal.upload_button", "Upload Design"), color = Color.White)
                            Spacer(Modifier.size(6.dp))
                            Text("0.1 EAZ", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.9f), maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    // Creator-Auswahl-Modal (wie Filter Modal)
    if (creatorSelectModalVisible && creatorNames.isNotEmpty()) {
        CreatorSelectModal(
            options = creatorNames,
            selected = selectedCreator,
            onSelect = { selectedCreator = it; creatorSelectModalVisible = false },
            onDismiss = { creatorSelectModalVisible = false },
            translationStore = translationStore
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatorSelectModal(
    options: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    translationStore: TranslationStore
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val config = LocalConfiguration.current
    val maxHeight = (config.screenHeightDp * 0.5f).dp

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F172A),
        dragHandle = null
    ) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF02060F).copy(alpha = 0.85f))
                    .padding(16.dp, 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    translationStore.t("creator.upload_modal.select_creator", "Creator auswählen"),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFFE5E7EB)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                        .clickable { onDismiss() }
                        .padding(6.dp)
                ) {
                    Text("×", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF9CA3AF))
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                options.forEach { name ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (name == selected) EazColors.Orange.copy(alpha = 0.25f)
                                else Color.White.copy(alpha = 0.08f)
                            )
                            .border(
                                if (name == selected) 1.dp else 0.dp,
                                if (name == selected) EazColors.Orange else Color.Transparent,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelect(name) }
                            .padding(14.dp)
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}
