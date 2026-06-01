package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private fun journeyHrefFromPresentationJson(pres: JSONObject, fallbackPath: String?): String? {
    val wb = pres.optString("web_href", "").trim()
    val base = wb.ifBlank { fallbackPath?.takeIf { it.startsWith("/") } ?: "" }.ifBlank { return null }
    var path = base
    val query = pres.optString("query", "").trim()
    if (query.isNotEmpty()) {
        val qp = query.removePrefix("?")
        path += if ('?' in path) "&$qp" else "?$qp"
    }
    val anchor = pres.optString("anchor", "").trim()
    if (anchor.isNotEmpty()) path += if (anchor.startsWith('#')) anchor else "#$anchor"
    return path
}

private data class JourneyTodo(
    val id: String,
    val title: String,
    val icon: String,
    val xp: Int,
    val link: String?,
    val action: String?,
    val completed: Boolean,
    val countCurrent: Int?,
    val countTarget: Int?
)

private fun parseJourneyTodo(
    tObj: JSONObject,
    t: (String, String) -> String
): JourneyTodo {
    val id = tObj.optString("id", "")
    val pres = when {
        tObj.isNull("presentation") -> JSONObject()
        else -> tObj.optJSONObject("presentation") ?: JSONObject()
    }
    val fb = TODO_CONFIG[id]

    val icon =
        pres.optString("icon", "").takeUnless { it.isBlank() } ?: fb?.first ?: "✓"

    val shopKey =
        pres.optString("title_shopify_key", "").takeUnless { it.isBlank() }
            ?: fb?.third
            ?: "creator.overview.todos_error"
    val label =
        t(shopKey, id.replace("todo_", "").replace('_', ' ').replaceFirstChar { it.uppercase() })

    val fbPath = fb?.second?.takeIf { it.startsWith("/") }
    val path =
        journeyHrefFromPresentationJson(pres, fbPath)?.takeIf { it.startsWith("/") }
            ?: fbPath

    return JourneyTodo(
        id = id,
        title = label,
        icon = icon,
        xp = tObj.optInt("xp", 10),
        link = path,
        action = null,
        completed = false,
        countCurrent = tObj.optInt("count_current", -1).takeIf { it >= 0 },
        countTarget = tObj.optInt("count_target", -1).takeIf { it >= 0 }
    )
}

private val TODO_CONFIG = mapOf(
    "todo_first_design" to Triple("💾", "/?eaz_open_shop_create=1", "creator.overview.todo_first_design"),
    "todo_first_product" to Triple("📦", "/pages/my-creations", "creator.overview.todo_first_product"),
    "todo_five_designs" to Triple("🎨", "/?eaz_open_shop_create=1", "creator.overview.todo_five_designs"),
    "todo_twenty_products" to Triple("🚀", "/pages/my-creations", "creator.overview.todo_twenty_products"),
    "todo_first_transaction" to Triple("🛒", "#", "creator.overview.todo_first_transaction"),
    "todo_become_creator" to Triple("⭐", "/pages/creator-settings", "creator.overview.todo_become_creator"),
    "todo_creator_name" to Triple("👤", "/pages/creator-settings", "creator.overview.todo_creator_name"),
    "todo_generate_design" to Triple("🎨", "/?eaz_open_shop_create=1", "creator.overview.todo_generate_design"),
    "todo_activate_design" to Triple("✅", "/pages/my-creations", "creator.overview.todo_activate_design"),
    "todo_publish_product" to Triple("🚀", "/pages/my-creations", "creator.overview.todo_publish_product"),
    "todo_upload_design" to Triple("📤", "/pages/my-creations", "creator.overview.todo_upload_design"),
    "todo_create_hero" to Triple("🖼️", "/pages/content-creation", "creator.overview.todo_create_hero"),
    "todo_create_avatar" to Triple("👤", "/pages/creator-settings#creator-image", "creator.overview.todo_create_avatar"),
    "todo_create_cover" to Triple("🎨", "/pages/creator-settings#cover-image", "creator.overview.todo_create_cover"),
    "todo_remix_design" to Triple("🔄", "/pages/inspirations", "creator.overview.todo_remix_design"),
    "todo_invite_user" to Triple("👥", "/pages/creator-settings#referral", "creator.overview.todo_invite_user")
)

