package com.eazpire.creator.ui

import android.content.Context
import com.eazpire.creator.api.CreatorApi
import com.eazpire.creator.api.ShopifyStorefrontCartApi
import com.eazpire.creator.cart.AppCartStore
import com.eazpire.creator.cart.StorefrontCartStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Shared Shopify enqueue / listing poll / cart add helpers for Shop Printify Design Studio.
 * Mirrors theme `eaz-shop-printify-studio-test.js` (buildShopStudioEnqueuePayload + performCartEnqueue).
 */
internal object ShopStudioShopifyActions {

    data class EnqueueResult(
        val ok: Boolean,
        val status: String = "",
        val listingId: String? = null,
        val shopifyVariantId: String? = null,
        val shopifyProductId: String? = null,
        val message: String? = null
    )

    data class CartAddResult(
        val ok: Boolean,
        val message: String? = null,
        val preparing: Boolean = false
    )

    fun buildPlacement(
        designDx: Float,
        designDy: Float,
        designScale: Float,
        designRotate: Float,
        patternEnabled: Boolean,
        activeMockPosition: String,
        zoneW: Float = 200f
    ): JSONObject {
        val w = zoneW.coerceAtLeast(1f)
        val px = (0.5f + designDx / w).coerceIn(0f, 1f)
        val py = (0.5f + designDy / w).coerceIn(0f, 1f)
        val pattern = JSONObject().apply {
            put("enabled", patternEnabled)
            if (patternEnabled) {
                put("mode", "grid")
                put("spacing_x", 1.0)
                put("spacing_y", 1.0)
            }
        }
        return JSONObject()
            .put("x", px.toDouble())
            .put("y", py.toDouble())
            .put("scale", designScale.toDouble())
            .put("angle", designRotate.toDouble())
            .put("pattern", pattern)
            .put("printify_position", activeMockPosition.ifBlank { "front" })
    }

    fun buildEnqueuePayload(
        ownerId: String,
        printifyProductId: String,
        productKey: String,
        intent: String,
        placement: JSONObject,
        selectedColorId: Long?,
        selectedSizeId: Long?,
        selectedVariantId: Long?,
        selectedColorLabel: String?,
        selectedSizeLabel: String?,
        priceCents: Int?,
        currency: String?,
        productTitle: String?,
        previewUrl: String?,
        quantity: Int = 1,
        contributorDesignIds: List<String> = emptyList(),
        mockUrls: List<String> = emptyList()
    ): JSONObject {
        val layers = JSONArray().put(
            JSONObject()
                .put("printify_product_id", printifyProductId)
                .put("product_key", productKey)
                .put("placement", placement.optString("printify_position", "front"))
                .put("scale", placement.optDouble("scale", 1.0))
                .put("angle", placement.optDouble("angle", 0.0))
                .put("x", placement.optDouble("x", 0.5))
                .put("y", placement.optDouble("y", 0.5))
        )
        return JSONObject().apply {
            put("owner_id", ownerId)
            put("printify_product_id", printifyProductId)
            put("product_key", productKey)
            put("intent", intent)
            put("quantity", quantity.coerceAtLeast(1))
            put("placement", placement.optString("printify_position", "front"))
            put("layers", layers)
            if (!selectedColorLabel.isNullOrBlank()) put("selected_color", selectedColorLabel)
            if (!selectedSizeLabel.isNullOrBlank()) put("selected_size", selectedSizeLabel)
            if (selectedColorId != null) put("color_value_id", selectedColorId)
            if (selectedSizeId != null) put("size_value_id", selectedSizeId)
            if (selectedVariantId != null) put("selected_variant_id", selectedVariantId)
            if (priceCents != null) put("price_cents", priceCents)
            if (!currency.isNullOrBlank()) put("currency", currency)
            if (!productTitle.isNullOrBlank()) put("product_title", productTitle)
            if (!previewUrl.isNullOrBlank()) put("preview_url", previewUrl)
            if (mockUrls.isNotEmpty()) {
                put("mock_urls", JSONArray().apply { mockUrls.forEach { put(it) } })
                put("preview_mock_index", 0)
            }
            if (contributorDesignIds.isNotEmpty()) {
                put(
                    "contributor_design_ids",
                    JSONArray().apply { contributorDesignIds.forEach { put(it) } }
                )
            }
        }
    }

