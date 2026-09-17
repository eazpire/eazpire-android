package com.eazpire.shared.switcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSwitchRoutingTest {

    @Test
    fun shopStorefrontPaths_areShop() {
        val shop = listOf(
            "/",
            "/cart",
            "/search?q=tee",
            "/products/softstyle-tee",
            "/collections/women",
            "/creator",
            "/creator/alex",
            "/pages/ask-team",
            "/pages/thankyou",
            "/q/thankyou",
        )
        shop.forEach { path ->
            assertTrue("expected shop path: $path", AppSwitchRouting.isShopStorefrontPath(path))
        }
    }

    @Test
    fun creatorToolPaths_areNotShop() {
        val creator = listOf(
            "/pages/creator-dashboard",
            "/pages/creator-dashboard/",
            "/pages/design-generator",
            "/pages/design-generator?tab=1",
        )
        creator.forEach { path ->
            assertFalse("expected creator path: $path", AppSwitchRouting.isShopStorefrontPath(path))
        }
    }

    @Test
    fun cartAndSearchPaths_areShop() {
        assertTrue(AppSwitchRouting.isShopStorefrontPath("/cart/"))
        assertTrue(AppSwitchRouting.isShopStorefrontPath("cart"))
        assertTrue(AppSwitchRouting.isShopStorefrontPath("/search/"))
    }
}
