package com.eazpire.creator.chat

/** Mirrors theme/assets/eazy-functions.js categories; labels resolved via TranslationStore + English fallbacks. */
internal enum class EazyFeatureCategory { Shared, Shop, Creator }

internal data class EazyFeatureDef(
    val id: String,
    val labelKey: String,
    val defaultLabel: String,
    val category: EazyFeatureCategory
)

internal object EazyChatFeatureCatalog {
    private val features: List<EazyFeatureDef> = listOf(
        EazyFeatureDef("interests", "eazy_fn.interests", "Interests", EazyFeatureCategory.Shared),
        EazyFeatureDef("community", "eazy_fn.community", "Community", EazyFeatureCategory.Shared),
        EazyFeatureDef("generate-design", "eazy_fn.generate_design", "Generate design", EazyFeatureCategory.Shared),
        EazyFeatureDef("my-creations", "eazy_fn.my_creations", "My creations", EazyFeatureCategory.Shared),
        EazyFeatureDef("publish", "eazy_fn.publish", "Publish products", EazyFeatureCategory.Shared),
        EazyFeatureDef("my-products", "eazy_fn.my_products", "My products", EazyFeatureCategory.Shared),
        EazyFeatureDef("active-jobs", "eazy_fn.active_jobs", "Active Jobs", EazyFeatureCategory.Shared),
        EazyFeatureDef("favorites", "eazy_fn.favorites", "Favorites", EazyFeatureCategory.Shop),
        EazyFeatureDef("gift-cards", "eazy_fn.gift_cards", "Gift cards", EazyFeatureCategory.Shop),
        EazyFeatureDef("promo-codes", "eazy_fn.promo_codes", "Promo codes", EazyFeatureCategory.Shop),
        EazyFeatureDef("size-ai", "eazy_fn.size_ai", "Size AI", EazyFeatureCategory.Shop),
        EazyFeatureDef("my-orders", "eazy_fn.my_orders", "My orders", EazyFeatureCategory.Shop),
        EazyFeatureDef("product-search", "eazy_fn.product_search", "Product search", EazyFeatureCategory.Shop),
        EazyFeatureDef("browse-shop", "eazy_fn.browse_shop", "Browse shop", EazyFeatureCategory.Shop),
        EazyFeatureDef("wardrobe", "eazy_fn.wardrobe", "Wardrobe", EazyFeatureCategory.Shop),
        EazyFeatureDef("my-mockups", "eazy_fn.my_mockups", "My mockups", EazyFeatureCategory.Shop),
        EazyFeatureDef("hero-images", "eazy_fn.hero_images", "Hero images", EazyFeatureCategory.Creator),
        EazyFeatureDef("creator-image", "eazy_fn.creator_image", "Creator image", EazyFeatureCategory.Creator),
        EazyFeatureDef("creator-settings", "eazy_fn.creator_settings", "Creator settings", EazyFeatureCategory.Creator),
        EazyFeatureDef("balance", "eazy_fn.balance", "Balance", EazyFeatureCategory.Creator),
        EazyFeatureDef("level", "eazy_fn.level", "Level", EazyFeatureCategory.Creator),
        EazyFeatureDef("mentor-support", "eazy_fn.mentor_support", "Support creators", EazyFeatureCategory.Creator)
    )

    fun forContext(ctx: EazyChatContext): List<EazyFeatureDef> = features.filter { def ->
        when (def.category) {
            EazyFeatureCategory.Shared -> true
            EazyFeatureCategory.Shop -> ctx == EazyChatContext.Shop
            EazyFeatureCategory.Creator -> ctx == EazyChatContext.Creator
        }
    }

    fun byId(id: String): EazyFeatureDef? = features.find { it.id == id }

    fun categoryLabel(cat: EazyFeatureCategory, t: (String, String) -> String): String = when (cat) {
        EazyFeatureCategory.Shared -> t("eazy_fn.cat_shared", "Shared")
        EazyFeatureCategory.Shop -> t("eazy_fn.cat_shop", "Shop")
        EazyFeatureCategory.Creator -> t("eazy_fn.cat_creator", "Creator")
    }
}
