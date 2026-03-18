package com.eazpire.creator.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eazpire.creator.api.CreatorApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private val MascotBg = Color(0xFF1F2937)
private val MascotCard = Color(0x0DFFFFFF)
private val MascotAccent = Color(0xFFF97316)
private val MascotText = Color(0xFFE5E7EB)
private val MascotMuted = Color(0xFF9CA3AF)
private val MascotGold = Color(0xFFFBBF24)

data class MascotData(
    val id: Int,
    val name: String,
    val nickname: String?,
    val color: String,
    val level: Int,
    val xp: Int,
    val isActive: Boolean,
    val typeCategory: String?
)

data class MascotQuest(
    val id: String,
    val title: String,
    val description: String,
    val progress: Int,
    val countTarget: Int,
    val xpReward: Int,
    val completed: Boolean,
    val claimable: Boolean
)

data class LockedMascot(
    val id: Int,
    val name: String,
    val unlockLevel: Int
)

@Composable
fun EazyMascotTabView(
    ownerId: String?,
    api: CreatorApi,
    t: (String, String) -> String,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var mascots by remember { mutableStateOf<List<MascotData>>(emptyList()) }
    var quests by remember { mutableStateOf<List<MascotQuest>>(emptyList()) }
    var lockedMascots by remember { mutableStateOf<List<LockedMascot>>(emptyList()) }
    var mood by remember { mutableStateOf<JSONObject?>(null) }
    var nextLevels by remember { mutableStateOf<Map<Int, JSONObject>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ownerId) {
        if (ownerId.isNullOrBlank()) {
            loading = false
            error = t("eazy_chat.mascot_login_required", "Sign in to see your mascot")
            return@LaunchedEffect
        }
        loading = true
        error = null
        try {
                var inv = withContext(Dispatchers.IO) { api.mascotInventory(ownerId) }
                if (!inv.optBoolean("ok", false) && inv.optString("error") == "missing_owner_id") {
                    error = t("eazy_chat.mascot_login_required", "Sign in to see your mascot")
                    loading = false
                    return@LaunchedEffect
                }
                if (!inv.optBoolean("ok", false) || (inv.optJSONArray("mascots")?.length() ?: 0) == 0) {
                    val init = withContext(Dispatchers.IO) { api.mascotInit(ownerId!!) }
                    if (init.optBoolean("ok", false)) {
                        inv = withContext(Dispatchers.IO) { api.mascotInventory(ownerId) }
                    }
                }
                if (inv.optBoolean("ok", false)) {
                    mascots = parseMascots(inv.optJSONArray("mascots"))
                    lockedMascots = parseLockedMascots(inv.optJSONArray("locked_mascots"))
                    mood = inv.optJSONObject("mood")
                    val nxt = inv.optJSONObject("next_levels")
                    nextLevels = mutableMapOf<Int, JSONObject>().apply {
                        nxt?.keys()?.forEach { key ->
                            val obj = nxt.optJSONObject(key)
                            if (obj != null) {
                                key.toIntOrNull()?.let { put(it, obj) }
                            }
                        }
                    }
                    val active = mascots.find { it.isActive }
                if (active != null) {
                    val qr = withContext(Dispatchers.IO) { api.mascotQuests(ownerId) }
                    if (qr.optBoolean("ok", false)) {
                        quests = parseQuests(qr.optJSONArray("quests"))
                    }
                }
            }
        } catch (_: Exception) {
            error = t("eazy_chat.chat_network_error_retry", "Network error. Please try again.")
        }
        loading = false
    }

    if (loading) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = MascotAccent, modifier = Modifier.size(32.dp))
                Text(t("eazy_chat.mascot_loading", "Loading mascots…"), style = MaterialTheme.typography.bodyMedium, color = MascotMuted)
            }
        }
        return
    }

    if (error != null) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = error!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MascotMuted,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val active = mascots.find { it.isActive } ?: mascots.firstOrNull()
    if (active == null) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = t("eazy_chat.mascot_empty", "No mascot available."),
                style = MaterialTheme.typography.bodyMedium,
                color = MascotMuted,
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val nxt = nextLevels[active.id]
    val progressPct = nxt?.optDouble("progress", 0.0)?.let { (it * 100).toInt().coerceIn(0, 100) } ?: 0
    val nextLevelXp = nxt?.optInt("next_level_xp", 0) ?: 0
    val moodStr = mood?.optString("mood", "happy") ?: "happy"
    val moodEmoji = mapOf("happy" to "😊", "excited" to "🤩", "chill" to "😎", "sleepy" to "😴", "focused" to "🧐")[moodStr] ?: "😊"
    val moodLabel = mapOf("happy" to "Happy", "excited" to "Excited", "chill" to "Chill", "sleepy" to "Sleepy", "focused" to "Focused")[moodStr] ?: moodStr

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Preview (like web eazy-mascot-preview)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MascotCard)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(60.dp))
                    .background(parseColor(active.color))
                    .clickable { /* pet */ },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (active.nickname ?: active.name).take(1).uppercase(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )
            }
            Text(
                text = active.nickname ?: active.name,
                style = MaterialTheme.typography.titleMedium,
                color = parseColor(active.color)
            )
            Text(
                text = "$moodEmoji $moodLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MascotMuted
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(parseColor(active.color))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("Lv. ${active.level}", style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MascotMuted.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressPct / 100f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(parseColor(active.color))
                    )
                }
                Text(
                    text = "${active.xp} XP${if (nextLevelXp > 0) " / $nextLevelXp" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MascotMuted
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp)
            ) {
                listOf("👋" to "pet", "🍬" to "feed", "🎮" to "play").forEach { (emoji, action) ->
                    IconButton(
                        onClick = {
                            ownerId?.let { oid ->
                                scope.launch {
                                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                                        api.mascotInteract(oid, action)
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MascotMuted.copy(alpha = 0.2f))
                    ) {
                        Text(text = emoji, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        // Quests
        if (quests.isNotEmpty()) {
            EazyMascotSection(
                title = t("eazy_chat.mascot_quests", "Quests"),
                count = "${quests.count { !it.completed }} ${t("eazy_chat.mascot_open", "open")}",
                accent = MascotAccent,
                defaultOpen = true
            ) {
                quests.forEach { q ->
                    MascotQuestCard(
                        quest = q,
                        t = t,
                        onClaim = { /* TODO */ }
                    )
                }
            }
        }

        // Owned mascots
        EazyMascotSection(
            title = t("eazy_chat.mascot_owned", "Your mascots"),
            count = "${mascots.size}",
            accent = Color(0xFF3B82F6),
            defaultOpen = true
        ) {
            mascots.forEach { m ->
                MascotCardItem(
                    mascot = m,
                    t = t,
                    onClick = {
                        if (!m.isActive && ownerId != null) {
                            scope.launch {
                                kotlinx.coroutines.withContext(Dispatchers.IO) {
                                    api.mascotSelect(ownerId!!, m.id)
                                }
                                loading = true
                                val inv = kotlinx.coroutines.withContext(Dispatchers.IO) { api.mascotInventory(ownerId) }
                                if (inv.optBoolean("ok", false)) {
                                    mascots = parseMascots(inv.optJSONArray("mascots"))
                                    val active = mascots.find { it.isActive }
                                    if (active != null) {
                                        val qr = kotlinx.coroutines.withContext(Dispatchers.IO) { api.mascotQuests(ownerId) }
                                        if (qr.optBoolean("ok", false)) quests = parseQuests(qr.optJSONArray("quests"))
                                    }
                                }
                                loading = false
                            }
                        }
                    }
                )
            }
        }

        // Locked mascots
        if (lockedMascots.isNotEmpty()) {
            EazyMascotSection(
                title = t("eazy_chat.mascot_locked", "Unlockable"),
                count = "${lockedMascots.size}",
                accent = MascotMuted,
                defaultOpen = false
            ) {
                lockedMascots.forEach { m ->
                    LockedMascotCard(mascot = m, t = t)
                }
            }
        }
    }
}

@Composable
private fun EazyMascotSection(
    title: String,
    count: String,
    accent: Color,
    defaultOpen: Boolean,
    content: @Composable () -> Unit
) {
    var isOpen by remember { mutableStateOf(defaultOpen) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.06f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isOpen = !isOpen }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MascotMuted)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(count, style = MaterialTheme.typography.labelSmall, color = MascotMuted)
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MascotMuted,
                    modifier = Modifier.rotate(if (isOpen) 180f else 0f)
                )
            }
        }
        if (isOpen) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun MascotQuestCard(
    quest: MascotQuest,
    t: (String, String) -> String,
    onClaim: () -> Unit
) {
    val pct = if (quest.countTarget > 0) (quest.progress.toFloat() / quest.countTarget * 100).toInt().coerceIn(0, 100) else 0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (quest.claimable) MascotGold.copy(alpha = 0.1f) else MascotCard)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (quest.completed) "✅ " else "" + quest.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MascotText
            )
            Text("+${quest.xpReward} XP", style = MaterialTheme.typography.labelMedium, color = MascotGold)
        }
        Text(quest.description, style = MaterialTheme.typography.bodySmall, color = MascotMuted)
        if (!quest.completed) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MascotMuted.copy(alpha = 0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(pct / 100f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MascotGold)
                    )
                }
                Text("${quest.progress}/${quest.countTarget}", style = MaterialTheme.typography.labelSmall, color = MascotMuted)
            }
            if (quest.claimable) {
                androidx.compose.material3.Button(
                    onClick = onClaim,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MascotGold, contentColor = Color.Black)
                ) {
                    Text(t("eazy_chat.mascot_claim", "Claim reward"))
                }
            }
        }
    }
}

