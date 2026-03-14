package com.eazpire.creator.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.eazpire.creator.EazColors
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.util.DebugLog

/**
 * My Creations Tab – native Android UI.
 * Web reference: theme/sections/account-my-designs.liquid
 * Displays recent jobs (designs, mockups) from list-jobs.
 */
data class JobItem(
    val jobId: String,
    val imageUrl: String?,
    val prompt: String?,
    val productName: String?,
    val done: Boolean,
    val progress: Int
)

@Composable
fun AccountCreationsTab(
    tokenStore: SecureTokenStore,
    modifier: Modifier = Modifier
) {
    val jwt = remember { tokenStore.getJwt() }
    val ownerId = remember { tokenStore.getOwnerId() ?: "" }
    val api = remember(jwt) { com.eazpire.creator.api.CreatorApi(jwt = jwt) }

    var items by remember { mutableStateOf<List<JobItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) return@LaunchedEffect
        isLoading = true
        errorMessage = null
        try {
            val resp = api.listJobs(ownerId, 30)
            if (resp.optBoolean("ok", false)) {
                val arr = resp.optJSONArray("items")
                items = parseJobsFromJson(arr)
            } else {
                errorMessage = resp.optString("error", "Failed to load creations")
            }
        } catch (e: Exception) {
            DebugLog.click("Creations load error: ${e.message}")
            errorMessage = "Failed to load creations"
        } finally {
            isLoading = false
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
            text = "My Creations",
            style = MaterialTheme.typography.titleMedium,
            color = EazColors.TextPrimary
        )
        Text(
            text = "Your designs and recent generation jobs.",
            style = MaterialTheme.typography.bodySmall,
            color = EazColors.TextSecondary
        )

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(24.dp))
            }
        } else {
            errorMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            if (items.isEmpty()) {
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
                            Icons.Default.Palette,
                            contentDescription = null,
                            modifier = Modifier.padding(bottom = 8.dp),
                            tint = EazColors.TextSecondary
                        )
                        Text(
                            text = "No creations yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EazColors.TextSecondary
                        )
                        Text(
                            text = "Create designs in the app",
                            style = MaterialTheme.typography.bodySmall,
                            color = EazColors.TextSecondary
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f),
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
                                        .background(EazColors.TopbarBorder.copy(alpha = 0.2f))
                                ) {
                                    if (item.imageUrl != null) {
                                        AsyncImage(
                                            model = item.imageUrl,
                                            contentDescription = item.prompt,
                                            modifier = Modifier.fillMaxWidth(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else if (!item.done) {
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                progress = (item.progress / 100f).coerceIn(0f, 1f),
                                                color = EazColors.Orange,
                                                modifier = Modifier.padding(24.dp)
                                            )
                                        }
                                    }
                                }
                                val label = item.productName?.takeIf { it.isNotBlank() }
                                    ?: item.prompt?.let { it.take(40) + if (it.length > 40) "…" else "" }
                                    ?: "Creation"
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EazColors.TextPrimary,
                                    modifier = Modifier.padding(8.dp),
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseJobsFromJson(arr: org.json.JSONArray?): List<JobItem> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val obj = arr.optJSONObject(i) ?: return@mapNotNull null
        val jobId = obj.optString("job_id", "").ifBlank { return@mapNotNull null }
        JobItem(
            jobId = jobId,
            imageUrl = obj.optString("image_url").takeIf { it.isNotBlank() }
                ?: (obj.optJSONObject("result")?.optString("image_url") ?: obj.optJSONObject("result")?.optString("preview_url")).takeIf { it?.isNotBlank() == true },
            prompt = obj.optString("prompt").takeIf { it.isNotBlank() },
            productName = obj.optString("product_name").takeIf { it.isNotBlank() },
            done = obj.optBoolean("done", false),
            progress = obj.optInt("progress", 0)
        )
    }
}
