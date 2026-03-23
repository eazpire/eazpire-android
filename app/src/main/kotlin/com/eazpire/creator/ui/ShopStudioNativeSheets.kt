package com.eazpire.creator.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.EazShopSheetColorScheme
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private val ShopSheetButtonShape = RoundedCornerShape(12.dp)
private val ShopImagePreviewShape = RoundedCornerShape(12.dp)

/** Light shop sheet theme: [EazShopSheetColorScheme] (brand orange + neutrals from [Theme.kt]). */
@Composable
internal fun ShopLightSheetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EazShopSheetColorScheme,
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShopGenerateNativeSheet(
    product: CatalogProduct,
    api: CreatorApi,
    ownerId: String?,
    translation: (String, String) -> String,
    onDismiss: () -> Unit,
    onRequireLogin: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var prompt by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BackHandler(onBack = onDismiss)

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
                    .verticalScroll(rememberScrollState())
            ) {
                StudioHeader(
                    title = translation(
                        "creator.shop_create_product.studio_generate_title",
                        "Generate design"
                    ),
                    subtitle = product.title,
                    onBack = onDismiss,
                    translation = translation
                )
                if (ownerId.isNullOrBlank()) {
                    Text(
                        translation(
                            "creator.shop_create_product.login_required",
                            "Sign in to create a design."
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                    ShopSheetPrimaryButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(translation("creator.common.close", "Close"))
                    }
                } else {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        label = {
                            Text(
                                translation(
                                    "creator.shop_create_product.prompt_label",
                                    "Describe your design"
                                )
                            )
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
                    ShopSheetPrimaryButton(
                        onClick = {
                            val p = prompt.trim()
                            if (p.isEmpty()) {
                                error = translation(
                                    "creator.shop_create_product.prompt_required",
                                    "Please enter a description."
                                )
                            } else {
                                error = null
                                scope.launch {
                                    busy = true
                                    try {
                                        val oid = ownerId ?: return@launch
                                        val res = withContext(Dispatchers.IO) {
                                            api.acceptShopCustomerDesignGenerate(oid, product.productKey, p)
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
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                translation("creator.shop_create_product.start_generate", "Start"),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ShopUploadNativeSheet(
    product: CatalogProduct,
    api: CreatorApi,
    ownerId: String?,
    translation: (String, String) -> String,
    onDismiss: () -> Unit,
    onRequireLogin: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        imageUri = uri
        error = null
    }

    BackHandler(onBack = onDismiss)

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
                StudioHeader(
                    title = translation(
                        "creator.shop_create_product.studio_upload_title",
                        "Upload design"
                    ),
                    subtitle = product.title,
                    onBack = onDismiss,
                    translation = translation
                )
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
                    ShopSheetOutlinedButton(
                        onClick = { pick.launch("image/*") },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            translation("creator.shop_create_product.pick_image", "Choose image"),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    imageUri?.let { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .clip(ShopImagePreviewShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    ShopSheetPrimaryButton(
                        onClick = {
                            val uri = imageUri
                            if (uri == null) {
                                error = translation(
                                    "creator.shop_create_product.image_required",
                                    "Please choose an image."
                                )
                            } else {
                                error = null
                                scope.launch {
                                    busy = true
                                    try {
                                        val oid = ownerId ?: return@launch
                                        val bytes = withContext(Dispatchers.IO) {
                                            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                        } ?: run {
                                            error = translation(
                                                "creator.shop_create_product.read_image_failed",
                                                "Could not read the image."
                                            )
                                            return@launch
                                        }
                                        val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                                        val name = uri.lastPathSegment?.takeIf { it.contains('.') } ?: "upload.jpg"
                                        val res = withContext(Dispatchers.IO) {
                                            api.acceptShopCustomerDesignUpload(oid, product.productKey, bytes, mime, name)
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
                        },
                        enabled = !busy && imageUri != null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                translation("creator.shop_create_product.start_upload", "Upload"),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudioHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    translation: (String, String) -> String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = translation("creator.common.back", "Back"),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}

@Composable
internal fun ShopSheetPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ShopSheetButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.38f)
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 2.dp,
            disabledElevation = 0.dp
        ),
        content = content
    )
}

@Composable
internal fun ShopSheetOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = ShopSheetButtonShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        content = content
    )
}

@Composable
private fun ShopSheetDragHandle() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(0x33000000))
        )
    }
}

/** Poll until job done or timeout (~3 min). */
private suspend fun pollShopDesignJob(api: CreatorApi, jobId: String) {
    val deadline = System.currentTimeMillis() + 180_000L
    while (coroutineContext.isActive && System.currentTimeMillis() < deadline) {
        val st = withContext(Dispatchers.IO) { api.pollJob(jobId) }
        if (st.optBoolean("done", false)) return
        delay(2000)
    }
}
