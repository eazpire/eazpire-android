package com.eazpire.creator.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.runtime.rememberCoroutineScope

data class DailyGamePickerItem(
    val slug: String,
    val title: String,
    val available: Boolean,
    val status: String,
    val cooldownRemainingSec: Int,
    val nextAvailableMs: Long? = null,
)

fun parseDailyGamePickerItems(arr: JSONArray?): List<DailyGamePickerItem> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val nextMs = o.optLong("next_available_ms", 0L).takeIf { it > 0L }
        DailyGamePickerItem(
            slug = o.optString("slug", ""),
            title = o.optString("title", o.optString("slug", "Game")),
            available = o.optBoolean("available", true),
            status = o.optString("status", "available"),
            cooldownRemainingSec = o.optInt("cooldown_remaining_sec", 0),
            nextAvailableMs = nextMs,
        )
    }.filter { it.slug.isNotBlank() }
}

private fun cooldownSecFromMs(ms: Long?): Int {
    val target = ms ?: return 0
    val sec = kotlin.math.ceil((target - System.currentTimeMillis()) / 1000.0).toInt()
    return sec.coerceAtLeast(0)
}

private fun formatCooldown(sec: Int): String {
    val s = sec.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val r = s % 60
    return if (h > 0) {
        "%d:%02d:%02d".format(h, m, r)
    } else {
        "%d:%02d".format(m, r)
    }
}

@Composable
fun EazyGamesPickerCarousel(
    api: CreatorApi,
    items: List<DailyGamePickerItem>,
    selectedSlug: String,
    notifyPush: Boolean,
    notifyEmail: Boolean,
    onSelect: (String) -> Unit,
    onNotifyChange: (push: Boolean, email: Boolean) -> Unit,
    t: (String, String) -> String,
) {
    val palette = LocalEazyModalPalette.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    var localItems by remember(items) { mutableStateOf(items) }

    LaunchedEffect(items) {
        localItems = items
    }

    LaunchedEffect(localItems.any { it.status == "cooldown" && cooldownSecFromMs(it.nextAvailableMs) > 0 }) {
        while (localItems.any { it.status == "cooldown" && cooldownSecFromMs(it.nextAvailableMs) > 0 }) {
            delay(1000)
            localItems =
                localItems.map { item ->
                    if (item.status != "cooldown") return@map item
                    val sec = cooldownSecFromMs(item.nextAvailableMs)
                    if (sec <= 0) {
                        item.copy(status = "available", available = true, cooldownRemainingSec = 0)
                    } else {
                        item.copy(cooldownRemainingSec = sec)
                    }
                }
            tick++
        }
    }

    val selected = localItems.find { it.slug == selectedSlug } ?: localItems.firstOrNull()
    val showNotify = selected?.status == "cooldown"

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            localItems.forEach { item ->
                val active = item.slug == selectedSlug
                Column(
                    modifier =
                        Modifier
                            .width(68.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (active) palette.accent.copy(alpha = 0.12f) else Color.Transparent,
                            )
                            .border(
                                1.dp,
                                when {
                                    active -> palette.accent.copy(alpha = 0.4f)
                                    item.status == "cooldown" -> palette.border
                                    else -> Color.Transparent
                                },
                                RoundedCornerShape(8.dp),
                            )
                            .clickable { onSelect(item.slug) }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        if (item.slug == "connect_four_5x5") "⚫" else "🃏",
                        fontSize = 16.sp,
                    )
                    Text(
                        when (item.slug) {
                            "connect_four_5x5" -> t("eazy_chat.games_connect_title", "Connect Four")
                            else -> t("eazy_chat.games_memory_title", "Memory Match")
                        },
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        textAlign = TextAlign.Center,
                        color = if (active) palette.text else palette.muted,
                        maxLines = 2,
                    )
                    if (item.status == "cooldown" && item.cooldownRemainingSec > 0) {
                        Text(
                            formatCooldown(item.cooldownRemainingSec),
                            fontSize = 8.sp,
                            color = palette.accent,
                        )
                    }
                }
            }
        }

        selected?.let { sel ->
            Spacer(modifier = Modifier.height(8.dp))
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.muted.copy(alpha = 0.06f))
                        .border(1.dp, palette.border, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                        .height(120.dp)
                        .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    when (sel.slug) {
                        "connect_four_5x5" -> t("eazy_chat.games_connect_title", "Connect Four")
                        else -> t("eazy_chat.games_memory_title", "Memory Match")
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = palette.accent,
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                val ruleKeys =
                    if (sel.slug == "connect_four_5x5") {
                        listOf(
                            "eazy_chat.games_connect_rules_p1" to "Goal: get four of your marks in a row before Eazy does.",
                            "eazy_chat.games_connect_rules_p2" to "You play X and move first. Eazy plays O.",
                            "eazy_chat.games_connect_rules_p3" to "You have four minutes for the whole round.",
                            "eazy_chat.games_connect_rules_p4" to "Beat Eazy for a chance at daily loot.",
                        )
                    } else {
                        listOf(
                            "eazy_chat.games_rules_p1" to "Goal: find all matching pairs on the board.",
                            "eazy_chat.games_rules_p2" to "After Start you get a short peek, then the countdown runs.",
                            "eazy_chat.games_rules_p3" to "Each non-matching pair counts as one wrong guess.",
                            "eazy_chat.games_rules_p4" to "Complete in time for a chance to win loot.",
                        )
                    }
                ruleKeys.forEach { (key, fb) ->
                    Text(
                        t(key, fb),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        color = palette.muted,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                if (sel.status == "cooldown" && sel.cooldownRemainingSec > 0) {
                    Text(
                        t("eazy_chat.games_cooldown_wait", "Available again in {{time}}")
                            .replace("{{time}}", formatCooldown(sel.cooldownRemainingSec)),
                        color = palette.text,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        if (showNotify) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    t("eazy_chat.games_notify_push", "Notify me (push) when available"),
                    fontSize = 11.sp,
                    color = palette.muted,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = notifyPush,
                    onCheckedChange = { v ->
                        onNotifyChange(v, notifyEmail)
                        scope.launch {
                            try {
                                api.saveNotificationPreferencesRaw(
                                    JSONObject().apply {
                                        put(
                                            "shop",
                                            JSONObject().apply {
                                                put(
                                                    "daily_game",
                                                    JSONObject().apply {
                                                        put("push", v)
                                                        put("in_app", v)
                                                        put("email", notifyEmail)
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            } catch (_: Exception) {}
                        }
                    },
                    colors =
                        SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = palette.accent,
                        ),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    t("eazy_chat.games_notify_email", "Notify me (email) when available"),
                    fontSize = 11.sp,
                    color = palette.muted,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = notifyEmail,
                    onCheckedChange = { v ->
                        onNotifyChange(notifyPush, v)
                        scope.launch {
                            try {
                                api.saveNotificationPreferencesRaw(
                                    JSONObject().apply {
                                        put(
                                            "shop",
                                            JSONObject().apply {
                                                put(
                                                    "daily_game",
                                                    JSONObject().apply {
                                                        put("push", notifyPush)
                                                        put("in_app", notifyPush)
                                                        put("email", v)
                                                    },
                                                )
                                            },
                                        )
                                    },
                                )
                            } catch (_: Exception) {}
                        }
                    },
                    colors =
                        SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = palette.accent,
                        ),
                )
            }
        }
    }
}