@Composable
fun CreatorJourneySection(
    translationStore: TranslationStore,
    ownerId: String?,
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var progressPercent by remember { mutableStateOf(0) }
    var completedCount by remember { mutableStateOf(0) }
    var totalCount by remember { mutableStateOf(0) }
    var openTodos by remember { mutableStateOf<List<JourneyTodo>>(emptyList()) }
    var completedTodos by remember { mutableStateOf<List<JourneyTodo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var activeTab by remember { mutableStateOf("open") }
    val t = { k: String, d: String -> translationStore.t(k, d) }

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
                    val completedIds = (r.optJSONArray("completed_todos") ?: JSONArray()).let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }.toSet()
                    }
                    val todosArr = r.optJSONArray("todos") ?: JSONArray()
                    val open = mutableListOf<JourneyTodo>()
                    val completed = mutableListOf<JourneyTodo>()
                    for (i in 0 until todosArr.length()) {
                        val tObj = todosArr.getJSONObject(i)
                        val id = tObj.optString("id", "")
                        val isClaimed = completedIds.contains(id) || tObj.optBoolean("completed", false)
                        val todo = parseJourneyTodo(tObj, t).copy(completed = isClaimed)
                        if (isClaimed) completed.add(todo) else open.add(todo)
                    }
                    openTodos = open
                    completedTodos = completed
                }
            } catch (_: Exception) {}
            isLoading = false
        }
    } else {
        isLoading = false
    }

    val containerShape = RoundedCornerShape(16.dp)
    val headerShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    val glassContainerBg = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F172A).copy(alpha = 0.72f),
            Color(0xFF0B1220).copy(alpha = 0.68f),
            Color(0xFF070B14).copy(alpha = 0.7f)
        )
    )
    val glassHeaderBg = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF02060F).copy(alpha = 0.88f),
            Color(0xFF050A16).copy(alpha = 0.9f)
        )
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(containerShape)
            .background(glassContainerBg)
            .border(1.dp, Color.White.copy(alpha = 0.12f), containerShape)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(headerShape)
                    .background(glassHeaderBg)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
            Text(
                text = t("creator.overview.onboarding_title", "Creator Journey"),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = com.eazpire.creator.EazColors.Orange
            )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.08f))
            )
        }
        Column(modifier = Modifier.padding(16.dp)) {
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
            text = if (isLoading) t("creator.overview.loading", "Loading…")
            else "${completedCount}/${totalCount}",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.8f)
        )

        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            JourneyTab(
                label = t("creator.overview.tab_open", "Open"),
                count = openTodos.size,
                active = activeTab == "open",
                onClick = { activeTab = "open" }
            )
            JourneyTab(
                label = t("creator.overview.tab_completed", "Completed"),
                count = completedTodos.size,
                active = activeTab == "completed",
                onClick = { activeTab = "completed" }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .heightIn(min = 80.dp)
        ) {
            if (activeTab == "open") {
                if (openTodos.isEmpty() && !isLoading) {
                    Text(
                        text = t("creator.overview.no_completed_yet", "Complete tasks to earn XP!"),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                } else {
                    openTodos.forEach { todo ->
                        JourneyTodoItem(todo = todo, t = t, isCompleted = false)
                    }
                }
            } else {
                if (completedTodos.isEmpty() && !isLoading) {
                    Text(
                        text = t("creator.overview.no_completed_todos", "No completed tasks yet"),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                } else {
                    completedTodos.forEach { todo ->
                        JourneyTodoItem(todo = todo, t = t, isCompleted = true)
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun CreatorJourneyGuestSection(
    translationStore: TranslationStore,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = { k: String, d: String -> translationStore.t(k, d) }
    val containerShape = RoundedCornerShape(16.dp)
    val headerShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    val glassContainerBg = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F172A).copy(alpha = 0.72f),
            Color(0xFF0B1220).copy(alpha = 0.68f),
            Color(0xFF070B14).copy(alpha = 0.7f),
        ),
    )
    val glassHeaderBg = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF02060F).copy(alpha = 0.88f),
            Color(0xFF050A16).copy(alpha = 0.9f),
        ),
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(containerShape)
            .background(glassContainerBg)
            .border(1.dp, Color.White.copy(alpha = 0.12f), containerShape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(headerShape)
                .background(glassHeaderBg)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = t("creator.overview.guest_journey_title", "Start Your Journey"),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = com.eazpire.creator.EazColors.Orange,
            )
            Text(
                text = "0/1",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.08f)),
        )
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .clickable(onClick = onLoginClick)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = "🚀", style = MaterialTheme.typography.titleLarge)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = t("creator.overview.todo_login", "Log in to start creating"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                    Text(
                        text = t("creator.overview.todo_login_reward", "🔓 Unlock Level 1"),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
                Text(text = "→", color = Color.White.copy(alpha = 0.6f))
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = t("creator.overview.guest_cta_text", "Don't have an account yet?"),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                )
                Text(
                    text = t("creator.overview.guest_cta_register", "Create free account"),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = com.eazpire.creator.EazColors.Orange,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clickable(onClick = onLoginClick),
                )
            }
        }
    }
}

@Composable
private fun JourneyTab(
    label: String,
    count: Int,
    active: Boolean,
    onClick: () -> Unit
) {
    val bg = if (active) com.eazpire.creator.EazColors.Orange.copy(alpha = 0.3f)
    else Color.White.copy(alpha = 0.08f)
    Row(
        modifier = Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) Color.White else Color.White.copy(alpha = 0.8f)
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = if (active) Color.White else Color.White.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun JourneyTodoItem(
    todo: JourneyTodo,
    t: (String, String) -> String,
    isCompleted: Boolean
) {
    val context = LocalContext.current
    val xpText = t("creator.overview.todo_xp_reward", "+%{xp} XP").replace("%{xp}", todo.xp.toString())
    val countText = if (todo.countCurrent != null && todo.countTarget != null) {
        " ${todo.countCurrent}/${todo.countTarget}"
    } else ""

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .then(
                if (todo.link != null) Modifier.clickable {
                    try {
                        val url = "https://www.eazpire.com${todo.link}"
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                    } catch (_: Exception) {}
                } else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = todo.icon, style = MaterialTheme.typography.titleMedium)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = todo.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Text(
                text = xpText + countText + if (isCompleted) " ✓" else "",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
        if (todo.link != null && !isCompleted) {
            Text(
                text = "→",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}
