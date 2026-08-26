package com.eazpire.creator.ui.creator

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

data class DashboardWidgetSpec(
    val id: String,
    val titleKey: String,
    val trackingRequired: Boolean,
    val configurable: Boolean,
)

data class DashboardWidgetPos(
    val id: String,
    val visible: Boolean,
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
)

data class DashboardSurface(
    val columns: Int,
    val widgets: List<DashboardWidgetPos>,
)

data class DashboardLayout(
    val id: String,
    val title: String,
    val description: String,
    val version: Int,
    val desktop: DashboardSurface,
    val tablet: DashboardSurface,
    val mobile: DashboardSurface,
    val quickActionIds: List<String>,
)

data class DashboardTemplate(
    val id: String,
    val title: String,
    val description: String,
    val desktop: DashboardSurface,
    val tablet: DashboardSurface,
    val mobile: DashboardSurface,
)

data class DashboardV5State(
    val activeLayoutId: String,
    val layouts: List<DashboardLayout>,
    val widgets: List<DashboardWidgetSpec>,
    val templates: List<DashboardTemplate>,
    val designsGenerated: String,
    val designsUploaded: String,
    val productsOnline: String,
    val productsOffline: String,
    val heroesGenerated: String,
    val heroesOnline: String,
    val salesEazpire: String,
    val salesAmazon: String,
    val heroImpressions: String?,
    val heroClicks: String?,
    val tracking: Map<String, Boolean>,
)

private fun JSONObject.optSurface(key: String, fallbackCols: Int): DashboardSurface {
    val obj = optJSONObject(key) ?: return DashboardSurface(fallbackCols, emptyList())
    val cols = obj.optInt("columns", fallbackCols)
    val arr = obj.optJSONArray("widgets") ?: JSONArray()
    val list = ArrayList<DashboardWidgetPos>(arr.length())
    for (i in 0 until arr.length()) {
        val w = arr.optJSONObject(i) ?: continue
        list.add(
            DashboardWidgetPos(
                id = w.optString("id"),
                visible = w.optBoolean("visible", true),
                x = w.optInt("x"),
                y = w.optInt("y"),
                w = w.optInt("w", 2),
                h = w.optInt("h", 2),
            )
        )
    }
    return DashboardSurface(cols, list)
}

fun parseDashboardV5(raw: JSONObject): DashboardV5State? {
    if (!raw.optBoolean("ok", false)) return null
    val registry = raw.optJSONObject("registry") ?: JSONObject()
    val widgetArr = registry.optJSONArray("widgets") ?: JSONArray()
    val specs = ArrayList<DashboardWidgetSpec>(widgetArr.length())
    for (i in 0 until widgetArr.length()) {
        val w = widgetArr.optJSONObject(i) ?: continue
        specs.add(
            DashboardWidgetSpec(
                id = w.optString("id"),
                titleKey = w.optString("titleKey"),
                trackingRequired = w.optBoolean("trackingRequired"),
                configurable = w.optBoolean("configurable"),
            )
        )
    }
    val tplArr = registry.optJSONArray("templates") ?: JSONArray()
    val templates = ArrayList<DashboardTemplate>(tplArr.length())
    for (i in 0 until tplArr.length()) {
        val t = tplArr.optJSONObject(i) ?: continue
        templates.add(
            DashboardTemplate(
                id = t.optString("id"),
                title = t.optString("title"),
                description = t.optString("description"),
                desktop = t.optSurface("desktop", 12),
                tablet = t.optSurface("tablet", 8),
                mobile = t.optSurface("mobile", 4),
            )
        )
    }
    val layoutsArr = raw.optJSONArray("layouts") ?: JSONArray()
    val layouts = ArrayList<DashboardLayout>(layoutsArr.length())
    for (i in 0 until layoutsArr.length()) {
        val l = layoutsArr.optJSONObject(i) ?: continue
        val settings = l.optJSONObject("widgetSettings")
        val qa = settings?.optJSONObject("quick-actions")?.optJSONArray("visibleIds")
        val qaIds = ArrayList<String>()
        if (qa != null) {
            for (q in 0 until qa.length()) qaIds.add(qa.optString(q))
        }
        layouts.add(
            DashboardLayout(
                id = l.optString("id"),
                title = l.optString("title"),
                description = l.optString("description"),
                version = l.optInt("version", 1),
                desktop = l.optSurface("desktop", 12),
                tablet = l.optSurface("tablet", 8),
                mobile = l.optSurface("mobile", 4),
                quickActionIds = if (qaIds.isEmpty()) {
                    listOf("generator", "designs", "content", "automations", "products")
                } else qaIds,
            )
        )
    }
    val data = raw.optJSONObject("data") ?: JSONObject()
    val stats = data.optJSONObject("stats")
    val designs = stats?.optJSONObject("designs")
    val products = stats?.optJSONObject("products")
    val heroes = stats?.optJSONObject("heroes")
    val sales = data.optJSONObject("sales")
    val hero = data.optJSONObject("hero")
    val trackingObj = data.optJSONObject("tracking") ?: JSONObject()
    val tracking = mutableMapOf<String, Boolean>()
    trackingObj.keys().forEach { tracking[it] = trackingObj.optBoolean(it) }
    fun num(obj: JSONObject?, key: String): String {
        if (obj == null || !obj.has(key) || obj.isNull(key)) return "–"
        return obj.optInt(key, 0).toString()
    }
    return DashboardV5State(
        activeLayoutId = raw.optString("activeLayoutId"),
        layouts = layouts,
        widgets = specs,
        templates = templates,
        designsGenerated = num(designs, "generated"),
        designsUploaded = num(designs, "uploaded"),
        productsOnline = num(products, "online"),
        productsOffline = num(products, "offline"),
        heroesGenerated = num(heroes, "generated"),
        heroesOnline = num(heroes, "online"),
        salesEazpire = num(sales, "eazpire"),
        salesAmazon = if (sales == null || sales.isNull("amazon")) "–" else sales.optInt("amazon").toString(),
        heroImpressions = if (hero == null || hero.isNull("impressions")) null else hero.optInt("impressions").toString(),
        heroClicks = if (hero == null || hero.isNull("clicks")) null else hero.optInt("clicks").toString(),
        tracking = tracking,
    )
}

