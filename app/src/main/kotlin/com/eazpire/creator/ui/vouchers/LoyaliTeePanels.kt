package com.eazpire.creator.ui.vouchers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val CardBorder = Color(0xFFE5E7EB)
private val TextStrong = Color(0xFF111827)
private val TextSubdued = Color(0xFF6B7280)
private val BadgeGreenBg = Color(0xFFECFDF5)
private val BadgeGreenText = Color(0xFF047857)
private val StampEmptyBorder = Color(0xFFD1D5DB)
private val StampFilledBorder = Color(0xFF0EA5E9)
private val StampFilledBg = Color(0xFFE0F2FE)
private val StampNextBorder = Color(0xFFF59E0B)
private val IconSky = Color(0xFF0EA5E9)
private val CtaOrange = Color(0xFFF97316)

private fun fmtLoyaliteeDate(ts: String?): String {
    if (ts.isNullOrBlank()) return ""
    return try {
        val instant = Instant.parse(ts.replace(" ", "T"))
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
            .withZone(ZoneId.systemDefault())
            .format(instant)
    } catch (_: Exception) {
        ts
    }
}

private fun tReplace(template: String, vars: Map<String, String>): String {
    var out = template
    vars.forEach { (k, v) -> out = out.replace("{$k}", v) }
    return out
}

@Composable
fun LoyaliTeeStampsPanel(
    status: JSONObject?,
    loading: Boolean,
    errorText: String?,
    t: (String, String) -> String,
    onChooseTee: (rewardId: String) -> Unit,
) {
    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        errorText != null -> Text(
            errorText,
            modifier = Modifier.padding(16.dp),
            color = TextSubdued
        )
        status == null -> Text(
            t("eaz.loyalitee.error_loading", "Could not load LoyaliTee. Please try again."),
            modifier = Modifier.padding(16.dp),
            color = TextSubdued
        )
        else -> LoyaliTeeStampCard(status = status, t = t, onChooseTee = onChooseTee)
    }
}

@Composable
private fun LoyaliTeeStampCard(
    status: JSONObject,
    t: (String, String) -> String,
    onChooseTee: (rewardId: String) -> Unit,
) {
    val stampCount = status.optInt("stamp_count", 0)
    val untilNext = status.optInt("stamps_until_next", 0)
    val perReward = status.optInt("stamps_per_reward", 10).coerceAtLeast(1)
    val availableArr = status.optJSONArray("available_rewards")
    val availableCount = availableArr?.length() ?: 0
    val firstRewardId = if (availableCount > 0) availableArr!!.getJSONObject(0).optString("id") else ""

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFFFF9F3), Color.White, Color(0xFFF0F9FF))
                    )
                )
                .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 18.dp)
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Checkroom,
                            contentDescription = null,
                            tint = IconSky,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            t("eaz.loyalitee.card_title", "LoyaliTee"),
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = TextStrong,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    if (availableCount > 0) {
                        Text(
                            tReplace(
                                t("eaz.loyalitee.rewards_available", "{count} free tee(s) ready"),
                                mapOf("count" to availableCount.toString())
                            ),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BadgeGreenText,
                            modifier = Modifier
                                .background(BadgeGreenBg, RoundedCornerShape(999.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val columns = 5
                    val rows = (perReward + columns - 1) / columns
                    for (row in 0 until rows) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            for (col in 0 until columns) {
                                val i = row * columns + col
                                if (i >= perReward) {
                                    Box(Modifier.weight(1f))
                                    continue
                                }
                                val filled = i < stampCount
                                val isNext = !filled && i == stampCount
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            when {
                                                filled -> StampFilledBg
                                                else -> Color.White
                                            }
                                        )
                                        .border(
                                            width = 2.dp,
                                            color = when {
                                                filled -> StampFilledBorder
                                                isNext -> StampNextBorder
                                                else -> StampEmptyBorder
                                            },
                                            shape = RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Checkroom,
                                        contentDescription = null,
                                        tint = if (filled) StampFilledBorder else Color(0xFFCBD5E1),
                                        modifier = Modifier.fillMaxSize(0.65f)
                                    )
                                }
                            }
                        }
                    }
                }

                val progressTemplate = t(
                    "eaz.loyalitee.progress",
                    "{current} of {total} stamps — {remaining} until your free tee"
                )
                val progressPlain = tReplace(
                    progressTemplate,
                    mapOf(
                        "current" to stampCount.toString(),
                        "total" to perReward.toString(),
                        "remaining" to untilNext.toString()
                    )
                )
                Text(
                    buildAnnotatedString {
                        val strong = stampCount.toString()
                        val idx = progressPlain.indexOf(strong)
                        if (idx >= 0) {
                            append(progressPlain.substring(0, idx))
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(strong) }
                            append(progressPlain.substring(idx + strong.length))
                        } else {
                            append(progressPlain)
                        }
                    },
                    fontSize = 14.sp,
                    color = TextStrong,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    t(
                        "eaz.loyalitee.hint",
                        "Each purchased item = 1 stamp. Logged-in only. Shipping paid separately."
                    ),
                    fontSize = 12.5.sp,
                    color = TextSubdued,
                    modifier = Modifier.padding(bottom = 14.dp)
                )

                Button(
                    onClick = { if (firstRewardId.isNotBlank()) onChooseTee(firstRewardId) },
                    enabled = availableCount > 0 && firstRewardId.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CtaOrange)
                ) {
                    Text(t("eaz.loyalitee.choose_tee", "Choose free T-shirt"))
                }
            }
        }
    }
}

@Composable
fun LoyaliTeeRedeemedPanel(
    status: JSONObject?,
    loading: Boolean,
    errorText: String?,
    t: (String, String) -> String,
) {
    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        errorText != null -> Text(errorText, modifier = Modifier.padding(16.dp), color = TextSubdued)
        status == null -> Text(
            t("eaz.loyalitee.error_loading", "Could not load LoyaliTee. Please try again."),
            modifier = Modifier.padding(16.dp),
            color = TextSubdued
        )
        else -> {
            val arr = status.optJSONArray("redeemed_rewards")
            val items = remember(arr) {
                if (arr == null) emptyList() else (0 until arr.length()).map { arr.getJSONObject(it) }
            }
            if (items.isEmpty()) {
                Text(
                    t("eaz.loyalitee.no_redeemed", "No redeemed LoyaliTee rewards yet."),
                    modifier = Modifier.padding(16.dp),
                    color = TextSubdued
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(280.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items) { item ->
                        LoyaliTeeRedeemedRow(item = item, t = t)
                    }
                }
            }
        }
    }
}

@Composable
private fun LoyaliTeeRedeemedRow(item: JSONObject, t: (String, String) -> String) {
    val title = item.optString("product_title").ifBlank {
        t("eaz.loyalitee.unnamed_tee", "Softstyle Cotton Tee")
    }
    val dateStr = fmtLoyaliteeDate(item.optString("redeemed_at"))
    val imageUrl = item.optString("image_url").takeIf { it.isNotBlank() }

    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
            .background(Color.White)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF3F4F6))
            )
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextStrong)
            if (dateStr.isNotBlank()) {
                Text(dateStr, fontSize = 13.sp, color = TextSubdued, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
