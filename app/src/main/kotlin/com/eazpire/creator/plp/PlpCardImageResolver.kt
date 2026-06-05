package com.eazpire.creator.plp

import android.content.Context
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyProductsApi
import com.eazpire.creator.mockup.CustomerMockPreviewStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** URLs shown on a PLP/carousel card + whether they are personalized mocks. */
data class PlpCardDisplay(
    val urls: List<String>,
    val isPersonalizedMock: Boolean,
) {
    val autoRotate: Boolean get() = urls.size > 1
}

/**
 * Shop variant rotation and optional personalized mock URLs (parity with web PLP + try-on).
 */
object PlpCardImageResolver {

    fun shopRotationUrls(product: ShopifyProductsApi.ProductItem): List<String> =
        product.variantImages.ifEmpty { product.images }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    suspend fun resolve(
        context: Context,
        creatorApi: CreatorApi?,
        ownerId: String,
        product: ShopifyProductsApi.ProductItem,
        mockMap: JSONObject?,
        sessionTryOnActive: Boolean,
    ): PlpCardDisplay = withContext(Dispatchers.IO) {
        val shop = shopRotationUrls(product)
        if (ownerId.isBlank() || creatorApi == null) {
            return@withContext PlpCardDisplay(shop, isPersonalizedMock = false)
        }

        val map = mockMap
            ?: CustomerMockPreviewStore.peekMap(ownerId)
            ?: CustomerMockPreviewStore.loadMap(creatorApi, ownerId)

        val autoMock = CustomerMockPreviewStore.shouldAutoShowMockOnCard(
            map,
            product.handle,
            product.metaProductKey,
            product.designId
        )
        val explicitlyOff = CustomerMockPreviewStore.isTryOnExplicitlyOff(context, product.handle)
        val sessionOn = CustomerMockPreviewStore.isTryOnSessionActive(context, product.handle)
        val useMock = !explicitlyOff && (sessionOn || autoMock)

        if (!useMock && CustomerMockPreviewStore.tryOnInfo(
                map,
                product.handle,
                product.metaProductKey,
                product.designId
            ) == null
        ) {
            return@withContext PlpCardDisplay(shop, isPersonalizedMock = false)
        }

        val mockUrls = CustomerMockPreviewStore.resolveCardImages(
            context,
            creatorApi,
            ownerId,
            product,
            map
        ).map { it.trim() }.filter { it.isNotBlank() }.distinct()

        when {
            mockUrls.size >= 2 -> PlpCardDisplay(mockUrls, isPersonalizedMock = true)
            mockUrls.size == 1 -> PlpCardDisplay(mockUrls, isPersonalizedMock = true)
            useMock && shop.isNotEmpty() -> PlpCardDisplay(shop, isPersonalizedMock = false)
            else -> PlpCardDisplay(shop, isPersonalizedMock = false)
        }
    }
}
