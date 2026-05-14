package com.eazpire.creator.shop.sidebar

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShopSidebarMenuParserTest {

    @Test
    fun parseMenusResponse_extractsMainAndAudience() {
        val women =
            JSONObject()
                .put("title", "Women")
                .put("handle", "women")
                .put("url", "https://www.eazpire.com/collections/women")
                .put("links", JSONArray())
        val audItem =
            JSONObject()
                .put("title", "Women")
                .put("handle", "women")
                .put("url", "/collections/women")
                .put("links", JSONArray())
        val root =
            JSONObject()
                .put(
                    "menus",
                    JSONObject()
                        .put(
                            "main",
                            JSONObject()
                                .put("handle", "main-menu")
                                .put("items", JSONArray().put(women)),
                        )
                        .put(
                            "audience",
                            JSONObject()
                                .put("handle", "audience")
                                .put("items", JSONArray().put(audItem)),
                        ),
                )
        val (main, aud) = ShopSidebarMenuParser.parseMenusResponse(root)
        assertNotNull(main)
        assertEquals("main-menu", main!!.handle)
        assertEquals(1, main.items.size)
        assertEquals("women", main.items.first().handle)
        assertNotNull(aud)
        assertEquals("audience", aud!!.handle)
        assertEquals(1, aud.items.size)
    }

    @Test
    fun inferHandle_fromCollectionUrlWhenHandleMissing() {
        val item =
            JSONObject()
                .put("title", "Mystery")
                .put("handle", "")
                .put("url", "/collections/sale-items")
                .put("links", JSONArray())
        val menu =
            JSONObject()
                .put("handle", "main-menu")
                .put("items", JSONArray().put(item))
        val parsed = ShopSidebarMenuParser.parseMenu(menu)
        assertEquals("sale-items", parsed.items.single().handle)
    }

    @Test
    fun parseItem_skipsBlankTitle() {
        val junk =
            JSONObject()
                .put("title", "   ")
                .put("handle", "x")
                .put("url", "")
                .put("links", JSONArray())
        val good =
            JSONObject()
                .put("title", "Ok")
                .put("handle", "ok")
                .put("url", "")
                .put("links", JSONArray())
        val menu =
            JSONObject()
                .put("items", JSONArray().put(junk).put(good))
        val parsed = ShopSidebarMenuParser.parseMenu(menu)
        assertEquals(1, parsed.items.size)
        assertEquals("ok", parsed.items.first().handle)
    }

    @Test
    fun hasSingleDuplicateParent_detectsLiquidCase() {
        val dupChild = ParsedNavItem("Women", "women", "", emptyList())
        val parent = ParsedNavItem("Women", "women", "", listOf(dupChild))
        assertTrue(hasSingleDuplicateParent(parent))
    }

    @Test
    fun hasRealSubitems_twoChildren_true() {
        val n =
            ParsedNavItem(
                "X",
                "x",
                "",
                listOf(ParsedNavItem("A", "a", "", emptyList()), ParsedNavItem("B", "b", "", emptyList())),
            )
        assertTrue(hasRealSubitems(n))
    }

    @Test
    fun parseMenusResponse_missingMenus_returnsNullPair() {
        val (main, aud) =
            ShopSidebarMenuParser.parseMenusResponse(JSONObject().put("ok", true))
        assertNull(main)
        assertNull(aud)
    }
}
