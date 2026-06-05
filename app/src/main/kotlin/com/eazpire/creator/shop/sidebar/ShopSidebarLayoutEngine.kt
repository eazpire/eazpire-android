package com.eazpire.creator.shop.sidebar

import com.eazpire.creator.ui.nav.EazNavTablerIcons

private const val SHOP_ORIGIN = "https://www.eazpire.com"

private val AUD_ORDER =
    listOf(
        ShopSidebarConstants.normalizeHandleLite("women"),
        ShopSidebarConstants.normalizeHandleLite("men"),
        ShopSidebarConstants.normalizeHandleLite("kids"),
        ShopSidebarConstants.normalizeHandleLite("toddler"),
    )

object ShopSidebarLayoutEngine {

    private val AUD_SET: Set<String> =
        ShopSidebarConstants.audienceHandlesRaw.map { ShopSidebarConstants.normalizeHandleLite(it) }.toSet()

    fun draggableSectionId(sec: SidebarGridSection): String? =
        when (sec) {
            is GutscheineGridSection -> ShopSidebarConstants.CONTAINER_GUTSCHEINE
            is CreatePromoSection -> null
            is AudienceSidebarSection -> ShopSidebarConstants.CONTAINER_AUDIENCE
            is GroupedCategorySection -> sec.containerId
            is RemainingTopSection -> sec.containerId
        }

    /**
     * @param injectCreatePlaceholder Shop Create pill (handle `eaz_shop_create`).
     */
    fun buildGridSections(
        main: ParsedMenu?,
        audienceExplicit: ParsedMenu?,
        injectCreatePlaceholder: Boolean = true,
        savedSectionOrder: List<String>?,
    ): Triple<SidebarAudienceSource, List<ParsedNavItem>, List<SidebarGridSection>> {
        val roots = main?.items.orEmpty()

        val source: SidebarAudienceSource =
            when {
                audienceExplicit?.items.orEmpty().isNotEmpty() -> SidebarAudienceSource.Dedicated
                roots.any { it.handle in AUD_SET } -> SidebarAudienceSource.Main
                else -> SidebarAudienceSource.Hardcoded
            }

        val sections = mutableListOf<SidebarGridSection>()
        sections += GutscheineGridSection()
        if (injectCreatePlaceholder) {
            sections += CreatePromoSection()
        }

        sections +=
            when (source) {
                SidebarAudienceSource.Dedicated -> audienceFromDedicated(requireNotNull(audienceExplicit))
                SidebarAudienceSource.Main -> audienceFromMain(roots.filter { it.handle in AUD_SET })
                SidebarAudienceSource.Hardcoded -> audienceHardcodedPanel()
            }

        var order = 1
        if (groupPresence(roots, ShopSidebarConstants.homeDecorHandles)) {
            sections +=
                GroupedCategorySection(
                    ShopSidebarConstants.CONTAINER_HOME_DECOR,
                    "eaz.sidebar.home_decor",
                    EazNavTablerIcons.iconNameForHandle("home-living"),
                    groupedTilesFor(roots, ShopSidebarConstants.homeDecorHandles.toSet(), "home-decor") { iconForHandle(it) },
                    true,
                    order++,
                )
        }
        if (groupPresence(roots, ShopSidebarConstants.lifestyleHandles)) {
            sections +=
                GroupedCategorySection(
                    ShopSidebarConstants.CONTAINER_LIFESTYLE,
                    "eaz.sidebar.lifestyle",
                    EazNavTablerIcons.iconNameForHandle("sparkle"),
                    groupedTilesFor(roots, ShopSidebarConstants.lifestyleHandles.toSet(), "lifestyle") { iconForHandle(it) },
                    true,
                    order++,
                )
        }
        if (groupPresence(roots, ShopSidebarConstants.techHandles)) {
            sections +=
                GroupedCategorySection(
                    ShopSidebarConstants.CONTAINER_TECH,
                    "eaz.sidebar.tech",
                    EazNavTablerIcons.iconNameForHandle("tech"),
                    groupedTilesFor(roots, ShopSidebarConstants.techHandles.toSet(), "tech") { iconForHandle(it) },
                    true,
                    order++,
                )
        }

        for (top in roots) {
            if (source == SidebarAudienceSource.Main && top.handle in AUD_SET) continue
            if (ShopSidebarConstants.skipHandles.contains(top.handle)) continue
            if (ShopSidebarConstants.groupedHandles.contains(top.handle)) continue
            sections += remainderSection(top, order++)
        }

        return Triple(source, roots, reorderWithSaved(sections, savedSectionOrder))
    }

