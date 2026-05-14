package com.eazpire.creator.shop.sidebar

private val NAV_SYNONYM = mapOf(
    "damen" to "women",
    "frauen" to "women",
    "female" to "women",
    "herren" to "men",
    "manner" to "men",
    "male" to "men",
    "kinder" to "kids",
    "babys" to "toddler",
    "baby" to "toddler",
    "kleinkinder" to "toddler",
    "wandkunst" to "wall_art",
    "wan-dkunst" to "wall_art",
    "home-&-living" to "home_living",
    "home_&_living" to "home_living",
    "wohnen-leben" to "home_living",
    "handyhullen" to "phone-cases",
    "kuscheltiere" to "plush-toys",
    "schmuck" to "jewelry",
    "taschen" to "bags",
    "bekleidung" to "clothing",
    "kleidung" to "clothing",
    "schuhe" to "shoes",
    "accessoires" to "accessories",
    "trinkgefasse" to "drinkware",
)

/** Parity target: [theme/snippets/eaz-nav-ui-key.liquid] output `eaz.nav.*` without `ui:` prefix. */
fun navUiTranslationKey(handle: String?): String {
    val h = ShopSidebarConstants.normalizeHandleLite(handle ?: "")
    val mapped = NAV_SYNONYM[h] ?: when (h) {
        "wall-art" -> "wall_art"
        "home-living" -> "home_living"
        else -> null
    } ?: h
    return "eaz.nav.$mapped"
}
