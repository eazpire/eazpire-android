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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.eazpire.creator.R
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.creator.GenCanvasModal
import com.eazpire.creator.ui.creator.GenInspirationModal
import com.eazpire.creator.ui.creator.GenMyDesignsModal
import com.eazpire.creator.ui.creator.GenRefSourceModal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.util.Base64

private data class ShopRefSlot(
    val label: String,
    val url: String,
    val strength: Int = 80
)

/**
 * Shop Create Product — generate: parity with web design studio (light shell, Eazy CTA, up to 5 refs, no EAZ price).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShopDesignStudioGenerateSheet(
    product: CatalogProduct,
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

    var showRefSource by remember { mutableStateOf(false) }
    var showInsp by remember { mutableStateOf(false) }
    var showMyDesigns by remember { mutableStateOf(false) }
    var showCanvas by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val refRowScroll = rememberScrollState()

    val ready = prompt.trim().isNotEmpty() || refs.isNotEmpty()

    fun relabel(list: List<ShopRefSlot>): List<ShopRefSlot> {
        val letters = listOf("A", "B", "C", "D", "E")
        return list.mapIndexed { i, r -> r.copy(label = letters.getOrElse(i) { "?" }) }
    }

    fun addRef(url: String) {
        if (refs.size >= 5) return
        val letters = listOf("A", "B", "C", "D", "E")
        val label = letters[refs.size]
        refs = relabel(refs + ShopRefSlot(label, url, 80))
    }

    fun removeRef(index: Int) {
        refs = relabel(refs.filterIndexed { i, _ -> i != index })
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
        onConfirm = { dataUrl ->
            addRef(dataUrl)
            showCanvas = false
        }
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        modifier = Modifier.fillMaxHeight(0.95f),
        dragHandle = { ShopSheetDragHandle() }
    ) {
        ShopLightSheetTheme {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp)
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
                    Text(
                        translation("creator.generator.title", "Design Generator"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            Text(translation("creator.generator.suggest", "Suggest"), modifier = Modifier.padding(start = 4.dp))
                        }
                        ShopSheetOutlinedButton(
                            onClick = { prompt = "" },
                            enabled = !busy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(translation("creator.js.clear", "Clear"))
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Upload zone (optional refs)
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            refs.forEachIndexed { index, slot ->
                                ShopRefChip(
                                    label = slot.label,
                                    imageUrl = slot.url,
                                    onRemove = { removeRef(index) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
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
                    error?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (ready) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.End
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
                                    error = null
                                    scope.launch {
                                        busy = true
                                        try {
                                            val oid = ownerId ?: return@launch
                                            val refPayload = refs.map {
                                                CreatorApi.ShopReferenceImage(
                                                    url = it.url,
                                                    label = it.label,
                                                    strength = it.strength
                                                )
                                            }
                                            val res = withContext(Dispatchers.IO) {
                                                api.acceptShopCustomerDesignGenerate(oid, product.productKey, p, refPayload)
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

                    if (busy) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopEazySpeechCluster(
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

@Composable
private fun ShopRefChip(
    label: String,
    imageUrl: String,
    onRemove: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp)
    ) {
        Text(label, fontWeight = FontWeight.Bold, color = EazColors.Orange, modifier = Modifier.padding(4.dp))
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
        }
    }
}
