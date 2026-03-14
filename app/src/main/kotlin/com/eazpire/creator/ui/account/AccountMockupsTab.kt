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
import androidx.compose.material.icons.filled.Image
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
 * My Mockups Tab – native Android UI.
 * Web reference: theme/sections/my-mockups.liquid
 * Displays generated product mockups from list-customer-mockups.
 */
data class CustomerMockup(
    val id: Long,
    val productKey: String,
    val productName: String,
    val mockupUrl: String,
    val useAsPreview: Boolean,
    val createdAt: String?
)

@Composable
fun AccountMockupsTab(
    tokenStore: SecureTokenStore,
    modifier: Modifier = Modifier
) {
    val jwt = remember { tokenStore.getJwt() }
    val ownerId = remember { tokenStore.getOwnerId() ?: "" }
    val api = remember(jwt) { com.eazpire.creator.api.CreatorApi(jwt = jwt) }

    var mockups by remember { mutableStateOf<List<CustomerMockup>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
            text = "Your generated product mockups with your designs.",
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

            if (mockups.isEmpty()) {
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
                            text = "Generate mockups from the web app",
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
                    items(mockups) { mockup ->
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
                                ) {
                                    AsyncImage(
                                        model = mockup.mockupUrl,
                                        contentDescription = mockup.productName,
                                        modifier = Modifier.fillMaxWidth(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Text(
                                    text = mockup.productName.ifBlank { "Mockup" },
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
