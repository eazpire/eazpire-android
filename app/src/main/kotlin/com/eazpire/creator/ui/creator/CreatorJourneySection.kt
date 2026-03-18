package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CreatorJourneySection(
    translationStore: TranslationStore,
    ownerId: String?,
    isLoggedIn: Boolean,
    modifier: Modifier = Modifier
) {
    var progressPercent by remember { mutableStateOf(0) }
    var completedCount by remember { mutableStateOf(0) }
    var totalCount by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }

    if (isLoggedIn && !ownerId.isNullOrBlank()) {
        val api = remember { CreatorApi() }
        LaunchedEffect(ownerId) {
            try {
                val r = withContext(Dispatchers.IO) { api.getOnboardingProgress(ownerId!!) }
                if (r.optBoolean("ok", false)) {
                    val stats = r.optJSONObject("stats")
                    if (stats != null) {
                        progressPercent = stats.optInt("progress_percent", 0)
                        completedCount = stats.optInt("completed_count", 0)
                        totalCount = stats.optInt("total_todos", 0)
                    }
                }
            } catch (_: Exception) {}
            isLoading = false
        }
    } else {
        isLoading = false
    }

    Text(
        text = translationStore.t("creator.overview.onboarding_title", "Creator Journey"),
        style = MaterialTheme.typography.titleSmall,
        color = Color.White,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .height(8.dp)
                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressPercent / 100f)
                    .fillMaxHeight()
                    .background(
                        com.eazpire.creator.EazColors.Orange,
                        RoundedCornerShape(4.dp)
                    )
            )
        }
        Text(
            text = if (isLoading) translationStore.t("creator.overview.loading", "Loading…")
            else "${completedCount}/${totalCount}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}
