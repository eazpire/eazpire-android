package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun CreatorLevelBadge(
    translationStore: TranslationStore,
    ownerId: String?,
    isLoggedIn: Boolean,
    modifier: Modifier = Modifier
) {
    var levelNum by remember { mutableStateOf(1) }
    var levelName by remember { mutableStateOf(translationStore.t("creator.overview.loading", "Loading…")) }
    var xpValue by remember { mutableStateOf("0 / 50") }
    var xpFillPercent by remember { mutableStateOf(0f) }

    if (isLoggedIn && !ownerId.isNullOrBlank()) {
        val api = remember { CreatorApi() }
        LaunchedEffect(ownerId) {
            try {
                val r = withContext(Dispatchers.IO) { api.getLevel(ownerId!!) }
                if (r.optBoolean("ok", false)) {
                    val totalXp = r.optInt("total_xp", 0)
                    val thresholds = r.optJSONArray("thresholds")
                    var level = 1
                    var nextXp = 50
                    if (thresholds != null) {
                        for (i in thresholds.length() - 1 downTo 0) {
                            val t = thresholds.getJSONObject(i)
                            if (totalXp >= t.optInt("xp_required", 0)) {
                                level = t.optInt("level", 1)
                                break
                            }
                        }
                        for (i in 0 until thresholds.length()) {
                            val t = thresholds.getJSONObject(i)
                            if (t.optInt("level", 0) == level + 1) {
                                nextXp = t.optInt("xp_required", 50)
                                break
                            }
                        }
                    }
                    val curT = (0 until (thresholds?.length() ?: 0)).firstOrNull {
                        thresholds?.getJSONObject(it)?.optInt("level", 0) == level
                    }?.let { thresholds?.getJSONObject(it) }
                    val curXpReq = curT?.optInt("xp_required", 0) ?: 0
                    val xpInLevel = totalXp - curXpReq
                    val xpNeeded = nextXp - curXpReq
                    val pct = if (xpNeeded > 0) (xpInLevel.toFloat() / xpNeeded * 100).coerceIn(0f, 100f) else 100f

                    levelNum = level
                    levelName = translationStore.t("creator.overview.level_names.$level", "Level $level")
                    xpValue = "$totalXp / $nextXp XP"
                    xpFillPercent = pct
                }
            } catch (_: Exception) {}
        }
    } else {
        levelName = translationStore.t("creator.overview.level_names.0", "Starter")
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .background(
                color = Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = EazColors.Orange,
                modifier = Modifier.size(28.dp)
            )
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(start = 12.dp))
            Column {
                Text(
                    text = translationStore.t("creator.overview.level_label", "LEVEL").uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = "$levelNum $levelName",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Box(
                modifier = Modifier
                    .height(24.dp)
                    .padding(2.dp)
                    .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(xpFillPercent / 100f)
                        .background(EazColors.Orange, RoundedCornerShape(10.dp))
                )
            }
            Text(
                text = xpValue,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}