    private fun reorderWithSaved(sections: List<SidebarGridSection>, savedOrder: List<String>?): List<SidebarGridSection> {
        if (savedOrder.isNullOrEmpty()) return sections
        val heads = mutableListOf<SidebarGridSection>()
        val movables = mutableListOf<SidebarGridSection>()
        for (s in sections) {
            if (draggableSectionId(s) == null) {
                heads += s
            } else {
                movables += s
            }
        }
        val byId = movables.mapNotNull { sec -> draggableSectionId(sec)?.let { it to sec } }.toMap()
        val used = mutableSetOf<String>()
        val orderedMovables = mutableListOf<SidebarGridSection>()
        for (id in savedOrder) {
            val seg = byId[id] ?: continue
            orderedMovables += seg
            used += id
        }
        /** Items not referenced in saved ordering keep relative tail order */
        for (seg in movables) {
            val id = draggableSectionId(seg) ?: continue
            if (id !in used) {
                orderedMovables += seg
            }
        }
        return heads + orderedMovables
    }

    private fun groupPresence(items: List<ParsedNavItem>, keys: List<String>): Boolean {
        val s = keys.map { ShopSidebarConstants.normalizeHandleLite(it) }.toSet()
        return items.any { it.handle in s }
    }

    private fun remainderSection(top: ParsedNavItem, order: Int): RemainingTopSection {
        val containerId = top.handle
        val body =
            if (top.links.isEmpty()) {
                RemainderBody.SingleTrending(
                    midHideId = "$containerId--self",
                    url = normalizeUrl(top.url),
                    label = top.title,
                    navKey = navUiTranslationKey(containerId),
                )
            } else {
                RemainderBody.Tiles(top.links.map { childTile(containerId, it) })
            }
        return RemainingTopSection(
            containerId = containerId,
            title = top.title,
            navTitleKey = navUiTranslationKey(containerId),
            body = body,
            draggable = true,
            persistentOrderHint = order,
        )
    }

    private fun childTile(parentContainer: String, child: ParsedNavItem): CategoryTile {
        val midId = "${parentContainer}--${child.handle}"
        val iconId = iconForHandle(child.handle)
        return if (hasRealChildSubitems(child)) {
            CategoryTile(
                midId = midId,
                titleRaw = child.title,
                navTitleKey = navUiTranslationKey(child.handle),
                emoji = iconId,
                expandable = true,
                leafUrl = null,
                expandCells =
                    child.links.map { g ->
                        val catId = "$midId--${g.handle}"
                        ExpandCell(
                            hideCatId = catId,
                            labelRaw = g.title,
                            navTitleKey = navUiTranslationKey(g.handle),
                            url = normalizeUrl(g.url),
                        )
                    },
            )
        } else {
            CategoryTile(
                midId = midId,
                titleRaw = child.title,
                navTitleKey = navUiTranslationKey(child.handle),
                emoji = iconId,
                expandable = false,
                leafUrl = normalizeUrl(child.url),
                expandCells = emptyList(),
            )
        }
    }

    private fun groupedTilesFor(
        mainItems: List<ParsedNavItem>,
        whitelist: Set<String>,
        prefix: String,
        emojiFor: (String) -> String,
    ): List<CategoryTile> {
        val wl = whitelist.map { ShopSidebarConstants.normalizeHandleLite(it) }.toSet()
        val out = mutableListOf<CategoryTile>()
        for (tl in mainItems) {
            if (!wl.contains(tl.handle)) continue
            val midId = "${prefix}--${tl.handle}"
            if (hasRealSubitems(tl)) {
                out +=
                    CategoryTile(
                        midId = midId,
                        titleRaw = tl.title,
                        navTitleKey = navUiTranslationKey(tl.handle),
                        emoji = emojiFor(tl.handle),
                        expandable = true,
                        leafUrl = null,
                        expandCells =
                            tl.links.map { c ->
                                ExpandCell("$midId--${c.handle}", c.title, navUiTranslationKey(c.handle), normalizeUrl(c.url))
                            },
                    )
            } else {
                out +=
                    CategoryTile(
                        midId = midId,
                        titleRaw = tl.title,
                        navTitleKey = navUiTranslationKey(tl.handle),
                        emoji = emojiFor(tl.handle),
                        expandable = false,
                        leafUrl = normalizeUrl(tl.url),
                        expandCells = emptyList(),
                    )
            }
        }
        return out
    }

