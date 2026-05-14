package com.eazpire.creator.shop.sidebar

/**
 * Keep in sync with [theme/snippets/eaz-sidebar-grid.liquid].
 */
object ShopSidebarConstants {
    val audienceHandlesRaw = listOf(
        "women", "men", "kids", "toddler", "baby", "female", "male", "frauen", "manner", "kinder", "babys"
    )
    val skipHandles =
        listOf(
            "clothing", "shoes", "accessories", "bekleidung", "schuhe", "accessoires",
            "gutscheine", "gift-cards"
        )

    val homeDecorHandles = listOf(
        "drinkware", "wall-art", "home-living", "home-&-living",
        "plush-toys", "stationery"
    )
    val lifestyleHandles = listOf(
        "bags", "jewelry", "schmuck", "taschen"
    )
    val techHandles = listOf(
        "tech", "phone-cases", "handyhullen"
    )

    /** Superset merged for remaining-section filter */
    val groupedHandles: Set<String> =
        (
            homeDecorHandles + lifestyleHandles + techHandles
            ).map { normalizeHandleLite(it) }.toSet()

    fun normalizeHandleLite(raw: String): String =
        raw.trim().lowercase().replace("/", "-").replace(" ", "-")

    /** Section container ids draggable (matching web dataset) */
    const val CONTAINER_GUTSCHEINE = "gutscheine"
    const val CONTAINER_AUDIENCE = "audience"
    const val CONTAINER_HOME_DECOR = "home-decor"
    const val CONTAINER_LIFESTYLE = "lifestyle"
    const val CONTAINER_TECH = "tech"
}
