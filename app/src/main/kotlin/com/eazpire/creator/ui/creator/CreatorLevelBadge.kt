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
import androidx.compose.runtime.collectAsState
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
import com.eazpire.creator.billing.EazBalanceRefreshBus
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.share.getActiveRefUrl
import com.eazpire.creator.ui.share.resolveShareUrl
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/** Level Badge 1:1 wie Web: creator-level-badge + creator-level-share-row (nur Share) */
@Composable
fun CreatorLevelBadge(
    translationStore: TranslationStore,
    tokenStore: SecureTokenStore,
    ownerId: String?,
    isLoggedIn: Boolean,
    onJourneyClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val economyRefreshTick by EazBalanceRefreshBus.tick.collectAsState()
    val scope = rememberCoroutineScope()
    var shareUrl by remember { mutableStateOf<String?>(null) }
    var levelNum by remember { mutableStateOf(1) }
    var levelName by remember { mutableStateOf(translationStore.t("creator.overview.loading", "Loading…")) }
    var levelLoadFailed by remember { mutableStateOf(false) }
    var xpValue by remember { mutableStateOf("0 / 50") }
    var xpFillPercent by remember { mutableStateOf(0f) }
    var xpHint by remember { mutableStateOf(translationStore.t("creator.mobile.xp_until_next", "XP until next level")) }

    if (isLoggedIn && !ownerId.isNullOrBlank()) {
        val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }
        LaunchedEffect(ownerId, economyRefreshTick) {
            levelLoadFailed = false
            shareUrl = getActiveRefUrl(api, ownerId!!)
            try {
                val r = withContext(Dispatchers.IO) { api.getLevel(ownerId!!) }
                if (r.optBoolean("ok", false)) {
                    val thresholds: JSONArray? = when {
                        r.has("level_thresholds") && r.getJSONArray("level_thresholds").length() > 0 ->
                            r.getJSONArray("level_thresholds")

                        r.has("thresholds") && r.getJSONArray("thresholds").length() > 0 ->
                            r.getJSONArray("thresholds")

                        else -> null
                    }
                    fun maxLevelFromThresholds(arr: JSONArray?): Int {
                        if (arr == null || arr.length() == 0) return 1
                        var m = 1
                        for (j in 0 until arr.length()) {
                            val lv = arr.getJSONObject(j).optInt("level", 0)
                            if (lv > m) m = lv
                        }
                        return kotlin.math.max(1, m)
                    }
                    val maxLevel = maxLevelFromThresholds(thresholds)
                    fun xpAt(L: Int): Int {
                        if (thresholds == null) return 0
                        for (j in 0 until thresholds.length()) {
                            val row = thresholds.getJSONObject(j)
                            if (row.optInt("level", 0) == L) return row.optInt("xp_required", 0)
                        }
                        return 0
                    }
                    val totalXp = r.optInt("total_xp", 0)
                    val rawLevel = r.optInt("current_level", r.optInt("level", 1))
                    val displayLevel = rawLevel.coerceIn(1, maxLevel)
                    val trialMode = r.optBoolean("trial_mode", false)
                    val trialNeedsCreatorCode = r.optBoolean("trial_needs_creator_code", false)

                    levelNum = displayLevel

                    levelName =
                        translationStore.t(
                            "creator.overview.level_names.$displayLevel",
                            translationStore.t("creator.overview.default_level", "Starter")
                        )

                    val labels = r.optJSONObject("level_labels")
                    if (labels != null && labels.has(displayLevel.toString())) {
                        val s = labels.optString(displayLevel.toString(), "").trim()
                        if (s.isNotEmpty()) levelName = s
                    }

                    if (trialMode) {
                        val capXp = kotlin.math.max(1, xpAt(2))
                        xpFillPercent =
                            kotlin.math.min(100f, totalXp.toFloat() / capXp.toFloat() * 100f)
                        xpValue = "$totalXp / $capXp XP"
                        xpHint =
                            if (trialNeedsCreatorCode || totalXp >= capXp) {
                                translationStore.t(
                                    "creator.overview.xp_need_creator_code",
                                    translationStore.t("creator.mobile.xp_need_creator_code", "")
                                )
                            } else {
                                val remTrial = kotlin.math.max(0, capXp - totalXp)
                                translationStore.t(
                                    "creator.overview.xp_until_next",
                                    translationStore.t("creator.mobile.xp_until_next", "XP until next level")
                                )
                                    .replace("{xp}", remTrial.toString())
                                    .replace("{level}", "2")
                            }
                    } else {
                        val curXpReq = xpAt(displayLevel)
                        val nextXpAbs = xpAt(displayLevel + 1)
                        val hasNext =
                            displayLevel < maxLevel && nextXpAbs > curXpReq
                        val xpInLevel = kotlin.math.max(0, totalXp - curXpReq)
                        val xpNeeded = if (hasNext) kotlin.math.max(1, nextXpAbs - curXpReq) else 1
                        xpFillPercent =
                            if (hasNext)
                                kotlin.math.min(100f, xpInLevel.toFloat() / xpNeeded.toFloat() * 100f)
                            else 100f
                        xpValue =
                            if (hasNext) "$xpInLevel / $xpNeeded XP" else "$totalXp XP"
                        xpHint =
                            if (!hasNext) {
                                translationStore.t(
                                    "creator.overview.max_level_reached",
                                    translationStore.t("creator.mobile.max_level_reached", "")
                                )
                            } else {
                                val rem = kotlin.math.max(0, nextXpAbs - totalXp)
                                translationStore.t(
                                    "creator.overview.xp_until_next",
                                    translationStore.t("creator.mobile.xp_until_next", "XP until next level")
                                )
                                    .replace("{xp}", rem.toString())
                                    .replace("{level}", (displayLevel + 1).toString())
                            }
                    }
                } else {
                    levelLoadFailed = true
                }
            } catch (_: Exception) {
                levelLoadFailed = true
            } finally {
                if (levelLoadFailed) {
                    levelName =
                        translationStore.t(
                            "creator.overview.default_level",
                            "Starter",
                        )
                }
            }
        }
    } else {
        levelNum = 0
        levelName = translationStore.t("creator.overview.level_names.0", "Starter")
        xpValue = "0 / 50"
        xpFillPercent = 0f
        xpHint = translationStore.t(
            "creator.mobile.xp_until_next",
            "XP until next level",
        )
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
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onJourneyClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = translationStore.t("creator.journey.open_aria", "Open Creator Journey"),
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
        // creator-level-share-row: nur Share (kein Copy) – mit Ref-Link + Zielseite
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
                    val oid = ownerId
                    if (oid.isNullOrBlank()) {
                        val fallback = "https://www.eazpire.com/pages/creator-dashboard"
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, fallback)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, null))
                    } else {
                        scope.launch {
                            val api = CreatorApi(jwt = tokenStore.getJwt())
                            val urlToShare = try {
                                resolveShareUrl(api, oid, "/pages/creator-dashboard")
                            } catch (_: Exception) {
                                shareUrl ?: "https://www.eazpire.com/pages/creator-dashboard"
                            }
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, urlToShare)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, null))
                        }
                    }
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