    suspend fun enqueue(
        api: CreatorApi,
        ownerId: String,
        payload: JSONObject
    ): EnqueueResult = withContext(Dispatchers.IO) {
        try {
            val data = api.shopStudioEnqueueShopify(ownerId, payload)
            if (!data.optBoolean("ok", false)) {
                return@withContext EnqueueResult(
                    ok = false,
                    message = listOf(
                        data.optString("message", ""),
                        data.optString("error", "")
                    ).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "enqueue_failed" }
                )
            }
            EnqueueResult(
                ok = true,
                status = data.optString("status", ""),
                listingId = data.opt("listing_id")?.toString()?.takeIf { it.isNotBlank() && it != "null" },
                shopifyVariantId = data.optString("shopify_variant_id", "").trim().ifBlank { null },
                shopifyProductId = data.optString("shopify_product_id", "").trim().ifBlank { null }
            )
        } catch (e: Exception) {
            EnqueueResult(ok = false, message = e.message ?: "enqueue_failed")
        }
    }

    /**
     * Poll listing until ready (or failed). Mirrors web: every 3s, max 45 attempts.
     */
    suspend fun pollListingReady(
        api: CreatorApi,
        ownerId: String,
        listingId: String
    ): EnqueueResult {
        var attempts = 0
        val maxAttempts = 45
        while (attempts < maxAttempts) {
            attempts++
            val data = withContext(Dispatchers.IO) {
                runCatching { api.shopStudioListingStatus(ownerId, listingId) }
                    .getOrElse { JSONObject().put("ok", false) }
            }
            if (data.optBoolean("ok", false)) {
                val status = data.optString("status", "")
                val variantId = data.optString("shopify_variant_id", "").trim().ifBlank { null }
                if (status == "ready" && !variantId.isNullOrBlank()) {
                    return EnqueueResult(
                        ok = true,
                        status = status,
                        listingId = listingId,
                        shopifyVariantId = variantId,
                        shopifyProductId = data.optString("shopify_product_id", "").trim().ifBlank { null }
                    )
                }
                if (status == "failed" || status == "cancelled") {
                    return EnqueueResult(
                        ok = false,
                        status = status,
                        listingId = listingId,
                        message = data.optString("error", status).ifBlank { status }
                    )
                }
            }
            delay(3000)
        }
        return EnqueueResult(ok = false, listingId = listingId, message = "timeout")
    }

    suspend fun addVariantToStorefrontCart(
        context: Context,
        variantIdRaw: String,
        quantity: Int = 1,
        customerAccessToken: String? = null,
        countryCode: String? = null
    ): CartAddResult = withContext(Dispatchers.IO) {
        val variantLong = variantIdRaw
            .substringAfterLast("/")
            .filter { it.isDigit() }
            .toLongOrNull()
            ?: return@withContext CartAddResult(ok = false, message = "invalid_variant")
        val cartApi = ShopifyStorefrontCartApi()
        val cartStore = StorefrontCartStore(context)
        val cartId = cartStore.cartId
        if (!cartId.isNullOrBlank()) {
            val add = cartApi.addLine(cartId, variantLong, quantity, customerAccessToken)
            if (add.ok) {
                add.cart?.let {
                    cartStore.cartId = it.cartId
                    AppCartStore.setCount(it.itemCount)
                }
                return@withContext CartAddResult(ok = true)
            }
        }
        val created = cartApi.createCart(
            listOf(variantLong to quantity.coerceAtLeast(1)),
            customerAccessToken,
            countryCode
        )
        if (created.ok && !created.cartId.isNullOrBlank()) {
            cartStore.cartId = created.cartId
            val loaded = cartApi.getCart(created.cartId)
            if (loaded != null) {
                AppCartStore.setCount(loaded.itemCount)
            } else {
                AppCartStore.setCount(quantity.coerceAtLeast(1))
            }
            return@withContext CartAddResult(ok = true)
        }
        CartAddResult(ok = false, message = created.message ?: "cart_add_failed")
    }

    /**
     * Enqueue cart intent; if pending, poll then add Storefront line.
     */
    suspend fun enqueueAndAddToCart(
        context: Context,
        api: CreatorApi,
        ownerId: String,
        payload: JSONObject,
        quantity: Int = 1,
        customerAccessToken: String? = null,
        countryCode: String? = null,
        onPreparing: (() -> Unit)? = null
    ): CartAddResult {
        val enq = enqueue(api, ownerId, payload.put("intent", "cart").put("quantity", quantity))
        if (!enq.ok) {
            return CartAddResult(ok = false, message = enq.message)
        }
        var variantId = enq.shopifyVariantId
        if (variantId.isNullOrBlank()) {
            val listingId = enq.listingId
                ?: return CartAddResult(ok = false, message = "missing_listing")
            onPreparing?.invoke()
            val polled = pollListingReady(api, ownerId, listingId)
            if (!polled.ok || polled.shopifyVariantId.isNullOrBlank()) {
                return CartAddResult(
                    ok = false,
                    preparing = true,
                    message = polled.message ?: "prepare_failed"
                )
            }
            variantId = polled.shopifyVariantId
        }
        return addVariantToStorefrontCart(
            context = context,
            variantIdRaw = variantId!!,
            quantity = quantity,
            customerAccessToken = customerAccessToken,
            countryCode = countryCode
        )
    }
}
