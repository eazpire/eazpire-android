package com.eazpire.creator.ui.nav

import com.eazpire.creator.shop.sidebar.ShopSidebarConstants

/**
 * Tabler outline icons (MIT) — paths from theme/snippets/eaz-na-tabler-icon.liquid
 */
object EazNavTablerIcons {

    val paths: Map<String, List<String>> = mapOf(
        "all" to listOf(
            "M4 5a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1l0 -4",
            "M14 5a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1l0 -4",
            "M4 15a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1l0 -4",
            "M14 15a1 1 0 0 1 1 -1h4a1 1 0 0 1 1 1v4a1 1 0 0 1 -1 1h-4a1 1 0 0 1 -1 -1l0 -4",
        ),
        "women" to listOf(
            "M10 16v5", "M14 16v5", "M8 16h8l-2 -7h-4l-2 7",
            "M5 11c1.667 -1.333 3.333 -2 5 -2", "M19 11c-1.667 -1.333 -3.333 -2 -5 -2",
            "M10 4a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
        ),
        "men" to listOf(
            "M10 16v5", "M14 16v5", "M9 9h6l-1 7h-4l-1 -7",
            "M5 11c1.333 -1.333 2.667 -2 4 -2", "M19 11c-1.333 -1.333 -2.667 -2 -4 -2",
            "M10 4a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
        ),
        "kids" to listOf(
            "M3 12a9 9 0 1 0 18 0a9 9 0 1 0 -18 0",
            "M9 10l.01 0", "M15 10l.01 0", "M9.5 15a3.5 3.5 0 0 0 5 0", "M12 3a2 2 0 0 0 0 4",
        ),
        "toddler" to listOf(
            "M6 19a2 2 0 1 0 4 0a2 2 0 1 0 -4 0", "M16 19a2 2 0 1 0 4 0a2 2 0 1 0 -4 0",
            "M2 5h2.5l1.632 4.897a6 6 0 0 0 5.693 4.103h2.675a5.5 5.5 0 0 0 0 -11h-.5v6",
            "M6 9h14", "M9 17l1 -3", "M16 14l1 3",
        ),
        "accessories" to listOf(
            "M6.331 8h11.339a2 2 0 0 1 1.977 2.304l-1.255 8.152a3 3 0 0 1 -2.966 2.544h-6.852a3 3 0 0 1 -2.965 -2.544l-1.255 -8.152a2 2 0 0 1 1.977 -2.304",
            "M9 11v-5a3 3 0 0 1 6 0v5",
        ),
        "home-living" to listOf(
            "M5 12l-2 0l9 -9l9 9l-2 0",
            "M5 12v7a2 2 0 0 0 2 2h10a2 2 0 0 0 2 -2v-7",
            "M9 21v-6a2 2 0 0 1 2 -2h2a2 2 0 0 1 2 2v6",
        ),
        "create" to listOf(
            "M15 4v5h5",
            "M9 20l-5.5 -5.5a1.5 1.5 0 0 1 0 -2.12l6.86 -6.86a2.5 2.5 0 0 1 3.54 0l5.86 5.86a2.5 2.5 0 0 1 0 3.54l-6.86 6.86a1.5 1.5 0 0 1 -2.12 0l-5.5 -5.5",
        ),
        "print-3d" to listOf(
            "M12 3l8 4.5l0 9l-8 4.5l-8 -4.5l0 -9l8 -4.5",
            "M12 12l8 -4.5", "M12 12l0 9", "M12 12l-8 -4.5",
        ),
        "blank-products" to listOf(
            "M15 4h1a3 3 0 0 1 3 3v3", "M15 13h1a3 3 0 0 0 3 -3v-3",
            "M9 4h-1a3 3 0 0 0 -3 3v3", "M9 13h-1a3 3 0 0 1 -3 -3v-3",
            "M9 20v2", "M15 20v2", "M6 4v2", "M18 4v2", "M15 4v2", "M9 4v2",
            "M6 4h12", "M6 13h12", "M14 8h-4",
        ),
        "drinkware" to listOf(
            "M5 11h14v-3h-14z", "M17.5 11l-1.5 10h-8l-1.5 -10",
            "M6 8v-1a2 2 0 0 1 2 -2h8a2 2 0 0 1 2 2v1",
        ),
        "wall-art" to listOf(
            "M15 8h.01",
            "M3 6a3 3 0 0 1 3 -3h12a3 3 0 0 1 3 3v12a3 3 0 0 1 -3 3h-12a3 3 0 0 1 -3 -3v-12",
            "M3 16l5 -5c.928 -.893 2.072 -.893 3 0l5 5", "M14 14l1 -1c.928 -.893 2.072 -.893 3 0l3 3",
        ),
        "tech" to listOf(
            "M3 5a2 2 0 0 1 2 -2h14a2 2 0 0 1 2 2v10a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-10",
            "M7 20h10", "M9 16v4", "M15 16v4",
        ),
        "stationery" to listOf(
            "M4 20h4l10.5 -10.5a2.828 2.828 0 1 0 -4 -4l-10.5 10.5v4", "M13.5 6.5l4 4",
        ),
        "gift" to listOf(
            "M3 8m0 1a1 1 0 0 1 1 -1h16a1 1 0 0 1 1 1v2a1 1 0 0 1 -1 1h-16a1 1 0 0 1 -1 -1z",
            "M12 8l0 13", "M19 12v7a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-7",
            "M7.5 8a2.5 2.5 0 0 1 0 -5a4.8 8 0 0 1 4.5 5a4.8 8 0 0 1 4.5 -5a2.5 2.5 0 0 1 0 5",
        ),
        "tag" to listOf(
            "M7.5 7.5m-1 0a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
            "M3 6v5.172a2 2 0 0 0 .586 1.414l7.71 7.71a2.41 2.41 0 0 0 3.408 0l5.592 -5.592a2.41 2.41 0 0 0 0 -3.408l-7.71 -7.71a2 2 0 0 0 -1.414 -.586h-5.172a3 3 0 0 0 -3 3z",
        ),
        "clothing" to listOf(
            "M12 3l0 2", "M6 5l6 -2l6 2",
            "M4 7l4 0l0 13a1 1 0 0 0 1 1h6a1 1 0 0 0 1 -1l0 -13l4 0",
        ),
        "shoes" to listOf(
            "M4 6h5.5l4.875 4.5a1 1 0 0 0 .75 .325h7.875a1 1 0 0 1 1 1v1a1 1 0 0 1 -1 1h-6.5a1 1 0 0 0 -.75 .325l-4.875 4.5h-7.125a1 1 0 0 1 -1 -1v-8a1 1 0 0 1 1 -1",
            "M4 10l16 0", "M10 6l0 4",
        ),
        "shirt" to listOf(
            "M15 4h1a3 3 0 0 1 3 3v3", "M15 13h1a3 3 0 0 0 3 -3v-3",
            "M9 4h-1a3 3 0 0 0 -3 3v3", "M9 13h-1a3 3 0 0 1 -3 -3v-3",
            "M9 20v2", "M15 20v2", "M6 4v2", "M18 4v2", "M15 4v2", "M9 4v2",
            "M6 4h12", "M6 13h12", "M14 8h-4",
        ),
        "hoodie" to listOf(
            "M7 4h10l2 4v12a2 2 0 0 1 -2 2h-10a2 2 0 0 1 -2 -2v-12l2 -4z",
            "M7 8h10", "M9 4v4", "M15 4v4",
        ),
        "sweatshirt" to listOf(
            "M7 4h10l3 3v11a2 2 0 0 1 -2 2h-12a2 2 0 0 1 -2 -2v-11l3 -3z", "M7 7h10",
        ),
        "tank-top" to listOf("M8 3h8l2 5l-2 13h-8l-2 -13l2 -5z"),
        "jacket" to listOf(
            "M6 3h12l2 4v13a1 1 0 0 1 -1 1h-14a1 1 0 0 1 -1 -1v-13l2 -4z",
            "M12 3v18", "M8 8h8",
        ),
        "shorts" to listOf("M4 6h16v5l-2 9h-12l-2 -9v-5z", "M12 6v14"),
        "dress" to listOf("M12 2l4 4v2l-2 14h-4l-2 -14v-2l4 -4z"),
        "sneaker" to listOf(
            "M4 6h5.5l4.875 4.5a1 1 0 0 0 .75 .325h7.875a1 1 0 0 1 1 1v1a1 1 0 0 1 -1 1h-6.5a1 1 0 0 0 -.75 .325l-4.875 4.5h-7.125a1 1 0 0 1 -1 -1v-8a1 1 0 0 1 1 -1",
        ),
        "boot" to listOf(
            "M4 17v-5a3 3 0 0 1 3 -3h10a3 3 0 0 1 3 3v5", "M4 17h16",
            "M7 11v-2a2 2 0 0 1 2 -2h6a2 2 0 0 1 2 2v2",
        ),
        "sandal" to listOf(
            "M4 17h16", "M6 17v-4a2 2 0 0 1 2 -2h8a2 2 0 0 1 2 2v4",
            "M8 11v-2a1 1 0 0 1 1 -1h6a1 1 0 0 1 1 1v2",
        ),
        "bag" to listOf(
            "M6 8a6 6 0 1 1 12 0c0 6 -3 10 -6 10s-6 -4 -6 -10", "M12 4v2", "M6 8h12",
        ),
        "jewelry" to listOf("M6 3h12l-3 7l3 11h-12l3 -11l-3 -7z", "M9 10h6"),
        "hat" to listOf(
            "M6 15a6 6 0 1 0 12 0v-3", "M11 5v2", "M13 5v2", "M4 12h16",
        ),
        "scarf" to listOf("M8 4h8l-1 16h-6l-1 -16z", "M8 8h8", "M9 12h6"),
        "plush" to listOf(
            "M12 10a4 4 0 1 0 -4 4h8a4 4 0 1 0 -4 -4",
            "M9 9l.01 0", "M15 9l.01 0", "M10 13c1 1 3 1 4 0",
        ),
        "phone" to listOf(
            "M6 5a2 2 0 0 1 2 -2h8a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-8a2 2 0 0 1 -2 -2v-14",
            "M11 4h2", "M12 17l.01 0",
        ),
        "socks" to listOf(
            "M4 12c0 -2 1 -4 4 -4h8c3 0 4 2 4 4v2c0 3 -2 6 -5 6h-6c-3 0 -5 -3 -5 -6v-2",
            "M8 18l-1 3", "M16 18l1 3",
        ),
        "pants" to listOf("M6 4h12l-1 16h-4l-1 -8h-2l-1 8h-4l-1 -16z"),
        "skirt" to listOf("M8 4h8l2 16h-12l2 -16z"),
        "long-sleeve" to listOf("M6 6h12v12h-12z", "M3 9v6", "M21 9v6"),
        "crop-top" to listOf("M8 4h8l1 4v12h-10v-12l1 -4z", "M7 10h10"),
        "star" to listOf(
            "M12 17.75l-6.172 3.245l1.179 -6.873l-5 -4.867l6.9 -1l3.086 -6.253l3.086 6.253l6.9 1l-5 4.867l1.179 6.873z",
        ),
        "flame" to listOf(
            "M12 12c2 -2.96 0 -7 -1 -8c0 3.038 -1.773 4.741 -3 6c-1.226 1.26 -2 3.24 -2 5a6 6 0 1 0 12 0c0 -1.532 -1.056 -3.94 -2 -5c-1.786 3 -2.791 3 -4 2z",
        ),
        "sparkle" to listOf(
            "M16 18a2 2 0 0 1 2 2a2 2 0 0 1 2 -2a2 2 0 0 1 -2 -2a2 2 0 0 1 -2 2",
            "M7 6a2 2 0 0 1 2 -2a2 2 0 0 1 2 2a2 2 0 0 1 -2 2a2 2 0 0 1 -2 -2",
            "M11 18l-1 1", "M12 13l-1 1", "M15 11l-1 1", "M16 6l-1 1",
        ),
        "pencil" to listOf(
            "M4 20h4l10.5 -10.5a2.828 2.828 0 1 0 -4 -4l-10.5 10.5v4", "M13.5 6.5l4 4",
        ),
        "box" to listOf(
            "M12 3l8 4.5l0 9l-8 4.5l-8 -4.5l0 -9l8 -4.5",
            "M12 12l8 -4.5", "M12 12l0 9", "M12 12l-8 -4.5",
        ),
    )

