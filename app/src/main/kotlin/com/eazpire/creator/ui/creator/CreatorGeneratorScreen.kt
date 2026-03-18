package com.eazpire.creator.ui.creator

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.launch
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

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
fun CreatorGeneratorScreen(
    tokenStore: SecureTokenStore,
    translationStore: TranslationStore,
    onOpenEazyChat: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }
    val ownerId = remember(tokenStore) { tokenStore.getOwnerId() ?: "" }
    val isLoggedIn = tokenStore.isLoggedIn()

    var targetProduct by remember { mutableStateOf("all") }
    var designType by remember { mutableStateOf("classic") }
    var prompt by remember { mutableStateOf("") }
    var selectedImages by remember { mutableStateOf<List<RefImage>>(emptyList()) }
    var suggestLoading by remember { mutableStateOf(false) }
    var generateLoading by remember { mutableStateOf(false) }
    var showTargetProductModal by remember { mutableStateOf(false) }
    var showDesignTypeModal by remember { mutableStateOf(false) }
    var showRefSourceModal by remember { mutableStateOf(false) }
    var showConfirmModal by remember { mutableStateOf(false) }
    var confirmBalance by remember { mutableStateOf("—") }
    var lastJobId by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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

    fun loadBalance() {
        if (ownerId.isBlank()) return
        scope.launch {
            try {
                val resp = api.getBalance(ownerId)
                if (resp.optBoolean("ok", false)) {
                    val bal = resp.optDouble("balance_eaz", 0.0)
                    confirmBalance = if (bal % 1 == 0.0) bal.toInt().toString() else "%.2f".format(bal)
                }
            } catch (_: Exception) {}
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

    fun buildPayload(): Map<String, Any?> {
        val refs = selectedImages.mapIndexed { i, img ->
            mapOf(
                "url" to img.dataUrl,
                "label" to ('A' + i).toString(),
                "similarity" to img.similarity
            )
        }
        val refsArray = JSONArray().apply { refs.forEach { put(JSONObject(it)) } }
        val primary = selectedImages.firstOrNull()
        return mapOf(
            "prompt" to prompt.trim(),
            "image_url" to (primary?.dataUrl ?: ""),
            "design_type" to designType,
            "target_product" to if (targetProduct == "all") "tshirt" else targetProduct,
            "ratio" to "portrait",
            "content_type" to "design-text",
            "styles" to JSONArray(),
            "design_colors" to JSONArray(),
            "background_colors" to JSONArray(),
            "background" to mapOf("mode" to "transparent"),
            "language" to mapOf("mode" to "as-design"),
            "reference_images" to refsArray,
            "owner_id" to ownerId
        )
    }

    fun onGenerate() {
        if (!prompt.isNotBlank() && selectedImages.isEmpty()) {
            errorMessage = translationStore.t("creator.generator.please_prompt_or_image", "Please enter a prompt or add a reference image.")
            return
        }
        if (!isLoggedIn || ownerId.isBlank()) {
            errorMessage = translationStore.t("eazy_chat.login_required_title", "Login required")
            return
        }
        showConfirmModal = true
        loadBalance()
    }

    fun doGenerate() {
        scope.launch {
            showConfirmModal = false
            generateLoading = true
            errorMessage = null
            try {
                val payload = buildPayload()
                val resp = api.submitGenerateJob(ownerId, payload)
                val jobId = resp.optString("jobId", "").takeIf { it.isNotBlank() }
                if (jobId != null) {
                    lastJobId = jobId
                    prompt = ""
                    selectedImages = emptyList()
                    targetProduct = "all"
                    designType = "classic"
                    onOpenEazyChat()
                } else {
                    errorMessage = resp.optString("message", resp.optString("error", "Unknown error"))
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: translationStore.t("chat.generic_error_retry_later", "Something went wrong. Please try again.")
            }
            generateLoading = false
        }
    }

    fun removeImage(index: Int) {
        selectedImages = selectedImages.toMutableList().apply { removeAt(index) }
    }

    if (showTargetProductModal) {
        AlertDialog(
            onDismissRequest = { showTargetProductModal = false },
            title = { Text(translationStore.t("creator.generator.target_product", "Target product")) },
            text = {
                Column {
                    TARGET_PRODUCT_OPTIONS.forEach { (value, label) ->
                        Text(
                            text = label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    targetProduct = value
                                    showTargetProductModal = false
                                }
                                .padding(12.dp),
                            color = if (targetProduct == value) EazColors.Orange else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTargetProductModal = false }) { Text("OK") } }
        )
    }

    if (showDesignTypeModal) {
        AlertDialog(
            onDismissRequest = { showDesignTypeModal = false },
            title = { Text(translationStore.t("creator.generator.design_type", "Design type")) },
            text = {
                Column {
                    DESIGN_TYPE_OPTIONS.forEach { (value, label) ->
                        Text(
                            text = label,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    designType = value
                                    showDesignTypeModal = false
                                }
                                .padding(12.dp),
                            color = if (designType == value) EazColors.Orange else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDesignTypeModal = false }) { Text("OK") } }
        )
    }

    if (showRefSourceModal) {
        AlertDialog(
            onDismissRequest = { showRefSourceModal = false },
            title = { Text(translationStore.t("creator.generator.select_reference", "Select reference image")) },
            text = {
                Column {
                    Text(
                        text = translationStore.t("creator.generator.source_device", "Device"),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showRefSourceModal = false
                                imagePicker.launch("image/*")
                            }
                            .padding(12.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showRefSourceModal = false }) { Text("OK") } }
        )
    }

    if (showConfirmModal) {
        val targetLabel = TARGET_PRODUCT_OPTIONS.find { it.first == targetProduct }?.second ?: "Anything"
        val designLabel = DESIGN_TYPE_OPTIONS.find { it.first == designType }?.second ?: "Classic"
        val promptTrunc = if (prompt.length > 80) prompt.take(77) + "…" else prompt.ifBlank { "—" }
        AlertDialog(
            onDismissRequest = { showConfirmModal = false },
            title = { Text(translationStore.t("creator.generator.confirm_title", "Generate design?")) },
            text = {
                Column {
                    Text("${translationStore.t("creator.generator.confirm_summary_target", "Target product")}: $targetLabel" )
                    Text("${translationStore.t("creator.generator.confirm_summary_design", "Design type")}: $designLabel")
                    Text("${translationStore.t("creator.generator.confirm_summary_prompt", "Prompt")}: $promptTrunc")
                    Text("${translationStore.t("creator.generator.confirm_summary_refs", "Reference images")}: ${selectedImages.size}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(translationStore.t("creator.generator.confirm_cost", "Cost: {{ cost }} EAZ").replace("{{ cost }}", "0.5"))
                    Text(translationStore.t("creator.generator.confirm_balance", "Available: {{ balance }} EAZ").replace("{{ balance }}", confirmBalance))
                }
            },
            confirmButton = {
                Button(onClick = { doGenerate() }) {
                    Text(translationStore.t("creator.generator.confirm_generate", "Generate"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmModal = false }) {
                    Text(translationStore.t("creator.common.cancel", "Cancel"))
                }
            }
        )
    }

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

        if (selectedImages.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            GenCard(
                title = translationStore.t("creator.generator.reference_images", "Reference images"),
                count = selectedImages.size
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    selectedImages.forEachIndexed { i, img ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                        ) {
                            AsyncImage(
                                model = img.dataUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Text(
                                text = ('A' + i).toString(),
                                color = EazColors.Orange,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(4.dp)
                            )
                            IconButton(
                                onClick = { removeImage(i) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = Color.White)
                            }
                        }
                    }
                }
            }
        }

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
            TextButton(onClick = { }) {
                Text(
                    translationStore.t("creator.generator.more_options", "More options"),
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = { onGenerate() },
                enabled = !generateLoading,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(48.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = EazColors.Orange,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (generateLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        translationStore.t("creator.generator.generate_btn", "Generate"),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.size(10.dp))
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text("0.5 EAZ", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
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
