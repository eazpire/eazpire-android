package com.eazpire.creator.ui.account

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.util.DebugLog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * My Mockups Tab – native Android UI.
 * Web reference: theme/sections/my-mockups.liquid
 * Upload photo, select person type, generate mockups, try-on toggle, delete.
 */
data class CustomerMockup(
    val id: Long,
    val productKey: String,
    val productName: String,
    val mockupUrl: String,
    val useAsPreview: Boolean,
    val createdAt: String?
)

private val PERSON_TYPES = listOf(
    "woman" to "Woman",
    "man" to "Man",
    "teen_girl" to "Teen Girl",
    "teen_boy" to "Teen Boy"
)

@Composable
fun AccountMockupsTab(
    tokenStore: SecureTokenStore,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val jwt = remember { runCatching { tokenStore.getJwt() }.getOrNull() }
    val ownerId = remember { runCatching { tokenStore.getOwnerId() }.getOrNull() ?: "" }

    var mockups by remember { mutableStateOf<List<CustomerMockup>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPersonType by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    var generatingProgress by remember { mutableStateOf("") }
    var lightboxMockup by remember { mutableStateOf<CustomerMockup?>(null) }
    var deleteConfirmMockup by remember { mutableStateOf<CustomerMockup?>(null) }

    val api = remember(jwt) { com.eazpire.creator.api.CreatorApi(jwt = jwt) }
    val scope = rememberCoroutineScope()

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedPhotoUri = uri
    }

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) return@LaunchedEffect
        isLoading = true
        errorMessage = null
        try {
            val resp = api.listCustomerMockups(ownerId)
            if (resp.optBoolean("ok", false)) {
                val arr = resp.optJSONArray("mockups")
                mockups = parseMockupsFromJson(arr)
            } else {
                errorMessage = resp.optString("error", "Failed to load mockups")
            }
        } catch (e: Exception) {
            DebugLog.click("Mockups load error: ${e.message}")
            errorMessage = "Failed to load mockups"
        } finally {
            isLoading = false
        }
    }

    fun loadMockups() {
        scope.launch {
            if (ownerId.isBlank()) return@launch
            isLoading = true
            errorMessage = null
            try {
                val resp = api.listCustomerMockups(ownerId)
                if (resp.optBoolean("ok", false)) {
                    val arr = resp.optJSONArray("mockups")
                    mockups = parseMockupsFromJson(arr)
                } else {
                    errorMessage = resp.optString("error", "Failed to load mockups")
                }
            } catch (e: Exception) {
                DebugLog.click("Mockups load error: ${e.message}")
                errorMessage = "Failed to load mockups"
            } finally {
                isLoading = false
            }
        }
    }

    fun onGenerate() {
        if (ownerId.isBlank() || selectedPhotoUri == null || selectedPersonType == null) return
        isGenerating = true
        generatingProgress = "Starting..."
        scope.launch {
            try {
                val uri = selectedPhotoUri!!
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalArgumentException("Could not read photo")
                val contentType = context.contentResolver.getType(uri) ?: "image/jpeg"
                val resp = api.generateCustomerMockups(ownerId, bytes, contentType, selectedPersonType!!)
                if (resp.optBoolean("ok", false)) {
                    val jobId = resp.optString("job_id", "")
                    val total = resp.optInt("total_products", 4)
                    generatingProgress = "Generating (0/$total)..."
                    var done = false
                    while (!done) {
                        delay(4000)
                        val poll = api.pollJob(jobId)
                        val completed = poll.optInt("completed_products", 0)
                        generatingProgress = "Generating ($completed/$total)..."
                        done = poll.optBoolean("done", false)
                    }
                    selectedPhotoUri = null
                    selectedPersonType = null
                    loadMockups()
                } else {
                    errorMessage = resp.optString("error", "Generation failed")
                }
            } catch (e: Exception) {
                DebugLog.click("Generate mockups error: ${e.message}")
                errorMessage = e.message ?: "Generation failed"
            } finally {
                isGenerating = false
            }
        }
    }

    fun onTogglePreview(mockup: CustomerMockup) {
        scope.launch {
            try {
                val resp = api.toggleMockupPreview(ownerId, mockup.id, !mockup.useAsPreview)
                if (resp.optBoolean("ok", false)) {
                    mockups = mockups.map { if (it.id == mockup.id) it.copy(useAsPreview = !it.useAsPreview) else it }
                }
            } catch (_: Exception) { }
        }
    }

    fun onDelete(mockup: CustomerMockup) {
        deleteConfirmMockup = null
        lightboxMockup = null
        scope.launch {
            try {
                val resp = api.deleteCustomerMockup(ownerId, mockup.id)
                if (resp.optBoolean("ok", false)) {
                    mockups = mockups.filter { it.id != mockup.id }
                } else {
                    errorMessage = resp.optString("error", "Delete failed")
                }
            } catch (e: Exception) {
                errorMessage = e.message ?: "Delete failed"
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "My Mockups",
            style = MaterialTheme.typography.titleMedium,
            color = EazColors.TextPrimary
        )
        Text(
            text = "Upload a photo, choose person type, and generate product mockups.",
            style = MaterialTheme.typography.bodySmall,
            color = EazColors.TextSecondary
        )

        if (ownerId.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
                    .background(EazColors.TopbarBorder.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Please log in to use My Mockups",
                    style = MaterialTheme.typography.bodyMedium,
                    color = EazColors.TextSecondary
                )
            }
        } else if (isGenerating) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(16.dp))
                    Text(
                        text = generatingProgress,
                        style = MaterialTheme.typography.bodySmall,
                        color = EazColors.TextSecondary
                    )
                }
            }
        } else {
            // Upload area
            OutlinedButton(
                onClick = { photoPicker.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text(if (selectedPhotoUri != null) "Change photo" else "Choose photo")
            }

            if (selectedPhotoUri != null) {
                Text(
                    text = "Person type",
                    style = MaterialTheme.typography.labelMedium,
                    color = EazColors.TextPrimary
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PERSON_TYPES.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { (key, label) ->
                                val selected = selectedPersonType == key
                                OutlinedButton(
                                    onClick = { selectedPersonType = key },
                                    modifier = Modifier
                                        .weight(1f)
                                        .then(if (selected) Modifier.border(2.dp, EazColors.Orange, RoundedCornerShape(8.dp)) else Modifier)
                                ) {
                                    Text(label, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                Button(
                    onClick = { onGenerate() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Generate mockups")
                }
            }

            errorMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(24.dp))
                }
            } else if (mockups.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp)
                        .background(EazColors.TopbarBorder.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Image,
                            contentDescription = null,
                            modifier = Modifier.padding(bottom = 8.dp),
                            tint = EazColors.TextSecondary
                        )
                        Text(
                            text = "No mockups yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EazColors.TextSecondary
                        )
                        Text(
                            text = "Choose a photo and generate mockups above",
                            style = MaterialTheme.typography.bodySmall,
                            color = EazColors.TextSecondary
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    mockups.chunked(2).forEach { rowMockups ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowMockups.forEach { mockup ->
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clickable { lightboxMockup = mockup },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(12.dp),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                    ) {
                                        if (mockup.mockupUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = mockup.mockupUrl,
                                                contentDescription = mockup.productName,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(EazColors.TopbarBorder.copy(alpha = 0.3f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Image, contentDescription = null, tint = EazColors.TextSecondary)
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = mockup.productName.ifBlank { "Mockup" },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = EazColors.TextPrimary,
                                            maxLines = 2,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedButton(
                                            onClick = { onTogglePreview(mockup) },
                                            modifier = Modifier.padding(0.dp)
                                        ) {
                                            Text(
                                                if (mockup.useAsPreview) "Try-on ✓" else "Try-on",
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    }
                                    }
                                }
                            }
                            if (rowMockups.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    // Lightbox
    lightboxMockup?.let { mockup ->
        AlertDialog(
            onDismissRequest = { lightboxMockup = null },
            modifier = Modifier.fillMaxWidth(0.95f),
            title = { Text(mockup.productName.ifBlank { "Mockup" }) },
            text = {
                Column {
                    if (mockup.mockupUrl.isNotBlank()) {
                        AsyncImage(
                            model = mockup.mockupUrl,
                            contentDescription = mockup.productName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    TextButton(onClick = { deleteConfirmMockup = mockup; lightboxMockup = null }) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { lightboxMockup = null }) { Text("Close") }
            }
        )
    }

    deleteConfirmMockup?.let { mockup ->
        AlertDialog(
            onDismissRequest = { deleteConfirmMockup = null },
            title = { Text("Delete mockup?") },
            text = { Text("This mockup will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = { onDelete(mockup) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmMockup = null }) { Text("Cancel") }
            }
        )
    }
}

private fun parseMockupsFromJson(arr: org.json.JSONArray?): List<CustomerMockup> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
        val id = obj.optLong("id", -1L)
        if (id < 0) return@mapNotNull null
        CustomerMockup(
            id = id,
            productKey = obj.optString("product_key", ""),
            productName = obj.optString("product_name", ""),
            mockupUrl = obj.optString("mockup_url", ""),
            useAsPreview = obj.optBoolean("use_as_preview", false),
            createdAt = obj.optString("created_at").takeIf { it.isNotBlank() }
        )
    }
}
