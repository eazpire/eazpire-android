package com.eazpire.creator.billing

import com.eazpire.creator.i18n.TranslationStore
import org.json.JSONObject

/**
 * EAZ feature costs — mirrors theme/assets/eaz-cost-catalog.js and src/features/billing/pricing.js.
 */
object EazCostCatalog {

    data class Item(
        val feature: String,
        val labelKey: String,
        val labelDefault: String,
        val defaultCost: Double
    )

    val items: List<Item> = listOf(
        Item("design_generate", "creator.settings.eaz_cost_design_generate", "Generate design", 10.0),
        Item("design_upload", "creator.settings.eaz_cost_design_upload", "Upload design", 1.0),
        Item("bg_remove", "creator.settings.eaz_cost_bg_remove", "Remove background", 0.2),
        Item("design_edit", "creator.settings.eaz_cost_design_edit", "Edit design", 0.3),
        Item("design_variation", "creator.settings.eaz_cost_design_variation", "Design variation", 0.2),
        Item("mockup_generate", "creator.settings.eaz_cost_mockup_generate", "Generate mockup", 0.1),
        Item("mockup_save", "creator.settings.eaz_cost_mockup_save", "Save mockup", 0.1),
        Item("hero_generate", "creator.settings.eaz_cost_hero_generate", "Hero image generation", 0.5),
        Item("hero_impression", "creator.settings.eaz_cost_hero_impression", "Hero impression", 0.01),
        Item("video_generate", "creator.settings.eaz_cost_video_generate", "Video generation", 2.0),
        Item("wardrobe_generate", "creator.settings.eaz_cost_wardrobe_generate", "Wardrobe generation", 0.5),
        Item("creator_image", "creator.settings.eaz_cost_creator_image", "Creator image", 5.0),
        Item("export_high_res", "creator.settings.eaz_cost_export_high_res", "High-res export", 1.0),
        Item("export_print", "creator.settings.eaz_cost_export_print", "Print export", 0.5)
    )

    fun defaultCost(feature: String): Double =
        items.find { it.feature == feature }?.defaultCost ?: 0.0

    fun resolveCost(balance: JSONObject?, feature: String): Double {
        val costs = balance?.optJSONObject("eaz_costs")
        if (costs != null && costs.has(feature)) {
            val n = costs.optDouble(feature, Double.NaN)
            if (!n.isNaN() && n >= 0) return n
        }
        return defaultCost(feature)
    }

    fun isFeatureActive(balance: JSONObject?, feature: String): Boolean {
        val active = balance?.optJSONObject("eaz_feature_active")
        if (active != null && active.has(feature)) {
            return active.optBoolean(feature, true)
        }
        return true
    }

    fun fmtEaz(value: Double): String {
        if (!value.isFinite()) return "—"
        if (value <= 0) return "0"
        return if (value % 1.0 == 0.0) value.toLong().toString()
        else "%.2f".format(value).trimEnd('0').trimEnd('.')
    }

    fun label(item: Item, translationStore: TranslationStore): String =
        translationStore.t(item.labelKey, item.labelDefault)
}
