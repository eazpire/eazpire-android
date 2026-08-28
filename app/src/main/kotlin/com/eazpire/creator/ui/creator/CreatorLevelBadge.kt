package com.eazpire.creator.ui.creator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eazpire.creator.EazColors
import com.eazpire.creator.billing.EazBalanceRefreshBus
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.auth.SecureTokenStore
import com.eazpire.creator.i18n.TranslationStore
import com.eazpire.creator.ui.share.getActiveRefUrl
import com.eazpire.creator.ui.share.prefetchShareUrl
import com.eazpire.creator.ui.share.sharePageLink
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

private const val ACK_PREFS = "creator_level_ack"
private val PulseAmber = Color(0xFFFBBF24)
private val PulseAmberDeep = Color(0xFFF59E0B)

private enum class LevelOverlay { Blocked, Success }

private fun ackPrefs(context: Context) =
    context.applicationContext.getSharedPreferences(ACK_PREFS, Context.MODE_PRIVATE)

private fun ackKey(ownerId: String) = "creator_lvl_ack_$ownerId"

private fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("eazpire", text))
}

/** Level Badge 1:1 wie Web: creator-level-badge + pulse + celebration + share (copies link). */
@Composable
fun CreatorLevelBadge(
    translationStore: TranslationStore,
    tokenStore: SecureTokenStore,
    ownerId: String?,
    isLoggedIn: Boolean,
    onJourneyClick: () -> Unit = {},
    onOpenCreatorCodes: () -> Unit = {},
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
    var pulse by remember { mutableStateOf(false) }
    var trialBlocked by remember { mutableStateOf(false) }
    var celebrateLevel by remember { mutableStateOf<Int?>(null) }
    var ackPending by remember { mutableStateOf(1) }
    var overlay by remember { mutableStateOf<LevelOverlay?>(null) }

    if (isLoggedIn && !ownerId.isNullOrBlank()) {
        val api = remember { CreatorApi(jwt = tokenStore.getJwt()) }
        LaunchedEffect(ownerId, economyRefreshTick) {
            levelLoadFailed = false
            shareUrl = getActiveRefUrl(api, ownerId!!)
            try {
                prefetchShareUrl(api, ownerId!!, "/pages/creator-dashboard", context)
            } catch (_: Exception) {}
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

                    val xpDer = r.optInt("xp_derived_level", displayLevel)
                    val prefs = ackPrefs(context)
                    val key = ackKey(ownerId!!)
                    if (!prefs.contains(key)) {
                        prefs.edit().putInt(key, displayLevel).apply()
                    }
                    val ack = prefs.getInt(key, displayLevel)
                    ackPending = ack
                    trialBlocked = trialMode && xpDer > displayLevel && displayLevel < 10
                    val canCelebrate = !trialMode && displayLevel > ack && displayLevel <= 10
                    celebrateLevel = if (canCelebrate) displayLevel else null
                    pulse = trialBlocked || canCelebrate
                } else {
                    levelLoadFailed = true
                    pulse = false
                }
            } catch (_: Exception) {
                levelLoadFailed = true
                pulse = false
            } finally {
                if (levelLoadFailed) {
                    levelName =
                        translationStore.t(
                            "creator.overview.default_level",
                            "Starter",
                        )
                    pulse = false
                }
            }
        }
    } else {
        levelNum = 0
        levelName = translationStore.t("creator.overview.level_names.0", "Starter")
        xpValue = "0 / 50"
        xpFillPercent = 0f
        pulse = false
        trialBlocked = false
        celebrateLevel = null
        overlay = null
        xpHint = translationStore.t(
            "creator.mobile.xp_until_next",
            "XP until next level",
        )
    }

    val badgeShape = RoundedCornerShape(28.dp)
    val pulseAnim = rememberInfiniteTransition(label = "creatorLevelPulse")
    val pulseT by pulseAnim.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glow",
    )
    val borderColor = if (pulse) {
        PulseAmber.copy(alpha = 0.35f + 0.30f * pulseT)
    } else {
        Color.White.copy(alpha = 0.12f)
    }
    val readyAria = translationStore.t(
        "creator.overview.level_up_badge_ready_aria",
        "Level up available — tap to continue.",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 68.dp, max = 88.dp)
                .clip(badgeShape)
                .background(Color(0xFF0B1220).copy(alpha = 0.55f), badgeShape)
                .then(
                    if (pulse) {
                        Modifier.drawBehind {
                            val r = 28.dp.toPx()
                            drawRoundRect(
                                color = PulseAmberDeep.copy(alpha = 0.22f + 0.33f * pulseT),
                                cornerRadius = CornerRadius(r),
                                style = Stroke(width = (2.dp.toPx() + 10.dp.toPx() * pulseT)),
                            )
                        }
                    } else Modifier
                )
                .border(if (pulse) 1.5.dp else 1.dp, borderColor, badgeShape)
                .clickable(
                    enabled = pulse,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    if (trialBlocked) {
                        overlay = LevelOverlay.Blocked
                    } else if (celebrateLevel != null) {
                        overlay = LevelOverlay.Success
                        ownerId?.let { oid ->
                            ackPrefs(context).edit().putInt(ackKey(oid), celebrateLevel ?: levelNum).apply()
                        }
                        pulse = false
                        trialBlocked = false
                        celebrateLevel = null
                    }
                }
                .semantics {
                    if (pulse) contentDescription = readyAria
                }
                .padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = translationStore.t("creator.overview.level_label", "LEVEL").uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                    ),
                    color = Color(0xFF9CA3AF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(48.dp, 999.dp, 999.dp, 48.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                            .fillMaxHeight((xpFillPercent / 100f).coerceIn(0f, 1f))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0xFFFB923C), EazColors.Orange)
                                ),
                                RoundedCornerShape(4.dp)
                            )
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    Text(
                        text = "XP",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color(0xFF9CA3AF),
                        maxLines = 1
                    )
                    Text(
                        text = xpValue,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (pulse) PulseAmber else EazColors.Orange,
                        maxLines = 1,
                        softWrap = false,
                    )
                    if (!pulse && xpHint.isNotBlank()) {
                        Text(
                            text = xpHint,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                lineHeight = 11.sp
                            ),
                            color = Color(0xFF6B7280),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
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
                    val fallback = shareUrl ?: "https://www.eazpire.com/pages/creator-dashboard"
                    if (oid.isNullOrBlank()) {
                        copyText(context, fallback)
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, fallback)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, null))
                    } else {
                        scope.launch {
                            val api = CreatorApi(jwt = tokenStore.getJwt())
                            try {
                                copyText(context, fallback)
                                sharePageLink(context, api, oid, "/pages/creator-dashboard")
                            } catch (_: Exception) {
                                copyText(context, fallback)
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, fallback)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, null))
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Share,
                contentDescription = translationStore.t("creator.common.share", "Share"),
                tint = EazColors.Orange,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    if (overlay != null) {
        LevelCelebrationDialog(
            mode = overlay!!,
            fromLevel = ackPending,
            toLevel = celebrateLevel ?: levelNum,
            levelName = levelName,
            translationStore = translationStore,
            onDismiss = { overlay = null },
            onOpenCreatorCodes = {
                overlay = null
                onOpenCreatorCodes()
            },
        )
    }
}

