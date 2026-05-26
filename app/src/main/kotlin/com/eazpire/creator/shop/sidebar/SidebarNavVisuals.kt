package com.eazpire.creator.shop.sidebar

/**
 * Emoji icons for sidebar list rows — aligned with grid tile emojis in [ShopSidebarLayoutEngine].
 */
object SidebarNavVisuals {

    fun emojiForHandle(rawHandle: String): String {
        val h = ShopSidebarConstants.normalizeHandleLite(rawHandle)
        return when {
            h in audienceSet -> emojiAudience(h)
            h in homeDecorSet -> emojiHomeDecor(h)
            h in lifestyleSet -> emojiLifestyle(h)
            h in techSet -> emojiTech(h)
            else -> emojiChildMid(h)
        }
    }

    fun collectCollectionHandles(items: List<ParsedNavItem>): Set<String> {
        val out = linkedSetOf<String>()
        fun walk(item: ParsedNavItem) {
            handleFromNavUrl(item.url)?.let { out.add(it) }
            item.links.forEach { walk(it) }
        }
        items.forEach { walk(it) }
        return out
    }

    fun handleFromNavUrl(url: String): String? {
        val u = url.trim()
        if (u.isBlank()) return null
        val path =
            when {
                u.startsWith("//") -> "https:$u"
                u.startsWith("/") -> "https://www.eazpire.com$u"
                else -> u
            }
        return Regex("/collections/([^/?#]+)", RegexOption.IGNORE_CASE)
            .find(path)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }
    }

    private val audienceSet =
        ShopSidebarConstants.audienceHandlesRaw
            .map { ShopSidebarConstants.normalizeHandleLite(it) }
            .toSet()

    private val homeDecorSet =
        ShopSidebarConstants.homeDecorHandles
            .map { ShopSidebarConstants.normalizeHandleLite(it) }
            .toSet()

    private val lifestyleSet =
        ShopSidebarConstants.lifestyleHandles
            .map { ShopSidebarConstants.normalizeHandleLite(it) }
            .toSet()

    private val techSet =
        ShopSidebarConstants.techHandles
            .map { ShopSidebarConstants.normalizeHandleLite(it) }
            .toSet()

    private fun emojiAudience(handle: String): String =
        when (handle) {
            "women", "female", "frauen" -> "👩"
            "men", "male", "manner" -> "👨"
            "kids", "kinder" -> "🧒"
            "toddler", "babys", "baby" -> "👶"
            else -> "👤"
        }

    private fun emojiHomeDecor(h: String): String =
        when (h) {
            "drinkware" -> "☕"
            "wall-art" -> "🖼️"
            "home-living", "home-&-living" -> "🏡"
            "plush-toys" -> "🧸"
            "stationery" -> "📝"
            else -> "📦"
        }

    private fun emojiLifestyle(h: String): String =
        when (h) {
            "bags", "taschen" -> "👜"
            "jewelry", "schmuck" -> "💍"
            else -> "📦"
        }

    private fun emojiTech(h: String): String =
        when (h) {
            "phone-cases", "handyhullen" -> "📱"
            "tech" -> "💻"
            else -> "📱"
        }

    private fun emojiChildMid(h: String): String =
        when (h) {
            "t-shirts" -> "👕"
            "hoodies" -> "🧥"
            "sweatshirts" -> "🧶"
            "tank-tops" -> "🎽"
            "crop-tops" -> "👚"
            "long-sleeves" -> "👔"
            "jackets", "coats" -> "🧥"
            "shorts" -> "🩳"
            "joggers", "pants", "jeans" -> "👖"
            "dresses" -> "👗"
            "socks" -> "🧦"
            "shoes", "schuhe" -> "👟"
            "bags", "taschen" -> "👜"
            "accessories", "accessoires" -> "👜"
            "jewelry", "schmuck" -> "💍"
            "deals" -> "🏷️"
            "new-arrivals" -> "⭐"
            "bestsellers" -> "🔥"
            "eaz-promotions", "promotions" -> "🏷️"
            "gift-card", "gift-cards", "gutscheine" -> "🎁"
            "home-living", "home-&-living" -> "🏡"
            "drinkware" -> "☕"
            "wall-art" -> "🖼️"
            "phone-cases" -> "📱"
            else -> "📦"
        }
}