@Composable
private fun MascotCardItem(
    mascot: MascotData,
    t: (String, String) -> String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (mascot.isActive) parseColor(mascot.color).copy(alpha = 0.1f) else MascotCard)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(parseColor(mascot.color)),
            contentAlignment = Alignment.Center
        ) {
            Text((mascot.nickname ?: mascot.name).take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(mascot.nickname ?: mascot.name, style = MaterialTheme.typography.bodyMedium, color = MascotText)
            Text(
                typeLabel(mascot.typeCategory),
                style = MaterialTheme.typography.labelSmall,
                color = MascotMuted
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(parseColor(mascot.color))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text("Lv.${mascot.level}", style = MaterialTheme.typography.labelSmall, color = Color.White)
        }
        if (mascot.isActive) {
            Text(t("eazy_chat.mascot_active", "Active"), style = MaterialTheme.typography.labelSmall, color = MascotAccent)
        }
    }
}

@Composable
private fun LockedMascotCard(
    mascot: LockedMascot,
    t: (String, String) -> String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MascotCard)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = MascotMuted, modifier = Modifier.size(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(mascot.name, style = MaterialTheme.typography.bodyMedium, color = MascotText)
            Text(
                t("eazy_chat.mascot_unlock_level", "Level %d required").format(mascot.unlockLevel),
                style = MaterialTheme.typography.labelSmall,
                color = MascotMuted
            )
        }
    }
}

