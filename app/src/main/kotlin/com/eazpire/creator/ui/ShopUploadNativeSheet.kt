package com.eazpire.creator.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.request.ImageRequest
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.creator.CropFrameOverlay
import com.eazpire.creator.ui.creator.CropRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private val ShopUploadPreviewShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShopUploadNativeSheet(
    product: CatalogProduct,
    imageUri: Uri,
    api: CreatorApi,
    ownerId: String?,
    translationStore: TranslationStore,
    translation: (String, String) -> String,
    onDismiss: () -> Unit,
    onRequireLogin: () -> Unit
) {
    fun t(key: String, def: String) = translationStore.t(key, def)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    var processedBytes by remember { mutableStateOf<ByteArray?>(null) }
    var cropRect by remember { mutableStateOf(CropRect.FULL) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var imageSize by remember { mutableStateOf<IntSize?>(null) }
    var cropProcessing by remember { mutableStateOf(false) }
    var removeBgProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(imageUri) {
        processedBytes = null
        cropRect = CropRect.FULL
        imageSize = null
        val mime = context.contentResolver.getType(imageUri) ?: ""
        if (mime.contains("svg")) return@LaunchedEffect
        val size = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(imageUri)?.use { ins ->
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(ins, null, opts)
                    IntSize(opts.outWidth, opts.outHeight).takeIf { opts.outWidth > 0 && opts.outHeight > 0 }
                }
            }.getOrNull()
        }
        imageSize = size
    }

    LaunchedEffect(processedBytes) {
        processedBytes?.let { bytes ->
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
        processedBytes?.let { return it }
        return withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
        }
    }

    fun bytesToPng(bytes: ByteArray): ByteArray {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return bytes
        val out = ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, out)
        return out.toByteArray()
    }

    androidx.activity.compose.BackHandler(onBack = onDismiss)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        modifier = Modifier.fillMaxHeight(0.92f),
        dragHandle = { ShopSheetDragHandle() }
    ) {
        ShopLightSheetTheme {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            t("creator.upload_modal.title", "Upload Design"),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            product.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = translation("creator.common.close", "Close"),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (ownerId.isNullOrBlank()) {
                    Text(
                        translation(
                            "creator.shop_create_product.login_required",
                            "Sign in to create a design."
                        ),
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .heightIn(max = 320.dp)
                            .clip(ShopUploadPreviewShape)
                            .border(2.dp, Color(0xFFE5E7EB), ShopUploadPreviewShape)
                            .background(Color(0xFFF9FAFB))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { containerSize = it },
                            contentAlignment = Alignment.Center
                        ) {
                            val displayModel = if (processedBytes != null) {
                                ImageRequest.Builder(context).data(processedBytes).build()
                            } else {
                                ImageRequest.Builder(context).data(imageUri).build()
                            }
                            AsyncImage(
                                model = displayModel,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                            val imgSz = imageSize
                            val cSize = containerSize
                            val imageDisplayRect = if (imgSz != null && cSize.width > 0 && cSize.height > 0) {
                                val scale = minOf(
                                    cSize.width.toFloat() / imgSz.width,
                                    cSize.height.toFloat() / imgSz.height
                                )
                                val dw = imgSz.width * scale
                                val dh = imgSz.height * scale
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

                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF9FAFB))
                            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                error = null
                                scope.launch {
                                    removeBgProcessing = true
                                    try {
                                        val oid = ownerId ?: return@launch
                                        val bytes = loadCurrentImageBytes() ?: return@launch
                                        val png = bytesToPng(bytes)
                                        val result = api.removeBackground(oid, png, "upload.png")
                                        processedBytes = result
                                    } catch (e: Exception) {
                                        error = e.message ?: "Remove background failed"
                                    } finally {
                                        removeBgProcessing = false
                                    }
                                }
                            },
                            enabled = !cropProcessing && !removeBgProcessing && !busy,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3F4F6)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (removeBgProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = EazColors.Orange,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.FilterCenterFocus, contentDescription = null, tint = EazColors.Orange)
                            }
                            Spacer(Modifier.size(6.dp))
                            Text(
                                t("creator.upload_modal.remove_background", "Remove background"),
                                color = Color(0xFF111827),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Button(
                            onClick = {
                                error = null
                                scope.launch {
                                    cropProcessing = true
                                    try {
                                        val oid = ownerId ?: return@launch
                                        val bytes = loadCurrentImageBytes() ?: return@launch
                                        val png = bytesToPng(bytes)
                                        val result = api.cropImage(oid, png, "upload.png")
                                        processedBytes = result
                                    } catch (e: Exception) {
                                        error = e.message ?: "Crop failed"
                                    } finally {
                                        cropProcessing = false
                                    }
                                }
                            },
                            enabled = !cropProcessing && !removeBgProcessing && !busy,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3F4F6)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (cropProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = EazColors.Orange,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.ContentCut, contentDescription = null, tint = EazColors.Orange)
                            }
                            Spacer(Modifier.size(6.dp))
                            Text(
                                t("creator.upload_modal.crop_image", "Crop image"),
                                color = Color(0xFF111827),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ShopEazySpeechCluster(
                            bubbleText = t("creator.shop_create_product.upload_eazy_cta", "Upload design"),
                            enabled = !busy && !cropProcessing && !removeBgProcessing,
                            onStart = {
                                scope.launch {
                                    busy = true
                                    error = null
                                    try {
                                        val oid = ownerId ?: return@launch
                                        val bytes = loadCurrentImageBytes() ?: run {
                                            error = translation(
                                                "creator.shop_create_product.read_image_failed",
                                                "Could not read the image."
                                            )
                                            return@launch
                                        }
                                        val rect = if (cropRect == CropRect.FULL) null else cropRect
                                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                            ?: run {
                                                error = translation(
                                                    "creator.shop_create_product.read_image_failed",
                                                    "Could not read the image."
                                                )
                                                return@launch
                                            }
                                        val finalBytes = if (rect != null) {
                                            val r = rect.toPixelRect(bmp.width, bmp.height)
                                            if (r.width() > 0 && r.height() > 0) {
                                                val cropped = android.graphics.Bitmap.createBitmap(
                                                    bmp,
                                                    r.left,
                                                    r.top,
                                                    r.width(),
                                                    r.height()
                                                )
                                                val out = ByteArrayOutputStream()
                                                cropped.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, out)
                                                cropped.recycle()
                                                out.toByteArray()
                                            } else bytes
                                        } else bytes
                                        val mime =
                                            if (processedBytes != null || rect != null) "image/png" else (
                                                context.contentResolver.getType(imageUri)
                                                    ?: "image/jpeg"
                                                )
                                        val name =
                                            if (mime.contains("png")) "upload.png" else "upload.jpg"
                                        val res = withContext(Dispatchers.IO) {
                                            api.acceptShopCustomerDesignUpload(
                                                oid,
                                                product.productKey,
                                                finalBytes,
                                                mime,
                                                name,
                                                visibilityPublic = true,
                                                creatorName = null
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
                    if (busy) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = EazColors.Orange)
                        }
                    }
                }
            }
        }
    }
}
