package com.eazpire.creator.plp

import com.eazpire.creator.api.ShopifyProductsApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlpRotationUrlsTest {

    @Test
    fun multiColor_usesPreviewDefaultPerColor() {
        val build = PlpRotationUrls.fromProductImages(
            listOf(
                img("https://shop/a.jpg", "White|front|preview-default"),
                img("https://shop/b.jpg", "Black|front|preview-default"),
                img("https://shop/c.jpg", "White|back"),
            )
        )
        assertEquals(2, build.urls.size)
        assertTrue(build.urls.contains("https://shop/a.jpg"))
        assertTrue(build.urls.contains("https://shop/b.jpg"))
    }

    @Test
    fun singleColor_includesAllViewsForPrimaryColor() {
        val build = PlpRotationUrls.fromProductImages(
            listOf(
                img("https://shop/front.jpg", "White|front|preview-default"),
                img("https://shop/back.jpg", "White|back"),
                img("https://shop/right.jpg", "White|right"),
            )
        )
        assertEquals(3, build.urls.size)
        assertEquals(listOf("White", "White", "White"), build.colorNames)
    }

    @Test
    fun photopaper_rotatesSizeGroupsNotViews() {
        val build = PlpRotationUrls.fromProductImages(
            listOf(
                img("https://shop/a4.jpg", "a4-vertical|front|preview-default"),
                img("https://shop/a4-context.jpg", "a4-vertical|context_1"),
                img("https://shop/a3.jpg", "a3-vertical|front|preview-default"),
            ),
            productKey = "photopaper-posters",
        )
        assertEquals(2, build.urls.size)
        assertTrue(build.urls.contains("https://shop/a4.jpg"))
        assertTrue(build.urls.contains("https://shop/a3.jpg"))
    }

    private fun img(src: String, alt: String) =
        ShopifyProductsApi.ProductImage(src = src, variantIds = emptyList(), alt = alt)
}
