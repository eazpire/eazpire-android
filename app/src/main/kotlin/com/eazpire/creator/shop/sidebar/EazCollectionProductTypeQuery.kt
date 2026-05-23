package com.eazpire.creator.shop.sidebar

import java.net.URLEncoder
import kotlin.text.Charsets

/**
 * Parity with [theme/snippets/eaz-collection-product-type-query.liquid].
 */
object EazCollectionProductTypeQuery {
    fun buildQueryFragment(navKey: String): String {
        fun enc(s: String) = URLEncoder.encode(s, Charsets.UTF_8.name())

        val slug = when (navKey) {
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
        if (slug.isEmpty()) return ""

        val filters = mutableListOf<String>()
        fun addType(type: String) {
            filters.add("filter.p.product_type=${enc(type)}")
        }

        when (navKey) {
            "tshirt" -> {
                addType("Unisex Softstyle Cotton Tee")
                addType("Women's Favorite Tee")
            }
            "hoodie" -> {
                addType("Unisex Hooded Sweatshirt")
                addType("Backprint Unisex Hooded Sweatshirt")
                addType("Unisex All-Over Print Hoodie")
            }
            "sweatshirt" -> addType("Unisex Crewneck Sweatshirt")
            "tank_top" -> addType("Unisex Jersey Tank")
            else -> { /* other keys: type slug only, filters optional */ }
        }

        return buildList {
            add("type=$slug")
            addAll(filters)
        }.joinToString("&")
    }
}