@Composable
private fun LevelCelebrationDialog(
    mode: LevelOverlay,
    fromLevel: Int,
    toLevel: Int,
    levelName: String,
    translationStore: TranslationStore,
    onDismiss: () -> Unit,
    onOpenCreatorCodes: () -> Unit,
) {
    val blocked = mode == LevelOverlay.Blocked
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF111827).copy(alpha = 0.94f))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                .padding(horizontal = 22.dp, vertical = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = translationStore.t(
                        if (blocked) "creator.overview.level_up_blocked_title" else "creator.overview.level_up_celebrate_title",
                        if (blocked) "Activate a Creator Code" else "LEVEL UP!",
                    ),
                    color = if (blocked) Color(0xFFBFDBFE) else Color(0xFFFEF3C7),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    letterSpacing = if (blocked) 0.sp else 0.6.sp,
                    textAlign = TextAlign.Center,
                )
                if (!blocked) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = fromLevel.toString(),
                            color = PulseAmber.copy(alpha = 0.55f),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 36.sp,
                        )
                        Text("→", color = Color(0xFFFDE047), fontSize = 22.sp)
                        Text(
                            text = toLevel.toString(),
                            color = PulseAmber,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 36.sp,
                        )
                    }
                    if (levelName.isNotBlank()) {
                        Text(
                            text = levelName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Text(
                    text = translationStore.t(
                        if (blocked) "creator.overview.level_up_blocked_body" else "creator.overview.level_up_celebrate_subtitle",
                        if (blocked) {
                            "You have enough XP for Level 2. Redeem a Creator Code first to unlock your Creator tier and continue leveling."
                        } else {
                            "You reached a new level!"
                        },
                    ),
                    color = Color(0xE0E5E7EB),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
                if (blocked) {
                    Button(
                        onClick = onOpenCreatorCodes,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB),
                            contentColor = Color.White,
                        ),
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    ) {
                        Text(
                            translationStore.t(
                                "creator.overview.level_up_open_creator_codes",
                                "Open Creator Codes",
                            ),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(
                        translationStore.t("creator.common.close", "Close"),
                        color = Color(0xFF9CA3AF),
                    )
                }
            }
        }
    }
}
