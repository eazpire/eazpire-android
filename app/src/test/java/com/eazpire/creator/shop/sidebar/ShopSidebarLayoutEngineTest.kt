package com.eazpire.creator.shop.sidebar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopSidebarLayoutEngineTest {

    @Test
    fun buildGridSections_nullMain_usesHardcodedAudience() {
        val (source, roots, sections) =
            ShopSidebarLayoutEngine.buildGridSections(
                main = null,
                audienceExplicit = null,
                injectCreatePlaceholder = true,
                savedSectionOrder = emptyList(),
            )
        assertEquals(SidebarAudienceSource.Hardcoded, source)
        assertTrue(roots.isEmpty())
        assertTrue(sections.any { it is GutscheineGridSection })
        assertTrue(sections.any { it is CreatePromoSection })
        val aud = sections.filterIsInstance<AudienceSidebarSection>().singleOrNull()
        assertNotNull(aud)
        assertEquals(SidebarAudienceSource.Hardcoded, aud!!.source)
        assertTrue(aud.cards.size >= 4)
    }

    @Test
    fun buildGridSections_womenInMain_usesMainAudience() {
        val main =
            ParsedMenu(
                "main-menu",
                listOf(
                    ParsedNavItem(
                        "Women",
                        "women",
                        "https://www.eazpire.com/collections/women",
                        listOf(
                            ParsedNavItem(
                                "Clothing",
                                "clothing",
                                "/collections/women",
                                emptyList(),
                            )
                        ),
                    )
                ),
            )
        val (source, roots, _) =
            ShopSidebarLayoutEngine.buildGridSections(
                main = main,
                audienceExplicit = null,
                injectCreatePlaceholder = true,
                savedSectionOrder = emptyList(),
            )
        assertEquals(SidebarAudienceSource.Main, source)
        assertEquals(1, roots.size)
    }

    @Test
    fun buildGridSections_dedicatedAudienceWhenExplicitMenuNonempty() {
        val main =
            ParsedMenu(
                "main-menu",
                listOf(ParsedNavItem("Other", "other", "/", emptyList())),
            )
        val aud =
            ParsedMenu(
                "audience",
                listOf(ParsedNavItem("Men", "men", "/collections/men", emptyList())),
            )
        val (source, _, sections) =
            ShopSidebarLayoutEngine.buildGridSections(
                main = main,
                audienceExplicit = aud,
                injectCreatePlaceholder = false,
                savedSectionOrder = emptyList(),
            )
        assertEquals(SidebarAudienceSource.Dedicated, source)
        assertTrue(
            sections.filterIsInstance<AudienceSidebarSection>().single().cards.any {
                it.audHandle == "men"
            }
        )
    }

    @Test
    fun buildGrid_sectionsIncludeHomeDecorGroupWhenRootsContainDrinkware() {
        val main =
            ParsedMenu(
                "main-menu",
                listOf(
                    ParsedNavItem(
                        "Drinkware",
                        "drinkware",
                        "/collections/drinkware",
                        emptyList(),
                    )
                ),
            )
        val (_, _, sections) =
            ShopSidebarLayoutEngine.buildGridSections(
                main = main,
                audienceExplicit = null,
                injectCreatePlaceholder = true,
                savedSectionOrder = emptyList(),
            )
        val decor = sections.filterIsInstance<GroupedCategorySection>().find { it.containerId == ShopSidebarConstants.CONTAINER_HOME_DECOR }
        assertNotNull(decor)
        assertTrue(decor!!.tiles.isNotEmpty())
    }

    @Test
    fun draggableSectionId_matchesWebContainerIds() {
        assertEquals(
            ShopSidebarConstants.CONTAINER_GUTSCHEINE,
            ShopSidebarLayoutEngine.draggableSectionId(GutscheineGridSection()),
        )
        assertEquals(null, ShopSidebarLayoutEngine.draggableSectionId(CreatePromoSection()))
        assertEquals(
            ShopSidebarConstants.CONTAINER_AUDIENCE,
            ShopSidebarLayoutEngine.draggableSectionId(
                AudienceSidebarSection(
                    SidebarAudienceSource.Hardcoded,
                    emptyList(),
                    emptyList(),
                ),
            ),
        )
        val grouped =
            GroupedCategorySection(
                ShopSidebarConstants.CONTAINER_TECH,
                "eaz.sidebar.tech",
                "",
                emptyList(),
                true,
                1,
            )
        assertEquals(ShopSidebarConstants.CONTAINER_TECH, ShopSidebarLayoutEngine.draggableSectionId(grouped))
    }

    @Test
    fun savedSectionOrder_movesAudienceAheadOfGutscheine() {
        val main =
            ParsedMenu(
                "main-menu",
                listOf(
                    ParsedNavItem("Women", "women", "/collections/women", emptyList()),
                    ParsedNavItem("Zoo", "zoo-extra", "/", emptyList()),
                ),
            )
        val (_, _, defaultOrder) =
            ShopSidebarLayoutEngine.buildGridSections(
                main = main,
                audienceExplicit = null,
                injectCreatePlaceholder = true,
                savedSectionOrder = null,
            )
        val defaultMovables =
            defaultOrder.mapNotNull { ShopSidebarLayoutEngine.draggableSectionId(it) }

        assertTrue(defaultMovables.indexOf(ShopSidebarConstants.CONTAINER_GUTSCHEINE) >= 0)
        assertTrue(defaultMovables.indexOf(ShopSidebarConstants.CONTAINER_AUDIENCE) >= 0)

        val swappedLead =
            listOf(
                ShopSidebarConstants.CONTAINER_AUDIENCE,
                ShopSidebarConstants.CONTAINER_GUTSCHEINE,
            ) + defaultMovables.filter { it != ShopSidebarConstants.CONTAINER_AUDIENCE && it != ShopSidebarConstants.CONTAINER_GUTSCHEINE }

        val (_, _, reordered) =
            ShopSidebarLayoutEngine.buildGridSections(
                main = main,
                audienceExplicit = null,
                injectCreatePlaceholder = true,
                savedSectionOrder = swappedLead,
            )

        val movableIds = reordered.mapNotNull { ShopSidebarLayoutEngine.draggableSectionId(it) }
        assertTrue(movableIds.indexOf(ShopSidebarConstants.CONTAINER_AUDIENCE) < movableIds.indexOf(ShopSidebarConstants.CONTAINER_GUTSCHEINE))
        assertTrue(reordered.first() is CreatePromoSection || reordered.filterIsInstance<CreatePromoSection>().isNotEmpty())
    }

    @Test
    fun skipHandles_excludedFromRemainderSections() {
        val main =
            ParsedMenu(
                "main-menu",
                listOf(
                    ParsedNavItem("Gift", "gift-cards", "/", emptyList()),
                    ParsedNavItem("Visible", "visible-handle", "/", emptyList()),
                ),
            )
        val (_, _, sections) =
            ShopSidebarLayoutEngine.buildGridSections(
                main = main,
                audienceExplicit = null,
                injectCreatePlaceholder = false,
                savedSectionOrder = emptyList(),
            )
        assertTrue(
            sections.filterIsInstance<RemainingTopSection>().none {
                it.containerId == ShopSidebarConstants.normalizeHandleLite("gift-cards")
            },
        )
        assertTrue(
            sections.filterIsInstance<RemainingTopSection>().any {
                it.containerId == "visible-handle"
            },
        )
    }
}
