package com.eazpire.creator.util

/**
 * Maps Shopify [productKey] metafield and/or storefront product type to CUSTOMER_DB
 * `product_types.key` for get-size-recommendation.
 */
object SizeAiProductTypeMapper {

    private val PRODUCT_KEY_TO_TYPE: Map<String, String> = mapOf(
        "unisex-softstyle-cotton-tee" to "t-shirt",
        "womens-favorite-tee" to "t-shirt",
        "unisex-jersey-tank" to "tank-top",
        "backprint-unisex-hooded-sweatshirt" to "hoodie",
        "unisex-crewneck-sweatshirt" to "sweater",
        "unisex-hooded-sweatshirt" to "hoodie",
        "unisex-hoodie" to "hoodie"
    )

    private val TYPE_LABEL_TO_KEY: Map<String, String> = mapOf(
        "t-shirt" to "t-shirt",
        "t shirt" to "t-shirt",
        "tee" to "t-shirt",
        "hoodie" to "hoodie",
        "hooded sweatshirt" to "hoodie",
        "sweatshirt" to "sweater",
        "crewneck" to "sweater",
        "pullover" to "sweater",
        "tank" to "tank-top",
        "tank top" to "tank-top",
        "long sleeve" to "long-sleeve",
        "jacket" to "jacket",
        "jeans" to "jeans",
        "pants" to "pants",
        "shorts" to "shorts",
        "joggers" to "joggers",
        "skirt" to "skirt",
        "sneaker" to "sneaker",
        "sneakers" to "sneaker",
        "boots" to "boots",
        "sandals" to "sandals",
        "loafers" to "loafers"
    )

    fun resolve(productKey: String?, shopifyProductType: String?): String? {
        val pk = productKey?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
        if (pk != null) {
            PRODUCT_KEY_TO_TYPE[pk]?.let { return it }
        }
        val raw = shopifyProductType?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
        val hyphenated = raw.replace(Regex("\\s+"), "-")
        TYPE_LABEL_TO_KEY[raw]?.let { return it }
        TYPE_LABEL_TO_KEY[hyphenated]?.let { return it }
        TYPE_LABEL_TO_KEY[raw.replace("-", " ")]?.let { return it }
        return hyphenated.takeIf { it.isNotBlank() }
    }
}

fun matchShopifySizeOption(availableSizes: List<String>, recommended: String): String? {
    val r = recommended.trim()
    if (r.isBlank()) return null
    availableSizes.firstOrNull { it.equals(r, ignoreCase = true) }?.let { return it }
    val rNorm = normalizeSizeToken(r)
    availableSizes.firstOrNull { normalizeSizeToken(it) == rNorm }?.let { return it }
    availableSizes.firstOrNull { av ->
        val a = normalizeSizeToken(av)
        a.contains(rNorm) || rNorm.contains(a)
    }?.let { return it }
    return null
}

private fun normalizeSizeToken(s: String): String =
    s.lowercase().replace(Regex("[^a-z0-9]+"), "")
