package com.eazpire.creator.shop.sidebar

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.ChildFriendly
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Man
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.Woman
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Icons for sidebar list rows — aligned with grid tile emojis in [ShopSidebarLayoutEngine].
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

    /** Orange Material icon for sidebar list rows (left of title). */
    fun vectorForHandle(rawHandle: String): ImageVector {
        val h = ShopSidebarConstants.normalizeHandleLite(rawHandle)
        return when {
            h in audienceSet -> vectorAudience(h)
            h in homeDecorSet -> vectorHomeDecor(h)
            h in lifestyleSet -> vectorLifestyle(h)
            h in techSet -> vectorTech(h)
            else -> vectorChildMid(h)
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

    private fun vectorAudience(handle: String): ImageVector =
        when (handle) {
            "women", "female", "frauen" -> Icons.Filled.Woman
            "men", "male", "manner" -> Icons.Filled.Man
            "kids", "kinder" -> Icons.Filled.ChildCare
            "toddler", "babys", "baby" -> Icons.Filled.ChildFriendly
            else -> Icons.Filled.Woman
        }

    private fun vectorHomeDecor(h: String): ImageVector =
        when (h) {
            "drinkware" -> Icons.Filled.LocalCafe
            "wall-art" -> Icons.Filled.Image
            "home-living", "home-&-living" -> Icons.Filled.Home
            "plush-toys" -> Icons.Filled.Toys
            "stationery" -> Icons.Filled.Edit
            else -> Icons.Filled.Inventory2
        }

    private fun vectorLifestyle(h: String): ImageVector =
        when (h) {
            "bags", "taschen" -> Icons.Filled.ShoppingBag
            "jewelry", "schmuck" -> Icons.Filled.Diamond
            else -> Icons.Filled.ShoppingBag
        }

    private fun vectorTech(h: String): ImageVector =
        when (h) {
            "phone-cases", "handyhullen" -> Icons.Filled.PhoneAndroid
            "tech" -> Icons.Filled.Computer
            else -> Icons.Filled.PhoneAndroid
        }

    private fun vectorChildMid(h: String): ImageVector =
        when (h) {
            "t-shirts", "hoodies", "sweatshirts", "tank-tops", "crop-tops", "long-sleeves",
            "jackets", "coats", "shorts", "joggers", "pants", "jeans", "dresses", "socks",
            "leggings", "skirts",
            -> Icons.Filled.Checkroom
            "shoes", "schuhe", "sneakers" -> Icons.Filled.DirectionsRun
            "bags", "taschen" -> Icons.Filled.ShoppingBag
            "accessories", "accessoires" -> Icons.Filled.Watch
            "jewelry", "schmuck" -> Icons.Filled.Diamond
            "deals", "eaz-promotions", "promotions" -> Icons.Filled.LocalOffer
            "new-arrivals" -> Icons.Filled.Star
            "bestsellers" -> Icons.Filled.Whatshot
            "gift-card", "gift-cards", "gutscheine" -> Icons.Filled.CardGiftcard
            "home-living", "home-&-living" -> Icons.Filled.Home
            "drinkware" -> Icons.Filled.LocalCafe
            "wall-art" -> Icons.Filled.Image
            "phone-cases" -> Icons.Filled.PhoneAndroid
            else -> Icons.Filled.Inventory2
        }
}
