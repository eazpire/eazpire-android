package com.eazpire.creator.shop.sidebar

import java.net.URLEncoder
import kotlin.text.Charsets

/**
 * Parity with [theme/snippets/eaz-collection-product-type-query.liquid] and web normalize script.
 */
object EazCollectionProductTypeQuery {
    fun buildQueryFragment(navKey: String): String {
        val pts = mutableListOf<String>()
        fun add(encodedType: String) {
            pts.add("filter.p.product_type=$encodedType")
        }
        fun encode(s: String) = URLEncoder.encode(s, Charsets.UTF_8.name())

        val eazUt = encode("Unisex Softstyle Cotton Tee")
        val eazWft = encode("Women's Favorite Tee")
        val eazUh = encode("Unisex Hooded Sweatshirt")
        val eazBh = encode("Backprint Unisex Hooded Sweatshirt")
        val eazAph = encode("Unisex All-Over Print Hoodie")
        val eazUc = encode("Unisex Crewneck Sweatshirt")
        val eazTank = encode("Unisex Jersey Tank")

        val ezJacket = encode("Jacket")
        val ezShorts = encode("Shorts")
        val ezDress = encode("Dress")
        val ezLongSleeve = encode("Long Sleeve")
        val ezPants = encode("Pants")
        val ezJoggers = encode("Joggers")
        val ezJeans = encode("Jeans")
        val ezLeggings = encode("Leggings")
        val ezSkirt = encode("Skirt")
        val ezSock = encode("Sock")
        val ezShoes = encode("Shoes")
        val ezSneakers = encode("Sneakers")
        val ezBoots = encode("Boots")
        val ezSandals = encode("Sandals")
        val ezAccessories = encode("Accessories")
        val ezBags = encode("Bags")
        val ezJewelry = encode("Jewelry")
        val ezHats = encode("Hats")
        val ezScarves = encode("Scarves")

        when (navKey) {
            "tshirt" -> {
                add(eazUt); add(eazWft)
            }
            "hoodie" -> {
                add(eazUh); add(eazBh); add(eazAph)
            }
            "sweatshirt" -> add(eazUc)
            "tank_top" -> add(eazTank)
            "apparel" -> {
                add(eazUt); add(eazWft); add(eazUh); add(eazBh); add(eazAph)
                add(eazUc); add(eazTank)
                add(ezJacket); add(ezShorts); add(ezDress); add(ezLongSleeve)
                add(ezPants); add(ezJoggers); add(ezJeans); add(ezLeggings)
                add(ezSkirt); add(ezSock)
            }
            "shoes_all" -> {
                add(ezShoes); add(ezSneakers); add(ezBoots); add(ezSandals)
            }
            "accessories_all" -> {
                add(ezAccessories); add(ezBags); add(ezJewelry); add(ezHats); add(ezScarves)
            }
            "jacket" -> add(ezJacket)
            "shorts" -> add(ezShorts)
            "dress" -> add(ezDress)
            "long_sleeve" -> add(ezLongSleeve)
            "pants" -> add(ezPants)
            "joggers" -> add(ezJoggers)
            "jeans" -> add(ezJeans)
            "leggings" -> add(ezLeggings)
            "skirt" -> add(ezSkirt)
            "socks" -> add(ezSock)
            "shoes" -> add(ezShoes)
            "sneakers" -> add(ezSneakers)
            "boots" -> add(ezBoots)
            "sandals" -> add(ezSandals)
            "bags" -> add(ezBags)
            "jewelry" -> add(ezJewelry)
            "hats" -> add(ezHats)
            "scarves" -> add(ezScarves)
            "accessories" -> add(ezAccessories)
            else -> return ""
        }
        return pts.joinToString("&")
    }
}
