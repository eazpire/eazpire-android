package com.eazpire.creator.shop.sidebar

import org.json.JSONArray
import org.json.JSONObject

data class ParsedMenu(val handle: String, val items: List<ParsedNavItem>)

data class ParsedNavItem(
    val title: String,
    val handle: String,
    val url: String,
    val links: List<ParsedNavItem>
)

enum class SidebarAudienceSource { Dedicated, Main, Hardcoded }

sealed interface SidebarGridSection {
    val draggable: Boolean
    val persistentOrderHint: Int
}

data class GutscheineGridSection(
    override val draggable: Boolean = true,
    override val persistentOrderHint: Int = -1
) : SidebarGridSection

/** Synthetic row – not draggable in web sidebar (web has no Create tile in drawer top); keep draggable=false */
data class CreatePromoSection(
    override val draggable: Boolean = false,
    override val persistentOrderHint: Int = -2,
) : SidebarGridSection

data class AudienceSidebarSection(
    val source: SidebarAudienceSource,
    val cards: List<AudienceCard>,
    val panelBodies: List<AudiencePanelBody>,
    override val draggable: Boolean = true,
    override val persistentOrderHint: Int = 0
) : SidebarGridSection

data class AudienceCard(
    val audHandle: String,
    val midId: String,
    val title: String,
    val collectionUrl: String,
    val navTitleKey: String,
    val emoji: String,
)

data class AudiencePanelBody(
    val audHandle: String,
    val categories: List<AudienceCategoryColumn>,
)

data class AudienceCategoryColumn(
    /** e.g. women--clothing — used for accordion */
    val rowKey: String,
    /** full cat id mid--... for hiding category row vs items */
    val catHidePrefix: String,
    val title: String,
    val titleUrl: String,
    val navTitleKey: String,
    val expandable: Boolean,
    val lines: List<AudienceDetailLine>,
)

data class AudienceDetailLine(
    val hideCatId: String,
    val labelRaw: String,
    val navTitleKey: String,
    val url: String,
)

data class GroupedCategorySection(
    val containerId: String,
    val sectionTitleKey: String,
    val sectionEmoji: String,
    val tiles: List<CategoryTile>,
    override val draggable: Boolean = true,
    override val persistentOrderHint: Int,
) : SidebarGridSection

data class RemainingTopSection(
    val containerId: String,
    val title: String,
    val navTitleKey: String,
    val body: RemainderBody,
    override val draggable: Boolean = true,
    override val persistentOrderHint: Int,
) : SidebarGridSection

sealed interface RemainderBody {
    data class Tiles(val tiles: List<CategoryTile>) : RemainderBody
    data class SingleTrending(val midHideId: String, val url: String, val label: String, val navKey: String) : RemainderBody
}

data class CategoryTile(
    val midId: String,
    val titleRaw: String,
    val navTitleKey: String,
    val emoji: String,
    val expandable: Boolean,
    val leafUrl: String?,
    val expandCells: List<ExpandCell>,
)

data class ExpandCell(
    val hideCatId: String,
    val labelRaw: String,
    val navTitleKey: String,
    val url: String,
)

object ShopSidebarMenuParser {

    fun parseMenusResponse(navJson: JSONObject): Pair<ParsedMenu?, ParsedMenu?> {
        val menus = navJson.optJSONObject("menus") ?: return Pair(null, null)
        val main = menus.optJSONObject("main")?.let { parseMenu(it) }
        val aud = menus.optJSONObject("audience")?.let { parseMenu(it) }
        return Pair(main, aud)
    }

    fun parseMenu(obj: JSONObject): ParsedMenu {
        val h = obj.optString("handle", "main-menu")
        val arr = obj.optJSONArray("items") ?: JSONArray()
        val items = (0 until arr.length()).mapNotNull { i ->
            parseItem(arr.optJSONObject(i))
        }
        return ParsedMenu(h, items)
    }

    private fun parseItem(o: JSONObject?): ParsedNavItem? {
        if (o == null) return null
        val title = o.optString("title", "").trim()
        if (title.isEmpty()) return null
        val handle = ShopSidebarConstants.normalizeHandleLite(o.optString("handle", ""))
            .ifEmpty { inferHandle(title, o.optString("url", "")) }
        val url = o.optString("url", "")
        val linkArr = o.optJSONArray("links") ?: JSONArray()
        val links = (0 until linkArr.length()).mapNotNull { i ->
            parseItem(linkArr.optJSONObject(i))
        }
        return ParsedNavItem(title, handle, url, links)
    }

    private fun inferHandle(title: String, url: String): String {
        val fromUrl =
            Regex("/collections/([^/?#]+)", RegexOption.IGNORE_CASE).find(url)?.groupValues?.getOrNull(1)?.lowercase().orEmpty()
        if (fromUrl.isNotEmpty()) return fromUrl
        return ShopSidebarConstants.normalizeHandleLite(title)
    }
}

fun hasRealSubitems(node: ParsedNavItem): Boolean {
    val childCount = node.links.size
    if (childCount > 1) return true
    if (childCount == 1 && node.links[0].handle != node.handle) return true
    return false
}

fun hasRealChildSubitems(child: ParsedNavItem): Boolean {
    if (child.links.size > 1) return true
    if (child.links.size == 1 && child.links[0].handle != child.handle) return true
    return false
}