    /** Parity with theme/snippets/eaz-nav-handle-icon-name.liquid */
    fun iconNameForHandle(rawHandle: String): String {
        var h = ShopSidebarConstants.normalizeHandleLite(rawHandle)
        h = when (h) {
            "damen", "frauen", "female" -> "women"
            "herren", "manner", "male" -> "men"
            "kinder" -> "kids"
            "babys", "baby", "kleinkinder" -> "toddler"
            "home-&-living", "wohnen-leben" -> "home-living"
            "wandkunst" -> "wall-art"
            "handyhullen" -> "phone-cases"
            "kuscheltiere" -> "plush-toys"
            "schmuck" -> "jewelry"
            "taschen" -> "bags"
            "bekleidung", "kleidung" -> "clothing"
            "schuhe" -> "shoes"
            "accessoires" -> "accessories"
            "trinkgefasse" -> "drinkware"
            "tshirt", "t-shirt", "t-shirts" -> "shirt"
            "hoodie", "hoodies" -> "hoodie"
            "sweatshirt", "sweatshirts" -> "sweatshirt"
            "tank_top", "tank-top", "tank-tops" -> "tank-top"
            "jacket", "jackets", "coat", "coats" -> "jacket"
            "dress", "dresses" -> "dress"
            "shoes_all", "sneakers" -> "sneaker"
            "boots" -> "boot"
            "sandals" -> "sandal"
            "accessories_all", "bags" -> "bag"
            "hats" -> "hat"
            "scarves" -> "scarf"
            "long_sleeve", "long-sleeves", "long-sleeve" -> "long-sleeve"
            "crop-top", "crop-tops" -> "crop-top"
            "pants", "joggers", "jeans", "leggings" -> "pants"
            "skirt", "skirts" -> "skirt"
            "eaz-promotions", "promotions", "deals" -> "tag"
            "gift-card", "gift-cards", "gutscheine", "coupons", "thankyou", "thank-you" -> "gift"
            "3d-print", "print-3d" -> "print-3d"
            "blank-products", "blank_products" -> "blank-products"
            "eaz_shop_create", "shop-create", "create" -> "create"
            "new-arrivals" -> "star"
            "bestsellers" -> "flame"
            "personalize" -> "pencil"
            "generate" -> "sparkle"
            "home_living" -> "home-living"
            "wall_art" -> "wall-art"
            "phone-cases" -> "phone"
            "plush-toys" -> "plush"
            else -> h
        }
        return when (h) {
            "all", "women", "men", "kids", "toddler", "accessories", "home-living",
            "create", "print-3d", "blank-products", "drinkware", "wall-art", "tech",
            "stationery", "gift", "tag", "clothing", "shoes", "shirt", "hoodie",
            "sweatshirt", "tank-top", "jacket", "shorts", "dress", "sneaker", "boot",
            "sandal", "bag", "jewelry", "hat", "scarf", "plush", "phone", "socks",
            "pants", "skirt", "long-sleeve", "crop-top", "star", "flame", "sparkle", "pencil",
            -> h
            else -> if (paths.containsKey(h)) h else "box"
        }
    }

    fun pathsForHandle(rawHandle: String): List<String> {
        val name = iconNameForHandle(rawHandle)
        return paths[name] ?: paths["box"].orEmpty()
    }
}
