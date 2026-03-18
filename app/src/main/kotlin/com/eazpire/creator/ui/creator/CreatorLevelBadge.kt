package com.eazpire.creator.ui.creator

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Level Badge 1:1 wie Web: creator-level-badge + creator-level-share-row (nur Share) */
@Composable
fun CreatorLevelBadge(
    translationStore: TranslationStore,
    tokenStore: SecureTokenStore,
    ownerId: String?,
    isLoggedIn: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var levelNum by remember { mutableStateOf(1) }
    var levelName by remember { mutableStateOf(translationStore.t("creator.overview.loading", "Loading…")) }
    var xpValue by remember { mutableStateOf("0 / 50") }
    var xpFillPercent by remember { mutableStateOf(0f) }
    var xpHint by remember { mutableStateOf(translationStore.t("creator.mobile.xp_until_next", "XP until next level")) }

    if (isLoggedIn && !ownerId.isNullOrBlank()) {
        val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }
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
            .padding(bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // creator-level-badge: icon | info | xp (wie Web)
        Row(
            modifier = Modifier
                .weight(1f)
                .background(
                    Color(0xFF0B1220).copy(alpha = 0.55f),
                    RoundedCornerShape(percent = 50)
                )
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(percent = 50))
                .padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // __icon: 44x44, gradient, star
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(EazColors.Orange, Color(0xFFEA580C))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            // __info: label + value (number + name)
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = translationStore.t("creator.overview.level_label", "LEVEL").uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = Color(0xFF9CA3AF)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = levelNum.toString(),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = EazColors.Orange
                    )
                    Text(
                        text = levelName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = Color.White
                    )
                }
            }
            // __xp: bar (8x48 vertical) + info
            Row(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(48.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(8.dp)
                        .height(48.dp)
                        .background(Color(0xFF374151), RoundedCornerShape(4.dp)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(xpFillPercent / 100f)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFFFB923C), EazColors.Orange)
                                ),
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Text(
                        text = "XP",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFF9CA3AF)
                    )
                    Text(
                        text = xpValue,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = EazColors.Orange
                    )
                    Text(
                        text = xpHint,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp
                        ),
                        color = Color(0xFF6B7280)
                    )
                }
            }
        }
        // creator-level-share-row: nur Share (kein Copy)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "https://www.eazpire.com")
                    }
                    context.startActivity(Intent.createChooser(sendIntent, null))
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Share,
                contentDescription = "Share",
                tint = EazColors.Orange,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
