package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class DailyLimitBar(
    val used: Int = 0,
    val cap: Int = 0,
    val locked: Boolean = false,
)

data class DailyLimitsSnapshot(
    val upload: DailyLimitBar = DailyLimitBar(),
    val generate: DailyLimitBar = DailyLimitBar(),
    val publish: DailyLimitBar = DailyLimitBar(),
    val showCountdown: Boolean = false,
)

data class JourneySlotUsage(
    val maxActiveDesignSlots: Int = 0,
    val maxProducts: Int = 0,
    val activeDesignsUsed: Int = 0,
    val productsUsed: Int = 0,
)

fun slotFillPercent(used: Int, cap: Int): Int {
    if (cap <= 0) return 0
    return min(100, ((used.toFloat() / cap.toFloat()) * 100f).roundToInt())
}

fun formatDailyResetCountdown(remainingMs: Long): String {
    val totalSec = max(0L, remainingMs / 1000L)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

fun msUntilNextUtcMidnight(nowMs: Long = System.currentTimeMillis()): Long {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.timeInMillis = nowMs
    cal.add(Calendar.DAY_OF_YEAR, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return max(0L, cal.timeInMillis - nowMs)
}

fun parseDailyLimitsSnapshot(resp: JSONObject?): DailyLimitsSnapshot {
    if (resp == null || !resp.optBoolean("ok", false)) return DailyLimitsSnapshot()
    val creation = resp.optJSONObject("creation_limits_effective") ?: JSONObject()
    val listing = resp.optJSONObject("listing_limits_effective") ?: JSONObject()
    val shopify = listing.optJSONObject("channels")?.optJSONObject("shopify") ?: JSONObject()
    val uploadCap = creation.optInt("upload_cap", 0)
    val generateCap = creation.optInt("generate_cap", 0)
    val dailyPublish = shopify.optInt("listings_per_day", 0)
    val publishCap = if (dailyPublish > 0) dailyPublish else shopify.optInt("listings_cap", 0)
    val publishUsed = if (dailyPublish > 0) {
        shopify.optInt("listings_used_today", 0)
    } else {
        shopify.optInt("listings_used_total", 0)
    }
    val mode = creation.optString("mode", "daily")
    return DailyLimitsSnapshot(
        upload = DailyLimitBar(
            used = creation.optInt("upload_used", 0),
            cap = uploadCap,
            locked = uploadCap <= 0,
        ),
        generate = DailyLimitBar(
            used = creation.optInt("generate_used", 0),
            cap = generateCap,
            locked = generateCap <= 0,
        ),
        publish = DailyLimitBar(
            used = publishUsed,
            cap = publishCap,
            locked = shopify.optBoolean("channel_unlocked", true).not() || publishCap <= 0,
        ),
        showCountdown = mode != "lifetime",
    )
}

fun parseJourneySlotUsage(resp: JSONObject?): JourneySlotUsage {
    val j = resp?.optJSONObject("journey_limits") ?: JSONObject()
    val listing = resp?.optJSONObject("listing_limits_effective")
    val shopify = listing?.optJSONObject("channels")?.optJSONObject("shopify")
    val listingCap = shopify?.optInt("listings_cap", 0) ?: 0
    val listingUsed = when {
        shopify == null -> j.optInt("products_used", 0)
        shopify.has("listings_used_total") -> shopify.optInt("listings_used_total", 0)
        shopify.has("listings_used_total") -> shopify.optInt("listings_used_total", 0)
        else -> j.optInt("products_used", 0)
    }
    return JourneySlotUsage(
        maxActiveDesignSlots = j.optInt("max_active_design_slots", 0),
        maxProducts = if (listingCap > 0) listingCap else j.optInt("max_products", 0),
        activeDesignsUsed = j.optInt("active_designs_used", 0),
        productsUsed = if (listingCap > 0) listingUsed else j.optInt("products_used", 0),
    )
}

private val UploadFill = Brush.horizontalGradient(
    listOf(Color(0xFF38BDF8), Color(0xFF7C3AED))
)
private val GenerateFill = Brush.horizontalGradient(
    listOf(Color(0xFF7C3AED), Color(0xFFC026D3))
)
private val PublishFill = Brush.horizontalGradient(
    listOf(Color(0xFFC026D3), Color(0xFFEA580C))
)
private val CountdownFill = Brush.horizontalGradient(
    listOf(Color(0xFF7C3AED), Color(0xFFF97316))
)
private val DesignsSlotFill = GenerateFill
private val ProductsSlotFill = PublishFill

@Composable
private fun CosmicUsageBar(
    used: Int,
    cap: Int,
    locked: Boolean,
    lockedLabel: String,
    fill: Brush,
    modifier: Modifier = Modifier,
    barWidth: Int = 68,
    barHeight: Int = 18,
    iconPrefix: String = "",
) {
    val pct = if (locked) 0 else slotFillPercent(used, cap)
    val count = if (locked) lockedLabel else "$used/$cap"
    val label = if (iconPrefix.isBlank()) count else "$iconPrefix $count"
    Box(
        modifier = modifier
            .width(barWidth.dp)
            .height(barHeight.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0xEB050810))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(999.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(pct / 100f)
                .align(Alignment.CenterStart)
                .background(fill)
        )
        Text(
            text = label,
            color = if (!locked && cap > 0 && used >= cap) Color(0xFFFFB070) else Color(0xFFF8FAFC),
            fontSize = 10.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 11.sp,
        )
    }
}

@Composable
fun CreatorDailyLimitsSubheader(
    snapshot: DailyLimitsSnapshot,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lockedLabel = translationStore.t("creator.daily_limits_subheader.locked", "Locked")
    var remainingMs by remember { mutableLongStateOf(msUntilNextUtcMidnight()) }
    LaunchedEffect(snapshot.showCountdown) {
        if (!snapshot.showCountdown) return@LaunchedEffect
        while (true) {
            remainingMs = msUntilNextUtcMidnight()
            delay(1000)
        }
    }
    val dayMs = 24L * 60L * 60L * 1000L
    val countdownPct = if (dayMs <= 0L) 0f else ((remainingMs.toFloat() / dayMs.toFloat()) * 100f)
        .coerceIn(0f, 100f)

    fun showInfo(key: String, fallback: String) {
        ReplaceInfoToast.show(context, translationStore.t(key, fallback))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xE1080512), Color(0xB8080512))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DailyLimitChip(
            iconPrefix = "↑",
            bar = snapshot.upload,
            lockedLabel = lockedLabel,
            fill = UploadFill,
            modifier = Modifier
                .weight(1f, fill = false)
                .clickable {
                    showInfo(
                        "creator.daily_limits_subheader.info_uploads",
                        "Daily image uploads used versus your limit. Resets with the timer.",
                    )
                },
        )
        Text("│", color = Color.White.copy(alpha = 0.16f), fontSize = 11.sp)
        DailyLimitChip(
            iconPrefix = "✦",
            bar = snapshot.generate,
            lockedLabel = lockedLabel,
            fill = GenerateFill,
            modifier = Modifier
                .weight(1f, fill = false)
                .clickable {
                    showInfo(
                        "creator.daily_limits_subheader.info_generations",
                        "Daily AI generations used versus your limit. Each generate counts.",
                    )
                },
        )
        Text("│", color = Color.White.copy(alpha = 0.16f), fontSize = 11.sp)
        DailyLimitChip(
            iconPrefix = "↗",
            bar = snapshot.publish,
            lockedLabel = lockedLabel,
            fill = PublishFill,
            modifier = Modifier
                .weight(1f, fill = false)
                .clickable {
                    showInfo(
                        "creator.daily_limits_subheader.info_publishes",
                        "Daily product publishes used versus your limit.",
                    )
                },
        )
        if (snapshot.showCountdown) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clickable {
                        showInfo(
                            "creator.daily_limits_subheader.info_timer",
                            "Time left until daily upload, generation, and publish limits reset (00:00 UTC).",
                        )
                    },
            ) {
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.06f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(countdownPct / 100f)
                            .background(CountdownFill)
                    )
                }
                Text(
                    text = formatDailyResetCountdown(remainingMs),
                    color = Color(0xFFC4B5FD),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun DailyLimitChip(
    iconPrefix: String,
    bar: DailyLimitBar,
    lockedLabel: String,
    fill: Brush,
    modifier: Modifier = Modifier,
) {
    CosmicUsageBar(
        used = bar.used,
        cap = bar.cap,
        locked = bar.locked,
        lockedLabel = lockedLabel,
        fill = fill,
        modifier = modifier,
            barWidth = 84,
        barHeight = 16,
        iconPrefix = iconPrefix,
    )
}

@Composable
fun CreationsSlotUsageBar(
    designsUsed: Int,
    designsCap: Int,
    productsUsed: Int,
    productsCap: Int,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    fun showInfo(key: String, fallback: String) {
        ReplaceInfoToast.show(context, translationStore.t(key, fallback))
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xD11C2434), Color(0xBF1C2434))
                )
            )
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.clickable {
                showInfo(
                    "creator.creations.slots_designs_info",
                    "Active designs in your library versus your Skill Tree slot cap. Inactive designs do not count.",
                )
            },
        ) {
            CosmicUsageBar(
                used = designsUsed,
                cap = designsCap,
                locked = designsCap <= 0,
                lockedLabel = "—",
                fill = DesignsSlotFill,
                barWidth = 88,
                iconPrefix = "✦",
            )
        }
        Text("│", color = Color.White.copy(alpha = 0.16f), fontSize = 11.sp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.clickable {
                showInfo(
                    "creator.creations.slots_products_info",
                    "Live listings versus your Skill Tree listing cap. Each listed product uses one slot.",
                )
            },
        ) {
            CosmicUsageBar(
                used = productsUsed,
                cap = productsCap,
                locked = productsCap <= 0,
                lockedLabel = "—",
                fill = ProductsSlotFill,
                barWidth = 88,
                iconPrefix = "↗",
            )
        }
    }
}

@Composable
fun rememberDailyLimitsSnapshot(
    api: CreatorApi,
    ownerId: String,
): DailyLimitsSnapshot {
    var snapshot by remember { mutableStateOf(DailyLimitsSnapshot()) }
    LaunchedEffect(ownerId) {
        if (ownerId.isBlank()) {
            snapshot = DailyLimitsSnapshot()
            return@LaunchedEffect
        }
        snapshot = runCatching {
            withContext(Dispatchers.IO) { parseDailyLimitsSnapshot(api.getDailyLimits(ownerId)) }
        }.getOrDefault(DailyLimitsSnapshot())
    }
    return snapshot
}
