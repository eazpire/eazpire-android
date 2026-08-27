package com.eazpire.creator.ui.designrequest

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.UniversalSearchApi
import com.eazpire.creator.i18n.LocalTranslationStore
import com.eazpire.creator.ui.modal.EazBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Base64

@Composable
fun ShopDesignRequestCta(
    query: String,
    modifier: Modifier = Modifier,
    onRequest: (String) -> Unit = {
        DesignRequestUiTrigger.openSheet(DesignRequestOpen(query = it, source = "search"))
    },
    onGenerate: () -> Unit = { DesignRequestUiTrigger.openGenerator() },
) {
    val store = LocalTranslationStore.current
    val cta = store?.t(
        "eaz.design_request.cta",
        "Not the right match? Send us a design request or create your own with the generator."
    ) ?: "Not the right match? Send us a design request or create your own with the generator."
    val requestLabel = store?.t("eaz.design_request.send", "Design request") ?: "Design request"
    val generatorLabel = store?.t("eaz.design_request.generator", "Generator") ?: "Generator"
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x1F7C3AED))
            .border(1.dp, Color(0x477C3AED), RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(cta, color = EazColors.TextPrimary, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onRequest(query) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
            ) { Text(requestLabel) }
            OutlinedButton(onClick = onGenerate) { Text(generatorLabel, color = EazColors.TextPrimary) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignRequestSheet(
    spec: DesignRequestOpen,
    ownerId: String?,
    api: CreatorApi,
    onDismiss: () -> Unit,
    onRequireLogin: () -> Unit,
) {
    val store = LocalTranslationStore.current
    fun t(key: String, fallback: String) = store?.t(key, fallback) ?: fallback
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember(spec) { mutableStateOf(spec.query) }
    var imageUrl by remember(spec) { mutableStateOf(spec.imageUrl.orEmpty()) }
    var imageBase64 by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf("designs") }
    var loading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var products by remember { mutableStateOf<List<UniversalSearchApi.ProductHit>>(emptyList()) }
    var designs by remember { mutableStateOf<List<UniversalSearchApi.DesignHit>>(emptyList()) }
    val universal = remember(api) { UniversalSearchApi(api) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }
                if (bytes != null) {
                    imageBase64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    imageUrl = uri.toString()
                }
            }
        }
    }

    fun preview() {
        val q = text.trim()
        if (q.length < 2) {
            products = emptyList()
            designs = emptyList()
            return
        }
        scope.launch {
            loading = true
            error = null
            val res = runCatching {
                universal.search(query = q, mode = "both", phase = "results", limit = 12)
            }.getOrNull()
            products = res?.products.orEmpty()
            designs = res?.designs.orEmpty()
            loading = false
        }
    }

    fun send() {
        val q = text.trim()
        if (q.isBlank() && imageUrl.isBlank() && imageBase64.isNullOrBlank()) {
            error = t("eaz.design_request.need_input", "Add text or an image.")
            return
        }
        if (ownerId.isNullOrBlank()) {
            DesignRequestPendingStore.save(
                context,
                spec.copy(query = q, imageUrl = imageUrl.takeIf { it.isNotBlank() })
            )
            onRequireLogin()
            return
        }
        scope.launch {
            sending = true
            error = null
            val body = mutableMapOf<String, Any?>(
                "query_text" to q,
                "source" to spec.source,
                "confirm" to true,
                "owner_id" to ownerId,
                "logged_in_customer_id" to ownerId,
            )
            spec.parentId?.let { body["parent_id"] = it }
            if (!imageBase64.isNullOrBlank()) {
                body["image_base64"] = imageBase64
                body["image_mime"] = "image/png"
            } else if (imageUrl.isNotBlank()) {
                body["image_url"] = imageUrl
            }
            val res = withContext(Dispatchers.IO) { api.createDesignRequest(body) }
            sending = false
            if (res.optInt("status", if (res.optBoolean("ok")) 200 else 400) == 429 ||
                res.optString("error") == "shop_studio_daily_limit"
            ) {
                error = t("eaz.design_request.limit", "Daily generate limit reached. Try again tomorrow.")
                return@launch
            }
            if (!res.optBoolean("ok", false)) {
                error = res.optString("error", t("eaz.design_request.need_input", "Could not send."))
                return@launch
            }
            onDismiss()
        }
    }

    EazBottomSheet(onDismissRequest = onDismiss, fullscreen = false) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t("eaz.design_request.title", "Design request"),
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = EazColors.TextPrimary
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = t("eaz.design_request.remove_image", "Close"))
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(t("eaz.design_request.text_label", "Describe the design you want")) },
                placeholder = { Text(t("eaz.design_request.text_placeholder", "e.g. a red dragon on a black hoodie")) },
                minLines = 3
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { picker.launch("image/*") }) {
                Text(t("eaz.design_request.add_image", "Add an image"))
            }
            if (imageUrl.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    TextButton(onClick = { imageUrl = ""; imageBase64 = null }) {
                        Text(t("eaz.design_request.remove_image", "Remove"))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = { preview() }, enabled = !loading) {
                Text(t("eaz.design_request.preview", "Show matches"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    t("eaz.design_request.tab_designs", "Designs"),
                    modifier = Modifier.clickable { tab = "designs" },
                    color = if (tab == "designs") Color(0xFF7C3AED) else EazColors.TextSecondary,
                    fontWeight = if (tab == "designs") FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    t("eaz.design_request.tab_products", "Products"),
                    modifier = Modifier.clickable { tab = "products" },
                    color = if (tab == "products") Color(0xFF7C3AED) else EazColors.TextSecondary,
                    fontWeight = if (tab == "products") FontWeight.SemiBold else FontWeight.Normal
                )
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.padding(12.dp).size(22.dp), color = EazColors.Orange)
            }
            val previewItems = if (tab == "products") {
                products.map { it.image to it.title }
            } else {
                designs.map { it.imageUrl to it.title }
            }
            if (previewItems.isEmpty() && !loading) {
                Text(
                    t("eaz.design_request.preview_empty", "No close matches yet. Send the request to generate drafts."),
                    color = EazColors.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.height(180.dp).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    userScrollEnabled = false
                ) {
                    items(previewItems.take(9)) { (img, title) ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFF5F5F5))
                        ) {
                            if (!img.isNullOrBlank()) {
                                AsyncImage(
                                    model = img,
                                    contentDescription = title,
                                    modifier = Modifier.matchParentSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                }
            }
            if (!error.isNullOrBlank()) {
                Text(error!!, color = Color(0xFFDC2626), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.align(Alignment.End)) {
                OutlinedButton(onClick = onDismiss) { Text(t("eaz.reference_search.cancel", "Cancel")) }
                Button(
                    onClick = { send() },
                    enabled = !sending,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                ) {
                    if (sending) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text(t("eaz.design_request.send", "Send request"))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesignActionMenu(
    visible: Boolean,
    imageUrl: String,
    designId: String? = null,
    canOpenStudio: Boolean = false,
    canDelete: Boolean = false,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onUseOnProduct: ((String, String?) -> Unit)? = null,
) {
    if (!visible) return
    val store = LocalTranslationStore.current
    fun t(key: String, fallback: String) = store?.t(key, fallback) ?: fallback
    EazBottomSheet(onDismissRequest = onDismiss, fullscreen = false) {
        Column(Modifier.fillMaxWidth().padding(8.dp)) {
            TextButton(onClick = {
                onDismiss()
                DesignRequestUiTrigger.openGenerator()
            }, modifier = Modifier.fillMaxWidth()) {
                Text(t("eaz.design_request.remix", "Create remix"), modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = {
                onDismiss()
                DesignRequestUiTrigger.openSheet(
                    DesignRequestOpen(imageUrl = imageUrl, source = "choose")
                )
            }, modifier = Modifier.fillMaxWidth()) {
                Text(t("eaz.design_request.choose", "Choose for request"), modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = {
                onDismiss()
                onUseOnProduct?.invoke(imageUrl, designId)
            }, modifier = Modifier.fillMaxWidth()) {
                Text(t("eaz.design_request.use_product", "Use on product"), modifier = Modifier.fillMaxWidth())
            }
            if (canOpenStudio) {
                TextButton(onClick = {
                    onDismiss()
                    onUseOnProduct?.invoke(imageUrl, designId)
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(t("eaz.design_request.open_studio", "Open design studio"), modifier = Modifier.fillMaxWidth())
                }
            }
            if (canDelete && onDelete != null) {
                TextButton(onClick = {
                    onDismiss()
                    onDelete()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(t("eaz.design_request.delete", "Delete"), color = Color(0xFFDC2626), modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
