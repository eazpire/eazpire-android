package com.eazpire.creator.ui.creator

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.File

private val TARGET_PRODUCT_OPTIONS = listOf(
    "all" to "Anything",
    "unisex-softstyle-cotton-tee" to "Unisex Softstyle Cotton Tee"
)

private val DESIGN_TYPE_OPTIONS = listOf(
    "classic" to "Classic",
    "pattern" to "Pattern",
    "all-over" to "All-Over",
    "full-coverage" to "Full-Coverage",
    "panorama" to "Panorama"
)

data class RefImage(
    val dataUrl: String,
    val similarity: Float = 0.8f
)

@Composable
private fun DataUrlImage(
    dataUrl: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
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
fun CreatorGeneratorScreen(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    onOpenEazyChat: () -> Unit = {},
    maxHeight: Dp = Dp.Infinity,
    modifier: Modifier = Modifier
) {
    val boundedHeight = if (maxHeight == Dp.Infinity) 4000.dp else maxHeight
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() ?: "" }

    var targetProduct by remember { mutableStateOf("all") }
    var designType by remember { mutableStateOf("classic") }
    var ratio by remember { mutableStateOf("portrait") }
    var contentType by remember { mutableStateOf("design-text") }
    var selectedStyles by remember { mutableStateOf<List<String>>(emptyList()) }
    var languageState by remember { mutableStateOf(GenLanguageState()) }
    var colorState by remember { mutableStateOf(GenColorState()) }
    var prompt by remember { mutableStateOf("") }
    var selectedImages by remember { mutableStateOf<List<RefImage>>(emptyList()) }
    var suggestLoading by remember { mutableStateOf(false) }
    var showTargetProductModal by remember { mutableStateOf(false) }
    var showDesignTypeModal by remember { mutableStateOf(false) }
    var showRefSourceModal by remember { mutableStateOf(false) }
    var showOptionsModal by remember { mutableStateOf(false) }
    var showInspirationModal by remember { mutableStateOf(false) }
    var showMyDesignsModal by remember { mutableStateOf(false) }
    var showCanvasModal by remember { mutableStateOf(false) }
    var showCanvasEditModal by remember { mutableStateOf(false) }
    var canvasEditIndex by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var lastCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            lastCameraUri?.let { uri ->
                scope.launch {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            val bytes = stream.readBytes()
                            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                            val dataUrl = "data:image/jpeg;base64,$base64"
                            selectedImages = selectedImages + RefImage(dataUrl)
                        }
                    } catch (_: Exception) {}
                    lastCameraUri = null
                }
            }
        }
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
            lastCameraUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            lastCameraUri?.let { cameraLauncher.launch(it) }
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
                        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        val dataUrl = "data:$mime;base64,$base64"
                        selectedImages = selectedImages + RefImage(dataUrl)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun onSuggest() {
        scope.launch {
            suggestLoading = true
            try {
                val resp = api.suggestPrompt()
                if (resp.optBoolean("ok", false)) {
                    val suggested = resp.optString("suggestedPrompt", "")
                    if (suggested.isNotBlank()) prompt = suggested
                }
            } catch (_: Exception) {}
            suggestLoading = false
        }
    }

    fun removeImage(index: Int) {
        selectedImages = selectedImages.toMutableList().apply { removeAt(index) }
    }

    GenRefSourceModal(
        visible = showRefSourceModal,
        onDismiss = { showRefSourceModal = false },
        translationStore = translationStore,
        onDevice = {
            showRefSourceModal = false
            imagePicker.launch("image/*")
        },
        onCamera = {
            showRefSourceModal = false
            val hasPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                lastCameraUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                cameraLauncher.launch(lastCameraUri!!)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        },
        onInspirations = {
            showRefSourceModal = false
            showInspirationModal = true
        },
        onMyDesigns = {
            showRefSourceModal = false
            showMyDesignsModal = true
        },
        onCanvas = {
            showRefSourceModal = false
            showCanvasModal = true
        }
    )

    GenInspirationModal(
        visible = showInspirationModal,
        onDismiss = { showInspirationModal = false },
        api = api,
        translationStore = translationStore,
        onSelect = { imageUrl ->
            scope.launch {
                try {
                    val fullUrl = when {
                        imageUrl.startsWith("http") -> imageUrl
                        imageUrl.startsWith("//") -> "https:$imageUrl"
                        else -> "https://www.eazpire.com${if (imageUrl.startsWith("/")) imageUrl else "/$imageUrl"}"
                    }
                    val bytes = withContext(Dispatchers.IO) {
                        java.net.URL(fullUrl).openStream().readBytes()
                    }
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val mime = when {
                        imageUrl.contains(".png") -> "image/png"
                        else -> "image/jpeg"
                    }
                    selectedImages = selectedImages + RefImage("data:$mime;base64,$base64")
                    showInspirationModal = false
                } catch (_: Exception) {}
            }
        }
    )

    GenMyDesignsModal(
        visible = showMyDesignsModal,
        onDismiss = { showMyDesignsModal = false },
        api = api,
        ownerId = ownerId,
        translationStore = translationStore,
        onSelect = { imageUrl ->
            scope.launch {
                try {
                    val fullUrl = when {
                        imageUrl.startsWith("http") -> imageUrl
                        imageUrl.startsWith("//") -> "https:$imageUrl"
                        else -> "https://www.eazpire.com${if (imageUrl.startsWith("/")) imageUrl else "/$imageUrl"}"
                    }
                    val bytes = withContext(Dispatchers.IO) {
                        java.net.URL(fullUrl).openStream().readBytes()
                    }
                    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val mime = when {
                        imageUrl.contains(".png") -> "image/png"
                        else -> "image/jpeg"
                    }
                    selectedImages = selectedImages + RefImage("data:$mime;base64,$base64")
                    showMyDesignsModal = false
                } catch (_: Exception) {}
            }
        }
    )

    GenCanvasModal(
        visible = showCanvasModal,
        onDismiss = { showCanvasModal = false },
        translationStore = translationStore,
        onConfirm = { dataUrl ->
            selectedImages = selectedImages + RefImage(dataUrl)
            showCanvasModal = false
        }
    )

    GenCanvasEditModal(
        visible = showCanvasEditModal,
        backgroundImageDataUrl = selectedImages.getOrNull(canvasEditIndex)?.dataUrl,
        onDismiss = { showCanvasEditModal = false },
        translationStore = translationStore,
        onConfirm = { dataUrl ->
            if (canvasEditIndex in selectedImages.indices) {
                scope.launch {
                    try {
                        val base64 = dataUrl.substringAfter(",", "")
                        if (base64.isNotBlank()) {
                            val bytes = Base64.decode(base64, Base64.DEFAULT)
                            val file = File(context.cacheDir, "ref_edit_${canvasEditIndex}_${System.currentTimeMillis()}.png")
                            file.writeBytes(bytes)
                        }
                    } catch (_: Exception) {}
                }
                selectedImages = selectedImages.toMutableList().apply {
                    set(canvasEditIndex, RefImage(dataUrl))
                }
            }
            showCanvasEditModal = false
        }
    )

    GenTargetProductModal(
        visible = showTargetProductModal,
        currentValue = targetProduct,
        onDismiss = { showTargetProductModal = false },
        onSelect = { targetProduct = it },
        translationStore = translationStore
    )

    GenDesignTypeModal(
        visible = showDesignTypeModal,
        currentValue = designType,
        onDismiss = { showDesignTypeModal = false },
        onSelect = { designType = it },
        translationStore = translationStore
    )

    GenOptionsModal(
        visible = showOptionsModal,
        ratio = ratio,
        contentType = contentType,
        selectedStyles = selectedStyles,
        languageState = languageState,
        colorState = colorState,
        onDismiss = { showOptionsModal = false },
        onApply = { showOptionsModal = false },
        onRatioChange = { ratio = it },
        onContentTypeChange = { contentType = it },
        onStylesChange = { selectedStyles = it },
        onLanguageChange = { languageState = it },
        onColorStateChange = { colorState = it },
        api = api,
        translationStore = translationStore
    )

    errorMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Error") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("OK") } }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .heightIn(max = boundedHeight)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GenPill(
                modifier = Modifier.weight(1f),
                label = translationStore.t("creator.generator.target_product", "Target product"),
                value = TARGET_PRODUCT_OPTIONS.find { it.first == targetProduct }?.second ?: "Anything",
                onClick = { showTargetProductModal = true }
            )
            GenPill(
                modifier = Modifier.weight(1f),
                label = translationStore.t("creator.generator.design_type", "Design type"),
                value = DESIGN_TYPE_OPTIONS.find { it.first == designType }?.second ?: "Classic",
                onClick = { showDesignTypeModal = true }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        GenUploadCard(
            label = translationStore.t("creator.generator.upload", "Upload"),
            optionalLabel = translationStore.t("creator.generator.optional", "Optional"),
            onClick = { showRefSourceModal = true }
        )

        GenSelectedImagesBar(
            selectedImages = selectedImages,
            translationStore = translationStore,
            onRemove = { removeImage(it) },
            onDraw = { i ->
                canvasEditIndex = i
                showCanvasEditModal = true
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        GenCard(
            title = translationStore.t("creator.generator.prompt", "Prompt"),
            actions = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = { onSuggest() },
                        enabled = !suggestLoading
                    ) {
                        if (suggestLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Lightbulb, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(translationStore.t("creator.generator.suggest", "Suggest"))
                    }
                    TextButton(onClick = { prompt = "" }) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(translationStore.t("creator.common.clear", "Clear"))
                    }
                }
            }
        ) {
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        translationStore.t(
                            "creator.generator.prompt_placeholder",
                            "Describe your design or upload an image – both optional"
                        ),
                        color = Color.White.copy(alpha = 0.35f)
                    )
                },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = EazColors.Orange.copy(alpha = 0.5f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                    cursorColor = EazColors.Orange,
                    focusedContainerColor = Color.Black.copy(alpha = 0.25f),
                    unfocusedContainerColor = Color.Black.copy(alpha = 0.25f)
                ),
                minLines = 4
            )
            TextButton(onClick = { showOptionsModal = true }) {
                Text(
                    translationStore.t("creator.generator.more_options", "More options"),
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun GenSelectedImagesBar(
    selectedImages: List<RefImage>,
    translationStore: TranslationStore,
    onRemove: (Int) -> Unit,
    onDraw: (Int) -> Unit
) {
    if (selectedImages.isEmpty()) return
    Spacer(modifier = Modifier.height(16.dp))
    GenCard(
        title = translationStore.t("creator.generator.reference_images", "Reference images"),
        count = selectedImages.size
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            selectedImages.forEachIndexed { i, img ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.3f))
                    ) {
                        DataUrlImage(
                            dataUrl = img.dataUrl,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(EazColors.Orange.copy(alpha = 0.9f))
                                .clickable { onDraw(i) }
                                .padding(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Brush,
                                contentDescription = translationStore.t("creator.generator.draw", "Draw"),
                                tint = Color(0xFF0B1220),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.7f))
                                .clickable { onRemove(i) }
                                .padding(5.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = translationStore.t("creator.common.remove", "Remove"),
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = ('A' + i).toString(),
                        color = EazColors.Orange,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GenPill(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x55232334))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp, 14.dp)
    ) {
        Column {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.5f)
            )
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterEnd),
            tint = Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun GenUploadCard(
    label: String,
    optionalLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = optionalLabel,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.45f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black.copy(alpha = 0.2f))
                .border(2.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = EazColors.Orange
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = label,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun GenCard(
    title: String,
    count: Int? = null,
    actions: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xCC232634))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = EazColors.Orange
            )
            if (actions != null) actions()
            else if (count != null) Text("$count", color = Color.White.copy(alpha = 0.8f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}
