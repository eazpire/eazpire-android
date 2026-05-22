package com.eazpire.creator.shop.sidebar

/**
 * Parity with [theme/snippets/eaz-collection-product-type-query.liquid] (clean ?t-shirt URLs).
 */
object EazCollectionProductTypeQuery {
    fun buildQueryFragment(navKey: String): String {
        return when (navKey) {
            "tshirt" -> "t-shirt"
            "tank_top" -> "tank-top"
            "long_sleeve" -> "long-sleeve"
            "shoes_all" -> "shoes"
            "accessories_all" -> "accessories"
            "apparel" -> "clothing"
            "hoodie", "sweatshirt", "jacket", "shorts", "dress", "pants", "joggers",
            "jeans", "leggings", "skirt", "socks", "sneakers", "boots", "sandals",
            "bags", "jewelry", "hats", "scarves", "shoes", "accessories" -> navKey.replace('_', '-')
            else -> ""
        }
    }
}