    private fun audienceFromDedicated(m: ParsedMenu): AudienceSidebarSection {
        val cards = mutableListOf<AudienceCard>()
        val panels = mutableListOf<AudiencePanelBody>()
        for (aud in m.items) {
            if (aud.handle == "baby" || aud.handle == "babys") continue
            val midRoot = "aud--${aud.handle}"
            cards += audienceCard(midRoot, aud)
            panels += AudiencePanelBody(aud.handle, audienceCategories(midRoot, aud))
        }
        return AudienceSidebarSection(SidebarAudienceSource.Dedicated, cards, panels)
    }

    private fun audienceFromMain(nodes: List<ParsedNavItem>): AudienceSidebarSection {
        val sorted = nodes.distinctBy { it.handle }.sortedWith(compareBy { h -> AUD_ORDER.indexOf(h.handle).takeIf { ix -> ix >= 0 } ?: 999 })
        val cards =
            sorted.map { top ->
                val midRoot = "aud--${top.handle}"
                audienceCard(midRoot, top)
            }
        val panels =
            sorted.map { top ->
                val midRoot = "aud--${top.handle}"
                AudiencePanelBody(top.handle, audienceCategories(midRoot, top))
            }
        return AudienceSidebarSection(SidebarAudienceSource.Main, cards, panels)
    }

    private fun audienceHardcodedPanel(): AudienceSidebarSection {
        val stubs =
            listOf(
                ParsedNavItem("Women", "women", collectionHref("women"), emptyList()),
                ParsedNavItem("Men", "men", collectionHref("men"), emptyList()),
                ParsedNavItem("Kids", "kids", collectionHref("kids"), emptyList()),
                ParsedNavItem("Toddler", "toddler", collectionHref("toddler"), emptyList()),
            )
        val cards = stubs.map { aud -> audienceCard("aud--${aud.handle}", aud) }
        val panels =
            stubs.map { aud ->
                val midRoot = "aud--${aud.handle}"
                AudiencePanelBody(aud.handle, hardcodedCategoryColumns(midRoot, aud.handle))
            }
        return AudienceSidebarSection(SidebarAudienceSource.Hardcoded, cards, panels)
    }

    private fun hardcodedCategoryColumns(midRoot: String, audHandle: String): List<AudienceCategoryColumn> =
        buildList {
            add(audCatColumn(midRoot, audHandle, "clothing", navUiTranslationKey("clothing"), "Clothing"))
            add(audCatColumn(midRoot, audHandle, "shoes", navUiTranslationKey("shoes"), "Shoes"))
            add(audCatColumn(midRoot, audHandle, "accessories", navUiTranslationKey("accessories"), "Accessories"))
        }

    private fun audCatColumn(
        midRoot: String,
        audHandle: String,
        catHandle: String,
        navTitleKeyT: String,
        titleHuman: String,
    ): AudienceCategoryColumn {
        val catId = "$midRoot--$catHandle"
        val fragMaster =
            when (catHandle) {
                "clothing" -> EazCollectionProductTypeQuery.buildQueryFragment("apparel")
                "shoes" -> EazCollectionProductTypeQuery.buildQueryFragment("shoes_all")
                else -> EazCollectionProductTypeQuery.buildQueryFragment("accessories_all")
            }
        val titleUrl = collectionHrefFiltered(audHandle, fragMaster)
        val lines =
            when (catHandle) {
                "clothing" -> hcClothingLines(audHandle, catId)
                "shoes" -> hcAccessoryLineTriplet(audHandle, catId, "shoes")
                else -> hcAccessoryLineTriplet(audHandle, catId, "accessories")
            }
        return AudienceCategoryColumn(
            rowKey = "$audHandle--$catHandle",
            catHidePrefix = catId,
            title = titleHuman,
            titleUrl = titleUrl,
            navTitleKey = navTitleKeyT,
            expandable = true,
            lines = lines,
        )
    }

    private fun hcClothingLines(audHandle: String, catPrefix: String): List<AudienceDetailLine> =
        listOf(
            audLine(catPrefix, "t-shirts", "T-Shirts", "tshirt", audHandle),
            audLine(catPrefix, "hoodies", "Hoodies", "hoodie", audHandle),
            audLine(catPrefix, "sweatshirts", "Sweatshirts", "sweatshirt", audHandle),
            audLine(catPrefix, "tank-tops", "Tank Tops", "tank_top", audHandle),
            audLine(catPrefix, "jackets", "Jackets", "jacket", audHandle),
            audLine(catPrefix, "shorts", "Shorts", "shorts", audHandle),
            audLine(catPrefix, "dresses", "Dresses", "dress", audHandle),
        )

