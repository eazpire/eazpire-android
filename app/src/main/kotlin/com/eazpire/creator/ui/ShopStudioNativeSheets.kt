package com.eazpire.creator.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.EazShopSheetColorScheme
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
internal fun ShopUploadNativeSheet(
    product: CatalogProduct,
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
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var creatorNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedCreator by remember { mutableStateOf("") }
    var creatorMenuExpanded by remember { mutableStateOf(false) }
    var visibilityPublic by remember { mutableStateOf(true) }

    LaunchedEffect(ownerId) {
        if (ownerId.isNullOrBlank()) return@LaunchedEffect
        try {
            val res = withContext(Dispatchers.IO) { api.getSettings(ownerId) }
            val arr = res.optJSONObject("settings")?.optJSONArray("creator_names")
            val list = mutableListOf<String>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    arr.optString(i, "").trim().takeIf { it.isNotEmpty() }?.let { list.add(it) }
                }
            }
            creatorNames = list
            if (selectedCreator.isEmpty() && list.isNotEmpty()) {
                selectedCreator = list.first()
            }
        } catch (_: Exception) {
        }
    }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            imageUri = uri
            error = null
        }
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
                    title = t("creator.upload_modal.title", "Upload Design"),
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
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .heightIn(max = 320.dp)
                                .clip(ShopImagePreviewShape)
                                .border(2.dp, Color(0xFFE5E7EB), ShopImagePreviewShape)
                                .background(Color(0xFFF9FAFB))
                                .clickable {
                                    pick.launch("image/*")
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (imageUri == null) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(16.dp)
                                ) {
                                    Text("📁", fontSize = 42.sp)
                                    Text(
                                        t("creator.upload_modal.dropzone_text", "Click here to select a design"),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        t("creator.upload_modal.dropzone_hint", "PNG, JPG, SVG up to 30MB"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            } else {
                                AsyncImage(
                                    model = imageUri,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                )
                            }
                        }
                        if (imageUri != null) {
                            Text(
                                t("creator.upload_modal.dropzone_text", "Click here to select a design"),
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .clickable { pick.launch("image/*") },
                                style = MaterialTheme.typography.labelMedium,
                                color = EazColors.Orange,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .clip(RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp))
                                .background(Color(0xFFF9FAFB))
                                .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { },
                                enabled = false,
                                modifier = Modifier.weight(1f),
                                shape = ShopSheetButtonShape
                            ) {
                                Text(
                                    t("creator.upload_modal.remove_background", "Remove background"),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2
                                )
                            }
                            OutlinedButton(
                                onClick = { },
                                enabled = false,
                                modifier = Modifier.weight(1f),
                                shape = ShopSheetButtonShape
                            ) {
                                Text(
                                    t("creator.upload_modal.crop_image", "Crop image"),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2
                                )
                            }
                        }
                    }

                    Divider(color = Color(0xFFE5E7EB))

                    if (creatorNames.isNotEmpty()) {
                        Text(
                            t("creator.upload_modal.confirm_creator", "Creator"),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { creatorMenuExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = ShopSheetButtonShape
                            ) {
                                Text(
                                    selectedCreator.ifBlank {
                                        t("creator.upload_modal.select_creator", "Select Creator")
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                            }
                            DropdownMenu(
                                expanded = creatorMenuExpanded,
                                onDismissRequest = { creatorMenuExpanded = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                creatorNames.forEach { name ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            selectedCreator = name
                                            creatorMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        t("creator.common.visibility", "Visibility"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        t("creator.upload_modal.visibility_hint", "With Public, your designs can be used as inspiration by other users."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            t("creator.upload_modal.visibility_private", "Private"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Switch(
                            checked = visibilityPublic,
                            onCheckedChange = { visibilityPublic = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = EazColors.Orange
                            )
                        )
                        Text(
                            t("creator.upload_modal.visibility_public", "Public"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = ShopSheetButtonShape
                        ) {
                            Text(translation("creator.common.cancel", "Cancel"))
                        }
                        Button(
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
                                                api.acceptShopCustomerDesignUpload(
                                                    oid,
                                                    product.productKey,
                                                    bytes,
                                                    mime,
                                                    name,
                                                    visibilityPublic = visibilityPublic,
                                                    creatorName = selectedCreator.takeIf { it.isNotBlank() }
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
                            },
                            enabled = !busy && imageUri != null,
                            modifier = Modifier.weight(1f),
                            shape = ShopSheetButtonShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EazColors.Orange,
                                contentColor = Color(0xFF111827)
                            )
                        ) {
                            if (busy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color(0xFF111827),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        t("creator.upload_modal.upload_button", "Upload Design"),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "0.1 EAZ",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
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
internal fun ShopSheetDragHandle() {
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
internal suspend fun pollShopDesignJob(api: CreatorApi, jobId: String) {
    val deadline = System.currentTimeMillis() + 180_000L
    while (coroutineContext.isActive && System.currentTimeMillis() < deadline) {
        val st = withContext(Dispatchers.IO) { api.pollJob(jobId) }
        if (st.optBoolean("done", false)) return
        delay(2000)
    }
}
