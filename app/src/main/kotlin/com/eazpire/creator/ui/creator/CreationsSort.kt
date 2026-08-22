package com.eazpire.creator.ui.creator

data class CreationsSortState(
    val key: String = "updated",
    val dir: String = "desc",
)

data class CreationsSortAvailability(
    val updated: Boolean = true,
    val name: Boolean = true,
    val favorites: Boolean = false,
    val remixes: Boolean = false,
    val publishedProducts: Boolean = true,
    val sales: Boolean = false,
    val clicks: Boolean = false,
    val addToCart: Boolean = false,
    val impressions: Boolean = false,
    val revenue: Boolean = false,
    val lastSale: Boolean = false,
) {
    fun isEnabled(key: String, tab: String): Boolean {
        if (key == "published_products" && tab != "designs") return false
        return when (key) {
            "updated", "name" -> true
            "favorites" -> favorites
            "remixes" -> remixes
            "published_products" -> publishedProducts
            else -> false
        }
    }
}

data class CreationsSortOption(
    val id: String,
    val tabs: Set<String>,
)

val CREATIONS_SORT_OPTIONS = listOf(
    CreationsSortOption("updated", setOf("designs", "products")),
    CreationsSortOption("name", setOf("designs", "products")),
    CreationsSortOption("favorites", setOf("designs", "products")),
    CreationsSortOption("remixes", setOf("designs", "products")),
    CreationsSortOption("published_products", setOf("designs")),
    CreationsSortOption("sales", setOf("designs", "products")),
    CreationsSortOption("clicks", setOf("designs", "products")),
    CreationsSortOption("add_to_cart", setOf("designs", "products")),
    CreationsSortOption("impressions", setOf("designs", "products")),
    CreationsSortOption("revenue", setOf("designs", "products")),
    CreationsSortOption("last_sale", setOf("designs", "products")),
)

fun nextCreationsSortState(current: CreationsSortState, nextKey: String): CreationsSortState {
    if (current.key == nextKey) {
        return current.copy(dir = if (current.dir == "desc") "asc" else "desc")
    }
    return CreationsSortState(key = nextKey, dir = if (nextKey == "name") "asc" else "desc")
}

fun parseCreationsSortAvailability(obj: org.json.JSONObject?): CreationsSortAvailability {
    if (obj == null) {
        return CreationsSortAvailability(favorites = true, remixes = true)
    }
    return CreationsSortAvailability(
        updated = obj.optBoolean("updated", true),
        name = obj.optBoolean("name", true),
        favorites = obj.optBoolean("favorites", false),
        remixes = obj.optBoolean("remixes", false),
        publishedProducts = obj.optBoolean("published_products", true),
        sales = obj.optBoolean("sales", false),
        clicks = obj.optBoolean("clicks", false),
        addToCart = obj.optBoolean("add_to_cart", false),
        impressions = obj.optBoolean("impressions", false),
        revenue = obj.optBoolean("revenue", false),
        lastSale = obj.optBoolean("last_sale", false),
    )
}

private fun cmpNumber(a: Long, b: Long, dir: String): Int {
    val raw = a.compareTo(b)
    return if (dir == "asc") raw else -raw
}

fun sortCreationDesigns(
    items: List<CreationDesign>,
    state: CreationsSortState,
    publishedCount: (CreationDesign) -> Int,
): List<CreationDesign> {
    return items.sortedWith { a, b ->
        val cmp = when (state.key) {
            "name" -> {
                val raw = a.title.compareTo(b.title, ignoreCase = true)
                if (state.dir == "asc") raw else -raw
            }
            "favorites" -> cmpNumber(a.favoriteCount.toLong(), b.favoriteCount.toLong(), state.dir)
            "remixes" -> cmpNumber(a.remixCount.toLong(), b.remixCount.toLong(), state.dir)
            "published_products" -> cmpNumber(publishedCount(a).toLong(), publishedCount(b).toLong(), state.dir)
            else -> cmpNumber(a.sortUpdatedAt, b.sortUpdatedAt, state.dir)
        }
        if (cmp != 0) cmp else (b.id ?: "").compareTo(a.id ?: "")
    }
}

fun sortCreationProducts(
    items: List<CreationProduct>,
    state: CreationsSortState,
): List<CreationProduct> {
    return items.sortedWith { a, b ->
        val cmp = when (state.key) {
            "name" -> {
                val raw = a.title.compareTo(b.title, ignoreCase = true)
                if (state.dir == "asc") raw else -raw
            }
            "favorites" -> cmpNumber(a.favoriteCount.toLong(), b.favoriteCount.toLong(), state.dir)
            "remixes" -> cmpNumber(a.remixCount.toLong(), b.remixCount.toLong(), state.dir)
            else -> cmpNumber(a.sortUpdatedAt, b.sortUpdatedAt, state.dir)
        }
        if (cmp != 0) cmp else b.id.compareTo(a.id)
    }
}
