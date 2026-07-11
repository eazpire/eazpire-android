package com.eazpire.creator.ui.creator

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eazpire.creator.EazColors
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.i18n.TranslationStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private data class LevelThreshold(val level: Int, val xpRequired: Int)

private data class LevelEazRow(val level: Int, val dailyEaz: Int, val maxEaz: Int)

private data class LevelGridItem(
    val level: Int,
    val state: String,
    val name: String,
    val meta: String,
    val badge: String?,
)

@Composable
fun CreatorSettingsLevelPanel(
    ownerId: String,
    api: CreatorApi,
    translationStore: TranslationStore,
    modifier: Modifier = Modifier,
    refreshKey: Int = 0,
) {
    var isLoading by remember { mutableStateOf(true) }
    var levelData by remember { mutableStateOf<JSONObject?>(null) }

    LaunchedEffect(ownerId, refreshKey) {
        val resolvedOwnerId = ownerId.trim()
        if (resolvedOwnerId.isBlank()) {
            levelData = null
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        try {
            levelData = withContext(Dispatchers.IO) { api.getLevel(resolvedOwnerId) }
        } catch (_: Exception) {
            levelData = null
        } finally {
            isLoading = false
        }
    }

    if (isLoading) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = EazColors.Orange, modifier = Modifier.padding(24.dp))
        }
        return
    }

    val data = levelData
    if (data == null || !data.optBoolean("ok", false)) {
        Text(
            text = translationStore.t("creator.level_panel.login_text", "You need to be logged in to see your Creator level."),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f),
            modifier = modifier,
        )
        return
    }

    val currentLevel = data.optInt("current_level", data.optInt("level", 1)).coerceAtLeast(1)
    val totalXp = data.optInt("total_xp", 0)
    val thresholds = parseThresholds(data.optJSONArray("level_thresholds") ?: data.optJSONArray("thresholds"))
    val levelEazRows = parseLevelEazRows(data.optJSONArray("level_eaz_by_level"))
    val maxXpLevel = thresholds.maxOfOrNull { it.level }?.coerceAtLeast(currentLevel) ?: currentLevel
    val currentThreshold = thresholdForLevel(currentLevel, thresholds)
    val nextThresholdRaw = thresholdForLevel(currentLevel + 1, thresholds)
    val hasNext = currentLevel < maxXpLevel && (
        nextThresholdRaw > currentThreshold ||
            thresholds.any { it.level == currentLevel + 1 }
        )
    val nextThreshold = if (hasNext) nextThresholdRaw else null
    val xpInLevel = (totalXp - currentThreshold).coerceAtLeast(0)
    val denom = if (nextThreshold != null) (nextThreshold - currentThreshold).coerceAtLeast(0) else 0
    val progress = if (nextThreshold != null && denom > 0) {
        (xpInLevel.toFloat() / denom.toFloat()).coerceIn(0f, 1f)
    } else {
        1f
    }
    val xpNeeded = if (nextThreshold != null) (nextThreshold - totalXp).coerceAtLeast(0) else 0
    val tierName = tierDisplayName(currentLevel, translationStore)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = translationStore.t("creator.level_panel.title", "Creator Level"),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )
            Text(
                text = translationStore.t("creator.level_panel.subtitle", "Level up and unlock new features"),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(EazColors.Orange.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .border(1.dp, EazColors.Orange.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Star, contentDescription = null, tint = EazColors.Orange, modifier = Modifier.size(36.dp))
            Column {
                Text(
                    text = translationStore.t("creator.level_panel.level_label", "Level"),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = currentLevel.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = EazColors.Orange,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = tierName,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = translationStore.t("creator.level_panel.xp_label", "XP"),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Text(
                    text = if (nextThreshold != null && denom > 0) {
                        val tpl = translationStore.t(
                            "creator.level_panel.xp_remaining_tpl",
                            "{{ current }} / {{ total }} XP"
                        )
                        tpl
                            .replace("{{ current }}", xpInLevel.toString())
                            .replace("{{ total }}", denom.toString())
                    } else {
                        translationStore.t("creator.level_panel.xp_max_title", "Max level reached")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
                color = EazColors.Orange,
                trackColor = Color.White.copy(alpha = 0.12f),
            )
            Text(
                text = if (nextThreshold != null && xpNeeded > 0) {
                    val tpl = translationStore.t(
                        "creator.level_panel.xp_remaining",
                        "{{ count }} XP until Level {{ level }}"
                    )
                    tpl
                        .replace("{{ count }}", xpNeeded.toString())
                        .replace("{{ level }}", (currentLevel + 1).toString())
                } else {
                    translationStore.t("creator.level_panel.xp_max_title", "Max level reached")
                },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
            )
        }

        val featureTitles = buildFeatureTitles(data, translationStore)
        if (featureTitles.isNotEmpty()) {
            Text(
                text = translationStore.t("creator.level_panel.current_features", "Features for this level"),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
            featureTitles.forEach { title ->
                LevelFeatureCard(title = title)
            }
        }

        val gridItems = buildLevelGridItems(
            currentLevel = currentLevel,
            maxLevels = maxXpLevel,
            thresholds = thresholds,
            levelEazRows = levelEazRows,
            data = data,
            translationStore = translationStore,
        )
        Text(
            text = translationStore.t("creator.level_panel.upcoming_levels", "Upcoming Levels"),
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
        )
        gridItems.forEach { item ->
            LevelGridCard(item = item)
        }
    }
}

@Composable
private fun LevelFeatureCard(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(10.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Star, contentDescription = null, tint = EazColors.Orange, modifier = Modifier.size(18.dp))
        Text(text = title, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f))
    }
}

@Composable
private fun LevelGridCard(item: LevelGridItem) {
    val bg = when (item.state) {
        "current" -> EazColors.Orange.copy(alpha = 0.18f)
        "unlocked" -> Color.White.copy(alpha = 0.06f)
        else -> Color.White.copy(alpha = 0.03f)
    }
    val border = when (item.state) {
        "current" -> EazColors.Orange.copy(alpha = 0.45f)
        else -> Color.White.copy(alpha = 0.1f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(10.dp))
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.state == "locked") {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White.copy(alpha = 0.45f), modifier = Modifier.size(20.dp))
        } else {
            Icon(Icons.Default.Star, contentDescription = null, tint = EazColors.Orange, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.level.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    color = EazColors.Orange,
                    fontWeight = FontWeight.Bold,
                )
                Text(text = item.name, style = MaterialTheme.typography.bodyMedium, color = Color.White)
                item.badge?.let { badge ->
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = EazColors.Orange,
                        modifier = Modifier
                            .background(EazColors.Orange.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = item.meta,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun parseThresholds(arr: JSONArray?): List<LevelThreshold> {
    if (arr == null) return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val row = arr.optJSONObject(i) ?: continue
            add(
                LevelThreshold(
                    level = row.optInt("level", 0),
                    xpRequired = row.optInt("xp_required", 0),
                )
            )
        }
    }
}

private fun parseLevelEazRows(arr: JSONArray?): List<LevelEazRow> {
    if (arr == null) return emptyList()
    return buildList {
        for (i in 0 until arr.length()) {
            val row = arr.optJSONObject(i) ?: continue
            add(
                LevelEazRow(
                    level = row.optInt("level", 0),
                    dailyEaz = row.optInt("daily_eaz", 0),
                    maxEaz = row.optInt("max_eaz", 0),
                )
            )
        }
    }
}

private fun thresholdForLevel(level: Int, thresholds: List<LevelThreshold>): Int =
    thresholds.firstOrNull { it.level == level }?.xpRequired ?: 0

private fun defaultLevelEaz(level: Int): LevelEazRow {
    val l = level.coerceAtLeast(1)
    return when {
        l <= 1 -> LevelEazRow(1, 0, 0)
        l == 2 -> LevelEazRow(2, 50, 50)
        else -> LevelEazRow(l, 60 + (l - 3) * 10, 80 + (l - 3) * 30)
    }
}

private fun eazLookup(level: Int, rows: List<LevelEazRow>): LevelEazRow {
    val hit = rows.firstOrNull { it.level == level }
    if (hit != null && (hit.dailyEaz > 0 || hit.maxEaz > 0)) return hit
    return defaultLevelEaz(level)
}

private fun tierDisplayName(level: Int, translationStore: TranslationStore): String =
    translationStore.t("creator.overview.level_names.$level", "Level $level")

private fun lockedLevelLabel(level: Int, translationStore: TranslationStore): String {
    val tpl = translationStore.t("creator.level_panel.level_locked_tpl", "Level {{ n }}")
    return tpl.replace("{{ n }}", level.toString())
}

private fun buildFeatureTitles(data: JSONObject, translationStore: TranslationStore): List<String> {
    val trialMode = data.optBoolean("trial_mode", false)
    val eazWallet = data.optBoolean("eaz_wallet_active", false)
    if (trialMode && !eazWallet) {
        var gen = data.optInt("trial_generate_cap", 5)
        var upl = data.optInt("trial_upload_cap", 20)
        if (gen <= 0) gen = 5
        if (upl <= 0) upl = 20
        val t1 = translationStore.t(
            "creator.level_panel.feature_starter_generations_tpl",
            "Up to {{ count }} Starter Pack generations"
        ).replace("{{ count }}", gen.toString())
        val t2 = translationStore.t(
            "creator.level_panel.feature_starter_uploads_tpl",
            "Up to {{ count }} Starter Pack uploads"
        ).replace("{{ count }}", upl.toString())
        return listOf(t1, t2)
    }

    val benefits = data.optJSONObject("benefits")
    val daily = benefits?.optInt("daily_eaz", 0) ?: 0
    val max = benefits?.optInt("max_eaz", 0) ?: 0
    val features = mutableListOf<String>()
    if (daily > 0) {
        features += translationStore.t(
            "creator.level_panel.feature_daily_tpl",
            "{{ count }} EAZ daily (free pool)"
        ).replace("{{ count }}", daily.toString())
    }
    if (max > 0) {
        features += translationStore.t(
            "creator.level_panel.feature_max_tpl",
            "{{ max }} EAZ max free pool"
        ).replace("{{ max }}", max.toString())
    }
    if (features.isEmpty()) {
        val lvl = data.optInt("current_level", 2).coerceAtLeast(2)
        val fb = defaultLevelEaz(lvl)
        features += translationStore.t(
            "creator.level_panel.feature_daily_tpl",
            "{{ count }} EAZ daily (free pool)"
        ).replace("{{ count }}", fb.dailyEaz.toString())
        features += translationStore.t(
            "creator.level_panel.feature_max_tpl",
            "{{ max }} EAZ max free pool"
        ).replace("{{ max }}", fb.maxEaz.toString())
    }
    return features
}

private fun buildLevelGridItems(
    currentLevel: Int,
    maxLevels: Int,
    thresholds: List<LevelThreshold>,
    levelEazRows: List<LevelEazRow>,
    data: JSONObject,
    translationStore: TranslationStore,
): List<LevelGridItem> {
    var genCap = data.optInt("trial_generate_cap", 5)
    var upCap = data.optInt("trial_upload_cap", 20)
    if (genCap <= 0) genCap = 5
    if (upCap <= 0) upCap = 20

    return (1..maxLevels).map { level ->
        val state = when {
            level < currentLevel -> "unlocked"
            level == currentLevel -> "current"
            else -> "locked"
        }
        val meta = if (level == 1) {
            translationStore.t(
                "creator.level_panel.level_grid_starter_tpl",
                "{{ gens }} generations · {{ uploads }} uploads · Starter Pack"
            )
                .replace("{{ gens }}", genCap.toString())
                .replace("{{ uploads }}", upCap.toString())
        } else {
            val ez = eazLookup(level, levelEazRows)
            translationStore.t(
                "creator.level_panel.level_grid_daily_max_tpl",
                "Daily {{ daily }} · Max {{ max }}"
            )
                .replace("{{ daily }}", ez.dailyEaz.toString())
                .replace("{{ max }}", ez.maxEaz.toString())
        }
        val name = if (state == "locked") {
            lockedLevelLabel(level, translationStore)
        } else {
            tierDisplayName(level, translationStore)
        }
        val badge = if (state == "current") {
            translationStore.t("creator.level_panel.badge_current", "Current")
        } else {
            null
        }
        LevelGridItem(level = level, state = state, name = name, meta = meta, badge = badge)
    }
}
