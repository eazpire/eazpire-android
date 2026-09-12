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
    fun singleColor_prefersFrontOnly_noViewCycling() {
        val build = PlpRotationUrls.fromProductImages(
            listOf(
                img("https://shop/folded.jpg", "White|folded"),
                img("https://shop/front.jpg", "White|front|preview-default"),
                img("https://shop/back.jpg", "White|back"),
            )
        )
        assertEquals(1, build.urls.size)
        assertEquals("https://shop/front.jpg", build.urls[0])
    }

    @Test
    fun multiColor_prefersLifestyleOverFolded() {
        val build = PlpRotationUrls.fromProductImages(
            listOf(
                img("https://shop/w-folded.jpg", "White|folded"),
                img("https://shop/w-front.jpg", "White|front|preview-default"),
                img("https://shop/w-life.jpg", "White|lifestyle-female|preview-default"),
                img("https://shop/b-life.jpg", "Black|lifestyle-female|preview-default"),
                img("https://shop/b-front.jpg", "Black|front|preview-default"),
            ),
            productKey = "unisex-softstyle-cotton-tee",
            preferredLifestyleView = "lifestyle-female",
        )
        assertEquals(2, build.urls.size)
        assertTrue(build.urls.contains("https://shop/w-life.jpg"))
        assertTrue(build.urls.contains("https://shop/b-life.jpg"))
    }

    @Test
    fun menCollection_missingMale_usesFrontNotFemale() {
        val build = PlpRotationUrls.fromProductImages(
            listOf(
                img("https://shop/w-front.jpg", "White|front|preview-default"),
                img("https://shop/w-f.jpg", "White|lifestyle-female|preview-default"),
                img("https://shop/b-f.jpg", "Black|lifestyle-female|preview-default"),
                img("https://shop/b-front.jpg", "Black|front|preview-default"),
            ),
            productKey = "unisex-softstyle-cotton-tee",
            preferredLifestyleView = "lifestyle-male",
        )
        assertEquals(2, build.urls.size)
        assertTrue(build.urls.contains("https://shop/w-front.jpg"))
        assertTrue(build.urls.contains("https://shop/b-front.jpg"))
        assertTrue(!build.urls.contains("https://shop/w-f.jpg"))
        assertTrue(!build.urls.contains("https://shop/b-f.jpg"))
    }

    @Test
    fun menCollection_partialMale_fillsFrontForMissingColors() {
        val build = PlpRotationUrls.fromProductImages(
            listOf(
                img("https://shop/w-m.jpg", "White|lifestyle-male|preview-default"),
                img("https://shop/w-front.jpg", "White|front|preview-default"),
                img("https://shop/b-f.jpg", "Black|lifestyle-female|preview-default"),
                img("https://shop/b-front.jpg", "Black|front|preview-default"),
                img("https://shop/n-f.jpg", "Navy|lifestyle-female|preview-default"),
                img("https://shop/n-front.jpg", "Navy|front|preview-default"),
            ),
            productKey = "unisex-softstyle-cotton-tee",
            preferredLifestyleView = "lifestyle-male",
        )
        assertEquals(3, build.urls.size)
        assertTrue(build.urls.contains("https://shop/w-m.jpg"))
        assertTrue(build.urls.contains("https://shop/b-front.jpg"))
        assertTrue(build.urls.contains("https://shop/n-front.jpg"))
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

    @Test
    fun posterArEligible_fromTitleWhenProductKeyMissing() {
        assertTrue(
            PlpRotationUrls.isPosterArEligible(
                productKey = null,
                images = emptyList(),
                productType = null,
                title = "Cool Design | Photopaper Poster",
            )
        )
    }

    @Test
    fun posterArEligible_fromContextAltLayout() {
        assertTrue(
            PlpRotationUrls.isPosterArEligible(
                productKey = null,
                images = listOf(
                    img("https://shop/a4.jpg", "a4-vertical|front|preview-default"),
                    img("https://shop/a4-context.jpg", "a4-vertical|context_1"),
                ),
            )
        )
    }

    private fun img(src: String, alt: String) =
        ShopifyProductsApi.ProductImage(src = src, variantIds = emptyList(), alt = alt)
}
