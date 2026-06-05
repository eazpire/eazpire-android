package com.eazpire.creator.shop.sidebar

import com.eazpire.creator.ui.nav.EazNavTablerIcons

/**
 * Unified nav icon ids — parity with theme/snippets/eaz-nav-handle-icon-name.liquid
 */
object SidebarNavVisuals {

    fun iconNameForHandle(rawHandle: String): String = EazNavTablerIcons.iconNameForHandle(rawHandle)

    /** @deprecated stored icon id in [CategoryTile.emoji] — use [iconNameForHandle] */
    fun emojiForHandle(rawHandle: String): String = iconNameForHandle(rawHandle)

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
}