    private fun hcAccessoryLineTriplet(audHandle: String, catPrefix: String, kind: String): List<AudienceDetailLine> =
        when (kind) {
            "shoes" ->
                listOf(
                    audLine(catPrefix, "sneakers", "Sneakers", "sneakers", audHandle),
                    audLine(catPrefix, "boots", "Boots", "boots", audHandle),
                    audLine(catPrefix, "sandals", "Sandals", "sandals", audHandle),
                )
            else ->
                /** accessories — hats row uses translated key in Liquid; reuse raw english label fallback */
                listOf(
                    audLine(catPrefix, "bags", "Bags", "bags", audHandle),
                    audLine(catPrefix, "jewelry", "Jewelry", "jewelry", audHandle),
                    audLine(catPrefix, "hats-caps", "Hats & Caps", "hats", audHandle),
                )
        }

    private fun audLine(catPrefix: String, slugId: String, label: String, navKeySlug: String, audHandle: String): AudienceDetailLine {
        val frag = EazCollectionProductTypeQuery.buildQueryFragment(navKeySlug)
        return AudienceDetailLine(
            hideCatId = "$catPrefix--$slugId",
            labelRaw = label,
            navTitleKey = "",
            url = collectionHrefFiltered(audHandle, frag),
        )
    }

    private fun audienceCategories(midRoot: String, audienceNode: ParsedNavItem): List<AudienceCategoryColumn> {
        val audienceHandle = audienceNode.handle
        return audienceNode.links.map { child ->
            val catId = "$midRoot--${child.handle}"
            val rowKey = "${audienceHandle}--${child.handle}"
            val grandchildren =
                if (child.links.isNotEmpty()) {
                    child.links.map { gc ->
                        val hideId = "${catId}--${gc.handle}"
                        AudienceDetailLine(
                            hideCatId = hideId,
                            labelRaw = gc.title,
                            navTitleKey = navUiTranslationKey(gc.handle),
                            url = normalizeUrl(gc.url),
                        )
                    }
                } else {
                    fallbackAudienceChildLines(audienceHandle, child.handle, catId)
                }
            AudienceCategoryColumn(
                rowKey = rowKey,
                catHidePrefix = catId,
                title = child.title,
                titleUrl = normalizeUrl(child.url),
                navTitleKey = navUiTranslationKey(child.handle),
                expandable = grandchildren.isNotEmpty(),
                lines = grandchildren,
            )
        }
    }

    private fun fallbackAudienceChildLines(collectionAudience: String, catHandleNorm: String, catId: String): List<AudienceDetailLine> =
        buildList {
            /** Mirror [theme/snippets/eaz-sidebar-grid.liquid] `when clothing` branches */
            when (ShopSidebarConstants.normalizeHandleLite(catHandleNorm)) {
                "clothing", "bekleidung" -> {
                    addAll(hcClothingLines(collectionAudience, catId))
                }
                "shoes", "schuhe" -> {
                    addAll(hcAccessoryLineTriplet(collectionAudience, catId, "shoes"))
                }
                "accessories", "accessoires" -> {
                    addAll(hcAccessoryLineTriplet(collectionAudience, catId, "accessories"))
                }
            }
        }

    private fun audienceCard(midRoot: String, aud: ParsedNavItem): AudienceCard {
        val h = aud.handle
        val url = normalizeUrl(if (aud.url.isNotBlank()) aud.url else "/collections/$h")
        return AudienceCard(
            audHandle = h,
            midId = midRoot,
            title = aud.title,
            collectionUrl = url,
            navTitleKey = navUiTranslationKey(h),
            emoji = iconForHandle(h),
        )
    }

    private fun iconForHandle(raw: String): String = EazNavTablerIcons.iconNameForHandle(raw)

    private fun normalizeUrl(raw: String): String {
        val u = raw.trim()
        when {
            u.isEmpty() -> return SHOP_ORIGIN
            u.startsWith("http://") || u.startsWith("https://") -> return u
            u.startsWith("//") -> return "https:$u"
            u.startsWith("/") -> return SHOP_ORIGIN.removeSuffix("/") + u.replaceFirst("^//+".toRegex(), "/")
            else -> return SHOP_ORIGIN.removeSuffix("/") + "/" + u
        }
    }

    private fun collectionHref(handle: String) = normalizeUrl("/collections/$handle")

    private fun collectionHrefFiltered(handle: String, q: String) =
        normalizeUrl("/collections/$handle").let { url ->
            if (q.isBlank()) url else "$url?$q"
        }
}