fun surfaceForWidth(widthDp: Float): String = when {
    widthDp < 700f -> "mobile"
    widthDp < 1100f -> "tablet"
    else -> "desktop"
}

fun DashboardLayout.surfaceNamed(name: String): DashboardSurface = when (name) {
    "mobile" -> mobile
    "tablet" -> tablet
    else -> desktop
}

fun DashboardLayout.withSurface(name: String, surface: DashboardSurface): DashboardLayout = when (name) {
    "mobile" -> copy(mobile = surface)
    "tablet" -> copy(tablet = surface)
    else -> copy(desktop = surface)
}

fun DashboardSurface.toJson(): JSONObject {
    val arr = JSONArray()
    widgets.forEach { w ->
        arr.put(
            JSONObject()
                .put("id", w.id)
                .put("visible", w.visible)
                .put("x", w.x)
                .put("y", w.y)
                .put("w", w.w)
                .put("h", w.h),
        )
    }
    return JSONObject().put("columns", columns).put("widgets", arr)
}

fun widgetsOverlap(a: DashboardWidgetPos, b: DashboardWidgetPos): Boolean {
    if (!a.visible || !b.visible || a.id == b.id) return false
    return a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y
}

fun compactWidgetsVertical(
    widgets: List<DashboardWidgetPos>,
    columns: Int,
    freezeId: String? = null,
): List<DashboardWidgetPos> {
    val visible = widgets.filter { it.visible }.map { it.copy() }
    val hidden = widgets.filter { !it.visible }.map { it.copy() }
    val frozen = if (freezeId != null) visible.filter { it.id == freezeId } else emptyList()
    val movable = (if (freezeId != null) visible.filter { it.id != freezeId } else visible)
        .sortedWith(compareBy<DashboardWidgetPos> { it.y }.thenBy { it.x })
    val placed = frozen.map { item ->
        item.copy(
            x = item.x.coerceIn(0, (columns - item.w).coerceAtLeast(0)),
            y = item.y.coerceAtLeast(0),
        )
    }.toMutableList()
    for (raw in movable) {
        var y = 0
        var placedItem = false
        while (!placedItem) {
            val probe = raw.copy(
                x = raw.x.coerceIn(0, (columns - raw.w).coerceAtLeast(0)),
                y = y,
            )
            if (placed.none { widgetsOverlap(probe, it) }) {
                placed.add(probe)
                placedItem = true
            } else {
                y += 1
                if (y > 200) {
                    placed.add(probe.copy(y = y))
                    placedItem = true
                }
            }
        }
    }
    return placed + hidden
}

fun resolveWidgetCollisions(
    widgets: List<DashboardWidgetPos>,
    movedId: String,
    columns: Int,
): List<DashboardWidgetPos> {
    val items = widgets.map { it.copy() }.toMutableList()
    val idx = items.indexOfFirst { it.id == movedId }
    if (idx < 0) return items
    val moved = items[idx]
    items[idx] = moved.copy(
        x = moved.x.coerceIn(0, (columns - moved.w).coerceAtLeast(0)),
        y = moved.y.coerceAtLeast(0),
    )
    var guard = 0
    var changed = true
    while (changed && guard++ < 200) {
        changed = false
        for (i in items.indices) {
            val item = items[i]
            if (item.id == movedId || !item.visible) continue
            val blockers = items.filter { widgetsOverlap(item, it) }
            if (blockers.isNotEmpty()) {
                val nextY = blockers.maxOf { it.y + it.h }
                if (item.y != nextY) {
                    items[i] = item.copy(y = nextY)
                    changed = true
                }
            }
        }
    }
    return compactWidgetsVertical(items, columns, movedId)
}

fun dashboardTitleCase(raw: String): String =
    raw.split(Regex("\\s+")).joinToString(" ") { word ->
        if (word.isEmpty()) word
        else word.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
        }
    }
