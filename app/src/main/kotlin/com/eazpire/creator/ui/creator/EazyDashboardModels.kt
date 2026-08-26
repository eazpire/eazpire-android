package com.eazpire.creator.ui.creator

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

data class DashboardV5State(
    val activeLayoutId: String,
    val layouts: List<DashboardLayout>,
    val widgets: List<DashboardWidgetSpec>,
    val templates: List<Pair<String, String>>,
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
    val templates = ArrayList<Pair<String, String>>(tplArr.length())
    for (i in 0 until tplArr.length()) {
        val t = tplArr.optJSONObject(i) ?: continue
        templates.add(t.optString("id") to t.optString("title"))
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