private fun parseColor(hex: String): Color {
    val s = hex.trim().removePrefix("#")
    return when (s.length) {
        6 -> Color(android.graphics.Color.parseColor("#$s"))
        8 -> Color(android.graphics.Color.parseColor("#$s"))
        else -> MascotAccent
    }
}

private fun typeLabel(type: String?): String = when (type) {
    "allrounder" -> "Allrounder"
    "shop" -> "Shop"
    "creator" -> "Creator"
    "community" -> "Community"
    "visual" -> "Visual"
    "creator_shop" -> "Creator+Shop"
    "creator_visual" -> "Creator+Visual"
    "community_shop" -> "Community+Shop"
    "allrounder_plus" -> "Allrounder+"
    else -> type ?: ""
}

private fun parseMascots(arr: JSONArray?): List<MascotData> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        MascotData(
            id = o.optInt("id", 0),
            name = o.optString("name", ""),
            nickname = o.optString("nickname", "").takeIf { it.isNotBlank() },
            color = o.optString("color", "#f97316"),
            level = o.optInt("level", 1),
            xp = o.optInt("xp", 0),
            isActive = o.optBoolean("is_active", false),
            typeCategory = o.optString("type_category", "").takeIf { it.isNotBlank() }
        )
    }
}

private fun parseQuests(arr: JSONArray?): List<MascotQuest> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        MascotQuest(
            id = o.optString("id", ""),
            title = o.optString("title", ""),
            description = o.optString("description", ""),
            progress = o.optInt("progress", 0),
            countTarget = o.optInt("count_target", 0),
            xpReward = o.optInt("xp_reward", 0),
            completed = o.optBoolean("completed", false),
            claimable = o.optBoolean("claimable", false)
        )
    }
}

private fun parseLockedMascots(arr: JSONArray?): List<LockedMascot> {
    if (arr == null) return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        LockedMascot(
            id = o.optInt("id", 0),
            name = o.optString("name", ""),
            unlockLevel = o.optInt("unlock_level", 0)
        )
    }
}
